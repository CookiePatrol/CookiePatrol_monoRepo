import pytest
from fastapi.testclient import TestClient

from app.application.check_url import CheckUrl
from app.domain.url_check import ThreatCheckUnavailable
from app.domain.verdict import Verdict, VerdictStatus
from app.infrastructure.adapters import SafeBrowsingChecker, SqliteVerdictCache
from app import infrastructure, presentation
from app.main import app
from app.presentation import get_check_url


class FakeChecker:
    def __init__(self, dangerous: bool = False, unavailable: bool = False):
        self.dangerous = dangerous
        self.unavailable = unavailable
        self.calls = 0

    def is_dangerous(self, url: str) -> bool:
        self.calls += 1
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


@pytest.mark.parametrize("url", ["example.com", "ftp://example.com", " " + "x" * 2049])
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
    monkeypatch.setattr(infrastructure, "SafeBrowsingChecker", lambda: checker)
    monkeypatch.setattr(infrastructure, "SqliteVerdictCache", lambda: cache)

    with TestClient(app) as test_client:
        response = test_client.post("/check", json={"url": "https://example.com"})

    assert response.status_code == 200
    assert response.json() == {"dangerous": True}
    assert checker.calls == 1


def test_default_dependency_runs_end_to_end_without_network(monkeypatch):
    calls = 0

    async def fake_provider_check(self, url: str):
        nonlocal calls
        calls += 1
        return Verdict(VerdictStatus.DANGEROUS), None

    from app.infrastructure.web_risk import WebRiskProvider

    monkeypatch.setattr(WebRiskProvider, "check", fake_provider_check)
    monkeypatch.setenv("CACHE_DB_PATH", ":memory:")
    monkeypatch.setattr(presentation, "_check_url", None)

    try:
        with TestClient(app) as test_client:
            first = test_client.post("/check", json={"url": "https://example.com"})
            second = test_client.post("/check", json={"url": "https://example.com"})
        assert first.status_code == second.status_code == 200
        assert first.json() == second.json() == {"dangerous": True}
        assert calls == 1
    finally:
        if presentation._check_url is not None:
            presentation._check_url.cache.close()


def test_safe_browsing_adapter_maps_verdict_and_provider_failure():
    class FakeProvider:
        def __init__(self, verdict=None, error=None):
            self.verdict = verdict
            self.error = error

        async def check(self, url: str):
            if self.error:
                raise self.error
            return self.verdict, None

    checker = SafeBrowsingChecker(FakeProvider(Verdict(VerdictStatus.DANGEROUS)))
    assert checker.is_dangerous("https://example.com") is True

    from app.infrastructure.web_risk import ProviderUnavailable

    unavailable = SafeBrowsingChecker(FakeProvider(error=ProviderUnavailable()))
    with pytest.raises(ThreatCheckUnavailable):
        unavailable.is_dangerous("https://example.com")


def test_sqlite_adapter_hashes_urls_and_caches_boolean_verdicts():
    cache = SqliteVerdictCache(":memory:")
    try:
        url = "https://example.com/?token=private"
        assert cache.get(url) is None
        cache.set(url, True)
        assert cache.get(url) is True
        row = cache.cache.connection.execute(
            "SELECT cache_key FROM verdict_cache"
        ).fetchone()
        assert row is not None
        assert row[0] != url
    finally:
        cache.close()
