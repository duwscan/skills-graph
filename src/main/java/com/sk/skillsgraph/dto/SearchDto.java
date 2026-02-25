package com.sk.skillsgraph.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sk.skillsgraph.dto.Enums.SkillCategory;
import com.sk.skillsgraph.dto.Enums.SkillStatus;
import java.util.List;

public final class SearchDto {

    private SearchDto() {
    }

    public enum MatchType {
        keyword,
        semantic,
        both
    }

    public record SearchFilters(
            SkillCategory category,
            SkillStatus status
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SearchResult(
            String id,
            @JsonProperty("external_id") String externalId,
            @JsonProperty("canonical_name") String canonicalName,
            String description,
            SkillCategory category,
            SkillStatus status,
            double score,
            @JsonProperty("match_type") MatchType matchType,
            String highlight
    ) {
    }

    public record SearchResponse(
            List<SearchResult> results,
            long total,
            @JsonProperty("query_time_ms") long queryTimeMs
    ) {
    }
}
