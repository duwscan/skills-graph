// =============================================================================
// Centralized Configuration Constants
// =============================================================================
// Single source of truth for all tunable values across the application.
// Values can be overridden via environment variables where noted.
// Import from `@/config/constants` — never hardcode these values inline.
// =============================================================================

// -----------------------------------------------------------------------------
// Embedding & Vector Search
// -----------------------------------------------------------------------------

/** Embedding vector dimensions (Matryoshka reduction from 3072). Model-coupled — do not override. */
export const EMBEDDING_DIMENSIONS = 1024;

/** OpenAI embedding model identifier. */
export const EMBEDDING_MODEL =
  process.env.EMBEDDING_MODEL ?? "text-embedding-3-large";

/** Redis TTL for cached embeddings (30 days). */
export const EMBEDDING_CACHE_TTL_SECONDS =
  Number(process.env.EMBEDDING_CACHE_TTL) || 2_592_000;

/** Max texts per `embedMany()` batch call. */
export const EMBEDDING_BATCH_SIZE = 100;

/** Cosine similarity ≥ this → treat as duplicate skill. */
export const SIMILARITY_DUPLICATE_THRESHOLD =
  Number(process.env.SIMILARITY_DUPLICATE_THRESHOLD) || 0.9;

/** Cosine similarity in [REVIEW, DUPLICATE) → flag for human review. */
export const SIMILARITY_REVIEW_THRESHOLD =
  Number(process.env.SIMILARITY_REVIEW_THRESHOLD) || 0.7;

/** Embedding similarity ≥ this → `RELATED` (high confidence). */
export const SIMILARITY_RELATED_HIGH_THRESHOLD = 0.85;

/** Embedding similarity ≥ this → `RELATED` (medium confidence). */
export const SIMILARITY_RELATED_LOW_THRESHOLD = 0.65;

/** Max candidate skills returned by RAG retrieval per chunk. */
export const RAG_CANDIDATE_LIMIT =
  Number(process.env.RAG_CANDIDATE_LIMIT) || 100;

// -----------------------------------------------------------------------------
// Extraction Pipeline
// -----------------------------------------------------------------------------

/** Target token count per chunk for section-based splitting. */
export const CHUNK_TARGET_TOKENS = 1500;

/** Hard max token count before sliding-window fallback. */
export const CHUNK_MAX_TOKENS = 2000;

/** Token overlap between consecutive sliding-window chunks. */
export const CHUNK_OVERLAP_TOKENS = 200;

/** Redis TTL for cached extraction results (7 days). */
export const EXTRACTION_CACHE_TTL_SECONDS =
  Number(process.env.EXTRACTION_CACHE_TTL) || 604_800;

/** Max input text length (characters) accepted by the extraction API. */
export const EXTRACTION_MAX_TEXT_LENGTH = 100_000;

/** Default minimum confidence to include a skill in extraction results. */
export const EXTRACTION_MIN_CONFIDENCE_DEFAULT = 0.5;

/** Confidence multiplier applied to expanded (parent/child/sibling) skills. */
export const EXTRACTION_EXPANSION_FACTOR = 0.6;

/** Default graph traversal depth for skill expansion. */
export const EXTRACTION_EXPANSION_DEPTH_DEFAULT = 1;

/** Max retries for a single LLM call via the AI SDK. */
export const EXTRACTION_MAX_RETRIES = 3;

/** Semaphore limit for concurrent LLM calls across all requests. */
export const LLM_CONCURRENCY_LIMIT =
  Number(process.env.LLM_CONCURRENCY_LIMIT) || 50;

// -----------------------------------------------------------------------------
// Discovery & Review
// -----------------------------------------------------------------------------

/** Number of extraction signals before a candidate triggers full discovery. */
export const DISCOVERY_SIGNAL_THRESHOLD =
  Number(process.env.DISCOVERY_SIGNAL_THRESHOLD) || 5;

/** Redis TTL for discovery signal counters (30 days). */
export const DISCOVERY_SIGNAL_TTL_SECONDS =
  Number(process.env.DISCOVERY_SIGNAL_TTL) || 2_592_000;

/** Number of skill pairs classified per single LLM batch call. */
export const RELATIONSHIP_BATCH_SIZE = 15;

// -----------------------------------------------------------------------------
// API & Pagination
// -----------------------------------------------------------------------------

/** Default page size for list endpoints. */
export const PAGINATION_DEFAULT_LIMIT = 20;

/** Maximum allowed page size. */
export const PAGINATION_MAX_LIMIT = 100;

/** Default page size for changelog listing. */
export const CHANGELOG_DEFAULT_LIMIT = 50;

/** Default ancestor/descendant traversal depth. */
export const TRAVERSAL_DEFAULT_DEPTH = 5;

/** Maximum ancestor/descendant traversal depth. */
export const TRAVERSAL_MAX_DEPTH = 10;

/** Maximum character length for generated slugs. */
export const SLUG_MAX_LENGTH = 100;

/** Maximum character length for skill canonical names. */
export const SKILL_NAME_MAX_LENGTH = 200;

/** Maximum character length for skill descriptions. */
export const SKILL_DESCRIPTION_MAX_LENGTH = 5000;

/** Maximum character length for alias surface forms. */
export const ALIAS_MAX_LENGTH = 200;

/** Hono body size limit in bytes (1 MB). */
export const BODY_SIZE_LIMIT = 1_048_576;

// -----------------------------------------------------------------------------
// Rate Limiting
// -----------------------------------------------------------------------------

/** Requests per minute for read endpoints. */
export const RATE_LIMIT_READ = Number(process.env.RATE_LIMIT_READ) || 100;

/** Requests per minute for extraction endpoints. */
export const RATE_LIMIT_EXTRACT =
  Number(process.env.RATE_LIMIT_EXTRACT) || 20;

/** Requests per minute for mutation endpoints. */
export const RATE_LIMIT_MUTATE = Number(process.env.RATE_LIMIT_MUTATE) || 50;

// -----------------------------------------------------------------------------
// Workers & Events
// -----------------------------------------------------------------------------

/** Max concurrent documents processed by the extraction worker. */
export const EXTRACTION_WORKER_CONCURRENCY =
  Number(process.env.EXTRACTION_WORKER_CONCURRENCY) || 10;

/** Max documents accepted in a single batch extraction request. */
export const BATCH_MAX_DOCUMENTS = 100;

/** Redis TTL for batch extraction results (24 hours). */
export const BATCH_RESULT_TTL_SECONDS =
  Number(process.env.BATCH_RESULT_TTL) || 86_400;

/** Debounce window (ms) for Typesense sync worker event batching. */
export const TYPESENSE_SYNC_DEBOUNCE_MS =
  Number(process.env.TYPESENSE_SYNC_DEBOUNCE_MS) || 500;

/** Max retries for a stream consumer before moving message to dead-letter. */
export const STREAM_CONSUMER_MAX_RETRIES = 3;

// -----------------------------------------------------------------------------
// Infrastructure
// -----------------------------------------------------------------------------

/** DB connection pool size for development. */
export const DB_POOL_SIZE_DEV = 10;

/** DB connection pool size for production. */
export const DB_POOL_SIZE_PROD = 50;

/** Effective pool size, overridable via env. Falls back to dev/prod default. */
export const DB_POOL_SIZE =
  Number(process.env.DB_POOL_SIZE) ||
  (process.env.NODE_ENV === "production" ? DB_POOL_SIZE_PROD : DB_POOL_SIZE_DEV);

/** Timeout (ms) for graceful shutdown to wait for in-flight work. */
export const GRACEFUL_SHUTDOWN_TIMEOUT_MS = 30_000;

/** Timeout (ms) for initial database connection on startup. */
export const DB_CONNECT_TIMEOUT_MS = 30_000;

/** Timeout (ms) for initial Redis connection on startup. */
export const REDIS_CONNECT_TIMEOUT_MS = 10_000;

// -----------------------------------------------------------------------------
// Quality Thresholds (Testing)
// -----------------------------------------------------------------------------

/** Minimum F1 score for golden-set extraction evaluation to pass. */
export const GOLDEN_SET_MIN_F1 = 0.8;

/** Minimum precision for golden-set evaluation. */
export const GOLDEN_SET_MIN_PRECISION = 0.75;

/** Minimum recall for golden-set evaluation. */
export const GOLDEN_SET_MIN_RECALL = 0.75;

/** Maximum p99 latency (seconds) for extraction load tests. */
export const LOAD_TEST_MAX_P99_SECONDS = 10;

/** Maximum error rate (fraction) for extraction load tests. */
export const LOAD_TEST_MAX_ERROR_RATE = 0.01;
