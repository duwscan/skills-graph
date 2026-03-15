"""Database initialization and index management."""

from __future__ import annotations

from typing import LiteralString

from neo4j import GraphDatabase

import neomodel

from config import get_config


def init_db() -> None:
    """Initialize neomodel connection using environment configuration."""
    config = get_config()
    neomodel.config.DATABASE_URL = config.neomodel_url  # type: ignore[misc]


def test_connection() -> bool:
    """Test Neo4j connectivity. Returns True if connection succeeds."""
    config = get_config()
    driver = GraphDatabase.driver(
        config.uri,
        auth=(config.user, config.password),
    )
    try:
        driver.verify_connectivity()
        return True
    except Exception:
        return False
    finally:
        driver.close()


INDEX_QUERIES: tuple[LiteralString, ...] = (
    "CREATE FULLTEXT INDEX skill_fulltext IF NOT EXISTS FOR (s:Skill) ON EACH [s.name, s.high_surface_forms, s.low_surface_forms, s.abbreviations]",
    "CREATE RANGE INDEX skill_version_range IF NOT EXISTS FOR (s:Skill) ON (s.version)",
    "CREATE RANGE INDEX skill_confidence_range IF NOT EXISTS FOR (s:Skill) ON (s.confidence_score)",
    "CREATE INDEX skill_name_exact IF NOT EXISTS FOR (s:Skill) ON (s.name)",
    "CREATE INDEX skill_type_idx IF NOT EXISTS FOR (s:Skill) ON (s.skill_type)",
)


def ensure_indexes() -> None:
    """Create required indexes for Skill nodes if they don't exist."""
    config = get_config()
    driver = GraphDatabase.driver(
        config.uri,
        auth=(config.user, config.password),
    )
    try:
        with driver.session(database=config.database) as session:
            for query in INDEX_QUERIES:
                session.run(query)
    finally:
        driver.close()
