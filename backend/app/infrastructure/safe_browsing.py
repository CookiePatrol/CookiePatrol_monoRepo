"""Google Safe Browsing Lookup API v4 adapter."""

import os
import re

import httpx

from app.domain.url_check import ThreatCheckUnavailable


class SafeBrowsingChecker:
    def __init__(self) -> None:
        self._api_key = os.environ.get("SAFE_BROWSING_API_KEY", "").strip()
        if not self._api_key:
            raise ThreatCheckUnavailable("SAFE_BROWSING_API_KEY is required")

    def is_dangerous(self, url: str) -> bool:
        try:
            # Keep the key out of URLs, HTTP request logs and exception messages.
            with httpx.Client(timeout=5.0) as client:
                response = client.post(
                    "https://safebrowsing.googleapis.com/v4/threatMatches:find",
                    headers={"x-goog-api-key": self._api_key},
                    json={
                        "client": {"clientId": "cookiepatrol", "clientVersion": "0.1.0"},
                        "threatInfo": {
                            "threatTypes": [
                                "MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE",
                                "POTENTIALLY_HARMFUL_APPLICATION",
                            ],
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
        if not isinstance(data, dict) or set(data) != {"matches"}:
            raise ThreatCheckUnavailable("Invalid Safe Browsing response")
        matches = data["matches"]
        if not isinstance(matches, list):
            raise ThreatCheckUnavailable("Invalid Safe Browsing matches")
        for match in matches:
            if not isinstance(match, dict):
                raise ThreatCheckUnavailable("Invalid Safe Browsing match")
            threat = match.get("threat")
            if (
                match.get("threatType") not in (
                    "MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE",
                    "POTENTIALLY_HARMFUL_APPLICATION",
                )
                or match.get("platformType") not in (
                    "WINDOWS", "LINUX", "ANDROID", "OSX", "IOS",
                    "ANY_PLATFORM", "ALL_PLATFORMS", "CHROME",
                )
                or match.get("threatEntryType") != "URL"
                or not isinstance(threat, dict)
                or not isinstance(threat.get("url"), str)
                or not threat["url"].strip()
            ):
                raise ThreatCheckUnavailable("Invalid Safe Browsing match")
            if "cacheDuration" in match and (
                not isinstance(match["cacheDuration"], str)
                or re.fullmatch(r"\d+(?:\.\d{1,9})?s", match["cacheDuration"]) is None
            ):
                raise ThreatCheckUnavailable("Invalid Safe Browsing cache duration")
        return bool(matches)
