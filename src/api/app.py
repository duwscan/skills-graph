"""FastAPI application factory with lifespan management."""

from __future__ import annotations

from contextlib import asynccontextmanager
from typing import AsyncIterator

from fastapi import FastAPI

from core.db import ensure_indexes, init_db, test_connection


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    """Application lifespan: initialize DB and indexes on startup."""
    init_db()
    if not test_connection():
        raise RuntimeError("Cannot connect to Neo4j -- check .env configuration")
    ensure_indexes()
    yield


def create_app() -> FastAPI:
    """Build and return the FastAPI application."""
    from api.v1.health import health_router
    from api.v1.router import v1_router

    app = FastAPI(
        title="Skills Graph API",
        version="0.2.0",
        description="Neo4j-backed skill taxonomy with LLM-powered detection agents.",
        lifespan=lifespan,
    )

    app.include_router(health_router)
    app.include_router(v1_router, prefix="/api/v1")

    return app


app = create_app()
