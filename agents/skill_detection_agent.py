"""SkillDetectionAgent — detect and map technical skills in free-form text.

Architecture (four-step pipeline):

    A. **Extract candidates** — an LLM with structured output identifies skill
       mentions in the input text, returning confidence scores and context
       rationale so false positives like "I go to school" are filtered out.

    B. **Fulltext retrieval** — each candidate is queried against the Neo4j
       ``skill_fulltext`` index (name, surface forms, abbreviations) to obtain
       ranked candidates with relevance scores.

    C. **Semantic reranking** — a local sentence-transformers embedding model
       (default: ``all-MiniLM-L6-v2``) is used to compute cosine similarity
       between the candidate and every skill in an in-memory vector store.
       Only matches with similarity >= ``semantic_threshold`` (default 0.9)
       are accepted.  An "exact-hit override" rule accepts the best fulltext
       match when Neo4j returns a very high relevance score, preventing
       well-known skills like *Java* or *JWT* from being dropped due to
       short-embedding edge cases.

    D. **Decide output** — candidates that matched go into ``matched``; those
       with high ``is_skill_confidence`` but no graph match go into ``unknown``
       (potential candidates for extending the graph).

Usage::

    from langchain_openai import ChatOpenAI
    from langchain_huggingface import HuggingFaceEmbeddings
    from neo4j import GraphDatabase
    from agents import SkillDetectionAgent

    llm = ChatOpenAI(model="gpt-4o-mini", temperature=0)
    embeddings = HuggingFaceEmbeddings(model_name="sentence-transformers/all-MiniLM-L6-v2")
    driver = GraphDatabase.driver("bolt://localhost:7687", auth=("neo4j", "password"))

    agent = SkillDetectionAgent(llm=llm, neo4j_driver=driver, embeddings=embeddings)
    result = agent.detect("I have experience with token-based authentication at backend")

    for skill in result.matched:
        print(skill.uid, skill.name, skill.confidence)
    for unknown in result.unknown:
        print(unknown.canonical, unknown.confidence)
"""

from __future__ import annotations

import json
import logging
import threading
from typing import Any

from langchain.agents import create_agent
from langchain_core.documents import Document
from langchain_core.embeddings import Embeddings
from langchain_core.language_models import BaseChatModel
from langchain_core.tools import tool
from langchain_core.vectorstores import InMemoryVectorStore
from neo4j import Driver

from agents.neo4j_skill_store import fulltext_search, load_all_skills
from agents.schemas import (
    CandidateExtractionResult,
    MatchedSkill,
    MatchEvidence,
    SkillDetectionResult,
    UnknownSkill,
)

logger = logging.getLogger("agents.skill_detection_agent")

_EXTRACTION_SYSTEM_PROMPT = """\
You are an expert at identifying technical and professional skills in text.

Your task:
1. Read the input text carefully.
2. Extract every phrase that refers to a REAL technical/professional skill,
   technology, tool, framework, concept, or methodology.
3. For each candidate, assign an `is_skill_confidence` score (0–1) that reflects
   how certain you are — given the CONTEXT — that this phrase refers to a skill.
   Use a low score for ambiguous or non-skill uses (e.g. "go to school" → NOT Go language).
4. Provide a `canonical` name (the well-known, standard name for the skill).
5. Return an empty candidates list if no genuine skills are present.

Context awareness rules:
- "I go to school" → the word "go" refers to movement, NOT the Go language → confidence ≤ 0.1
- "I work with Java" → "Java" clearly refers to the programming language → confidence ≥ 0.95
- "token based authentication" → can be mapped to JWT and/or OAuth → add as separate candidates
"""

_AGENT_SYSTEM_PROMPT = """\
You are a skill-graph mapping assistant. Given a piece of text, you must:

1. Use the `extract_skill_candidates` tool to identify skill mentions in the text.
2. For each high-confidence candidate (is_skill_confidence >= 0.5), use BOTH:
   - `search_skills_fulltext` to find matches by name/abbreviation
   - `search_skills_semantic` to find matches by meaning
3. Combine the results and decide:
   - If a skill was found in the graph: include it in `matched` with its `uid`.
   - If a skill looks real but was NOT found: include it in `unknown` with confidence.
4. Return a SkillDetectionResult with `matched` and `unknown` lists.

Important:
- Only include skills in `matched` when you are confident they map to the correct graph entry.
- Discard candidates with is_skill_confidence < 0.5.
- De-duplicate: if two candidates map to the same uid, keep only the higher-confidence one.
"""

# Minimum Neo4j fulltext score to consider an exact-hit override for semantic threshold
_FULLTEXT_EXACT_HIT_THRESHOLD = 3.0

# Minimum is_skill_confidence for a candidate to be considered at all
_MIN_IS_SKILL_CONFIDENCE = 0.5

# Minimum is_skill_confidence for an unmatched candidate to appear in `unknown`
_MIN_UNKNOWN_CONFIDENCE = 0.65


class SkillDetectionAgent:
    """Detects and maps skills in free-form text to UIDs in the skills graph.

    Args:
        llm: A LangChain chat model used for candidate extraction and agent reasoning.
        neo4j_driver: An active Neo4j driver instance.
        neo4j_database: The Neo4j database name (default: ``"neo4j"``).
        embeddings: A LangChain embeddings model for semantic search.
        semantic_threshold: Cosine similarity threshold for accepting a semantic
            match (default: 0.9).
        fulltext_top_k: Number of fulltext results to retrieve per candidate
            (default: 8).
        unknown_confidence_threshold: Minimum ``is_skill_confidence`` for an
            unmatched candidate to be reported in the ``unknown`` list.
    """

    def __init__(
        self,
        llm: BaseChatModel,
        neo4j_driver: Driver,
        neo4j_database: str = "neo4j",
        embeddings: Embeddings | None = None,
        semantic_threshold: float = 0.9,
        fulltext_top_k: int = 8,
        unknown_confidence_threshold: float = _MIN_UNKNOWN_CONFIDENCE,
    ) -> None:
        self._llm = llm
        self._driver = neo4j_driver
        self._database = neo4j_database
        self._embeddings = embeddings
        self._semantic_threshold = semantic_threshold
        self._fulltext_top_k = fulltext_top_k
        self._unknown_confidence_threshold = unknown_confidence_threshold

        # Lazy-loaded vector store (populated on first use)
        self._vector_store: InMemoryVectorStore | None = None
        self._skills_by_uid: dict[str, dict[str, Any]] = {}
        self._vector_store_lock = threading.Lock()

        # Extraction chain: LLM with structured output for Step A
        self._extraction_chain = self._llm.with_structured_output(CandidateExtractionResult)

        # Build agent with tools for orchestration
        self._agent = create_agent(
            model=self._llm,
            tools=self._build_tools(),
            system_prompt=_AGENT_SYSTEM_PROMPT,
            response_format=SkillDetectionResult,
        )

    # ------------------------------------------------------------------
    # Public interface
    # ------------------------------------------------------------------

    def detect(self, text: str) -> SkillDetectionResult:
        """Detect and map skills in *text* to UIDs in the skills graph.

        Args:
            text: A sentence or chunk of text to analyse.

        Returns:
            A :class:`SkillDetectionResult` with ``matched`` and ``unknown`` lists.

        Raises:
            ValueError: If *text* is empty.
        """
        if not text or not text.strip():
            raise ValueError("Input text must not be empty.")

        logger.debug("detect called, text length=%d", len(text))

        result = self._agent.invoke(
            {
                "messages": [
                    {
                        "role": "user",
                        "content": (
                            "Detect and map skills in the following text:\n\n"
                            f"{text.strip()}"
                        ),
                    }
                ]
            },
            config={"recursion_limit": 20},
        )
        return result["structured_response"]

    # ------------------------------------------------------------------
    # Vector store (lazy)
    # ------------------------------------------------------------------

    def _ensure_vector_store(self) -> None:
        """Build the in-memory vector store from all Neo4j skills (once)."""
        if self._vector_store is not None:
            return
        if self._embeddings is None:
            return

        with self._vector_store_lock:
            if self._vector_store is not None:
                return  # double-check after acquiring lock

            logger.info("Building in-memory skill vector store…")
            all_skills = load_all_skills(self._driver, self._database)
            if not all_skills:
                logger.warning("No skills loaded from Neo4j — semantic search unavailable.")
                return

            self._skills_by_uid = {s["uid"]: s for s in all_skills}
            docs = [
                Document(page_content=s["text"], metadata={"uid": s["uid"]})
                for s in all_skills
            ]
            vs = InMemoryVectorStore(embedding=self._embeddings)
            vs.add_documents(docs)
            self._vector_store = vs
            logger.info("Vector store built with %d skills.", len(all_skills))

    def _semantic_search(
        self, query: str, top_k: int = 5
    ) -> list[dict[str, Any]]:
        """Search the in-memory vector store for semantically similar skills.

        Returns a list of dicts with keys: ``uid``, ``name``, ``label``,
        ``skill_type``, ``semantic_score``.
        Returns empty list when embeddings are unavailable or store is empty.
        """
        self._ensure_vector_store()
        if self._vector_store is None:
            return []

        results: list[dict[str, Any]] = []
        try:
            hits = self._vector_store.similarity_search_with_score(query, k=top_k)
            for doc, score in hits:
                uid = doc.metadata.get("uid", "")
                skill = self._skills_by_uid.get(uid, {})
                results.append(
                    {
                        "uid": uid,
                        "name": skill.get("name", ""),
                        "label": skill.get("label"),
                        "skill_type": skill.get("skill_type", ""),
                        "semantic_score": float(score),
                    }
                )
        except Exception as err:
            logger.warning("semantic_search failed for query=%r: %s", query, err)

        return results

    # ------------------------------------------------------------------
    # Tools
    # ------------------------------------------------------------------

    def _build_tools(self) -> list[Any]:
        """Build and return the LangChain tools used by the agent."""
        agent_self = self  # capture reference for closures

        @tool
        def extract_skill_candidates(text: str) -> str:
            """Extract skill candidates from a piece of text using structured LLM output.

            Use this first to identify what skills might be mentioned in the input.

            Args:
                text: The raw input text to analyse for skill mentions.

            Returns:
                JSON string with a ``candidates`` list. Each candidate has:
                ``text``, ``canonical``, ``type_hint``, ``rationale``,
                ``is_skill_confidence`` (0–1).
            """
            try:
                result: CandidateExtractionResult = agent_self._extraction_chain.invoke(
                    [
                        {"role": "system", "content": _EXTRACTION_SYSTEM_PROMPT},
                        {"role": "user", "content": text},
                    ]
                )
                return result.model_dump_json()
            except Exception as err:
                logger.warning("extract_skill_candidates failed: %s", err)
                return CandidateExtractionResult(candidates=[]).model_dump_json()

        @tool
        def search_skills_fulltext(query: str) -> str:
            """Search the skills graph using the Neo4j fulltext index.

            Matches against skill names, surface forms, and abbreviations.
            Use this for keyword-based lookup (e.g. "JWT", "Python", "OAuth").

            Args:
                query: Search term — a skill name, abbreviation, or phrase.

            Returns:
                JSON array of up to 8 matches. Each entry has:
                ``uid``, ``name``, ``label``, ``skill_type``, ``score``.
            """
            results = fulltext_search(
                query,
                agent_self._driver,
                agent_self._database,
                agent_self._fulltext_top_k,
            )
            return json.dumps(results)

        @tool
        def search_skills_semantic(candidate_text: str) -> str:
            """Search the skills graph using semantic (embedding) similarity.

            Use this to handle paraphrases and synonyms that keyword search may miss.
            Only consider results with ``semantic_score`` >= 0.9 as strong matches.

            Args:
                candidate_text: The canonical skill name or phrase to search for.

            Returns:
                JSON array of up to 5 semantic matches. Each entry has:
                ``uid``, ``name``, ``label``, ``skill_type``, ``semantic_score``.
            """
            results = agent_self._semantic_search(candidate_text)
            return json.dumps(results)

        return [extract_skill_candidates, search_skills_fulltext, search_skills_semantic]

    # ------------------------------------------------------------------
    # Direct pipeline (non-agent path, used internally for testing)
    # ------------------------------------------------------------------

    def detect_pipeline(self, text: str) -> SkillDetectionResult:
        """Run the deterministic four-step pipeline directly (no agent loop).

        This is useful for testing and for environments without an LLM API key.
        It requires an LLM capable of structured output.

        Args:
            text: Input text to analyse.

        Returns:
            :class:`SkillDetectionResult`
        """
        if not text or not text.strip():
            raise ValueError("Input text must not be empty.")

        # Step A — extract candidates
        try:
            extraction: CandidateExtractionResult = self._extraction_chain.invoke(
                [
                    {"role": "system", "content": _EXTRACTION_SYSTEM_PROMPT},
                    {"role": "user", "content": text.strip()},
                ]
            )
        except Exception as err:
            raise RuntimeError(f"Candidate extraction failed: {err}") from err

        matched: dict[str, MatchedSkill] = {}  # uid → MatchedSkill (de-dup)
        unknown_map: dict[str, UnknownSkill] = {}  # canonical → UnknownSkill

        for candidate in extraction.candidates:
            if candidate.is_skill_confidence < _MIN_IS_SKILL_CONFIDENCE:
                logger.debug(
                    "Skipping low-confidence candidate %r (score=%.2f)",
                    candidate.canonical,
                    candidate.is_skill_confidence,
                )
                continue

            best_match = self._resolve_candidate(candidate)

            if best_match is not None:
                uid = best_match.uid
                # Keep the highest-confidence match per uid
                if uid not in matched or best_match.confidence > matched[uid].confidence:
                    matched[uid] = best_match
            elif candidate.is_skill_confidence >= self._unknown_confidence_threshold:
                key = candidate.canonical.lower()
                if key not in unknown_map or (
                    candidate.is_skill_confidence
                    > unknown_map[key].confidence
                ):
                    unknown_map[key] = UnknownSkill(
                        text=candidate.text,
                        canonical=candidate.canonical,
                        confidence=candidate.is_skill_confidence,
                    )

        return SkillDetectionResult(
            matched=list(matched.values()),
            unknown=list(unknown_map.values()),
        )

    def _resolve_candidate(self, candidate: Any) -> MatchedSkill | None:
        """Attempt to map a single candidate to a skill in the graph.

        Tries multiple query variants (text, canonical, abbreviations) against
        both fulltext and semantic search, then applies the thresholding rules.
        """
        query_variants = _build_query_variants(candidate.text, candidate.canonical)

        # Step B — fulltext search across all variants
        fulltext_hits: list[dict[str, Any]] = []
        for variant in query_variants:
            hits = fulltext_search(
                variant, self._driver, self._database, self._fulltext_top_k
            )
            fulltext_hits.extend(hits)

        # Deduplicate fulltext hits, keep best score per uid
        best_fulltext: dict[str, dict[str, Any]] = {}
        for hit in fulltext_hits:
            uid = hit["uid"]
            if uid not in best_fulltext or hit["score"] > best_fulltext[uid]["score"]:
                best_fulltext[uid] = hit

        # Step C — semantic search
        self._ensure_vector_store()
        semantic_hits: list[dict[str, Any]] = []
        for variant in query_variants[:2]:  # limit to top 2 variants for speed
            s_hits = self._semantic_search(variant, top_k=5)
            semantic_hits.extend(s_hits)

        best_semantic: dict[str, dict[str, Any]] = {}
        for hit in semantic_hits:
            uid = hit["uid"]
            if uid not in best_semantic or hit["semantic_score"] > best_semantic[uid]["semantic_score"]:
                best_semantic[uid] = hit

        # Step D — apply thresholds and pick winner
        candidate_uids = set(best_fulltext) | set(best_semantic)

        best_result: MatchedSkill | None = None
        best_confidence = 0.0

        for uid in candidate_uids:
            ft_hit = best_fulltext.get(uid)
            sem_hit = best_semantic.get(uid)

            ft_score = ft_hit["score"] if ft_hit else 0.0
            sem_score = sem_hit["semantic_score"] if sem_hit else 0.0

            # Accept if semantic threshold met
            semantic_accept = sem_score >= self._semantic_threshold
            # Accept via exact-hit override if fulltext score is very high
            exact_override = ft_score >= _FULLTEXT_EXACT_HIT_THRESHOLD and ft_hit is not None

            if not (semantic_accept or exact_override):
                continue

            # Determine method and confidence
            if semantic_accept and ft_score > 0:
                method: str = "semantic"
                confidence = _combine_scores(sem_score, ft_score)
            elif semantic_accept:
                method = "semantic"
                confidence = sem_score
            else:
                method = "exact_override"
                # Normalize fulltext score to 0–1 range (heuristic cap at 10)
                confidence = min(ft_score / 10.0, 1.0)

            if confidence > best_confidence:
                best_confidence = confidence
                source = ft_hit if ft_hit else sem_hit
                assert source is not None  # guaranteed by the loop logic
                best_result = MatchedSkill(
                    uid=uid,
                    name=source["name"],
                    label=source.get("label"),
                    skill_type=source.get("skill_type", ""),
                    confidence=round(confidence, 4),
                    evidence=MatchEvidence(
                        method=method,  # type: ignore[arg-type]
                        fulltext_score=ft_score if ft_score > 0 else None,
                        semantic_score=sem_score if sem_score > 0 else None,
                    ),
                )

        return best_result


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _build_query_variants(text: str, canonical: str) -> list[str]:
    """Build a deduplicated list of query strings for a candidate.

    Includes the original text, the canonical name, and a lowercased variant
    of each to maximise fulltext index coverage.
    """
    seen: set[str] = set()
    variants: list[str] = []

    for v in (canonical, text, canonical.lower(), text.lower()):
        v = v.strip()
        if v and v not in seen:
            seen.add(v)
            variants.append(v)

    return variants


def _combine_scores(semantic: float, fulltext: float, cap: float = 10.0) -> float:
    """Blend semantic cosine similarity with a normalized fulltext score.

    The semantic score (0–1) is weighted at 70 %; the normalized fulltext
    score (0–1) is weighted at 30 %.
    """
    normalized_ft = min(fulltext / cap, 1.0)
    return round(0.7 * semantic + 0.3 * normalized_ft, 4)
