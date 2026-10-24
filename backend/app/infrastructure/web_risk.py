import os
from datetime import datetime, timedelta, timezone

import httpx

from app.domain.verdict import Verdict, VerdictStatus


class ProviderUnavailable(Exception):
    pass


class WebRiskProvider:
    """Google Web Risk Lookup API adapter."""

    ENDPOINT = "https://webrisk.googleapis.com/v1/uris:search"
    THREAT_TYPES = ("SOCIAL_ENGINEERING", "MALWARE", "UNWANTED_SOFTWARE")
    NOT_LISTED_CACHE_SECONDS = 300

    def __init__(self, api_key: str | None = None, timeout_seconds: float = 4.0):
        self.api_key = api_key if api_key is not None else os.getenv("GOOGLE_WEB_RISK_API_KEY")
        self.timeout_seconds = timeout_seconds

    async def check(self, url: str) -> tuple[Verdict, datetime]:
        if not self.api_key:
            raise ProviderUnavailable("Google Web Risk API key is not configured")
        params = [("key", self.api_key), ("uri", url)]
        params.extend(("threatTypes", threat_type) for threat_type in self.THREAT_TYPES)
        try:
            async with httpx.AsyncClient(timeout=self.timeout_seconds) as client:
                response = await client.get(self.ENDPOINT, params=params)
                response.raise_for_status()
                payload = response.json()
        except (httpx.HTTPError, ValueError) as exc:
            raise ProviderUnavailable("Google Web Risk lookup failed") from exc

        if not isinstance(payload, dict):
            raise ProviderUnavailable("Google Web Risk returned an invalid response")
        threat = payload.get("threat")
        now = datetime.now(timezone.utc)
        if threat is None:
            # Lookup has no negative-cache expiration, so keep not-listed results briefly.
            threat_types: tuple[str, ...] = ()
            status = VerdictStatus.NOT_LISTED
            expires_at = now + timedelta(seconds=self.NOT_LISTED_CACHE_SECONDS)
        elif isinstance(threat, dict) and isinstance(threat.get("threatTypes", []), list):
            threat_types = tuple(sorted({
                value for value in threat.get("threatTypes", []) if isinstance(value, str)
            }))
            status = VerdictStatus.DANGEROUS if threat_types else VerdictStatus.NOT_LISTED
            expires_at = (
                self._parse_expiry(threat.get("expireTime"))
                if status == VerdictStatus.DANGEROUS
                else now + timedelta(seconds=self.NOT_LISTED_CACHE_SECONDS)
            )
        else:
            raise ProviderUnavailable("Google Web Risk returned an invalid response")

        return Verdict(status=status, threat_types=threat_types, source="google_web_risk"), expires_at

    @staticmethod
    def _parse_expiry(value: str | None) -> datetime:
        if not value:
            return datetime.now(timezone.utc)
        try:
            expiry = datetime.fromisoformat(value.replace("Z", "+00:00"))
            return expiry.astimezone(timezone.utc) if expiry.tzinfo else datetime.now(timezone.utc)
        except ValueError:
            return datetime.now(timezone.utc)
