# Phase 8: Observability, Hardening & QA

> **Timeline:** Week 15-16
> **Dependencies:** Phase 4 (Extraction), Phase 5 (Discovery), Phase 7 (Workers)
> **Unlocks:** Production deployment
> **Context:** Tests must cover section-aware weighting, co-occurrence aggregation, re-analysis on skill activation, and empirical edge strengthening — features added across Phases 4-7

---

## Goal

Add monitoring, LLM observability, rate limiting, authentication, comprehensive test suites, and production configuration. This is the final hardening pass before production deployment.

---

## 8.1 LLM Observability (Helicone)

### Context

Helicone acts as a transparent proxy that logs every LLM call with prompt, response, latency, cost, and token counts. It requires zero code changes beyond adding a header.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 8.1.1 | Helicone proxy setup | In `src/config/providers.ts`: when `HELICONE_API_KEY` is set, configure Anthropic and OpenAI providers to route through Helicone by setting `baseURL` to Helicone gateway and adding `Helicone-Auth` header | `src/config/providers.ts` |
| 8.1.2 | Custom properties | For each LLM call, add Helicone custom properties headers: `Helicone-Property-Task` (extraction, classification, discovery, audit), `Helicone-Property-Tier` (haiku, sonnet, opus), `Helicone-Property-Source` (api, batch, worker). Add a helper function `getHeliconeHeaders(task, tier)` | `src/lib/helicone.ts` |
| 8.1.3 | Cost dashboard | Document Helicone dashboard setup: create views for cost by task type, cost by model tier, daily cost trend, error rate. Set alert: daily cost > 120% of 7-day average | Documentation |

### Checklist

- [ ] When `HELICONE_API_KEY` is set, all LLM calls route through Helicone
- [ ] When `HELICONE_API_KEY` is NOT set, calls go directly to providers (no error)
- [ ] Custom properties appear in Helicone dashboard
- [ ] Each LLM call in the codebase includes task/tier properties
- [ ] Helicone logs show prompt, response, latency, cost for every call
- [ ] Cost can be filtered by task type in Helicone dashboard

---

## 8.2 Application Observability

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 8.2.1 | Structured logger | JSON structured logger using `pino` (or `consola`). Log levels: debug, info, warn, error. Include `request_id`, `timestamp`, `level`, `message`, `data`. Configure level via `LOG_LEVEL` env (default: `info` in prod, `debug` in dev) | `src/lib/logger.ts` |
| 8.2.2 | Request logging middleware | Hono middleware that logs every request: `{ method, path, status, duration_ms, request_id, user_agent }`. Log at `info` level for successful requests, `warn` for 4xx, `error` for 5xx | `src/middleware/request-logger.ts` |
| 8.2.3 | LLM call logging | Wrapper around AI SDK calls that logs: `{ task, model, input_tokens, output_tokens, latency_ms, cache_hit, success, error? }`. Log at `info` level. Aggregate stats for monitoring | `src/lib/llm-logger.ts` |
| 8.2.4 | Enhanced health check | Expand `GET /health` to return: DB connection pool stats (active/idle/total), Redis connection status and memory usage, Typesense status and document count, current graph version, uptime, last extraction timestamp | `src/routes/health.ts` |
| 8.2.5 | Metrics endpoint | `GET /metrics` (optional) — return Prometheus-compatible metrics: `http_requests_total`, `http_request_duration_seconds`, `llm_calls_total`, `llm_call_duration_seconds`, `extraction_skills_found`, `cache_hit_ratio` | `src/routes/metrics.ts` |

### Checklist

- [ ] All logs are JSON structured (parseable by log aggregators)
- [ ] Request ID appears in every log line for a given request
- [ ] Request timing: every request logs duration_ms
- [ ] LLM calls: every call logs model, tokens, latency, success
- [ ] `GET /health` returns comprehensive dependency status
- [ ] `GET /health` returns 503 when any dependency is down
- [ ] Log level is configurable via `LOG_LEVEL` env
- [ ] No sensitive data in logs (no API keys, no PII, no full prompts in production)

---

## 8.3 Rate Limiting & Security

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 8.3.1 | Rate limiter middleware | Redis-backed sliding window rate limiter. Limits per API key (or per IP if no key): Read endpoints: `RATE_LIMIT_READ` req/min. Extraction endpoints: `RATE_LIMIT_EXTRACT` req/min. Mutation endpoints: `RATE_LIMIT_MUTATE` req/min. All configurable via env vars (see `src/config/constants.ts`). Return `429 Too Many Requests` with `Retry-After` header when exceeded | `src/middleware/rate-limit.ts` |
| 8.3.2 | API key authentication | Middleware that extracts API key from `Authorization: Bearer <key>` header. Validate against stored keys (env variable `API_KEYS` as JSON, or Redis hash `api_keys:{key} → { role, name, created_at }`). Support roles: `reader` (read-only), `curator` (read + write + review queue), `admin` (all including deprecate/merge) | `src/middleware/auth.ts` |
| 8.3.3 | Role-based route guard | Middleware factory `requireRole(role)` that checks the authenticated API key has the required role. Apply to mutation routes: `curator` for skill/alias/edge creation, `curator` for review queue decisions, `admin` for deprecate/merge | `src/middleware/auth.ts` |
| 8.3.4 | Input size limits | Verify all Zod schemas enforce maximum sizes: extraction text ≤ `EXTRACTION_MAX_TEXT_LENGTH` chars, batch ≤ `BATCH_MAX_DOCUMENTS` documents, skill name ≤ `SKILL_NAME_MAX_LENGTH` chars, description ≤ `SKILL_DESCRIPTION_MAX_LENGTH` chars, alias ≤ `ALIAS_MAX_LENGTH` chars. Add Hono body size limit middleware (`BODY_SIZE_LIMIT` default). All limits from `src/config/constants.ts` | `src/schemas/*.ts`, `src/index.ts` |
| 8.3.5 | Security headers | Add `hono/secure-headers` middleware: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `X-XSS-Protection: 1; mode=block` | `src/index.ts` |

### Checklist

- [ ] Rate limiter: 101st read request in 1 minute → 429
- [ ] Rate limiter: response includes `Retry-After` header
- [ ] Rate limiter: different limits for read/extract/mutate endpoints
- [ ] Auth: request without API key → 401 Unauthorized
- [ ] Auth: request with invalid API key → 401 Unauthorized
- [ ] Auth: `reader` key can GET skills but cannot POST skills → 403
- [ ] Auth: `curator` key can POST skills and decide review queue
- [ ] Auth: `admin` key can deprecate and merge
- [ ] Auth: extraction endpoint requires at least `reader` role
- [ ] Input limits: text > `EXTRACTION_MAX_TEXT_LENGTH` chars → 400 validation error (see `src/config/constants.ts`)
- [ ] Input limits: batch > `BATCH_MAX_DOCUMENTS` docs → 400 validation error (see `src/config/constants.ts`)
- [ ] Body size: request > `BODY_SIZE_LIMIT` → 413 Payload Too Large (see `src/config/constants.ts`)
- [ ] Security headers present in all responses

---

## 8.4 Comprehensive Test Suite

### Context

Tests are organized in 4 tiers: unit tests (fast, no external deps), integration tests (require DB/Redis), extraction tests (require LLM API), and golden set evaluation (quality measurement).

### Tasks

| # | Test Category | Tests | Files |
|---|---|---|---|
| 8.4.1 | Unit — Guardrails | Cycle detection: simple cycle, transitive cycle, diamond DAG, deep chain (10 levels), valid DAG (no false positives). Sanitization: HTML stripping, Unicode normalization, whitespace handling, special characters. Duplicate thresholds: similarity at `SIMILARITY_DUPLICATE_THRESHOLD` → duplicate, just below → not duplicate (see `src/config/constants.ts`) | `test/unit/guardrails.test.ts` |
| 8.4.2 | Unit — Chunker & Section Detection | Section-based: resume with 5 sections → 5 chunks. Sliding window: 4000-word blob → 3 chunks with overlap. Short text: 100 words → 1 chunk. Edge cases: empty text, single paragraph, very long single section. **Section detector**: JD with "Requirements"/"Responsibilities" → `type: "jd"` with correct weights. CV with "Skills"/"Experience" → `type: "cv"`. Plain text → `type: "generic"` | `test/unit/chunker.test.ts`, `test/unit/section-detector.test.ts` |
| 8.4.3 | Unit — Section Weighting | Skill in "Requirements" section → confidence × 1.0. Skill in "Nice to have" → confidence × 0.75. Skill in "Company description" → confidence × 0.5. Generic text without sections → no weight adjustment (1.0×). All JD and CV weight constants applied correctly (see `src/config/constants.ts`) | `test/unit/section-weighting.test.ts` |
| 8.4.4 | Unit — Merge & Dedup | Result merging: skill in 2 chunks → highest confidence kept. Evidence combination: arrays merged and deduplicated. Empty chunks: no crash. All skills below min_confidence: empty result | `test/unit/merge-dedup.test.ts` |
| 8.4.5 | Unit — Slug generator | Basic: "Machine Learning" → "machine-learning". Unicode: "Café Brewing" → "cafe-brewing". Special chars: "C++" → "c-plus-plus". Duplicate handling: "Python" → "python", "Python" → "python-2" | `test/unit/slug.test.ts` |
| 8.4.6 | Integration — CRUD lifecycle | Full lifecycle test: create root → create child → add aliases → create edges → verify ancestors/descendants → update → deprecate → verify changelog has all entries | `test/integration/crud-lifecycle.test.ts` |
| 8.4.7 | Integration — Extraction | Run extraction against 5 test documents with known expected skills. Verify each document returns at least 3 expected skills with confidence > 0.5. Measure precision and recall. **Verify section-aware weighting**: JD with "Requirements" section → skills have full confidence; skills from "Benefits" section → reduced confidence | `test/integration/extraction.test.ts` |
| 8.4.8 | Integration — Discovery | Feed text with unknown skills → verify candidates extracted → verify review queue populated → approve candidate → verify skill created with edges (`provenance = 'llm_predicted'`, `weight = 0.5`) → verify searchable in Typesense → **verify activation event published to `reanalysis:jobs` stream** | `test/integration/discovery.test.ts` |
| 8.4.9 | Integration — Merge | Create two skills with different aliases and edges → merge → verify: aliases transferred, edges re-pointed, no duplicates, source marked as merged, **co-occurrence data merged** (counts summed, `skill_a_id < skill_b_id` maintained), changelog complete | `test/integration/merge.test.ts` |
| 8.4.10 | Integration — Search | Create 20+ skills with varied names → test keyword search (exact, partial, typo) → test semantic search (related concept) → test hybrid search (combined ranking) → verify category filtering | `test/integration/search.test.ts` |
| 8.4.11 | Integration — Co-occurrence | Extract skills from 10+ documents → verify co-occurrence pairs published to Redis Stream → verify worker upserts into `skill_co_occurrences` table → run `bun run co-occurrence:process` → verify empirical edges created for pairs above `CO_OCCURRENCE_EDGE_THRESHOLD` → verify edge weights updated correctly → verify weight never exceeds 1.0 | `test/integration/co-occurrence.test.ts` |
| 8.4.12 | Integration — Re-analysis | Extract document with unknown skills → approve one discovered candidate → verify activation event triggers re-analysis worker → verify worker queries `extraction_logs` → verify matching documents queued for re-extraction → verify re-extraction picks up newly activated skill | `test/integration/reanalysis.test.ts` |
| 8.4.13 | Golden set evaluation | 50+ labeled documents with ground-truth skill annotations. **Include JDs and CVs with section labels** to verify section-aware weighting. Run `bun test:golden` to measure F1, precision, recall. Fail if F1 < `GOLDEN_SET_MIN_F1` (see `src/config/constants.ts`). Report per-document breakdown. **Additional metric**: verify section-weighted confidence scores are within expected ranges | `test/golden-set/evaluate.test.ts`, `test/golden-set/fixtures/` |
| 8.4.14 | Load test | Script using `autocannon` or simple Bun loop: 50 concurrent extraction requests for 60 seconds. Measure p50/p95/p99 latency, throughput, error rate. Pass criteria: p99 < `LOAD_TEST_MAX_P99_SECONDS`s, errors < `LOAD_TEST_MAX_ERROR_RATE` (see `src/config/constants.ts`) | `test/load/extraction-load.ts` |

### Golden Set Fixture Format

```json
{
  "documents": [
    {
      "id": "jd-001",
      "text": "Senior ML Engineer with Python, TensorFlow...",
      "expected_skills": ["Machine Learning", "Python", "TensorFlow"],
      "source": "job_description"
    }
  ]
}
```

### Checklist

- [ ] `bun test` runs all unit + integration tests
- [ ] Unit tests: guardrails, chunker, section detection, section weighting, merge, slug — all pass
- [ ] Integration tests: CRUD lifecycle — full flow passes
- [ ] Integration tests: extraction — returns expected skills from test documents with correct section weighting
- [ ] Integration tests: discovery — full flow from text to approved skill, activation event published
- [ ] Integration tests: merge — aliases, edges, and co-occurrence data transferred correctly
- [ ] Integration tests: search — keyword, semantic, hybrid all return relevant results
- [ ] Integration tests: co-occurrence — pairs recorded, empirical edges created above threshold
- [ ] Integration tests: re-analysis — activation triggers re-extraction of matching documents
- [ ] `bun test:golden` — F1 ≥ `GOLDEN_SET_MIN_F1`, precision ≥ `GOLDEN_SET_MIN_PRECISION`, recall ≥ `GOLDEN_SET_MIN_RECALL` (see `src/config/constants.ts`)
- [ ] `bun test:golden` — section-weighted confidence scores within expected ranges
- [ ] `bun run test:load` — p99 < `LOAD_TEST_MAX_P99_SECONDS`s, error rate < `LOAD_TEST_MAX_ERROR_RATE` (see `src/config/constants.ts`)
- [ ] Test coverage: all services have at least one test
- [ ] Tests are isolated: each test can run independently, no cross-test dependencies
- [ ] CI-friendly: tests can run in a Docker environment

---

## 8.5 Production Configuration

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 8.5.1 | Dockerfile | Multi-stage build: `FROM oven/bun:1 AS base` → install deps → copy source → `CMD ["bun", "run", "src/index.ts"]`. Separate target for workers: `CMD ["bun", "run", "src/workers/index.ts"]` | `Dockerfile` |
| 8.5.2 | docker-compose.prod.yml | Production compose: `app` (2 replicas), `workers` (1 replica), `postgres`, `redis`, `typesense`. Health checks on all services. Restart policies (`unless-stopped`). Resource limits (memory, CPU). Environment from `.env.prod` | `docker-compose.prod.yml` |
| 8.5.3 | Environment validation | App refuses to start if required env vars missing. Additional prod validations: `NODE_ENV=production` requires `ANTHROPIC_API_KEY`, `OPENAI_API_KEY`. Warn if `HELICONE_API_KEY` not set in production | `src/config/env.ts` |
| 8.5.4 | Graceful shutdown | Handle SIGTERM and SIGINT: (1) Stop accepting new requests (Hono). (2) Wait for in-flight requests to complete (timeout: 30s). (3) Close DB connection pool. (4) Close Redis connection. (5) Exit with code 0. Log each shutdown step | `src/index.ts` |
| 8.5.5 | Startup checks | On app boot: (1) Validate env. (2) Connect to DB (fail if unreachable after 30s). (3) Connect to Redis (fail if unreachable after 10s). (4) Run pending migrations (optional, configurable). (5) Start PG NOTIFY listener. (6) Log "ready" with port and environment | `src/index.ts` |

### Dockerfile

```dockerfile
FROM oven/bun:1 AS base
WORKDIR /app

# Install dependencies
COPY package.json bun.lock* ./
RUN bun install --frozen-lockfile --production

# Copy source
COPY src/ src/
COPY drizzle.config.ts tsconfig.json ./

# Health check
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -f http://localhost:3000/health || exit 1

EXPOSE 3000

# Default: API server
CMD ["bun", "run", "src/index.ts"]
```

### Checklist

- [ ] `docker build -t skills-graph .` builds successfully
- [ ] `docker run skills-graph` starts the API server
- [ ] `docker run skills-graph bun run src/workers/index.ts` starts workers
- [ ] `docker-compose.prod.yml` starts all services with health checks
- [ ] App refuses to start with missing `DATABASE_URL` (clear error message)
- [ ] App waits for DB connection on startup (retry with backoff)
- [ ] SIGTERM triggers graceful shutdown (logged steps)
- [ ] In-flight requests complete before shutdown
- [ ] After shutdown, exit code is 0
- [ ] Health check passes within 5s of startup
- [ ] Production compose: 2 app replicas behind load balancer

---

## Phase 8 Completion Verification

```bash
# Run full test suite
bun test
# → All unit + integration tests pass

# Run golden set evaluation
bun test:golden
# → F1: 0.87, Precision: 0.89, Recall: 0.85 ✓

# Run load test
bun run test:load
# → p50: 1.2s, p95: 3.8s, p99: 5.1s, errors: 0% ✓

# Test auth
curl http://localhost:3000/api/skills
# → 401 Unauthorized

curl -H "Authorization: Bearer test-reader-key" http://localhost:3000/api/skills
# → 200 OK

curl -H "Authorization: Bearer test-reader-key" -X POST http://localhost:3000/api/skills \
  -d '{"canonical_name":"Test"}'
# → 403 Forbidden (reader can't create)

curl -H "Authorization: Bearer test-curator-key" -X POST http://localhost:3000/api/skills \
  -d '{"canonical_name":"Test","category":"tool","path":"technology.test"}'
# → 201 Created

# Test rate limiting
for i in {1..25}; do
  curl -s -o /dev/null -w "%{http_code}\n" \
    -H "Authorization: Bearer test-reader-key" \
    -X POST http://localhost:3000/api/extract \
    -d '{"text":"test"}' &
done
wait
# → Some responses should be 429 (rate limited)

# Build production image
docker build -t skills-graph .
docker compose -f docker-compose.prod.yml up -d

# Verify production health
curl http://localhost:3000/health | jq
# → All dependencies connected

# Test graceful shutdown
docker compose -f docker-compose.prod.yml stop app
# → Logs show graceful shutdown steps

echo "Phase 8 complete ✓"
echo "Skills Graph is production-ready! 🎉"
```

---

## Phase 8 Master Checklist

### 8.1 LLM Observability
- [ ] Helicone proxy conditionally enabled
- [ ] Custom properties (task, tier, source) on all LLM calls
- [ ] Cost tracking dashboard configured

### 8.2 Application Observability
- [ ] JSON structured logging with pino
- [ ] Request timing middleware (duration_ms on every request)
- [ ] LLM call metrics logging
- [ ] Enhanced health check with dependency stats
- [ ] Optional Prometheus metrics endpoint

### 8.3 Rate Limiting & Security
- [ ] Redis-backed sliding window rate limiter
- [ ] API key authentication with role-based access
- [ ] Route guards: reader, curator, admin roles
- [ ] Input size limits enforced
- [ ] Security headers on all responses

### 8.4 Test Suite
- [ ] Unit tests: guardrails, chunker, section detection, section weighting, merge, slug
- [ ] Integration tests: CRUD lifecycle, extraction (with section weighting), discovery (with activation event), merge (with co-occurrence), search
- [ ] Integration tests: co-occurrence aggregation and empirical edge strengthening
- [ ] Integration tests: re-analysis on skill activation
- [ ] Golden set: F1 ≥ `GOLDEN_SET_MIN_F1`, section-weighted confidence validated (see `src/config/constants.ts`)
- [ ] Load test: p99 < `LOAD_TEST_MAX_P99_SECONDS`s, errors < `LOAD_TEST_MAX_ERROR_RATE` (see `src/config/constants.ts`)

### 8.5 Production Configuration
- [ ] Dockerfile (multi-stage, production deps only)
- [ ] docker-compose.prod.yml with replicas, health checks, resource limits
- [ ] Graceful shutdown (drain requests, close connections)
- [ ] Startup checks (env, DB, Redis)
- [ ] All required env vars validated on boot
