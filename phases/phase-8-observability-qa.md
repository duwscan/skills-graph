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
| 8.1.1 | Helicone proxy setup | In `AiConfig.java`: when `HELICONE_API_KEY` is set, override `spring.ai.anthropic.base-url` to `https://anthropic.helicone.ai` and add `Helicone-Auth` default header. Implement as `@Conditional` or profile-based bean override | `src/main/java/com/skillsgraph/config/AiConfig.java` |
| 8.1.2 | Custom properties | `HeliconeUtils.getHeaders(String task, String tier)` returns `Map<String,String>` with `Helicone-Property-Task`, `Helicone-Property-Tier`, `Helicone-Property-Source`. Passed as `.defaultHeaders()` on `ChatClient` builders | `src/main/java/com/skillsgraph/util/HeliconeUtils.java` |
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
| 8.2.1 | Structured JSON logger | Logback + `logstash-logback-encoder` for JSON structured output. Log fields: `requestId` (from MDC), `timestamp`, `level`, `message`, `data`. Configure via `logging.level.root` in `application.yml` (default: `INFO` prod, `DEBUG` dev) | `src/main/resources/logback-spring.xml` |
| 8.2.2 | Request logging filter | `OncePerRequestFilter` extending `RequestLoggingFilter`: logs `{ method, path, status, durationMs, requestId, userAgent }`. INFO for 2xx, WARN for 4xx, ERROR for 5xx | `src/main/java/com/skillsgraph/middleware/RequestLoggingFilter.java` |
| 8.2.3 | LLM call logging | `LlmCallLogger` aspect (`@Aspect`) or wrapper: logs `{ task, model, inputTokens, outputTokens, latencyMs, cacheHit, success, error }` as INFO. Integrates with Micrometer for metrics | `src/main/java/com/skillsgraph/util/LlmCallLogger.java` |
| 8.2.4 | Enhanced health check | Custom `HealthIndicator` beans for each dependency. Spring Boot Actuator aggregates them. Expose `GET /actuator/health` with full component details: DB pool stats, Redis memory, search service status, current graph version | `src/main/java/com/skillsgraph/health/` |
| 8.2.5 | Metrics endpoint | Add `micrometer-registry-prometheus`. `GET /actuator/prometheus` exposes metrics: `http_requests_total`, `http_request_duration_seconds`, `llm_calls_total`, `llm_call_duration_seconds`, `extraction_skills_found`, `cache_hit_ratio` | `src/main/resources/application.yml` |

### Checklist

- [ ] All logs are JSON structured (Logback + logstash-logback-encoder)
- [ ] Request ID (MDC `requestId`) appears in every log line
- [ ] Every request logs `durationMs`
- [ ] LLM calls: every call logs model, tokens, latency, success
- [ ] `GET /actuator/health` returns comprehensive component status
- [ ] `GET /actuator/health` returns 503 when any dependency is down
- [ ] Log level configurable via `logging.level.root` or `LOG_LEVEL` env
- [ ] No sensitive data in logs (no API keys, no PII, no full prompts in production)

---

## 8.3 Rate Limiting & Security

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 8.3.1 | Rate limiter middleware | Redis-backed sliding window rate limiter. Limits per API key (or per IP if no key): Read endpoints: `RATE_LIMIT_READ` req/min. Extraction endpoints: `RATE_LIMIT_EXTRACT` req/min. Mutation endpoints: `RATE_LIMIT_MUTATE` req/min. All configurable via env vars (see `src/main/java/com/skillsgraph/config/AppConstants.java`). Return `429 Too Many Requests` with `Retry-After` header when exceeded | `src/main/java/com/skillsgraph/middleware/RateLimitFilter.java` |
| 8.3.2 | API key authentication | `ApiKeyAuthFilter extends OncePerRequestFilter`: extracts `Authorization: Bearer <key>`. Validates against Redis hash `api_keys:{key}` → `{ role, name, createdAt }`. Roles: `READER`, `CURATOR`, `ADMIN`. Sets `SecurityContext` | `src/main/java/com/skillsgraph/middleware/ApiKeyAuthFilter.java` |
| 8.3.3 | Role-based method security | `@PreAuthorize("hasRole('CURATOR')")` / `@PreAuthorize("hasRole('ADMIN')")` on controller methods. Enable with `@EnableMethodSecurity` | `src/main/java/com/skillsgraph/controller/` |
| 8.3.4 | Input size limits | Verify all Jakarta Bean Validation `@Size` annotations enforce limits from `AppConstants`: extraction text ≤ `EXTRACTION_MAX_TEXT_LENGTH`, batch ≤ `BATCH_MAX_DOCUMENTS`, skill name ≤ `SKILL_NAME_MAX_LENGTH`, description ≤ `SKILL_DESCRIPTION_MAX_LENGTH`, alias ≤ `ALIAS_MAX_LENGTH`. Set `spring.servlet.multipart.max-request-size` in `application.yml` | `src/main/java/com/skillsgraph/dto/`, `src/main/resources/application.yml` |
| 8.3.5 | Security headers | Add `spring-security-web` or custom `OncePerRequestFilter` adding: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `X-XSS-Protection: 1; mode=block`, `Strict-Transport-Security` | `src/main/java/com/skillsgraph/middleware/SecurityHeadersFilter.java` |

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
- [ ] Input limits: text > `EXTRACTION_MAX_TEXT_LENGTH` chars → 400 validation error (see `src/main/java/com/skillsgraph/config/AppConstants.java`)
- [ ] Input limits: batch > `BATCH_MAX_DOCUMENTS` docs → 400 validation error (see `src/main/java/com/skillsgraph/config/AppConstants.java`)
- [ ] Body size: request > `BODY_SIZE_LIMIT` → 413 Payload Too Large (see `src/main/java/com/skillsgraph/config/AppConstants.java`)
- [ ] Security headers present in all responses

---

## 8.4 Comprehensive Test Suite

### Context

Tests are organized in 4 tiers: unit tests (fast, no external deps), integration tests (require DB/Redis), extraction tests (require LLM API), and golden set evaluation (quality measurement).

### Tasks

| # | Test Category | Tests | Files |
|---|---|---|---|
| 8.4.1 | Unit — Guardrails | Cycle detection: simple cycle, transitive cycle, diamond DAG, deep chain (10 levels), valid DAG (no false positives). Sanitization: HTML stripping, Unicode normalization, whitespace handling, special characters. Duplicate thresholds: similarity at `SIMILARITY_DUPLICATE_THRESHOLD` → duplicate, just below → not duplicate (see `src/main/java/com/skillsgraph/config/AppConstants.java`) | `src/test/java/com/skillsgraph/service/GuardrailServiceTest.java` |
| 8.4.2 | Unit — Chunker & Section Detection | Section-based: resume with 5 sections → 5 chunks. Sliding window: 4000-word blob → 3 chunks with overlap. Short text: 100 words → 1 chunk. Edge cases: empty text, single paragraph, very long single section. **Section detector**: JD with "Requirements"/"Responsibilities" → `type: "jd"` with correct weights. CV with "Skills"/"Experience" → `type: "cv"`. Plain text → `type: "generic"` | `src/test/java/com/skillsgraph/service/DocumentChunkerTest.java`, `src/test/java/com/skillsgraph/service/SectionDetectorTest.java` |
| 8.4.3 | Unit — Section Weighting | Skill in "Requirements" section → confidence × 1.0. Skill in "Nice to have" → confidence × 0.75. Skill in "Company description" → confidence × 0.5. Generic text without sections → no weight adjustment (1.0×). All JD and CV weight constants applied correctly (see `src/main/java/com/skillsgraph/config/AppConstants.java`) | `src/test/java/com/skillsgraph/service/SectionWeightingTest.java` |
| 8.4.4 | Unit — Merge & Dedup | Result merging: skill in 2 chunks → highest confidence kept. Evidence combination: arrays merged and deduplicated. Empty chunks: no crash. All skills below min_confidence: empty result | `src/test/java/com/skillsgraph/service/MergeDedupTest.java` |
| 8.4.5 | Unit — Slug generator | Basic: "Machine Learning" → "machine-learning". Unicode: "Café Brewing" → "cafe-brewing". Special chars: "C++" → "c-plus-plus". Duplicate handling: "Python" → "python", "Python" → "python-2" | `src/test/java/com/skillsgraph/util/SlugUtilsTest.java` |
| 8.4.6 | Integration — CRUD lifecycle | Full lifecycle test: create root → create child → add aliases → create edges → verify ancestors/descendants → update → deprecate → verify changelog has all entries | `src/test/java/com/skillsgraph/integration/CrudLifecycleTest.java` |
| 8.4.7 | Integration — Extraction | Run extraction against 5 test documents with known expected skills. Verify each document returns at least 3 expected skills with confidence > 0.5. Measure precision and recall. **Verify section-aware weighting**: JD with "Requirements" section → skills have full confidence; skills from "Benefits" section → reduced confidence | `src/test/java/com/skillsgraph/integration/ExtractionTest.java` |
| 8.4.8 | Integration — Discovery | Feed text with unknown skills → verify candidates extracted → verify review queue populated → approve candidate → verify skill created with edges (`provenance = 'llm_predicted'`, `weight = 0.5`) → verify searchable in full-text search service → **verify activation event published to `reanalysis:jobs` stream** | `src/test/java/com/skillsgraph/integration/DiscoveryTest.java` |
| 8.4.9 | Integration — Merge | Create two skills with different aliases and edges → merge → verify: aliases transferred, edges re-pointed, no duplicates, source marked as merged, **co-occurrence data merged** (counts summed, `skill_a_id < skill_b_id` maintained), changelog complete | `src/test/java/com/skillsgraph/integration/MergeTest.java` |
| 8.4.10 | Integration — Search | Create 20+ skills with varied names → test keyword search (exact, partial, typo) → test semantic search (related concept) → test hybrid search (combined ranking) → verify category filtering | `src/test/java/com/skillsgraph/integration/SearchTest.java` |
| 8.4.11 | Integration — Co-occurrence | Extract skills from 10+ documents → verify co-occurrence pairs published to Redis Stream → verify worker upserts into `skill_co_occurrences` table → run `./mvnw spring-boot:run -Dspring-boot.run.arguments=--co-occurrence-process` → verify empirical edges created for pairs above `CO_OCCURRENCE_EDGE_THRESHOLD` → verify edge weights updated correctly → verify weight never exceeds 1.0 | `src/test/java/com/skillsgraph/integration/CoOccurrenceTest.java` |
| 8.4.12 | Integration — Re-analysis | Extract document with unknown skills → approve one discovered candidate → verify activation event triggers re-analysis worker → verify worker queries `extraction_logs` → verify matching documents queued for re-extraction → verify re-extraction picks up newly activated skill | `src/test/java/com/skillsgraph/integration/ReanalysisTest.java` |
| 8.4.13 | Golden set evaluation | 50+ labeled documents with ground-truth skill annotations. **Include JDs and CVs with section labels** to verify section-aware weighting. Run `./mvnw test:golden` to measure F1, precision, recall. Fail if F1 < `GOLDEN_SET_MIN_F1` (see `src/main/java/com/skillsgraph/config/AppConstants.java`). Report per-document breakdown. **Additional metric**: verify section-weighted confidence scores are within expected ranges | `src/test/java/com/skillsgraph/goldenset/GoldenSetEvaluationTest.java`, `src/test/resources/golden-set/` |
| 8.4.14 | Load test | Script using `Gatling / k6` or simple Bun loop: 50 concurrent extraction requests for 60 seconds. Measure p50/p95/p99 latency, throughput, error rate. Pass criteria: p99 < `LOAD_TEST_MAX_P99_SECONDS`s, errors < `LOAD_TEST_MAX_ERROR_RATE` (see `src/main/java/com/skillsgraph/config/AppConstants.java`) | `src/test/java/com/skillsgraph/load/ExtractionLoadTest.java` |

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

- [ ] `./mvnw test` runs all unit + integration tests (Testcontainers)
- [ ] Unit tests: guardrails, chunker, section detection, section weighting, merge, slug — all pass
- [ ] Integration tests: CRUD lifecycle — full flow passes
- [ ] Integration tests: extraction — returns expected skills from test documents with correct section weighting
- [ ] Integration tests: discovery — full flow from text to approved skill, activation event published
- [ ] Integration tests: merge — aliases, edges, and co-occurrence data transferred correctly
- [ ] Integration tests: search — keyword, semantic, hybrid all return relevant results
- [ ] Integration tests: co-occurrence — pairs recorded, empirical edges created above threshold
- [ ] Integration tests: re-analysis — activation triggers re-extraction of matching documents
- [ ] `./mvnw test:golden` — F1 ≥ `GOLDEN_SET_MIN_F1`, precision ≥ `GOLDEN_SET_MIN_PRECISION`, recall ≥ `GOLDEN_SET_MIN_RECALL` (see `src/main/java/com/skillsgraph/config/AppConstants.java`)
- [ ] `./mvnw test:golden` — section-weighted confidence scores within expected ranges
- [ ] `./mvnw gatling:test` — p99 < `LOAD_TEST_MAX_P99_SECONDS`s, error rate < `LOAD_TEST_MAX_ERROR_RATE` (see `src/main/java/com/skillsgraph/config/AppConstants.java`)
- [ ] Test coverage: all services have at least one test
- [ ] Tests are isolated: each test can run independently, no cross-test dependencies
- [ ] CI-friendly: tests can run in a Docker environment

---

## 8.5 Production Configuration

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 8.5.1 | Dockerfile | Multi-stage build: `FROM eclipse-temurin:21-jdk-alpine AS build` → copy `pom.xml` + `mvnw` → `./mvnw package -DskipTests` → `FROM eclipse-temurin:21-jre-alpine AS runtime` → copy JAR → `ENTRYPOINT ["java", "-jar", "app.jar"]`. Separate Docker Compose `workers` service with `--workers` Spring profile | `Dockerfile` |
| 8.5.2 | docker-compose.prod.yml | Production compose: `app` (2 replicas), `workers` (1 replica), `postgres`, `redis`, `full-text-search`. Health checks on all services. Restart policies (`unless-stopped`). Resource limits (memory, CPU). Environment from `.env.prod` | `docker-compose.prod.yml` |
| 8.5.3 | Environment validation | `AppProperties` with `@Validated` causes `BindException` on startup if required fields are missing. `@PostConstruct` warnings if `HELICONE_API_KEY` not set in production profile | `src/main/java/com/skillsgraph/config/AppProperties.java` |
| 8.5.4 | Graceful shutdown | Handle SIGTERM (Spring's graceful shutdown) and SIGINT: (1) Stop accepting new requests (Spring Web MVC). (2) Wait for in-flight requests to complete (timeout: 30s). (3) Close DB connection pool. (4) Close Redis connection. (5) Exit with code 0. Log each shutdown step | `src/main/java/com/skillsgraph/SkillsGraphApplication.java` |
| 8.5.5 | Startup checks | Spring Boot auto-runs Flyway migrations. HikariCP connects to DB on first use (configurable max wait). Lettuce connects to Redis. `ApplicationReadyEvent` listener logs "ready" with port/profile. Add `@HealthIndicator` retry if DB unreachable | `src/main/java/com/skillsgraph/SkillsGraphApplication.java` |

### Dockerfile

```dockerfile
FROM eclipse-temurin:21-jre AS base
WORKDIR /app

# Install dependencies
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN ./mvnw package -DskipTests

# Copy source
COPY src/ src/
COPY application.yml Java 21 compiler configuration ./

# Health check
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

EXPOSE 8080

# Default: API server
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Checklist

- [ ] `docker build -t skills-graph .` builds successfully
- [ ] `docker run skills-graph` starts the API server
- [ ] `docker run skills-graph java -jar app.jar --spring.profiles.active=workers` starts workers
- [ ] `docker-compose.prod.yml` starts all services with health checks
- [ ] App refuses to start with missing `DATABASE_URL` (clear error message)
- [ ] App waits for DB connection on startup (retry with backoff)
- [ ] SIGTERM (Spring's graceful shutdown) triggers graceful shutdown (logged steps)
- [ ] In-flight requests complete before shutdown
- [ ] After shutdown, exit code is 0
- [ ] Health check passes within 5s of startup
- [ ] Production compose: 2 app replicas behind load balancer

---

## Phase 8 Completion Verification

```bash
# Run full test suite
./mvnw test
# → All unit + integration tests pass

# Run golden set evaluation
./mvnw test:golden
# → F1: 0.87, Precision: 0.89, Recall: 0.85 ✓

# Run load test
./mvnw gatling:test
# → p50: 1.2s, p95: 3.8s, p99: 5.1s, errors: 0% ✓

# Test auth
curl http://localhost:8080/api/skills
# → 401 Unauthorized

curl -H "Authorization: Bearer test-reader-key" http://localhost:8080/api/skills
# → 200 OK

curl -H "Authorization: Bearer test-reader-key" -X POST http://localhost:8080/api/skills \
  -d '{"canonical_name":"Test"}'
# → 403 Forbidden (reader can't create)

curl -H "Authorization: Bearer test-curator-key" -X POST http://localhost:8080/api/skills \
  -d '{"canonical_name":"Test","category":"tool","path":"technology.test"}'
# → 201 Created

# Test rate limiting
for i in {1..25}; do
  curl -s -o /dev/null -w "%{http_code}\n" \
    -H "Authorization: Bearer test-reader-key" \
    -X POST http://localhost:8080/api/extract \
    -d '{"text":"test"}' &
done
wait
# → Some responses should be 429 (rate limited)

# Build production image
docker build -t skills-graph .
docker compose -f docker-compose.prod.yml up -d

# Verify production health
curl http://localhost:8080/health | jq
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
- [ ] JSON structured logging with Logback / SLF4J
- [ ] Request timing middleware (duration_ms on every request)
- [ ] LLM call metrics logging
- [ ] Enhanced health check with dependency stats
- [ ] Optional Prometheus metrics endpoint

### 8.3 Rate Limiting & Security
- [ ] Redis-backed sliding window rate limiter (`RateLimitFilter`)
- [ ] API key authentication with role-based access (`ApiKeyAuthFilter`)
- [ ] `@PreAuthorize` method security: READER, CURATOR, ADMIN roles
- [ ] Jakarta Bean Validation `@Size` limits enforced on all DTOs
- [ ] Security headers filter on all responses

### 8.4 Test Suite
- [ ] Unit tests: guardrails, chunker, section detection, section weighting, merge, slug
- [ ] Integration tests: CRUD lifecycle, extraction (with section weighting), discovery (with activation event), merge (with co-occurrence), search
- [ ] Integration tests: co-occurrence aggregation and empirical edge strengthening
- [ ] Integration tests: re-analysis on skill activation
- [ ] Golden set: F1 ≥ `GOLDEN_SET_MIN_F1`, section-weighted confidence validated (see `src/main/java/com/skillsgraph/config/AppConstants.java`)
- [ ] Load test: p99 < `LOAD_TEST_MAX_P99_SECONDS`s, errors < `LOAD_TEST_MAX_ERROR_RATE` (see `src/main/java/com/skillsgraph/config/AppConstants.java`)

### 8.5 Production Configuration
- [ ] Multi-stage Dockerfile (eclipse-temurin:21-jdk build, eclipse-temurin:21-jre runtime)
- [ ] `docker-compose.prod.yml` with replicas, health checks, resource limits
- [ ] `server.shutdown: graceful` — drain requests, close HikariCP + Lettuce
- [ ] Flyway auto-runs on startup; `AppProperties` validates required config
- [ ] Spring Boot production-ready actuator + metrics endpoints
