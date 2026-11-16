import pytest
from evaluation import combine, leakage_audit, load_cases, metrics, run_pipeline


def test_metrics_with_hand_computed_confusion_matrix():
    rows = [dict(phishing=True, prediction="dangerous")] * 3 + [dict(phishing=False, prediction="dangerous")]
    rows += [dict(phishing=False, prediction="not_detected")] * 2 + [dict(phishing=True, prediction="not_detected")]
    rows += [dict(phishing=True, prediction="unknown")]
    result = metrics(rows)
    assert [result[k] for k in ("tp", "fp", "tn", "fn", "unknown")] == [3, 1, 2, 1, 1]
    assert result["precision"]["value"] == 3 / 4
    assert result["recall"]["value"] == 3 / 4
    assert result["f1"]["value"] == 6 / 8
    assert result["confusion_matrix"]["actual_phishing"] == {"dangerous": 3, "not_detected": 1}
    assert result["confusion_matrix"]["actual_normal"] == {"dangerous": 1, "not_detected": 2}
    assert result["false_positive_rate"]["value"] == 1 / 3
    assert result["coverage"]["value"] == 7 / 8
    assert result["recall_all_phishing"]["value"] == 3 / 5
    assert metrics([])["precision"]["value"] is None


@pytest.mark.parametrize("outcomes", [["timeout"], ["rate_limited"], ["unavailable"], ["not_detected", "timeout"]])
def test_local_warning_survives_provider_failures(outcomes):
    assert combine(True, outcomes) == "dangerous"
    assert combine(False, outcomes) == "unknown"


def test_dataset_metadata_and_split_audit():
    cases = load_cases()
    assert len(cases) >= 100
    assert len({case["id"] for case in cases}) == len(cases)
    for split in ("development", "holdout"):
        chosen = [c for c in cases if c["split"] == split]
        assert len(chosen) >= 50
        assert sum(c["phishing"] for c in chosen) >= 20
        assert all(c["rationale"] and c["source"] == "synthetic" for c in chosen)
    assert leakage_audit(cases)["exact_duplicates"] == 0
    assert leakage_audit(cases)["similar_pairs"] == []
    assert {pair["holdout"] for pair in leakage_audit(cases)["manual_similarity_findings"]} == {"EN06", "EN11", "EN12", "EN24"}
    assert leakage_audit(cases)["independent_validation"].startswith("not_established")


def test_live_evaluation_without_keys_stops_before_any_request(monkeypatch):
    monkeypatch.setenv("SAFE_BROWSING_API_KEY", "")
    monkeypatch.setenv("OPENAI_API_KEY", "")
    with pytest.raises(ValueError, match="not run"):
        run_pipeline([], {}, "live")
