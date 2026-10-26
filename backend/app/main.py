from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.presentation import close_check_url, router


@asynccontextmanager
async def lifespan(_app: FastAPI):
    try:
        yield
    finally:
        close_check_url()


app = FastAPI(title="CookiePatrol", lifespan=lifespan)
app.include_router(router)
