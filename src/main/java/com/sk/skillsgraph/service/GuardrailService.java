package com.sk.skillsgraph.service;

import com.sk.skillsgraph.dto.EdgeDto.CreateEdgeRequest;
import com.sk.skillsgraph.dto.Enums.RelationshipType;
import com.sk.skillsgraph.dto.Enums.SkillCategory;
import com.sk.skillsgraph.util.AppExceptions.CycleDetectedException;
import com.sk.skillsgraph.util.AppExceptions.ValidationException;
import java.text.Normalizer;
import java.util.Locale;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

@Service
public class GuardrailService {

    private final Neo4jClient neo4jClient;

    public GuardrailService(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    public void validateEdge(CreateEdgeRequest input) {
        checkSelfEdge(input.sourceSkillId(), input.targetSkillId());
        checkDuplicateEdge(input.sourceSkillId(), input.targetSkillId(), input.relationshipType());
        checkCycle(input.sourceSkillId(), input.targetSkillId(), input.relationshipType());
    }

    public void validateActivation(String skillId, SkillCategory category) {
        OrphanCheckResult result = checkOrphan(skillId, category);
        if (!result.valid()) {
            throw new ValidationException(
                    "Non-root active skills must have at least one parent_of edge",
                    422
            );
        }
    }

    public void checkSelfEdge(String sourceSkillId, String targetSkillId) {
        if (sourceSkillId != null && sourceSkillId.equals(targetSkillId)) {
            throw new ValidationException("source_skill_id and target_skill_id must be different", 422);
        }
    }

    public void checkDuplicateEdge(String sourceSkillId, String targetSkillId, RelationshipType relationshipType) {
        String relType = toRelationshipLabel(relationshipType);
        Long count = neo4jClient.query("""
                        MATCH (source:Skill {id: $sourceId})-[r:%s]->(target:Skill {id: $targetId})
                        RETURN count(r) AS edgeCount
                        """.formatted(relType))
                .bind(sourceSkillId).to("sourceId")
                .bind(targetSkillId).to("targetId")
                .fetchAs(Long.class)
                .one()
                .orElse(0L);

        if (count != null && count > 0) {
            throw new ValidationException("Duplicate edge already exists for source, target, and relationship_type", 422);
        }
    }

    public void checkCycle(String sourceSkillId, String targetSkillId, RelationshipType relationshipType) {
        if (relationshipType != RelationshipType.parent_of) {
            return;
        }

        Boolean wouldCreateCycle = neo4jClient.query("""
                        MATCH path = (target:Skill {id: $targetId})-[:PARENT_OF*1..10]->(source:Skill {id: $sourceId})
                        RETURN count(path) > 0 AS wouldCreateCycle
                        """)
                .bind(sourceSkillId).to("sourceId")
                .bind(targetSkillId).to("targetId")
                .fetchAs(Boolean.class)
                .one()
                .orElse(false);

        if (Boolean.TRUE.equals(wouldCreateCycle)) {
            throw new CycleDetectedException("Creating this parent_of edge would create a cycle");
        }
    }

    public OrphanCheckResult checkOrphan(String skillId, SkillCategory category) {
        if (category == SkillCategory.root) {
            return new OrphanCheckResult(true, true);
        }

        Long incomingParentCount = neo4jClient.query("""
                        MATCH (s:Skill {id: $skillId})
                        OPTIONAL MATCH (parent:Skill)-[:PARENT_OF]->(s)
                        RETURN count(parent) AS incomingParentCount
                        """)
                .bind(skillId).to("skillId")
                .fetchAs(Long.class)
                .one()
                .orElse(0L);

        boolean hasParent = incomingParentCount != null && incomingParentCount > 0L;
        return new OrphanCheckResult(hasParent, false);
    }

    public String sanitizeName(String name) {
        if (name == null) {
            return "";
        }
        String cleaned = name.replaceAll("<[^>]*>", " ");
        cleaned = Normalizer.normalize(cleaned, Normalizer.Form.NFC);
        cleaned = cleaned.trim().replaceAll("\\s+", " ");
        return cleaned;
    }

    public String toRelationshipLabel(RelationshipType relationshipType) {
        if (relationshipType == null) {
            throw new ValidationException("relationship_type is required", 400);
        }
        return relationshipType.name().toUpperCase(Locale.ROOT);
    }

    public record OrphanCheckResult(boolean valid, boolean isRoot) {
    }
}
