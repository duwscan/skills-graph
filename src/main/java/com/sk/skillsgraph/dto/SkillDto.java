package com.sk.skillsgraph.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sk.skillsgraph.dto.Enums.SkillCategory;
import com.sk.skillsgraph.dto.Enums.SkillStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class SkillDto {

    private SkillDto() {
    }

    public record CreateSkillRequest(
            @JsonProperty("canonical_name")
            @NotBlank
            @Size(max = 200)
            String canonicalName,
            @Size(max = 5000)
            String description,
            SkillCategory category,
            SkillStatus status,
            Map<String, Object> metadata
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UpdateSkillRequest(
            @JsonProperty("canonical_name")
            @Size(max = 200)
            String canonicalName,
            @Size(max = 5000)
            String description,
            SkillCategory category,
            SkillStatus status,
            Map<String, Object> metadata
    ) {
    }

    public record SkillResponse(
            String id,
            @JsonProperty("external_id") String externalId,
            @JsonProperty("canonical_name") String canonicalName,
            String slug,
            String description,
            SkillCategory category,
            SkillStatus status,
            Integer version,
            String source,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt,
            List<AliasDto.AliasResponse> aliases,
            List<EdgeDto.EdgeResponse> relationships
    ) {
    }

    public record SkillSummary(
            String id,
            @JsonProperty("external_id") String externalId,
            @JsonProperty("canonical_name") String canonicalName,
            String slug,
            SkillCategory category,
            SkillStatus status
    ) {
    }

    public record SkillPathNode(
            SkillSummary skill,
            int depth
    ) {
    }

    public record ListSkillsResponse(
            List<SkillSummary> items,
            long total,
            int limit,
            int offset
    ) {
    }
}
