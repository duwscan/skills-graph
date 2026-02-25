# skills-graph

Phase 1 foundation for an LLM-first Skills Graph service using Java 21, Spring Boot 4, Neo4j, PostgreSQL (pgvector), Redis, and Spring AI.

## Prerequisites

- Java 21
- Docker + Docker Compose

## Quick Start

```bash
cp .env.example .env
docker compose up -d
./mvnw flyway:migrate
./mvnw spring-boot:run
```

## Useful Commands

```bash
# infra helpers (implemented via app args)
./mvnw spring-boot:run -Dspring-boot.run.arguments=--infra-up
./mvnw spring-boot:run -Dspring-boot.run.arguments=--infra-down
./mvnw spring-boot:run -Dspring-boot.run.arguments=--infra-reset

# seed locale + root categories
./mvnw spring-boot:run -Dspring-boot.run.arguments=--seed

# tests
./mvnw test
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
