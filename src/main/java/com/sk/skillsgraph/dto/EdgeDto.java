package com.sk.skillsgraph.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sk.skillsgraph.dto.Enums.EdgeStatus;
import com.sk.skillsgraph.dto.Enums.Provenance;
import com.sk.skillsgraph.dto.Enums.RelationshipType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public final class EdgeDto {

    private EdgeDto() {
    }

    public record CreateEdgeRequest(
            @JsonProperty("source_skill_id")
            @NotBlank
            String sourceSkillId,
            @JsonProperty("target_skill_id")
            @NotBlank
            String targetSkillId,
            @JsonProperty("relationship_type")
            @NotNull
            RelationshipType relationshipType,
            @DecimalMin("0.0")
            @DecimalMax("1.0")
            Double confidence,
            Provenance provenance
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EdgeResponse(
            String id,
            @JsonProperty("source_skill_id") String sourceSkillId,
            @JsonProperty("target_skill_id") String targetSkillId,
            @JsonProperty("relationship_type") RelationshipType relationshipType,
            double confidence,
            Provenance provenance,
            EdgeStatus status,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt,
            @JsonProperty("target_skill_name") String targetSkillName
    ) {
    }
}
