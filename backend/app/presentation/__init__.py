import threading
import time
from collections import OrderedDict, deque
from urllib.parse import urlsplit

from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel, field_validator

from app.application.check_url import CheckUrl
from app.domain.url_check import PROMPT_VERSION, ThreatCheckUnavailable

router = APIRouter()
_check_url: CheckUrl | None = None
_check_url_lock = threading.Lock()


class ClientRateLimiter:
    """Small process-local limit to bound accidental or abusive provider spend."""

    def __init__(self, limit: int = 60, window_seconds: float = 60, max_clients: int = 4096):
        self.limit = limit
        self.window_seconds = window_seconds
        self.max_clients = max_clients
        self._requests: OrderedDict[str, deque[float]] = OrderedDict()
        self._lock = threading.Lock()

    def retry_after(self, client: str, now: float | None = None) -> int | None:
        now = time.monotonic() if now is None else now
        cutoff = now - self.window_seconds
        with self._lock:
            requests = self._requests.get(client)
            if requests is None:
                requests = deque()
                self._requests[client] = requests
            else:
                while requests and requests[0] <= cutoff:
                    requests.popleft()
                self._requests.move_to_end(client)

            if len(requests) >= self.limit:
                return max(1, int(requests[0] + self.window_seconds - now + 0.999))

            requests.append(now)
            while len(self._requests) > self.max_clients:
                self._requests.popitem(last=False)
            return None


_check_rate_limiter = ClientRateLimiter()


def get_check_url() -> CheckUrl:
    global _check_url
    with _check_url_lock:
        if _check_url is None:
            try:
                from app.infrastructure import OpenAIThreatChecker, SafeBrowsingChecker, SqliteVerdictCache
                checker = OpenAIThreatChecker()
                primary = SafeBrowsingChecker()
                cache = SqliteVerdictCache(namespace=checker.cache_namespace)
            except ThreatCheckUnavailable as exc:
                raise HTTPException(status_code=503, detail="Threat check unavailable") from exc
            except ImportError as exc:
                raise HTTPException(status_code=503, detail="Threat check unavailable") from exc

            _check_url = CheckUrl(checker, cache, primary=primary, detailed_checker=checker,
                                  primary_cache_ttl=primary.cache_ttl_remaining)
        return _check_url


def close_check_url() -> None:
    global _check_url
    with _check_url_lock:
        service = _check_url
        _check_url = None
    if service is not None:
        close = getattr(service.cache, "close", None)
        if callable(close):
            close()


class CheckRequest(BaseModel):
    url: str

    @field_validator("url", mode="before")
    @classmethod
    def validate_url(cls, value: object) -> str:
        if not isinstance(value, str):
            raise ValueError("URL must be a string")
        value = value.strip()
        if not value or len(value) > 2048:
            raise ValueError("URL must be between 1 and 2048 characters")
        if any(character.isspace() or ord(character) < 32 for character in value):
            raise ValueError("URL must not contain whitespace or control characters")
        try:
            parsed = urlsplit(value)
            hostname = parsed.hostname
            _ = parsed.port  # Force validation of malformed or out-of-range ports.
        except ValueError as exc:
            raise ValueError("URL must be a valid HTTP(S) URL") from exc
        if (
            parsed.scheme.lower() not in {"http", "https"}
            or not hostname
            or parsed.username is not None
            or parsed.password is not None
        ):
            raise ValueError("URL must be a valid HTTP(S) URL without embedded credentials")
        return value


class CheckResponse(BaseModel):
    dangerous: bool


class CheckDetailsResponse(CheckResponse):
    source: str
    indicators: list[str]
    cached_indicators: list[str] | None = None
    reason: str
    prompt_version: str = PROMPT_VERSION
    limitation: str = "AI는 주소 문자열만 분석해요. 웹페이지·이동 경로를 확인하지 않으며 안전을 보증하지 않아요."
    google_cache_ttl_seconds: float = 0.0


def enforce_rate_limit(request: Request) -> None:
    client = request.client.host if request.client is not None else "unknown"
    retry_after = _check_rate_limiter.retry_after(client)
    if retry_after is not None:
        raise HTTPException(
            status_code=429,
            detail="Too many URL checks; try again later",
            headers={"Retry-After": str(retry_after)},
        )


@router.post("/check", response_model=CheckResponse, dependencies=[Depends(enforce_rate_limit)])
def check_url(
    body: CheckRequest,
    service: CheckUrl = Depends(get_check_url),
) -> CheckResponse:
    try:
        return CheckResponse(dangerous=service(body.url))
    except ThreatCheckUnavailable as exc:
        raise HTTPException(status_code=503, detail="Threat check unavailable") from exc


@router.post("/check/details", response_model=CheckDetailsResponse, response_model_exclude_none=True,
             dependencies=[Depends(enforce_rate_limit)])
def check_url_details(body: CheckRequest, service: CheckUrl = Depends(get_check_url)) -> CheckDetailsResponse:
    try:
        result = service.assess(body.url)
        # Keep the original indicators field empty on cache hits for older app versions.
        cached = result.source == "ai_cache"
        return CheckDetailsResponse(dangerous=result.dangerous, source=result.source,
                                    indicators=[] if cached else list(result.indicators),
                                    cached_indicators=list(result.indicators) if cached and result.indicators else None,
                                    reason=result.reason,
                                    google_cache_ttl_seconds=result.google_cache_ttl_seconds)
    except ThreatCheckUnavailable as exc:
        raise HTTPException(status_code=503, detail="Threat check unavailable") from exc


@router.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
