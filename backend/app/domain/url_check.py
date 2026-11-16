from dataclasses import dataclass
from typing import Literal, Protocol


PROMPT_VERSION = "url-structure-v2"
INDICATOR_REASONS = {
    "brand_impersonation": "공식 기관이나 서비스를 흉내 낸 주소 구조가 보여요.",
    "credential_lure": "로그인이나 인증정보 입력으로 유도하는 주소 구조가 보여요.",
    "deceptive_host": "주소의 실제 연결 대상을 혼동하게 하는 구조가 보여요.",
    "encoded_destination": "연결 대상을 감추는 인코딩이나 중첩 주소가 보여요.",
    "suspicious_download": "의심스러운 설치 파일로 유도하는 주소 구조가 보여요.",
}


@dataclass(frozen=True)
class UrlAssessment:
    dangerous: bool
    source: Literal["safe_browsing", "ai", "ai_cache"]
    indicators: tuple[str, ...] = ()
    google_cache_ttl_seconds: float = 0.0

    @property
    def reason(self) -> str:
        if self.source == "safe_browsing":
            return "Google 위험 목록에 등록된 링크예요."
        if self.source == "ai_cache":
            return ("최근 AI 검사에서 주소의 위험 신호가 발견됐어요."
                    if self.dangerous else "최근 AI 검사에서 주소의 위험 신호를 찾지 못했어요. 안전 보증은 아니에요.")
        if self.indicators:
            return "AI가 주소에서 위험 신호를 찾았어요. " + INDICATOR_REASONS[self.indicators[0]]
        if self.dangerous:
            return "AI가 주소의 위험 신호를 찾았어요. 사이트 방문 결과는 아니에요."
        return "AI가 주소의 위험 신호를 찾지 못했어요. 안전 보증은 아니에요."


class ThreatCheckUnavailable(Exception):
    """위험 분석 불가 (네트워크, 키 오류, 할당량 초과, AI 판단 불가 등)."""

    def __init__(self, message: str = "Threat check unavailable", reason: str = "unavailable"):
        super().__init__(message)
        self.reason = reason


class ThreatChecker(Protocol):
    def is_dangerous(self, url: str) -> bool: ...  # 실패 시 ThreatCheckUnavailable


class DetailedThreatChecker(Protocol):
    def assess(self, url: str) -> UrlAssessment: ...


class VerdictCache(Protocol):
    def get(self, url: str) -> bool | None: ...  # 없거나 만료면 None

    def set(self, url: str, dangerous: bool) -> None: ...
