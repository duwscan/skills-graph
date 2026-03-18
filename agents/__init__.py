"""Agents package — skill detection and mapping tools.

Public API::

    from agents import SkillDetectionAgent
    from agents.schemas import SkillDetectionResult, MatchedSkill, UnknownSkill
"""

from agents.skill_detection_agent import SkillDetectionAgent

__all__ = ["SkillDetectionAgent"]
