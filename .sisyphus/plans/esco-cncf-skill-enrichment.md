# ESCO + CNCF Skill Enrichment

## TL;DR
> **Summary**: Add a deterministic, idempotent seed/import workflow that enriches the existing `Skill` graph from ESCO and CNCF only, focused on aliases, taxonomy, and relation enrichment without introducing `Occupation` nodes.
> **Deliverables**:
> - ESCO bootstrap + normalization + import workflow
> - CNCF landscape normalization + import workflow
> - Skill model/index updates for external IDs and provenance
> - Relation enrichment rules for `IS_A` and `RELATED_TO`
> - Seed script / CLI entrypoint
> - Pytest unit + integration coverage and rerun-idempotency checks
> **Effort**: Medium
> **Parallel**: YES - 2 waves
> **Critical Path**: 1 -> 2 -> 3 -> 4 -> 7

## Context
### Original Request
Use fetch and tell me how to enrich the graph with external global dataset, no api key require. Then narrow to ESCO and CNCF only, no Occupation nodes, enrich relations, and include a script to seed the graph toward taxonomy + aliases and tool enrichment.

### Interview Summary
- Keep the graph `Skill`-only.
- Use only ESCO and CNCF as external sources.
- Prioritize taxonomy + aliases; fold tool/product enrichment into the same workflow.
- Enrich `Skill`-to-`Skill` relations rather than creating new core entity types.
- Use `Tests After` as the verification strategy.

### Metis Review (gaps addressed)
- Default ESCO acquisition to a manual bootstrap directory because the public download flow is not reliably unattended.
- Use deterministic matching only: exact normalized name plus a curated alias map generated from source data; no fuzzy or LLM matching.
- Preserve existing user-created skills and relationships by default; only upsert missing source-managed properties/edges or refresh properties already tagged as source-managed.
- Keep provenance on nodes and relationships as properties rather than introducing new entity types in v1.

## Work Objectives
### Core Objective
Implement a seed/import workflow that ingests ESCO taxonomy data and CNCF landscape tool data into the existing Neo4j/neomodel `Skill` graph, enriching aliases and `IS_A` / `RELATED_TO` relations in a rerun-safe way.

### Deliverables
- Python import modules for ESCO and CNCF parsing, normalization, mapping, and persistence.
- Schema/model updates to support stable source identifiers and provenance metadata on `Skill` and imported relationships.
- A dedicated seed/import script entrypoint separate from `main.py`.
- Fixture datasets and pytest coverage for normalization, persistence, and idempotent reruns.
- Verification commands and graph assertions for smoke and integration checks.

### Definition of Done (verifiable conditions with commands)
- `uv run pytest tests/unit -q` passes.
- `uv run pytest tests/integration -q` passes.
- `uv run python scripts/seed_skills.py --source esco --input data/raw/esco` completes successfully against local Neo4j.
- `uv run python scripts/seed_skills.py --source cncf --input data/raw/cncf/landscape.yml` completes successfully against local Neo4j.
- Running both imports twice yields unchanged imported node and edge counts.
- Cypher assertions confirm imported `Skill` nodes have source metadata and imported relationships have provenance + required properties.

### Must Have
- No API keys or authenticated services.
- No `Occupation` nodes or occupation-derived logic.
- Deterministic merge rules and non-destructive reruns.
- ESCO as canonical taxonomy/alias source.
- CNCF as supplemental tool-skill source.
- Machine-verifiable pytest + Cypher assertions.

### Must NOT Have (guardrails, AI slop patterns, scope boundaries)
- No fuzzy matching, embeddings, LLM mapping, or heuristic web scraping.
- No UI, API server, scheduler, or background job system.
- No new relationship families beyond existing `IS_A`, `REQUIRES`, `RELATED_TO` unless explicitly approved later.
- No overwriting of existing manually curated properties/edges unless they are already source-managed for the same source.
- No assumption of a stable unattended ESCO download URL; v1 uses a manual bootstrap directory.

## Verification Strategy
> ZERO HUMAN INTERVENTION - all verification is agent-executed.
- Test decision: tests-after + `pytest`
- QA policy: every task includes executable happy-path and failure-path scenarios
- Evidence: `.sisyphus/evidence/task-{N}-{slug}.{ext}`

## Execution Strategy
### Parallel Execution Waves
> Target: 5-8 tasks per wave. Shared schema + normalization decisions land first for maximum parallelism.

Wave 1: Task 1 schema/provenance, Task 2 fixture/test harness, Task 3 ESCO parser/mapping, Task 4 CNCF parser/mapping, Task 5 import statistics + conflict rules
Wave 2: Task 6 persistence layer, Task 7 seed CLI/script, Task 8 integration/rerun verification, Task 9 smoke + docs alignment

### Dependency Matrix (full, all tasks)
- 1 blocks 3, 4, 6, 7, 8
- 2 blocks 3, 4, 8
- 3 blocks 6, 7, 8
- 4 blocks 6, 7, 8
- 5 blocks 6, 7, 8
- 6 blocks 7, 8, 9
- 7 blocks 8, 9
- 8 blocks 9
- 9 has no downstream blockers except final verification

### Agent Dispatch Summary (wave -> task count -> categories)
- Wave 1 -> 5 tasks -> `unspecified-high`, `quick`
- Wave 2 -> 4 tasks -> `unspecified-high`, `quick`

## TODOs
> Implementation + Test = ONE task. Never separate.
> Every task includes agent profile, references, acceptance criteria, and QA scenarios.

- [ ] 1. Extend the `Skill` schema and index strategy for source-managed imports

  **What to do**: Add source metadata fields to `Skill` for deterministic imports: `source_ids` by source, `source_priority`, `source_license`, `source_version`, `last_imported_at`, and a marker that a node or relationship is source-managed. Update database bootstrap to create indexes/constraints that support merges on imported IDs while preserving existing `name` uniqueness semantics. Decide that `name` remains the display/canonical app field while source IDs are import keys.
  **Must NOT do**: Do not remove `name` uniqueness, do not add `Occupation`, and do not invent provenance nodes in v1.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` - Reason: schema + Neo4j merge semantics affect all downstream work.
  - Skills: [`neo4j-data-models`, `python-best-practices`, `python-type-safety`] - why: graph merge design, Python model updates, typed metadata fields.
  - Omitted: [`neo4j-cypher-guide`] - why: import path is model-first and does not rely on custom read-query generation.

  **Parallelization**: Can Parallel: NO | Wave 1 | Blocks: 3, 4, 6, 7, 8 | Blocked By: none

  **References**:
  - Pattern: `models/skill.py:47` - existing `Skill` model to extend without changing core ontology.
  - Pattern: `db.py:36` - existing index creation pattern to extend for import keys.
  - Pattern: `config.py:13` - dataclass style already used in project for configuration.
  - External: `https://neo4j.com/docs/cypher-manual/current/clauses/load-csv/` - idempotent import design guidance.

  **Acceptance Criteria**:
  - [ ] `uv run pytest tests/unit/test_skill_import_metadata.py -q` passes.
  - [ ] `uv run python -c "from db import INDEX_QUERIES; print(len(INDEX_QUERIES))"` shows new import-related indexes are registered.
  - [ ] `uv run python -c "from models import Skill; print(hasattr(Skill, 'source_ids'))"` prints `True`.

  **QA Scenarios**:
  ```text
  Scenario: Schema exposes deterministic import fields
    Tool: Bash
    Steps: Run `uv run pytest tests/unit/test_skill_import_metadata.py -q`
    Expected: Tests pass and confirm default values, normalization, and required provenance fields.
    Evidence: .sisyphus/evidence/task-1-skill-schema.txt

  Scenario: Missing source metadata fails validation
    Tool: Bash
    Steps: Run `uv run pytest tests/unit/test_skill_import_metadata.py -q -k missing`
    Expected: Test passes by asserting importer/model validation rejects incomplete source-managed payloads.
    Evidence: .sisyphus/evidence/task-1-skill-schema-error.txt
  ```

  **Commit**: YES | Message: `feat(skill): add source-aware import metadata` | Files: `models/skill.py`, `db.py`, `tests/unit/test_skill_import_metadata.py`

- [ ] 2. Add fixture datasets and importer test harness

  **What to do**: Create minimal golden fixtures for ESCO and CNCF under `tests/fixtures/` plus shared pytest helpers for disposable graph setup, fixture loading, and exact Cypher assertions. Include one valid ESCO subset, one valid CNCF subset, one malformed ESCO row missing concept URI, one unsupported CNCF item, and one duplicate alias collision case.
  **Must NOT do**: Do not use live network fetches in tests, and do not depend on the full raw datasets for CI.

  **Recommended Agent Profile**:
  - Category: `quick` - Reason: bounded test harness + fixtures.
  - Skills: [`python-best-practices`, `python-code-style`] - why: clean fixture helpers and test layout.
  - Omitted: [`neo4j-data-models`] - why: task is test harness focused, not schema design.

  **Parallelization**: Can Parallel: YES | Wave 1 | Blocks: 3, 4, 8 | Blocked By: none

  **References**:
  - Pattern: `pyproject.toml:13` - existing pytest dependency availability.
  - Pattern: `Makefile:32` - current smoke-test command entrypoint to complement, not replace.
  - Pattern: `docker-compose.yml:19` - import volume context for later integration tests.

  **Acceptance Criteria**:
  - [ ] `uv run pytest tests/unit/test_fixture_sanity.py -q` passes.
  - [ ] `tests/fixtures/esco_skills_sample.csv` and `tests/fixtures/cncf_landscape_sample.yml` exist and are consumed by tests.
  - [ ] Malformed and unsupported fixture paths are covered by explicit tests.

  **QA Scenarios**:
  ```text
  Scenario: Fixture harness loads deterministic sample data
    Tool: Bash
    Steps: Run `uv run pytest tests/unit/test_fixture_sanity.py -q`
    Expected: Fixture sanity tests pass and confirm stable row counts and required columns/keys.
    Evidence: .sisyphus/evidence/task-2-fixtures.txt

  Scenario: Broken fixture shape is rejected
    Tool: Bash
    Steps: Run `uv run pytest tests/unit/test_fixture_sanity.py -q -k malformed`
    Expected: Test passes by asserting malformed ESCO or CNCF fixture data raises the expected exception.
    Evidence: .sisyphus/evidence/task-2-fixtures-error.txt
  ```

  **Commit**: YES | Message: `test(import): add ESCO and CNCF fixtures` | Files: `tests/fixtures/*`, `tests/conftest.py`, `tests/unit/test_fixture_sanity.py`

- [ ] 3. Implement ESCO normalization and mapping for aliases, hierarchy, and related concepts

  **What to do**: Build a parser/mapper that reads a manually bootstrapped ESCO dataset from `data/raw/esco/`, extracts only skill concepts, normalizes preferred labels and aliases, maps broader/narrower links to `IS_A`, and maps related concepts to `RELATED_TO`. Define deterministic rules for alias casing, dedupe, and collision resolution: exact normalized name match first, then alias match only when unique, otherwise skip and record conflict statistics.
  **Must NOT do**: Do not import occupations, qualifications, or non-skill ESCO concepts.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` - Reason: source parsing and semantic mapping are core business logic.
  - Skills: [`python-best-practices`, `neo4j-cypher`, `python-type-safety`] - why: robust parser design and explicit semantic mapping.
  - Omitted: [`neo4j-driver-python`] - why: this task stops at mapping, not persistence.

  **Parallelization**: Can Parallel: YES | Wave 1 | Blocks: 6, 7, 8 | Blocked By: 1, 2

  **References**:
  - Pattern: `models/skill.py:27` - existing list normalization helper to mirror for aliases.
  - Pattern: `models/is_a_rel.py:8` - required `IS_A` properties to satisfy when mapping hierarchy edges.
  - Pattern: `models/related_to_rel.py:8` - required `RELATED_TO` properties and allowed `relation_type` values.
  - External: `https://esco.ec.europa.eu/en/use-esco/download` - source scope and bootstrap expectations.

  **Acceptance Criteria**:
  - [ ] `uv run pytest tests/unit/test_esco_mapping.py -q` passes.
  - [ ] Mapping tests prove only skill concepts are emitted.
  - [ ] Duplicate alias collision fixture yields deterministic skip/report behavior.

  **QA Scenarios**:
  ```text
  Scenario: ESCO sample maps to aliases and relations correctly
    Tool: Bash
    Steps: Run `uv run pytest tests/unit/test_esco_mapping.py -q`
    Expected: Tests pass with exact expected normalized aliases, `IS_A` edges, and `RELATED_TO` edges from fixture data.
    Evidence: .sisyphus/evidence/task-3-esco-mapping.txt

  Scenario: ESCO row missing concept URI is rejected
    Tool: Bash
    Steps: Run `uv run pytest tests/unit/test_esco_mapping.py -q -k missing_uri`
    Expected: Test passes by asserting a specific validation exception or skipped-row statistic for missing concept identifiers.
    Evidence: .sisyphus/evidence/task-3-esco-mapping-error.txt
  ```

  **Commit**: YES | Message: `feat(import): map ESCO skills and relations` | Files: `scripts/` or `importers/` ESCO modules, `tests/unit/test_esco_mapping.py`

- [ ] 4. Implement CNCF normalization and mapping for tool skills and complementary relations

  **What to do**: Parse `landscape.yml` from a local CNCF bootstrap path, filter only entries that should become tool skills, normalize names/categories, and map them into `Skill` payloads with `skill_type="tool"`. Define conservative relation rules: use category/subcategory similarity for `RELATED_TO` with `relation_type="complementary"`; do not infer `REQUIRES` from CNCF data in v1.
  **Must NOT do**: Do not import vendors/companies as separate nodes and do not treat every landscape item as a skill without passing explicit inclusion rules.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` - Reason: filtering CNCF to skill-like tools requires deterministic curation rules.
  - Skills: [`python-best-practices`, `python-type-safety`] - why: YAML parsing and strict mapping logic.
  - Omitted: [`neo4j-driver-python`] - why: persistence belongs to Task 6.

  **Parallelization**: Can Parallel: YES | Wave 1 | Blocks: 6, 7, 8 | Blocked By: 1, 2

  **References**:
  - Pattern: `models/skill.py:63` - current `skill_type` choices; update or reuse while preserving the `tool` category.
  - Pattern: `models/related_to_rel.py:16` - required relationship fields for complementary tool relations.
  - External: `https://github.com/cncf/landscape` - authoritative source repo.

  **Acceptance Criteria**:
  - [ ] `uv run pytest tests/unit/test_cncf_mapping.py -q` passes.
  - [ ] Unsupported CNCF items are skipped and counted.
  - [ ] Tool entries map to `skill_type="tool"` with stable source IDs.

  **QA Scenarios**:
  ```text
  Scenario: CNCF sample maps tool skills and complementary relations
    Tool: Bash
    Steps: Run `uv run pytest tests/unit/test_cncf_mapping.py -q`
    Expected: Tests pass and assert exact emitted tool skills plus `RELATED_TO` complementary edges from fixture categories.
    Evidence: .sisyphus/evidence/task-4-cncf-mapping.txt

  Scenario: Unsupported CNCF item is skipped deterministically
    Tool: Bash
    Steps: Run `uv run pytest tests/unit/test_cncf_mapping.py -q -k unsupported`
    Expected: Test passes by asserting unsupported entries are excluded and importer stats capture the skip count.
    Evidence: .sisyphus/evidence/task-4-cncf-mapping-error.txt
  ```

  **Commit**: YES | Message: `feat(import): map CNCF tool skills` | Files: `scripts/` or `importers/` CNCF modules, `tests/unit/test_cncf_mapping.py`

- [ ] 5. Define import statistics, conflict resolution, and source precedence rules

  **What to do**: Add a shared import result model that records created/updated/skipped counts, collision reasons, and source precedence decisions. Encode the v1 defaults: ESCO owns taxonomy and aliases; CNCF may create new `tool` skills or enrich existing `tool` skills; manual non-source-managed values are preserved; imported edges tagged with source provenance may be refreshed only by the same source.
  **Must NOT do**: Do not silently overwrite local manual edits or merge ambiguous alias collisions.

  **Recommended Agent Profile**:
  - Category: `quick` - Reason: bounded shared domain rules.
  - Skills: [`python-best-practices`, `python-type-safety`] - why: typed result model and conflict semantics.
  - Omitted: [`neo4j-data-models`] - why: persistence behavior is handled elsewhere.

  **Parallelization**: Can Parallel: YES | Wave 1 | Blocks: 6, 7, 8 | Blocked By: none

  **References**:
  - Pattern: `models/requires_rel.py:8` - relationship property strictness to preserve when future sources appear.
  - Pattern: `README.md:83` - relationship semantics currently documented; preserve vocabulary.

  **Acceptance Criteria**:
  - [ ] `uv run pytest tests/unit/test_import_precedence.py -q` passes.
  - [ ] Precedence tests prove ESCO wins alias/taxonomy conflicts and CNCF cannot downgrade manually curated skills.
  - [ ] Ambiguous alias collisions are logged as skipped, not auto-merged.

  **QA Scenarios**:
  ```text
  Scenario: Source precedence follows documented defaults
    Tool: Bash
    Steps: Run `uv run pytest tests/unit/test_import_precedence.py -q`
    Expected: Tests pass and verify ESCO taxonomy precedence, CNCF tool enrichment rules, and manual-value preservation.
    Evidence: .sisyphus/evidence/task-5-import-precedence.txt

  Scenario: Ambiguous alias collision is skipped
    Tool: Bash
    Steps: Run `uv run pytest tests/unit/test_import_precedence.py -q -k collision`
    Expected: Test passes by asserting the importer records a skipped conflict instead of mutating graph identity.
    Evidence: .sisyphus/evidence/task-5-import-precedence-error.txt
  ```

  **Commit**: YES | Message: `feat(import): add source precedence rules` | Files: shared import result/types module, `tests/unit/test_import_precedence.py`

- [ ] 6. Implement rerun-safe persistence for imported skills and relationships

  **What to do**: Build the persistence layer that upserts `Skill` nodes and imported `IS_A` / `RELATED_TO` edges using deterministic source IDs and source-managed provenance properties. Ensure reruns are idempotent, existing manual relationships are preserved, and imported relationships satisfy existing required properties (`weight`, `level`/`relation_type`, `confidence` where applicable). Use explicit default property values for imported relations and document them in code/tests.
  **Must NOT do**: Do not delete existing graph data, and do not write imported edges without the required neomodel properties.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` - Reason: DB write semantics and idempotency are high-risk.
  - Skills: [`neo4j-driver-python`, `python-best-practices`, `neo4j-data-models`] - why: transaction safety, merge semantics, model alignment.
  - Omitted: [`neo4j-cypher-guide`] - why: direct driver/neomodel writes matter more than read-query tuning.

  **Parallelization**: Can Parallel: NO | Wave 2 | Blocks: 7, 8, 9 | Blocked By: 1, 3, 4, 5

  **References**:
  - Pattern: `db.py:45` - DB setup entrypoint available before import runs.
  - Pattern: `main.py:27` - sample creation path showing current save/connect style.
  - Pattern: `models/is_a_rel.py:17` - required `IS_A` fields.
  - Pattern: `models/related_to_rel.py:16` - required `RELATED_TO` fields.

  **Acceptance Criteria**:
  - [ ] `uv run pytest tests/integration/test_skill_persistence.py -q` passes.
  - [ ] Integration tests prove imported nodes/edges are not duplicated on rerun.
  - [ ] Cypher assertions confirm imported relationships carry provenance and required properties.

  **QA Scenarios**:
  ```text
  Scenario: Persistence creates and updates imported graph data safely
    Tool: Bash
    Steps: Run `uv run pytest tests/integration/test_skill_persistence.py -q`
    Expected: Tests pass and verify exact node/edge counts, required relationship properties, and provenance fields.
    Evidence: .sisyphus/evidence/task-6-skill-persistence.txt

  Scenario: Second import run does not duplicate nodes or edges
    Tool: Bash
    Steps: Run `uv run pytest tests/integration/test_skill_persistence.py -q -k rerun`
    Expected: Test passes by asserting identical counts after two runs of the same fixture import.
    Evidence: .sisyphus/evidence/task-6-skill-persistence-error.txt
  ```

  **Commit**: YES | Message: `feat(import): persist source-managed skills and relations` | Files: persistence modules, `tests/integration/test_skill_persistence.py`

- [ ] 7. Add a dedicated seed script / CLI for ESCO and CNCF bootstrap inputs

  **What to do**: Add a dedicated script such as `scripts/seed_skills.py` that accepts `--source esco|cncf|all`, `--input <path>`, and optional `--dry-run`. The script should initialize the DB, validate the provided local dataset path, invoke the correct importer, print import statistics, and exit non-zero on validation/import failure. Keep it separate from `main.py`.
  **Must NOT do**: Do not overload the smoke test entrypoint in `main.py`, and do not fetch ESCO interactively inside the script in v1.

  **Recommended Agent Profile**:
  - Category: `quick` - Reason: bounded orchestration entrypoint once import layer exists.
  - Skills: [`python-best-practices`, `python-code-style`] - why: CLI ergonomics and clean error handling.
  - Omitted: [`neo4j-data-models`] - why: schema/persistence are already handled.

  **Parallelization**: Can Parallel: NO | Wave 2 | Blocks: 8, 9 | Blocked By: 1, 3, 4, 5, 6

  **References**:
  - Pattern: `main.py:9` - existing top-level executable pattern to mirror without reusing.
  - Pattern: `Makefile:29` - existing command-oriented workflow for local developer usage.
  - Pattern: `config.py:77` - DB config singleton access.

  **Acceptance Criteria**:
  - [ ] `uv run python scripts/seed_skills.py --help` exits 0 and documents required flags.
  - [ ] `uv run python scripts/seed_skills.py --source esco --input data/raw/esco --dry-run` exits 0 with summary output.
  - [ ] `uv run python scripts/seed_skills.py --source all --input data/raw` supports sequential ESCO then CNCF execution.

  **QA Scenarios**:
  ```text
  Scenario: Seed CLI runs dry-run and real-run flows
    Tool: Bash
    Steps: Run `uv run python scripts/seed_skills.py --source esco --input data/raw/esco --dry-run` and `uv run python scripts/seed_skills.py --source cncf --input data/raw/cncf/landscape.yml --dry-run`
    Expected: Both commands exit 0 and print deterministic import summaries without mutating the graph in dry-run mode.
    Evidence: .sisyphus/evidence/task-7-seed-cli.txt

  Scenario: Missing input path fails fast
    Tool: Bash
    Steps: Run `uv run python scripts/seed_skills.py --source esco --input data/raw/missing-esco`
    Expected: Command exits non-zero with a clear validation error naming the missing path.
    Evidence: .sisyphus/evidence/task-7-seed-cli-error.txt
  ```

  **Commit**: YES | Message: `feat(cli): add skill seed entrypoint` | Files: `scripts/seed_skills.py`, related tests

- [ ] 8. Add full integration verification for ESCO + CNCF combined imports

  **What to do**: Add integration tests that seed fixture subsets from both sources into a disposable Neo4j instance, then run exact Cypher assertions for counts, aliases, hierarchy edges, complementary tool relations, provenance, and rerun-idempotency. Include combined-source collision tests proving ESCO taxonomy precedence and CNCF tool enrichment coexist safely.
  **Must NOT do**: Do not rely on manual Neo4j Browser inspection.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` - Reason: integration and graph assertions validate the whole system.
  - Skills: [`neo4j-driver-python`, `python-best-practices`] - why: DB assertions and fixture orchestration.
  - Omitted: [`python-code-style`] - why: correctness matters more than style at this stage.

  **Parallelization**: Can Parallel: NO | Wave 2 | Blocks: 9 | Blocked By: 1, 2, 3, 4, 5, 6, 7

  **References**:
  - Pattern: `docker-compose.yml:2` - local Neo4j service assumptions.
  - Pattern: `README.md:21` - local Neo4j access baseline.
  - Pattern: `README.md:89` - existing index expectations to extend, not break.

  **Acceptance Criteria**:
  - [ ] `uv run pytest tests/integration/test_seed_skills.py -q` passes.
  - [ ] Combined-source test proves exactly one imported node exists for a skill matched across sources by deterministic identity rules.
  - [ ] Exact Cypher assertions verify required relationship properties and source metadata on imported graph elements.

  **QA Scenarios**:
  ```text
  Scenario: Combined ESCO + CNCF import succeeds end-to-end
    Tool: Bash
    Steps: Run `uv run pytest tests/integration/test_seed_skills.py -q`
    Expected: Tests pass and verify exact graph counts, aliases, `IS_A` hierarchy, and `RELATED_TO` complementary edges.
    Evidence: .sisyphus/evidence/task-8-seed-skills.txt

  Scenario: Combined import rerun remains idempotent
    Tool: Bash
    Steps: Run `uv run pytest tests/integration/test_seed_skills.py -q -k rerun`
    Expected: Test passes by asserting unchanged node and edge counts after a second import pass.
    Evidence: .sisyphus/evidence/task-8-seed-skills-error.txt
  ```

  **Commit**: YES | Message: `test(import): verify end-to-end skill seeding` | Files: `tests/integration/test_seed_skills.py`

- [ ] 9. Align local commands and smoke verification with the new seed workflow

  **What to do**: Update developer commands and smoke verification so the project can initialize Neo4j and run the new seed workflow cleanly. Add or update `Makefile` targets for `seed-esco`, `seed-cncf`, and `seed-all`, and keep `main.py` either as a pure smoke test or convert it to call only non-destructive verification logic. Document manual ESCO bootstrap expectations and local file layout in `README.md`.
  **Must NOT do**: Do not turn README into a large data manual or require users to inspect the graph manually for correctness.

  **Recommended Agent Profile**:
  - Category: `quick` - Reason: bounded workflow/documentation alignment.
  - Skills: [`python-code-style`] - why: keep smoke script and command docs consistent.
  - Omitted: [`neo4j-data-models`] - why: no new graph semantics should be introduced here.

  **Parallelization**: Can Parallel: NO | Wave 2 | Blocks: none | Blocked By: 6, 7, 8

  **References**:
  - Pattern: `Makefile:18` - current command style to extend.
  - Pattern: `README.md:23` - current command documentation section.
  - Pattern: `main.py:10` - current smoke-test messaging.

  **Acceptance Criteria**:
  - [ ] `make help` lists seed-related commands.
  - [ ] `README.md` documents `data/raw/esco/` manual bootstrap and `data/raw/cncf/landscape.yml` expectations.
  - [ ] `uv run python main.py` still works as a smoke verifier or is intentionally narrowed to connectivity/index checks with tests updated accordingly.

  **QA Scenarios**:
  ```text
  Scenario: Local workflow exposes seed commands clearly
    Tool: Bash
    Steps: Run `make help`
    Expected: Output includes `seed-esco`, `seed-cncf`, and `seed-all` with clear descriptions.
    Evidence: .sisyphus/evidence/task-9-local-workflow.txt

  Scenario: Seed command fails clearly when ESCO bootstrap is absent
    Tool: Bash
    Steps: Run `make seed-esco` without `data/raw/esco/` present
    Expected: Command exits non-zero with a message instructing the user to place ESCO files in the documented bootstrap directory.
    Evidence: .sisyphus/evidence/task-9-local-workflow-error.txt
  ```

  **Commit**: YES | Message: `docs(workflow): add seed commands and bootstrap guidance` | Files: `Makefile`, `README.md`, `main.py` if needed

## Final Verification Wave (4 parallel agents, ALL must APPROVE)
- [ ] F1. Plan Compliance Audit - oracle
- [ ] F2. Code Quality Review - unspecified-high
- [ ] F3. Real Manual QA - unspecified-high (+ playwright if UI)
- [ ] F4. Scope Fidelity Check - deep

## Commit Strategy
- Commit in the sequence listed per task; keep each commit passing its scoped tests.
- Do not combine schema, importer, and end-to-end verification into one commit.
- Prefer one source or concern per commit for clean rollback and review.

## Success Criteria
- The repo gains a dedicated, rerun-safe seed/import workflow for ESCO and CNCF only.
- The graph remains `Skill`-only and preserves existing ontology (`IS_A`, `REQUIRES`, `RELATED_TO`).
- ESCO enriches aliases and taxonomy; CNCF enriches tool skills and complementary relations.
- Running the seed workflow twice does not duplicate imported nodes or edges.
- All verification is executable with `pytest`, CLI commands, and exact Cypher assertions; no human graph inspection is required.
