# Phase 3: Embedding & Search Infrastructure (Laravel Backend)

> **Timeline:** Week 5-6  
> **Dependencies:** Phase 1 (Foundation), Phase 2 (CRUD API) recommended  
> **Unlocks:** Phase 4 (Extraction Pipeline), Phase 5 (Discovery & HITL)  
> **Context:** Embeddings power RAG retrieval, duplicate detection, co-occurrence-based relationship discovery, and semantic search  
> **Note:** This document describes the **Laravel 12 backend implementation**. The original TS/Bun file paths remain in earlier phases for historical context only.

---

## Goal

Add vector embedding generation for all skills/aliases, pgvector similarity search, and Typesense full-text search in the **Laravel** backend. This phase enables the **RAG retrieval step** needed by the extraction pipeline (Phase 4), the **duplicate detection** needed by the discovery pipeline (Phase 5), and the **embedding-based deduplication** used during co-occurrence edge strengthening (Phase 7).

---

## 3.1 Embedding Service (Laravel AI)

### Context

Embeddings are generated via the **Laravel AI SDK** (`laravel/ai`) using the `Embeddings` API and the model configured in `config/ai.php`:

- Provider: `ai.default_for_embeddings` (e.g. `ollama_openai`, `openai`, etc.).
- Model: `ai.embedding_model` (e.g. `mxbai-embed-large`).
- Dimensions: `ai.embedding_dimensions` (defaults to `1024`).

Caching is handled through Laravel AI’s built-in embedding caching:

- Global toggle: `ai.caching.embeddings.cache` (wired to `EMBEDDING_CACHE` env).
- Store: `ai.caching.embeddings.store` (defaults to `CACHE_STORE`).

All embedding operations are centralized in `App\Ai\EmbeddingService`.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 3.1.1 | `EmbeddingService` class | Wrap the Laravel AI `Embeddings` API, reading provider/model/dimensions from `config/ai.php`. Provide `embed()` and `embedMany()` with optional caching | `app/Ai/EmbeddingService.php`, `config/ai.php` |
| 3.1.2 | Embedding caching | Enable caching via `ai.caching.embeddings.cache` / `EMBEDDING_CACHE` and per-call `->cache()` options. Rely on the AI SDK for cache keys and TTLs | `app/Ai/EmbeddingService.php`, `config/ai.php` |
| 3.1.3 | Skill embedding backfill | Use the `skills:embed` Artisan command to generate embeddings for all skills and aliases with `NULL` embeddings; support chunking and `--force` re-embedding | `app/Console/Commands/EmbedSkillsAndAliases.php` |
| 3.1.4 | Skill & alias columns | Store embeddings in PostgreSQL `vector(1024)` columns on `skills.embedding` and `skill_aliases.alias_embedding` | `database/migrations/2026_03_03_071015_create_skills_table.php`, `database/migrations/2026_03_03_071016_create_skill_aliases_table.php`, `app/Models/Skill.php`, `app/Models/SkillAlias.php` |

### Checklist

- [ ] `EmbeddingService` uses `config/ai.php` provider/model and `embedding_dimensions`.
- [ ] Embedding caching can be toggled via `EMBEDDING_CACHE` and per-call `->cache()`.
- [ ] `php artisan skills:embed` processes all skills/aliases with `NULL` embeddings.
- [ ] `skills:embed` is idempotent by default (skips already-embedded entries).
- [ ] `skills:embed --force` re-embeds all records and reports progress.
- [ ] Skills and aliases have 1024-dimension embeddings persisted in PostgreSQL.

---

## 3.2 Vector Search (pgvector)

### Context

pgvector enables approximate nearest neighbor (ANN) search using HNSW indexes. The Laravel `VectorSearchService` wraps these queries for RAG retrieval, duplicate detection, and semantic search.

- `skills.embedding` and `skill_aliases.alias_embedding` are `vector(1024)` with HNSW indexes.
- All similarity queries use cosine distance `<=>`.
- Tunable thresholds and RAG limits live in `config/skills_graph.php`.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 3.2.1 | `VectorSearchService` class | Service that encapsulates pgvector queries and uses cosine distance for similarity; relies on Eloquent models | `app/Ai/VectorSearchService.php` |
| 3.2.2 | `findSimilarSkills(embedding, k, filters?)` | Return top‑K nearest active skills. SQL: `SELECT *, 1 - (embedding <=> ?::vector) AS similarity FROM skills WHERE status = 'active' AND embedding IS NOT NULL [AND filters] ORDER BY embedding <=> ?::vector LIMIT k`. Optional filters: `category`, `exclude_ids` | `app/Ai/VectorSearchService.php` |
| 3.2.3 | `findSimilarAliases(embedding, k)` | Same pattern but against `skill_aliases.alias_embedding`, joining back to `skills` and returning alias + parent skill info | `app/Ai/VectorSearchService.php` |
| 3.2.4 | `findCandidatesForChunk(chunkEmbedding, k=rag_candidate_limit)` | RAG retrieval function: given a chunk embedding, return top `rag_candidate_limit` skills from config with `{ id, external_id, canonical_name, description, similarity }` | `app/Ai/VectorSearchService.php`, `config/skills_graph.php` |
| 3.2.5 | `checkDuplicate(name, description?)` | Embed candidate text via `EmbeddingService->embed()`, search for nearest neighbors, and return `{ isDuplicate: bool, matches: [{ skill, similarity }] }`. Use thresholds from `config/skills_graph.php`: `similarity_duplicate_threshold`, `similarity_review_threshold` | `app/Ai/VectorSearchService.php` |
| 3.2.6 | `cosineSimilarity(a, b)` | Pure PHP cosine similarity between two embeddings (for in-memory comparisons) | `app/Ai/VectorSearchService.php` |

### Checklist

- [ ] `findSimilarSkills()` returns active skills ordered by similarity (highest first).
- [ ] `findSimilarSkills()` respects `k` and filter options (`category`, `exclude_ids`).
- [ ] `findSimilarAliases()` returns aliases with parent skill info and similarity.
- [ ] `findCandidatesForChunk()` returns top `rag_candidate_limit` candidates with required fields.
- [ ] `checkDuplicate("Machine Learning")` returns `isDuplicate: true` when that skill exists.
- [ ] `checkDuplicate("Quantum Computing")` returns `isDuplicate: false` when unique.
- [ ] `cosineSimilarity([1,0], [1,0])` returns `1.0`, `cosineSimilarity([1,0], [0,1])` returns `0.0`.
- [ ] PHPUnit tests in `tests/Unit/Ai/VectorSearchServiceTest.php` cover ordering, thresholds, and cosine similarity.

---

## 3.3 Typesense Integration (Laravel)

### Context

Typesense provides fast full‑text search with autocomplete, typo tolerance, and faceting. It backs the skills search API where users type partial or fuzzy queries and expect relevant skills like “Machine Learning” to appear.

The Laravel implementation uses:

- PHP client: `typesense/typesense-php`.
- Config: `config/typesense.php`.
- Service: `App\Ai\TypesenseSearchService`.
- Artisan commands: `search:setup`, `search:reindex`.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 3.3.1 | Typesense config | Configure host, port, protocol, API key, and collection name via env (`TYPESENSE_HOST`, `TYPESENSE_PORT`, `TYPESENSE_PROTOCOL`, `TYPESENSE_API_KEY`) | `config/typesense.php` |
| 3.3.2 | Typesense client & schema | Initialize Typesense client and define the `skills` collection: `{ id (string, PK), external_id, canonical_name (string, facet), slug, description, category (string, facet), status (string, facet), aliases (string[], facet) }` | `app/Ai/TypesenseSearchService.php` |
| 3.3.3 | `setupCollection()` | Idempotently drop and recreate the `skills` collection with the schema above; exposed via `php artisan search:setup` | `app/Ai/TypesenseSearchService.php`, `app/Console/Commands/TypesenseSetup.php` |
| 3.3.4 | `indexSkill(Skill $skill)` | Upsert a single skill document into Typesense, including aliases from the `Skill::aliases` relationship | `app/Ai/TypesenseSearchService.php` |
| 3.3.5 | `removeSkill(skillId)` | Remove a skill document from Typesense when deprecated/merged/deleted | `app/Ai/TypesenseSearchService.php` |
| 3.3.6 | `reindexAll()` | Drop collection, recreate, and bulk index all active skills (with aliases), chunked by 100; exposed via `php artisan search:reindex` | `app/Ai/TypesenseSearchService.php`, `app/Console/Commands/TypesenseReindex.php` |
| 3.3.7 | `search(query, filters?, limit?)` | Search Typesense with `query_by="canonical_name,aliases,description"`, `filter_by` for `category`/`status`, `per_page` for limit. Return `{ results: [{ skill_id, score, highlights }], total, search_time_ms }` | `app/Ai/TypesenseSearchService.php` |
| 3.3.8 | Commands testing | Feature tests ensure `search:setup` and `search:reindex` invoke the Typesense service correctly | `tests/Feature/Console/TypesenseCommandsTest.php` |

### Checklist

- [ ] Typesense client connects using `config/typesense.php` values.
- [ ] `php artisan search:setup` creates (or recreates) the `skills` collection.
- [ ] `php artisan search:reindex` indexes all active skills with aliases.
- [ ] `search("machine learning")` returns “Machine Learning” as a top result.
- [ ] `search("mahcine lerning")` still returns “Machine Learning” (typo tolerance).
- [ ] `search("ML")` returns “Machine Learning” via alias search.
- [ ] `search("python", { category: "tool" })` filters by category.
- [ ] Real‑time sync on skill/alias mutations can be added via queued jobs and `TypesenseSearchService::indexSkill()` / `removeSkill()` in later phases.

---

## 3.4 Hybrid Search Endpoint (Laravel API)

### Context

The search API combines:

- **Typesense** for keyword / fuzzy search.
+- **pgvector** for semantic similarity via embeddings.

This gives users the best of both worlds: exact keyword matches AND semantically similar skills.

The hybrid logic lives in `App\Ai\HybridSearchService` and is exposed via an authenticated Laravel API endpoint.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 3.4.1 | `HybridSearchService` | Run Typesense search and pgvector search, merge via reciprocal rank fusion (RRF): `score = Σ 1/(k + rank_i)` with `k = 60`, where `rank_i` is the result rank in each list. Deduplicate by skill ID and compute `match_type` (`"keyword"`, `"semantic"`, `"both"`) | `app/Ai/HybridSearchService.php` |
| 3.4.2 | Request validation | Validate `q`, `category`, `status`, and `limit` (bounded by `pagination_max_limit`) via `SkillSearchRequest` | `app/Http/Requests/SkillSearchRequest.php`, `config/skills_graph.php` |
| 3.4.3 | Search API route | `GET /api/skills/search?q={text}&category={cat}&status={status}&limit={n}` — calls `HybridSearchService`, returns `{ results: [{ skill, score, match_type, highlights }], total, query_time_ms }` | `app/Http/Controllers/Api/SkillSearchController.php`, `routes/api.php` |
| 3.4.4 | Feature tests | Ensure validation, structure, and wiring with `HybridSearchService` work as expected | `tests/Feature/Api/SkillSearchTest.php` |

### Checklist

- [ ] `GET /api/skills/search?q=machine+learning` returns results from both Typesense and pgvector (when configured).
- [ ] Results are deduplicated (no skill appears twice).
- [ ] Results are ranked by merged RRF score.
- [ ] `match_type` indicates `"keyword"`, `"semantic"`, or `"both"`.
- [ ] Response includes `query_time_ms` for performance monitoring.
- [ ] Empty query is rejected by validation (422), not treated as “return all”.
- [ ] Category filter works: `?category=tool` returns only tools.
- [ ] Limit defaults to `pagination_default_limit`, max `pagination_max_limit` from `config/skills_graph.php`.

---

## Phase 3 Completion Verification (Laravel)

```bash
# Generate embeddings for seed data
php artisan skills:embed --chunk=50
# → "Embedded N skills.", "Embedded M skill aliases."

# Set up Typesense
php artisan search:setup
php artisan search:reindex
# → "Typesense skills collection ready.", "Typesense reindex completed."

# Test semantic search (pgvector)
curl "http://localhost/api/skills/search?q=artificial+intelligence" | jq
# → returns "Technology" and related skills by semantic similarity

# Test fuzzy search (Typesense)
curl "http://localhost/api/skills/search?q=technlogy" | jq
# → returns "Technology" via typo correction

# Verify hybrid results and filters
curl "http://localhost/api/skills/search?q=python&category=tool" | jq
# → returns "Python" tool skills
```

---

## Phase 3 Master Checklist (Laravel)

### 3.1 Embedding Service
- [ ] `EmbeddingService` uses `config/ai.php` provider/model/dimensions.
- [ ] Embedding caching configurable via `EMBEDDING_CACHE` and per-call `->cache()`.
- [ ] `php artisan skills:embed` bulk backfill script for skills and aliases.

### 3.2 Vector Search (pgvector)
- [ ] `findSimilarSkills()` with cosine distance and filters.
- [ ] `findSimilarAliases()` for alias matching.
- [ ] `findCandidatesForChunk()` — RAG retrieval (top `rag_candidate_limit` from `config/skills_graph.php`).
- [ ] `checkDuplicate()` with similarity thresholds from `config/skills_graph.php`.
- [ ] `cosineSimilarity()` utility function.
- [ ] Unit tests in `tests/Unit/Ai/VectorSearchServiceTest.php`.

### 3.3 Typesense Integration
- [ ] Client connects, collection schema defined in `config/typesense.php` / `TypesenseSearchService`.
- [ ] `php artisan search:setup` and `php artisan search:reindex` Artisan commands work.
- [ ] `TypesenseSearchService::search()` supports typo tolerance, autocomplete, and faceting via Typesense.
- [ ] Future: real-time sync on skill/alias mutations via queued jobs.

### 3.4 Hybrid Search Endpoint
- [ ] `GET /api/skills/search` combines keyword + semantic results via `HybridSearchService`.
- [ ] Reciprocal rank fusion deduplicates and ranks.
- [ ] Response includes `match_type` and `query_time_ms`.
- [ ] Feature tests in `tests/Feature/Api/SkillSearchTest.php` pass.
