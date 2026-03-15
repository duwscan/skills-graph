# Draft: External Global Dataset Enrichment

## Requirements (confirmed)
- enrich the graph with external global dataset
- no api key require
- use fetch-style HTTP(S) downloads rather than authenticated APIs
- no need for `Occupation` nodes
- use only `ESCO` and `CNCF`
- enrich `Skill`-to-`Skill` relations
- include a script to seed the graph toward this goal

## Technical Decisions
- Anchor the recommendation to the existing Neo4j + neomodel `Skill` graph rather than a generic graph stack.
- Prefer downloadable public datasets over live APIs because the repo has no ETL layer and the user disallowed API-key dependencies.
- Recommend idempotent import with provenance because the current project only has smoke-test writes and no refresh workflow.
- Exclude `Occupation` expansion and keep enrichment limited to `Skill`-centric properties and `Skill`-to-`Skill` relationships.
- Use `ESCO` as the semantic taxonomy source for aliases and hierarchy.
- Use `CNCF` landscape data as the tool/product source for `skill_type="tool"` coverage and complementary relations.
- Primary enrichment goal is taxonomy plus aliases, with tool enrichment folded into the same seed workflow.

## Research Findings
- `README.md`: repo currently models only `Skill` nodes with `IS_A`, `REQUIRES`, and `RELATED_TO` relationships.
- `main.py`: current ingestion is manual sample creation only; there is no fetch/import pipeline.
- `docker-compose.yml`: Neo4j 5.26 runs with APOC enabled and an import volume mounted at `/var/lib/neo4j/import`.
- Public no-auth datasets identified: ESCO, O*NET, GeoNames, CNCF Landscape, Stack Overflow survey, World Bank HCI+.
- Neo4j-friendly ingestion patterns: fetch file -> validate schema -> `MERGE` by stable external id -> track provenance and license metadata.
- Narrowed source set: only ESCO and CNCF are in scope; O*NET and GeoNames are out.

## Open Questions
- Test strategy for the new seed/import workflow.

## Scope Boundaries
- INCLUDE: public datasets, direct download/fetch patterns, `Skill`-only graph-model fit, licensing/provenance guardrails.
- INCLUDE: a dedicated seed/import script for ESCO + CNCF and relation enrichment logic.
- EXCLUDE: authenticated APIs, proprietary job-posting feeds, actual code implementation.
