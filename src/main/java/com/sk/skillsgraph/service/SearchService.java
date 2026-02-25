package com.sk.skillsgraph.service;

import com.sk.skillsgraph.config.AppConstants;
import com.sk.skillsgraph.dto.SearchDto.SearchFilters;
import com.sk.skillsgraph.dto.SearchDto.SearchResponse;
import com.sk.skillsgraph.dto.SearchDto.SearchResult;
import com.sk.skillsgraph.dto.SearchDto.MatchType;
import com.sk.skillsgraph.dto.Enums.SkillCategory;
import com.sk.skillsgraph.dto.Enums.SkillStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class SearchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchService.class);

    private final JdbcTemplate jdbcTemplate;
    private final Neo4jClient neo4jClient;

    public SearchService(JdbcTemplate jdbcTemplate, Neo4jClient neo4jClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.neo4jClient = neo4jClient;
    }

    public void setupSearchInfrastructure() {
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS skill_search_index (
                    skill_id TEXT PRIMARY KEY,
                    canonical_name TEXT NOT NULL,
                    description TEXT,
                    category TEXT,
                    status TEXT NOT NULL DEFAULT 'active',
                    alias_text TEXT,
                    search_vector tsvector,
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_skill_search_vector ON skill_search_index USING GIN (search_vector)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_skill_search_name_trgm ON skill_search_index USING GIN (canonical_name gin_trgm_ops)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_skill_search_alias_trgm ON skill_search_index USING GIN (alias_text gin_trgm_ops)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_skill_search_status ON skill_search_index (status)");
    }

    @Async
    public void indexSkillAsync(String skillId) {
        try {
            indexSkill(skillId);
        } catch (Exception exception) {
            LOGGER.warn("Failed to index skill for search {}", skillId, exception);
        }
    }

    public void indexSkill(String skillId) {
        Map<String, Object> row = neo4jClient.query("""
                        MATCH (s:Skill {id: $skillId})
                        OPTIONAL MATCH (s)-[:HAS_ALIAS]->(a:Alias)
                        WITH s, collect(coalesce(a.surfaceForm, '')) AS aliasForms
                        RETURN s.id AS id,
                               s.canonicalName AS canonicalName,
                               s.description AS description,
                               s.category AS category,
                               s.status AS status,
                               aliasForms AS aliasForms
                        LIMIT 1
                        """)
                .bind(skillId).to("skillId")
                .fetch()
                .one()
                .orElse(null);

        if (row == null) {
            removeSkill(skillId);
            return;
        }

        String canonicalName = asString(row.get("canonicalName"));
        String description = asString(row.get("description"));
        String category = asString(row.get("category"));
        String status = normalizeStatus(asString(row.get("status")));
        String aliasText = extractAliasText(row.get("aliasForms"));

        jdbcTemplate.update("""
                INSERT INTO skill_search_index(skill_id, canonical_name, description, category, status, alias_text, search_vector, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, CASE
                                            WHEN ? = 'active'
                                                THEN to_tsvector('simple', trim(coalesce(?, '') || ' ' || coalesce(?, '')))
                                            ELSE NULL
                                          END, NOW())
                ON CONFLICT (skill_id) DO UPDATE
                SET canonical_name = EXCLUDED.canonical_name,
                    description = EXCLUDED.description,
                    category = EXCLUDED.category,
                    status = EXCLUDED.status,
                    alias_text = EXCLUDED.alias_text,
                    search_vector = CASE
                                      WHEN EXCLUDED.status = 'active'
                                          THEN to_tsvector('simple', trim(coalesce(EXCLUDED.canonical_name, '') || ' ' || coalesce(EXCLUDED.alias_text, '')))
                                      ELSE NULL
                                    END,
                    updated_at = NOW()
                """,
                skillId,
                canonicalName,
                description,
                category,
                status,
                aliasText,
                status,
                canonicalName,
                aliasText
        );
    }

    public void removeSkill(String skillId) {
        jdbcTemplate.update("""
                UPDATE skill_search_index
                SET status = 'deprecated',
                    search_vector = NULL,
                    updated_at = NOW()
                WHERE skill_id = ?
                """, skillId);
    }

    public int reindexAllActiveSkills() {
        List<String> activeSkillIds = new ArrayList<>(neo4jClient.query("""
                        MATCH (s:Skill)
                        WHERE coalesce(s.status, 'active') = 'active'
                        RETURN s.id AS id
                        ORDER BY s.canonicalName ASC
                        """)
                .fetchAs(String.class)
                .mappedBy((typeSystem, record) -> record.get("id").asString())
                .all());

        int indexed = 0;
        for (String skillId : activeSkillIds) {
            indexSkill(skillId);
            indexed++;
            if (indexed % AppConstants.EMBEDDING_BATCH_SIZE == 0 || indexed == activeSkillIds.size()) {
                LOGGER.info("Indexed {}/{} skills for full-text search", indexed, activeSkillIds.size());
            }
        }
        return indexed;
    }

    public SearchResponse search(String query, SearchFilters filters, int limit) {
        long startedAt = System.currentTimeMillis();
        if (query == null || query.isBlank()) {
            return new SearchResponse(List.of(), 0, 0);
        }

        int normalizedLimit = Math.max(1, Math.min(limit, AppConstants.PAGINATION_MAX_LIMIT));
        String normalizedQuery = query.trim();
        String category = filters == null || filters.category() == null ? null : filters.category().name();
        String status = filters == null || filters.status() == null ? null : filters.status().name();

        List<SearchResult> results = jdbcTemplate.query("""
                        SELECT skill_id,
                               canonical_name,
                               description,
                               category,
                               status,
                               GREATEST(
                                   COALESCE(ts_rank(search_vector, plainto_tsquery('simple', ?)), 0),
                                   COALESCE(similarity(canonical_name, ?), 0),
                                   COALESCE(similarity(alias_text, ?), 0)
                               ) AS score
                        FROM skill_search_index
                        WHERE search_vector IS NOT NULL
                          AND (? IS NULL OR category = ?)
                          AND (? IS NULL OR status = ?)
                          AND (
                              search_vector @@ plainto_tsquery('simple', ?)
                              OR canonical_name % ?
                              OR alias_text % ?
                          )
                        ORDER BY score DESC, canonical_name ASC
                        LIMIT ?
                        """,
                (rs, rowNum) -> new SearchResult(
                        rs.getString("skill_id"),
                        null,
                        rs.getString("canonical_name"),
                        rs.getString("description"),
                        parseCategory(rs.getString("category")),
                        parseStatus(rs.getString("status")),
                        rs.getDouble("score"),
                        MatchType.keyword,
                        rs.getString("canonical_name")
                ),
                normalizedQuery,
                normalizedQuery,
                normalizedQuery,
                category,
                category,
                status,
                status,
                normalizedQuery,
                normalizedQuery,
                normalizedQuery,
                normalizedLimit
        );

        long elapsed = System.currentTimeMillis() - startedAt;
        return new SearchResponse(results, results.size(), elapsed);
    }

    public void updateSkillStatus(String skillId, String status) {
        jdbcTemplate.update("""
                UPDATE skill_search_index
                SET status = ?,
                    search_vector = CASE WHEN ? = 'active' THEN search_vector ELSE NULL END,
                    updated_at = NOW()
                WHERE skill_id = ?
                """, status, status, skillId);
    }

    private String extractAliasText(Object value) {
        if (!(value instanceof List<?> aliases)) {
            return "";
        }
        List<String> normalized = new ArrayList<>();
        for (Object alias : aliases) {
            String asString = asString(alias);
            if (asString != null && !asString.isBlank()) {
                normalized.add(asString.trim());
            }
        }
        return String.join(" ", normalized);
    }

    private SkillCategory parseCategory(String value) {
        if (value == null || value.isBlank()) {
            return SkillCategory.domain;
        }
        try {
            return SkillCategory.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return SkillCategory.domain;
        }
    }

    private SkillStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return SkillStatus.candidate;
        }
        try {
            return SkillStatus.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return SkillStatus.candidate;
        }
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "active";
        }
        return status.toLowerCase(Locale.ROOT);
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
