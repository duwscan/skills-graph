# Phase 1: Foundation & Data Layer

> **Timeline:** Week 1-2
> **Dependencies:** None (starting phase)
> **Unlocks:** Phase 2 (CRUD API), Phase 3 (Embeddings & Search)
> **Context:** LLM-First Skills Graph powering a Recruitment Agency Platform

---

## Goal

Set up the project scaffold, database schema, and core configuration so all subsequent phases have a solid base to build on. By the end of this phase, you should have a running Hono server on Bun with a fully migrated PostgreSQL database and Redis connection.

---

## 1.1 Project Initialization

### Context

The project uses **Bun** as runtime (native TypeScript, fast startup), **Hono** as the HTTP framework (lightweight, Web Standards, AI SDK compatible), and **Drizzle ORM** for type-safe database access.

### Directory Structure

```
skills-graph/
├── src/
│   ├── index.ts                  # Hono app entry point
│   ├── config/
│   │   ├── env.ts                # Environment variable validation (Zod)
│   │   ├── constants.ts          # Centralized configurable values (thresholds, TTLs, limits)
│   │   └── providers.ts          # AI SDK provider registry
│   ├── db/
│   │   ├── client.ts             # PostgreSQL connection pool (Drizzle)
│   │   ├── schema.ts             # Drizzle schema definitions
│   │   ├── migrations/           # SQL migration files
│   │   │   └── 001_initial.sql
│   │   └── redis.ts              # Redis client (ioredis)
│   ├── routes/                   # Hono route modules
│   ├── services/                 # Business logic layer
│   ├── schemas/                  # Shared Zod schemas
│   └── lib/                      # Utilities (slug, hash, etc.)
├── test/
│   ├── fixtures/                 # Seed data, golden set
│   └── helpers/                  # Test utilities
├── package.json
├── tsconfig.json
├── bunfig.toml
├── drizzle.config.ts
├── docker-compose.yml
├── .env.example
└── ARCHITECTURE.md
```

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 1.1.1 | Initialize Bun project | `bun init`, configure `tsconfig.json` with `strict: true`, path aliases (`@/` → `src/`). Create `bunfig.toml` if needed | `package.json`, `tsconfig.json`, `bunfig.toml` |
| 1.1.2 | Install core dependencies | Runtime: `hono`, `@hono/zod-validator`, `zod`. AI: `ai`, `@ai-sdk/anthropic`, `@ai-sdk/openai`. DB: `drizzle-orm`, `postgres`, `drizzle-kit`. Cache: `ioredis`. Search: `typesense`. Concurrency: `async-mutex`. Utils: `nanoid`, `slugify` | `package.json` |
| 1.1.3 | Install dev dependencies | `@types/bun`, `typescript`, `prettier`, `@biomejs/biome` (or eslint) | `package.json` |
| 1.1.4 | Create `.env.example` | Document all required and optional env vars with example values | `.env.example` |
| 1.1.5 | Create `.gitignore` | Ignore `node_modules`, `.env`, `dist`, `*.log`, `.DS_Store` | `.gitignore` |

### `.env.example`

```env
# Database
DATABASE_URL=postgresql://skills:skills_dev@localhost:5432/skills_graph

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
PORT=3000
NODE_ENV=development
```

### Checklist

- [ ] `bun init` completed, `package.json` exists
- [ ] `tsconfig.json` has `strict: true` and path aliases configured
- [ ] All runtime dependencies installed and importable
- [ ] All dev dependencies installed
- [ ] `.env.example` created with all variables documented
- [ ] `.gitignore` covers all standard exclusions
- [ ] `bun run dev` script defined in `package.json` (e.g., `bun --watch src/index.ts`)
- [ ] `bun run test` script defined in `package.json`

---

## 1.2 Docker Infrastructure

### Context

Local development requires PostgreSQL 16 with extensions (`pgvector`, `ltree`, `pg_trgm`) and Redis 7. Use the `pgvector/pgvector:pg16` Docker image which bundles pgvector. The `ltree` and `pg_trgm` extensions are built-in to PostgreSQL.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 1.2.1 | Create `docker-compose.yml` | PostgreSQL 16 (pgvector image) + Redis 7-alpine. Map ports 5432 and 6379. Use named volume for Postgres data persistence | `docker-compose.yml` |
| 1.2.2 | Add init SQL script | Mount an `init.sql` that enables `ltree` and `pg_trgm` extensions on database creation. pgvector is auto-enabled by the image | `docker/init.sql` |
| 1.2.3 | Add Typesense container | Typesense server for full-text search (needed in Phase 3 but set up now to avoid reconfiguration) | `docker-compose.yml` |
| 1.2.4 | Add npm scripts for Docker | `bun run infra:up` → `docker compose up -d`, `bun run infra:down` → `docker compose down`, `bun run infra:reset` → down + remove volumes + up | `package.json` |

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

volumes:
  pgdata:
  redisdata:
  typesensedata:
```

### `docker/init.sql`

```sql
CREATE EXTENSION IF NOT EXISTS ltree;
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
```

### Checklist

- [ ] `docker compose up -d` starts all 3 services without errors
- [ ] PostgreSQL is accessible on `localhost:5432`
- [ ] Redis is accessible on `localhost:6379`
- [ ] Typesense is accessible on `localhost:8108`
- [ ] `docker compose down && docker compose up -d` restarts cleanly (data persists)
- [ ] `bun run infra:reset` cleans all volumes and starts fresh
- [ ] PostgreSQL has `ltree`, `vector`, `pg_trgm` extensions enabled:
  ```bash
  docker exec -it skills-graph-postgres-1 psql -U skills -d skills_graph \
    -c "SELECT extname FROM pg_extension;"
  # → ltree, vector, pg_trgm
  ```

---

## 1.3 Environment Configuration

### Context

All environment variables are validated at startup using Zod. The app refuses to start if required variables are missing or malformed. This prevents runtime errors from misconfiguration.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 1.3.1 | Environment schema | Define Zod schema for all env vars. Include `TYPESENSE_URL` (default `http://localhost:8108`) and `TYPESENSE_API_KEY`. Mark `HELICONE_API_KEY` as optional. Parse `PORT` as number. Validate `DATABASE_URL` starts with `postgresql://` | `src/config/env.ts` |
| 1.3.2 | Export typed config | Export a `config` object with typed, validated values. All modules import from here instead of `process.env` | `src/config/env.ts` |
| 1.3.3 | Fail-fast on invalid config | If Zod parsing fails, print clear error message listing missing/invalid vars and exit with code 1 | `src/config/env.ts` |

### `src/config/env.ts` (reference)

```typescript
import { z } from "zod";

const envSchema = z.object({
  DATABASE_URL: z.string().startsWith("postgresql://"),
  REDIS_URL: z.string().startsWith("redis://"),
  ANTHROPIC_API_KEY: z.string().min(1),
  OPENAI_API_KEY: z.string().min(1),
  TYPESENSE_URL: z.string().url().default("http://localhost:8108"),
  TYPESENSE_API_KEY: z.string().min(1),
  HELICONE_API_KEY: z.string().optional(),
  PORT: z.coerce.number().default(3000),
  NODE_ENV: z.enum(["development", "production", "test"]).default("development"),
});

const parsed = envSchema.safeParse(process.env);

if (!parsed.success) {
  console.error("Invalid environment configuration:");
  console.error(parsed.error.flatten().fieldErrors);
  process.exit(1);
}

export const config = parsed.data;
```

### Checklist

- [ ] `src/config/env.ts` exists and exports typed `config`
- [ ] App exits with clear error message when `DATABASE_URL` is missing
- [ ] App exits with clear error message when `ANTHROPIC_API_KEY` is missing
- [ ] App starts successfully when all required vars are set
- [ ] `TYPESENSE_URL` defaults to `http://localhost:8108` when not set
- [ ] `TYPESENSE_API_KEY` is required — app exits if missing
- [ ] `HELICONE_API_KEY` is optional — app starts without it
- [ ] `PORT` defaults to 3000 when not set
- [ ] `NODE_ENV` defaults to "development" when not set

---

## 1.3b Centralized Constants

### Context

All tunable values (thresholds, TTLs, limits, dimensions, timeouts, etc.) are defined in a single **constants file** that serves as the single source of truth. This prevents hardcoded magic numbers from scattering across the codebase and makes values easy to find, change, and keep consistent. Where appropriate, constants read from environment variables with sensible defaults.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 1.3b.1 | Create constants file | Define all configurable values grouped by domain: Embedding & Vector Search, Extraction Pipeline, Discovery & Review, API & Pagination, Rate Limiting, Workers & Events, Infrastructure, Quality Thresholds. Each constant has a JSDoc comment explaining its purpose | `src/config/constants.ts` |
| 1.3b.2 | Env var overrides | For operationally-tunable values, read from `process.env` with the constant as the fallback default. See table below for which constants support env overrides | `src/config/constants.ts` |

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

- [ ] `src/config/constants.ts` exists and exports all constants listed above
- [ ] Constants are grouped by domain with clear JSDoc comments
- [ ] Env-overridable constants fall back to their default when env var is unset
- [ ] `DB_POOL_SIZE` resolves to 10 in dev and 50 in production when env var is unset
- [ ] All other modules import from `@/config/constants` instead of hardcoding values
- [ ] No magic numbers remain inline in service or route files

---

## 1.4 AI SDK Provider Registry

### Context

The Vercel AI SDK's `createProviderRegistry()` provides a unified interface to switch between LLM providers (Anthropic primary, OpenAI fallback) and embedding models. All services import models from this registry instead of creating provider instances directly.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 1.4.1 | Create provider registry | Set up `createProviderRegistry()` with `anthropic` and `openai` providers | `src/config/providers.ts` |
| 1.4.2 | Export model helpers | Export convenience functions: `getExtractionModel(tier)`, `getEmbeddingModel()`, `getClassificationModel()` that return the correct model instance based on tier strategy | `src/config/providers.ts` |
| 1.4.3 | Helicone proxy support | If `HELICONE_API_KEY` is set, configure Anthropic and OpenAI providers to route through Helicone proxy | `src/config/providers.ts` |

### `src/config/providers.ts` (reference)

```typescript
import { createProviderRegistry } from "ai";
import { anthropic } from "@ai-sdk/anthropic";
import { openai } from "@ai-sdk/openai";
import { config } from "./env";
import { EMBEDDING_MODEL, EMBEDDING_DIMENSIONS } from "./constants";

export const registry = createProviderRegistry({ anthropic, openai });

export function getExtractionModel(tier: "fast" | "standard" | "complex" = "standard") {
  switch (tier) {
    case "fast":    return anthropic("claude-haiku-4-5-20251001");
    case "standard": return anthropic("claude-haiku-4-5-20251001");
    case "complex": return anthropic("claude-sonnet-4-5-20250929");
  }
}

export function getEmbeddingModel() {
  return openai.embedding(EMBEDDING_MODEL, { dimensions: EMBEDDING_DIMENSIONS });
}

export function getClassificationModel() {
  return anthropic("claude-sonnet-4-5-20250929");
}
```

### Checklist

- [ ] `src/config/providers.ts` exists and exports registry + model helpers
- [ ] `getExtractionModel("fast")` returns Claude Haiku
- [ ] `getExtractionModel("complex")` returns Claude Sonnet
- [ ] `getEmbeddingModel()` returns the model specified by `EMBEDDING_MODEL` at `EMBEDDING_DIMENSIONS` dims (see `src/config/constants.ts`)
- [ ] `getClassificationModel()` returns Claude Sonnet
- [ ] When `HELICONE_API_KEY` is set, providers route through Helicone
- [ ] When `HELICONE_API_KEY` is NOT set, providers call APIs directly

---

## 1.5 Database Schema & Migrations

### Context

The full schema from ARCHITECTURE.md §2.7 defines 5 tables: `skills`, `skill_aliases`, `skill_relationships`, `locale_config`, `graph_changelog`. All tables use UUID primary keys, CHECK constraints for enums, and specialized indexes (HNSW for vectors, GiST for ltree, GIN for trigrams).

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 1.5.1 | Create initial migration | Full SQL schema from ARCHITECTURE.md §2.8. Include all 6 tables (`skills`, `skill_aliases`, `skill_relationships`, `skill_co_occurrences`, `locale_config`, `graph_changelog`), CHECK constraints, UNIQUE constraints, and indexes | `src/db/migrations/001_initial.sql` |
| 1.5.2 | Create graph version sequence | `CREATE SEQUENCE graph_version_seq;` for monotonic changelog version numbers | `src/db/migrations/001_initial.sql` |
| 1.5.3 | Drizzle schema definitions | Define all tables in Drizzle schema format for type-safe queries. Map pgvector `vector(1024)` and `ltree` as custom column types | `src/db/schema.ts` |
| 1.5.4 | Migration runner | `bun run db:migrate` script that reads `.sql` files from `migrations/` and applies them in order. Track applied migrations in a `_migrations` table | `src/db/migrate.ts` |
| 1.5.5 | Seed data script | `bun run db:seed` — insert locale_config rows (en, vi, fr, ja, zh) and 5-10 root skill categories (Technology, Business, Design, Science, Languages, Soft Skills) | `src/db/seed.ts` |

### Tables Summary

| Table | Purpose | Key Columns |
|---|---|---|
| `skills` | Canonical skill nodes | `id`, `external_id`, `canonical_name`, `slug`, `status`, `category`, `path` (ltree), `embedding` (vector), `version` |
| `skill_aliases` | Multi-locale surface forms | `skill_id` (FK), `surface_form`, `locale` (BCP-47), `is_primary`, `alias_embedding` (vector) |
| `skill_relationships` | Directed edges between skills | `source_skill_id`, `target_skill_id`, `relationship_type`, `confidence`, `weight`, `provenance` (incl. `empirical`), `status` |
| `skill_co_occurrences` | Empirical co-occurrence tracking | `skill_a_id`, `skill_b_id`, `co_occurrence_count`, `source_type_counts` (JSONB), `last_seen_at` |
| `locale_config` | Supported locales and coverage | `locale` (PK), `display_name`, `is_active`, `coverage_pct` |
| `graph_changelog` | Versioned mutation log for CDC | `graph_version`, `actor`, `mutation_type`, `entity_type`, `entity_id`, `diff_payload` (JSONB) |

### Indexes Created

| Index | Type | Purpose |
|---|---|---|
| `idx_skills_embedding` | HNSW (vector_cosine_ops) | Semantic similarity search on skills |
| `idx_aliases_embedding` | HNSW (vector_cosine_ops) | Semantic similarity search on aliases |
| `idx_skills_path` | GiST (ltree) | Hierarchical path queries |
| `idx_skills_name_trgm` | GIN (gin_trgm_ops) | Fuzzy text search on skill names |
| `idx_aliases_surface_trgm` | GIN (gin_trgm_ops) | Fuzzy text search on alias surface forms |
| `idx_skills_status` | B-tree (partial) | Fast lookup of active skills |
| `idx_relationships_source` | B-tree | Edge lookups by source skill |
| `idx_relationships_target` | B-tree | Edge lookups by target skill |
| `idx_changelog_version` | B-tree | CDC queries by version |

### Checklist

- [ ] `src/db/migrations/001_initial.sql` contains complete schema (6 tables, all constraints, all indexes)
- [ ] `CREATE SEQUENCE graph_version_seq` is included
- [ ] `bun run db:migrate` applies migration successfully
- [ ] `bun run db:migrate` is idempotent (running twice doesn't error)
- [ ] All 6 tables exist: `\dt` shows `skills`, `skill_aliases`, `skill_relationships`, `skill_co_occurrences`, `locale_config`, `graph_changelog`
- [ ] All CHECK constraints work: inserting invalid `status` value fails
- [ ] UNIQUE constraint works: inserting duplicate `slug` fails
- [ ] `bun run db:seed` inserts locale_config rows and root categories
- [ ] `bun run db:seed` is idempotent (running twice doesn't create duplicates)
- [ ] Drizzle schema (`src/db/schema.ts`) matches SQL schema
- [ ] Drizzle can perform basic SELECT/INSERT on all tables

---

## 1.6 Database & Redis Clients

### Context

Database access uses Drizzle ORM over `postgres` (porsager/postgres) for connection pooling. Redis uses `ioredis` with typed cache helpers. A pgvector serialization helper converts between JS arrays and PostgreSQL vector format.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 1.6.1 | PostgreSQL client | Connection pool via `postgres` library with Drizzle ORM wrapper. Pool size from `DB_POOL_SIZE` in `src/config/constants.ts` (10 dev / 50 prod, overridable via `DB_POOL_SIZE` env var). Include connection error handling and logging | `src/db/client.ts` |
| 1.6.2 | Redis client | ioredis connection with reconnect strategy (exponential backoff). Export typed cache helpers | `src/db/redis.ts` |
| 1.6.3 | Cache helpers | `cacheGet<T>(key): Promise<T | null>` — JSON.parse cached value. `cacheSet(key, value, ttlSeconds)` — JSON.stringify and SET EX. `cacheDelete(key)`. `cacheMakeKey(...parts)` — join parts with `:` | `src/db/redis.ts` |
| 1.6.4 | pgvector helpers | `toSql(embedding: number[]): string` — convert `[0.1, 0.2, ...]` to `'[0.1,0.2,...]'`. `fromSql(pgString): number[]` — parse vector string back to array | `src/lib/pgvector.ts` |
| 1.6.5 | Graceful shutdown helpers | Export `closeDatabase()` and `closeRedis()` functions for clean shutdown | `src/db/client.ts`, `src/db/redis.ts` |

### Checklist

- [ ] `src/db/client.ts` exports Drizzle `db` instance
- [ ] Database connection works: `db.select().from(skills).limit(1)` returns without error
- [ ] Connection pool size is configurable via `DB_POOL_SIZE` env var (see `src/config/constants.ts` for defaults)
- [ ] `src/db/redis.ts` exports `redis` client and cache helpers
- [ ] Redis connection works: `redis.ping()` returns "PONG"
- [ ] `cacheSet("test:key", { foo: "bar" }, 60)` stores value
- [ ] `cacheGet<{foo:string}>("test:key")` retrieves typed value
- [ ] `cacheDelete("test:key")` removes value
- [ ] `cacheMakeKey("taxonomy", "skill", "abc123")` returns `"taxonomy:skill:abc123"`
- [ ] `src/lib/pgvector.ts` — `toSql([0.1, 0.2, 0.3])` returns `'[0.1,0.2,0.3]'`
- [ ] `fromSql('[0.1,0.2,0.3]')` returns `[0.1, 0.2, 0.3]`
- [ ] Graceful shutdown: `closeDatabase()` drains pool, `closeRedis()` disconnects cleanly

---

## 1.7 Hono App Skeleton

### Context

The Hono app serves as the API gateway. At this stage, only the skeleton is set up — route groups are mounted but handlers are placeholders. Middleware includes error handling, request ID, CORS, and timing.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 1.7.1 | Hono app entry | Create Hono app instance. Mount route groups as placeholders: `/api/skills`, `/api/edges`, `/api/extract`, `/api/taxonomy`, `/api/review-queue` | `src/index.ts` |
| 1.7.2 | Health check endpoint | `GET /health` — check DB connection (`SELECT 1`), Redis connection (`PING`), return `{ status, version, db, redis, uptime_seconds }` | `src/routes/health.ts` |
| 1.7.3 | Error handler middleware | Catch-all `app.onError()` handler. Return `{ error: message, status, request_id }`. Log the full error with stack trace. Map known error types to HTTP status codes (ZodError → 400, NotFoundError → 404, ConflictError → 409, etc.) | `src/middleware/error-handler.ts` |
| 1.7.4 | Request ID middleware | Generate `crypto.randomUUID()` for each request. Set `x-request-id` response header. Store in Hono context for logging | `src/middleware/request-id.ts` |
| 1.7.5 | CORS middleware | Use `hono/cors` with configurable origins. Default: allow all in development, restrictive in production | `src/index.ts` |
| 1.7.6 | Custom error classes | `NotFoundError`, `ConflictError`, `ValidationError`, `CycleDetectedError` extending base `AppError` class with `statusCode` property | `src/lib/errors.ts` |
| 1.7.7 | Server startup | Listen on configured PORT. Log startup message with port and environment | `src/index.ts` |

### `src/index.ts` (reference)

```typescript
import { Hono } from "hono";
import { cors } from "hono/cors";
import { config } from "./config/env";
import { healthRoutes } from "./routes/health";
import { requestId } from "./middleware/request-id";
import { errorHandler } from "./middleware/error-handler";

const app = new Hono();

// Middleware
app.use("*", cors());
app.use("*", requestId());

// Routes
app.route("/health", healthRoutes);
// Placeholder route groups (implemented in Phase 2+)
// app.route("/api/skills", skillRoutes);
// app.route("/api/edges", edgeRoutes);
// app.route("/api/extract", extractRoutes);
// app.route("/api/taxonomy", taxonomyRoutes);
// app.route("/api/review-queue", reviewRoutes);

// Error handler
app.onError(errorHandler);

export default {
  port: config.PORT,
  fetch: app.fetch,
};
```

### Checklist

- [ ] `bun run dev` starts server on PORT 3000
- [ ] `GET /health` returns `{ status: "ok", version: "0.1.0", db: "connected", redis: "connected", uptime_seconds: N }`
- [ ] `GET /health` returns `{ status: "degraded", db: "disconnected" }` when DB is down
- [ ] All responses include `x-request-id` header
- [ ] `GET /nonexistent` returns `{ error: "Not Found", status: 404, request_id: "..." }`
- [ ] CORS headers are present in responses
- [ ] Error handler catches thrown errors and returns structured JSON
- [ ] `NotFoundError`, `ConflictError`, `ValidationError` classes exist
- [ ] Server logs startup message: `"Skills Graph API started on port 3000 (development)"`
- [ ] `Ctrl+C` triggers graceful shutdown (closes DB pool, Redis connection)

---

## 1.8 Initial Tests

### Context

Set up the test infrastructure with `bun test`. Write initial tests for configuration, utilities, and health check.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 1.8.1 | Test configuration | Configure `bun test` in `package.json`. Set up test environment (use `.env.test` or inline env vars) | `package.json` |
| 1.8.2 | Test helpers | Create helpers for spinning up test DB, resetting state between tests, creating test Hono app instance | `test/helpers/setup.ts` |
| 1.8.3 | Config tests | Test that env validation rejects missing required vars. Test default values for optional vars | `test/config/env.test.ts` |
| 1.8.4 | pgvector helper tests | Test `toSql()` and `fromSql()` with various vector sizes and edge cases (empty, single element, large dimensions) | `test/lib/pgvector.test.ts` |
| 1.8.5 | Health check test | Test `GET /health` returns correct shape and status | `test/routes/health.test.ts` |
| 1.8.6 | Error handler tests | Test that different error types map to correct HTTP status codes | `test/middleware/error-handler.test.ts` |

### Checklist

- [ ] `bun test` runs without errors
- [ ] Config tests pass: invalid env → exits, valid env → config object
- [ ] pgvector helper tests pass: `toSql`, `fromSql` round-trip correctly
- [ ] Health check test passes: correct JSON shape when DB/Redis are up
- [ ] Error handler tests pass: ZodError → 400, NotFoundError → 404, generic → 500
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
bun run db:migrate  # → "Migration 001_initial applied"
bun run db:seed     # → "Seeded 5 locales, 6 root categories"

# Verify tables
docker exec -it skills-graph-postgres-1 psql -U skills -d skills_graph \
  -c "\dt"
# → 6 tables (skills, skill_aliases, skill_relationships, skill_co_occurrences, locale_config, graph_changelog, _migrations)

# Verify extensions
docker exec -it skills-graph-postgres-1 psql -U skills -d skills_graph \
  -c "SELECT extname FROM pg_extension;"
# → ltree, vector, pg_trgm, plpgsql

# Verify seed data
docker exec -it skills-graph-postgres-1 psql -U skills -d skills_graph \
  -c "SELECT canonical_name FROM skills;"
# → Technology, Business, Design, Science, Languages, Soft Skills

# 3. Application
bun run dev &
sleep 2

curl http://localhost:3000/health | jq
# → {"status":"ok","version":"0.1.0","db":"connected","redis":"connected","uptime_seconds":2}

# Verify 404 handling
curl http://localhost:3000/api/nonexistent | jq
# → {"error":"Not Found","status":404,"request_id":"..."}

kill %1

# 4. Tests
bun test
# → All tests pass

echo "Phase 1 complete ✓"
```

---

## Phase 1 Master Checklist

### 1.1 Project Initialization
- [ ] Bun project initialized with `package.json`
- [ ] `tsconfig.json` configured with `strict: true` and path aliases
- [ ] All dependencies installed (runtime + dev)
- [ ] `.env.example` created
- [ ] `.gitignore` configured

### 1.2 Docker Infrastructure
- [ ] `docker-compose.yml` with PostgreSQL, Redis, Typesense
- [ ] `docker/init.sql` enables extensions
- [ ] All containers start and pass health checks
- [ ] npm scripts for infra management (`infra:up`, `infra:down`, `infra:reset`)

### 1.3 Environment Configuration
- [ ] `src/config/env.ts` validates all env vars with Zod
- [ ] App fails fast with clear error on missing vars
- [ ] Optional vars have defaults
- [ ] Typed `config` export used throughout codebase

### 1.3b Centralized Constants
- [ ] `src/config/constants.ts` exports all tunable values grouped by domain (incl. Section Weighting, Co-occurrence & Empirical)
- [ ] Env-overridable constants read from `process.env` with defaults
- [ ] All modules import from `@/config/constants` — no inline magic numbers

### 1.4 AI SDK Provider Registry
- [ ] `src/config/providers.ts` exports provider registry
- [ ] Model tier helpers: `getExtractionModel()`, `getEmbeddingModel()`, `getClassificationModel()`
- [ ] Helicone proxy conditionally enabled

### 1.5 Database Schema & Migrations
- [ ] `001_initial.sql` contains complete schema (5 tables + sequence)
- [ ] All CHECK constraints, UNIQUE constraints, indexes defined
- [ ] `bun run db:migrate` works and is idempotent
- [ ] `bun run db:seed` populates initial data
- [ ] Drizzle schema matches SQL

### 1.6 Database & Redis Clients
- [ ] PostgreSQL connection pool with Drizzle ORM
- [ ] Redis client with typed cache helpers
- [ ] pgvector serialization helpers
- [ ] Graceful shutdown functions

### 1.7 Hono App Skeleton
- [ ] Server starts on configured port
- [ ] `GET /health` works and checks dependencies
- [ ] Error handler returns structured JSON
- [ ] Request ID middleware adds `x-request-id` header
- [ ] CORS enabled
- [ ] Custom error classes defined
- [ ] Graceful shutdown on SIGTERM

### 1.8 Initial Tests
- [ ] `bun test` infrastructure set up
- [ ] Config validation tests pass
- [ ] pgvector helper tests pass
- [ ] Health check endpoint tests pass
- [ ] Error handler tests pass
