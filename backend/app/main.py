from fastapi import FastAPI

from app.presentation import router

app = FastAPI(title="CookiePatrol")
app.include_router(router)
