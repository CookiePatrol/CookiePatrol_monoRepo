import json

import httpx
import pytest
from fastapi.testclient import TestClient

from app import presentation
from app.application.check_url import CheckUrl
from app.domain.url_check import ThreatCheckUnavailable
from app.infrastructure import SafeBrowsingChecker
from app.infrastructure import safe_browsing
from app.main import app


class Checker:
    def __init__(self, verdict, name, calls):
        self.verdict, self.name, self.calls = verdict, name, calls

    def is_dangerous(self, url):
        self.calls.append(self.name)
        if self.verdict is None:
            raise ThreatCheckUnavailable()
        return self.verdict


class Cache:
    def __init__(self):
        self.values = {}

    def get(self, url):
        return self.values.get(url)

    def set(self, url, dangerous):
        self.values[url] = dangerous


@pytest.mark.parametrize("primary,secondary,expected", [
    (True, True, True), (True, False, True), (True, None, True),
    (False, True, True), (False, False, False),
    (None, True, True), (None, False, False),
])
def test_google_first_and_fallback(primary, secondary, expected):
    calls, cache = [], Cache()
    service = CheckUrl(Checker(secondary, "ai", calls), cache, Checker(primary, "google", calls))
    assert service("https://example.com") is expected
    assert calls == (["google"] if primary else ["google", "ai"])
    assert cache.values == ({} if primary else {"https://example.com": expected})


@pytest.mark.parametrize("primary", [False, None])
def test_no_ai_assessment_is_unavailable_and_not_cached(primary):
    calls, cache = [], Cache()
    service = CheckUrl(Checker(None, "ai", calls), cache, Checker(primary, "google", calls))
    with pytest.raises(ThreatCheckUnavailable):
        service("https://example.com")
    assert calls == ["google", "ai"]
    assert cache.values == {}


def test_google_positive_overrides_cached_ai_negative():
    calls, cache = [], Cache()
    cache.set("https://example.com", False)
    service = CheckUrl(Checker(False, "ai", calls), cache, Checker(True, "google", calls))
    assert service("https://example.com") is True
    assert calls == ["google"]


def test_ai_cache_is_used_only_after_google():
    calls, cache = [], Cache()
    service = CheckUrl(Checker(True, "ai", calls), cache, Checker(False, "google", calls))
    assert service("https://example.com") is True
    assert service("https://example.com") is True
    assert calls == ["google", "ai", "google"]


VALID_MATCH = {
    "threatType": "SOCIAL_ENGINEERING", "platformType": "ANY_PLATFORM",
    "threatEntryType": "URL", "threat": {"url": "https://example.com"},
    "cacheDuration": "300.000s",
}


@pytest.fixture
def mock_google(monkeypatch):
    monkeypatch.setenv("SAFE_BROWSING_API_KEY", "google-test-secret")
    real_client = httpx.Client

    def create(handler):
        monkeypatch.setattr(safe_browsing.httpx, "Client", lambda **kwargs:
                            real_client(transport=httpx.MockTransport(handler), **kwargs))
        return SafeBrowsingChecker()
    return create


def test_google_request_and_cache_duration(mock_google, monkeypatch, caplog):
    calls = []
    now = [1000.0]
    monkeypatch.setattr(safe_browsing.time, "monotonic", lambda: now[0])

    def handler(request):
        calls.append(request)
        assert request.url.query == b""
        assert request.headers["x-goog-api-key"] == "google-test-secret"
        assert str(request.url) == "https://safebrowsing.googleapis.com/v4/threatMatches:find"
        assert json.loads(request.content)["threatInfo"]["threatEntries"] == [{"url": "https://example.com"}]
        assert all(value == 5.0 for value in request.extensions["timeout"].values())
        return httpx.Response(200, json={"matches": [VALID_MATCH]})

    checker = mock_google(handler)
    assert checker.is_dangerous("https://example.com") is True
    now[0] += 299
    assert checker.is_dangerous("https://example.com") is True
    assert len(calls) == 1
    now[0] += 1
    assert checker.is_dangerous("https://example.com") is True
    assert len(calls) == 2
    assert "google-test-secret" not in caplog.text
    assert "https://example.com" not in checker._positive_cache


@pytest.mark.parametrize("payload", [{}, {"matches": []}])
def test_google_no_match(mock_google, payload):
    assert mock_google(lambda request: httpx.Response(200, json=payload)).is_dangerous("https://example.com") is False


@pytest.mark.parametrize("payload", [None, [], True, {"error": "bad"}, {"matches": None},
    {"matches": [{}]}, {"matches": [None]},
    {"matches": [{**VALID_MATCH, "cacheDuration": "-1s"}]},
    {"matches": [{**VALID_MATCH, "threat": {}}]},
    {"matches": [{**VALID_MATCH, "threatType": "UNKNOWN"}]},
])
def test_google_invalid_response_is_unavailable(mock_google, payload):
    with pytest.raises(ThreatCheckUnavailable):
        mock_google(lambda request: httpx.Response(200, json=payload)).is_dangerous("https://example.com")


@pytest.mark.parametrize("status", [400, 401, 403, 429, 500, 503])
def test_google_error(mock_google, status):
    with pytest.raises(ThreatCheckUnavailable):
        mock_google(lambda request: httpx.Response(status)).is_dangerous("https://example.com")


def test_google_timeout(mock_google):
    def handler(request):
        raise httpx.ReadTimeout("timeout", request=request)
    with pytest.raises(ThreatCheckUnavailable):
        mock_google(handler).is_dangerous("https://example.com")


def test_google_missing_key(monkeypatch):
    monkeypatch.delenv("SAFE_BROWSING_API_KEY", raising=False)
    with pytest.raises(ThreatCheckUnavailable):
        SafeBrowsingChecker().is_dangerous("https://example.com")


@pytest.mark.parametrize("google_payload,expected_hosts,expected", [
    ({"matches": [VALID_MATCH]}, ["safebrowsing.googleapis.com"], True),
    ({}, ["safebrowsing.googleapis.com", "api.openai.com"], False),
])
def test_default_api_full_provider_chain(monkeypatch, tmp_path, google_payload, expected_hosts, expected):
    hosts = []
    monkeypatch.setenv("SAFE_BROWSING_API_KEY", "google-test-secret")
    monkeypatch.setenv("OPENAI_API_KEY", "openai-test-secret")
    monkeypatch.setenv("CACHE_DB_PATH", str(tmp_path / "cache.db"))
    monkeypatch.setattr(presentation, "_check_url", None)
    real_client = httpx.Client

    def handler(request):
        hosts.append(request.url.host)
        if request.url.host == "safebrowsing.googleapis.com":
            return httpx.Response(200, json=google_payload)
        return httpx.Response(200, json={"status": "completed", "output": [{
            "type": "message", "status": "completed",
            "content": [{"type": "output_text", "text": '{"verdict":"not_detected"}'}],
        }]})

    monkeypatch.setattr(safe_browsing.httpx, "Client", lambda **kwargs:
                        real_client(transport=httpx.MockTransport(handler), **kwargs))
    with TestClient(app) as client:
        response = client.post("/check", json={"url": "https://example.com"})
    assert response.status_code == 200
    assert response.json() == {"dangerous": expected}
    assert hosts == expected_hosts


def test_google_match_works_without_openai_key(monkeypatch, tmp_path):
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    monkeypatch.setenv("CACHE_DB_PATH", str(tmp_path / "cache.db"))
    monkeypatch.setattr(presentation, "_check_url", None)
    monkeypatch.setattr(SafeBrowsingChecker, "is_dangerous", lambda self, url: True)
    with TestClient(app) as client:
        response = client.post("/check", json={"url": "https://example.com"})
    assert response.status_code == 200
    assert response.json() == {"dangerous": True}
