package com.sk.skillsgraph.service;

import com.sk.skillsgraph.dto.EdgeDto.CreateEdgeRequest;
import com.sk.skillsgraph.dto.EdgeDto.EdgeResponse;
import com.sk.skillsgraph.dto.Enums.EdgeStatus;
import com.sk.skillsgraph.dto.Enums.Provenance;
import com.sk.skillsgraph.dto.Enums.RelationshipType;
import com.sk.skillsgraph.service.ChangelogService.MutationType;
import com.sk.skillsgraph.util.AppExceptions.EdgeNotFoundException;
import com.sk.skillsgraph.util.AppExceptions.ValidationException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EdgeService {

    private final Neo4jClient neo4jClient;
    private final SkillService skillService;
    private final GuardrailService guardrailService;
    private final ChangelogService changelogService;

    public EdgeService(
            Neo4jClient neo4jClient,
            SkillService skillService,
            GuardrailService guardrailService,
            ChangelogService changelogService
    ) {
        this.neo4jClient = neo4jClient;
        this.skillService = skillService;
        this.guardrailService = guardrailService;
        this.changelogService = changelogService;
    }

    @Transactional
    public EdgeResponse create(CreateEdgeRequest input) {
        String sourceSkillId = skillService.resolveSkillId(input.sourceSkillId());
        String targetSkillId = skillService.resolveSkillId(input.targetSkillId());
        CreateEdgeRequest normalizedInput = new CreateEdgeRequest(
                sourceSkillId,
                targetSkillId,
                input.relationshipType(),
                input.confidence(),
                input.provenance()
        );
        guardrailService.validateEdge(normalizedInput);

        String relationshipLabel = guardrailService.toRelationshipLabel(input.relationshipType());
        String edgeId = UUID.randomUUID().toString();
        double confidence = input.confidence() == null ? 1.0D : input.confidence();
        Provenance provenance = input.provenance() == null ? Provenance.human_curated : input.provenance();
        String now = Instant.now().toString();

        neo4jClient.query("""
                        MATCH (source:Skill {id: $sourceSkillId}), (target:Skill {id: $targetSkillId})
                        CREATE (source)-[r:%s {
                            id: $id,
                            confidence: $confidence,
                            provenance: $provenance,
                            status: 'active',
                            createdAt: datetime($now),
                            updatedAt: datetime($now)
                        }]->(target)
                        """.formatted(relationshipLabel))
                .bind(sourceSkillId).to("sourceSkillId")
                .bind(targetSkillId).to("targetSkillId")
                .bind(edgeId).to("id")
                .bind(confidence).to("confidence")
                .bind(provenance.name()).to("provenance")
                .bind(now).to("now")
                .run();

        changelogService.record(
                "system",
                MutationType.edge_created,
                "edge",
                edgeId,
                Map.of(
                        "source_skill_id", sourceSkillId,
                        "target_skill_id", targetSkillId,
                        "relationship_type", input.relationshipType().name()
                )
        );

        return getByEdgeId(edgeId);
    }

    public Map<String, List<EdgeResponse>> getBySkill(String idOrExternalIdOrSlug) {
        String skillId = skillService.resolveSkillId(idOrExternalIdOrSlug);
        List<EdgeResponse> edges = neo4jClient.query("""
                        MATCH (s:Skill {id: $skillId})-[r]-(other:Skill)
                        WHERE type(r) IN ['PARENT_OF', 'RELATED_TO', 'REQUIRES', 'SUPERSEDED_BY']
                        RETURN r.id AS id,
                               startNode(r).id AS sourceSkillId,
                               endNode(r).id AS targetSkillId,
                               type(r) AS relationshipType,
                               coalesce(r.confidence, 1.0) AS confidence,
                               coalesce(r.provenance, 'human_curated') AS provenance,
                               coalesce(r.status, 'active') AS status,
                               r.createdAt AS createdAt,
                               r.updatedAt AS updatedAt,
                               other.canonicalName AS targetSkillName
                        """)
                .bind(skillId).to("skillId")
                .fetch()
                .all()
                .stream()
                .map(this::toEdgeResponse)
                .toList();

        return edges.stream().collect(Collectors.groupingBy(
                edge -> edge.relationshipType().name(),
                LinkedHashMap::new,
                Collectors.toList()
        ));
    }

    public List<EdgeResponse> getRelated(String idOrExternalIdOrSlug) {
        String skillId = skillService.resolveSkillId(idOrExternalIdOrSlug);

        return neo4jClient.query("""
                        MATCH (s:Skill {id: $skillId})-[r]-(other:Skill)
                        WHERE type(r) IN ['RELATED_TO', 'REQUIRES']
                        RETURN r.id AS id,
                               startNode(r).id AS sourceSkillId,
                               endNode(r).id AS targetSkillId,
                               type(r) AS relationshipType,
                               coalesce(r.confidence, 1.0) AS confidence,
                               coalesce(r.provenance, 'human_curated') AS provenance,
                               coalesce(r.status, 'active') AS status,
                               r.createdAt AS createdAt,
                               r.updatedAt AS updatedAt,
                               other.canonicalName AS targetSkillName
                        ORDER BY targetSkillName ASC
                        """)
                .bind(skillId).to("skillId")
                .fetch()
                .all()
                .stream()
                .map(this::toEdgeResponse)
                .toList();
    }

    @Transactional
    public EdgeResponse deprecate(String edgeId) {
        Long updated = neo4jClient.query("""
                        MATCH ()-[r]->()
                        WHERE r.id = $edgeId
                        SET r.status = 'deprecated',
                            r.updatedAt = datetime($now)
                        RETURN count(r) AS total
                        """)
                .bind(edgeId).to("edgeId")
                .bind(Instant.now().toString()).to("now")
                .fetchAs(Long.class)
                .one()
                .orElse(0L);

        if (updated == null || updated == 0L) {
            throw new EdgeNotFoundException(edgeId);
        }

        changelogService.record(
                "system",
                MutationType.edge_deprecated,
                "edge",
                edgeId,
                Map.of("status", "deprecated")
        );
        return getByEdgeId(edgeId);
    }

    @Transactional
    public void delete(String edgeId) {
        EdgeResponse existing = getByEdgeId(edgeId);
        if (!(existing.status() == EdgeStatus.pending_review || existing.status() == EdgeStatus.rejected)) {
            throw new ValidationException("Only pending_review or rejected edges can be deleted", 422);
        }

        neo4jClient.query("""
                        MATCH ()-[r]->()
                        WHERE r.id = $edgeId
                        DELETE r
                        """)
                .bind(edgeId).to("edgeId")
                .run();

        changelogService.record("system", MutationType.edge_updated, "edge", edgeId, Map.of("deleted", true));
    }

    private EdgeResponse getByEdgeId(String edgeId) {
        return neo4jClient.query("""
                        MATCH (source:Skill)-[r]->(target:Skill)
                        WHERE r.id = $edgeId
                        RETURN r.id AS id,
                               source.id AS sourceSkillId,
                               target.id AS targetSkillId,
                               type(r) AS relationshipType,
                               coalesce(r.confidence, 1.0) AS confidence,
                               coalesce(r.provenance, 'human_curated') AS provenance,
                               coalesce(r.status, 'active') AS status,
                               r.createdAt AS createdAt,
                               r.updatedAt AS updatedAt,
                               target.canonicalName AS targetSkillName
                        LIMIT 1
                        """)
                .bind(edgeId).to("edgeId")
                .fetch()
                .one()
                .map(this::toEdgeResponse)
                .orElseThrow(() -> new EdgeNotFoundException(edgeId));
    }

    private EdgeResponse toEdgeResponse(Map<String, Object> row) {
        return new EdgeResponse(
                asString(row.get("id")),
                asString(row.get("sourceSkillId")),
                asString(row.get("targetSkillId")),
                parseRelationshipType(asString(row.get("relationshipType"))),
                toDouble(row.get("confidence"), 1.0D),
                parseProvenance(asString(row.get("provenance"))),
                parseEdgeStatus(asString(row.get("status"))),
                toInstant(row.get("createdAt")),
                toInstant(row.get("updatedAt")),
                asString(row.get("targetSkillName"))
        );
    }

    private RelationshipType parseRelationshipType(String value) {
        if (value == null || value.isBlank()) {
            return RelationshipType.related_to;
        }
        try {
            return RelationshipType.valueOf(value.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return RelationshipType.related_to;
        }
    }

    private Provenance parseProvenance(String value) {
        if (value == null || value.isBlank()) {
            return Provenance.human_curated;
        }
        try {
            return Provenance.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return Provenance.human_curated;
        }
    }

    private EdgeStatus parseEdgeStatus(String value) {
        if (value == null || value.isBlank()) {
            return EdgeStatus.active;
        }
        try {
            return EdgeStatus.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return EdgeStatus.active;
        }
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private double toDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof ZonedDateTime zonedDateTime) {
            return zonedDateTime.toInstant();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toInstant(ZoneOffset.UTC);
        }
        try {
            return Instant.parse(value.toString());
        } catch (Exception ignored) {
            return null;
        }
    }
}
