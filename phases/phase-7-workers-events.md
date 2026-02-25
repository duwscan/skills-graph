# Phase 7: Workers, Events & Batch Processing

> **Timeline:** Week 13-14
> **Dependencies:** Phase 4 (Extraction), Phase 5 (Discovery), Phase 6 (Lifecycle)
> **Unlocks:** Phase 8 (Observability & QA)

---

## Goal

Implement background workers for async extraction, batch processing, event-driven full-text search service sync, **co-occurrence aggregation**, **re-analysis on skill activation**, and discovery signal aggregation using Redis Streams. This decouples heavy processing from the API request/response cycle using Spring `@Async` and Redis Streams.

---

## 7.1 Redis Streams Infrastructure

### Context

Redis Streams provides a lightweight message queue with consumer groups, acknowledgment, and dead-letter handling. It reuses the existing Redis infrastructure (no Kafka needed at this scale).

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 7.1.1 | Stream producer | `@Service EventProducer.publishEvent(String stream, Map<String,String> event)` — uses `RedisTemplate.opsForStream().add(stream, event)` to append events. Serializes payload as flat key-value pairs (Redis Streams requirement) | `src/main/java/com/skillsgraph/service/events/EventProducer.java` |
| 7.1.2 | Stream consumer base | Abstract `StreamConsumer` class: (1) `XREADGROUP` with block timeout. (2) Process each message via abstract `handleMessage()`. (3) `XACK` on success. (4) On failure: retry up to `STREAM_CONSUMER_MAX_RETRIES` times (see `src/main/java/com/skillsgraph/com.sk.skillsgraph.config/AppConstants.java`), then move to dead-letter stream (`{stream}:dead`). (5) Graceful shutdown on SIGTERM (Spring's graceful shutdown) | `src/main/java/com/skillsgraph/service/events/StreamConsumer.java` |
| 7.1.3 | Consumer group setup com.sk.skillsgraph.script | `./mvnw spring-boot:run -Dspring-boot.run.arguments=--streams-setup` — create consumer groups for all streams: `extraction:jobs` (group: `extractors`), `discovery:signals` (group: `discoverers`), `sync:full-text-search` (group: `syncers`), `co-occurrence:pairs` (group: `co-occurrence-workers`), `reanalysis:jobs` (group: `reanalyzers`). Idempotent (use `XGROUP CREATE ... MKSTREAM`) | `src/main/java/com/skillsgraph/com.sk.skillsgraph.script/StreamsSetupRunner.java` |
| 7.1.4 | Stream name constants | `StreamNames` interface with `String EXTRACTION_JOBS = "extraction:jobs"`, `DISCOVERY_SIGNALS`, `CO_OCCURRENCE_PAIRS`, `REANALYSIS_JOBS` | `src/main/java/com/skillsgraph/service/events/StreamNames.java` |

### Checklist

- [ ] `publishEvent("extraction:jobs", { doc_id, text })` adds message to stream
- [ ] `StreamConsumer` reads messages with `XREADGROUP` and consumer group
- [ ] `StreamConsumer` acknowledges processed messages with `XACK`
- [ ] `StreamConsumer` retries failed messages up to `STREAM_CONSUMER_MAX_RETRIES` times (see `src/main/java/com/skillsgraph/com.sk.skillsgraph.config/AppConstants.java`)
- [ ] `StreamConsumer` moves poison messages to dead-letter stream
- [ ] `StreamConsumer` shuts down gracefully on SIGTERM (Spring's graceful shutdown)
- [ ] `StreamsSetupRunner` creates all consumer groups on startup
- [ ] `StreamsSetupRunner` is idempotent (running twice doesn't error)
- [ ] Dead-letter messages are inspectable: `XRANGE extraction:jobs:dead - +`

---

## 7.2 Batch Extraction Worker

### Context

The batch extraction API accepts multiple documents, queues them as individual jobs in Redis Streams, and workers process them asynchronously. Users poll for results via a job status endpoint.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 7.2.1 | Batch job model | Store batch state in Redis: key `batch:{jobId}`, value `{ id, total, completed, failed, status, results: {}, created_at, completed_at }`. Status: `queued` → `processing` → `completed` / `partial_failure` | `src/main/java/com/skillsgraph/service/extraction/BatchExtractionService.java` |
| 7.2.2 | `POST /api/extract/batch` | Accept `{ documents: [{ id: string, text: string, metadata?: object }], options?: ExtractOptions }`. Max `BATCH_MAX_DOCUMENTS` documents (see `src/main/java/com/skillsgraph/com.sk.skillsgraph.config/AppConstants.java`). Generate `jobId` (UUID.randomUUID()). Create batch state in Redis. Publish each document as a message to `extraction:jobs` stream with `batch_id` and `doc_id`. Return `{ job_id, document_count, status: "queued" }` | `src/main/java/com/skillsgraph/com.sk.skillsgraph.controller/ExtractionController.java` |
| 7.2.3 | `GET /api/extract/jobs/:jobId` | Return batch job status. Include: `{ id, total, completed, failed, status, results: { [doc_id]: ExtractionResult }, created_at, completed_at, progress_pct }`. If `status = "completed"`, include all results | `src/main/java/com/skillsgraph/com.sk.skillsgraph.controller/ExtractionController.java` |
| 7.2.4 | Extraction worker | Extends `StreamConsumer`. Consumes from `extraction:jobs`. For each message: (1) run `SkillExtractionPipeline.extract(text)`, (2) store result in batch state (`HSET batch:{jobId} doc:{docId} <result>`), (3) increment completed count, (4) check if batch is done → update status, (5) ACK message | `src/main/java/com/skillsgraph/worker/ExtractionWorker.java` |
| 7.2.5 | Concurrency limit | Each worker processes up to `EXTRACTION_WORKER_CONCURRENCY` documents concurrently (semaphore). Configurable via `EXTRACTION_WORKER_CONCURRENCY` env var (see `src/main/java/com/skillsgraph/com.sk.skillsgraph.config/AppConstants.java`) | `src/main/java/com/skillsgraph/worker/ExtractionWorker.java` |
| 7.2.6 | Batch TTL | Batch results in Redis expire after `BATCH_RESULT_TTL_SECONDS` (see `src/main/java/com/skillsgraph/com.sk.skillsgraph.config/AppConstants.java`). Set TTL on batch key after completion | `src/main/java/com/skillsgraph/service/extraction/BatchExtractionService.java` |

### Checklist

- [ ] `POST /api/extract/batch` accepts up to `BATCH_MAX_DOCUMENTS` documents (see `src/main/java/com/skillsgraph/com.sk.skillsgraph.config/AppConstants.java`)
- [ ] `POST /api/extract/batch` returns immediately with job_id and `queued` status
- [ ] Worker picks up jobs from `extraction:jobs` stream
- [ ] Worker processes documents and stores results in Redis
- [ ] `GET /api/extract/jobs/:jobId` shows progress (completed/total)
- [ ] `GET /api/extract/jobs/:jobId` returns all results when batch is complete
- [ ] Batch status transitions: `queued` → `processing` → `completed`
- [ ] Failed documents: batch status becomes `partial_failure` with error details
- [ ] Concurrency: worker processes max `EXTRACTION_WORKER_CONCURRENCY` documents simultaneously (see `src/main/java/com/skillsgraph/com.sk.skillsgraph.config/AppConstants.java`)
- [ ] Results expire after `BATCH_RESULT_TTL_SECONDS` (see `src/main/java/com/skillsgraph/com.sk.skillsgraph.config/AppConstants.java`)
- [ ] `GET /api/extract/jobs/nonexistent` returns 404

---

## 7.3 Discovery Signal Worker

### Context

The extraction pipeline's `discovered_candidates` are published as signals to a Redis Stream. The discovery worker aggregates these signals and triggers the full discovery pipeline when a candidate reaches a threshold count.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 7.3.1 | Signal publishing | In `SkillExtractionPipeline`, after extraction: if `discovered_candidates` is non-empty, publish each to `discovery:signals` stream with `{ surface_form, normalized_form, category_guess, source }` | `src/main/java/com/skillsgraph/service/extraction/SkillExtractionPipeline.java` |
| 7.3.2 | Signal aggregation | The worker maintains counts in Redis: `signal:{normalized_form}` → count. Increment on each signal. When count reaches `DISCOVERY_SIGNAL_THRESHOLD` (see `src/main/java/com/skillsgraph/com.sk.skillsgraph.config/AppConstants.java`, configurable via `DISCOVERY_SIGNAL_THRESHOLD` env var), trigger full discovery | `src/main/java/com/skillsgraph/worker/DiscoveryWorker.java` |
| 7.3.3 | Discovery trigger | When threshold reached: (1) Check if candidate already exists in review queue → skip if pending. (2) Run `DiscoveryService.deduplicateCandidate()`. (3) Add to review queue if passes dedup. (4) Reset signal counter | `src/main/java/com/skillsgraph/worker/DiscoveryWorker.java` |
| 7.3.4 | Signal expiry | Signal counters expire after `DISCOVERY_SIGNAL_TTL_SECONDS` (see `src/main/java/com/skillsgraph/com.sk.skillsgraph.config/AppConstants.java`; if a candidate never reaches threshold, it's forgotten) | `src/main/java/com/skillsgraph/worker/DiscoveryWorker.java` |

### Checklist

- [ ] Extraction pipeline publishes `discovered_candidates` to `discovery:signals` stream
- [ ] Worker reads signals and increments counters in Redis
- [ ] Counter reaches threshold → triggers deduplication + review queue add
- [ ] Already-queued candidates are skipped (no duplicate queue entries)
- [ ] Signal counters have `DISCOVERY_SIGNAL_TTL_SECONDS` TTL (see `src/main/java/com/skillsgraph/com.sk.skillsgraph.config/AppConstants.java`)
- [ ] Threshold is configurable via `DISCOVERY_SIGNAL_THRESHOLD` env var (see `src/main/java/com/skillsgraph/com.sk.skillsgraph.config/AppConstants.java`)

---

## 7.4 full-text search service Sync Worker

### Context

Instead of synchronously updating full-text search service on every skill mutation (which slows down the API), mutations are published to a Redis Stream and a worker processes them asynchronously. The worker batches updates within a short time window for efficiency.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 7.4.1 | PG NOTIFY → Stream bridge | The PG NOTIFY listener (Phase 6.3.2) publishes skill mutation events to `sync:full-text-search` stream: `{ entity_type: "skill", entity_id, mutation_type }` | `src/main/java/com/skillsgraph/service/changelog/PgNotifyListener.java` |
| 7.4.2 | Sync worker | Consumes from `sync:full-text-search`. Collects events within a `TYPESENSE_SYNC_DEBOUNCE_MS` debounce window (see `src/main/java/com/skillsgraph/com.sk.skillsgraph.config/AppConstants.java`). Then batch-processes: for each unique `entity_id`, re-fetch the skill with aliases and upsert into full-text search service. For deprecated/merged skills, remove from full-text search service | `src/main/java/com/skillsgraph/worker/SearchSyncWorker.java` |
| 7.4.3 | Idempotent sync | Multiple events for the same skill within the debounce window are collapsed into a single full-text search service operation | `src/main/java/com/skillsgraph/worker/SearchSyncWorker.java` |

### Checklist

- [ ] PG NOTIFY listener publishes events to `sync:full-text-search` stream
- [ ] Worker reads events and debounces within `TYPESENSE_SYNC_DEBOUNCE_MS` window (see `src/main/java/com/skillsgraph/com.sk.skillsgraph.config/AppConstants.java`)
- [ ] Skill create/update → upsert in full-text search service
- [ ] Skill deprecate/merge → remove from full-text search service
- [ ] Multiple rapid updates to same skill → single full-text search service upsert
- [ ] full-text search service stays in sync within 1-2 seconds of any mutation

---

## 7.5 Co-occurrence Aggregation Worker

### Context

The extraction pipeline publishes skill pair events to a Redis Stream after every extraction. The co-occurrence worker consumes these events and upserts pairs into the `skill_co_occurrences` table. A separate periodic job scans for pairs exceeding the threshold and creates/strengthens relationship edges.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 7.5.1 | Co-occurrence event publishing | In `SkillExtractionPipeline`, after final merge: publish `{ skill_ids: string[], source_type: string }` to `co-occurrence:pairs` stream. Only include active skill IDs (not expanded, not discovered candidates) | `src/main/java/com/skillsgraph/service/extraction/SkillExtractionPipeline.java` |
| 7.5.2 | Co-occurrence worker | Extends `StreamConsumer`. Consumes from `co-occurrence:pairs`. For each message: generate all unique pairs from `skill_ids` (order by UUID for consistent key), upsert into `skill_co_occurrences` table (increment count, update `source_type_counts`, set `last_seen_at`). Batch within `CO_OCCURRENCE_BATCH_WINDOW_MS` debounce window (see `src/main/java/com/skillsgraph/com.sk.skillsgraph.config/AppConstants.java`) | `src/main/java/com/skillsgraph/worker/CoOccurrenceWorker.java` |
| 7.5.3 | Edge strengthening job | `./mvnw spring-boot:run -Dspring-boot.run.arguments=--co-occurrence-process` — periodic com.sk.skillsgraph.script (run via cron, e.g., nightly). Scans `skill_co_occurrences` where `co_occurrence_count >= CO_OCCURRENCE_EDGE_THRESHOLD` (see `src/main/java/com/skillsgraph/com.sk.skillsgraph.config/AppConstants.java`). For each qualifying pair: if `related_to` edge exists → update `weight = min(1.0, weight + count/CO_OCCURRENCE_NORMALIZATION_FACTOR)`. If no edge exists → create new `related_to` edge with `provenance = 'empirical'`. Record in changelog | `src/main/java/com/skillsgraph/com.sk.skillsgraph.script/CoOccurrenceProcessor.java` |

### Checklist

- [ ] Extraction pipeline publishes skill IDs to `co-occurrence:pairs` stream after extraction
- [ ] Worker consumes events and upserts into `skill_co_occurrences` table
- [ ] Co-occurrence count increments correctly for repeated pairs
- [ ] `source_type_counts` tracks breakdown by cv/jd/course
- [ ] `./mvnw spring-boot:run -Dspring-boot.run.arguments=--co-occurrence-process` creates new `empirical` edges for pairs above threshold
- [ ] `./mvnw spring-boot:run -Dspring-boot.run.arguments=--co-occurrence-process` strengthens existing edge weights
- [ ] Edge weight never exceeds 1.0
- [ ] Changelog records edge creation/strengthening from co-occurrence

---

## 7.6 Re-analysis Worker

### Context

When a skill transitions from `candidate` to `active` (curator approves), previously processed documents that mentioned this skill (as `discovered_candidates`) should be re-extracted. This ensures candidate profiles are updated with the newly recognized skill. See ARCHITECTURE.md §5.5.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 7.6.1 | Extraction log table | New migration `V3__extraction_logs.sql`: `CREATE TABLE extraction_logs (id UUID PK DEFAULT gen_random_uuid(), document_hash TEXT NOT NULL, source_type TEXT, input_text_preview TEXT, discovered_candidates JSONB DEFAULT '[]', extracted_skill_ids UUID[], created_at TIMESTAMPTZ DEFAULT now())`. Index on `discovered_candidates` using GIN for JSONB containment queries | `src/main/java/com/skillsgraph/migrations/V3__extraction_logs.sql` |
| 7.6.2 | Log extraction results | In `SkillExtractionPipeline`, after extraction: insert a row into `extraction_logs` with `document_hash` (SHA-256 of input text), `source_type`, first 500 chars of text as `input_text_preview`, `discovered_candidates` array, and `extracted_skill_ids`. Only log if `discovered_candidates` is non-empty (to limit table size) | `src/main/java/com/skillsgraph/service/extraction/SkillExtractionPipeline.java` |
| 7.6.3 | Activation event publishing | In `ReviewQueueService.decide()` approve flow: after skill is created and activated, publish `{ skill_id, skill_name, normalized_name, activated_at }` to `reanalysis:jobs` stream | `src/main/java/com/skillsgraph/service/discovery/ReviewQueueService.java` |
| 7.6.4 | Re-analysis worker | Extends `StreamConsumer`. Consumes from `reanalysis:jobs`. For each activation event: (1) Query `extraction_logs` for rows where `discovered_candidates` contains the activated skill's `normalized_name` (JSONB containment: `discovered_candidates @> '[{"normalized_form": "..."}]'`). (2) For each matching log, queue the original document for re-extraction via `extraction:jobs` stream with a `reanalysis: true` flag. (3) Limit to most recent `REANALYSIS_MAX_DOCUMENTS` documents (default 1000, see `src/main/java/com/skillsgraph/com.sk.skillsgraph.config/AppConstants.java`) to prevent runaway processing | `src/main/java/com/skillsgraph/worker/ReanalysisWorker.java` |

### Checklist

- [ ] Migration creates `extraction_logs` table with GIN index on `discovered_candidates`
- [ ] Extraction pipeline logs results when `discovered_candidates` is non-empty
- [ ] Curator approve → activation event published to `reanalysis:jobs` stream
- [ ] Re-analysis worker queries `extraction_logs` for matching documents
- [ ] Matching documents are queued for re-extraction
- [ ] Re-extraction picks up the newly activated skill from the taxonomy
- [ ] `REANALYSIS_MAX_DOCUMENTS` limits prevent runaway processing (see `src/main/java/com/skillsgraph/com.sk.skillsgraph.config/AppConstants.java`)
- [ ] Re-analysis doesn't block the approve API response (async via stream)

---

## 7.7 Cache Invalidation

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 7.5.1 | Invalidation on mutations | In the PG NOTIFY listener: when a skill is updated/deprecated/merged, delete `taxonomy:skill:{id}` from Redis cache. For merges, also invalidate the survivor's cache | `src/main/java/com/skillsgraph/service/changelog/PgNotifyListener.java` |
| 7.5.2 | Extraction cache consideration | Extraction cache entries (`extract:{hash}`) reference skill IDs. When a skill is modified, these cache entries become stale. Strategy: set short-enough TTL (`EXTRACTION_CACHE_TTL_SECONDS` — see `src/main/java/com/skillsgraph/com.sk.skillsgraph.config/AppConstants.java`) and accept eventual consistency. Don't try to invalidate extraction cache (too many entries, unclear which reference which skills) | Documentation |

### Checklist

- [ ] Skill update → `taxonomy:skill:{id}` deleted from Redis
- [ ] Skill deprecate → `taxonomy:skill:{id}` deleted from Redis
- [ ] Skill merge → both source and survivor cache keys deleted
- [ ] Cache miss after invalidation → fresh data fetched from DB

---

## 7.8 Worker Entry Point & Management

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 7.8.1 | Worker entry point | `src/main/java/com/skillsgraph/worker/WorkerConfiguration.java` — starts all workers: extraction, discovery, full-text-search-sync, co-occurrence, reanalysis. Each worker runs as a Spring-managed `@Bean` with `StreamMessageListenerContainer` or `@Async` thread pool. Script: `./mvnw spring-boot:run -Dspring-boot.run.arguments=--workers` | `src/main/java/com/skillsgraph/worker/WorkerConfiguration.java` |
| 7.8.2 | Graceful shutdown | On SIGTERM (Spring's graceful shutdown): stop consuming new messages, wait for in-flight messages to complete (timeout: `GRACEFUL_SHUTDOWN_TIMEOUT_MS` — see `src/main/java/com/skillsgraph/com.sk.skillsgraph.config/AppConstants.java`), close DB and Redis connections, exit | `src/main/java/com/skillsgraph/worker/WorkerConfiguration.java` |
| 7.8.3 | Worker health logging | Each worker logs startup, message processing (debug level), errors, and shutdown. Log format: `{ worker, event, message_id, duration_ms }` | `src/main/java/com/skillsgraph/worker/WorkerConfiguration.java` |
| 7.8.4 | Spring profiles for selective workers | Use Spring profiles (`-Dspring.profiles.active=worker-extraction`) to enable only specific worker beans. The default profile enables all workers. Workers can also be deployed in separate JVM instances | `src/main/resources/application.yml` |

### Checklist

- [ ] `./mvnw spring-boot:run -Dspring-boot.run.arguments=--workers` starts all 5 workers
- [ ] Workers log startup: `"Extraction worker started, consuming from extraction:jobs"`
- [ ] Workers process messages and log results
- [ ] `Ctrl+C` / SIGTERM (Spring's graceful shutdown) triggers graceful shutdown
- [ ] Workers wait for in-flight messages before exiting
- [ ] Individual worker scripts work (`./mvnw spring-boot:run -Dspring-boot.run.arguments=--worker-extraction`)

---

## Phase 7 Completion Verification

```bash
# Set up streams
./mvnw spring-boot:run -Dspring-boot.run.arguments=--streams-setup

# Start workers in background
./mvnw spring-boot:run -Dspring-boot.run.arguments=--workers &
WORKER_PID=$!

# Submit batch extraction
RESULT=$(curl -s -X POST http://localhost:8080/api/extract/batch \
  -H "Content-Type: application/json" \
  -d '{
    "documents": [
      {"id": "doc1", "text": "Python developer with Django and React experience"},
      {"id": "doc2", "text": "Machine Learning engineer skilled in TensorFlow and PyTorch"},
      {"id": "doc3", "text": "DevOps specialist with Kubernetes and Terraform expertise"}
    ]
  }')
JOB_ID=$(echo $RESULT | jq -r '.job_id')
echo "Batch job: $JOB_ID"

# Poll for results (should complete within 30s)
sleep 10
curl "http://localhost:8080/api/extract/jobs/$JOB_ID" | jq '.status, .completed, .total'
# → "completed", 3, 3

# Get full results
curl "http://localhost:8080/api/extract/jobs/$JOB_ID" | jq '.results.doc1.skills[:2]'

# Test full-text search service sync: update a skill and verify search is updated
curl -X PATCH http://localhost:8080/api/skills/<uuid> \
  -d '{"description": "Updated for sync test"}'
sleep 2
curl "http://localhost:8080/api/skills/search?q=<skill-name>" | jq '.[0].description'
# → "Updated for sync test"

# Test discovery signal aggregation
for i in {1..5}; do
  curl -X POST http://localhost:8080/api/extract \
    -d '{"text": "Expert in CrewAI multi-agent framework"}'
done
sleep 5
curl http://localhost:8080/api/review-queue | jq '.[].candidate_name'
# → should include "CrewAI"

# Graceful shutdown
kill $WORKER_PID
wait $WORKER_PID

./mvnw test -Dtest="*WorkerTest"
echo "Phase 7 complete ✓"
```

---

## Phase 7 Master Checklist

### 7.1 Redis Streams Infrastructure
- [ ] `EventProducer.publishEvent()` using Spring Data Redis `StreamOperations`
- [ ] Abstract `StreamConsumer` base with XREADGROUP, XACK, retry, dead-letter
- [ ] `StreamsSetupRunner` creates all consumer groups on startup
- [ ] `StreamNames` constants defined

### 7.2 Batch Extraction
- [ ] `POST /api/extract/batch` queues documents and returns job_id
- [ ] `GET /api/extract/jobs/:jobId` shows progress and results
- [ ] Extraction worker processes jobs concurrently (max `EXTRACTION_WORKER_CONCURRENCY` — see `src/main/java/com/skillsgraph/com.sk.skillsgraph.config/AppConstants.java`)
- [ ] Batch results expire after `BATCH_RESULT_TTL_SECONDS` (see `src/main/java/com/skillsgraph/com.sk.skillsgraph.config/AppConstants.java`)

### 7.3 Discovery Signal Worker
- [ ] Extraction pipeline publishes discovered_candidates
- [ ] Worker aggregates signals with Redis counters
- [ ] Threshold triggers → review queue entry
- [ ] Signal counters expire after `DISCOVERY_SIGNAL_TTL_SECONDS` (see `src/main/java/com/skillsgraph/com.sk.skillsgraph.config/AppConstants.java`)

### 7.4 full-text search service Sync Worker
- [ ] PG NOTIFY → Redis Stream bridge
- [ ] Worker debounces and batch-upserts to full-text search service
- [ ] full-text search service stays in sync within 1-2 seconds

### 7.5 Co-occurrence Aggregation
- [ ] Extraction pipeline publishes skill pairs to `co-occurrence:pairs` stream
- [ ] Worker consumes events and upserts into `skill_co_occurrences` table
- [ ] `./mvnw spring-boot:run -Dspring-boot.run.arguments=--co-occurrence-process` creates/strengthens empirical edges above threshold
- [ ] Edge weight never exceeds 1.0
- [ ] Changelog records edge creation/strengthening

### 7.6 Re-analysis Worker
- [ ] Migration creates `extraction_logs` table with GIN index
- [ ] Extraction pipeline logs results when `discovered_candidates` is non-empty
- [ ] Curator approve → activation event published to `reanalysis:jobs` stream
- [ ] Re-analysis worker queries logs and queues matching documents for re-extraction
- [ ] `REANALYSIS_MAX_DOCUMENTS` limits prevent runaway processing (see `src/main/java/com/skillsgraph/com.sk.skillsgraph.config/AppConstants.java`)

### 7.7 Cache Invalidation
- [ ] Skill mutations invalidate taxonomy cache
- [ ] Merge invalidates both source and survivor caches

### 7.8 Worker Management
- [ ] `WorkerConfiguration` registers all workers as Spring beans
- [ ] Workers start automatically; Spring graceful shutdown handles SIGTERM
- [ ] Health logging for all workers via SLF4J / Logback

---

## Future Optimization: Provider Batch APIs

> **Status:** Deferred — implement when batch volume exceeds 10,000 documents/day or cost optimization becomes a priority.

ARCHITECTURE.md §4.5 describes an **Offline (batch)** processing mode using Anthropic and OpenAI Batch APIs, which offer a **50% cost reduction** with a 24-hour SLA. This phase implements batch processing via Redis Streams (near-real-time), which is sufficient for initial deployment.

When ready to optimize, add a `BatchAPIService` that:
1. Collects extraction requests into JSONL files matching the Anthropic/OpenAI batch format.
2. Submits via `POST /v1/messages/batches` (Anthropic) or the OpenAI Batch API.
3. Polls for completion (24h SLA).
4. Parses results and stores them in the same batch state format as the Redis Streams worker.

This is a pure cost optimization — functionality is identical to the current Redis Streams approach.
