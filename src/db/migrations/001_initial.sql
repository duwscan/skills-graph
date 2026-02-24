-- Skills Graph: Initial Schema Migration
-- Reference: ARCHITECTURE.md §2.8

-- Extensions are created by docker/init.sql, but ensure they exist
CREATE EXTENSION IF NOT EXISTS ltree;
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Monotonic version sequence for changelog
CREATE SEQUENCE IF NOT EXISTS graph_version_seq;

-- Core skills table
CREATE TABLE IF NOT EXISTS skills (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_id TEXT UNIQUE NOT NULL,
    canonical_name TEXT NOT NULL,
    slug TEXT UNIQUE NOT NULL,
    description TEXT,
    status TEXT NOT NULL DEFAULT 'candidate'
        CHECK (status IN ('candidate', 'active', 'deprecated', 'merged')),
    category TEXT
        CHECK (category IN ('domain', 'tool', 'certification', 'soft_skill', 'methodology', 'language')),
    path ltree NOT NULL,
    embedding vector(1024),
    version INT NOT NULL DEFAULT 1,
    source TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    metadata JSONB NOT NULL DEFAULT '{}'
);

-- Aliases (multi-locale surface forms)
CREATE TABLE IF NOT EXISTS skill_aliases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    skill_id UUID NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    surface_form TEXT NOT NULL,
    locale TEXT NOT NULL DEFAULT 'en',
    source TEXT NOT NULL DEFAULT 'curated'
        CHECK (source IN ('curated', 'llm_discovered', 'user_submitted')),
    is_primary BOOLEAN NOT NULL DEFAULT false,
    alias_embedding vector(1024),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Relationships between skills
CREATE TABLE IF NOT EXISTS skill_relationships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_skill_id UUID NOT NULL REFERENCES skills(id),
    target_skill_id UUID NOT NULL REFERENCES skills(id),
    relationship_type TEXT NOT NULL
        CHECK (relationship_type IN ('parent_of', 'child_of', 'related_to', 'requires', 'superseded_by')),
    confidence FLOAT NOT NULL DEFAULT 1.0 CHECK (confidence >= 0 AND confidence <= 1),
    weight FLOAT NOT NULL DEFAULT 1.0 CHECK (weight >= 0 AND weight <= 1),
    provenance TEXT NOT NULL DEFAULT 'human_curated'
        CHECK (provenance IN ('human_curated', 'llm_predicted', 'embedding_similarity', 'empirical')),
    status TEXT NOT NULL DEFAULT 'active'
        CHECK (status IN ('active', 'pending_review', 'rejected', 'deprecated')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (source_skill_id, target_skill_id, relationship_type),
    CHECK (source_skill_id != target_skill_id)
);

-- Co-occurrence tracking (empirical evidence)
CREATE TABLE IF NOT EXISTS skill_co_occurrences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    skill_a_id UUID NOT NULL REFERENCES skills(id),
    skill_b_id UUID NOT NULL REFERENCES skills(id),
    co_occurrence_count INT NOT NULL DEFAULT 1,
    source_type_counts JSONB NOT NULL DEFAULT '{}',
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (skill_a_id, skill_b_id),
    CHECK (skill_a_id < skill_b_id)
);

-- Locale configuration
CREATE TABLE IF NOT EXISTS locale_config (
    locale TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    coverage_pct FLOAT NOT NULL DEFAULT 0
);

-- Graph changelog for versioning and CDC
CREATE TABLE IF NOT EXISTS graph_changelog (
    id BIGSERIAL PRIMARY KEY,
    graph_version BIGINT NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor TEXT NOT NULL,
    mutation_type TEXT NOT NULL
        CHECK (mutation_type IN ('skill_created', 'skill_updated', 'skill_deprecated',
               'skill_merged', 'alias_added', 'alias_removed', 'edge_created',
               'edge_updated', 'edge_deprecated')),
    entity_type TEXT NOT NULL,
    entity_id UUID NOT NULL,
    diff_payload JSONB NOT NULL DEFAULT '{}'
);

-- Indexes: skills
CREATE INDEX IF NOT EXISTS idx_skills_embedding ON skills USING hnsw (embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS idx_skills_path ON skills USING gist (path);
CREATE INDEX IF NOT EXISTS idx_skills_name_trgm ON skills USING gin (canonical_name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_skills_status ON skills (status) WHERE status = 'active';
CREATE INDEX IF NOT EXISTS idx_skills_slug ON skills (slug);

-- Indexes: aliases
CREATE INDEX IF NOT EXISTS idx_aliases_embedding ON skill_aliases USING hnsw (alias_embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS idx_aliases_skill_id ON skill_aliases (skill_id);
CREATE INDEX IF NOT EXISTS idx_aliases_surface_trgm ON skill_aliases USING gin (surface_form gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_aliases_locale ON skill_aliases (locale);

-- Indexes: relationships
CREATE INDEX IF NOT EXISTS idx_relationships_source ON skill_relationships (source_skill_id);
CREATE INDEX IF NOT EXISTS idx_relationships_target ON skill_relationships (target_skill_id);
CREATE INDEX IF NOT EXISTS idx_relationships_type ON skill_relationships (relationship_type);
CREATE INDEX IF NOT EXISTS idx_relationships_provenance ON skill_relationships (provenance);

-- Indexes: co-occurrences
CREATE INDEX IF NOT EXISTS idx_co_occurrences_skill_a ON skill_co_occurrences (skill_a_id);
CREATE INDEX IF NOT EXISTS idx_co_occurrences_skill_b ON skill_co_occurrences (skill_b_id);
CREATE INDEX IF NOT EXISTS idx_co_occurrences_count ON skill_co_occurrences (co_occurrence_count DESC);

-- Indexes: changelog
CREATE INDEX IF NOT EXISTS idx_changelog_version ON graph_changelog (graph_version);
CREATE INDEX IF NOT EXISTS idx_changelog_entity ON graph_changelog (entity_type, entity_id);
