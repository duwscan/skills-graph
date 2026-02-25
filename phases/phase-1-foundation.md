# Phase 1: Foundation & Data Layer

> **Timeline:** Week 1-2
> **Dependencies:** None (starting phase)
> **Unlocks:** Phase 2 (CRUD API), Phase 3 (Embeddings & Search)
> **Context:** LLM-First Skills Graph powering a Recruitment Agency Platform

---

## Goal

Set up the project scaffold, database schema, and core configuration so all subsequent phases have a solid base to build on. By the end of this phase, you should have a running Spring Boot application with a fully migrated PostgreSQL database and Redis connection.

---

## 1.1 Project Initialization

### Context

The project uses **Java 21** as runtime, **Spring Boot 3 + Spring Web MVC** as the HTTP framework (lightweight, standards-based, Spring AI compatible), and **Spring Data JPA** for type-safe database access.

### Directory Structure

```
skills-graph/
├── src/
│   ├── main/
│   │   ├── java/com/skillsgraph/
│   │   │   ├── SkillsGraphApplication.java   # Spring Boot entry point
│   │   │   ├── config/
│   │   │   │   ├── AppProperties.java        # @ConfigurationProperties (env vars)
│   │   │   │   ├── AppConstants.java         # Centralized configurable values (thresholds, TTLs, limits)
│   │   │   │   ├── Neo4jConfig.java          # Spring Data Neo4j configuration
│   │   │   │   └── AiConfig.java             # Spring AI provider configuration
│   │   │   ├── controller/                   # @RestController endpoints
│   │   │   ├── service/                      # @Service business logic
│   │   │   ├── dto/                          # Request/response records + enums
│   │   │   ├── domain/                       # @Node Spring Data Neo4j models
│   │   │   ├── repository/                   # Spring Data Neo4j Neo4jRepository interfaces
│   │   │   └── util/                         # Utilities (SlugUtils, etc.)
│   │   └── resources/
│   │       ├── application.yml               # Main configuration
│   │       ├── application-dev.yml           # Dev overrides
│   │       ├── neo4j/
│   │       │   └── schema.cypher             # Neo4j constraints and indexes
│   │       └── db/migration/                 # Flyway SQL migration files (PostgreSQL only)
│   │           └── V1__embedding_tables.sql  # PostgreSQL embedding + changelog tables
│   └── test/
│       ├── java/com/skillsgraph/             # JUnit 5 + Spring Boot Test
│       └── resources/
│           └── application-test.yml          # Test configuration (Testcontainers)
├── pom.xml
├── mvnw / mvnw.cmd                           # Maven wrapper
├── docker-compose.yml
├── .env.example
└── ARCHITECTURE.md
```

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 1.1.1 | Initialize Maven project | Use Spring Initializr (start.spring.io) with: Spring Boot 3, Java 21, Group `com.skillsgraph`. Add starters: `spring-boot-starter-web`, `spring-boot-starter-data-neo4j`, `spring-boot-starter-data-jpa`, `spring-boot-starter-data-redis`, `flyway-core` | `pom.xml`, `mvnw` |
| 1.1.2 | Add Spring AI dependencies | Spring AI BOM + starters: `spring-ai-anthropic-spring-boot-starter`, `spring-ai-openai-spring-boot-starter`. Pgvector JDBC extension | `pom.xml` |
| 1.1.3 | Add tooling dependencies | Checkstyle, SpotBugs, Lombok (optional), springdoc-openapi, Testcontainers, postgresql JDBC driver | `pom.xml` |
| 1.1.4 | Create `.env.example` | Document all required and optional env vars with example values | `.env.example` |
| 1.1.5 | Create `.gitignore` | Ignore `target/`, `.env`, `*.log`, `.DS_Store`, `.idea/`, `*.class` | `.gitignore` |

### `.env.example`

```env
# Database (PostgreSQL - embeddings + changelog)
DATABASE_URL=postgresql://skills:skills_dev@localhost:5432/skills_graph

# Neo4j (graph storage)
NEO4J_URI=bolt://localhost:7687
NEO4J_USERNAME=neo4j
NEO4J_PASSWORD=skills_dev

# Redis
REDIS_URL=redis://localhost:6379

# AI Providers
ANTHROPIC_API_KEY=sk-ant-...
OPENAI_API_KEY=sk-...

# Search
TYPESENSE_URL=http://localhost:8108
TYPESENSE_API_KEY=skills_dev_key

# Observability (optional)
HELICONE_API_KEY=

# App
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=development
```

### Checklist

- [ ] `pom.xml` created with Spring Boot 3 parent, Java 21 (`maven-compiler-plugin` source/target `21`), and all required starters
- [ ] `pom.xml` compiler plugin configured for Java 21 with `-parameters` flag
- [ ] All runtime dependencies installed and importable
- [ ] All dev dependencies installed
- [ ] `.env.example` created with all variables documented
- [ ] `.gitignore` covers all standard exclusions
- [ ] `./mvnw spring-boot:run` starts the application
- [ ] `./mvnw test` runs JUnit 5 tests

---

## 1.2 Docker Infrastructure

### Context

Local development requires PostgreSQL 16 with extensions (`pgvector`, `pg_trgm`) and Redis 7, plus Neo4j 5. Use the `pgvector/pgvector:pg16` Docker image which bundles pgvector. The `pg_trgm` extension is built-in to PostgreSQL.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 1.2.1 | Create `docker-compose.yml` | PostgreSQL 16 (pgvector image) + Redis 7-alpine + Neo4j 5. Map ports 5432, 6379, 7474, 7687. Use named volumes for data persistence | `docker-compose.yml` |
| 1.2.2 | Add init SQL script | Mount an `init.sql` that enables `pg_trgm` extension on database creation. pgvector is auto-enabled by the image | `docker/init.sql` |
| 1.2.3 | Add Maven / application runner scripts for Docker | `./mvnw spring-boot:run -Dspring-boot.run.arguments=--infra-up` → `docker compose up -d`, `docker compose down` → `docker compose down`, `docker compose down -v && docker compose up -d` → down + remove volumes + up | `pom.xml` |

### `docker-compose.yml`

```yaml
services:
  postgres:
    image: pgvector/pgvector:pg16
    ports: ["5432:5432"]
    environment:
      POSTGRES_DB: skills_graph
      POSTGRES_USER: skills
      POSTGRES_PASSWORD: skills_dev
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./docker/init.sql:/docker-entrypoint-initdb.d/init.sql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U skills -d skills_graph"]
      interval: 5s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]
    command: redis-server --appendonly yes
    volumes:
      - redisdata:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 5s
      retries: 5

  typesense:
    image: typesense/typesense:27.1
    ports: ["8108:8108"]
    environment:
      TYPESENSE_API_KEY: skills_dev_key
      TYPESENSE_DATA_DIR: /data
    volumes:
      - typesensedata:/data

  neo4j:
    image: neo4j:5
    ports: ["7474:7474", "7687:7687"]
    environment:
      NEO4J_AUTH: neo4j/skills_dev
      NEO4J_PLUGINS: '["apoc"]'
    volumes:
      - neo4jdata:/data
    healthcheck:
      test: ["CMD", "neo4j", "status"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  pgdata:
  redisdata:
  typesensedata:
  neo4jdata:
```

### `docker/init.sql`

```sql
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
```

### Checklist

- [ ] `docker compose up -d` starts all 4 services without errors
- [ ] PostgreSQL is accessible on `localhost:5432`
- [ ] Redis is accessible on `localhost:6379`
- [ ] Typesense is accessible on `localhost:8108`
- [ ] Neo4j is accessible on `localhost:7687` (Bolt) and `localhost:7474` (HTTP browser)
- [ ] `docker compose down && docker compose up -d` restarts cleanly (data persists)
- [ ] `docker compose down -v && docker compose up -d` cleans all volumes and starts fresh
- [ ] PostgreSQL has `vector`, `pg_trgm` extensions enabled:
  ```bash
  docker exec -it skills-graph-postgres-1 psql -U skills -d skills_graph \
    -c "SELECT extname FROM pg_extension;"
  # → vector, pg_trgm
  ```

---

## 1.3 Environment Configuration

### Context

All environment variables are validated at startup using Jakarta Bean Validation. The app refuses to start if required variables are missing or malformed. This prevents runtime errors from misconfiguration.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 1.3.1 | `application.yml` | Define all configuration properties with defaults. Required: `ANTHROPIC_API_KEY`, `OPENAI_API_KEY`, DataSource URL/credentials. Optional: `HELICONE_API_KEY`. `SERVER_PORT` defaults to 8080 | `src/main/resources/application.yml` |
| 1.3.2 | `AppProperties.java` | `@ConfigurationProperties(prefix="app")` + `@Validated` record. All services inject `AppProperties` instead of reading `System.getenv()` directly | `src/main/java/com/skillsgraph/config/AppProperties.java` |
| 1.3.3 | Fail-fast on invalid config | Spring Boot throws `BindException` on startup if required properties are missing/invalid. The exception message lists the invalid field names | `src/main/java/com/skillsgraph/config/AppProperties.java` |

### `src/main/resources/application.yml` (reference)

```yaml
spring:
  neo4j:
    uri: ${NEO4J_URI:bolt://localhost:7687}
    authentication:
      username: ${NEO4J_USERNAME:neo4j}
      password: ${NEO4J_PASSWORD:skills_dev}
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/skills_graph}
    username: ${SPRING_DATASOURCE_USERNAME:skills}
    password: ${SPRING_DATASOURCE_PASSWORD:skills_dev}
    hikari:
      maximum-pool-size: ${DB_POOL_SIZE:10}
  redis:
    host: ${SPRING_REDIS_HOST:localhost}
    port: ${SPRING_REDIS_PORT:6379}
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
    openai:
      api-key: ${OPENAI_API_KEY}
      embedding:
        options:
          model: text-embedding-3-large
          dimensions: 1024
  flyway:
    enabled: true
    locations: classpath:db/migration

app:
  helicone-api-key: ${HELICONE_API_KEY:}

server:
  port: ${SERVER_PORT:8080}
  shutdown: graceful
```

### `src/main/java/com/skillsgraph/config/AppProperties.java` (reference)

```java
@ConfigurationProperties(prefix = "app")
@Validated
public record AppProperties(
    String heliconeApiKey  // optional — empty string if not set
) {}
```

### Checklist

- [ ] `src/main/resources/application.yml` contains all configuration
- [ ] Spring fails with `BindException` when `ANTHROPIC_API_KEY` is missing
- [ ] App starts successfully when all required vars are set
- [ ] `HELICONE_API_KEY` is optional — app starts without it
- [ ] `SERVER_PORT` defaults to 8080 when not set
- [ ] `SPRING_PROFILES_ACTIVE` selects the correct profile (dev/test/prod)

---

## 1.3b Centralized Constants

### Context

All tunable values (thresholds, TTLs, limits, dimensions, timeouts, etc.) are defined in a single **constants file** that serves as the single source of truth. This prevents hardcoded magic numbers from scattering across the codebase and makes values easy to find, change, and keep consistent. Where appropriate, constants read from environment variables with sensible defaults.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 1.3b.1 | Create `AppConstants` class | Define all configurable values as `static final` fields, grouped by domain: Embedding & Vector Search, Extraction Pipeline, Discovery & Review, API & Pagination, Rate Limiting, Workers & Events, Infrastructure, Quality Thresholds. Each constant has a Javadoc comment | `src/main/java/com/skillsgraph/config/AppConstants.java` |
| 1.3b.2 | Env var overrides | For operationally-tunable values, read from `application.yml` (via `@Value` or a dedicated properties class) with the constant as the fallback default. See table below for which constants support env overrides | `src/main/java/com/skillsgraph/config/AppConstants.java` |

### Constants Overview

| Group | Constants | Env Overrides |
|---|---|---|
| Embedding & Vector Search | `EMBEDDING_DIMENSIONS` (1024), `EMBEDDING_MODEL`, `EMBEDDING_CACHE_TTL_SECONDS` (30 days), `EMBEDDING_BATCH_SIZE` (100), `SIMILARITY_DUPLICATE_THRESHOLD` (0.90), `SIMILARITY_REVIEW_THRESHOLD` (0.70), `SIMILARITY_RELATED_HIGH_THRESHOLD` (0.85), `SIMILARITY_RELATED_LOW_THRESHOLD` (0.65), `RAG_CANDIDATE_LIMIT` (100) | `EMBEDDING_MODEL`, `EMBEDDING_CACHE_TTL`, `SIMILARITY_DUPLICATE_THRESHOLD`, `SIMILARITY_REVIEW_THRESHOLD`, `RAG_CANDIDATE_LIMIT` |
| Extraction Pipeline | `CHUNK_TARGET_TOKENS` (1500), `CHUNK_MAX_TOKENS` (2000), `CHUNK_OVERLAP_TOKENS` (200), `EXTRACTION_CACHE_TTL_SECONDS` (7 days), `EXTRACTION_MAX_TEXT_LENGTH` (100000), `EXTRACTION_MIN_CONFIDENCE_DEFAULT` (0.5), `EXTRACTION_EXPANSION_FACTOR` (0.6), `EXTRACTION_EXPANSION_DEPTH_DEFAULT` (1), `EXTRACTION_MAX_RETRIES` (3), `LLM_CONCURRENCY_LIMIT` (50) | `EXTRACTION_CACHE_TTL`, `LLM_CONCURRENCY_LIMIT` |
| Section Weighting | `SECTION_WEIGHT_JD_REQUIREMENTS` (1.0), `SECTION_WEIGHT_JD_RESPONSIBILITIES` (0.9), `SECTION_WEIGHT_JD_NICE_TO_HAVE` (0.75), `SECTION_WEIGHT_JD_COMPANY` (0.5), `SECTION_WEIGHT_JD_BENEFITS` (0.3), `SECTION_WEIGHT_CV_SKILLS` (1.0), `SECTION_WEIGHT_CV_EXPERIENCE` (0.9), `SECTION_WEIGHT_CV_PROJECTS` (0.85), `SECTION_WEIGHT_CV_EDUCATION` (0.8), `SECTION_WEIGHT_CV_SUMMARY` (0.7) | — |
| Co-occurrence & Empirical | `CO_OCCURRENCE_EDGE_THRESHOLD` (20), `CO_OCCURRENCE_NORMALIZATION_FACTOR` (100), `CO_OCCURRENCE_BATCH_WINDOW_MS` (1000) | `CO_OCCURRENCE_EDGE_THRESHOLD`, `CO_OCCURRENCE_NORMALIZATION_FACTOR` |
| Discovery & Review | `DISCOVERY_SIGNAL_THRESHOLD` (5), `DISCOVERY_SIGNAL_TTL_SECONDS` (30 days), `RELATIONSHIP_BATCH_SIZE` (15) | `DISCOVERY_SIGNAL_THRESHOLD`, `DISCOVERY_SIGNAL_TTL` |
| API & Pagination | `PAGINATION_DEFAULT_LIMIT` (20), `PAGINATION_MAX_LIMIT` (100), `CHANGELOG_DEFAULT_LIMIT` (50), `TRAVERSAL_DEFAULT_DEPTH` (5), `TRAVERSAL_MAX_DEPTH` (10), `SLUG_MAX_LENGTH` (100), `SKILL_NAME_MAX_LENGTH` (200), `SKILL_DESCRIPTION_MAX_LENGTH` (5000), `ALIAS_MAX_LENGTH` (200), `BODY_SIZE_LIMIT` (1 MB) | — |
| Rate Limiting | `RATE_LIMIT_READ` (100 req/min), `RATE_LIMIT_EXTRACT` (20 req/min), `RATE_LIMIT_MUTATE` (50 req/min) | `RATE_LIMIT_READ`, `RATE_LIMIT_EXTRACT`, `RATE_LIMIT_MUTATE` |
| Workers & Events | `EXTRACTION_WORKER_CONCURRENCY` (10), `BATCH_MAX_DOCUMENTS` (100), `BATCH_RESULT_TTL_SECONDS` (24 hours), `TYPESENSE_SYNC_DEBOUNCE_MS` (500), `STREAM_CONSUMER_MAX_RETRIES` (3) | `EXTRACTION_WORKER_CONCURRENCY`, `BATCH_RESULT_TTL`, `TYPESENSE_SYNC_DEBOUNCE_MS` |
| Infrastructure | `DB_POOL_SIZE_DEV` (10), `DB_POOL_SIZE_PROD` (50), `DB_POOL_SIZE` (resolved), `GRACEFUL_SHUTDOWN_TIMEOUT_MS` (30s), `DB_CONNECT_TIMEOUT_MS` (30s), `REDIS_CONNECT_TIMEOUT_MS` (10s) | `DB_POOL_SIZE` |
| Quality Thresholds | `GOLDEN_SET_MIN_F1` (0.80), `GOLDEN_SET_MIN_PRECISION` (0.75), `GOLDEN_SET_MIN_RECALL` (0.75), `LOAD_TEST_MAX_P99_SECONDS` (10), `LOAD_TEST_MAX_ERROR_RATE` (0.01) | — |

### Checklist

- [ ] `AppConstants.java` exists with all constants listed above
- [ ] Constants are grouped by domain with clear Javadoc comments
- [ ] Env-overridable constants fall back to their static default when not configured
- [ ] `DB_POOL_SIZE` resolves to 10 in dev and 50 in production when env var is unset
- [ ] All other modules import from `AppConstants` instead of hardcoding values
- [ ] No magic numbers remain inline in service or route files

---

## 1.4 Spring AI Provider Registry

### Context

Spring AI's auto-configuration provides a unified interface to switch between LLM providers (Anthropic primary, OpenAI fallback) and embedding models. All services inject `ChatClient` or `EmbeddingModel` beans instead of creating provider instances directly.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 1.4.1 | Define ChatClient beans | `AiConfig.java` — define `@Bean("fastChatClient")` (Claude Haiku) and `@Bean("standardChatClient")` (Claude Sonnet) using `ChatClient.builder()` | `src/main/java/com/skillsgraph/config/AiConfig.java` |
| 1.4.2 | EmbeddingModel bean | `EmbeddingModel` is auto-configured by Spring AI from `application.yml` (`spring.ai.openai.embedding.options.model/dimensions`). Optionally expose as a named bean for clarity | `src/main/java/com/skillsgraph/config/AiConfig.java` |
| 1.4.3 | Helicone proxy support | If `HELICONE_API_KEY` is set, override `spring.ai.anthropic.base-url` to `https://anthropic.helicone.ai` and add `Helicone-Auth` default header via `@Conditional` bean | `src/main/java/com/skillsgraph/config/AiConfig.java` |

### `src/main/java/com/skillsgraph/config/AiConfig.java` (reference)

```java
@Configuration
public class AiConfig {

    /**
     * Fast ChatClient — Claude Haiku (low cost, high volume extraction)
     */
    @Bean("fastChatClient")
    public ChatClient fastChatClient(AnthropicChatModel chatModel) {
        return ChatClient.builder(chatModel)
            .defaultOptions(AnthropicChatOptions.builder()
                .withModel("claude-haiku-4-5-20251001")
                .withMaxTokens(4096)
                .build())
            .build();
    }

    /**
     * Standard ChatClient — Claude Sonnet (complex extraction, classification)
     */
    @Bean("standardChatClient")
    public ChatClient standardChatClient(AnthropicChatModel chatModel) {
        return ChatClient.builder(chatModel)
            .defaultOptions(AnthropicChatOptions.builder()
                .withModel("claude-sonnet-4-5-20250929")
                .withMaxTokens(8192)
                .build())
            .build();
    }
}
// EmbeddingModel is auto-configured by spring-ai-openai-spring-boot-starter
// from application.yml: spring.ai.openai.embedding.options.model/dimensions
```

### Checklist

- [ ] `AiConfig.java` defines `fastChatClient` and `standardChatClient` beans
- [ ] `@Qualifier("fastChatClient")` resolves to Claude Haiku configuration
- [ ] `@Qualifier("standardChatClient")` resolves to Claude Sonnet configuration
- [ ] `EmbeddingModel` bean auto-configured from `application.yml` (text-embedding-3-large, 1024 dims)
- [ ] When `HELICONE_API_KEY` is set, `baseURL` routes through Helicone
- [ ] When `HELICONE_API_KEY` is NOT set, calls go directly to providers

---

## 1.5 Database Schema & Migrations

### Context

The system uses a **hybrid database architecture** (see ARCHITECTURE.md §2.8): **Neo4j 5** stores skill nodes, alias nodes, and all relationships. **PostgreSQL 16** stores vector embeddings and operational tables (`skill_embeddings`, `alias_embeddings`, `locale_config`, `graph_changelog`).

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 1.5.1 | Create Neo4j schema | `src/main/resources/neo4j/schema.cypher` — Neo4j constraints (skill_id, externalId, slug, alias_id) and indexes (status, category, canonicalName, fulltext). Applied on startup via a `CommandLineRunner` or `Neo4jTemplate.run()` | `src/main/resources/neo4j/schema.cypher` |
| 1.5.2 | Create PostgreSQL migration | `src/main/resources/db/migration/V1__embedding_tables.sql` — PostgreSQL embedding tables only: `skill_embeddings`, `alias_embeddings`, `locale_config`, `graph_changelog`. CREATE SEQUENCE `graph_version_seq`. All indexes (HNSW for vectors, GIN for trigrams) | `src/main/resources/db/migration/V1__embedding_tables.sql` |
| 1.5.3 | Spring Data Neo4j `@Node` classes | Define `@Node("Skill")` and `@Node("Alias")` classes in `domain/`. Map relationships as `@Relationship` annotations. Use `String` for IDs | `src/main/java/com/skillsgraph/domain/` |
| 1.5.4 | Flyway auto-migration (PostgreSQL only) | Flyway runs automatically on startup via `spring.flyway.enabled=true`. Only manages the PostgreSQL embedding/changelog tables | `src/main/resources/db/migration/` |
| 1.5.5 | Seed data script | `./mvnw spring-boot:run -Dspring-boot.run.arguments=--seed` — insert locale_config rows (en, vi, fr, ja, zh) via JPA and create 5-10 root `(:Skill)` nodes in Neo4j (Technology, Business, Design, Science, Languages, Soft Skills) | `src/main/java/com/skillsgraph/script/SeedRunner.java` |

### Tables Summary

**Neo4j (graph nodes and relationships):**

| Node/Relationship | Purpose | Key Properties |
|---|---|---|
| `(:Skill)` | Canonical skill nodes | `id`, `externalId`, `canonicalName`, `slug`, `status`, `category`, `version`, `source`, `createdAt`, `updatedAt` |
| `(:Alias)` | Multi-locale surface forms | `id`, `surfaceForm`, `locale` (BCP-47), `isPrimary`, `source`, `createdAt` |
| `[:PARENT_OF]`, `[:RELATED_TO]`, `[:REQUIRES]` | Directed edges between skills | `confidence`, `weight`, `provenance`, `status`, `createdAt`, `updatedAt` |
| `[:SUPERSEDED_BY]` | Deprecation pointer | `createdAt` |
| `[:HAS_ALIAS]` | Skill → Alias link | — |
| `[:CO_OCCURS_WITH]` | Empirical co-occurrence | `count`, `sourceCounts`, `lastSeenAt` |

**PostgreSQL (embeddings + operational tables):**

| Table | Purpose | Key Columns |
|---|---|---|
| `skill_embeddings` | Skill vector embeddings | `skill_id` (TEXT PK, matches Neo4j node id), `embedding` (vector(1024)), `updated_at` |
| `alias_embeddings` | Alias vector embeddings | `alias_id` (TEXT PK, matches Neo4j node id), `alias_embedding` (vector(1024)), `updated_at` |
| `locale_config` | Supported locales and coverage | `locale` (PK), `display_name`, `is_active`, `coverage_pct` |
| `graph_changelog` | Versioned mutation log for CDC | `graph_version`, `actor`, `mutation_type`, `entity_type`, `entity_id`, `diff_payload` (JSONB) |

### Indexes Created

| Index | Type | Purpose |
|---|---|---|
**Neo4j indexes (in schema.cypher):**

| Index | Type | Purpose |
|---|---|---|
| `skill_id` constraint | Unique | Fast lookup by skill id |
| `skill_status` | B-tree | Fast lookup of active skills |
| `skill_category` | B-tree | Filter skills by category |
| `skill_fulltext` | Fulltext | Full-text search on canonicalName, slug |
| `alias_fulltext` | Fulltext | Full-text search on surfaceForm |

**PostgreSQL indexes (in V1__embedding_tables.sql):**

| Index | Type | Purpose |
|---|---|---|
| `idx_skills_embedding` | HNSW (vector_cosine_ops) | Semantic similarity search on skills |
| `idx_aliases_embedding` | HNSW (vector_cosine_ops) | Semantic similarity search on aliases |
| `idx_skills_name_trgm` | GIN (gin_trgm_ops) | Fuzzy text search on skill_id |
| `idx_changelog_version` | B-tree | CDC queries by version |

### Checklist

- [ ] `src/main/resources/neo4j/schema.cypher` contains Neo4j constraints and indexes
- [ ] `src/main/resources/db/migration/V1__embedding_tables.sql` contains PostgreSQL embedding + changelog tables
- [ ] `CREATE SEQUENCE graph_version_seq` is included in the SQL migration
- [ ] `./mvnw flyway:migrate` applies PostgreSQL migration successfully
- [ ] `./mvnw flyway:migrate` is idempotent (running twice doesn't error)
- [ ] PostgreSQL tables exist: `\dt` shows `skill_embeddings`, `alias_embeddings`, `locale_config`, `graph_changelog`
- [ ] Neo4j constraints applied on startup: `skill_id`, `externalId`, `slug` are unique
- [ ] `./mvnw spring-boot:run -Dspring-boot.run.arguments=--seed` inserts locale_config rows and root `(:Skill)` nodes in Neo4j
- [ ] `./mvnw spring-boot:run -Dspring-boot.run.arguments=--seed` is idempotent (running twice doesn't create duplicates)
- [ ] Spring Data Neo4j `@Node` classes match Neo4j schema
- [ ] Spring Data Neo4j can perform basic MATCH/CREATE on Neo4j nodes

---

## 1.6 Database & Redis Clients

### Context

Database access uses **Spring Data JPA** (HikariCP connection pooling) with Flyway migrations. Redis uses **Spring Data Redis (Lettuce)** with typed cache helpers. A pgvector serialization helper converts between Java float arrays and PostgreSQL vector format.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 1.6.1 | PostgreSQL client | Connection pool via `postgres` library with Spring Data JPA wrapper. Pool size from `DB_POOL_SIZE` in `src/main/java/com/skillsgraph/config/AppConstants.java` (10 dev / 50 prod, overridable via `DB_POOL_SIZE` env var). Include connection error handling and logging | `src/main/java/com/skillsgraph/config/DataSourceConfig.java` |
| 1.6.2 | Redis client | Spring Data Redis (Lettuce) connection with reconnect strategy (exponential backoff). Export typed cache helpers | `src/main/java/com/skillsgraph/config/RedisConfig.java` |
| 1.6.3 | Cache helpers | `cacheGet<T>(key): Promise<T | null>` — JSON.parse cached value. `cacheSet(key, value, ttlSeconds)` — JSON.stringify and SET EX. `cacheDelete(key)`. `cacheMakeKey(...parts)` — join parts with `:` | `src/main/java/com/skillsgraph/config/RedisConfig.java` |
| 1.6.4 | pgvector helpers | `toSql(embedding: number[]): string` — convert `[0.1, 0.2, ...]` to `'[0.1,0.2,...]'`. `fromSql(pgString): number[]` — parse vector string back to array | `src/main/java/com/skillsgraph/util/PgVectorUtils.java` |
| 1.6.5 | Graceful shutdown helpers | Export `closeDatabase()` and `closeRedis()` functions for clean shutdown | `src/main/java/com/skillsgraph/config/DataSourceConfig.java`, `src/main/java/com/skillsgraph/config/RedisConfig.java` |

### Checklist

- [ ] `src/main/java/com/skillsgraph/config/DataSourceConfig.java` exports Spring Data JPA `db` instance
- [ ] Database connection works: `db.select().from(skills).limit(1)` returns without error
- [ ] Connection pool size is configurable via `DB_POOL_SIZE` env var (see `src/main/java/com/skillsgraph/config/AppConstants.java` for defaults)
- [ ] `src/main/java/com/skillsgraph/config/RedisConfig.java` exports `redis` client and cache helpers
- [ ] Redis connection works: `redis.ping()` returns "PONG"
- [ ] `cacheSet("test:key", { foo: "bar" }, 60)` stores value
- [ ] `cacheGet<{foo:string}>("test:key")` retrieves typed value
- [ ] `cacheDelete("test:key")` removes value
- [ ] `cacheMakeKey("taxonomy", "skill", "abc123")` returns `"taxonomy:skill:abc123"`
- [ ] `src/main/java/com/skillsgraph/util/PgVectorUtils.java` — `toSql([0.1, 0.2, 0.3])` returns `'[0.1,0.2,0.3]'`
- [ ] `fromSql('[0.1,0.2,0.3]')` returns `[0.1, 0.2, 0.3]`
- [ ] Graceful shutdown: `closeDatabase()` drains pool, `closeRedis()` disconnects cleanly

---

## 1.7 Spring Web MVC App Skeleton

### Context

The Spring Web MVC app serves as the API gateway. At this stage, only the skeleton is set up — route groups are mounted but handlers are placeholders. Middleware includes error handling, request ID, CORS, and timing.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 1.7.1 | Spring Web MVC app entry | Create Spring Web MVC app instance. Mount route groups as placeholders: `/api/skills`, `/api/edges`, `/api/extract`, `/api/taxonomy`, `/api/review-queue` | `src/main/java/com/skillsgraph/SkillsGraphApplication.java` |
| 1.7.2 | Health check endpoint | `GET /actuator/health` — check DB connection (`SELECT 1`), Redis connection (`PING`), return `{ status, version, db, redis, uptime_seconds }` | `src/main/java/com/skillsgraph/controller/HealthController.java` |
| 1.7.3 | Error handler middleware | Catch-all `app.onError()` handler. Return `{ error: message, status, request_id }`. Log the full error with stack trace. Map known error types to HTTP status codes (Jakarta Bean ValidationException → 400, SkillNotFoundException (extends RuntimeException) → 404, DuplicateSkillException → 409, etc.) | `src/main/java/com/skillsgraph/middleware/GlobalExceptionHandler.java` |
| 1.7.4 | Request ID middleware | Generate `UUID.randomUUID().toString()` for each request. Set `X-Request-ID` response header. Store in Spring Web MVC context for logging | `src/main/java/com/skillsgraph/middleware/RequestIdFilter.java` |
| 1.7.5 | CORS configuration | Use Spring `WebMvcConfigurer#addCorsMappings` with configurable allowed origins. Default: allow all in development, restrictive in production | `src/main/java/com/skillsgraph/config/WebMvcConfig.java` |
| 1.7.6 | Custom error classes | `SkillNotFoundException`, `DuplicateSkillException`, `ValidationException`, `CycleDetectedException` extending base `AppException` class with `statusCode` property | `src/main/java/com/skillsgraph/util/AppExceptions.java` |
| 1.7.7 | Server startup | Listen on configured PORT. Log startup message with port and environment | `src/main/java/com/skillsgraph/SkillsGraphApplication.java` |

### `src/main/java/com/skillsgraph/SkillsGraphApplication.java` (reference)

```java
package com.skillsgraph;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SkillsGraphApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkillsGraphApplication.class, args);
    }
}
```

`src/main/resources/application.yml` (skeleton):

```yaml
server:
  port: ${SERVER_PORT:8080}

spring:
  application:
    name: skills-graph
  datasource:
    url: ${DATABASE_URL}
    hikari:
      maximum-pool-size: 10
  data:
    redis:
      url: ${REDIS_URL}
  flyway:
    enabled: true
    locations: classpath:db/migration

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

### Checklist

- [ ] `./mvnw spring-boot:run` starts server on PORT 8080
- [ ] `GET /actuator/health` returns `{ status: "ok", version: "0.1.0", db: "connected", redis: "connected", uptime_seconds: N }`
- [ ] `GET /actuator/health` returns `{ status: "degraded", db: "disconnected" }` when DB is down
- [ ] All responses include `X-Request-ID` header
- [ ] `GET /nonexistent` returns `{ error: "Not Found", status: 404, request_id: "..." }`
- [ ] CORS headers are present in responses
- [ ] Error handler catches thrown errors and returns structured JSON
- [ ] `SkillNotFoundException`, `DuplicateSkillException`, `ValidationException` classes exist
- [ ] Server logs startup message: `"Skills Graph API started on port 8080 (development)"`
- [ ] `Ctrl+C` triggers graceful shutdown (closes DB pool, Redis connection)

---

## 1.8 Initial Tests

### Context

Set up the test infrastructure with `./mvnw test`. Write initial tests for configuration, utilities, and health check.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 1.8.1 | Test configuration | Configure `./mvnw test` in `pom.xml`. Set up test environment (use `.env.test` or inline env vars) | `pom.xml` |
| 1.8.2 | Test helpers | Create helpers for spinning up test DB, resetting state between tests, creating test Spring Web MVC app instance | `src/test/java/com/skillsgraph/TestSetup.java` |
| 1.8.3 | Config tests | Test that env validation rejects missing required vars. Test default values for optional vars | `src/test/java/com/skillsgraph/config/AppPropertiesTest.java` |
| 1.8.4 | pgvector helper tests | Test `toSql()` and `fromSql()` with various vector sizes and edge cases (empty, single element, large dimensions) | `src/test/java/com/skillsgraph/util/PgVectorUtilsTest.java` |
| 1.8.5 | Health check test | Test `GET /actuator/health` returns correct shape and status | `src/test/java/com/skillsgraph/controller/HealthControllerTest.java` |
| 1.8.6 | Error handler tests | Test that different error types map to correct HTTP status codes | `src/test/java/com/skillsgraph/middleware/GlobalExceptionHandlerTest.java` |

### Checklist

- [ ] `./mvnw test` runs without errors
- [ ] Config tests pass: invalid env → exits, valid env → config object
- [ ] pgvector helper tests pass: `toSql`, `fromSql` round-trip correctly
- [ ] Health check test passes: correct JSON shape when DB/Redis are up
- [ ] Error handler tests pass: Jakarta Bean ValidationException → 400, SkillNotFoundException (extends RuntimeException) → 404, generic → 500
- [ ] Tests are isolated: each test file can run independently
- [ ] Test output shows clear pass/fail for each test case

---

## Phase 1 Completion Verification

Run the following to verify the entire phase is complete:

```bash
# 1. Infrastructure
docker compose up -d
docker compose ps  # All 3 services "Up (healthy)"

# 2. Database
./mvnw flyway:migrate  # → "Migration 001_initial applied"
./mvnw spring-boot:run -Dspring-boot.run.arguments=--seed     # → "Seeded 5 locales, 6 root categories"

# Verify PostgreSQL tables
docker exec -it skills-graph-postgres-1 psql -U skills -d skills_graph \
  -c "\dt"
# → skill_embeddings, alias_embeddings, locale_config, graph_changelog

# Verify extensions
docker exec -it skills-graph-postgres-1 psql -U skills -d skills_graph \
  -c "SELECT extname FROM pg_extension;"
# → vector, pg_trgm, plpgsql

# Verify Neo4j seed data
curl -u neo4j:skills_dev http://localhost:7474/db/data/transaction/commit \
  -H "Content-Type: application/json" \
  -d '{"statements":[{"statement":"MATCH (s:Skill) RETURN s.canonicalName"}]}'
# → Technology, Business, Design, Science, Languages, Soft Skills

# 3. Application
./mvnw spring-boot:run &
sleep 2

curl http://localhost:8080/health | jq
# → {"status":"ok","version":"0.1.0","db":"connected","redis":"connected","uptime_seconds":2}

# Verify 404 handling
curl http://localhost:8080/api/nonexistent | jq
# → {"error":"Not Found","status":404,"request_id":"..."}

kill %1

# 4. Tests
./mvnw test
# → All tests pass

echo "Phase 1 complete ✓"
```

---

## Phase 1 Master Checklist

### 1.1 Project Initialization
- [ ] Maven project initialized with `pom.xml`
- [ ] `Java 21` compiler configured and `strict` null handling enabled
- [ ] All dependencies installed (runtime + dev)
- [ ] `.env.example` created
- [ ] `.gitignore` configured

### 1.2 Docker Infrastructure
- [ ] `docker-compose.yml` with PostgreSQL, Redis, full-text search service
- [ ] `docker/init.sql` enables extensions
- [ ] All containers start and pass health checks
- [ ] Maven / application runner scripts for infra management (`infra:up`, `infra:down`, `infra:reset`)

### 1.3 Environment Configuration
- [ ] `src/main/resources/application.yml` validates all env vars with Jakarta Bean Validation
- [ ] App fails fast with clear error on missing vars
- [ ] Optional vars have defaults
- [ ] Typed `config` export used throughout codebase

### 1.3b Centralized Constants
- [ ] `src/main/java/com/skillsgraph/config/AppConstants.java` exports all tunable values grouped by domain (incl. Section Weighting, Co-occurrence & Empirical)
- [ ] Env-overridable constants read from `process.env` with defaults
- [ ] All modules import from `AppConstants` — no inline magic numbers

### 1.4 Spring AI Provider Registry
- [ ] `src/main/java/com/skillsgraph/config/AiConfig.java` exports provider registry
- [ ] Model tier helpers: `getExtractionModel()`, `embeddingModel (autowired Spring AI bean)`, `standardChatClient`
- [ ] Helicone proxy conditionally enabled

### 1.5 Database Schema & Migrations
- [ ] `src/main/resources/neo4j/schema.cypher` contains Neo4j constraints and indexes
- [ ] `V1__embedding_tables.sql` contains PostgreSQL embedding + changelog tables
- [ ] `./mvnw flyway:migrate` works and is idempotent (PostgreSQL only)
- [ ] Neo4j schema applied on startup via `CommandLineRunner`
- [ ] `./mvnw spring-boot:run -Dspring-boot.run.arguments=--seed` populates initial data in both Neo4j and PostgreSQL
- [ ] Spring Data Neo4j `@Node` classes match Neo4j schema

### 1.6 Database & Redis Clients
- [ ] PostgreSQL connection pool with Spring Data JPA
- [ ] Redis client with typed cache helpers
- [ ] pgvector serialization helpers
- [ ] Graceful shutdown functions

### 1.7 Spring Web MVC App Skeleton
- [ ] Server starts on configured port
- [ ] `GET /actuator/health` works and checks dependencies
- [ ] Error handler returns structured JSON
- [ ] Request ID middleware adds `X-Request-ID` header
- [ ] CORS enabled
- [ ] Custom error classes defined
- [ ] Graceful shutdown on SIGTERM (Spring's graceful shutdown)

### 1.8 Initial Tests
- [ ] `./mvnw test` infrastructure set up
- [ ] Config validation tests pass
- [ ] pgvector helper tests pass
- [ ] Health check endpoint tests pass
- [ ] Error handler tests pass
