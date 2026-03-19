"""Skill detection agent API endpoints."""

from __future__ import annotations

from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field

from agents.skill_detection.agent import SkillDetectionAgent
from agents.skill_detection.schemas import SkillDetectionResult
from api.deps import get_skill_detection_agent

skill_detection_router = APIRouter()


class DetectRequest(BaseModel):
    """Request body for skill detection."""

    text: str = Field(
        ...,
        min_length=1,
        max_length=10_000,
        description="Free-form text to analyze for skills.",
        examples=["I work with Java and Spring Boot for backend development"],
    )


@skill_detection_router.post(
    "/detect",
    response_model=SkillDetectionResult,
    summary="Detect skills in text",
    description="Analyze free-form text and return matched and unknown skills.",
)
async def detect_skills(
    body: DetectRequest,
    agent: SkillDetectionAgent = Depends(get_skill_detection_agent),
) -> SkillDetectionResult:
    """Run the skill detection agent on the provided text."""
    return agent.detect(body.text)
