"""End-to-end tests for SkillDetectionAgent.

These tests require:
- A running Neo4j instance with seeded skill data
- OPENAI_API_KEY (or a running Ollama server if LLM_PROVIDER=ollama)

Skip with: pytest -m "not e2e"
"""

from __future__ import annotations

import os

import pytest

pytestmark = pytest.mark.e2e

_SKIP_REASON = "E2E tests require NEO4J_URI and OPENAI_API_KEY (or Ollama)"


def _can_run_e2e() -> bool:
    has_neo4j = bool(os.environ.get("NEO4J_URI"))
    has_llm = bool(os.environ.get("OPENAI_API_KEY")) or os.environ.get(
        "LLM_PROVIDER"
    ) == "ollama"
    return has_neo4j and has_llm


@pytest.fixture(scope="module")
def agent():
    if not _can_run_e2e():
        pytest.skip(_SKIP_REASON)
    from agents import SkillDetectionAgent

    return SkillDetectionAgent()


def test_java_detected(agent) -> None:
    result = agent.detect("I work with Java")
    skill_names = [s.name for s in result.matched_skills]
    assert "Java" in skill_names


def test_go_not_detected_in_casual_context(agent) -> None:
    result = agent.detect("I go to school")
    skill_names = [s.name for s in result.matched_skills]
    assert "Go" not in skill_names
    assert "Golang" not in skill_names


def test_implicit_skill_extraction(agent) -> None:
    result = agent.detect(
        "I have experience at token base authentication at backend"
    )
    all_names = [s.name for s in result.matched_skills] + [
        s.canonical for s in result.unknown_skills
    ]
    # Should find at least one authentication-related skill
    assert any(
        "authentication" in name.lower() or "jwt" in name.lower() or "oauth" in name.lower()
        for name in all_names
    ), f"Expected auth-related skill, got: {all_names}"


def test_no_skills_in_plain_text(agent) -> None:
    result = agent.detect("The weather is nice today")
    assert len(result.matched_skills) == 0


def test_result_has_uids(agent) -> None:
    result = agent.detect("I work with Python and JavaScript")
    for skill in result.matched_skills:
        assert skill.uid.startswith("skill_"), f"Bad uid: {skill.uid}"
        assert skill.skill_type in {"technical", "soft", "domain", "tool"}
        assert 0.0 <= skill.confidence <= 1.0
