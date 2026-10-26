import threading
from urllib.parse import urlsplit

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, field_validator

from app.application.check_url import CheckUrl
from app.domain.url_check import ThreatCheckUnavailable

router = APIRouter()
_check_url: CheckUrl | None = None
_check_url_lock = threading.Lock()


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

            _check_url = CheckUrl(checker, cache, primary=primary)
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


@router.post("/check", response_model=CheckResponse)
def check_url(body: CheckRequest, service: CheckUrl = Depends(get_check_url)) -> CheckResponse:
    try:
        return CheckResponse(dangerous=service(body.url))
    except ThreatCheckUnavailable as exc:
        raise HTTPException(status_code=503, detail="Threat check unavailable") from exc


@router.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
