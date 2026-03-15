# Skills Graph

A Python project for modeling skills as a graph using Neo4j and neomodel.

## Quick Start

```bash
# 1. Start Neo4j with Docker
make up

# 2. Setup environment
cp .env.example .env

# 3. Install dependencies
make install

# 4. Run smoke test
make test
```

Neo4j Browser: http://localhost:7474 (neo4j / skillsgraph123)

## Make Commands

```
make up       Start Neo4j container
make down     Stop Neo4j container
make logs     Show Neo4j logs
make shell    Open Python shell with models loaded
make test     Run smoke test
make lint     Run linter (ruff)
make format   Format code (ruff)
make clean    Remove cache files
```

## Setup (Manual)

1. Install dependencies with uv:
   ```bash
   uv sync
   ```

2. Start Neo4j:
   ```bash
   docker compose up -d
   ```

3. Copy `.env.example` to `.env`:
   ```bash
   cp .env.example .env
   ```

## Project Structure

```
.
├── config.py           # Neo4j configuration
├── db.py               # Database initialization and indexes
├── main.py             # Smoke test script
├── models/
│   ├── __init__.py     # Package exports
│   ├── skill.py        # Skill node model
│   ├── is_a_rel.py     # IS_A relationship
│   ├── requires_rel.py # REQUIRES relationship
│   └── related_to_rel.py # RELATED_TO relationship
└── pyproject.toml      # Project configuration
```

## Data Model

### Skill Node

- `uid`: Unique identifier (format: skill_[uuid])
- `name`: Human-readable skill name
- `skill_type`: Category (technical, soft, domain, tool)
- `high_surface_forms`: Primary aliases
- `low_surface_forms`: Secondary aliases
- `abbreviations`: Common abbreviations
- `created_at`, `updated_at`: Timestamps
- `version`: Schema version
- `confidence_score`: Confidence (0.0-1.0)

### Relationships

- **IS_A**: Skill hierarchy (weight, level, confidence)
- **REQUIRES**: Dependencies (weight, min_level, is_mandatory)
- **RELATED_TO**: Similarity (weight, relation_type)

## Indexes

The following indexes are created automatically:
- Fulltext index on name, surface forms, and abbreviations
- Range indexes on version and confidence_score
- Exact index on name and skill_type
