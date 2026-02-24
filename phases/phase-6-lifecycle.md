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
| 6.1.1 | `SkillService.deprecate(id, successorIds)` | Transaction: (1) Validate skill is `active`. (2) Set `status = 'deprecated'`. (3) Create `superseded_by` edge(s) to successor(s). (4) Remap all aliases to first successor (update `skill_id`). (5) Record changelog entries for each mutation. (6) Fire PG NOTIFY. (7) Remove from Typesense index | `src/services/lifecycle/deprecation.ts` |
| 6.1.2 | Deprecation validation | Reject if: skill already deprecated/merged, no successor_ids provided, successor doesn't exist, successor is also deprecated/merged. Return descriptive error | `src/services/lifecycle/deprecation.ts` |
| 6.1.3 | `POST /api/skills/:id/deprecate` | Accept `{ successor_ids: UUID[], notes?: string }`. Run deprecation. Return updated skill with new status and superseded_by edges | `src/routes/skills.ts` |

### Checklist

- [ ] `deprecate()` sets status to `deprecated`
- [ ] `deprecate()` creates `superseded_by` edges to all successors
- [ ] `deprecate()` remaps aliases from deprecated skill to first successor
- [ ] `deprecate()` records changelog entries (skill_deprecated, alias remapping, edge creation)
- [ ] `deprecate()` fires PG NOTIFY for CDC consumers
- [ ] `deprecate()` removes skill from Typesense search index
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
| 6.2.1 | `SkillService.merge(sourceId, targetId)` | Full transactional merge: (1) Validate both skills. (2) Move all aliases from source to survivor. (3) Re-point all edges where source is the `source_skill_id` to survivor (skip if equivalent edge exists). (4) Re-point all edges where source is the `target_skill_id` to survivor (skip if equivalent edge exists). (5) Delete orphaned duplicate edges. (6) **Merge co-occurrence data** (see 6.2.6). (7) Set source `status = 'merged'`. (8) Create `superseded_by` edge from source to survivor. (9) Record changelog with full diff. (10) Fire PG NOTIFY. (11) Update Typesense: remove source, re-index survivor with merged aliases | `src/services/lifecycle/merge.ts` |
| 6.2.2 | Edge deduplication during merge | When re-pointing edges, check if an equivalent edge already exists on the survivor (same target/source + relationship_type). If so, keep the one with higher confidence and delete the other | `src/services/lifecycle/merge.ts` |
| 6.2.3 | Post-merge cycle check | After re-pointing edges, run cycle detection on the survivor's edges to ensure the merge didn't introduce cycles | `src/services/lifecycle/merge.ts` |
| 6.2.4 | Merge validation | Reject if: source = target (self-merge), source or target doesn't exist, source or target already deprecated/merged | `src/services/lifecycle/merge.ts` |
| 6.2.5 | `POST /api/skills/:source/merge/:target` | Run merge. Return merged result: survivor skill with all transferred aliases and edges | `src/routes/skills.ts` |
| 6.2.6 | Co-occurrence data merge | During merge, transfer co-occurrence data from source to survivor in `skill_co_occurrences` table. Re-point `skill_a_id`/`skill_b_id` references from source to survivor. Where both source and survivor have co-occurrence rows with the same partner skill, sum the counts and merge `source_type_counts` JSONB. Delete orphaned rows. Maintain `CHECK (skill_a_id < skill_b_id)` constraint by swapping if needed | `src/services/lifecycle/merge.ts` |

### Merge SQL Reference

```sql
BEGIN;
  -- Move aliases
  UPDATE skill_aliases SET skill_id = $survivor WHERE skill_id = $source;

  -- Re-point edges (source side): source was the origin
  UPDATE skill_relationships SET source_skill_id = $survivor
  WHERE source_skill_id = $source
  AND NOT EXISTS (
    SELECT 1 FROM skill_relationships existing
    WHERE existing.source_skill_id = $survivor
    AND existing.target_skill_id = skill_relationships.target_skill_id
    AND existing.relationship_type = skill_relationships.relationship_type
  );

  -- Re-point edges (target side): source was the destination
  UPDATE skill_relationships SET target_skill_id = $survivor
  WHERE target_skill_id = $source
  AND NOT EXISTS (
    SELECT 1 FROM skill_relationships existing
    WHERE existing.target_skill_id = $survivor
    AND existing.source_skill_id = skill_relationships.source_skill_id
    AND existing.relationship_type = skill_relationships.relationship_type
  );

  -- Clean up orphaned edges (duplicates that couldn't be re-pointed)
  DELETE FROM skill_relationships
  WHERE source_skill_id = $source OR target_skill_id = $source;

  -- Mark source as merged
  UPDATE skills SET status = 'merged', updated_at = now() WHERE id = $source;

  -- Create superseded_by edge
  INSERT INTO skill_relationships (source_skill_id, target_skill_id, relationship_type, provenance)
  VALUES ($source, $survivor, 'superseded_by', 'human_curated');

  -- Merge co-occurrence data: re-point source → survivor
  -- For rows where source is skill_a_id
  UPDATE skill_co_occurrences SET skill_a_id = $survivor
  WHERE skill_a_id = $source
  AND NOT EXISTS (
    SELECT 1 FROM skill_co_occurrences existing
    WHERE existing.skill_a_id = LEAST($survivor, skill_co_occurrences.skill_b_id)
    AND existing.skill_b_id = GREATEST($survivor, skill_co_occurrences.skill_b_id)
  );

  -- For rows where source is skill_b_id
  UPDATE skill_co_occurrences SET skill_b_id = $survivor
  WHERE skill_b_id = $source
  AND NOT EXISTS (
    SELECT 1 FROM skill_co_occurrences existing
    WHERE existing.skill_a_id = LEAST(skill_co_occurrences.skill_a_id, $survivor)
    AND existing.skill_b_id = GREATEST(skill_co_occurrences.skill_a_id, $survivor)
  );

  -- For duplicate co-occurrence rows (both source and survivor have data with same partner),
  -- sum counts into survivor's row and delete source's row
  -- (handled programmatically — query overlapping pairs, merge counts, delete source rows)

  -- Clean up any remaining co-occurrence rows referencing source
  DELETE FROM skill_co_occurrences
  WHERE skill_a_id = $source OR skill_b_id = $source;

  -- Fix ordering constraint: ensure skill_a_id < skill_b_id after re-pointing
  UPDATE skill_co_occurrences
  SET skill_a_id = skill_b_id, skill_b_id = skill_a_id
  WHERE skill_a_id > skill_b_id;
COMMIT;
```

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
- [ ] `merge()` updates Typesense: removes source, re-indexes survivor
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
| 6.3.1 | Enhanced version endpoint | `GET /api/taxonomy/version` returns `{ graph_version, last_mutation_at, total_skills (active), total_edges (active), total_aliases, supported_locales }` | `src/routes/taxonomy.ts` |
| 6.3.2 | PG NOTIFY listener | `src/services/changelog/listener.ts` — subscribe to `graph_changes` channel on PostgreSQL. On notification: (1) invalidate relevant Redis cache keys (`taxonomy:skill:{id}`), (2) publish to Redis Stream for Typesense sync (Phase 7). Start listener on app boot | `src/services/changelog/listener.ts` |
| 6.3.3 | Snapshot export | `bun run snapshot:create` — export entire taxonomy as JSON: `{ version, timestamp, skills: [...], relationships: [...], aliases: [...], co_occurrences: [...], locale_config: [...] }`. Write to `snapshots/skills-graph-v{version}-{date}.json` | `src/scripts/snapshot-create.ts` |
| 6.3.4 | Snapshot import | `bun run snapshot:import <file>` — import a snapshot to restore taxonomy state. Truncate all tables (including `skill_co_occurrences`), insert snapshot data, rebuild Typesense index, regenerate embeddings | `src/scripts/snapshot-import.ts` |

### Checklist

- [ ] `GET /api/taxonomy/version` returns comprehensive stats
- [ ] PG NOTIFY listener starts on app boot
- [ ] PG NOTIFY listener invalidates Redis cache on skill mutations
- [ ] `bun run snapshot:create` exports valid JSON file
- [ ] Snapshot file contains all skills, relationships, aliases, co_occurrences, locale_config
- [ ] Snapshot filename includes version and date
- [ ] `bun run snapshot:import` restores taxonomy from snapshot
- [ ] Snapshot round-trip: export → wipe → import → verify data matches

---

## Phase 6 Completion Verification

```bash
# Create skills to test lifecycle
FLASH_ID=$(curl -s -X POST http://localhost:3000/api/skills \
  -d '{"canonical_name":"Adobe Flash","category":"tool","path":"technology.web.flash"}' | jq -r '.id')
HTML5_ID=$(curl -s -X POST http://localhost:3000/api/skills \
  -d '{"canonical_name":"HTML5 Animation","category":"tool","path":"technology.web.html5_animation"}' | jq -r '.id')

# Deprecate Flash
curl -X POST "http://localhost:3000/api/skills/$FLASH_ID/deprecate" \
  -d "{\"successor_ids\":[\"$HTML5_ID\"]}" | jq
# → status: deprecated, superseded_by: HTML5 Animation

# Verify Flash not in search
curl "http://localhost:3000/api/skills/search?q=flash" | jq
# → empty or no Flash result

# Create duplicate skills for merge test
ML1_ID=$(curl -s -X POST http://localhost:3000/api/skills \
  -d '{"canonical_name":"ML","category":"domain","path":"technology.ml"}' | jq -r '.id')
curl -X POST "http://localhost:3000/api/skills/$ML1_ID/aliases" \
  -d '{"surface_form":"machine learning","locale":"en"}'

# Merge ML into Machine Learning
ML_ID="<existing-machine-learning-uuid>"
curl -X POST "http://localhost:3000/api/skills/$ML1_ID/merge/$ML_ID" | jq
# → source merged, aliases transferred

# Verify aliases transferred
curl "http://localhost:3000/api/skills/$ML_ID/aliases" | jq
# → includes "ML" alias from merged skill

# Snapshot
bun run snapshot:create
ls snapshots/
# → skills-graph-v15-2026-02-12.json

# Version
curl http://localhost:3000/api/taxonomy/version | jq
# → { graph_version: 15, total_skills: 12, ... }

bun test src/services/lifecycle/
echo "Phase 6 complete ✓"
```

---

## Phase 6 Master Checklist

### 6.1 Deprecation
- [ ] `deprecate()` with status change, superseded_by edges, alias remapping
- [ ] Changelog, PG NOTIFY, Typesense removal
- [ ] Validation rejects invalid deprecation requests
- [ ] Deprecated skills excluded from search and extraction
- [ ] Co-occurrence data preserved for historical analysis
- [ ] Full transaction with rollback on failure

### 6.2 Merging
- [ ] `merge()` transfers aliases, re-points edges, handles deduplication
- [ ] `merge()` merges co-occurrence data (re-point, sum counts, maintain ordering constraint)
- [ ] Source marked as `merged` with `superseded_by` edge
- [ ] Post-merge cycle check
- [ ] Changelog with comprehensive diff
- [ ] Validation rejects self-merge and invalid targets

### 6.3 Versioning & Snapshots
- [ ] Enhanced version endpoint with stats
- [ ] PG NOTIFY listener for cache invalidation
- [ ] `bun run snapshot:create` exports valid snapshot (includes `skill_co_occurrences` data)
- [ ] `bun run snapshot:import` restores from snapshot
