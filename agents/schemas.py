"""Pydantic schemas for SkillDetectionAgent inputs and outputs."""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


class SkillCandidate(BaseModel):
    """A potential skill extracted from input text by the LLM."""

    text: str = Field(description="The exact phrase from the input that refers to a skill")
    canonical: str = Field(
        description="The canonical/standard name for this skill (e.g. 'JWT' from 'token based authentication')"
    )
    type_hint: str | None = Field(
        default=None,
        description="Hint about skill category: technical, soft, domain, or tool",
    )
    rationale: str = Field(
        description="Brief explanation of why this phrase is considered a skill"
    )
    is_skill_confidence: float = Field(
        ge=0.0,
        le=1.0,
        description=(
            "Confidence (0–1) that this candidate is a genuine technical/professional skill "
            "given its context. Use context to avoid false positives like 'go to school'."
        ),
    )


class CandidateExtractionResult(BaseModel):
    """Structured LLM output: list of skill candidates extracted from input text."""

    candidates: list[SkillCandidate] = Field(
        description="Skill candidates extracted from the input text. Empty list if no skills found."
    )


class MatchEvidence(BaseModel):
    """Evidence for how a skill match was found."""

    method: Literal["fulltext", "semantic", "exact_override"] = Field(
        description="Search method that produced the match"
    )
    fulltext_score: float | None = Field(
        default=None, description="Neo4j fulltext index relevance score"
    )
    semantic_score: float | None = Field(
        default=None, description="Cosine similarity score from embedding search (0–1)"
    )


class MatchedSkill(BaseModel):
    """A skill candidate that was successfully mapped to the skills graph."""

    uid: str = Field(description="Unique identifier in the skills graph (format: skill_...)")
    name: str = Field(description="Canonical skill name in the graph")
    label: str | None = Field(default=None, description="Display label for the skill")
    skill_type: str = Field(description="Skill category: technical, soft, domain, or tool")
    confidence: float = Field(ge=0.0, le=1.0, description="Overall match confidence")
    evidence: MatchEvidence = Field(description="Evidence detailing how the match was found")


class UnknownSkill(BaseModel):
    """A skill candidate that appears genuine but has no match in the current graph."""

    text: str = Field(description="Original phrase from the input text")
    canonical: str = Field(description="Canonical name inferred for this skill")
    confidence: float = Field(
        ge=0.0,
        le=1.0,
        description=(
            "Confidence (0–1) that this is a real skill that should be added to the graph"
        ),
    )


class SkillDetectionResult(BaseModel):
    """Final output of the SkillDetectionAgent."""

    matched: list[MatchedSkill] = Field(
        default_factory=list,
        description="Skills successfully mapped to UIDs in the skills graph",
    )
    unknown: list[UnknownSkill] = Field(
        default_factory=list,
        description=(
            "High-confidence skill candidates that have no match in the graph. "
            "These are candidates for adding to the graph."
        ),
    )
