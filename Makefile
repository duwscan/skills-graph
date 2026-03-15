.PHONY: up down logs shell test clean install sync help

help:
	@echo "Skills Graph - Available commands:"
	@echo ""
	@echo "  make up       Start Neo4j container"
	@echo "  make down     Stop Neo4j container"
	@echo "  make logs     Show Neo4j logs"
	@echo "  make shell    Open Python shell with models loaded"
	@echo "  make test     Run smoke test"
	@echo "  make lint     Run linter (ruff)"
	@echo "  make format   Format code (ruff)"
	@echo "  make clean    Remove cache and compiled files"
	@echo "  make install  Install dependencies"
	@echo "  make sync     Sync dependencies"

up:
	docker compose up -d
	@echo "Neo4j starting... Browser at http://localhost:7474"
	@echo "Credentials: neo4j / skillsgraph123"

down:
	docker compose down

logs:
	docker compose logs -f neo4j

shell:
	uv run python -i -c "from db import init_db; init_db(); from models import *; print('Models loaded: Skill, IsARel, RequiresRel, RelatedToRel')"

test:
	uv run main.py

lint:
	uv run ruff check .

format:
	uv run ruff format .

clean:
	find . -type d -name "__pycache__" -exec rm -rf {} + 2>/dev/null || true
	find . -type d -name ".ruff_cache" -exec rm -rf {} + 2>/dev/null || true
	find . -type d -name ".mypy_cache" -exec rm -rf {} + 2>/dev/null || true
	find . -type f -name "*.pyc" -delete 2>/dev/null || true

install:
	uv sync

sync:
	uv sync
