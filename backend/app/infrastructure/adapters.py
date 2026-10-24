import asyncio
import hashlib
from datetime import datetime, timedelta, timezone

from app.domain.url_check import ThreatCheckUnavailable
from app.domain.verdict import Verdict, VerdictStatus
from app.infrastructure.sqlite_cache import SQLiteVerdictCache
from app.infrastructure.web_risk import ProviderUnavailable, WebRiskProvider


class SafeBrowsingChecker:
    """Adapt the Web Risk provider to the domain's synchronous boolean port."""

    def __init__(self, provider: WebRiskProvider | None = None):
        self.provider = provider or WebRiskProvider()

    def is_dangerous(self, url: str) -> bool:
        try:
            verdict, _ = asyncio.run(self.provider.check(url))
        except ProviderUnavailable as exc:
            raise ThreatCheckUnavailable from exc
        return verdict.status is VerdictStatus.DANGEROUS


class SqliteVerdictCache:
    """Adapt the expiring verdict cache to the domain's boolean cache port."""

    TTL_SECONDS = 300

    def __init__(self, db_path: str | None = None):
        self.cache = SQLiteVerdictCache(db_path)

    @staticmethod
    def _key(url: str) -> str:
        return hashlib.sha256(url.encode("utf-8")).hexdigest()

    def get(self, url: str) -> bool | None:
        now = datetime.now(timezone.utc)
        verdict = self.cache.get(self._key(url), now)
        if verdict is None:
            return None
        return verdict.status is VerdictStatus.DANGEROUS

    def set(self, url: str, dangerous: bool) -> None:
        now = datetime.now(timezone.utc)
        status = VerdictStatus.DANGEROUS if dangerous else VerdictStatus.NOT_LISTED
        verdict = Verdict(status=status, source="google_web_risk")
        self.cache.put(
            self._key(url),
            verdict,
            now + timedelta(seconds=self.TTL_SECONDS),
        )

    def close(self) -> None:
        self.cache.close()
