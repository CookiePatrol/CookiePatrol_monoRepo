# Cookie Patrol backend

FastAPI service checking Google Safe Browsing first, then using the OpenAI Responses API for additional URL analysis when Google has no match or is unavailable. The Android app sends only candidate URLs; message and screen text stay on the device. The Google lookup checks a live threat database; the AI model only analyzes the URL string and does not browse the page.

## API key and local startup

Enter your Google Safe Browsing and OpenAI API keys in `backend/.env` (created locally and ignored by Git):

```dotenv
SAFE_BROWSING_API_KEY=your-google-key-here
OPENAI_API_KEY=your-openai-key-here
OPENAI_MODEL=gpt-4.1-mini
```

For a fresh checkout, copy `backend/.env.example` to `backend/.env` first. The backend automatically reads this file regardless of the working directory. Existing environment variables take precedence. Restart the backend after changing the key or model. Keep the key on the server; never put it in the Android app.

From `backend/`:

```powershell
uv sync
uv run uvicorn app.main:app --host 127.0.0.1 --port 8000
```

Or use an existing virtual environment:

```powershell
.\.venv\Scripts\python.exe -m pip install 'fastapi[standard]' 'httpx>=0.27,<1' 'python-dotenv>=1.0'
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

API documentation: http://localhost:8000/docs. The emulator uses `http://10.0.2.2:8000`. For a physical phone, configure an HTTPS backend URL in the Android `cookiePatrolApiBaseUrl` property; release builds reject cleartext API connections.

## API behavior

Review update (2026-11-16): see [review report](../docs/심사_반영_보고서.md) and [reproducible evaluation](../docs/평가_실행_방법.md). Real provider detection performance is **not measured**. The checked-in provider comparison is a **mock simulation**; actual API comparison was not run.

`POST /check` accepts `{"url":"https://example.com/path"}` and retains the existing response contract:

- `200 {"dangerous":true}`: Google listed the URL as a threat, or the AI detected suspicious URL indicators. Google matches skip AI calls.
- `200 {"dangerous":false}`: Google had no match or was unavailable, and AI did not detect concrete indicators. This is not a safety guarantee.
- `503`: Google did not establish a threat and AI analysis was unavailable (missing/invalid OpenAI key, timeout, refusal, malformed output or unknown judgment). A Google failure or missing Google key falls back to AI. A Google threat match still works without an OpenAI key. The app preserves its local warnings.
- `422`: invalid URL input.

`GET /health` checks process health, not provider credentials or availability.

`POST /check/details` uses the same request and adds `source` (`safe_browsing`, `ai`, `ai_cache`), `indicators`, a fixed Korean `reason`, `prompt_version`, `limitation`, and `google_cache_ttl_seconds` (remaining positive Google cache validity, otherwise zero). A cache hit with stored evidence includes `cached_indicators` and leaves `indicators` empty for older clients. Both routes share the rate limit. When the detailed route returns 404 or 405, the app uses the Boolean verdict from `/check` without attributing it to a specific provider. A Google failure followed by valid AI output uses that output. The evaluation tool records provider failures separately.

AI output now requires `verdict` and `indicators`: `brand_impersonation`, `credential_lure`, `deceptive_host`, `encoded_destination`, `suspicious_download`. Dangerous verdicts require codes; negative verdicts require an empty list. Contradictions, unknown/duplicate codes and incomplete/refused output fail to 503, preserving local warnings. Codes are inferences from URL structure, not verified page findings. No URL excerpts or free-form model explanation are cached. Cached verdicts are identified without inventing their old evidence. The cache namespace includes `url-structure-v2` and the model.

Simultaneous identical URL checks share one in-flight chain, bounded to 64 pending keys. A follower waits at most 22 seconds. Input validation errors use generic text without echoing URL tokens.

The default model is `gpt-4.1-mini`; configure `OPENAI_MODEL` to change it. The request uses a strict JSON schema, a 200-token output limit, no browsing tools, and `store: false`. OpenAI API usage can incur charges. Assessment is limited to the URL string, so redirects and page content are not inspected. Structured output validates format, not judgment accuracy.

`POST /check` is limited to 60 requests per minute per client IP in each backend process and returns `429` with `Retry-After` when exceeded. This in-memory limit is a demo safeguard, not a distributed quota; deployments with multiple workers or replicas should also configure a shared gateway limit and provider spending caps. Expired AI verdicts are removed as new entries are written, and the persistent cache is capped at 100,000 entries. The Safe Browsing positive cache is capped at 10,000 entries.

## Cache and data handling

AI results are cached for 24 hours in `./data/cookiepatrol-cache.sqlite3` (override with `CACHE_DB_PATH`). AI cache keys include provider, model, and prompt version. Google is consulted before the AI cache, so an old AI result cannot override a new Google threat match. Google positive matches are cached separately in memory for the returned `cacheDuration`; a server restart clears that cache. No-match Google results are not cached. The cache stores SHA-256 URL keys, verdicts, timestamps, and recognized indicator codes. Hashes are not anonymization. Failures and unknown assessments are not cached.

Full candidate URLs are sent to Google Safe Browsing and, only if additional analysis is needed, to OpenAI and may contain personal data or tokens in their path or query. Avoid personalized links and logging request bodies, full URLs, or authorization headers. `store: false` disables response storage; it does not imply zero data retention under all OpenAI policies.

Android reuses AI results for at most five minutes. Google positive results use the lesser of five minutes and the server's remaining Google validity, conservatively subtracting the entire request duration. Missing or zero validity disables client reuse while still displaying the current warning. A late weaker result cannot replace stronger evidence for the same notification event.

## Tests

Automatic external checks in the Android app now default off until separate consent. Notification/accessibility permissions alone do not enable URL transfer. The manual-check button explicitly submits one URL even below the automatic threshold.

The cache's 24 hours are a validity period. Expired SQLite rows are removed on lookup/subsequent writes, rather than necessarily at the instant they expire. Restrict database access and define operational cleanup. Do not log request bodies or authorization headers. Default server access logs may contain client IPs and endpoint paths.

```powershell
uv run pytest -q
```

Provider requests are mocked, so tests do not require a real key or spend API credits. Test actual authenticated access only after configuring your key.

Official references: [Google Lookup API](https://developers.google.com/safe-browsing/v4/lookup-api), [Google caching](https://developers.google.com/safe-browsing/v4/caching), [Responses API](https://developers.openai.com/api/docs/guides/text), [Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs).
