"""Pydantic models for skill detection agent I/O."""

from __future__ import annotations

from pydantic import BaseModel


class MatchedSkill(BaseModel):
    """A skill matched to the graph."""

    uid: str
    name: str
    skill_type: str
    confidence: float
    evidence: str
    match_method: str


class UnknownSkill(BaseModel):
    """A skill candidate not found in the graph."""

    text: str
    canonical: str
    confidence: float
    evidence: str


class SkillDetectionResult(BaseModel):
    """Skill detection output."""

    input_text: str
    matched_skills: list[MatchedSkill] = []
    unknown_skills: list[UnknownSkill] = []
