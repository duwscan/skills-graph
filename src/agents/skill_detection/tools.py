"""LangChain tools for searching skills in the Neo4j graph."""

from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from typing import Any

import numpy as np
from langchain_core.tools import tool
from neo4j import GraphDatabase

from core.ai import AI_CONFIG
from core.config import get_config

# ---------------------------------------------------------------------------
# Lucene escaping
# ---------------------------------------------------------------------------

_LUCENE_SPECIAL_CHARS = frozenset(r'+-&|!(){}[]^"~*?:\/.')


def _escape_lucene(query: str) -> str:
    """Escape special characters for the Neo4j fulltext (Lucene) query parser."""
    escaped: list[str] = []
    for ch in query:
        if ch in _LUCENE_SPECIAL_CHARS:
            escaped.append("\\")
        escaped.append(ch)
    return "".join(escaped)


# ---------------------------------------------------------------------------
# Neo4j driver helper
# ---------------------------------------------------------------------------


def _get_driver() -> tuple[Any, str]:
    """Get a Neo4j driver and database name from the existing config singleton."""
    cfg = get_config()
    driver = GraphDatabase.driver(cfg.uri, auth=(cfg.user, cfg.password))
    return driver, cfg.database


# ---------------------------------------------------------------------------
# Fulltext search tool (batch)
# ---------------------------------------------------------------------------

_FULLTEXT_QUERY = """\
CALL db.index.fulltext.queryNodes('skill_fulltext', $search_term)
YIELD node, score
RETURN node.uid AS uid,
       node.name AS name,
       score
ORDER BY score DESC
LIMIT $k
"""

_EXACT_QUERY = """\
MATCH (s:Skill)
WHERE s.name =~ $pattern
RETURN s.uid AS uid,
       s.name AS name
LIMIT 1
"""


def _search_single_fulltext(
    session: Any, query: str
) -> list[dict[str, Any]]:
    """Run exact + fulltext search for a single query within an open session."""
    results: list[dict[str, Any]] = []
    seen_uids: set[str] = set()

    # 1. Exact case-insensitive match
    pattern = f"(?i)^{re.escape(query)}$"
    for record in session.run(_EXACT_QUERY, pattern=pattern).data():
        uid = record["uid"]
        if uid not in seen_uids:
            seen_uids.add(uid)
            results.append({"uid": uid, "name": record["name"], "score": 1.0})

    # 2. Fulltext search
    escaped = _escape_lucene(query)
    if escaped.strip():
        for record in session.run(
            _FULLTEXT_QUERY, search_term=escaped, k=3
        ).data():
            uid = record["uid"]
            if uid not in seen_uids:
                seen_uids.add(uid)
                results.append({
                    "uid": uid,
                    "name": record["name"],
                    "score": round(record["score"], 1),
                })

    return results


@tool
def search_skills_fulltext(queries: list[str]) -> str:
    """Batch fulltext search. Pass all skill candidates as a list.

    Args:
        queries: Skill names to search (e.g. ["Java", "Docker"]).
    """
    driver, database = _get_driver()
    batch_results: dict[str, list[dict[str, Any]]] = {}

    try:
        with driver.session(database=database) as session:
            for q in queries:
                batch_results[q] = _search_single_fulltext(session, q)
    finally:
        driver.close()

    return json.dumps(batch_results, ensure_ascii=False)


# ---------------------------------------------------------------------------
# Semantic search tool (batch)
# ---------------------------------------------------------------------------


@dataclass
class _SkillEmbeddingIndex:
    """In-memory vector index of all skills for semantic search."""

    embeddings: Any  # np.ndarray (n_skills, dim)
    skill_data: list[dict[str, Any]] = field(default_factory=list)
    model: Any = None  # SentenceTransformer


_index_cache: _SkillEmbeddingIndex | None = None


def _build_composite_text(skill: dict[str, Any]) -> str:
    """Build a composite text string from a skill record for embedding."""
    parts = [skill["name"]]
    for key in ("high_surface_forms", "low_surface_forms", "abbreviations"):
        values = skill.get(key)
        if values:
            parts.extend(values)
    return " ".join(parts)


def _load_all_skills() -> list[dict[str, Any]]:
    """Fetch all Skill nodes from Neo4j."""
    driver, database = _get_driver()
    try:
        with driver.session(database=database) as session:
            records = session.run(
                "MATCH (s:Skill) "
                "RETURN s.uid AS uid, s.name AS name, s.skill_type AS skill_type, "
                "s.high_surface_forms AS high_surface_forms, "
                "s.low_surface_forms AS low_surface_forms, "
                "s.abbreviations AS abbreviations"
            ).data()
        return records
    finally:
        driver.close()


def _get_or_build_index() -> _SkillEmbeddingIndex:
    """Get or lazily build the in-memory embedding index of all skills."""
    global _index_cache
    if _index_cache is not None:
        return _index_cache

    from sentence_transformers import SentenceTransformer

    model_name = AI_CONFIG["embedding"]["model"]
    model = SentenceTransformer(model_name)

    skills = _load_all_skills()
    texts = [_build_composite_text(s) for s in skills]
    embeddings = model.encode(texts, normalize_embeddings=True)

    _index_cache = _SkillEmbeddingIndex(
        embeddings=np.array(embeddings),
        skill_data=skills,
        model=model,
    )
    return _index_cache


def reset_semantic_index() -> None:
    """Reset the cached embedding index (e.g. after skills are updated)."""
    global _index_cache
    _index_cache = None


@tool
def search_skills_semantic(queries: list[str]) -> str:
    """Batch semantic search. Pass all unmatched candidates as a list.

    Args:
        queries: Skill names to search (e.g. ["token based auth", "backend dev"]).
    """
    threshold = AI_CONFIG["embedding"]["semantic_threshold"]
    index = _get_or_build_index()

    # Encode all queries in one batch
    query_embeddings = index.model.encode(queries, normalize_embeddings=True)
    all_similarities = np.dot(query_embeddings, index.embeddings.T)

    batch_results: dict[str, list[dict[str, Any]]] = {}

    for i, q in enumerate(queries):
        similarities = all_similarities[i]
        ranked_indices = np.argsort(similarities)[::-1]
        matches: list[dict[str, Any]] = []

        for idx in ranked_indices[:3]:
            sim = float(similarities[idx])
            if sim < threshold:
                break
            skill = index.skill_data[idx]
            matches.append({
                "uid": skill["uid"],
                "name": skill["name"],
                "score": round(sim, 1),
            })

        batch_results[q] = matches

    return json.dumps(batch_results, ensure_ascii=False)
