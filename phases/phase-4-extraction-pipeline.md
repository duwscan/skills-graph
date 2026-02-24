# Phase 4: Skill Extraction Pipeline (LLM + RAG)

> **Timeline:** Week 7-9
> **Dependencies:** Phase 2 (CRUD API), Phase 3 (Embeddings & Search)
> **Unlocks:** Phase 5 (Discovery & HITL), Phase 7 (Workers & Events), Phase 8 (QA)

---

## Goal

Implement the full RAG-based skill extraction pipeline — the system's **core value proposition**. Takes free text (job postings, resumes, course descriptions) and returns a ranked list of skills from the taxonomy. Includes **section-aware weighting** (LinkedIn-inspired) and **co-occurrence recording** for empirical edge strengthening. Uses Spring AI's `ChatClient` with `BeanOutputConverter` for structured extraction.

---

## 4.1 Text Processing

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 4.1.1 | Document parser | Accept plaintext and HTML input. Strip HTML tags while preserving text structure (headings → `\n\n`, list items → `\n- `). Normalize whitespace. Return clean plaintext | `src/main/java/com/skillsgraph/service/extraction/DocumentParser.java` |
| 4.1.2 | Token estimator | `estimateTokens(String text): int` — approximate token count using word-count heuristic (words * 1.3). Used to determine chunk boundaries | `src/main/java/com/skillsgraph/service/extraction/TokenEstimator.java` |
| 4.1.3 | Section detector | Detect document type (`jd`, `cv`, `generic`) and identify named sections. For JDs: look for headings like "Requirements", "Qualifications", "Responsibilities", "Nice to have", "About us". For CVs: look for "Skills", "Experience", "Projects", "Education", "Summary". Return `{ type: "jd" | "cv" | "generic", sections: Array<{ name: string; type: string; weight: number; startOffset: number; endOffset: number }> }` | `src/main/java/com/skillsgraph/service/extraction/SectionDetector.java` |
| 4.1.4 | Section-based chunker | Split text on structural boundaries: `\n\n` (paragraphs), `\n#` (markdown headings), `---` (horizontal rules). Merge small consecutive sections until target size (~`CHUNK_TARGET_TOKENS` tokens — see `src/main/java/com/skillsgraph/config/AppConstants.java`). If a single section exceeds `CHUNK_MAX_TOKENS` tokens, apply sliding window. **Preserve section metadata** on each chunk from the section detector | `src/main/java/com/skillsgraph/service/extraction/DocumentChunker.java` |
| 4.1.5 | Sliding window fallback | For unstructured text without clear section boundaries: split into `CHUNK_TARGET_TOKENS` token chunks with `CHUNK_OVERLAP_TOKENS` token overlap. Ensure splits happen at sentence boundaries when possible | `src/main/java/com/skillsgraph/service/extraction/DocumentChunker.java` |
| 4.1.6 | Chunk interface | `// Java record for Chunk { text: string; index: number; startOffset: number; endOffset: number; tokenEstimate: number; section?: { name: string; type: string; weight: number; } }` | `src/main/java/com/skillsgraph/service/extraction/ExtractionTypes.java` |

### Section Weight Constants

Section weights are defined in `src/main/java/com/skillsgraph/config/AppConstants.java`:

**Job Description Weights:**

| Section | Constant | Default | Rationale |
|---|---|---|---|
| Requirements / Qualifications | `SECTION_WEIGHT_JD_REQUIREMENTS` | 1.0 | Core skills the role demands |
| Responsibilities / Duties | `SECTION_WEIGHT_JD_RESPONSIBILITIES` | 0.9 | Skills implied by the work |
| Nice-to-have / Preferred | `SECTION_WEIGHT_JD_NICE_TO_HAVE` | 0.75 | Optional skills |
| About the team / Company | `SECTION_WEIGHT_JD_COMPANY` | 0.5 | Context skills, not requirements |
| Benefits / Perks | `SECTION_WEIGHT_JD_BENEFITS` | 0.3 | Rarely contains relevant skills |

**CV / Resume Weights:**

| Section | Constant | Default | Rationale |
|---|---|---|---|
| Skills (explicit list) | `SECTION_WEIGHT_CV_SKILLS` | 1.0 | Self-declared skills |
| Work Experience | `SECTION_WEIGHT_CV_EXPERIENCE` | 0.9 | Skills demonstrated in practice |
| Projects | `SECTION_WEIGHT_CV_PROJECTS` | 0.85 | Skills applied in context |
| Education / Certifications | `SECTION_WEIGHT_CV_EDUCATION` | 0.8 | Formal skill acquisition |
| Summary / Objective | `SECTION_WEIGHT_CV_SUMMARY` | 0.7 | Often aspirational |

### Checklist

- [ ] Parser strips HTML tags: `"<b>Python</b> and <i>Java</i>"` → `"Python and Java"`
- [ ] Parser preserves structure: headings become `\n\n`, lists become `\n- `
- [ ] Token estimator: `estimateTokens("hello world")` returns ~3
- [ ] Section detector: JD with "Requirements" and "Responsibilities" headings → detects `type: "jd"` with 2 sections
- [ ] Section detector: CV with "Skills" and "Experience" headings → detects `type: "cv"` with 2 sections
- [ ] Section detector: plain text without clear headings → `type: "generic"`, no sections
- [ ] Section detector: assigns correct weight constants to each section type
- [ ] Section chunker: a 5-paragraph document produces 2-3 chunks of ~`CHUNK_TARGET_TOKENS` tokens (see `src/main/java/com/skillsgraph/config/AppConstants.java`)
- [ ] Section chunker: small paragraphs are merged together (not one chunk per paragraph)
- [ ] Section chunker: chunks preserve `section` metadata from detector
- [ ] Sliding window: a 4,000 token block without sections → 3 chunks with `CHUNK_OVERLAP_TOKENS` token overlap
- [ ] Sliding window: splits at sentence boundaries (not mid-word)
- [ ] Chunk objects have correct `startOffset` and `endOffset` relative to original text
- [ ] Empty text → returns empty chunk array (no errors)
- [ ] Very short text (< 100 tokens) → returns single chunk

---

## 4.2 Extraction Schemas & Prompts

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 4.2.1 | Extraction output schema | Java record for LLM structured output: `extracted_skills[]` (skill_id, skill_name, confidence, evidence[], proficiency_hint, context_type, section?) and `discovered_candidates[]` (surface_form, suggested_category, reason). Detailed `.describe()` on every field for LLM guidance. The `section` field indicates which document section the skill was found in | `src/main/java/com/skillsgraph/dto/ExtractionDto.java` |
| 4.2.2 | Extraction request schema | Java DTO record for API input: `text` (string, 1-`EXTRACTION_MAX_TEXT_LENGTH` chars — see `src/main/java/com/skillsgraph/config/AppConstants.java`), `options?` (expand: boolean, min_confidence: number, locale: string, max_skills: number) | `src/main/java/com/skillsgraph/dto/ExtractionDto.java` |
| 4.2.3 | Extraction response schema | Java DTO record for API output: `skills[]`, `discovered_candidates[]`, `metadata` (chunks_processed, cache_hits, processing_time_ms, model_used) | `src/main/java/com/skillsgraph/dto/ExtractionDto.java` |
| 4.2.4 | System prompt | Constant string with instructions for the LLM: role, input format, output constraints ("ONLY return skills from the candidate list"), confidence scoring guidelines | `src/main/java/com/skillsgraph/service/extraction/ExtractionPrompts.java` |
| 4.2.5 | Few-shot examples | 2-3 input/output examples covering: explicit skill mention, implicit skill mention, no skills found. Include in system prompt | `src/main/java/com/skillsgraph/service/extraction/ExtractionPrompts.java` |
| 4.2.6 | Prompt builder | `buildPrompt(chunk: string, candidates: CandidateSkill[])` — format candidates as numbered list `[SK-1001] Machine Learning`, append chunk text. Ensure total prompt stays under context limits. If too many candidates, truncate to top `RAG_CANDIDATE_LIMIT` by similarity (see `src/main/java/com/skillsgraph/config/AppConstants.java`) | `src/main/java/com/skillsgraph/service/extraction/ExtractionPrompts.java` |

### Checklist

- [ ] Extraction output schema validates correct LLM output
- [ ] Extraction output schema rejects output with missing required fields
- [ ] System prompt clearly instructs LLM to only return skills from candidate list
- [ ] Few-shot examples cover explicit mention, implicit mention, and empty case
- [ ] `buildPrompt()` formats candidates correctly
- [ ] `buildPrompt()` truncates candidate list if it would exceed token budget
- [ ] All Jakarta Bean Validation fields have `.describe()` annotations for LLM guidance

---

## 4.3 Extraction Pipeline Core

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 4.3.1 | `SkillExtractionPipeline` class | Constructor: `EmbeddingService`, `VectorSearchService`, `RedisClient`, model config. Main orchestration class | `src/main/java/com/skillsgraph/service/extraction/SkillExtractionPipeline.java` |
| 4.3.2 | `extract(text, options?)` | Full pipeline orchestration: `parse → detect sections → chunk (with section metadata) → processChunks → applyWeighting → mergeAndDeduplicate → expand → recordCoOccurrences → filterByConfidence → return` | `src/main/java/com/skillsgraph/service/extraction/SkillExtractionPipeline.java` |
| 4.3.3 | `processChunk(chunk)` | Per-chunk logic: (1) compute cache key `extract:{sha256(chunk.text + sortedCandidateIds)}`, (2) check Redis cache, (3) if miss: embed chunk → retrieve top `RAG_CANDIDATE_LIMIT` candidates (see `src/main/java/com/skillsgraph/config/AppConstants.java`) → build prompt (include section context) → call LLM → validate → cache → return | `src/main/java/com/skillsgraph/service/extraction/SkillExtractionPipeline.java` |
| 4.3.4 | Model tier selection | `selectModel(chunk, options)` — Haiku for chunks < 500 tokens or English-only standard docs; Sonnet for multilingual, long, or complex docs. Configurable via options | `src/main/java/com/skillsgraph/service/extraction/SkillExtractionPipeline.java` |
| 4.3.5 | LLM call | `callLLM(prompt, model)` — use `chatClient.call().entity(ExtractionResult.class)` from Spring AI. Set `maxRetries: EXTRACTION_MAX_RETRIES` (see `src/main/java/com/skillsgraph/config/AppConstants.java`). Track tokens used, latency. Return structured output | `src/main/java/com/skillsgraph/service/extraction/SkillExtractionPipeline.java` |
| 4.3.6 | Result validation | `validateResults(output, candidates)` — reject any `skill_id` not in the candidate list. Log stripped entries for monitoring. Return only valid extractions | `src/main/java/com/skillsgraph/service/extraction/SkillExtractionPipeline.java` |
| 4.3.7 | Section weighting | `applyWeighting(results, chunk)` — if chunk has section metadata, multiply each extracted skill's confidence by the section weight. `final_confidence = llm_confidence × section_weight`. Skills from "Requirements" sections keep full confidence; skills from "Company description" get 0.5x | `src/main/java/com/skillsgraph/service/extraction/SkillExtractionPipeline.java` |
| 4.3.8 | Result merging | `mergeAndDeduplicate(chunkResults[])` — for skills appearing in multiple chunks: keep highest confidence, merge evidence arrays (deduplicate), use latest proficiency_hint. Sort final results by confidence DESC | `src/main/java/com/skillsgraph/service/extraction/SkillExtractionPipeline.java` |
| 4.3.9 | Co-occurrence recording | After final merge, publish all pairs of extracted active skill IDs to `co-occurrence:pairs` Redis Stream with `{ skill_ids: string[], source_type: "cv" | "jd" | "course" | "generic" }`. Async — don't block the API response | `src/main/java/com/skillsgraph/service/extraction/SkillExtractionPipeline.java` |
| 4.3.10 | Response caching | Cache key: `extract:{sha256(chunk + sortedCandidateIds)}`. TTL: `EXTRACTION_CACHE_TTL_SECONDS` (see `src/main/java/com/skillsgraph/config/AppConstants.java`). Store as JSON string in Redis | `src/main/java/com/skillsgraph/service/extraction/SkillExtractionPipeline.java` |
| 4.3.11 | Metadata tracking | Track per-extraction: total chunks processed, cache hits, cache misses, total LLM tokens, total processing time, model used, document_type, sections_detected | `src/main/java/com/skillsgraph/service/extraction/SkillExtractionPipeline.java` |

### Checklist

- [ ] `extract("Simple job description about Python")` returns extracted skills with confidence scores
- [ ] Short text (< `CHUNK_TARGET_TOKENS` tokens) → processes as single chunk
- [ ] Long text (> 3,000 tokens) → splits into multiple chunks, processes each
- [ ] Section detection: JD with "Requirements" heading → `document_type: "jd"`, sections detected
- [ ] Section weighting: skill in "Requirements" section keeps full confidence (1.0x)
- [ ] Section weighting: skill in "Nice to have" section gets reduced confidence (0.75x)
- [ ] Section weighting: skill in "Company description" section gets 0.5x confidence
- [ ] Section weighting: generic text without sections → no weight adjustment (1.0x)
- [ ] Co-occurrence: extraction of [Python, Django, React] publishes 3 pairs to Redis Stream
- [ ] Co-occurrence: async — doesn't delay API response
- [ ] Cache hit: second call with identical text returns immediately (< 10ms)
- [ ] Cache miss: first call makes LLM API call and caches result
- [ ] Validation: fabricated skill_ids from LLM are stripped (not returned)
- [ ] Merging: skill appearing in chunk 1 (0.8) and chunk 2 (0.9) → returned with 0.9 confidence
- [ ] Merging: evidence arrays from both chunks are combined
- [ ] Tier selection: short English text → uses Haiku; long multilingual → uses Sonnet
- [ ] Metadata: response includes `chunks_processed`, `cache_hits`, `processing_time_ms`, `document_type`, `sections_detected`
- [ ] `maxRetries: EXTRACTION_MAX_RETRIES` — LLM call retries on transient errors (see `src/main/java/com/skillsgraph/config/AppConstants.java`)
- [ ] Error handling: if LLM call fails after retries, chunk is skipped (not crash entire extraction)

---

## 4.4 Skill Expansion

### Context

After extracting skills, the pipeline can optionally "expand" results by querying the graph for parent, child, and sibling skills. This enriches the output — if "PyTorch" is extracted, expansion adds "Deep Learning" (parent) and "TensorFlow" (sibling).

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 4.4.1 | `SkillExpansionService` | Given a list of extracted skill IDs, query the graph for related skills using recursive CTE | `src/main/java/com/skillsgraph/service/extraction/SkillExpansionService.java` |
| 4.4.2 | Expansion SQL | Use the CTE from ARCHITECTURE.md §4.4: get parents (source of `parent_of` edges), children (target of `parent_of` edges), and siblings (other targets of same parent) | `src/main/java/com/skillsgraph/service/extraction/SkillExpansionService.java` |
| 4.4.3 | Confidence reduction | Expanded skills receive `original_confidence * EXTRACTION_EXPANSION_FACTOR` (see `src/main/java/com/skillsgraph/config/AppConstants.java`). Mark with `expansion_type: "parent" | "child" | "sibling"` and `is_expanded: true` | `src/main/java/com/skillsgraph/service/extraction/SkillExpansionService.java` |
| 4.4.4 | Deduplication with extracted | If an expanded skill was already directly extracted, keep the directly extracted version (higher confidence). Don't add duplicates | `src/main/java/com/skillsgraph/service/extraction/SkillExpansionService.java` |
| 4.4.5 | Configurable | Accept `options.expand: boolean` (default true) and `options.expansion_depth: number` (default `EXTRACTION_EXPANSION_DEPTH_DEFAULT` — see `src/main/java/com/skillsgraph/config/AppConstants.java`) | `src/main/java/com/skillsgraph/service/extraction/SkillExpansionService.java` |

### Checklist

- [ ] Expansion finds parents: extract "Deep Learning" → expansion adds "Machine Learning" (parent)
- [ ] Expansion finds siblings: extract "PyTorch" → expansion adds "TensorFlow" (sibling)
- [ ] Expansion finds children: extract "Data Science" → expansion adds "Machine Learning", "Data Engineering" (children)
- [ ] Expanded skills have `is_expanded: true` and `expansion_type` set
- [ ] Expanded skills have reduced confidence (original * `EXTRACTION_EXPANSION_FACTOR` — see `src/main/java/com/skillsgraph/config/AppConstants.java`)
- [ ] No duplicates: if "Machine Learning" was both extracted and expanded, only extracted version appears
- [ ] `expand: false` option skips expansion entirely
- [ ] `expansion_depth: 0` skips expansion
- [ ] Expansion doesn't include deprecated or merged skills

---

## 4.5 Extraction API Routes

### Tasks

| # | Task | Detail | Files |
|---|---|---|---|
| 4.5.1 | `POST /api/extract` | Accept extraction request body. Run `SkillExtractionPipeline.extract()`. Return extraction response with skills, discovered_candidates, and metadata | `src/main/java/com/skillsgraph/controller/ExtractionController.java` |
| 4.5.2 | Request validation | `@Valid("json", extractionRequestSchema)` — validate text length (1-`EXTRACTION_MAX_TEXT_LENGTH` chars — see `src/main/java/com/skillsgraph/config/AppConstants.java`), options | `src/main/java/com/skillsgraph/controller/ExtractionController.java` |
| 4.5.3 | Concurrency control | Use semaphore (`ReentrantLock / @Async`) to limit concurrent LLM calls to `LLM_CONCURRENCY_LIMIT` across all requests (see `src/main/java/com/skillsgraph/config/AppConstants.java`). If semaphore is full, return 429 with `Retry-After` header | `src/main/java/com/skillsgraph/service/extraction/SkillExtractionPipeline.java` |
| 4.5.4 | Min confidence filter | Apply `options.min_confidence` (default `EXTRACTION_MIN_CONFIDENCE_DEFAULT` — see `src/main/java/com/skillsgraph/config/AppConstants.java`) to filter out low-confidence extractions from the response. Still return metadata about total extractions before filtering | `src/main/java/com/skillsgraph/service/extraction/SkillExtractionPipeline.java` |

### Checklist

- [ ] `POST /api/extract` with valid text → 200 with extracted skills
- [ ] Response shape: `{ skills: [...], discovered_candidates: [...], metadata: {...} }`
- [ ] `min_confidence: 0.8` filters out low-confidence results
- [ ] Text > `EXTRACTION_MAX_TEXT_LENGTH` chars → 400 validation error (see `src/main/java/com/skillsgraph/config/AppConstants.java`)
- [ ] Empty text → 400 validation error
- [ ] 51st concurrent request → 429 with Retry-After header (limit: `LLM_CONCURRENCY_LIMIT` — see `src/main/java/com/skillsgraph/config/AppConstants.java`)
- [ ] Extraction of a typical job description completes in < 5 seconds
- [ ] Extraction caches results — second identical request is < 100ms

---

## Phase 4 Completion Verification

```bash
# Ensure taxonomy has skills to extract against
# (should have seed data + any skills created in Phase 2 testing)

# Extract from a job description
curl -X POST http://localhost:8080/api/extract \
  -H "Content-Type: application/json" \
  -d '{
    "text": "We are looking for a Senior Machine Learning Engineer with experience in Python, TensorFlow, and deploying models on AWS. Experience with CI/CD pipelines and Docker is a plus. Strong communication skills required.",
    "options": { "expand": true, "min_confidence": 0.5 }
  }' | jq

# Expected: skills like Machine Learning, Python, TensorFlow, AWS, CI/CD, Docker, Communication
# Each with confidence, evidence (substring), proficiency_hint

# Test multi-chunk extraction
curl -X POST http://localhost:8080/api/extract \
  -H "Content-Type: application/json" \
  -d '{"text": "<long 5000-word resume text here>"}' | jq '.metadata'
# → {"chunks_processed": 3, "cache_hits": 0, "processing_time_ms": 4200}

# Test caching (should be fast)
time curl -X POST http://localhost:8080/api/extract \
  -d '{"text": "Python developer with React experience"}'
time curl -X POST http://localhost:8080/api/extract \
  -d '{"text": "Python developer with React experience"}'
# Second call should be < 100ms

# Test expansion
curl -X POST http://localhost:8080/api/extract \
  -d '{"text": "Deep Learning specialist", "options": {"expand": true}}' | jq '.skills[] | {name: .skill_name, expanded: .is_expanded, type: .expansion_type}'
# → Deep Learning (direct), Machine Learning (parent), Neural Networks (sibling)

# Test discovered_candidates
curl -X POST http://localhost:8080/api/extract \
  -d '{"text": "Expert in LangGraph and CrewAI frameworks"}' | jq '.discovered_candidates'
# → [{"surface_form": "LangGraph", ...}, {"surface_form": "CrewAI", ...}]

./mvnw test -Dtest="*ExtractionTest"
echo "Phase 4 complete ✓"
```

---

## Phase 4 Master Checklist

### 4.1 Text Processing
- [ ] HTML parser strips tags, preserves structure
- [ ] Token estimator approximates token count
- [ ] Section-based chunker splits on structural boundaries
- [ ] Sliding window fallback for unstructured text
- [ ] Chunks have correct offsets and token estimates

### 4.2 Schemas & Prompts
- [ ] Extraction output schema with all fields and `.describe()` annotations
- [ ] Request and response schemas for API
- [ ] System prompt with clear instructions
- [ ] 2-3 few-shot examples (explicit, implicit, empty)
- [ ] Prompt builder with candidate formatting and size limits

### 4.3 Pipeline Core
- [ ] `SkillExtractionPipeline` orchestrates full flow
- [ ] Cache check → embed → retrieve → prompt → LLM → validate → cache
- [ ] Model tier selection (Haiku vs Sonnet)
- [ ] Spring AI `chatClient.call().entity(ExtractionResult.class)` for structured output
- [ ] Validation strips invalid skill_ids
- [ ] Merging deduplicates across chunks (highest confidence wins)
- [ ] Response caching with `EXTRACTION_CACHE_TTL_SECONDS` TTL (see `src/main/java/com/skillsgraph/config/AppConstants.java`)
- [ ] Metadata tracking (chunks, cache hits, time, tokens)

### 4.4 Skill Expansion
- [ ] Finds parents, children, and siblings
- [ ] Confidence reduction (original * `EXTRACTION_EXPANSION_FACTOR` — see `src/main/java/com/skillsgraph/config/AppConstants.java`)
- [ ] Deduplication with directly extracted skills
- [ ] Configurable via `expand` and `expansion_depth` options

### 4.5 API Routes
- [ ] `POST /api/extract` with validation and concurrency control
- [ ] Min confidence filtering
- [ ] 429 response when at capacity
- [ ] < 5s p99 for typical documents
