"""Reproducible synthetic-data evaluation. Default mock mode NEVER calls a provider."""
import argparse
import hashlib
import json
import os
import re
import statistics
import time
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "frontend/app/src/debug/assets"
WEIGHTS = dict(zip(
    ("short_url", "http", "ip_host", "userinfo", "idn", "apk", "lure_words", "family", "credential", "transfer", "suspension", "identity"),
    (2, 1, 3, 3, 2, 3, 2, 2, 2, 2, 2, 3), strict=True))


def load_cases():
    cases = []
    for name in ("evaluation-cases.txt", "holdout-cases.txt"):
        for line in (DATA / name).read_text(encoding="utf-8-sig").splitlines():
            if not line.strip() or line.startswith("#"):
                continue
            parts = line.split("|", 7)
            if len(parts) != 8:
                raise ValueError("Invalid dataset row")
            key, label, category, split, rationale, source, evaluation_type, text = parts
            if label not in ("normal", "phishing") or split not in ("development", "holdout") or source != "synthetic":
                raise ValueError("Invalid dataset metadata")
            cases.append(dict(id=key, phishing=label == "phishing", category=category, split=split,
                              rationale=rationale, source=source, evaluation_type=evaluation_type, text=text))
    if len({case["id"] for case in cases}) != len(cases):
        raise ValueError("Duplicate case id")
    return cases


def metrics(rows):
    counts = Counter(tp=0, fp=0, tn=0, fn=0, unknown=0, unknown_phishing=0, unknown_normal=0)
    for row in rows:
        label, prediction = row["phishing"], row["prediction"]
        if prediction not in ("dangerous", "not_detected", "unknown"):
            raise ValueError("Invalid prediction")
        if prediction == "unknown":
            counts["unknown"] += 1
            counts["unknown_phishing" if label else "unknown_normal"] += 1
        else:
            positive = prediction == "dangerous"
            counts["tp" if label and positive else "fp" if positive else "fn" if label else "tn"] += 1
    tp, fp, tn, fn = (counts[k] for k in ("tp", "fp", "tn", "fn"))
    def ratio(n, d):
        return {"value": n / d if d else None, "numerator": n, "denominator": d}
    return dict(counts, confusion_matrix={"actual_phishing": {"dangerous": tp, "not_detected": fn},
                                        "actual_normal": {"dangerous": fp, "not_detected": tn}},
                precision=ratio(tp, tp + fp), recall=ratio(tp, tp + fn),
                f1=ratio(2 * tp, 2 * tp + fp + fn), false_positive_rate=ratio(fp, fp + tn),
                false_negative_rate=ratio(fn, fn + tp), coverage=ratio(tp + fp + tn + fn, len(rows)),
                recall_all_phishing=ratio(tp, tp + fn + counts["unknown_phishing"]))


def leakage_audit(cases):
    def grams(text):
        text = re.sub(r"https?://\S+|\b(?:[a-z0-9-]+\.)+[a-z]{2,}/\S*", " URL ", text.lower())
        text = re.sub(r"\d+", "N", text)
        text = re.sub(r"[^a-z가-힣N]+", "", text)
        return {text[i:i + 4] for i in range(max(1, len(text) - 3))}
    development = [(case, grams(case["text"])) for case in cases if case["split"] == "development"]
    pairs = []
    for case in (c for c in cases if c["split"] == "holdout"):
        current = grams(case["text"])
        for dev, old in development:
            score = len(current & old) / len(current | old) if current | old else 1
            if score >= 0.45:
                pairs.append(dict(development=dev["id"], holdout=case["id"], jaccard_4gram=score))
    # A lexical threshold missed these paraphrased situations during the second review.
    # Keep them visible instead of treating zero lexical matches as independent validation.
    reviewed = [
        ("N14", "EN11", "가족 사칭 주의·인증번호 전달 금지 교육의 변형"),
        ("N15", "EN12", "주민번호 입력·기재 금지 교육의 변형"),
        ("N14", "EN24", "비밀번호·인증번호 제공 금지 교육이라는 유사 상황"),
        ("N26", "EN06", "정상 내부·실습 IP 서버 안내라는 유사 상황"),
    ]
    by_id = {case["id"]: case for case in cases}
    manual = [dict(development=dev, holdout=holdout, reason=reason,
                   development_text=by_id[dev]["text"], holdout_text=by_id[holdout]["text"])
              for dev, holdout, reason in reviewed if dev in by_id and holdout in by_id]
    return {"method": "URL/number normalized character 4-gram Jaccard >= 0.45; heuristic, not proof of independence",
            "exact_duplicates": len(cases) - len({c["text"] for c in cases}), "similar_pairs": pairs,
            "manual_similarity_findings": manual,
            "independent_validation": "not_established; related scenarios found; independent team review needed"}


def mock_provider(case_id, stage, fixtures):
    return fixtures.get(case_id, {}).get(stage, "not_detected")


def combine(local_warned, outcomes):
    if local_warned or "dangerous" in outcomes:
        return "dangerous"
    if any(value not in ("not_detected", "dangerous") for value in outcomes):
        return "unknown"
    return "not_detected"


def run_pipeline(cases, local, mode, max_attempts=20):
    fixtures = json.loads((ROOT / "backend/evaluation-mocks.json").read_text(encoding="utf-8"))
    stages = {"rules": [], "rules_google": [], "rules_google_ai": []}
    providers = {}
    actual_calls = Counter(google=0, ai=0)
    live_cache = {}
    if mode == "live":
        from dotenv import load_dotenv
        from app.infrastructure import OpenAIThreatChecker, SafeBrowsingChecker
        load_dotenv(Path(__file__).parent / ".env", override=False)
        if not all(os.environ.get(key, "").strip() for key in ("SAFE_BROWSING_API_KEY", "OPENAI_API_KEY")):
            raise ValueError("Actual API comparison not run: configure both required API keys locally")
        providers = {"google": SafeBrowsingChecker(), "ai": OpenAIThreatChecker()}

    def lookup(case, provider, url):
        if mode == "mock":
            return mock_provider(case["id"], provider, fixtures), 0.0
        key = (provider, url)
        if provider == "google" and providers["google"].cache_ttl_remaining(url) > 0:
            return "dangerous", 0.0
        if provider == "ai" and key in live_cache:
            value, expires_at = live_cache[key]
            if time.monotonic() < expires_at:
                return value, 0.0
        if sum(actual_calls.values()) >= max_attempts:
            return "not_run_budget", 0.0
        from app.domain.url_check import ThreatCheckUnavailable
        actual_calls[provider] += 1
        started = time.perf_counter()
        try:
            dangerous = providers[provider].is_dangerous(url)
            value = "dangerous" if dangerous else "not_detected"
        except ThreatCheckUnavailable as exc:
            value = exc.reason
        # No exception messages or provider response bodies are written to reports.
        elapsed = (time.perf_counter() - started) * 1000
        if provider == "ai" and value in ("dangerous", "not_detected"):
            live_cache[key] = (value, time.monotonic() + 24 * 60 * 60)
        return value, elapsed

    for case in cases:
        result = local[case["id"]]
        local_warned = result["score"] >= 3
        candidate = result["score"] >= 2 and bool(result["urls"])
        google, ai, resolved = [], [], []
        google_ms = ai_ms = 0.0
        google_calls = ai_calls = 0
        if candidate:
            for url in result["urls"][:10]:
                g, elapsed = lookup(case, "google", url)
                google.append(g); google_ms += elapsed; google_calls += 1
                if g != "dangerous":
                    a, elapsed = lookup(case, "ai", url)
                    ai.append(a); ai_ms += elapsed; ai_calls += 1
                    # The application uses a completed AI assessment after Google failure.
                    # Keep Google's failure in diagnostics without overriding that result.
                    resolved.append(a)
                else:
                    resolved.append(g)
        common = dict(id=case["id"], phishing=case["phishing"], split=case["split"],
                      score=result["score"], signals=result["signals"], candidate=candidate)
        local_ms = result["local_elapsed_ns_median"] / 1_000_000
        for stage, outcomes, diagnostics, calls, elapsed, sent in (
            ("rules", [], [], {"google": 0, "ai": 0}, local_ms, []),
            ("rules_google", google, google, {"google": google_calls, "ai": 0}, local_ms + google_ms, ["candidate full URL -> Google"] if candidate else []),
            ("rules_google_ai", resolved, google + ai, {"google": google_calls, "ai": ai_calls}, local_ms + google_ms + ai_ms,
             (["candidate full URL -> Google"] if candidate else []) + (["candidate full URL -> OpenAI"] if ai_calls else [])),
        ):
            prediction = combine(local_warned, outcomes)
            stages[stage].append(dict(common, prediction=prediction, resolved_url_outcomes=outcomes, provider_outcomes=diagnostics,
                                      logical_calls=calls, elapsed_ms=elapsed, additional_data=sent))
    summaries = {}
    for stage, rows in stages.items():
        summaries[stage] = {}
        for split in ("development", "holdout"):
            chosen = [r for r in rows if r["split"] == split]
            summaries[stage][split] = dict(metrics(chosen),
                fp_ids=[r["id"] for r in chosen if not r["phishing"] and r["prediction"] == "dangerous"],
                fn_ids=[r["id"] for r in chosen if r["phishing"] and r["prediction"] == "not_detected"],
                unknown_ids=[r["id"] for r in chosen if r["prediction"] == "unknown"],
                provider_statuses=dict(Counter(s for r in chosen for s in r["provider_outcomes"])),
                mean_elapsed_ms=statistics.mean(r["elapsed_ms"] for r in chosen),
                logical_calls={p: sum(r["logical_calls"][p] for r in chosen) for p in ("google", "ai")})
    return dict(mode=mode, actual_provider_attempts=dict(actual_calls),
                model=providers["ai"].model if mode == "live" else None,
                latency_scope="JVM local median + provider wall time; mock provider latency=0; not device/network performance",
                summaries=summaries, rows=stages)


def threshold_comparison(cases, local):
    output = []
    development = [c for c in cases if c["split"] == "development"]
    for transfer_weight in (2, 3):
        for warning in (2, 3, 4):
            for lookup in (1, 2, 3):
                rows, candidates = [], 0
                for case in development:
                    result = local[case["id"]]
                    score = sum((transfer_weight if code == "transfer" else WEIGHTS[code]) for code in result["signals"])
                    rows.append(dict(phishing=case["phishing"], prediction="dangerous" if score >= warning else "not_detected"))
                    candidates += bool(result["urls"]) and score >= lookup
                output.append(dict(warning_threshold=warning, lookup_threshold=lookup, transfer_weight=transfer_weight,
                                   candidates=candidates, **metrics(rows)))
    return output


def render_report(report):
    lines = ["# 합성 데이터 평가 실행 결과", "", f"실행 모드: **{'모의 시험 (mock)' if report['pipeline']['mode'] == 'mock' else '실제 API 호출 (live)'}**. 실기기 결과가 아닙니다.",
             "mock은 고정 응답으로 파이프라인 동작을 검증합니다. AI 모델의 탐지 성능을 측정한 것이 아닙니다.",
             "", "정답은 팀을 위해 작성한 합성 시나리오이며, 주소의 실제 악성 판정을 뜻하지 않습니다.",
             "동일 작성자의 합성 문장이며 개발/평가 사이에 의미가 유사한 교육 문구가 있습니다. 독립 성능 검증은 미완료입니다.", "",
             "| 구성 | 집단 | TP | FP | TN | FN | 판단불가 | 정밀도 | 재현율 | F1 | 오탐률 |", "|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|"]
    def value(metric):
        return "계산 불가" if metric["value"] is None else f"{100 * metric['value']:.2f}% ({metric['numerator']}/{metric['denominator']})"
    for stage, splits in report["pipeline"]["summaries"].items():
        for split, m in splits.items():
            lines.append(f"| {stage} | {split} | {m['tp']} | {m['fp']} | {m['tn']} | {m['fn']} | {m['unknown']} | " +
                         " | ".join(value(m[k]) for k in ("precision", "recall", "f1", "false_positive_rate")) + " |")
    lines += ["", "정밀도=TP/(TP+FP), 재현율=TP/(TP+FN), F1=2TP/(2TP+FP+FN), 오탐률=FP/(FP+TN).",
              "주 혼동행렬은 판단불가를 제외합니다. JSON의 coverage와 recall_all_phishing(TP/전체 위험 수)을 함께 확인하세요.",
              "", "| 변경 | 집단 | TP | FP | TN | FN |", "|---|---|---:|---:|---:|---:|"]
    for split, m in report["baseline"].items():
        lines.append(f"| 원본 70923b4 규칙 | {split} | {m['tp']} | {m['fp']} | {m['tn']} | {m['fn']} |")
    lines += ["", "## 지연·호출·전송", "", "| 구성 | 집단 | 평균 ms | Google 논리 조회 | AI 논리 조회 |", "|---|---|---:|---:|---:|"]
    for stage, splits in report["pipeline"]["summaries"].items():
        for split, m in splits.items():
            lines.append(f"| {stage} | {split} | {m['mean_elapsed_ms']:.3f} | {m['logical_calls']['google']} | {m['logical_calls']['ai']} |")
    lines += ["", f"실제 외부 호출 시도: {report['pipeline']['actual_provider_attempts']}", report["pipeline"]["latency_scope"],
              "외부 전송은 후보 URL 전체입니다. 원문 문장·알림 제목·인증번호는 전송하지 않습니다. URL 속 값은 예외이므로 주의가 필요합니다.",
              "", "## 오탐·미탐·판단불가", ""]
    for stage, splits in report["pipeline"]["summaries"].items():
        for split, m in splits.items():
            lines.append(f"- {stage}/{split}: FP={m['fp_ids']}, FN={m['fn_ids']}, unknown={m['unknown_ids']}; 상태={m['provider_statuses']}")
    stages = report["pipeline"]["rows"]
    lines += ["", "## AI를 더했을 때 바뀐 사례", ""]
    for before, after in zip(stages["rules_google"], stages["rules_google_ai"], strict=True):
        if before["prediction"] != after["prediction"]:
            correct = after["prediction"] == ("dangerous" if after["phishing"] else "not_detected")
            lines.append(f"- {after['id']}: {before['prediction']} → {after['prediction']} ({'개선' if correct else '악화 또는 판단불가'}; {report['pipeline']['mode']})")
    lines += ["", "## 사례별 결과", "", "입력·정답 근거·합성 표시·평가 유형은 데이터 원본을 참고하세요.",
              "", "| ID | 집단 | 정답 | 원본 점수 | 수정 점수 | 신호 | 후보 | 규칙 | +Google | +AI |", "|---|---|---|---:|---:|---|---|---|---|---|"]
    local = report["local_rows"]
    for a, b, c in zip(stages["rules"], stages["rules_google"], stages["rules_google_ai"], strict=True):
        lines.append(f"| {a['id']} | {a['split']} | {a['phishing']} | {local[a['id']]['baseline_score']} | {a['score']} | {', '.join(a['signals'])} | {a['candidate']} | {a['prediction']} | {b['prediction']} | {c['prediction']} |")
    lines += ["", "## 분리 검토", "", f"정확히 같은 원문 수: {report['leakage']['exact_duplicates']}; 유사 후보: {report['leakage']['similar_pairs']}",
              report["leakage"]["method"], "", "개발용 후보 가중치·임계값 비교는 아래 표와 JSON의 threshold_development에 기록합니다. 평가용 문장을 보고 선택하지 않습니다."]
    for finding in report["leakage"]["manual_similarity_findings"]:
        lines.append(f"- 별도 의미 검토: {finding['development']} ↔ {finding['holdout']}: {finding['reason']}")
    if "similarity_sensitivity" in report:
        sensitivity = report["similarity_sensitivity"]
        lines += ["", "### 유사 상황 제외 민감도 분석", "",
                  f"사후 의미 검토로 {sensitivity['excluded_ids']}를 제외한 {sensitivity['case_count']}건입니다. 최종 독립 성능이나 학습에 사용할 결과가 아닙니다.",
                  "| 규칙 | TP | FP | TN | FN |", "|---|---:|---:|---:|---:|"]
        for label in ("baseline", "current"):
            m = sensitivity[label]
            lines.append(f"| {label} | {m['tp']} | {m['fp']} | {m['tn']} | {m['fn']} |")
        lines.append("이 제외에는 어려운 정상 사례도 빠지므로 남은 사례의 높은 정밀도를 개선 증거로 제시하지 마세요. 새 독립 평가용 자료가 필요합니다.")
    lines += ["", "## 개발용 설정 비교", "", "| 경고 기준 | 조회 기준 | 송금 가중치 | TP | FP | FN | F1 | URL 후보 수 |", "|---:|---:|---:|---:|---:|---:|---:|---:|"]
    for candidate in report["threshold_development"]:
        lines.append(f"| {candidate['warning_threshold']} | {candidate['lookup_threshold']} | {candidate['transfer_weight']} | {candidate['tp']} | {candidate['fp']} | {candidate['fn']} | {value(candidate['f1'])} | {candidate['candidates']} |")
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--mode", choices=("mock", "live"), default="mock")
    parser.add_argument("--local-results", type=Path, default=ROOT / "frontend/app/build/reports/evaluation/local-results.json")
    parser.add_argument("--output", type=Path, default=ROOT / "docs/evaluation")
    parser.add_argument("--max-provider-attempts", type=int, default=20)
    parser.add_argument("--audit-only", action="store_true")
    args = parser.parse_args()
    if args.max_provider_attempts < 1:
        parser.error("--max-provider-attempts must be positive")
    cases = load_cases()
    audit = leakage_audit(cases)
    if args.audit_only:
        print(json.dumps(audit, ensure_ascii=False, indent=2)); return
    local_document = json.loads(args.local_results.read_text(encoding="utf-8"))
    for name in ("evaluation-cases.txt", "holdout-cases.txt"):
        if local_document["dataset_sha256"][name] != hashlib.sha256((DATA / name).read_bytes()).hexdigest():
            raise ValueError("Dataset changed; re-run Android evaluation")
    for name, expected in local_document["rule_sources_sha256"].items():
        source = ROOT / "frontend/app/src/main/java/com/cookiepatrol" / name
        if hashlib.sha256(source.read_bytes()).hexdigest() != expected:
            raise ValueError("Rule sources changed; re-run Android evaluation")
    local = {row["id"]: row for row in local_document["rows"]}
    if set(local) != {case["id"] for case in cases}:
        raise ValueError("Re-run Android evaluation; local results do not match dataset ids")
    from app.domain.url_check import PROMPT_VERSION
    from app.infrastructure.openai_checker import INSTRUCTIONS
    excluded_ids = {finding["holdout"] for finding in audit["manual_similarity_findings"]}
    sensitivity_cases = [c for c in cases if c["split"] == "holdout" and c["id"] not in excluded_ids]
    sensitivity = dict(excluded_ids=sorted(excluded_ids), case_count=len(sensitivity_cases))
    for label, score_key in (("baseline", "baseline_score"), ("current", "score")):
        sensitivity[label] = metrics([dict(phishing=c["phishing"], prediction="dangerous" if local[c["id"]][score_key] >= 3 else "not_detected")
                                      for c in sensitivity_cases])
    report = dict(generated_at=datetime.now(timezone.utc).isoformat(), dataset_version=local_document["dataset_version"],
                  similarity_sensitivity=sensitivity,
                  prompt_version=PROMPT_VERSION, prompt_sha256=hashlib.sha256(INSTRUCTIONS.encode("utf-8")).hexdigest(),
                  mock_fixture_sha256=hashlib.sha256((Path(__file__).parent / "evaluation-mocks.json").read_bytes()).hexdigest(),
                  dataset_sha256={name: hashlib.sha256((DATA / name).read_bytes()).hexdigest() for name in ("evaluation-cases.txt", "holdout-cases.txt")},
                  rule_version=local_document["rule_version"], rule_sources_sha256=local_document["rule_sources_sha256"], baseline_commit=local_document["baseline_commit"],
                  baseline={split: metrics([dict(phishing=c["phishing"], prediction="dangerous" if local[c["id"]]["baseline_score"] >= 3 else "not_detected")
                                              for c in cases if c["split"] == split]) for split in ("development", "holdout")},
                  leakage=audit, threshold_development=threshold_comparison(cases, local), local_rows=local,
                  pipeline=run_pipeline(cases, local, args.mode, args.max_provider_attempts))
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / f"{args.mode}-results.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    (args.output / f"{args.mode}-results.md").write_text(render_report(report), encoding="utf-8")
    print(json.dumps({"mode": args.mode, "cases": len(cases), "actual_provider_attempts": report["pipeline"]["actual_provider_attempts"],
                      "output": str(args.output)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
