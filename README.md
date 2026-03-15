# Skills Graph

A Python project for modeling skills as a graph using Neo4j and neomodel.

## Setup

1. Install dependencies with uv:
   ```bash
   uv sync
   ```

2. Copy `.env.example` to `.env` and configure your Neo4j connection:
   ```bash
   cp .env.example .env
   ```

3. Edit `.env` with your Neo4j credentials:
   ```
   NEO4J_URI=bolt://localhost:7687
   NEO4J_USER=neo4j
   NEO4J_PASSWORD=your_password
   NEO4J_DATABASE=neo4j
   ```

## Running the Smoke Test

```bash
uv run main.py
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
