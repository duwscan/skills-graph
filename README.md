# skills-graph

Phase 1 foundation for an LLM-first Skills Graph service using Java 21, Spring Boot 4, Neo4j, PostgreSQL (pgvector), Redis, and Spring AI.

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
