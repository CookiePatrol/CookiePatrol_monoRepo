import json

import httpx
import pytest
from fastapi.testclient import TestClient

from app.application.check_url import CheckUrl
from app.domain.url_check import PROMPT_VERSION, ThreatCheckUnavailable, UrlAssessment
from app.infrastructure import OpenAIThreatChecker
from app.infrastructure import openai_checker
from app.main import app
from app.presentation import get_check_url


class Cache:
    def __init__(self):
        self.values = {}

    def get(self, url):
        return self.values.get(url)

    def set(self, url, dangerous):
        self.values[url] = dangerous


class Checker:
    def __init__(self, dangerous):
        self.dangerous = dangerous
        self.calls = 0

    def is_dangerous(self, url):
        self.calls += 1
        return self.dangerous

    def assess(self, url):
        self.calls += 1
        return UrlAssessment(self.dangerous, "ai", ("brand_impersonation",) if self.dangerous else ())


def test_details_exposes_source_and_codes_without_url_or_fabricating_cached_evidence():
    checker, primary = Checker(True), Checker(False)
    service = CheckUrl(checker, Cache(), primary, detailed_checker=checker)
    app.dependency_overrides[get_check_url] = lambda: service
    url = "https://bank.example.attacker.example/login?token=PRIVATE"
    try:
        with TestClient(app) as client:
            first = client.post("/check/details", json={"url": url})
            second = client.post("/check/details", json={"url": url})
            legacy = client.post("/check", json={"url": url})
        assert first.status_code == 200
        assert first.json()["source"] == "ai"
        assert first.json()["indicators"] == ["brand_impersonation"]
        assert first.json()["prompt_version"] == PROMPT_VERSION
        assert "AI" in first.json()["reason"]
        assert "PRIVATE" not in first.text and url not in first.text
        assert second.json()["source"] == "ai_cache"
        assert second.json()["indicators"] == []
        assert "최근" in second.json()["reason"]
        assert legacy.json() == {"dangerous": True}
        assert checker.calls == 1 and primary.calls == 3
    finally:
        app.dependency_overrides.clear()


def test_google_match_details_skips_ai_even_with_cached_negative():
    cache = Cache()
    cache.set("https://example.com", False)
    checker = Checker(False)
    result = CheckUrl(checker, cache, Checker(True), detailed_checker=checker).assess("https://example.com")
    assert result == UrlAssessment(True, "safe_browsing")
    assert "Google" in result.reason
    assert checker.calls == 0


@pytest.mark.parametrize("payload", [
    {"verdict": "dangerous", "indicators": []},
    {"verdict": "not_detected", "indicators": ["credential_lure"]},
    {"verdict": "dangerous", "indicators": ["invented"]},
    {"verdict": "dangerous", "indicators": ["deceptive_host", "deceptive_host"]},
    {"verdict": "dangerous", "indicators": [True]},
    {"verdict": "dangerous", "indicators": "brand_impersonation"},
    {"verdict": "dangerous"},
    {"verdict": "unknown", "indicators": []},
])
def test_invalid_evidence_never_becomes_safe_or_cached(monkeypatch, payload):
    monkeypatch.setenv("OPENAI_API_KEY", "test-key")
    original = httpx.Client
    response = {"status": "completed", "output": [{
        "type": "message", "status": "completed",
        "content": [{"type": "output_text", "text": json.dumps(payload)}],
    }]}
    monkeypatch.setattr(openai_checker.httpx, "Client", lambda **kwargs:
        original(transport=httpx.MockTransport(lambda request: httpx.Response(200, json=response)), **kwargs))
    checker, cache = OpenAIThreatChecker(), Cache()
    service = CheckUrl(checker, cache, detailed_checker=checker)
    with pytest.raises(ThreatCheckUnavailable):
        service.assess("https://example.com")
    assert cache.values == {}


def test_details_and_legacy_share_rate_limit(monkeypatch):
    from app import presentation
    monkeypatch.setattr(presentation, "_check_rate_limiter", presentation.ClientRateLimiter(limit=1))
    app.dependency_overrides[get_check_url] = lambda: CheckUrl(Checker(False), Cache())
    try:
        with TestClient(app) as client:
            assert client.post("/check/details", json={"url": "https://example.com"}).status_code == 200
            response = client.post("/check", json={"url": "https://example.com"})
        assert response.status_code == 429
        assert int(response.headers["Retry-After"]) >= 1
    finally:
        app.dependency_overrides.clear()


def test_details_failure_is_503_and_never_echoes_input():
    class Unavailable:
        def assess(self, url):
            raise ThreatCheckUnavailable(reason="timeout")
    app.dependency_overrides[get_check_url] = lambda: CheckUrl(Checker(False), Cache(), detailed_checker=Unavailable())
    try:
        with TestClient(app) as client:
            response = client.post("/check/details", json={"url": "https://example.com/?token=PRIVATE"})
        assert response.status_code == 503
        assert "PRIVATE" not in response.text
    finally:
        app.dependency_overrides.clear()
