# Phase 6: Graph Maintenance & Lifecycle

> **Timeline:** Week 12
> **Dependencies:** Phase 2 (CRUD API)
> **Unlocks:** Phase 7 (Workers & Events)
> **Context:** Skills are never hard-deleted. Empirical co-occurrence data must be handled during merge/deprecation.

---

## Goal

Implement deprecation, merging, versioning, and snapshot features. Skills are never hard-deleted — they transition through lifecycle states (`active` → `deprecated` / `merged`). Every mutation is tracked in the changelog for CDC consumers.

---

## 6.1 Skill Deprecation

### Context

When a skill becomes obsolete (e.g., "Adobe Flash"), it is deprecated with a pointer to its successor(s). Aliases are remapped, downstream consumers receive deprecation events, and the skill remains queryable (for historical data) but excluded from extraction and search. Co-occurrence data for the deprecated skill is preserved as-is for historical analysis.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 6.1.1 | `SkillService.deprecate(id, successorIds)` | Transaction: (1) Validate skill is `active`. (2) `SET s.status = 'deprecated'` in Neo4j via Cypher. (3) `CREATE (s)-[:SUPERSEDED_BY]->(successor)` in Neo4j for each successor. (4) Remap aliases: `MATCH (s:Skill {id:$id})-[r:HAS_ALIAS]->(a:Alias), (successor:Skill {id: $successorId}) DELETE r CREATE (successor)-[:HAS_ALIAS]->(a)`. (5) Record changelog entries in PostgreSQL `graph_changelog`. (6) Fire `pg_notify('graph_changes', json)`. (7) Remove from full-text search service index | `src/main/java/com/skillsgraph/service/lifecycle/DeprecationService.java` |
| 6.1.2 | Deprecation validation | Reject if: skill already deprecated/merged, no successor_ids provided, successor doesn't exist, successor is also deprecated/merged. Return descriptive error | `src/main/java/com/skillsgraph/service/lifecycle/DeprecationService.java` |
| 6.1.3 | `POST /api/skills/:id/deprecate` | Accept `{ successor_ids: UUID[], notes?: string }`. Run deprecation. Return updated skill with new status and superseded_by edges | `src/main/java/com/skillsgraph/com.sk.skillsgraph.controller/SkillController.java` |

### Checklist

- [ ] `deprecate()` sets status to `deprecated`
- [ ] `deprecate()` creates `superseded_by` edges to all successors
- [ ] `deprecate()` remaps aliases from deprecated skill to first successor
- [ ] `deprecate()` records changelog entries (skill_deprecated, alias remapping, edge creation)
- [ ] `deprecate()` fires PG NOTIFY for CDC consumers
- [ ] `deprecate()` calls `searchService.removeSkill(id)` (sets `search_vector = NULL`)
- [ ] Validation: rejects already-deprecated skill (409)
- [ ] Validation: rejects if no successor_ids provided (400)
- [ ] Validation: rejects if successor is deprecated/merged (422)
- [ ] Deprecated skill is still queryable via `GET /api/skills/:id`
- [ ] Deprecated skill does NOT appear in `GET /api/skills?status=active`
- [ ] Deprecated skill does NOT appear in search results
- [ ] Deprecated skill does NOT appear in extraction candidates (pgvector query filters by status=active)
- [ ] Co-occurrence data for deprecated skill is preserved (not deleted) for historical analysis
- [ ] All operations in single transaction — rollback on any failure

---

## 6.2 Skill Merging

### Context

When two skills are discovered to be duplicates (e.g., "Machine Learning" and "ML"), they are merged. One is chosen as the survivor, the other as the source. All aliases, edges, references, and **co-occurrence data** are transferred to the survivor.

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 6.2.1 | `SkillService.merge(sourceId, targetId)` | Full transactional merge using Cypher: (1) Validate both skills. (2) Move all `[:HAS_ALIAS]` relationships from source to survivor. (3) Re-point all outgoing relationships from source to survivor (skip duplicates, use `apoc.merge.relationship`). (4) Re-point all incoming relationships targeting source to survivor (skip duplicates). (5) **Merge `[:CO_OCCURS_WITH]` relationships** (see 6.2.6). (6) `SET source.status = 'merged'`. (7) `CREATE (source)-[:SUPERSEDED_BY]->(survivor)`. (8) Record changelog with full diff in PostgreSQL `graph_changelog`. (9) Fire `pg_notify`. (10) Update full-text search service | `src/main/java/com/skillsgraph/service/lifecycle/MergeService.java` |
| 6.2.2 | Edge deduplication during merge | When re-pointing edges, check if an equivalent edge already exists on the survivor (same target/source + relationship_type). If so, keep the one with higher confidence and delete the other | `src/main/java/com/skillsgraph/service/lifecycle/MergeService.java` |
| 6.2.3 | Post-merge cycle check | After re-pointing edges, run cycle detection on the survivor's edges to ensure the merge didn't introduce cycles | `src/main/java/com/skillsgraph/service/lifecycle/MergeService.java` |
| 6.2.4 | Merge validation | Reject if: source = target (self-merge), source or target doesn't exist, source or target already deprecated/merged | `src/main/java/com/skillsgraph/service/lifecycle/MergeService.java` |
| 6.2.5 | `POST /api/skills/:source/merge/:target` | Run merge. Return merged result: survivor skill with all transferred aliases and edges | `src/main/java/com/skillsgraph/com.sk.skillsgraph.controller/SkillController.java` |
| 6.2.6 | Co-occurrence data merge | During merge, transfer `[:CO_OCCURS_WITH]` relationships from source to survivor in Neo4j. Use `MERGE (survivor)-[existing:CO_OCCURS_WITH]-(partner)` with `ON MATCH SET existing.count = existing.count + r.count`. Delete source's `[:CO_OCCURS_WITH]` relationships after merging | `src/main/java/com/skillsgraph/service/lifecycle/MergeService.java` |

### Merge Cypher Reference

```cypher
// Step 1: Move all aliases from source to survivor
MATCH (source:Skill {id: $sourceId})-[r:HAS_ALIAS]->(alias:Alias), (survivor:Skill {id: $survivorId})
DELETE r
CREATE (survivor)-[:HAS_ALIAS]->(alias);

// Step 2: Re-point outgoing relationships (skip duplicates)
// Uses MERGE pattern; for dynamic rel types, apoc.merge.relationship (APOC Extended) can be used:
// CALL apoc.merge.relationship(survivor, type(r), {}, properties(r), other) YIELD rel
MATCH (source:Skill {id: $sourceId})-[r]->(other:Skill)
WHERE type(r) <> 'SUPERSEDED_BY'
  AND NOT ((:Skill {id: $survivorId})-[x]->(other) WHERE type(x) = type(r))
MATCH (survivor:Skill {id: $survivorId})
CALL apoc.merge.relationship(survivor, type(r), {}, properties(r), other) YIELD rel
DELETE r;

// Step 3: Re-point incoming relationships (skip duplicates)
MATCH (other:Skill)-[r]->(source:Skill {id: $sourceId})
WHERE type(r) <> 'SUPERSEDED_BY'
  AND NOT ((other)-[x]->(:Skill {id: $survivorId}) WHERE type(x) = type(r))
MATCH (survivor:Skill {id: $survivorId})
CALL apoc.merge.relationship(other, type(r), {}, properties(r), survivor) YIELD rel
DELETE r;

// Step 4: Merge CO_OCCURS_WITH data
MATCH (source:Skill {id: $sourceId})-[r:CO_OCCURS_WITH]-(partner:Skill)
MATCH (survivor:Skill {id: $survivorId})
MERGE (survivor)-[existing:CO_OCCURS_WITH]-(partner)
  ON CREATE SET existing.count = r.count, existing.sourceCounts = r.sourceCounts, existing.lastSeenAt = r.lastSeenAt
  ON MATCH SET existing.count = existing.count + r.count, existing.lastSeenAt = datetime()
DELETE r;

// Step 5: Mark source as merged, create SUPERSEDED_BY
MATCH (source:Skill {id: $sourceId}), (survivor:Skill {id: $survivorId})
SET source.status = 'merged', source.updatedAt = datetime()
CREATE (source)-[:SUPERSEDED_BY {createdAt: datetime()}]->(survivor);
```

> **Note:** `apoc.merge.relationship` requires the **APOC Core** library (bundled with Neo4j 5 by default when `NEO4J_PLUGINS: '["apoc"]'` is set in docker-compose; APOC Core is freely available). If APOC is not configured, handle each relationship type explicitly with individual `MERGE` statements. The `graph_changelog` entry is written to PostgreSQL and `pg_notify` is fired after the Neo4j operations complete.

### Checklist

- [ ] `merge()` transfers all aliases from source to survivor
- [ ] `merge()` re-points outgoing edges (source_skill_id) from source to survivor
- [ ] `merge()` re-points incoming edges (target_skill_id) from source to survivor
- [ ] `merge()` deduplicates: skips edges where equivalent already exists on survivor
- [ ] `merge()` deletes orphaned edges left on source after re-pointing
- [ ] `merge()` sets source status to `merged`
- [ ] `merge()` creates `superseded_by` edge from source to survivor
- [ ] `merge()` records comprehensive changelog (alias transfers, edge re-pointing, status change)
- [ ] `merge()` fires PG NOTIFY
- [ ] `merge()` updates full-text search service: removes source, re-indexes survivor
- [ ] `merge()` merges co-occurrence data: re-points rows from source to survivor
- [ ] `merge()` sums co-occurrence counts for overlapping partner skills
- [ ] `merge()` maintains `skill_a_id < skill_b_id` constraint after re-pointing
- [ ] `merge()` deletes orphaned co-occurrence rows left on source
- [ ] Post-merge cycle check passes
- [ ] Validation: rejects self-merge (source = target)
- [ ] Validation: rejects if source is already merged/deprecated
- [ ] Validation: rejects if target is already merged/deprecated
- [ ] All operations in single transaction — rollback on any failure
- [ ] Merged skill still queryable via `GET /api/skills/:id` (shows `superseded_by`)

---

## 6.3 Versioning & Snapshots

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 6.3.1 | Enhanced version endpoint | `GET /api/taxonomy/version` returns `{ graph_version, last_mutation_at, total_skills (active), total_edges (active), total_aliases, supported_locales }` | `src/main/java/com/skillsgraph/com.sk.skillsgraph.controller/TaxonomyController.java` |
| 6.3.2 | PG NOTIFY listener | `src/main/java/com/skillsgraph/service/changelog/PgNotifyListener.java` — subscribe to `graph_changes` channel on PostgreSQL. Neo4j mutations are recorded to `graph_changelog` (PostgreSQL) before `pg_notify` is fired, so this listener captures all graph changes. On notification: (1) invalidate relevant Redis cache keys (`taxonomy:skill:{id}`), (2) publish to Redis Stream for full-text search service sync (Phase 7). Start listener on app boot | `src/main/java/com/skillsgraph/service/changelog/PgNotifyListener.java` |
| 6.3.3 | Snapshot export | `SnapshotCreateRunner` (triggered via `--snapshot-create`) — exports taxonomy as JSON: `{ version, timestamp, skills, relationships, aliases, coOccurrences, localeConfig }`. Writes to `snapshots/skills-graph-v{version}-{date}.json` | `src/main/java/com/skillsgraph/com.sk.skillsgraph.script/SnapshotCreateRunner.java` |
| 6.3.4 | Snapshot import | `SnapshotImportRunner` (triggered via `--snapshot-import`) — truncate all tables, insert snapshot data, rebuild search vector, regenerate embeddings | `src/main/java/com/skillsgraph/com.sk.skillsgraph.script/SnapshotImportRunner.java` |

### Checklist

- [ ] `GET /api/taxonomy/version` returns comprehensive stats
- [ ] PG NOTIFY listener starts on app boot
- [ ] PG NOTIFY listener invalidates Redis cache on skill mutations
- [ ] `SnapshotCreateRunner` exports valid JSON file
- [ ] Snapshot file contains all skills, relationships, aliases, co_occurrences, locale_config
- [ ] Snapshot filename includes version and date
- [ ] `SnapshotImportRunner` restores taxonomy from snapshot
- [ ] Snapshot round-trip: export → wipe → import → verify data matches

---

## Phase 6 Completion Verification

```bash
# Create skills to test lifecycle
FLASH_ID=$(curl -s -X POST http://localhost:8080/api/skills \
  -d '{"canonical_name":"Adobe Flash","category":"tool","path":"technology.web.flash"}' | jq -r '.id')
HTML5_ID=$(curl -s -X POST http://localhost:8080/api/skills \
  -d '{"canonical_name":"HTML5 Animation","category":"tool","path":"technology.web.html5_animation"}' | jq -r '.id')

# Deprecate Flash
curl -X POST "http://localhost:8080/api/skills/$FLASH_ID/deprecate" \
  -d "{\"successor_ids\":[\"$HTML5_ID\"]}" | jq
# → status: deprecated, superseded_by: HTML5 Animation

# Verify Flash not in search
curl "http://localhost:8080/api/skills/search?q=flash" | jq
# → empty or no Flash result

# Create duplicate skills for merge test
ML1_ID=$(curl -s -X POST http://localhost:8080/api/skills \
  -d '{"canonical_name":"ML","category":"com.sk.skillsgraph.domain","path":"technology.ml"}' | jq -r '.id')
curl -X POST "http://localhost:8080/api/skills/$ML1_ID/aliases" \
  -d '{"surface_form":"machine learning","locale":"en"}'

# Merge ML into Machine Learning
ML_ID="<existing-machine-learning-uuid>"
curl -X POST "http://localhost:8080/api/skills/$ML1_ID/merge/$ML_ID" | jq
# → source merged, aliases transferred

# Verify aliases transferred
curl "http://localhost:8080/api/skills/$ML_ID/aliases" | jq
# → includes "ML" alias from merged skill

# Snapshot
./mvnw spring-boot:run -Dspring-boot.run.arguments=--snapshot-create
ls snapshots/
# → skills-graph-v15-2026-02-12.json

# Version
curl http://localhost:8080/api/taxonomy/version | jq
# → { graph_version: 15, total_skills: 12, ... }

./mvnw test -Dtest="*LifecycleTest"
echo "Phase 6 complete ✓"
```

---

## Phase 6 Master Checklist

### 6.1 Deprecation
- [ ] `deprecate()` with status change, superseded_by edges, alias remapping
- [ ] Changelog, PG NOTIFY, full-text search service removal
- [ ] Validation rejects invalid deprecation requests
- [ ] Deprecated skills excluded from search and extraction
- [ ] Co-occurrence data preserved for historical analysis
- [ ] Full transaction with rollback on failure

### 6.2 Merging
- [ ] `merge()` moves `[:HAS_ALIAS]` relationships from source to survivor (Cypher)
- [ ] `merge()` re-points outgoing and incoming relationships using `apoc.merge.relationship`
- [ ] `merge()` merges `[:CO_OCCURS_WITH]` data (MERGE + count accumulation)
- [ ] Source marked as `merged` with `[:SUPERSEDED_BY]` relationship
- [ ] Post-merge cycle check
- [ ] Changelog with comprehensive diff
- [ ] Validation rejects self-merge and invalid targets

### 6.3 Versioning & Snapshots
- [ ] Enhanced version endpoint with stats
- [ ] PG NOTIFY listener for cache invalidation
- [ ] `./mvnw spring-boot:run -Dspring-boot.run.arguments=--snapshot-create` exports valid snapshot (includes Neo4j graph data: nodes + relationships)
- [ ] `./mvnw spring-boot:run -Dspring-boot.run.arguments=--snapshot-import` restores from snapshot
