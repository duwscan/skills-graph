# skills-graph

Phase 1 foundation for an LLM-first Skills Graph service using Java 21, Spring Boot 4, Neo4j, PostgreSQL (pgvector), Redis, and Spring AI.

Current status: Phase 2 core taxonomy CRUD API is implemented.

## Prerequisites

- Java 21
- Docker + Docker Compose

## Quick Start

```bash
# edit src/main/resources/application-development.yml with your API keys
docker compose up -d
./mvnw flyway:migrate
./mvnw spring-boot:run
```

## Useful Commands

```bash
# list shortcuts
make help

# infra
make up
make down
make reset

# seed locale + root categories
make seed

# tests
make test
```

## Health Endpoint

```bash
curl http://localhost:8080/actuator/health
```

Returns:

```json
{
  "status": "ok",
  "version": "0.1.0",
  "db": "connected",
  "redis": "connected",
  "uptime_seconds": 12
}
```

## Phase 2 API

```bash
# skills
GET    /api/skills
GET    /api/skills/search?q=machine+learning
POST   /api/skills
GET    /api/skills/{id}
PATCH  /api/skills/{id}
GET    /api/skills/{id}/ancestors
GET    /api/skills/{id}/descendants
GET    /api/skills/{id}/related
GET    /api/skills/{id}/edges

# aliases
POST   /api/skills/{id}/aliases
GET    /api/skills/{id}/aliases
PATCH  /api/skills/{id}/aliases/{aliasId}
DELETE /api/skills/{id}/aliases/{aliasId}

# edges
POST   /api/edges
POST   /api/edges/{edgeId}/deprecate
DELETE /api/edges/{edgeId}

# taxonomy metadata
GET    /api/taxonomy/roots
GET    /api/taxonomy/version
GET    /api/taxonomy/changelog?since=0
```
