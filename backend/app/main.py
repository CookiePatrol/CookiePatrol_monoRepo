from contextlib import asynccontextmanager
from pathlib import Path

from dotenv import load_dotenv
from fastapi import FastAPI
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.presentation import close_check_url, router

# Resolve the key file independently of the shell's working directory.
# Existing environment variables take precedence over local configuration.
load_dotenv(Path(__file__).resolve().parents[1] / ".env", override=False)


@asynccontextmanager
async def lifespan(_app: FastAPI):
    try:
        yield
    finally:
        close_check_url()


app = FastAPI(title="CookiePatrol", lifespan=lifespan)
app.include_router(router)


@app.exception_handler(RequestValidationError)
async def invalid_input(_request, _exc):
    # Pydantic's default error details echo input values, including URL tokens.
    return JSONResponse(status_code=422, content={"detail": "Invalid request; provide one valid HTTP(S) URL"})
