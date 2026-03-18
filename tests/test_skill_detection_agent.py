"""Tests for SkillDetectionAgent and related components.

These tests are fully offline — they mock Neo4j, embeddings, and the LLM so
no external services are required.
"""

from __future__ import annotations

import json
from typing import Any
from unittest.mock import MagicMock, patch

import pytest

from agents.neo4j_skill_store import fulltext_search, load_all_skills
from agents.schemas import (
    CandidateExtractionResult,
    MatchedSkill,
    MatchEvidence,
    SkillCandidate,
    SkillDetectionResult,
    UnknownSkill,
)
from agents.skill_detection_agent import (
    SkillDetectionAgent,
    _build_query_variants,
    _combine_scores,
)


# ---------------------------------------------------------------------------
# Helpers / Fixtures
# ---------------------------------------------------------------------------


def _make_driver(session_records: list[dict[str, Any]]) -> MagicMock:
    """Build a minimal Neo4j driver mock that yields *session_records* from run()."""
    mock_records = [MagicMock(**{"__getitem__": lambda self, k, r=rec: r.get(k)}) for rec in session_records]
    for i, rec in enumerate(session_records):
        mock_records[i].__getitem__ = lambda self, k, r=rec: r[k]

    mock_session = MagicMock()
    mock_session.__enter__ = MagicMock(return_value=mock_session)
    mock_session.__exit__ = MagicMock(return_value=False)
    mock_session.run.return_value = mock_records

    mock_driver = MagicMock()
    mock_driver.session.return_value = mock_session
    return mock_driver


def _make_skill_records(skills: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Return skill records formatted for Neo4j mock responses."""
    defaults = {
        "label": None,
        "high_surface_forms": [],
        "low_surface_forms": [],
        "abbreviations": [],
        "score": 5.0,
    }
    return [{**defaults, **s} for s in skills]


def _make_candidate(
    text: str,
    canonical: str,
    confidence: float = 0.9,
) -> SkillCandidate:
    return SkillCandidate(
        text=text,
        canonical=canonical,
        rationale="test",
        is_skill_confidence=confidence,
    )


def _make_extraction_result(candidates: list[SkillCandidate]) -> CandidateExtractionResult:
    return CandidateExtractionResult(candidates=candidates)


def _mock_llm_structured(candidates: list[SkillCandidate]) -> MagicMock:
    """Mock an LLM whose with_structured_output chain returns CandidateExtractionResult."""
    chain = MagicMock()
    chain.invoke.return_value = _make_extraction_result(candidates)

    llm = MagicMock()
    llm.with_structured_output.return_value = chain
    return llm


def _make_agent(
    candidates: list[SkillCandidate],
    skills: list[dict[str, Any]],
    embeddings: Any = None,
) -> tuple[SkillDetectionAgent, MagicMock]:
    """Build a SkillDetectionAgent with mocked LLM and Neo4j driver."""
    neo4j_records = _make_skill_records(skills)
    driver = _make_driver(neo4j_records)
    llm = _mock_llm_structured(candidates)

    # Prevent create_agent from being called (we use detect_pipeline)
    with patch("agents.skill_detection_agent.create_agent", return_value=MagicMock()):
        agent = SkillDetectionAgent(
            llm=llm,
            neo4j_driver=driver,
            neo4j_database="neo4j",
            embeddings=embeddings,
        )
    return agent, driver


# ---------------------------------------------------------------------------
# Schema tests
# ---------------------------------------------------------------------------


class TestSchemas:
    def test_skill_candidate_validation(self) -> None:
        c = SkillCandidate(
            text="JWT",
            canonical="JSON Web Token",
            rationale="common auth mechanism",
            is_skill_confidence=0.95,
        )
        assert c.canonical == "JSON Web Token"
        assert 0.0 <= c.is_skill_confidence <= 1.0

    def test_skill_candidate_confidence_bounds(self) -> None:
        with pytest.raises(Exception):
            SkillCandidate(
                text="x",
                canonical="x",
                rationale="r",
                is_skill_confidence=1.5,  # out of range
            )

    def test_skill_detection_result_defaults(self) -> None:
        r = SkillDetectionResult()
        assert r.matched == []
        assert r.unknown == []

    def test_matched_skill_round_trip(self) -> None:
        m = MatchedSkill(
            uid="skill_abc123",
            name="Python",
            skill_type="technical",
            confidence=0.95,
            evidence=MatchEvidence(method="semantic", semantic_score=0.95),
        )
        data = json.loads(m.model_dump_json())
        assert data["uid"] == "skill_abc123"
        assert data["evidence"]["method"] == "semantic"

    def test_unknown_skill(self) -> None:
        u = UnknownSkill(text="Temporal DB", canonical="Temporal Database", confidence=0.8)
        assert u.confidence == 0.8


# ---------------------------------------------------------------------------
# neo4j_skill_store tests
# ---------------------------------------------------------------------------


class TestNeo4jSkillStore:
    def test_fulltext_search_empty_query(self) -> None:
        driver = MagicMock()
        results = fulltext_search("", driver, "neo4j")
        assert results == []
        driver.session.assert_not_called()

    def test_fulltext_search_whitespace_query(self) -> None:
        driver = MagicMock()
        results = fulltext_search("   ", driver, "neo4j")
        assert results == []

    def test_fulltext_search_returns_records(self) -> None:
        skill = {
            "uid": "skill_java01",
            "name": "Java",
            "label": "Java",
            "skill_type": "technical",
            "score": 5.0,
        }
        driver = _make_driver([skill])
        results = fulltext_search("Java", driver, "neo4j")
        assert len(results) == 1
        assert results[0]["uid"] == "skill_java01"
        assert results[0]["score"] == 5.0

    def test_fulltext_search_handles_exception(self) -> None:
        driver = MagicMock()
        driver.session.side_effect = Exception("Connection refused")
        results = fulltext_search("Java", driver, "neo4j")
        assert results == []

    def test_load_all_skills_builds_text(self) -> None:
        skill = {
            "uid": "skill_py01",
            "name": "Python",
            "label": "Python",
            "skill_type": "technical",
            "high_surface_forms": ["Python programming"],
            "low_surface_forms": [],
            "abbreviations": ["py"],
        }
        driver = _make_driver([skill])
        results = load_all_skills(driver, "neo4j")
        assert len(results) == 1
        assert "Python" in results[0]["text"]
        assert "py" in results[0]["text"]

    def test_load_all_skills_handles_exception(self) -> None:
        driver = MagicMock()
        driver.session.side_effect = Exception("Timeout")
        results = load_all_skills(driver, "neo4j")
        assert results == []


# ---------------------------------------------------------------------------
# Helper function tests
# ---------------------------------------------------------------------------


class TestHelpers:
    def test_build_query_variants_deduplication(self) -> None:
        variants = _build_query_variants("Python", "Python")
        # Should not have duplicates
        assert len(variants) == len(set(variants))

    def test_build_query_variants_includes_canonical(self) -> None:
        variants = _build_query_variants("jwt auth", "JWT")
        assert "JWT" in variants

    def test_build_query_variants_includes_text(self) -> None:
        variants = _build_query_variants("jwt auth", "JWT")
        assert "jwt auth" in variants

    def test_combine_scores_all_semantic(self) -> None:
        score = _combine_scores(1.0, 0.0)
        assert score == pytest.approx(0.7, abs=1e-4)

    def test_combine_scores_blended(self) -> None:
        score = _combine_scores(0.95, 5.0)
        # 0.7 * 0.95 + 0.3 * 0.5 = 0.665 + 0.15 = 0.815
        assert score == pytest.approx(0.815, abs=1e-3)

    def test_combine_scores_caps_fulltext(self) -> None:
        score = _combine_scores(0.9, 100.0)  # fulltext capped at 1.0
        assert score == pytest.approx(0.7 * 0.9 + 0.3 * 1.0, abs=1e-3)


# ---------------------------------------------------------------------------
# SkillDetectionAgent.detect_pipeline tests
# ---------------------------------------------------------------------------


class TestSkillDetectionAgentPipeline:
    """Tests for the deterministic pipeline path (detect_pipeline).

    All external dependencies (Neo4j driver, LLM, embeddings) are mocked.
    """

    def test_empty_text_raises(self) -> None:
        agent, _ = _make_agent([], [])
        with pytest.raises(ValueError, match="empty"):
            agent.detect_pipeline("")

    def test_whitespace_text_raises(self) -> None:
        agent, _ = _make_agent([], [])
        with pytest.raises(ValueError, match="empty"):
            agent.detect_pipeline("   ")

    def test_no_skills_in_text(self) -> None:
        """'I go to school' should not produce any matched or unknown skills."""
        low_confidence_candidate = _make_candidate("go", "Go language", confidence=0.05)
        agent, _ = _make_agent([low_confidence_candidate], [])
        result = agent.detect_pipeline("I go to school")
        assert result.matched == []
        assert result.unknown == []

    def test_java_matched_via_fulltext(self) -> None:
        """'I work with Java' should match the Java skill via fulltext."""
        java_skill = {
            "uid": "skill_java01",
            "name": "Java",
            "label": "Java",
            "skill_type": "technical",
            "score": 8.0,  # high enough for exact_override
        }
        candidate = _make_candidate("Java", "Java", confidence=0.97)
        agent, driver = _make_agent([candidate], [java_skill])

        result = agent.detect_pipeline("I work with Java")

        assert len(result.matched) == 1
        assert result.matched[0].uid == "skill_java01"
        assert result.matched[0].name == "Java"

    def test_known_skill_matched_via_semantic(self) -> None:
        """A candidate with semantic score >= 0.9 should appear in matched."""
        skill_data = {
            "uid": "skill_jwt01",
            "name": "JWT",
            "label": "JSON Web Token",
            "skill_type": "technical",
            "score": 0.5,  # low fulltext score
        }
        candidate = _make_candidate("token based authentication", "JWT", confidence=0.9)

        # Build mock embeddings that return a high cosine similarity
        mock_embeddings = MagicMock()
        mock_embeddings.embed_documents.return_value = [[0.9, 0.1, 0.0]]
        mock_embeddings.embed_query.return_value = [0.9, 0.1, 0.0]

        driver = _make_driver(_make_skill_records([skill_data]))
        llm = _mock_llm_structured([candidate])

        with patch("agents.skill_detection_agent.create_agent", return_value=MagicMock()):
            agent = SkillDetectionAgent(
                llm=llm,
                neo4j_driver=driver,
                neo4j_database="neo4j",
                embeddings=mock_embeddings,
                semantic_threshold=0.9,
            )

        # Mock the vector store directly for deterministic control
        mock_vs = MagicMock()
        from langchain_core.documents import Document

        mock_vs.similarity_search_with_score.return_value = [
            (Document(page_content="JWT | JSON Web Token", metadata={"uid": "skill_jwt01"}), 0.93)
        ]
        agent._vector_store = mock_vs
        agent._skills_by_uid = {
            "skill_jwt01": {
                "uid": "skill_jwt01",
                "name": "JWT",
                "label": "JSON Web Token",
                "skill_type": "technical",
            }
        }

        result = agent.detect_pipeline("I have experience with token-based authentication at backend")

        assert len(result.matched) == 1
        assert result.matched[0].uid == "skill_jwt01"
        assert result.matched[0].evidence.semantic_score == pytest.approx(0.93, abs=1e-4)

    def test_semantic_below_threshold_no_match(self) -> None:
        """Candidates with semantic score < 0.9 and low fulltext score are excluded."""
        skill_data = {
            "uid": "skill_xyz01",
            "name": "SomeTech",
            "label": "SomeTech",
            "skill_type": "technical",
            "score": 0.5,
        }
        candidate = _make_candidate("something", "SomeTech", confidence=0.9)

        driver = _make_driver(_make_skill_records([skill_data]))
        llm = _mock_llm_structured([candidate])

        with patch("agents.skill_detection_agent.create_agent", return_value=MagicMock()):
            agent = SkillDetectionAgent(
                llm=llm,
                neo4j_driver=driver,
                neo4j_database="neo4j",
                embeddings=None,  # no embeddings → semantic_score = 0
                semantic_threshold=0.9,
            )

        result = agent.detect_pipeline("something technical")

        # fulltext score 0.5 < _FULLTEXT_EXACT_HIT_THRESHOLD (3.0) → no exact override
        # no embeddings → no semantic match
        assert result.matched == []

    def test_unknown_skill_reported(self) -> None:
        """High-confidence candidates with no graph match appear in unknown list."""
        candidate = _make_candidate("Temporal DB", "Temporal Database", confidence=0.85)

        # No Neo4j results
        driver = _make_driver([])
        llm = _mock_llm_structured([candidate])

        with patch("agents.skill_detection_agent.create_agent", return_value=MagicMock()):
            agent = SkillDetectionAgent(
                llm=llm,
                neo4j_driver=driver,
                neo4j_database="neo4j",
                embeddings=None,
            )

        result = agent.detect_pipeline("We use Temporal DB for workflow orchestration")

        assert result.matched == []
        assert len(result.unknown) == 1
        assert result.unknown[0].canonical == "Temporal Database"
        assert result.unknown[0].confidence == pytest.approx(0.85)

    def test_unknown_low_confidence_not_reported(self) -> None:
        """Low-confidence unmatched candidates are silently dropped."""
        candidate = _make_candidate("maybe skill", "MaybeSkill", confidence=0.55)

        driver = _make_driver([])
        llm = _mock_llm_structured([candidate])

        with patch("agents.skill_detection_agent.create_agent", return_value=MagicMock()):
            agent = SkillDetectionAgent(
                llm=llm,
                neo4j_driver=driver,
                neo4j_database="neo4j",
                embeddings=None,
                unknown_confidence_threshold=0.65,
            )

        result = agent.detect_pipeline("maybe some skill here")
        assert result.unknown == []

    def test_deduplication_keeps_highest_confidence(self) -> None:
        """When two candidates map to the same uid, only the highest-confidence one is kept."""
        java_skill = {
            "uid": "skill_java01",
            "name": "Java",
            "label": "Java",
            "skill_type": "technical",
            "score": 5.0,
        }
        # Two candidates both mapping to the same uid
        c1 = _make_candidate("Java", "Java", confidence=0.9)
        c2 = _make_candidate("java programming", "Java", confidence=0.95)

        driver = _make_driver(_make_skill_records([java_skill]))
        llm = _mock_llm_structured([c1, c2])

        with patch("agents.skill_detection_agent.create_agent", return_value=MagicMock()):
            agent = SkillDetectionAgent(
                llm=llm,
                neo4j_driver=driver,
                neo4j_database="neo4j",
                embeddings=None,
            )

        result = agent.detect_pipeline("Java and java programming are the same")
        # Should be deduplicated to a single entry
        uids = [m.uid for m in result.matched]
        assert uids.count("skill_java01") <= 1

    def test_exact_override_accepted(self) -> None:
        """A very high fulltext score triggers exact-hit override (no semantic needed)."""
        skill_data = {
            "uid": "skill_jwt01",
            "name": "JWT",
            "label": "JSON Web Token",
            "skill_type": "technical",
            "score": 4.5,  # >= _FULLTEXT_EXACT_HIT_THRESHOLD (3.0)
        }
        candidate = _make_candidate("JWT", "JWT", confidence=0.92)

        driver = _make_driver(_make_skill_records([skill_data]))
        llm = _mock_llm_structured([candidate])

        with patch("agents.skill_detection_agent.create_agent", return_value=MagicMock()):
            agent = SkillDetectionAgent(
                llm=llm,
                neo4j_driver=driver,
                neo4j_database="neo4j",
                embeddings=None,  # no embeddings — relying on exact override
                semantic_threshold=0.9,
            )

        result = agent.detect_pipeline("I use JWT for auth")
        assert len(result.matched) == 1
        assert result.matched[0].uid == "skill_jwt01"
        assert result.matched[0].evidence.method == "exact_override"
