import httpx
from fastapi.testclient import TestClient

from app import presentation
from app.infrastructure import safe_browsing, SafeBrowsingChecker
from app.main import app
from app.presentation import get_check_url
from app.application.check_url import CheckUrl


def test_google_ttl_flows_to_details_and_counts_down_without_renewing(monkeypatch, tmp_path):
    monkeypatch.setenv("SAFE_BROWSING_API_KEY", "fake-google-key")
    monkeypatch.setenv("CACHE_DB_PATH", str(tmp_path / "cache.db"))
    monkeypatch.setattr(presentation, "_check_url", None)
    now = [1000.0]
    monkeypatch.setattr(safe_browsing.time, "monotonic", lambda: now[0])
    real_client = httpx.Client
    requests = []
    def respond(request):
        requests.append(request)
        return httpx.Response(200, json={"matches": [{
            "threatType": "SOCIAL_ENGINEERING", "platformType": "ANY_PLATFORM", "threatEntryType": "URL",
            "threat": {"url": "https://example.com/"}, "cacheDuration": "2s",
        }]})
    monkeypatch.setattr(safe_browsing.httpx, "Client", lambda **kwargs:
                        real_client(transport=httpx.MockTransport(respond), **kwargs))
    with TestClient(app) as client:
        assert client.post("/check/details", json={"url": "https://example.com/"}).json()["google_cache_ttl_seconds"] == 2
        now[0] += 1.5
        assert client.post("/check/details", json={"url": "https://example.com/"}).json()["google_cache_ttl_seconds"] == 0.5
        assert len(requests) == 1
        now[0] += 0.5
        assert client.post("/check/details", json={"url": "https://example.com/"}).json()["google_cache_ttl_seconds"] == 2
        assert len(requests) == 2


def test_zero_duration_threat_still_warns_but_has_no_cache_ttl(monkeypatch):
    checker = SafeBrowsingChecker()
    monkeypatch.setattr(checker, "is_dangerous", lambda url: True)
    class Cache:
        def get(self, url):
            raise AssertionError("Google threat must not consult AI cache")
    result = CheckUrl(checker, Cache(), primary=checker, primary_cache_ttl=checker.cache_ttl_remaining).assess("https://example.com/")
    assert result.dangerous is True
    assert result.google_cache_ttl_seconds == 0
