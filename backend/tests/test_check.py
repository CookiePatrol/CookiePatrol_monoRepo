import pytest
from fastapi.testclient import TestClient

from app.application.check_url import CheckUrl
from app.domain.url_check import ThreatCheckUnavailable
from app import infrastructure, presentation
from app.main import app
from app.presentation import close_check_url, get_check_url


class FakeChecker:
    cache_namespace = "test"

    def __init__(self, dangerous: bool = False, unavailable: bool = False):
        self.dangerous = dangerous
        self.unavailable = unavailable
        self.calls = 0
        self.last_url: str | None = None

    def is_dangerous(self, url: str) -> bool:
        self.calls += 1
        self.last_url = url
        if self.unavailable:
            raise ThreatCheckUnavailable()
        return self.dangerous


class FakeCache:
    def __init__(self):
        self.values: dict[str, bool] = {}

    def get(self, url: str) -> bool | None:
        return self.values.get(url)

    def set(self, url: str, dangerous: bool) -> None:
        self.values[url] = dangerous


@pytest.fixture
def client():
    checker = FakeChecker(dangerous=True)
    cache = FakeCache()
    app.dependency_overrides[get_check_url] = lambda: CheckUrl(checker, cache)
    with TestClient(app) as test_client:
        yield test_client, checker, cache
    app.dependency_overrides.clear()


def test_dangerous_response(client):
    response = client[0].post("/check", json={"url": " https://example.com "})
    assert response.status_code == 200
    assert response.json() == {"dangerous": True}
    assert client[1].last_url == "https://example.com"


def test_safe_response():
    checker = FakeChecker(dangerous=False)
    app.dependency_overrides[get_check_url] = lambda: CheckUrl(checker, FakeCache())
    try:
        response = TestClient(app).post("/check", json={"url": "https://example.com"})
    finally:
        app.dependency_overrides.clear()
    assert response.status_code == 200
    assert response.json() == {"dangerous": False}


def test_second_request_uses_cache(client):
    test_client, checker, _ = client
    url = "https://example.com"
    assert test_client.post("/check", json={"url": url}).status_code == 200
    assert test_client.post("/check", json={"url": url}).status_code == 200
    assert checker.calls == 1


@pytest.mark.parametrize(
    "url",
    [
        "example.com",
        "ftp://example.com",
        "http://",
        "https://?query=value",
        "https://example.com:70000/path",
        "https://user:password@example.com/",
        "https://example.com/a b",
        " " + "x" * 2049,
    ],
)
def test_invalid_url_returns_422(client, url):
    response = client[0].post("/check", json={"url": url})
    assert response.status_code == 422


def test_unavailable_returns_503_without_caching():
    checker = FakeChecker(unavailable=True)
    cache = FakeCache()
    app.dependency_overrides[get_check_url] = lambda: CheckUrl(checker, cache)
    try:
        response = TestClient(app).post("/check", json={"url": "https://example.com"})
    finally:
        app.dependency_overrides.clear()
    assert response.status_code == 503
    assert cache.values == {}


def test_default_dependency_wiring_uses_infrastructure(monkeypatch):
    checker = FakeChecker(dangerous=True)
    cache = FakeCache()
    monkeypatch.setattr(presentation, "_check_url", None)
    monkeypatch.setattr(infrastructure, "OpenAIThreatChecker", lambda: checker)
    monkeypatch.setattr(infrastructure, "SafeBrowsingChecker", lambda: FakeChecker())
    monkeypatch.setattr(infrastructure, "SqliteVerdictCache", lambda **kwargs: cache)

    with TestClient(app) as test_client:
        response = test_client.post("/check", json={"url": "https://example.com"})

    assert response.status_code == 200
    assert response.json() == {"dangerous": True}
    assert checker.calls == 1


def test_missing_api_key_returns_503(monkeypatch):
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    monkeypatch.delenv("SAFE_BROWSING_API_KEY", raising=False)
    monkeypatch.setattr(presentation, "_check_url", None)

    with TestClient(app) as test_client:
        response = test_client.post("/check", json={"url": "https://example.com"})

    assert response.status_code == 503


def test_uppercase_http_scheme_is_valid(client):
    response = client[0].post("/check", json={"url": "HTTPS://Example.com/path"})
    assert response.status_code == 200
    assert client[1].last_url == "HTTPS://Example.com/path"


def test_close_check_url_closes_cache(monkeypatch):
    class ClosableCache(FakeCache):
        closed = False

        def close(self):
            self.closed = True

    cache = ClosableCache()
    monkeypatch.setattr(presentation, "_check_url", CheckUrl(FakeChecker(), cache))

    close_check_url()

    assert cache.closed
    assert presentation._check_url is None
