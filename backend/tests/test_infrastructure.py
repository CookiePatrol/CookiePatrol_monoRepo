import hashlib
import json
import logging
import sqlite3
from concurrent.futures import ThreadPoolExecutor

import httpx
import pytest

from app.domain.url_check import ThreatCheckUnavailable
from app.infrastructure import SafeBrowsingChecker, SqliteVerdictCache
from app.infrastructure import safe_browsing, verdict_cache


VALID_MATCH = {
    "threatType": "SOCIAL_ENGINEERING",
    "platformType": "ANY_PLATFORM",
    "threatEntryType": "URL",
    "threat": {"url": "https://example.com"},
    "cacheDuration": "300.000s",
}


@pytest.fixture
def cache(tmp_path, monkeypatch):
    monkeypatch.setenv("CACHE_DB_PATH", str(tmp_path / "cache.db"))
    instance = SqliteVerdictCache()
    yield instance
    instance.close()


@pytest.mark.parametrize("dangerous", [True, False])
def test_cache_round_trip_and_update(cache, dangerous):
    cache.set("https://example.com", dangerous)
    assert cache.get("https://example.com") is dangerous
    cache.set("https://example.com", not dangerous)
    assert cache.get("https://example.com") is not dangerous


def test_cache_missing(cache):
    assert cache.get("https://missing.example") is None


def test_cache_expiration_and_refresh(cache, monkeypatch):
    now = 1_000_000.0
    monkeypatch.setattr(verdict_cache.time, "time", lambda: now)
    cache.set("https://example.com", False)
    now += 24 * 60 * 60 - 1
    assert cache.get("https://example.com") is False
    now += 1
    assert cache.get("https://example.com") is None
    cache.set("https://example.com", True)
    assert cache.get("https://example.com") is True


def test_cache_persists(cache):
    cache.set("https://example.com", True)
    other = SqliteVerdictCache()
    try:
        assert other.get("https://example.com") is True
    finally:
        other.close()


def test_cache_stores_only_sha256_url_key(cache):
    url = "https://example.com/path?token=private"
    cache.set(url, True)

    columns = {
        row[1] for row in cache._connection.execute("PRAGMA table_info(verdicts)").fetchall()
    }
    row = cache._connection.execute("SELECT cache_key FROM verdicts").fetchone()

    assert "url" not in columns
    assert row == (hashlib.sha256(url.encode("utf-8")).hexdigest(),)
    assert url not in repr(cache._connection.execute("SELECT * FROM verdicts").fetchall())


def test_cache_migrates_and_removes_legacy_plaintext_urls(tmp_path, monkeypatch):
    db_path = tmp_path / "legacy.db"
    private_url = "https://example.com/?token=private"
    connection = sqlite3.connect(db_path)
    connection.execute(
        "CREATE TABLE verdicts (url TEXT PRIMARY KEY, dangerous INTEGER NOT NULL, checked_at REAL NOT NULL)"
    )
    connection.execute("INSERT INTO verdicts VALUES (?, 1, 1000)", (private_url,))
    connection.commit()
    connection.close()
    monkeypatch.setenv("CACHE_DB_PATH", str(db_path))

    cache = SqliteVerdictCache()
    try:
        columns = {
            row[1] for row in cache._connection.execute("PRAGMA table_info(verdicts)").fetchall()
        }
        rows = cache._connection.execute("SELECT * FROM verdicts").fetchall()
        assert columns == {"cache_key", "dangerous", "checked_at"}
        assert rows == []
        assert private_url.encode("utf-8") not in db_path.read_bytes()
    finally:
        cache.close()


def test_cache_thread_pool(cache):
    def store_and_read(index):
        url = f"https://example.com/{index}"
        cache.set(url, bool(index % 2))
        return cache.get(url)

    with ThreadPoolExecutor(max_workers=8) as pool:
        assert list(pool.map(store_and_read, range(32))) == [
            bool(index % 2) for index in range(32)
        ]


@pytest.fixture
def mock_checker(monkeypatch):
    monkeypatch.setenv("SAFE_BROWSING_API_KEY", "test-secret-key")
    original_client = httpx.Client

    def create(handler):
        transport = httpx.MockTransport(handler)
        monkeypatch.setattr(
            safe_browsing.httpx, "Client",
            lambda **kwargs: original_client(transport=transport, **kwargs),
        )
        return SafeBrowsingChecker()

    return create


@pytest.mark.parametrize(
    ("payload", "expected"),
    [({}, False), ({"matches": [VALID_MATCH]}, True),
     ({"matches": []}, False)],
)
def test_checker_verdict_and_request(mock_checker, payload, expected, caplog):
    def handler(request):
        assert request.method == "POST"
        assert str(request.url) == (
            "https://safebrowsing.googleapis.com/v4/threatMatches:find"
        )
        assert request.headers["x-goog-api-key"] == "test-secret-key"
        assert all(value == 5.0 for value in request.extensions["timeout"].values())
        body = json.loads(request.content)
        assert body["client"]["clientId"] == "cookiepatrol"
        assert body["threatInfo"] == {
            "threatTypes": ["MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE",
                            "POTENTIALLY_HARMFUL_APPLICATION"],
            "platformTypes": ["ANY_PLATFORM"],
            "threatEntryTypes": ["URL"],
            "threatEntries": [{"url": "https://example.com"}],
        }
        return httpx.Response(200, json=payload)

    with caplog.at_level(logging.DEBUG):
        assert mock_checker(handler).is_dangerous("https://example.com") is expected
    assert "test-secret-key" not in caplog.text


@pytest.mark.parametrize("status", [400, 401, 403, 429, 500, 503])
def test_checker_http_error(mock_checker, status, caplog):
    checker = mock_checker(lambda request: httpx.Response(status))
    with caplog.at_level(logging.DEBUG), pytest.raises(ThreatCheckUnavailable) as error:
        checker.is_dangerous("https://example.com")
    assert "test-secret-key" not in str(error.value)
    assert "test-secret-key" not in caplog.text
    assert error.value.__suppress_context__


@pytest.mark.parametrize("error_type", [httpx.ReadTimeout, httpx.ConnectError])
def test_checker_network_error(mock_checker, error_type):
    def handler(request):
        raise error_type("transport failed", request=request)

    with pytest.raises(ThreatCheckUnavailable):
        mock_checker(handler).is_dangerous("https://example.com")


@pytest.mark.parametrize("payload", [None, [], True, {"error": "bad"},
                                     {"matches": None}, {"matches": {}},
                                     {"matches": [None]}, {"matches": [{}]}])
def test_checker_invalid_response(mock_checker, payload):
    checker = mock_checker(lambda request: httpx.Response(200, json=payload))
    with pytest.raises(ThreatCheckUnavailable):
        checker.is_dangerous("https://example.com")


def test_checker_invalid_json(mock_checker):
    checker = mock_checker(lambda request: httpx.Response(200, content=b"not json"))
    with pytest.raises(ThreatCheckUnavailable):
        checker.is_dangerous("https://example.com")


@pytest.mark.parametrize("field", ["threatType", "platformType", "threatEntryType", "threat"])
def test_checker_rejects_missing_match_fields(mock_checker, field):
    match = {key: value for key, value in VALID_MATCH.items() if key != field}
    checker = mock_checker(lambda request: httpx.Response(200, json={"matches": [match]}))
    with pytest.raises(ThreatCheckUnavailable):
        checker.is_dangerous("https://example.com")


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("threatType", "UNKNOWN"), ("threatType", []),
        ("platformType", "PLATFORM_TYPE_UNSPECIFIED"), ("platformType", {}),
        ("threatEntryType", "EXECUTABLE"), ("threatEntryType", None),
        ("threat", None), ("threat", {}), ("threat", {"hash": "abc"}),
        ("threat", {"url": 123}), ("threat", {"url": "   "}),
        ("cacheDuration", 300), ("cacheDuration", "300"),
        ("cacheDuration", "-1s"), ("cacheDuration", "1.1234567890s"),
    ],
)
def test_checker_rejects_malformed_match_fields(mock_checker, field, value):
    match = {**VALID_MATCH, field: value}
    checker = mock_checker(lambda request: httpx.Response(200, json={"matches": [match]}))
    with pytest.raises(ThreatCheckUnavailable):
        checker.is_dangerous("https://example.com")


def test_checker_rejects_arbitrary_and_mixed_matches(mock_checker):
    checker = mock_checker(lambda request: httpx.Response(
        200, json={"matches": [VALID_MATCH, {"unexpected": True}]}
    ))
    with pytest.raises(ThreatCheckUnavailable):
        checker.is_dangerous("https://example.com")


@pytest.mark.parametrize("key", [None, "", "   "])
def test_checker_requires_key(monkeypatch, key):
    if key is None:
        monkeypatch.delenv("SAFE_BROWSING_API_KEY", raising=False)
    else:
        monkeypatch.setenv("SAFE_BROWSING_API_KEY", key)
    with pytest.raises(ThreatCheckUnavailable):
        SafeBrowsingChecker()
