# Cookie Patrol backend

FastAPI service for URL reputation checks using Google Safe Browsing. The app sends only a candidate URL to this service; message text and local risk signals stay on the Android device.

## Run locally

From this directory, configure `SAFE_BROWSING_API_KEY` in the environment, then run:

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

`dangerous: false` means Safe Browsing did not list the URL; it is not a guarantee that the URL is safe. The Android client maps this result to `not_listed` and preserves local warnings. Provider failures return HTTP 503 and map to `unknown`. Invalid URLs receive HTTP 422. `/health` is a basic process health check.

## Privacy and operational notes

- Message content is not accepted by this API. The exact candidate URL is sent to Google Safe Browsing and may contain personal data in its path or query string; explain this transfer to users and avoid submitting personalized links.
- The SQLite cache stores a SHA-256 URL key, verdict, and check time rather than the clear-text URL. A plain hash is pseudonymous, not anonymized, and its database file still needs access controls. Existing cache tables with clear-text URLs are dropped on startup.
- Avoid logging request bodies and full URLs. Cached results expire after 24 hours; provider failures are not cached.
- `SAFE_BROWSING_API_KEY` belongs in server-side environment configuration, never in the Android app.
- This service does not fetch the submitted URL. It validates its shape and sends it to the configured reputation provider.
