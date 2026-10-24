from dataclasses import dataclass
from enum import StrEnum


class VerdictStatus(StrEnum):
    DANGEROUS = "dangerous"
    NOT_LISTED = "not_listed"
    UNKNOWN = "unknown"


@dataclass(frozen=True)
class Verdict:
    status: VerdictStatus
    threat_types: tuple[str, ...] = ()
    source: str | None = None
    cached: bool = False
    reason: str | None = None
