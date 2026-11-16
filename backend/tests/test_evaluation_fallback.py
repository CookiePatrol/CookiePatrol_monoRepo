from evaluation import run_pipeline
import pytest


@pytest.mark.parametrize("failure", ["timeout", "unavailable", "rate_limited"])
@pytest.mark.parametrize("ai_result", ["dangerous", "not_detected"])
def test_google_failure_then_successful_ai_matches_production_fallback(monkeypatch, failure, ai_result):
    monkeypatch.setattr("evaluation.mock_provider", lambda case, provider, fixtures:
                        failure if provider == "google" else ai_result)
    cases = [dict(id="DEV", phishing=False, split="development"), dict(id="EVAL", phishing=False, split="holdout")]
    local = {case["id"]: dict(score=2, signals=["lure_words"], urls=["https://example.com/"], local_elapsed_ns_median=1)
             for case in cases}
    report = run_pipeline(cases, local, "mock")
    assert report["rows"]["rules_google"][0]["prediction"] == "unknown"
    assert report["rows"]["rules_google_ai"][0]["prediction"] == ai_result
    assert report["actual_provider_attempts"] == {"google": 0, "ai": 0}
