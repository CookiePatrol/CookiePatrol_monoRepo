from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, field_validator

from app.application.check_url import CheckUrl
from app.domain.url_check import ThreatCheckUnavailable

router = APIRouter()
_check_url: CheckUrl | None = None


def get_check_url() -> CheckUrl:
    global _check_url
    if _check_url is None:
        try:
            from app.infrastructure import SafeBrowsingChecker, SqliteVerdictCache
            checker = SafeBrowsingChecker()
            cache = SqliteVerdictCache()
        except ThreatCheckUnavailable as exc:
            raise HTTPException(status_code=503, detail="Threat check unavailable") from exc
        except ImportError as exc:
            raise HTTPException(status_code=503, detail="Threat check unavailable") from exc

        _check_url = CheckUrl(checker, cache)
    return _check_url


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
        if not value.startswith(("http://", "https://")):
            raise ValueError("URL must start with http:// or https://")
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
