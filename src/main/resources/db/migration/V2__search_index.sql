CREATE EXTENSION IF NOT EXISTS pg_trgm;

ALTER TABLE skill_embeddings
    ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'active';

CREATE INDEX IF NOT EXISTS idx_skill_embeddings_status
    ON skill_embeddings (status);

CREATE TABLE IF NOT EXISTS skill_search_index (
    skill_id TEXT PRIMARY KEY,
    canonical_name TEXT NOT NULL,
    description TEXT,
    category TEXT,
    status TEXT NOT NULL DEFAULT 'active',
    alias_text TEXT,
    search_vector tsvector,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_skill_search_vector
    ON skill_search_index USING GIN (search_vector);

CREATE INDEX IF NOT EXISTS idx_skill_search_name_trgm
    ON skill_search_index USING GIN (canonical_name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_skill_search_alias_trgm
    ON skill_search_index USING GIN (alias_text gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_skill_search_status
    ON skill_search_index (status);
