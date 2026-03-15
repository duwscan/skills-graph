"""Neo4j configuration loaded from environment variables."""

from __future__ import annotations

import os
from dataclasses import dataclass

from dotenv import load_dotenv

load_dotenv()


@dataclass(frozen=True)
class Neo4jConfig:
    """Neo4j connection configuration loaded from environment variables.

    Attributes:
        uri: Neo4j connection URI (e.g., bolt://localhost:7687)
        user: Neo4j username
        password: Neo4j password
        database: Neo4j database name (default: neo4j)
    """

    uri: str
    user: str
    password: str
    database: str = "neo4j"

    @classmethod
    def from_env(cls) -> Neo4jConfig:
        """Load configuration from environment variables.

        Required: NEO4J_URI, NEO4J_USER, NEO4J_PASSWORD
        Optional: NEO4J_DATABASE (defaults to 'neo4j')
        """
        uri = os.environ.get("NEO4J_URI", "")
        user = os.environ.get("NEO4J_USER", "")
        password = os.environ.get("NEO4J_PASSWORD", "")

        missing: list[str] = []
        if not uri:
            missing.append("NEO4J_URI")
        if not user:
            missing.append("NEO4J_USER")
        if not password:
            missing.append("NEO4J_PASSWORD")

        if missing:
            msg = f"Missing required environment variables: {', '.join(missing)}"
            raise ValueError(msg)

        return cls(
            uri=uri,
            user=user,
            password=password,
            database=os.environ.get("NEO4J_DATABASE", "neo4j"),
        )

    @property
    def neomodel_url(self) -> str:
        """Build neomodel-compatible connection URL (bolt://user:password@host:port/database)."""
        uri = self.uri
        prefixes = ("bolt://", "neo4j://", "bolt+s://", "neo4j+s://")
        for prefix in prefixes:
            if uri.startswith(prefix):
                host_port = uri[len(prefix) :]
                break
        else:
            host_port = uri

        return f"bolt://{self.user}:{self.password}@{host_port}/{self.database}"


_config: Neo4jConfig | None = None


def get_config() -> Neo4jConfig:
    """Get the Neo4j configuration singleton."""
    global _config
    if _config is None:
        _config = Neo4jConfig.from_env()
    return _config


def reset_config() -> None:
    """Reset the configuration singleton (useful for testing)."""
    global _config
    _config = None
