CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE IF NOT EXISTS skill_embeddings (
    skill_id TEXT PRIMARY KEY,
    embedding vector(1024) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS alias_embeddings (
    alias_id TEXT PRIMARY KEY,
    alias_embedding vector(1024) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS locale_config (
    locale VARCHAR(16) PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    coverage_pct INTEGER NOT NULL DEFAULT 0 CHECK (coverage_pct BETWEEN 0 AND 100)
);

CREATE SEQUENCE IF NOT EXISTS graph_version_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS graph_changelog (
    graph_version BIGINT PRIMARY KEY DEFAULT nextval('graph_version_seq'),
    actor VARCHAR(128) NOT NULL,
    mutation_type VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id TEXT NOT NULL,
    diff_payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_skills_embedding
    ON skill_embeddings USING hnsw (embedding vector_cosine_ops);

CREATE INDEX IF NOT EXISTS idx_aliases_embedding
    ON alias_embeddings USING hnsw (alias_embedding vector_cosine_ops);

CREATE INDEX IF NOT EXISTS idx_skills_name_trgm
    ON skill_embeddings USING gin (skill_id gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_changelog_version
    ON graph_changelog (graph_version);
