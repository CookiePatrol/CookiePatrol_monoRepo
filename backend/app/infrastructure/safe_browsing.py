"""Google Safe Browsing Lookup API v4 with provider-directed positive caching."""

import hashlib
import os
import re
import threading
import time

import httpx

from app.domain.url_check import ThreatCheckUnavailable


class SafeBrowsingChecker:
    def __init__(self) -> None:
        self._api_key = os.environ.get("SAFE_BROWSING_API_KEY", "").strip()
        self._positive_cache: dict[str, float] = {}
        self._lock = threading.Lock()

    def is_dangerous(self, url: str) -> bool:
        if not self._api_key:
            raise ThreatCheckUnavailable("SAFE_BROWSING_API_KEY is required")
        key = hashlib.sha256(url.encode("utf-8")).hexdigest()
        now = time.monotonic()
        with self._lock:
            self._positive_cache = {k: expiry for k, expiry in self._positive_cache.items() if expiry > now}
            if key in self._positive_cache:
                return True
        try:
            with httpx.Client(timeout=5.0) as client:
                response = client.post(
                    "https://safebrowsing.googleapis.com/v4/threatMatches:find",
                    headers={"x-goog-api-key": self._api_key},
                    json={
                        "client": {"clientId": "cookiepatrol", "clientVersion": "0.1.0"},
                        "threatInfo": {
                            "threatTypes": ["MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE",
                                            "POTENTIALLY_HARMFUL_APPLICATION"],
                            "platformTypes": ["ANY_PLATFORM"],
                            "threatEntryTypes": ["URL"],
                            "threatEntries": [{"url": url}],
                        },
                    },
                )
                response.raise_for_status()
                data = response.json()
        except (httpx.HTTPError, ValueError):
            raise ThreatCheckUnavailable("Safe Browsing lookup failed") from None

        if data == {}:
            return False
        try:
            if not isinstance(data, dict) or set(data) != {"matches"} or not isinstance(data["matches"], list):
                raise ValueError()
            durations = []
            for match in data["matches"]:
                if (
                    match["threatType"] not in ("MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE",
                                                "POTENTIALLY_HARMFUL_APPLICATION")
                    or match["platformType"] not in ("WINDOWS", "LINUX", "ANDROID", "OSX", "IOS",
                                                      "ANY_PLATFORM", "ALL_PLATFORMS", "CHROME")
                    or match["threatEntryType"] != "URL"
                    or not isinstance(match["threat"]["url"], str)
                    or not match["threat"]["url"].strip()
                    or not isinstance(match["cacheDuration"], str)
                    or re.fullmatch(r"\d+(?:\.\d{1,9})?s", match["cacheDuration"]) is None
                ):
                    raise ValueError()
                durations.append(float(match["cacheDuration"][:-1]))
        except (KeyError, TypeError, ValueError, OverflowError):
            raise ThreatCheckUnavailable("Invalid Safe Browsing response") from None
        if durations:
            with self._lock:
                self._positive_cache[key] = time.monotonic() + max(durations)
        return bool(durations)
