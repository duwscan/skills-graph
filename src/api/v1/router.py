"""Versioned API router aggregating all v1 sub-routers."""

from __future__ import annotations

from fastapi import APIRouter

from api.v1.skill_detection import skill_detection_router

v1_router = APIRouter()
v1_router.include_router(
    skill_detection_router,
    prefix="/agents/skill-detection",
    tags=["skill-detection"],
)
