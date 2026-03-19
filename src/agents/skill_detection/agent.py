"""Skill detection agent using LangGraph ReAct pattern."""

from __future__ import annotations

from typing import TYPE_CHECKING

from langchain.agents import create_agent

from agents.skill_detection.prompts import SYSTEM_PROMPT
from agents.skill_detection.schemas import SkillDetectionResult
from agents.skill_detection.tools import (
    search_skills_fulltext,
    search_skills_semantic,
)
from core.ai import AI_CONFIG

if TYPE_CHECKING:
    from langchain_core.language_models import BaseChatModel


def _build_llm(cfg: dict) -> BaseChatModel:
    """Build an LLM instance from the centralized AI config dict.

    Uses lazy imports so only the chosen provider's package needs to be installed.
    """
    provider = cfg["provider"]

    if provider == "openai":
        from langchain_openai import ChatOpenAI

        kwargs: dict = {
            "model": cfg["model_name"],
            "temperature": 0.9,
        }
        if cfg["openai"]["api_key"]:
            kwargs["openai_api_key"] = cfg["openai"]["api_key"]
        if cfg["openai"]["base_url"]:
            kwargs["openai_api_base"] = cfg["openai"]["base_url"]
        return ChatOpenAI(**kwargs)

    if provider == "ollama":
        from langchain_ollama import ChatOllama

        return ChatOllama(
            model=cfg["model_name"],
            temperature=cfg["temperature"],
            base_url=cfg["ollama"]["base_url"],
        )

    raise ValueError(
        f"Unsupported LLM provider: {provider!r}. Use 'openai' or 'ollama'."
    )


class SkillDetectionAgent:
    """Agent that detects skills in text and maps them to the Neo4j skill graph.

    Uses a hybrid approach:
    1. LLM-based extraction of skill candidates (context-aware)
    2. Neo4j fulltext search for graph matching
    3. Semantic reranking with sentence-transformers

    Example::

        agent = SkillDetectionAgent()
        result = agent.detect("I work with Java and Spring Boot")
        for skill in result.matched_skills:
            print(f"{skill.name} ({skill.uid})")
    """

    def __init__(self, *, ai_config: dict | None = None) -> None:
        cfg = ai_config or AI_CONFIG
        self._llm = _build_llm(cfg)
        self._agent = create_agent(
            model=self._llm,
            tools=[search_skills_fulltext, search_skills_semantic],
            response_format=SkillDetectionResult,
            system_prompt=SYSTEM_PROMPT,
        )

    def detect(self, text: str) -> SkillDetectionResult:
        """Detect skills in the given text.

        Args:
            text: Free-form text to analyze for skills.

        Returns:
            SkillDetectionResult with matched and unknown skills.
        """
        result = self._agent.invoke(
            {"messages": [{"role": "user", "content": text}]},
        )
        return result["structured_response"]
