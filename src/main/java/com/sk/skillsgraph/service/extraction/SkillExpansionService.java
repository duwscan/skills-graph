package com.sk.skillsgraph.service.extraction;

import com.sk.skillsgraph.config.AppConstants;
import com.sk.skillsgraph.dto.ExtractionDto.ExtractedSkill;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

@Service
public class SkillExpansionService {

    private final Neo4jClient neo4jClient;

    public SkillExpansionService(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    public List<ExtractedSkill> expand(List<ExtractedSkill> extractedSkills, int depth) {
        if (extractedSkills == null || extractedSkills.isEmpty() || depth <= 0) {
            return List.of();
        }
        int maxDepth = Math.max(1, depth);

        Map<String, ExtractedSkill> directById = new HashMap<>();
        for (ExtractedSkill skill : extractedSkills) {
            directById.put(skill.skillId(), skill);
        }

        List<ExtractedSkill> expanded = new ArrayList<>();
        Map<String, ExtractedSkill> bestExpandedById = new HashMap<>();

        for (ExtractedSkill source : extractedSkills) {
            if (source.skillId() == null || source.skillId().isBlank()) {
                continue;
            }
            String expansionQuery = """
                            MATCH (source:Skill {id: $skillId})
                            CALL {
                                WITH source
                                MATCH (ancestor:Skill)-[:PARENT_OF*1..%d]->(source)
                                WHERE coalesce(ancestor.status, 'active') = 'active'
                                RETURN ancestor AS related, 'parent' AS expansionType
                                UNION
                                WITH source
                                MATCH (source)-[:PARENT_OF*1..%d]->(descendant:Skill)
                                WHERE coalesce(descendant.status, 'active') = 'active'
                                RETURN descendant AS related, 'child' AS expansionType
                                UNION
                                WITH source
                                MATCH (parent:Skill)-[:PARENT_OF]->(source)
                                MATCH (parent)-[:PARENT_OF]->(sibling:Skill)
                                WHERE sibling.id <> source.id
                                  AND coalesce(sibling.status, 'active') = 'active'
                                RETURN sibling AS related, 'sibling' AS expansionType
                            }
                            RETURN related.id AS skillId,
                                   related.externalId AS externalId,
                                   related.canonicalName AS canonicalName,
                                   expansionType
                            """.formatted(maxDepth, maxDepth);
            List<Map<String, Object>> neighbors = new ArrayList<>(neo4jClient.query(expansionQuery)
                    .bind(source.skillId()).to("skillId")
                    .fetch()
                    .all());

            for (Map<String, Object> row : neighbors) {
                String skillId = asString(row.get("skillId"));
                if (skillId == null || skillId.isBlank() || directById.containsKey(skillId)) {
                    continue;
                }
                String externalId = asString(row.get("externalId"));
                String canonicalName = asString(row.get("canonicalName"));
                String expansionType = asString(row.get("expansionType"));
                double confidence = clamp(source.confidence() * AppConstants.EXTRACTION_EXPANSION_FACTOR);

                ExtractedSkill candidate = new ExtractedSkill(
                        skillId,
                        externalId,
                        canonicalName,
                        confidence,
                        List.of("Expanded from " + source.skillName() + " (" + expansionType + ")"),
                        source.proficiencyHint(),
                        source.contextType(),
                        source.section(),
                        true,
                        expansionType
                );

                bestExpandedById.compute(skillId, (id, existing) -> {
                    if (existing == null || candidate.confidence() > existing.confidence()) {
                        return candidate;
                    }
                    return existing;
                });
            }
        }

        expanded.addAll(bestExpandedById.values());
        expanded.sort(Comparator.comparingDouble(ExtractedSkill::confidence).reversed());
        return expanded;
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
