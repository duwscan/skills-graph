# Phase 3: Embedding & Search Infrastructure

> **Timeline:** Week 5-6
> **Dependencies:** Phase 1 (Foundation), Phase 2 (CRUD API) recommended
> **Unlocks:** Phase 4 (Extraction Pipeline), Phase 5 (Discovery & HITL)
> **Context:** Embeddings power RAG retrieval, duplicate detection, co-occurrence-based relationship discovery, and semantic search

---

## Goal

Add vector embedding generation for all skills/aliases, pgvector similarity search, and Typesense full-text search. This phase enables the **RAG retrieval step** needed by the extraction pipeline (Phase 4), the **duplicate detection** needed by the discovery pipeline (Phase 5), and the **embedding-based deduplication** used during co-occurrence edge strengthening (Phase 7).

---

## 3.1 Embedding Service

### Context

Embeddings are generated via the Vercel AI SDK's `embed()` and `embedMany()` functions using the model specified by `EMBEDDING_MODEL` at `EMBEDDING_DIMENSIONS` dimensions (see `src/config/constants.ts`; defaults: `text-embedding-3-large` at 1024 — Matryoshka reduction from 3072). All embeddings are cached in Redis with a TTL of `EMBEDDING_CACHE_TTL_SECONDS` to avoid redundant API calls.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 3.1.1 | `EmbeddingService` class | Constructor takes `getEmbeddingModel()` from providers config and Redis client. Handles all embedding operations with caching | `src/services/embedding.ts` |
| 3.1.2 | `embedText(text)` | Embed a single text string. Cache key: `embed:{sha256(text)}`. Check Redis cache first → return cached if found → call `embed()` from AI SDK → cache result with `EMBEDDING_CACHE_TTL_SECONDS` TTL (see `src/config/constants.ts`) → return embedding (number[]) | `src/services/embedding.ts` |
| 3.1.3 | `embedTexts(texts[])` | Embed multiple texts. Check cache for each → call `embedMany()` for cache misses only → cache each result → return all embeddings in order. AI SDK automatically handles batch size limits | `src/services/embedding.ts` |
| 3.1.4 | `embedSkill(skill)` | Generate embedding for `"${skill.canonical_name}: ${skill.description || ''}"` and UPDATE the skill's `embedding` column in PostgreSQL | `src/services/embedding.ts` |
| 3.1.5 | `embedAlias(alias)` | Generate embedding for `alias.surface_form` and UPDATE the alias's `alias_embedding` column | `src/services/embedding.ts` |
| 3.1.6 | Hook into SkillService | After `SkillService.create()` and `SkillService.update()` (when name or description changes), call `embedSkill()`. After `AliasService.create()`, call `embedAlias()`. Use async (non-blocking) — don't delay the API response | `src/services/skill.ts`, `src/services/alias.ts` |
| 3.1.7 | Bulk re-embedding script | `bun run embed:all` — query all skills and aliases with NULL embeddings, batch embed in groups of `EMBEDDING_BATCH_SIZE` (see `src/config/constants.ts`) using `embedTexts()`, update rows. Report progress. For initial backfill or after embedding model change | `src/scripts/embed-all.ts` |

### Checklist

- [ ] `EmbeddingService` class instantiated with correct model and Redis client
- [ ] `embedText("Machine Learning")` returns a `EMBEDDING_DIMENSIONS`-dimension float array (see `src/config/constants.ts`)
- [ ] `embedText("Machine Learning")` called twice → second call hits Redis cache (verify with Redis `GET`)
- [ ] `embedTexts(["Python", "JavaScript", "TypeScript"])` returns 3 embeddings, each `EMBEDDING_DIMENSIONS` dims
- [ ] `embedTexts()` only calls AI SDK for cache misses (verify with mock)
- [ ] `embedSkill()` updates the `skills.embedding` column
- [ ] `embedAlias()` updates the `skill_aliases.alias_embedding` column
- [ ] Creating a skill via API triggers async embedding generation
- [ ] Updating a skill's name triggers re-embedding
- [ ] Creating an alias triggers async alias embedding
- [ ] `bun run embed:all` processes all skills/aliases with NULL embeddings
- [ ] `bun run embed:all` is idempotent (skips already-embedded entries)
- [ ] `bun run embed:all` reports progress: `"Embedded 100/350 skills..."`

---

## 3.2 Vector Search (pgvector)

### Context

pgvector enables approximate nearest neighbor (ANN) search using HNSW indexes. The `VectorSearchService` wraps these queries for use by the extraction pipeline (RAG retrieval), duplicate detection, and semantic search.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 3.2.1 | `VectorSearchService` class | Constructor takes DB client. All methods query pgvector using cosine distance (`<=>`) | `src/services/vector-search.ts` |
| 3.2.2 | `findSimilarSkills(embedding, k, filters?)` | Return top-K nearest active skills. SQL: `SELECT *, 1 - (embedding <=> $1::vector) AS similarity FROM skills WHERE status = 'active' AND embedding IS NOT NULL ORDER BY embedding <=> $1::vector LIMIT $2`. Optional filters: `category`, exclude IDs | `src/services/vector-search.ts` |
| 3.2.3 | `findSimilarAliases(embedding, k)` | Same but against `skill_aliases.alias_embedding`. Return alias + parent skill info | `src/services/vector-search.ts` |
| 3.2.4 | `findCandidatesForChunk(chunkEmbedding, k=RAG_CANDIDATE_LIMIT)` | The **RAG retrieval function**: given a text chunk embedding, return top `RAG_CANDIDATE_LIMIT` skills (see `src/config/constants.ts`) with `{ id, external_id, canonical_name, description, similarity }`. This is the core function used by the extraction pipeline in Phase 4. Include skill descriptions for prompt context | `src/services/vector-search.ts` |
| 3.2.5 | `checkDuplicate(name, description?)` | Embed the candidate text via `EmbeddingService.embedText()`, search for nearest neighbors. Return `{ isDuplicate: boolean, matches: Array<{ skill, similarity }> }`. Thresholds from `src/config/constants.ts`: ≥`SIMILARITY_DUPLICATE_THRESHOLD` = duplicate, [`SIMILARITY_REVIEW_THRESHOLD`, `SIMILARITY_DUPLICATE_THRESHOLD`) = similar (flag for review), <`SIMILARITY_REVIEW_THRESHOLD` = unique. Also used by the discovery pipeline (Phase 5) for candidate deduplication and by the co-occurrence edge strengthening job (Phase 7) to validate pairs before creating empirical edges | `src/services/vector-search.ts` |
| 3.2.6 | `cosineSimilarity(a, b)` | Pure JS cosine similarity between two embeddings (for in-memory comparisons without hitting DB) | `src/lib/math.ts` |

### Checklist

- [ ] `findSimilarSkills()` returns skills ordered by similarity (highest first)
- [ ] `findSimilarSkills()` only returns active skills with non-null embeddings
- [ ] `findSimilarSkills()` respects the `k` limit
- [ ] `findSimilarAliases()` returns aliases with parent skill info
- [ ] `findCandidatesForChunk()` returns top `RAG_CANDIDATE_LIMIT` candidates with required fields
- [ ] `findCandidatesForChunk()` query completes in < 50ms on 10K skills
- [ ] `checkDuplicate("Machine Learning")` returns `isDuplicate: true` when "Machine Learning" exists
- [ ] `checkDuplicate("Quantum Computing")` returns `isDuplicate: false` when it doesn't exist
- [ ] `checkDuplicate("ML")` returns `isDuplicate: true` (alias match via alias embedding)
- [ ] `cosineSimilarity([1,0], [1,0])` returns 1.0
- [ ] `cosineSimilarity([1,0], [0,1])` returns 0.0

---

## 3.3 Typesense Integration

### Context

Typesense provides fast full-text search with built-in autocomplete, typo tolerance, and faceting. It's ideal for the skills search API where users type partial queries like "mahcine lerning" and expect "Machine Learning" to appear.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 3.3.1 | Typesense client | Initialize Typesense client with server URL and API key from env. Export singleton instance | `src/services/typesense.ts` |
| 3.3.2 | Collection schema | Define `skills` collection: `{ id (string, PK), external_id, canonical_name (string, facet), slug, description, category (string, facet), status (string, facet), aliases (string[], facet) }`. Configure `canonical_name` and `aliases` as searchable fields with typo tolerance | `src/services/typesense.ts` |
| 3.3.3 | `setupCollection()` | Create or update the Typesense collection. Idempotent — delete and recreate if exists. Script: `bun run search:setup` | `src/scripts/search-setup.ts` |
| 3.3.4 | `indexSkill(skill)` | Upsert a single skill document into Typesense. Fetch all aliases for the skill and include them as a `string[]` field. Called after skill/alias mutations | `src/services/typesense.ts` |
| 3.3.5 | `removeSkill(skillId)` | Remove a skill document from Typesense. Called when skill is deprecated/merged | `src/services/typesense.ts` |
| 3.3.6 | `reindexAll()` | Drop collection, recreate, bulk index all active skills with aliases. Script: `bun run search:reindex`. Report progress | `src/scripts/search-reindex.ts` |
| 3.3.7 | `search(query, filters?, limit?)` | Search Typesense: `query_by: "canonical_name,aliases,description"`, `filter_by` for category/status, `per_page` for limit. Return `{ results: [{ skill, score, highlights }], total, search_time_ms }` | `src/services/typesense.ts` |
| 3.3.8 | Sync on mutations | After skill/alias create/update/delete (in SkillService and AliasService), call `indexSkill()` to keep Typesense in sync. Use async (non-blocking) | `src/services/skill.ts`, `src/services/alias.ts` |

### Checklist

- [ ] Typesense client connects to the container from docker-compose
- [ ] `bun run search:setup` creates the `skills` collection
- [ ] `bun run search:reindex` indexes all active skills with their aliases
- [ ] `search("machine learning")` returns "Machine Learning" as top result
- [ ] `search("mahcine lerning")` returns "Machine Learning" (typo tolerance)
- [ ] `search("ML")` returns "Machine Learning" (alias search)
- [ ] `search("python", { category: "tool" })` filters by category
- [ ] `search("pyth")` returns "Python" (prefix/autocomplete)
- [ ] Creating a new skill via API makes it searchable in Typesense within 1s
- [ ] Updating a skill name updates the Typesense document
- [ ] Adding an alias makes the skill findable by that alias in Typesense
- [ ] `reindexAll()` completes without errors and all active skills are indexed

---

## 3.4 Hybrid Search Endpoint

### Context

The search API combines Typesense (keyword/fuzzy) and pgvector (semantic similarity) results for comprehensive search. This gives users the best of both worlds: exact keyword matches AND semantically similar skills.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 3.4.1 | `HybridSearchService` | Run Typesense search and pgvector search in parallel. Merge results using reciprocal rank fusion (RRF): `score = Σ 1/(k + rank_i)` where `k=60` and `rank_i` is the rank in each result list. Deduplicate by skill ID, keeping highest merged score | `src/services/hybrid-search.ts` |
| 3.4.2 | Search API route | `GET /api/skills/search?q={text}&category={cat}&status={status}&limit={n}` — calls `HybridSearchService`, returns `{ results: [{ skill, score, match_type }], total, query_time_ms }` | `src/routes/skills.ts` |
| 3.4.3 | Search response schema | Zod schema for the search response: `searchResponseSchema` with typed results including highlights | `src/schemas/search.ts` |

### Checklist

- [ ] `GET /api/skills/search?q=machine+learning` returns results from both Typesense and pgvector
- [ ] Results are deduplicated (no skill appears twice)
- [ ] Results are ranked by merged RRF score
- [ ] `match_type` field indicates `"keyword"`, `"semantic"`, or `"both"`
- [ ] Response includes `query_time_ms` for performance monitoring
- [ ] Empty query returns empty results (not all skills)
- [ ] Category filter works: `?category=tool` only returns tools
- [ ] Limit defaults to `PAGINATION_DEFAULT_LIMIT`, max `PAGINATION_MAX_LIMIT` (see `src/config/constants.ts`)

---

## Phase 3 Completion Verification

```bash
# Generate embeddings for seed data
bun run embed:all
# → "Embedded 6/6 skills, 6/6 aliases"

# Set up Typesense
bun run search:setup
bun run search:reindex
# → "Indexed 6 skills"

# Test semantic search (pgvector)
curl "http://localhost:3000/api/skills/search?q=artificial+intelligence" | jq
# → returns "Technology" and related skills by semantic similarity

# Test fuzzy search (Typesense)
curl "http://localhost:3000/api/skills/search?q=technlogy" | jq
# → returns "Technology" via typo correction

# Test duplicate detection
curl -X POST http://localhost:3000/api/skills \
  -H "Content-Type: application/json" \
  -d '{"canonical_name":"Tech","category":"domain","path":"tech"}'
# → 409 if "Technology" exists with similarity > 0.90

# Verify real-time sync: create skill, then search
curl -X POST http://localhost:3000/api/skills \
  -H "Content-Type: application/json" \
  -d '{"canonical_name":"Python","category":"tool","description":"Programming language","path":"technology.programming.python"}'
sleep 1
curl "http://localhost:3000/api/skills/search?q=python" | jq
# → returns newly created "Python" skill

bun test
echo "Phase 3 complete ✓"
```

---

## Phase 3 Master Checklist

### 3.1 Embedding Service
- [ ] `EmbeddingService` with cached `embedText()` and `embedTexts()`
- [ ] Auto-embedding on skill create/update and alias create
- [ ] `bun run embed:all` bulk backfill script
- [ ] Redis caching with `EMBEDDING_CACHE_TTL_SECONDS` TTL (see `src/config/constants.ts`)
- [ ] Correct model: `EMBEDDING_MODEL` at `EMBEDDING_DIMENSIONS` dimensions (see `src/config/constants.ts`)

### 3.2 Vector Search (pgvector)
- [ ] `findSimilarSkills()` with cosine distance
- [ ] `findSimilarAliases()` for alias matching
- [ ] `findCandidatesForChunk()` — the RAG retrieval function (top `RAG_CANDIDATE_LIMIT` — see `src/config/constants.ts`)
- [ ] `checkDuplicate()` with similarity thresholds
- [ ] `cosineSimilarity()` utility function

### 3.3 Typesense Integration
- [ ] Client connects, collection schema defined
- [ ] `bun run search:setup` and `bun run search:reindex` scripts
- [ ] `search()` with typo tolerance, autocomplete, faceting
- [ ] Real-time sync on skill/alias mutations
- [ ] `indexSkill()` and `removeSkill()` functions

### 3.4 Hybrid Search Endpoint
- [ ] `GET /api/skills/search` combines keyword + semantic results
- [ ] Reciprocal rank fusion deduplicates and ranks
- [ ] Response includes match_type and query_time_ms
