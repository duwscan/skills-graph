.PHONY: up down logs shell test test-unit test-e2e lint format clean install sync \
        graph-fresh seed-all serve help

help:
	@echo "Skills Graph - Available commands:"
	@echo ""
	@echo "  make up          Start Neo4j container"
	@echo "  make down        Stop Neo4j container"
	@echo "  make logs        Show Neo4j logs"
	@echo "  make shell       Open Python shell with models loaded"
	@echo "  make test        Run all tests"
	@echo "  make test-unit   Run unit tests only"
	@echo "  make test-e2e    Run E2E tests only"
	@echo "  make lint        Run linter (ruff)"
	@echo "  make format      Format code (ruff)"
	@echo "  make clean       Remove cache and compiled files"
	@echo "  make install     Install all dependencies"
	@echo "  make sync        Sync dependencies"
	@echo "  make graph-fresh Clear all data in Neo4j graph"
	@echo "  make seed-all    Seed all JSON data files into Neo4j"
	@echo "  make serve       Start FastAPI dev server"

up:
	docker compose up -d
	@echo "Neo4j starting... Browser at http://localhost:7474"
	@echo "Credentials: neo4j / skillsgraph123"

down:
	docker compose down

logs:
	docker compose logs -f neo4j

shell:
	uv run python -i -c "from core.db import init_db; init_db(); from models import *; print('Models loaded: Skill, IsARel, RequiresRel, RelatedToRel')"

test:
	uv run pytest tests/

test-unit:
	uv run pytest tests/unit/

test-e2e:
	uv run pytest tests/e2e/ -m e2e

lint:
	uv run ruff check src/ tests/

format:
	uv run ruff format src/ tests/

clean:
	find . -type d -name "__pycache__" -exec rm -rf {} + 2>/dev/null || true
	find . -type d -name ".ruff_cache" -exec rm -rf {} + 2>/dev/null || true
	find . -type d -name ".mypy_cache" -exec rm -rf {} + 2>/dev/null || true
	find . -type f -name "*.pyc" -delete 2>/dev/null || true

install:
	uv sync --all-extras

sync:
	uv sync --all-extras

graph-fresh:
	uv run python -c "from core.db import init_db; import neomodel; init_db(); neomodel.db.cypher_query('MATCH (n) DETACH DELETE n'); print('Graph cleared')"

seed-all:
	uv run skills-seed

serve:
	uv run uvicorn api.app:app --reload --host 0.0.0.0 --port 8000
