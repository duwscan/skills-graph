# Phase 5: Taxonomy Management (Discovery + HITL Curation)

> **Timeline:** Week 10-11
> **Dependencies:** Phase 2 (CRUD API), Phase 3 (Embeddings & Search), Phase 4 (Extraction Pipeline)
> **Unlocks:** Phase 7 (Workers & Events)

---

## Goal

Implement the skill discovery pipeline (LLM-based NER + embedding deduplication) and the curator review queue. This closes the loop: the extraction pipeline discovers unknown skills → discovery pipeline evaluates them → curators approve/reject → taxonomy grows. Approved skills trigger **re-analysis** of previously processed documents (Phase 7).

---

## 5.1 Skill Discovery Service

### Context

The discovery pipeline has 3 stages: (1) collect signals from extraction output, (2) use LLM zero-shot NER to extract candidate skills, (3) deduplicate against existing taxonomy using embeddings. Candidates that pass deduplication are added to the curator review queue.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 5.1.1 | `DiscoveryService` class | Orchestrates the 3-stage pipeline. Constructor: `EmbeddingService`, `VectorSearchService`, `ReviewQueueService`, AI SDK model config | `src/services/discovery/discovery.ts` |
| 5.1.2 | Discovery Zod schema | Output schema for LLM NER: `candidates[]` with `surface_form`, `normalized_form`, `category_guess` (enum), `is_likely_new` (boolean), `reason` (string). Use `.describe()` on all fields | `src/schemas/discovery.ts` |
| 5.1.3 | `extractCandidates(text)` | Stage 2: Call LLM with zero-shot NER prompt. Use `generateText` + `Output.object(discoverySchema)`. Model: Claude Haiku (high volume, lower complexity). Prompt instructs LLM to find skills NOT in a standard taxonomy | `src/services/discovery/discovery.ts` |
| 5.1.4 | `deduplicateCandidate(candidate)` | Stage 3: Embed candidate's `normalized_form` via `EmbeddingService`. Run `VectorSearchService.checkDuplicate()`. Classify: ≥`SIMILARITY_DUPLICATE_THRESHOLD` similarity → `alias` (auto-add as alias), [`SIMILARITY_REVIEW_THRESHOLD`, `SIMILARITY_DUPLICATE_THRESHOLD`) → `review` (add to queue with similar skills noted), <`SIMILARITY_REVIEW_THRESHOLD` → `new` (add to queue as genuinely new). Thresholds from `src/config/constants.ts` | `src/services/discovery/discovery.ts` |
| 5.1.5 | `processCandidates(candidates[])` | Process each candidate through deduplication. For `alias` type: optionally auto-create alias on matching skill. For `review` and `new`: add to review queue with similarity matches and suggested parents | `src/services/discovery/discovery.ts` |
| 5.1.6 | `discoverFromText(text, source)` | Full pipeline: `extractCandidates(text)` → `processCandidates(results)`. Accept `source` metadata (e.g., "job_posting", "resume", "course") for tracking | `src/services/discovery/discovery.ts` |
| 5.1.7 | Feed from extraction | When `SkillExtractionPipeline.extract()` returns `discovered_candidates`, feed them into `processCandidates()` asynchronously (don't block extraction response). Each candidate is published as a signal to the `discovery:signals` Redis Stream. The discovery worker (Phase 7) aggregates signals and triggers the full discovery pipeline when a candidate reaches `DISCOVERY_SIGNAL_THRESHOLD` (see `src/config/constants.ts`, configurable via `DISCOVERY_SIGNAL_THRESHOLD` env var) | `src/services/extraction/pipeline.ts` |

### Discovery Prompt

```
You are analyzing text to discover potential professional skills, technologies,
tools, methodologies, and competencies that might NOT already exist in a
standard skills taxonomy.

Focus on:
- Emerging technologies and frameworks
- Niche domain skills
- New methodologies or practices
- Tools that have gained popularity recently

Do NOT include:
- Common, well-known skills (Python, JavaScript, SQL, etc.)
- Generic terms (communication, teamwork, etc.)
- Company names or product brands (unless they ARE the skill)

Text: {chunk}
```

### Checklist

- [ ] `extractCandidates()` calls LLM and returns structured candidate list
- [ ] `extractCandidates("Expert in LangGraph and CrewAI")` returns LangGraph and CrewAI as candidates
- [ ] `deduplicateCandidate()` correctly classifies: existing skill name → `alias`, similar → `review`, novel → `new`
- [ ] `alias` candidates: auto-creates alias on matching skill (when confidence ≥ `SIMILARITY_DUPLICATE_THRESHOLD` — see `src/config/constants.ts`)
- [ ] `review` candidates: adds to queue with `similar_existing` populated
- [ ] `new` candidates: adds to queue with `suggested_parents` populated
- [ ] `discoverFromText()` runs full pipeline end-to-end
- [ ] Extraction pipeline feeds `discovered_candidates` into discovery asynchronously
- [ ] Discovery doesn't block or slow down extraction API response
- [ ] Discovery is idempotent: same candidate text doesn't create duplicate queue entries

---

## 5.2 Review Queue

### Context

The review queue is a staging area where discovered skill candidates await curator decisions. Each entry includes the candidate name, LLM classification, embedding similarity matches, and suggested taxonomy placements.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 5.2.1 | Review queue migration | New migration `002_review_queue.sql`: `CREATE TABLE review_queue (id UUID PK DEFAULT gen_random_uuid(), candidate_name TEXT NOT NULL, normalized_name TEXT NOT NULL, category_guess TEXT, signals_count INT DEFAULT 1, llm_score FLOAT, similar_existing JSONB DEFAULT '[]', suggested_parents JSONB DEFAULT '[]', status TEXT DEFAULT 'pending' CHECK(status IN ('pending','approved','rejected','merged','deferred')), curator_id TEXT, decision TEXT, decision_notes TEXT, created_at TIMESTAMPTZ DEFAULT now(), decided_at TIMESTAMPTZ, source TEXT, UNIQUE(normalized_name, status) -- prevent duplicate pending entries)` | `src/db/migrations/002_review_queue.sql` |
| 5.2.2 | `ReviewQueueService.add(candidate)` | Insert or update queue entry. If `normalized_name` already exists with `pending` status, increment `signals_count` instead of creating duplicate. Store `similar_existing` (skills with similarity [`SIMILARITY_REVIEW_THRESHOLD`, `SIMILARITY_DUPLICATE_THRESHOLD`) — see `src/config/constants.ts`) and `suggested_parents` (nearest parent-category skills) | `src/services/discovery/review-queue.ts` |
| 5.2.3 | `ReviewQueueService.list(filters?)` | Paginated listing of pending candidates, sorted by `signals_count * llm_score DESC` (highest value candidates first). Filter by `status`, `category_guess`. Include `total` count | `src/services/discovery/review-queue.ts` |
| 5.2.4 | `ReviewQueueService.getById(id)` | Full details for a single candidate: all fields plus expanded `similar_existing` (with full skill objects) and `suggested_parents` (with full skill objects) | `src/services/discovery/review-queue.ts` |
| 5.2.5 | `ReviewQueueService.decide(id, decision)` | Process curator decision. Update queue entry status + decision fields + `decided_at`. Then execute the decision flow (§5.2.6-5.2.8) | `src/services/discovery/review-queue.ts` |
| 5.2.6 | Approve flow | On `approve`: (1) Create skill via `SkillService.create()` with provided name, description, category, path. (2) Generate embedding via `EmbeddingService`. (3) Create `parent_of` edges from suggested parents to new skill with `provenance = 'llm_predicted'` and `weight = 0.5` (LLM priori — will be adjusted by co-occurrence data over time). (4) Add any extra aliases. (5) Run all quality guardrails. (6) Record in changelog. (7) **Publish activation event** to `reanalysis:jobs` Redis Stream for re-analysis of previously processed documents. All in a transaction — rollback on any failure | `src/services/discovery/review-queue.ts` |
| 5.2.7 | Reject flow | On `reject`: Mark queue entry as `rejected` with `decision_notes`. No taxonomy changes | `src/services/discovery/review-queue.ts` |
| 5.2.8 | Merge flow | On `merge`: (1) Create alias on the specified target skill via `AliasService.create()` with the candidate name as `surface_form`. (2) Generate alias embedding. (3) Mark queue entry as `merged`. (4) Record in changelog | `src/services/discovery/review-queue.ts` |
| 5.2.9 | Defer flow | On `defer`: Mark as `deferred` with notes. Can be re-listed later with `?status=deferred` filter | `src/services/discovery/review-queue.ts` |

### Checklist

- [ ] Migration creates `review_queue` table
- [ ] `add()` inserts new candidate into queue
- [ ] `add()` increments `signals_count` for duplicate pending candidates (same normalized_name)
- [ ] `list()` returns candidates sorted by priority (signals * score)
- [ ] `list()` supports pagination and status filter
- [ ] `getById()` returns full candidate with expanded similar/parent skill objects
- [ ] `decide(id, "approve")` creates skill + edges + aliases in transaction
- [ ] `decide(id, "approve")` runs quality guardrails (cycle check, duplicate check)
- [ ] `decide(id, "approve")` rolls back on guardrail failure
- [ ] `decide(id, "approve")` creates edges with `provenance = 'llm_predicted'` and `weight = 0.5`
- [ ] `decide(id, "approve")` publishes activation event to `reanalysis:jobs` stream
- [ ] `decide(id, "reject")` marks as rejected, no taxonomy changes
- [ ] `decide(id, "merge", { target_id })` creates alias on target skill
- [ ] `decide(id, "defer")` marks as deferred with notes
- [ ] Approved skill appears in search and has embedding

---

## 5.3 LLM-Based Relationship Prediction

### Context

When a new skill is being reviewed, the system suggests relationships using two approaches: embedding similarity (fast, for `related_to`) and LLM classification (accurate, for `parent_of`/`child_of`).

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 5.3.1 | `RelationshipPredictionService` | Combines embedding similarity and LLM classification to suggest relationships for new or existing skills | `src/services/discovery/relationship-prediction.ts` |
| 5.3.2 | Relationship schema | Zod schema for LLM output: `reasoning` (string, generated first for CoT), `classification` (enum: PARENT_CHILD, CHILD_PARENT, RELATED, PREREQUISITE, NONE), `confidence` (number 0-1) | `src/schemas/relationship.ts` |
| 5.3.3 | `predictByEmbedding(skillA, skillB)` | Compute cosine similarity between embeddings. Return: ≥`SIMILARITY_RELATED_HIGH_THRESHOLD` → `RELATED` (high confidence), [`SIMILARITY_RELATED_LOW_THRESHOLD`, `SIMILARITY_RELATED_HIGH_THRESHOLD`) → `RELATED` (medium), <`SIMILARITY_RELATED_LOW_THRESHOLD` → `NONE`. Thresholds from `src/config/constants.ts`. No LLM call needed | `src/services/discovery/relationship-prediction.ts` |
| 5.3.4 | `predictByLLM(skillA, skillB)` | Use `generateText` + `Output.object(relationshipSchema)` with chain-of-thought prompt. Include skill names, descriptions, and parent breadcrumbs as context. Model: Claude Sonnet | `src/services/discovery/relationship-prediction.ts` |
| 5.3.5 | `classifyBatch(pairs[])` | Classify `RELATIONSHIP_BATCH_SIZE` skill pairs in a single LLM call using array schema (see `src/config/constants.ts`). Returns array of classifications. Reduces cost by ~10x vs one-pair-per-call | `src/services/discovery/relationship-prediction.ts` |
| 5.3.6 | `suggestRelationships(skill)` | Find 5 nearest existing skills by embedding → classify each pair with LLM → return suggested edges sorted by confidence. Used to populate `suggested_parents` in review queue and during curator review | `src/services/discovery/relationship-prediction.ts` |

### Chain-of-Thought Prompt

```
Classify the relationship between these two skills.

Skill A: {name_a} — {description_a}
  Parent chain: {breadcrumb_a}

Skill B: {name_b} — {description_b}
  Parent chain: {breadcrumb_b}

Think step by step about the relationship, then classify.
```

### Checklist

- [ ] `predictByEmbedding()` returns correct relationship type based on similarity thresholds
- [ ] `predictByLLM("Python", "Django")` returns `PARENT_CHILD` (Python is parent)
- [ ] `predictByLLM("React", "Angular")` returns `RELATED`
- [ ] `predictByLLM("Python", "Cooking")` returns `NONE`
- [ ] Chain-of-thought: `reasoning` field is populated before `classification`
- [ ] `classifyBatch()` processes 10+ pairs in a single LLM call
- [ ] `classifyBatch()` returns correct number of results matching input pairs
- [ ] `suggestRelationships(newSkill)` returns 3-5 suggested edges with confidence
- [ ] Suggested relationships include both `parent_of` and `related_to` types

---

## 5.4 Review Queue API Routes

### Tasks

| # | Route | Method | Detail | Files |
|---|---|---|---|---|
| 5.4.1 | `/api/review-queue` | GET | List pending candidates. Query params: `status`, `category`, `limit`, `offset`, `sort_by` (default: priority). Returns paginated list | `src/routes/review-queue.ts` |
| 5.4.2 | `/api/review-queue/:id` | GET | Get single candidate with full details including expanded similar_existing and suggested_parents | `src/routes/review-queue.ts` |
| 5.4.3 | `/api/review-queue/:id/decision` | POST | Submit decision. Body: `{ decision: "approve"|"reject"|"merge"|"defer", merge_target_id?, parent_ids?, aliases?, description?, category?, path?, notes? }`. Validate with Zod schema | `src/routes/review-queue.ts` |
| 5.4.4 | `/api/discover` | POST | Manually trigger discovery. Body: `{ text: string, source: string }`. Runs `DiscoveryService.discoverFromText()`. Returns `{ candidates_found, added_to_queue, auto_aliased }` | `src/routes/discovery.ts` |

### Checklist

- [ ] `GET /api/review-queue` returns paginated pending candidates
- [ ] `GET /api/review-queue?status=deferred` filters to deferred candidates
- [ ] `GET /api/review-queue/:id` returns full candidate details
- [ ] `POST /api/review-queue/:id/decision` with `approve` creates the skill
- [ ] `POST /api/review-queue/:id/decision` with `reject` marks as rejected
- [ ] `POST /api/review-queue/:id/decision` with `merge` creates alias on target
- [ ] `POST /api/review-queue/:id/decision` with `defer` marks as deferred
- [ ] `POST /api/review-queue/:id/decision` returns 404 for non-existent id
- [ ] `POST /api/review-queue/:id/decision` returns 409 for already-decided candidates
- [ ] `POST /api/discover` extracts candidates from text and returns summary

---

## Phase 5 Completion Verification

```bash
# Trigger discovery on text with unknown skills
curl -X POST http://localhost:3000/api/discover \
  -H "Content-Type: application/json" \
  -d '{"text":"Looking for an expert in LangGraph, CrewAI, and agentic AI systems with experience in prompt engineering","source":"job_posting"}' | jq

# Check review queue
curl http://localhost:3000/api/review-queue | jq
# → candidates like LangGraph, CrewAI ranked by priority

# Get candidate details
curl http://localhost:3000/api/review-queue/<id> | jq
# → includes similar_existing, suggested_parents

# Approve a candidate
curl -X POST http://localhost:3000/api/review-queue/<id>/decision \
  -H "Content-Type: application/json" \
  -d '{"decision":"approve","parent_ids":["<ai-uuid>"],"description":"Graph-based agent orchestration framework","category":"tool"}' | jq

# Verify skill was created
curl http://localhost:3000/api/skills/search?q=LangGraph | jq
# → newly created skill appears in search

# Merge a candidate (alias into existing)
curl -X POST http://localhost:3000/api/review-queue/<id>/decision \
  -d '{"decision":"merge","merge_target_id":"<prompt-engineering-uuid>"}'

# Verify extraction feeds discovery
curl -X POST http://localhost:3000/api/extract \
  -d '{"text":"We use Cursor IDE and v0 for AI-assisted development"}'
sleep 2
curl http://localhost:3000/api/review-queue | jq
# → "Cursor IDE" and "v0" should appear as candidates

bun test src/services/discovery/
echo "Phase 5 complete ✓"
```

---

## Phase 5 Master Checklist

### 5.1 Skill Discovery Service
- [ ] 3-stage pipeline: signal collection → LLM NER → embedding dedup
- [ ] `extractCandidates()` uses LLM zero-shot NER
- [ ] `deduplicateCandidate()` classifies as alias/review/new
- [ ] Auto-alias for very high similarity (≥`SIMILARITY_DUPLICATE_THRESHOLD` — see `src/config/constants.ts`) candidates
- [ ] Extraction pipeline feeds discovered_candidates into discovery
- [ ] Idempotent: no duplicate queue entries

### 5.2 Review Queue
- [ ] `review_queue` table created via migration
- [ ] `add()` with signal counting for duplicates
- [ ] `list()` sorted by priority with pagination
- [ ] `decide()` handles approve/reject/merge/defer
- [ ] Approve creates skill + edges + aliases in transaction
- [ ] Merge creates alias on target skill
- [ ] Quality guardrails run during approve

### 5.3 Relationship Prediction
- [ ] Embedding similarity for `related_to` detection
- [ ] LLM chain-of-thought for `parent_of`/`child_of`
- [ ] Batch classification (`RELATIONSHIP_BATCH_SIZE` pairs per call — see `src/config/constants.ts`)
- [ ] `suggestRelationships()` for new skill placement

### 5.4 API Routes
- [ ] `GET /api/review-queue` with pagination and filters
- [ ] `GET /api/review-queue/:id` with full details
- [ ] `POST /api/review-queue/:id/decision` for all 4 decision types
- [ ] `POST /api/discover` for manual discovery trigger
