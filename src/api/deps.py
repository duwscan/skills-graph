"""FastAPI dependency injection providers."""

from __future__ import annotations

from functools import lru_cache

from agents.skill_detection.agent import SkillDetectionAgent


@lru_cache(maxsize=1)
def get_skill_detection_agent() -> SkillDetectionAgent:
    """Provide a singleton SkillDetectionAgent instance.

    The agent is expensive to construct (loads LLM, builds ReAct graph),
    so we cache it for the process lifetime.
    """
    return SkillDetectionAgent()
