# Phase 3: Embedding & Search Infrastructure

> **Timeline:** Week 5-6
> **Dependencies:** Phase 1 (Foundation), Phase 2 (CRUD API) recommended
> **Unlocks:** Phase 4 (Extraction Pipeline), Phase 5 (Discovery & HITL)
> **Context:** Embeddings power RAG retrieval, duplicate detection, co-occurrence-based relationship discovery, and semantic search

---

## Goal

Add vector embedding generation for all skills/aliases, pgvector similarity search, and full-text search service full-text search. This phase enables the **RAG retrieval step** needed by the extraction pipeline (Phase 4), the **duplicate detection** needed by the discovery pipeline (Phase 5), and the **embedding-based deduplication** used during co-occurrence edge strengthening (Phase 7).

> **Storage split:** Graph structure (skills, aliases, relationships) lives in Neo4j. Vector embeddings live in PostgreSQL (`skill_embeddings`, `alias_embeddings` tables). `VectorSearchService` queries PostgreSQL/pgvector. `SkillService` queries Neo4j for graph data.

---

## 3.1 Embedding Service

### Context

Embeddings are generated via Spring AI's `EmbeddingModel.embed()` and `EmbeddingModel.embedAll()` functions using the model specified by `EMBEDDING_MODEL` at `EMBEDDING_DIMENSIONS` dimensions (see `src/main/java/com/skillsgraph/config/AppConstants.java`; defaults: `text-embedding-3-large` at 1024 — Matryoshka reduction from 3072). All embeddings are cached in Redis with a TTL of `EMBEDDING_CACHE_TTL_SECONDS` to avoid redundant API calls.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 3.1.1 | `EmbeddingService` class | Constructor takes `embeddingModel (autowired Spring AI bean)` from providers config and Redis client. Handles all embedding operations with caching | `src/main/java/com/skillsgraph/service/EmbeddingService.java` |
| 3.1.2 | `embedText(text)` | Embed a single text string. Cache key: `embed:{sha256(text)}`. Check Redis cache first → return cached if found → call `EmbeddingModel.embed() → cache result with `EMBEDDING_CACHE_TTL_SECONDS` TTL (see `src/main/java/com/skillsgraph/config/AppConstants.java`) → return embedding (number[]) | `src/main/java/com/skillsgraph/service/EmbeddingService.java` |
| 3.1.3 | `embedTexts(texts[])` | Embed multiple texts. Check cache for each → call `embeddingModel.embedAll()` for cache misses only → cache each result → return all embeddings in order. Spring AI automatically handles batch size limits | `src/main/java/com/skillsgraph/service/EmbeddingService.java` |
| 3.1.4 | `embedSkill(skill)` | Generate embedding for `"${skill.canonicalName}: ${skill.description || ''}"` and INSERT/UPDATE into the `skill_embeddings` PostgreSQL table (keyed by the Neo4j skill node's `id`) | `src/main/java/com/skillsgraph/service/EmbeddingService.java` |
| 3.1.5 | `embedAlias(alias)` | Generate embedding for `alias.surfaceForm` and INSERT/UPDATE into the `alias_embeddings` PostgreSQL table (keyed by the Neo4j alias node's `id`) | `src/main/java/com/skillsgraph/service/EmbeddingService.java` |
| 3.1.6 | Hook into SkillService | After `SkillService.create()` and `SkillService.update()` (when name or description changes), call `embedSkill()`. After `AliasService.create()`, call `embedAlias()`. Use async (non-blocking) — don't delay the API response | `src/main/java/com/skillsgraph/service/SkillService.java`, `src/main/java/com/skillsgraph/service/AliasService.java` |
| 3.1.7 | Bulk re-embedding script | `./mvnw spring-boot:run -Dspring-boot.run.arguments=--embed-all` — query all skills and aliases with NULL embeddings, batch embed in groups of `EMBEDDING_BATCH_SIZE` (see `src/main/java/com/skillsgraph/config/AppConstants.java`) using `embedTexts()`, update rows. Report progress. For initial backfill or after embedding model change | `src/main/java/com/skillsgraph/script/EmbedAllRunner.java` |

### Checklist

- [ ] `EmbeddingService` bean autowired with Spring AI `EmbeddingModel` and `CacheService`
- [ ] `embedText("Machine Learning")` returns a `EMBEDDING_DIMENSIONS`-dimension float array (see `src/main/java/com/skillsgraph/config/AppConstants.java`)
- [ ] `embeddingService.embedText("Machine Learning")` called twice → second call hits Redis cache
- [ ] `embeddingService.embedTexts(List.of("Python", "JavaScript", "Java"))` returns 3 embeddings, each 1024-dim
- [ ] `embeddingService.embedTexts()` only calls `EmbeddingModel.embedAll()` for cache misses
- [ ] `embedSkill()` updates the `skills.embedding` column
- [ ] `embedAlias()` updates the `skill_aliases.alias_embedding` column
- [ ] Creating a skill via API triggers async embedding generation
- [ ] Updating a skill's name triggers re-embedding
- [ ] Creating an alias triggers async alias embedding
- [ ] `EmbedAllRunner --embed-all` processes all skills/aliases with NULL embeddings
- [ ] `EmbedAllRunner --embed-all` is idempotent (skips already-embedded entries)
- [ ] Reports progress: `"Embedded 100/350 skills..."`

---

## 3.2 Vector Search (pgvector)

### Context

pgvector enables approximate nearest neighbor (ANN) search using HNSW indexes. The `VectorSearchService` wraps these queries for use by the extraction pipeline (RAG retrieval), duplicate detection, and semantic search.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 3.2.1 | `VectorSearchService` class | Constructor takes DB client. All methods query pgvector using cosine distance (`<=>`) | `src/main/java/com/skillsgraph/service/VectorSearchService.java` |
| 3.2.2 | `findSimilarSkills(embedding, k, filters?)` | Return top-K nearest skills from the `skill_embeddings` PostgreSQL table. SQL: `SELECT se.skill_id, 1 - (se.embedding <=> $1::vector) AS similarity FROM skill_embeddings se WHERE se.status = 'active' ORDER BY se.embedding <=> $1::vector LIMIT $2`. **Sync mechanism:** add a `status TEXT` column to `skill_embeddings` (default `'active'`); `ChangelogService` calls `UPDATE skill_embeddings SET status = $status WHERE skill_id = $id` whenever a skill's status changes in Neo4j. This keeps the filter column in sync without a round-trip. Graph context (aliases, relationships) is fetched from Neo4j via `Neo4jTemplate` after finding the IDs | `src/main/java/com/skillsgraph/service/VectorSearchService.java` |
| 3.2.3 | `findSimilarAliases(embedding, k)` | Same but against `alias_embeddings.alias_embedding` (PostgreSQL). Return alias IDs, then fetch alias + parent skill info from Neo4j via `Neo4jTemplate` | `src/main/java/com/skillsgraph/service/VectorSearchService.java` |
| 3.2.4 | `findCandidatesForChunk(chunkEmbedding, k=RAG_CANDIDATE_LIMIT)` | The **RAG retrieval function**: given a text chunk embedding, return top `RAG_CANDIDATE_LIMIT` skills (see `src/main/java/com/skillsgraph/config/AppConstants.java`) with `{ id, external_id, canonical_name, description, similarity }`. This is the core function used by the extraction pipeline in Phase 4. Include skill descriptions for prompt context | `src/main/java/com/skillsgraph/service/VectorSearchService.java` |
| 3.2.5 | `checkDuplicate(name, description?)` | Embed the candidate text via `EmbeddingService.embedText()`, search for nearest neighbors. Return `{ isDuplicate: boolean, matches: Array<{ skill, similarity }> }`. Thresholds from `src/main/java/com/skillsgraph/config/AppConstants.java`: ≥`SIMILARITY_DUPLICATE_THRESHOLD` = duplicate, [`SIMILARITY_REVIEW_THRESHOLD`, `SIMILARITY_DUPLICATE_THRESHOLD`) = similar (flag for review), <`SIMILARITY_REVIEW_THRESHOLD` = unique. Also used by the discovery pipeline (Phase 5) for candidate deduplication and by the co-occurrence edge strengthening job (Phase 7) to validate pairs before creating empirical edges | `src/main/java/com/skillsgraph/service/VectorSearchService.java` |
| 3.2.6 | `cosineSimilarity(a, b)` | Pure JS cosine similarity between two embeddings (for in-memory comparisons without hitting DB) | `src/main/java/com/skillsgraph/util/VectorMath.java` |

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

## 3.3 full-text search service Integration

### Context

full-text search service provides fast full-text search with built-in autocomplete, typo tolerance, and faceting. It's ideal for the skills search API where users type partial queries like "mahcine lerning" and expect "Machine Learning" to appear.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 3.3.1 | `SearchService` Spring bean | `@Service SearchService` wraps all full-text search operations using `JdbcTemplate` with PostgreSQL `tsvector`/`pg_trgm` queries | `src/main/java/com/skillsgraph/service/SearchService.java` |
| 3.3.2 | Search index view/column | Add `search_vector tsvector` column to `skills` table (populated by trigger or `UPDATE`). GIN index on `search_vector`. Include `canonical_name` + all alias `surface_form` values | `src/main/resources/db/migration/V2__search_index.sql` |
| 3.3.3 | `SearchSetupRunner` | `ApplicationRunner` that creates the GIN index if not exists and warms up the search vector. Triggered via `--search-setup` argument | `src/main/java/com/skillsgraph/script/SearchSetupRunner.java` |
| 3.3.4 | `indexSkill(UUID skillId)` | Regenerate `search_vector` for a skill by concatenating `canonical_name` and all alias `surface_form` values. Called asynchronously (`@Async`) after skill/alias mutations | `src/main/java/com/skillsgraph/service/SearchService.java` |
| 3.3.5 | Deprecation/merge handling | When a skill is deprecated or merged, `search_vector` is set to `NULL` (excluded from search queries via `WHERE search_vector IS NOT NULL`) | `src/main/java/com/skillsgraph/service/SearchService.java` |
| 3.3.6 | `SearchReindexRunner` | `ApplicationRunner` triggered via `--search-reindex`. Rebuilds `search_vector` for all active skills in batches. Reports progress | `src/main/java/com/skillsgraph/script/SearchReindexRunner.java` |
| 3.3.7 | `search(String query, SearchFilters filters, int limit)` | Run `WHERE search_vector @@ plainto_tsquery(:q) OR canonical_name %% :q` ordered by `ts_rank` + trigram similarity. Return `SearchResponse` with `results`, `total`, `queryTimeMs` | `src/main/java/com/skillsgraph/service/SearchService.java` |
| 3.3.8 | Sync on mutations | After skill/alias create/update/delete (in SkillService and AliasService), call `indexSkill()` to keep full-text search service in sync. Use async (non-blocking) | `src/main/java/com/skillsgraph/service/SkillService.java`, `src/main/java/com/skillsgraph/service/AliasService.java` |

### Checklist

- [ ] PostgreSQL `pg_trgm` extension and GIN index are enabled
- [ ] `SearchSetupRunner --search-setup` creates GIN index if not exists
- [ ] `SearchReindexRunner --search-reindex` rebuilds `search_vector` for all active skills
- [ ] `searchService.search("machine learning")` returns "Machine Learning" as top result
- [ ] Trigram: `searchService.search("mahcine lerning")` returns "Machine Learning"
- [ ] Alias search: `searchService.search("ML")` returns "Machine Learning"
- [ ] Category filter: `searchService.search("python", SearchFilters.category("tool"))` filters correctly
- [ ] Prefix: `searchService.search("pyth")` returns "Python"
- [ ] Creating a new skill via API makes it searchable within 1s (async `@Async`)
- [ ] Updating a skill name updates the search vector
- [ ] Adding an alias makes the skill findable by that alias
- [ ] `SearchReindexRunner` completes without errors and all active skills are indexed

---

## 3.4 Hybrid Search Endpoint

### Context

The search API combines full-text search service (keyword/fuzzy) and pgvector (semantic similarity) results for comprehensive search. This gives users the best of both worlds: exact keyword matches AND semantically similar skills.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 3.4.1 | `HybridSearchService` | Run full-text search service search and pgvector search in parallel. Merge results using reciprocal rank fusion (RRF): `score = Σ 1/(k + rank_i)` where `k=60` and `rank_i` is the rank in each result list. Deduplicate by skill ID, keeping highest merged score | `src/main/java/com/skillsgraph/service/HybridSearchService.java` |
| 3.4.2 | Search API route | `GET /api/skills/search?q={text}&category={cat}&status={status}&limit={n}` — calls `HybridSearchService`, returns `{ results: [{ skill, score, match_type }], total, query_time_ms }` | `src/main/java/com/skillsgraph/controller/SkillController.java` |
| 3.4.3 | Search response DTO | `SearchResponse` Java record with typed results including `matchType` and highlight snippets | `src/main/java/com/skillsgraph/dto/SearchDto.java` |

### Checklist

- [ ] `GET /api/skills/search?q=machine+learning` returns results from both full-text search service and pgvector
- [ ] Results are deduplicated (no skill appears twice)
- [ ] Results are ranked by merged RRF score
- [ ] `match_type` field indicates `"keyword"`, `"semantic"`, or `"both"`
- [ ] Response includes `query_time_ms` for performance monitoring
- [ ] Empty query returns empty results (not all skills)
- [ ] Category filter works: `?category=tool` only returns tools
- [ ] Limit defaults to `PAGINATION_DEFAULT_LIMIT`, max `PAGINATION_MAX_LIMIT` (see `src/main/java/com/skillsgraph/config/AppConstants.java`)

---

## Phase 3 Completion Verification

```bash
# Generate embeddings for seed data
./mvnw spring-boot:run -Dspring-boot.run.arguments=--embed-all
# → "Embedded 6/6 skills, 6/6 aliases"

# Set up full-text search service
./mvnw spring-boot:run -Dspring-boot.run.arguments=--search-setup
./mvnw spring-boot:run -Dspring-boot.run.arguments=--search-reindex
# → "Indexed 6 skills"

# Test semantic search (pgvector)
curl "http://localhost:8080/api/skills/search?q=artificial+intelligence" | jq
# → returns "Technology" and related skills by semantic similarity

# Test fuzzy search (full-text search service)
curl "http://localhost:8080/api/skills/search?q=technlogy" | jq
# → returns "Technology" via typo correction

# Test duplicate detection
curl -X POST http://localhost:8080/api/skills \
  -H "Content-Type: application/json" \
  -d '{"canonical_name":"Tech","category":"domain","path":"tech"}'
# → 409 if "Technology" exists with similarity > 0.90

# Verify real-time sync: create skill, then search
curl -X POST http://localhost:8080/api/skills \
  -H "Content-Type: application/json" \
  -d '{"canonical_name":"Python","category":"tool","description":"Programming language","path":"technology.programming.python"}'
sleep 1
curl "http://localhost:8080/api/skills/search?q=python" | jq
# → returns newly created "Python" skill

./mvnw test
echo "Phase 3 complete ✓"
```

---

## Phase 3 Master Checklist

### 3.1 Embedding Service
- [ ] `EmbeddingService` with cached `embedText()` and `embedTexts()`
- [ ] Auto-embedding on skill create/update and alias create
- [ ] `./mvnw spring-boot:run -Dspring-boot.run.arguments=--embed-all` bulk backfill script
- [ ] Redis caching with `EMBEDDING_CACHE_TTL_SECONDS` TTL (see `src/main/java/com/skillsgraph/config/AppConstants.java`)
- [ ] Correct model: `EMBEDDING_MODEL` at `EMBEDDING_DIMENSIONS` dimensions (see `src/main/java/com/skillsgraph/config/AppConstants.java`)

### 3.2 Vector Search (pgvector)
- [ ] `findSimilarSkills()` with cosine distance
- [ ] `findSimilarAliases()` for alias matching
- [ ] `findCandidatesForChunk()` — the RAG retrieval function (top `RAG_CANDIDATE_LIMIT` — see `src/main/java/com/skillsgraph/config/AppConstants.java`)
- [ ] `checkDuplicate()` with similarity thresholds
- [ ] `cosineSimilarity()` utility function

### 3.3 full-text search service Integration
- [ ] Client connects, collection schema defined
- [ ] `./mvnw spring-boot:run -Dspring-boot.run.arguments=--search-setup` and `./mvnw spring-boot:run -Dspring-boot.run.arguments=--search-reindex` scripts
- [ ] `search()` with typo tolerance, autocomplete, faceting
- [ ] Real-time sync on skill/alias mutations
- [ ] `indexSkill()` and `removeSkill()` functions

### 3.4 Hybrid Search Endpoint
- [ ] `GET /api/skills/search` combines keyword + semantic results
- [ ] Reciprocal rank fusion deduplicates and ranks
- [ ] Response includes match_type and query_time_ms
