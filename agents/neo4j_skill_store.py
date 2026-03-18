"""Neo4j skill store helpers: fulltext search and bulk skill loading."""

from __future__ import annotations

import logging
from typing import Any

from neo4j import Driver

logger = logging.getLogger("agents.neo4j_skill_store")

_FULLTEXT_SEARCH_QUERY = """\
CALL db.index.fulltext.queryNodes('skill_fulltext', $q)
YIELD node, score
RETURN node.uid       AS uid,
       node.name      AS name,
       node.label     AS label,
       node.skill_type AS skill_type,
       score
ORDER BY score DESC
LIMIT $k
"""

_LOAD_ALL_SKILLS_QUERY = """\
MATCH (s:Skill)
RETURN s.uid                 AS uid,
       s.name                AS name,
       s.label               AS label,
       s.skill_type          AS skill_type,
       s.high_surface_forms  AS high_surface_forms,
       s.low_surface_forms   AS low_surface_forms,
       s.abbreviations       AS abbreviations
"""


def fulltext_search(
    query: str,
    driver: Driver,
    database: str,
    top_k: int = 8,
) -> list[dict[str, Any]]:
    """Run a fulltext search against the ``skill_fulltext`` Neo4j index.

    Args:
        query: Search query string.
        driver: Active Neo4j driver.
        database: Neo4j database name.
        top_k: Maximum number of results to return.

    Returns:
        List of dicts with keys: ``uid``, ``name``, ``label``, ``skill_type``, ``score``.
        Returns empty list on error or empty query.
    """
    safe_query = query.strip()
    if not safe_query:
        return []

    results: list[dict[str, Any]] = []
    try:
        with driver.session(database=database) as session:
            records = session.run(_FULLTEXT_SEARCH_QUERY, q=safe_query, k=top_k)
            for record in records:
                results.append(
                    {
                        "uid": record["uid"],
                        "name": record["name"],
                        "label": record["label"],
                        "skill_type": record["skill_type"],
                        "score": float(record["score"]),
                    }
                )
    except Exception as err:
        logger.warning("fulltext_search failed for query=%r: %s", safe_query, err)

    return results


def load_all_skills(
    driver: Driver,
    database: str,
) -> list[dict[str, Any]]:
    """Load all Skill nodes from Neo4j for building an in-memory embedding index.

    Each returned dict includes a ``text`` field that concatenates the skill
    name, label, and top surface forms / abbreviations — suitable for embedding.

    Args:
        driver: Active Neo4j driver.
        database: Neo4j database name.

    Returns:
        List of skill dicts with keys: ``uid``, ``name``, ``label``,
        ``skill_type``, ``high_surface_forms``, ``low_surface_forms``,
        ``abbreviations``, ``text``.
    """
    results: list[dict[str, Any]] = []
    try:
        with driver.session(database=database) as session:
            records = session.run(_LOAD_ALL_SKILLS_QUERY)
            for record in records:
                high_forms: list[str] = record["high_surface_forms"] or []
                low_forms: list[str] = record["low_surface_forms"] or []
                abbreviations: list[str] = record["abbreviations"] or []

                # Build a single text blob for embedding: name + label + surface forms + abbreviations
                parts: list[str] = [record["name"]]
                label = record["label"]
                if label and label != record["name"]:
                    parts.append(label)
                parts.extend(high_forms[:3])
                parts.extend(abbreviations[:2])

                results.append(
                    {
                        "uid": record["uid"],
                        "name": record["name"],
                        "label": label,
                        "skill_type": record["skill_type"],
                        "high_surface_forms": high_forms,
                        "low_surface_forms": low_forms,
                        "abbreviations": abbreviations,
                        "text": " | ".join(parts),
                    }
                )
    except Exception as err:
        logger.warning("load_all_skills failed: %s", err)

    return results
