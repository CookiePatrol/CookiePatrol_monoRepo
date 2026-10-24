from datetime import datetime
from typing import Protocol

from app.domain.verdict import Verdict


class ThreatProvider(Protocol):
    async def check(self, url: str) -> tuple[Verdict, datetime]: ...


class VerdictCache(Protocol):
    def get(self, key: str, now: datetime) -> Verdict | None: ...

    def put(self, key: str, verdict: Verdict, expires_at: datetime) -> None: ...
