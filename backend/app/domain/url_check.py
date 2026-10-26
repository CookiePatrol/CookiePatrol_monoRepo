from typing import Protocol


class ThreatCheckUnavailable(Exception):
    """위험 분석 불가 (네트워크, 키 오류, 할당량 초과, AI 판단 불가 등)."""


class ThreatChecker(Protocol):
    def is_dangerous(self, url: str) -> bool: ...  # 실패 시 ThreatCheckUnavailable


class VerdictCache(Protocol):
    def get(self, url: str) -> bool | None: ...  # 없거나 만료면 None

    def set(self, url: str, dangerous: bool) -> None: ...
