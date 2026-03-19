"""Health and readiness probes."""

from __future__ import annotations

from fastapi import APIRouter
from pydantic import BaseModel

from core.db import test_connection


class HealthResponse(BaseModel):
    status: str
    neo4j: str


health_router = APIRouter(tags=["health"])


@health_router.get("/health", response_model=HealthResponse)
async def health() -> HealthResponse:
    """Liveness probe -- always returns 200."""
    return HealthResponse(status="ok", neo4j="connected")


@health_router.get("/readiness", response_model=HealthResponse)
async def readiness() -> HealthResponse:
    """Readiness probe -- checks Neo4j connectivity."""
    neo4j_ok = test_connection()
    return HealthResponse(
        status="ok" if neo4j_ok else "degraded",
        neo4j="connected" if neo4j_ok else "unreachable",
    )
