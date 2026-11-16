"""Provider connectivity with a harmless URL; reports no secrets, prompts or raw responses."""
import argparse
import json
import os
import time
from datetime import datetime, timezone
from pathlib import Path

from dotenv import load_dotenv
from app.domain.url_check import ThreatCheckUnavailable
from app.infrastructure import OpenAIThreatChecker, SafeBrowsingChecker


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--live", action="store_true", help="Attempt one request per configured provider")
    args = parser.parse_args()
    load_dotenv(Path(__file__).parent / ".env", override=False)
    result = {"recorded_at": datetime.now(timezone.utc).isoformat(), "mode": "live" if args.live else "configuration_only",
              "test_url": "https://example.com/", "providers": {}}
    for name, key, checker in (("google", "SAFE_BROWSING_API_KEY", SafeBrowsingChecker()),
                               ("ai", "OPENAI_API_KEY", OpenAIThreatChecker())):
        configured = bool(os.environ.get(key, "").strip())
        row = dict(key_configured=configured, attempted=False, status="not_configured" if not configured else "not_run")
        if configured and args.live:
            row["attempted"] = True
            start = time.perf_counter()
            try:
                row["dangerous"] = checker.is_dangerous("https://example.com/")
                row["status"] = "completed"
            except ThreatCheckUnavailable as exc:
                row["status"] = exc.reason
            row["elapsed_ms"] = (time.perf_counter() - start) * 1000
        result["providers"][name] = row
    result["model"] = OpenAIThreatChecker().model
    output = Path(__file__).resolve().parents[1] / "docs/verification/providers.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
