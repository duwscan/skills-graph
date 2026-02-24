# Phase 2: Core API (Taxonomy CRUD)

> **Timeline:** Week 3-4
> **Dependencies:** Phase 1 (Foundation)
> **Unlocks:** Phase 4 (Extraction Pipeline), Phase 5 (Discovery & HITL), Phase 6 (Lifecycle)
> **Context:** LLM-First Skills Graph — all skill references use `skills.id` as single source of truth

---

## Goal

Implement all taxonomy query and mutation APIs from ARCHITECTURE.md §6 — the REST endpoints for creating, reading, updating, and managing skills, aliases, and edges. No AI/LLM features yet. By the end of this phase, you should be able to fully manage a skills taxonomy through the API.

---

## 2.1 Shared Jakarta Bean Validation Schemas

### Context

Java records with Jakarta Bean Validation annotations serve as the **single source of truth** for both API request/response validation (via `@Valid`) and LLM structured output (via Spring AI `BeanOutputConverter`). They live in the `dto/` package.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 2.1.1 | Shared Java enums | Define `SkillStatus` (`candidate`, `active`, `deprecated`, `merged`), `SkillCategory` (`domain`, `tool`, `certification`, `soft_skill`, `methodology`, `language`), `RelationshipType` (`parent_of`, `child_of`, `related_to`, `requires`, `superseded_by`), `Provenance` (`human_curated`, `llm_predicted`, `embedding_similarity`, `empirical`), `AliasSource` (`curated`, `llm_discovered`, `user_submitted`), `EdgeStatus` (`active`, `pending_review`, `rejected`, `deprecated`) | `src/main/java/com/skillsgraph/dto/Enums.java` |
| 2.1.2 | Skill schemas | `createSkillSchema`: `canonical_name` (required), `description`, `category`, `status` (default `candidate`), `metadata` (optional JSON). Auto-generate `slug` from name. `updateSkillSchema`: all fields optional (partial). `skillResponseSchema`: full skill with `id`, `external_id`, `version`, timestamps, nested `aliases[]`, `relationships[]` | `src/main/java/com/skillsgraph/dto/SkillDto.java` |
| 2.1.3 | Alias schemas | `createAliasSchema`: `surface_form`, `locale` (default `en`), `source` (default `curated`), `is_primary` (default false). `aliasResponseSchema` | `src/main/java/com/skillsgraph/dto/AliasDto.java` |
| 2.1.4 | Edge schemas | `createEdgeSchema`: `source_skill_id` (UUID), `target_skill_id` (UUID), `relationship_type`, `confidence` (default 1.0), `provenance` (default `human_curated`). `edgeResponseSchema` | `src/main/java/com/skillsgraph/dto/EdgeDto.java` |
| 2.1.5 | Query schemas | `paginationSchema`: `limit` (default `PAGINATION_DEFAULT_LIMIT`, max `PAGINATION_MAX_LIMIT` — see `src/main/java/com/skillsgraph/config/AppConstants.java`), `offset` (default 0). `listSkillsQuerySchema`: pagination + `status`, `category`, `q` (search text). `depthSchema`: `depth` (default `TRAVERSAL_DEFAULT_DEPTH`, max `TRAVERSAL_MAX_DEPTH`) | `src/main/java/com/skillsgraph/dto/QueryParams.java` |

### Checklist

- [ ] `src/main/java/com/skillsgraph/dto/Enums.java` — all 6 enum types exported as Java enums
- [ ] `src/main/java/com/skillsgraph/dto/SkillDto.java` — `createSkillSchema`, `updateSkillSchema`, `skillResponseSchema` defined
- [ ] `src/main/java/com/skillsgraph/dto/AliasDto.java` — `createAliasSchema`, `aliasResponseSchema` defined
- [ ] `src/main/java/com/skillsgraph/dto/EdgeDto.java` — `createEdgeSchema`, `edgeResponseSchema` defined
- [ ] `src/main/java/com/skillsgraph/dto/QueryParams.java` — pagination, list filters, depth param schemas
- [ ] All schemas have proper defaults and constraints
- [ ] Java types can be inferred from schemas: `type CreateSkill = CreateSkillRequest`

---

## 2.2 Skill CRUD Service

### Context

The `SkillService` is the primary business logic layer for skill nodes. It handles creation (with auto-generated external_id and slug), retrieval (with joined aliases/edges), updates, listing with filtering, and hierarchical traversal (ancestors/descendants).

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 2.2.1 | `create(input)` | Validate input via `createSkillSchema`. Auto-generate `external_id` as `SK-{UUID.randomUUID()(8)}`. Auto-generate `slug` from `canonical_name` via `SlugUtils()`. Create Neo4j `(:Skill)` node via `Neo4jTemplate` or `SkillRepository.save()`. Create initial `(:Alias)` node linked via `[:HAS_ALIAS]`. Record in `graph_changelog` (PostgreSQL). Return full skill object | `src/main/java/com/skillsgraph/service/SkillService.java` |
| 2.2.2 | `getById(id)` | Fetch skill by `id` or `externalId` or `slug` from Neo4j. Use Cypher `MATCH (s:Skill)-[:HAS_ALIAS]->(a:Alias)` and `MATCH (s)-[r]->()` patterns via `Neo4jTemplate`. Throw `SkillNotFoundException` if not found | `src/main/java/com/skillsgraph/service/SkillService.java` |
| 2.2.3 | `update(id, input)` | Validate input via `updateSkillSchema`. Partial update only provided fields. Increment `version`, set `updated_at = now()`. Record changelog with diff (old vs new values). If `canonical_name` changed, update the primary `en` alias too | `src/main/java/com/skillsgraph/service/SkillService.java` |
| 2.2.4 | `list(query)` | Paginated listing with filters: `status`, `category`, `source`. If `q` parameter present, use trigram similarity search (`canonical_name % $q`) ordered by `similarity(canonical_name, $q) DESC`. Return `{ items, total, limit, offset }` | `src/main/java/com/skillsgraph/service/SkillService.java` |
| 2.2.5 | `getAncestors(id, depth?)` | Use Cypher variable-length pattern `(s:Skill {id: $skillId})<-[:PARENT_OF*1..$depth]-(ancestor:Skill)` via `Neo4jTemplate`. Limit depth (default `TRAVERSAL_DEFAULT_DEPTH`, max `TRAVERSAL_MAX_DEPTH` — see `src/main/java/com/skillsgraph/config/AppConstants.java`). Return ordered list of ancestor skills | `src/main/java/com/skillsgraph/service/SkillService.java` |
| 2.2.6 | `getDescendants(id, depth?)` | Use Cypher variable-length pattern `(parent:Skill {id: $skillId})-[:PARENT_OF*1..$depth]->(s:Skill)` via `Neo4jTemplate`. Limit depth (default `TRAVERSAL_DEFAULT_DEPTH`, max `TRAVERSAL_MAX_DEPTH`). Return tree structure or flat list of descendant skills | `src/main/java/com/skillsgraph/service/SkillService.java` |
| 2.2.7 | `getRoots()` | Return all active skills that have no incoming `PARENT_OF` relationships. Cypher: `MATCH (s:Skill {status: 'active'}) WHERE NOT ()-[:PARENT_OF]->(s) RETURN s`. These are the top-level taxonomy categories | `src/main/java/com/skillsgraph/service/SkillService.java` |
| 2.2.8 | Slug utility | `public static String generateSlug(String name)` — lowercase, replace spaces with hyphens, remove special chars, truncate to `SLUG_MAX_LENGTH` chars (see `src/main/java/com/skillsgraph/config/AppConstants.java`). Handle duplicates by appending `-2`, `-3`, etc. | `src/main/java/com/skillsgraph/util/SlugUtils.java` |

### Cypher Ancestors Query

```cypher
// Find all ancestors up to configurable depth using variable-length paths
MATCH path = (s:Skill {id: $skillId})<-[:PARENT_OF*1..$depth]-(ancestor:Skill)
WHERE ancestor.status = 'active'
RETURN DISTINCT ancestor, length(path) AS depth
ORDER BY depth ASC
```

```java
@Service
public class SkillService {
    private final Neo4jTemplate neo4jTemplate;
    
    public List<SkillWithDepth> getAncestors(String skillId, int depth) {
        return neo4jTemplate.findAll(
            "MATCH path = (s:Skill {id: $skillId})<-[:PARENT_OF*1..$depth]-(ancestor:Skill) " +
            "WHERE ancestor.status = 'active' " +
            "RETURN DISTINCT ancestor, length(path) AS depth ORDER BY depth ASC",
            Map.of("skillId", skillId, "depth", depth),
            SkillWithDepth.class
        );
    }
}
```

### Checklist

- [ ] `create()` — generates `SK-` external_id, slug, initial alias, changelog entry
- [ ] `create()` — rejects duplicate `canonical_name` by checking slug uniqueness
- [ ] `getById()` — works with UUID id, external_id (`SK-xxx`), and slug
- [ ] `getById()` — includes nested aliases and relationships in response
- [ ] `getById()` — throws `SkillNotFoundException` for non-existent id
- [ ] `update()` — partial updates work (only update provided fields)
- [ ] `update()` — increments `version` and updates `updated_at`
- [ ] `update()` — records changelog with before/after diff
- [ ] `list()` — pagination works with `limit` and `offset`
- [ ] `list()` — filters by `status`, `category` work
- [ ] `list()` — trigram fuzzy search (`?q=`) returns relevant results
- [ ] `getAncestors()` — returns correct ancestor chain for a deeply nested skill
- [ ] `getAncestors()` — handles polyhierarchy (skill with multiple parents)
- [ ] `getDescendants()` — returns correct descendants to specified depth
- [ ] `getDescendants()` — respects depth limit
- [ ] `getRoots()` — returns only skills with no incoming `parent_of` edges
- [ ] `getRoots()` — returns seeded root categories (Technology, Business, etc.)
- [ ] `getRoots()` — does not return skills that have a parent
- [ ] Slug generation handles special characters, Unicode, duplicates

---

## 2.3 Alias Service

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 2.3.1 | `create(skillId, input)` | Validate input via `createAliasSchema`. If `is_primary = true`, unset any existing primary alias for the same `(skill_id, locale)` in a transaction. Insert alias row. Record changelog | `src/main/java/com/skillsgraph/service/AliasService.java` |
| 2.3.2 | `listBySkill(skillId, locale?)` | Return all aliases for a skill. If `locale` param provided, filter by locale. Order by `is_primary DESC, surface_form ASC` | `src/main/java/com/skillsgraph/service/AliasService.java` |
| 2.3.3 | `delete(aliasId)` | Delete alias by ID. Prevent deletion of the last `is_primary = true` alias for any locale. Throw `ValidationException` if attempted. Record changelog | `src/main/java/com/skillsgraph/service/AliasService.java` |
| 2.3.4 | `update(aliasId, input)` | Update alias fields (surface_form, locale, is_primary). Handle primary promotion/demotion in transaction | `src/main/java/com/skillsgraph/service/AliasService.java` |

### Checklist

- [ ] `create()` — inserts alias and records changelog
- [ ] `create()` with `is_primary: true` — demotes previous primary for same skill+locale
- [ ] `listBySkill()` — returns all aliases, primary first
- [ ] `listBySkill(id, "vi")` — filters to Vietnamese aliases only
- [ ] `delete()` — works for non-primary aliases
- [ ] `delete()` — blocks deletion of last primary alias (returns 422)
- [ ] Creating a skill auto-creates a primary `en` alias (from Phase 2.2.1)

---

## 2.4 Edge Service

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 2.4.1 | `create(input)` | Validate input via `createEdgeSchema`. Run quality guardrails (§2.5) BEFORE insert. Use Cypher to create the typed relationship: `MATCH (source:Skill {id: $sourceId}), (target:Skill {id: $targetId}) CREATE (source)-[:PARENT_OF {id: $id, confidence: $confidence, ...}]->(target)`. Record changelog. Return created edge | `src/main/java/com/skillsgraph/service/EdgeService.java` |
| 2.4.2 | `getBySkill(skillId)` | Return all edges where skill is source or target. Group by `relationship_type`: `{ parent_of: [...], child_of: [...], related_to: [...], requires: [...] }` | `src/main/java/com/skillsgraph/service/EdgeService.java` |
| 2.4.3 | `getRelated(skillId)` | Return skills connected via `related_to` and `requires` edges. Include the edge metadata (confidence, provenance) | `src/main/java/com/skillsgraph/service/EdgeService.java` |
| 2.4.4 | `deprecate(edgeId)` | Set edge `status = 'deprecated'`. Record changelog | `src/main/java/com/skillsgraph/service/EdgeService.java` |
| 2.4.5 | `delete(edgeId)` | Hard delete an edge. Record changelog. Only allow if edge status is `pending_review` or `rejected` | `src/main/java/com/skillsgraph/service/EdgeService.java` |

### Checklist

- [ ] `create()` — inserts edge after guardrails pass
- [ ] `create()` — rejects self-edges (source = target)
- [ ] `create()` — rejects duplicate edges (same source+target+type)
- [ ] `create()` — rejects edges that would create cycles (for `parent_of`)
- [ ] `getBySkill()` — returns edges grouped by relationship_type
- [ ] `getRelated()` — returns connected skills with edge metadata
- [ ] `deprecate()` — sets status to `deprecated`, records changelog
- [ ] `delete()` — only works on `pending_review` or `rejected` edges

---

## 2.5 Quality Guardrails Service

### Context

Guardrails run **before** any node or edge is committed. They enforce data integrity rules that go beyond simple DB constraints: cycle detection in the DAG, semantic duplicate detection, orphan prevention, and name sanitization.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 2.5.1 | Cycle detection | `checkCycle(sourceId, targetId, type)` — only applies to `PARENT_OF` relationships. Use Cypher: `MATCH path = (target:Skill {id: $targetId})-[:PARENT_OF*1..10]->(source:Skill {id: $sourceId}) RETURN count(path) > 0 AS wouldCreateCycle`. Return `{ valid: boolean, cyclePath?: string[] }` | `src/main/java/com/skillsgraph/service/GuardrailService.java` |
| 2.5.2 | Self-edge prevention | `checkSelfEdge(sourceId, targetId)` — return error if same. Technically also enforced by CHECK constraint, but checking in code gives a better error message | `src/main/java/com/skillsgraph/service/GuardrailService.java` |
| 2.5.3 | Duplicate edge prevention | `checkDuplicateEdge(sourceId, targetId, type)` — query existing edges. Return error if duplicate found | `src/main/java/com/skillsgraph/service/GuardrailService.java` |
| 2.5.4 | Orphan check | `checkOrphan(skillId)` — when activating a skill (`status → active`), verify at least one `parent_of` edge targets it (unless it's a root category). Return `{ valid: boolean, isRoot: boolean }` | `src/main/java/com/skillsgraph/service/GuardrailService.java` |
| 2.5.5 | Name sanitization | `sanitizeName(name: string): string` — strip HTML tags, normalize Unicode to NFC, trim whitespace, collapse multiple spaces. Return cleaned string | `src/main/java/com/skillsgraph/service/GuardrailService.java` |
| 2.5.6 | Run all edge guardrails | `validateEdge(input)` — orchestrator that runs self-edge check, duplicate check, and cycle detection. Throws `ValidationException` or `CycleDetectedException` with descriptive message | `src/main/java/com/skillsgraph/service/GuardrailService.java` |

### Cycle Detection Algorithm

```java
async function checkCycle(sourceId: string, targetId: string): Promise<CycleCheckResult> {
  // If adding edge "sourceId parent_of targetId",
  // check if targetId is already an ancestor of sourceId
  // (which would create: sourceId -> ... -> targetId -> sourceId)

  const visited = new Set<string>();
  const queue: Array<{ id: string; path: string[] }> = [
    { id: sourceId, path: [sourceId] }
  ];

  while (queue.length > 0) {
    const { id, path } = queue.shift()!;
    if (id === targetId) {
      return { valid: false, cyclePath: [...path, targetId] };
    }
    if (visited.has(id)) continue;
    visited.add(id);

    // Get parents of current node
    // Cypher equivalent via Neo4jTemplate:
    // MATCH path = (target:Skill {id: $targetId})-[:PARENT_OF*1..10]->(source:Skill {id: $sourceId})
    // RETURN count(path) > 0 AS wouldCreateCycle
    const parents = await neo4jTemplate.findAll(
      "MATCH (n:Skill {id: $id})<-[:PARENT_OF]-(parent:Skill) RETURN parent",
      Map.of("id", id), Skill.class
    );

    for (const parent of parents) {
      queue.push({ id: parent.source_skill_id, path: [...path, parent.source_skill_id] });
    }
  }

  return { valid: true };
}
```

### Checklist

- [ ] `checkCycle()` — detects simple cycle: A → B → A
- [ ] `checkCycle()` — detects transitive cycle: A → B → C → A
- [ ] `checkCycle()` — allows valid edges in a DAG (no false positives)
- [ ] `checkCycle()` — handles diamond DAG correctly (A → B, A → C, B → D, C → D)
- [ ] `checkCycle()` — returns the cycle path for debugging
- [ ] `checkSelfEdge()` — rejects source = target
- [ ] `checkDuplicateEdge()` — rejects existing (source, target, type) combination
- [ ] `checkOrphan()` — returns valid for skills with at least one parent
- [ ] `checkOrphan()` — returns valid for root categories (no parent needed)
- [ ] `checkOrphan()` — returns invalid for non-root skills with no parent
- [ ] `sanitizeName()` — strips `<b>bold</b>` → `bold`
- [ ] `sanitizeName()` — normalizes `café` (composed) and `café` (decomposed) to same form
- [ ] `sanitizeName()` — trims whitespace and collapses multiple spaces
- [ ] `validateEdge()` — runs all checks and throws first failure

---

## 2.6 Changelog Service

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 2.6.1 | `record(params)` | After writing mutations to Neo4j, insert row into `graph_changelog` (PostgreSQL). Get next `graph_version` from `nextval('graph_version_seq')`. Accept: `actor`, `mutation_type`, `entity_type`, `entity_id`, `diff_payload` (JSONB). Fire `pg_notify('graph_changes', json)` for CDC consumers | `src/main/java/com/skillsgraph/service/ChangelogService.java` |
| 2.6.2 | `list(since?, limit?)` | Paginated listing of changelog entries where `graph_version > since`. Order by `graph_version ASC`. Default limit `CHANGELOG_DEFAULT_LIMIT` (see `src/main/java/com/skillsgraph/config/AppConstants.java`) | `src/main/java/com/skillsgraph/service/ChangelogService.java` |
| 2.6.3 | `getVersion()` | Return current graph version (latest `graph_version` from changelog). If no entries, return 0. Also return `total_skills`, `total_edges` counts | `src/main/java/com/skillsgraph/service/ChangelogService.java` |
| 2.6.4 | `MutationType` enum | Define all valid mutation types: `skill_created`, `skill_updated`, `skill_deprecated`, `skill_merged`, `alias_added`, `alias_removed`, `edge_created`, `edge_updated`, `edge_deprecated` | `src/main/java/com/skillsgraph/service/ChangelogService.java` |

### Checklist

- [ ] `record()` — inserts changelog entry with auto-incremented version
- [ ] `record()` — fires PG NOTIFY on `graph_changes` channel
- [ ] `list()` — returns paginated entries ordered by version
- [ ] `list(since: 5)` — only returns entries with version > 5
- [ ] `getVersion()` — returns current version number
- [ ] `getVersion()` — returns 0 when changelog is empty
- [ ] Versions are monotonically increasing (never decrease, never repeat)

---

## 2.7 Spring @RestController Route Handlers

### Tasks

| # | Route | Method | Service Call | Validation |
|---|---|---|---|---|
| 2.7.1 | `/api/skills` | POST | `SkillService.create()` | `@Valid("json", createSkillSchema)` |
| 2.7.2 | `/api/skills/{id}` | GET | `SkillService.getById()` | Path param (UUID or slug) |
| 2.7.3 | `/api/skills/{id}` | PATCH | `SkillService.update()` | `@Valid("json", updateSkillSchema)` |
| 2.7.4 | `/api/skills` | GET | `SkillService.list()` | `@Valid("query", listSkillsQuerySchema)` |
| 2.7.5 | `/api/skills/{id}/ancestors` | GET | `SkillService.getAncestors()` | Optional `?depth=` |
| 2.7.6 | `/api/skills/{id}/descendants` | GET | `SkillService.getDescendants()` | Optional `?depth=` |
| 2.7.7 | `/api/skills/{id}/related` | GET | `EdgeService.getRelated()` | — |
| 2.7.8 | `/api/skills/{id}/aliases` | POST | `AliasService.create()` | `@Valid("json", createAliasSchema)` |
| 2.7.9 | `/api/skills/{id}/aliases` | GET | `AliasService.listBySkill()` | Optional `?locale=` |
| 2.7.10 | `/api/edges` | POST | `EdgeService.create()` | `@Valid("json", createEdgeSchema)` |
| 2.7.11 | `/api/taxonomy/roots` | GET | `SkillService.getRoots()` — Cypher: `MATCH (s:Skill {status: 'active'}) WHERE NOT ()-[:PARENT_OF]->(s) RETURN s`. Returns top-level category nodes seeded in Phase 1 (Technology, Business, Design, etc.) | — |
| 2.7.12 | `/api/taxonomy/version` | GET | `ChangelogService.getVersion()` | — |
| 2.7.13 | `/api/taxonomy/changelog` | GET | `ChangelogService.list()` | `?since=`, `?limit=` |

### Checklist

- [ ] All 13 routes respond with correct HTTP status codes
- [ ] `POST /api/skills` — 201 on success with created skill
- [ ] `POST /api/skills` — 400 on validation error (missing canonical_name)
- [ ] `GET /api/skills/:id` — 200 with full skill (aliases + relationships)
- [ ] `GET /api/skills/:id` — 404 for non-existent id
- [ ] `PATCH /api/skills/:id` — 200 with updated skill
- [ ] `GET /api/skills` — 200 with paginated results
- [ ] `GET /api/skills?q=machine` — returns fuzzy matched skills
- [ ] `GET /api/skills/:id/ancestors` — 200 with ancestor chain
- [ ] `GET /api/skills/:id/descendants` — 200 with descendants
- [ ] `POST /api/skills/:id/aliases` — 201 on success
- [ ] `POST /api/edges` — 201 on success
- [ ] `POST /api/edges` — 422 on cycle detection with descriptive error
- [ ] `GET /api/taxonomy/roots` — returns root category skills
- [ ] `GET /api/taxonomy/version` — returns current graph version
- [ ] `GET /api/taxonomy/changelog?since=0` — returns all changelog entries
- [ ] All responses include proper `Content-Type: application/json`
- [ ] All mutation endpoints record changelog entries

---

## Phase 2 Completion Verification

```bash
# Create root categories (should already exist from seed)
curl http://localhost:8080/api/taxonomy/roots | jq
# → [{"canonical_name":"Technology",...}, {"canonical_name":"Business",...}, ...]

# Create skill hierarchy
TECH_ID=$(curl -s -X POST http://localhost:8080/api/skills \
  -H "Content-Type: application/json" \
  -d '{"canonical_name":"Data Science","category":"domain","description":"Field combining statistics and programming","path":"technology.data_science"}' | jq -r '.id')

ML_ID=$(curl -s -X POST http://localhost:8080/api/skills \
  -H "Content-Type: application/json" \
  -d '{"canonical_name":"Machine Learning","category":"domain","description":"Subset of AI","path":"technology.data_science.machine_learning"}' | jq -r '.id')

# Create parent_of edge
curl -X POST http://localhost:8080/api/edges \
  -H "Content-Type: application/json" \
  -d "{\"source_skill_id\":\"$TECH_ID\",\"target_skill_id\":\"$ML_ID\",\"relationship_type\":\"parent_of\"}"

# Test cycle detection
curl -X POST http://localhost:8080/api/edges \
  -H "Content-Type: application/json" \
  -d "{\"source_skill_id\":\"$ML_ID\",\"target_skill_id\":\"$TECH_ID\",\"relationship_type\":\"parent_of\"}"
# → 422 {"error":"Cycle detected"}

# Add alias
curl -X POST "http://localhost:8080/api/skills/$ML_ID/aliases" \
  -H "Content-Type: application/json" \
  -d '{"surface_form":"ML","locale":"en"}'

curl -X POST "http://localhost:8080/api/skills/$ML_ID/aliases" \
  -H "Content-Type: application/json" \
  -d '{"surface_form":"Học máy","locale":"vi","is_primary":true}'

# Get skill with all data
curl "http://localhost:8080/api/skills/$ML_ID" | jq
# → full skill with aliases [ML, Machine Learning, Học máy] and relationships

# Get ancestors
curl "http://localhost:8080/api/skills/$ML_ID/ancestors" | jq
# → [{"canonical_name":"Data Science","depth":1}]

# Fuzzy search
curl "http://localhost:8080/api/skills?q=mahcine+lerning" | jq
# → returns "Machine Learning" via trigram matching

# Changelog
curl "http://localhost:8080/api/taxonomy/changelog?since=0" | jq
# → all mutation entries

./mvnw test
echo "Phase 2 complete ✓"
```

---

## Phase 2 Master Checklist

### 2.1 Shared Jakarta Bean Validation Schemas
- [ ] All enum types defined and exported
- [ ] Create/update/response schemas for skills, aliases, edges
- [ ] Query/pagination schemas defined
- [ ] Java types inferable from all schemas

### 2.2 Skill CRUD Service
- [ ] `create()` with auto external_id, slug, initial alias, changelog
- [ ] `getById()` with joined aliases and relationships
- [ ] `update()` with partial update, version increment, changelog
- [ ] `list()` with pagination, filtering, fuzzy search
- [ ] `getAncestors()` with recursive CTE
- [ ] `getDescendants()` with recursive CTE and depth limit
- [ ] `getRoots()` returns top-level skills with no incoming `parent_of` edges

### 2.3 Alias Service
- [ ] `create()` with primary enforcement (only one primary per skill+locale)
- [ ] `listBySkill()` with optional locale filter
- [ ] `delete()` with last-primary protection

### 2.4 Edge Service
- [ ] `create()` with guardrails integration
- [ ] `getBySkill()` grouped by relationship_type
- [ ] `getRelated()` for related_to and requires neighbors
- [ ] `deprecate()` with changelog

### 2.5 Quality Guardrails
- [ ] Cycle detection (BFS/DFS) with path reporting
- [ ] Self-edge prevention
- [ ] Duplicate edge prevention
- [ ] Orphan check for non-root skills
- [ ] Name sanitization (HTML, Unicode, whitespace)

### 2.6 Changelog Service
- [ ] `record()` with auto version and PG NOTIFY
- [ ] `list()` with `since` filter and pagination
- [ ] `getVersion()` returns current version + counts

### 2.7 Route Handlers
- [ ] All 13 endpoints implemented and responding
- [ ] Proper HTTP status codes (201 created, 400 validation, 404 not found, 409 conflict, 422 unprocessable)
- [ ] All mutations record changelog entries
- [ ] Integration tests pass for full lifecycle
