package com.sk.skillsgraph.config;

public final class AppConstants {

    private AppConstants() {
    }

    /** Embedding/vector dimensions for OpenAI Matryoshka embeddings. */
    public static final int EMBEDDING_DIMENSIONS = 1024;
    /** Embedding model for skill and alias semantic search. */
    public static final String EMBEDDING_MODEL = "text-embedding-3-large";
    /** Redis TTL for embedding cache entries (30 days). */
    public static final long EMBEDDING_CACHE_TTL_SECONDS = 2_592_000L;
    /** Max documents per embedding batch request. */
    public static final int EMBEDDING_BATCH_SIZE = 100;
    /** Similarity threshold for automatic duplicate detection. */
    public static final double SIMILARITY_DUPLICATE_THRESHOLD = 0.90;
    /** Similarity threshold for review queue classification. */
    public static final double SIMILARITY_REVIEW_THRESHOLD = 0.70;
    /** High threshold for related edge prediction. */
    public static final double SIMILARITY_RELATED_HIGH_THRESHOLD = 0.85;
    /** Low threshold for related edge prediction. */
    public static final double SIMILARITY_RELATED_LOW_THRESHOLD = 0.65;
    /** Maximum RAG candidate skills retrieved per chunk. */
    public static final int RAG_CANDIDATE_LIMIT = 100;

    /** Target chunk size for extraction prompts. */
    public static final int CHUNK_TARGET_TOKENS = 1500;
    /** Hard chunk size cap for sliding-window fallback. */
    public static final int CHUNK_MAX_TOKENS = 2000;
    /** Chunk overlap to preserve context across windows. */
    public static final int CHUNK_OVERLAP_TOKENS = 200;
    /** Redis TTL for extraction response cache entries (7 days). */
    public static final long EXTRACTION_CACHE_TTL_SECONDS = 604_800L;
    /** Maximum text payload length accepted by extraction endpoints. */
    public static final int EXTRACTION_MAX_TEXT_LENGTH = 100_000;
    /** Default confidence filter for extraction responses. */
    public static final double EXTRACTION_MIN_CONFIDENCE_DEFAULT = 0.5;
    /** Confidence discount applied to expansion-generated skills. */
    public static final double EXTRACTION_EXPANSION_FACTOR = 0.6;
    /** Default expansion graph traversal depth. */
    public static final int EXTRACTION_EXPANSION_DEPTH_DEFAULT = 1;
    /** Retry count for transient LLM failures. */
    public static final int EXTRACTION_MAX_RETRIES = 3;
    /** Global cap for concurrent LLM requests. */
    public static final int LLM_CONCURRENCY_LIMIT = 50;

    /** Relative confidence weight for JD requirements sections. */
    public static final double SECTION_WEIGHT_JD_REQUIREMENTS = 1.0;
    /** Relative confidence weight for JD responsibilities sections. */
    public static final double SECTION_WEIGHT_JD_RESPONSIBILITIES = 0.9;
    /** Relative confidence weight for JD nice-to-have sections. */
    public static final double SECTION_WEIGHT_JD_NICE_TO_HAVE = 0.75;
    /** Relative confidence weight for JD company description sections. */
    public static final double SECTION_WEIGHT_JD_COMPANY = 0.5;
    /** Relative confidence weight for JD benefits sections. */
    public static final double SECTION_WEIGHT_JD_BENEFITS = 0.3;
    /** Relative confidence weight for CV skills sections. */
    public static final double SECTION_WEIGHT_CV_SKILLS = 1.0;
    /** Relative confidence weight for CV experience sections. */
    public static final double SECTION_WEIGHT_CV_EXPERIENCE = 0.9;
    /** Relative confidence weight for CV projects sections. */
    public static final double SECTION_WEIGHT_CV_PROJECTS = 0.85;
    /** Relative confidence weight for CV education sections. */
    public static final double SECTION_WEIGHT_CV_EDUCATION = 0.8;
    /** Relative confidence weight for CV summary sections. */
    public static final double SECTION_WEIGHT_CV_SUMMARY = 0.7;

    /** Threshold count required before strengthening empirical edges. */
    public static final int CO_OCCURRENCE_EDGE_THRESHOLD = 20;
    /** Normalization factor for empirical edge weight increments. */
    public static final int CO_OCCURRENCE_NORMALIZATION_FACTOR = 100;
    /** Debounce window for co-occurrence batch aggregation. */
    public static final long CO_OCCURRENCE_BATCH_WINDOW_MS = 1000;

    /** Discovery signal count threshold before promoting a candidate. */
    public static final int DISCOVERY_SIGNAL_THRESHOLD = 5;
    /** Redis TTL for discovery signal counters (30 days). */
    public static final long DISCOVERY_SIGNAL_TTL_SECONDS = 2_592_000L;
    /** Max relationship pairs classified in one LLM call. */
    public static final int RELATIONSHIP_BATCH_SIZE = 15;

    /** Default list endpoint page size. */
    public static final int PAGINATION_DEFAULT_LIMIT = 20;
    /** Maximum list endpoint page size. */
    public static final int PAGINATION_MAX_LIMIT = 100;
    /** Default changelog query limit. */
    public static final int CHANGELOG_DEFAULT_LIMIT = 50;
    /** Default taxonomy traversal depth. */
    public static final int TRAVERSAL_DEFAULT_DEPTH = 5;
    /** Maximum taxonomy traversal depth. */
    public static final int TRAVERSAL_MAX_DEPTH = 10;
    /** Maximum generated slug length. */
    public static final int SLUG_MAX_LENGTH = 100;
    /** Maximum skill name length. */
    public static final int SKILL_NAME_MAX_LENGTH = 200;
    /** Maximum skill description length. */
    public static final int SKILL_DESCRIPTION_MAX_LENGTH = 5000;
    /** Maximum alias surface-form length. */
    public static final int ALIAS_MAX_LENGTH = 200;
    /** Request body size limit in bytes (1 MB). */
    public static final int BODY_SIZE_LIMIT = 1_048_576;

    /** Read endpoint rate limit (requests/minute). */
    public static final int RATE_LIMIT_READ = 100;
    /** Extraction endpoint rate limit (requests/minute). */
    public static final int RATE_LIMIT_EXTRACT = 20;
    /** Mutation endpoint rate limit (requests/minute). */
    public static final int RATE_LIMIT_MUTATE = 50;

    /** Extraction worker concurrency. */
    public static final int EXTRACTION_WORKER_CONCURRENCY = 10;
    /** Maximum document count in one extraction batch. */
    public static final int BATCH_MAX_DOCUMENTS = 100;
    /** Batch result TTL in seconds (24 hours). */
    public static final long BATCH_RESULT_TTL_SECONDS = 86_400L;
    /** Debounce interval for Typesense sync events. */
    public static final long TYPESENSE_SYNC_DEBOUNCE_MS = 500L;
    /** Maximum stream retry attempts before dead-lettering. */
    public static final int STREAM_CONSUMER_MAX_RETRIES = 3;

    /** Default DB pool size for development profile. */
    public static final int DB_POOL_SIZE_DEV = 10;
    /** Default DB pool size for production profile. */
    public static final int DB_POOL_SIZE_PROD = 50;
    /** Effective DB pool size default (profile overrides should use Spring YAML properties). */
    public static final int DB_POOL_SIZE = DB_POOL_SIZE_DEV;
    /** Shutdown timeout for in-flight requests and workers. */
    public static final long GRACEFUL_SHUTDOWN_TIMEOUT_MS = 30_000L;
    /** Database connection timeout in milliseconds. */
    public static final long DB_CONNECT_TIMEOUT_MS = 30_000L;
    /** Redis connection timeout in milliseconds. */
    public static final long REDIS_CONNECT_TIMEOUT_MS = 10_000L;

    /** Golden-set minimum F1 score. */
    public static final double GOLDEN_SET_MIN_F1 = 0.80;
    /** Golden-set minimum precision score. */
    public static final double GOLDEN_SET_MIN_PRECISION = 0.75;
    /** Golden-set minimum recall score. */
    public static final double GOLDEN_SET_MIN_RECALL = 0.75;
    /** Load-test maximum p99 latency (seconds). */
    public static final int LOAD_TEST_MAX_P99_SECONDS = 10;
    /** Load-test maximum allowed error rate. */
    public static final double LOAD_TEST_MAX_ERROR_RATE = 0.01;
}
