"""Unit tests for agents/schemas.py."""

from __future__ import annotations

from agents.skill_detection.schemas import (
    MatchedSkill,
    SkillDetectionResult,
    UnknownSkill,
)


def test_matched_skill_round_trip() -> None:
    skill = MatchedSkill(
        uid="skill_abc123",
        name="Python",
        skill_type="technical",
        confidence=0.95,
        evidence="I work with Python",
        match_method="exact",
    )
    data = skill.model_dump()
    restored = MatchedSkill.model_validate(data)
    assert restored.uid == "skill_abc123"
    assert restored.name == "Python"
    assert restored.confidence == 0.95
    assert restored.match_method == "exact"


def test_unknown_skill_round_trip() -> None:
    skill = UnknownSkill(
        text="microservices architecture",
        canonical="Microservices Architecture",
        confidence=0.85,
        evidence="mentioned in context of system design",
    )
    data = skill.model_dump()
    restored = UnknownSkill.model_validate(data)
    assert restored.text == "microservices architecture"
    assert restored.canonical == "Microservices Architecture"
    assert restored.confidence == 0.85


def test_detection_result_empty() -> None:
    result = SkillDetectionResult(input_text="hello world")
    assert result.input_text == "hello world"
    assert result.matched_skills == []
    assert result.unknown_skills == []


def test_detection_result_with_skills() -> None:
    result = SkillDetectionResult(
        input_text="I use Java and React",
        matched_skills=[
            MatchedSkill(
                uid="skill_001",
                name="Java",
                skill_type="technical",
                confidence=1.0,
                evidence="I use Java",
                match_method="exact",
            ),
        ],
        unknown_skills=[
            UnknownSkill(
                text="React",
                canonical="React.js",
                confidence=0.9,
                evidence="I use React",
            ),
        ],
    )
    assert len(result.matched_skills) == 1
    assert len(result.unknown_skills) == 1
    assert result.matched_skills[0].name == "Java"
    assert result.unknown_skills[0].canonical == "React.js"


def test_detection_result_json_serialization() -> None:
    result = SkillDetectionResult(
        input_text="test",
        matched_skills=[
            MatchedSkill(
                uid="skill_x",
                name="Go",
                skill_type="technical",
                confidence=0.98,
                evidence="I code in Go",
                match_method="fulltext",
            ),
        ],
    )
    json_str = result.model_dump_json()
    restored = SkillDetectionResult.model_validate_json(json_str)
    assert restored.matched_skills[0].uid == "skill_x"
    assert restored.matched_skills[0].name == "Go"
