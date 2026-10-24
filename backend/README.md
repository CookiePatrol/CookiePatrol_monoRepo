# Cookie Patrol backend

FastAPI service for URL reputation checks using Google Web Risk. The app sends only a candidate URL to this service; message text and local risk signals stay on the Android device.

## Run locally

From this directory, enable the Web Risk API for a Google Cloud project, configure `GOOGLE_WEB_RISK_API_KEY` in the environment, then run:

```powershell
uv run fastapi dev app/main.py
```

If the key is missing or Google cannot be reached, `POST /check` returns HTTP 503. The Android client maps failures to `unknown`; an unavailable provider is never treated as a safe result. The SQLite cache defaults to `./data/cookiepatrol-cache.sqlite3`; override it with `CACHE_DB_PATH`.

## API

`POST /check`

```json
{"url": "https://example.com/path"}
```

Successful response:

```json
{"dangerous": true}
```

`dangerous: false` means Web Risk did not list the URL; it is not a guarantee that the URL is safe. The Android client maps this result to `not_listed` and preserves local warnings. Provider failures return HTTP 503 and map to `unknown`. Invalid URLs receive HTTP 422. `/health` is a basic process health check.

## Privacy and operational notes

- Message content is not accepted by this API. Avoid logging request bodies and full URLs: query strings may contain tokens or personal data.
- An uncached candidate URL is sent to Google Web Risk for lookup. Disclose that transfer to users. The cache stores a SHA-256 key, verdict, threat types, source, and expiry, not the original URL.
- The boolean cache adapter stores a SHA-256 URL key with a fixed 5-minute TTL. Provider failure results are not cached. This fixed TTL does not preserve Web Risk's per-threat `expireTime`; carry expiry through the application port if exact provider expiry is required.
- `GOOGLE_WEB_RISK_API_KEY` belongs in server-side environment configuration, never in the Android app. Restrict the key to the Web Risk API and protect the service with deployment-level rate limits before public release.
- Google currently lists Lookup API `uris.search` as free for up to 100,000 calls per month, then $0.50 per 1,000 calls from 100,001 to 10,000,000. Calls beyond that free tier can incur charges. Set Google Cloud budget alerts and monitor usage before public release.
- This service does not fetch the submitted URL. It validates its shape and sends it to the configured reputation provider.
