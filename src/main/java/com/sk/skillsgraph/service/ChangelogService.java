package com.sk.skillsgraph.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sk.skillsgraph.config.AppConstants;
import com.sk.skillsgraph.repository.SkillRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ChangelogService {

    public enum MutationType {
        skill_created,
        skill_updated,
        skill_deprecated,
        skill_merged,
        alias_added,
        alias_updated,
        alias_removed,
        edge_created,
        edge_updated,
        edge_deprecated
    }

    public record ChangelogEntry(
            long graphVersion,
            String actor,
            String mutationType,
            String entityType,
            String entityId,
            Map<String, Object> diffPayload,
            Instant createdAt
    ) {
    }

    public record VersionInfo(
            long graphVersion,
            long totalSkills,
            long totalEdges
    ) {
    }

    private final JdbcTemplate jdbcTemplate;
    private final Neo4jClient neo4jClient;
    private final SkillRepository skillRepository;
    private final ObjectMapper objectMapper;

    public ChangelogService(
            JdbcTemplate jdbcTemplate,
            Neo4jClient neo4jClient,
            SkillRepository skillRepository,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.neo4jClient = neo4jClient;
        this.skillRepository = skillRepository;
        this.objectMapper = objectMapper;
    }

    public long record(String actor, MutationType mutationType, String entityType, String entityId, Map<String, Object> diffPayload) {
        String serializedPayload = toJson(diffPayload == null ? Map.of() : diffPayload);

        Long graphVersion = jdbcTemplate.queryForObject("""
                INSERT INTO graph_changelog(actor, mutation_type, entity_type, entity_id, diff_payload)
                VALUES (?, ?, ?, ?, CAST(? AS jsonb))
                RETURNING graph_version
                """, Long.class, actor, mutationType.name(), entityType, entityId, serializedPayload);

        Map<String, Object> notification = Map.of(
                "graph_version", graphVersion == null ? 0L : graphVersion,
                "mutation_type", mutationType.name(),
                "entity_type", entityType,
                "entity_id", entityId
        );
        jdbcTemplate.update("SELECT pg_notify('graph_changes', CAST(? AS text))", toJson(notification));

        return graphVersion == null ? 0L : graphVersion;
    }

    public List<ChangelogEntry> list(Long since, Integer limit) {
        long sinceVersion = since == null ? 0L : Math.max(0L, since);
        int pageSize = limit == null ? AppConstants.CHANGELOG_DEFAULT_LIMIT : Math.max(1, limit);

        return jdbcTemplate.query("""
                SELECT graph_version, actor, mutation_type, entity_type, entity_id, diff_payload::text AS diff_payload, created_at
                FROM graph_changelog
                WHERE graph_version > ?
                ORDER BY graph_version ASC
                LIMIT ?
                """, (rs, rowNum) -> new ChangelogEntry(
                rs.getLong("graph_version"),
                rs.getString("actor"),
                rs.getString("mutation_type"),
                rs.getString("entity_type"),
                rs.getString("entity_id"),
                fromJsonMap(rs.getString("diff_payload")),
                rs.getTimestamp("created_at").toInstant()
        ), sinceVersion, pageSize);
    }

    public VersionInfo getVersion() {
        Long graphVersion = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(graph_version), 0) FROM graph_changelog",
                Long.class
        );

        long totalSkills = skillRepository.count();

        Long totalEdges = neo4jClient.query("""
                MATCH ()-[r]->()
                WHERE type(r) IN ['PARENT_OF', 'RELATED_TO', 'REQUIRES', 'SUPERSEDED_BY']
                RETURN count(r) AS total
                """)
                .fetchAs(Long.class)
                .one()
                .orElse(0L);

        return new VersionInfo(graphVersion == null ? 0L : graphVersion, totalSkills, totalEdges);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize changelog payload", exception);
        }
    }

    private Map<String, Object> fromJsonMap(String payload) {
        if (payload == null || payload.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payload, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            return Map.of("raw", payload);
        }
    }
}
