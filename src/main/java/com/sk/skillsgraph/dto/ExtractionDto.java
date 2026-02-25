package com.sk.skillsgraph.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sk.skillsgraph.config.AppConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class ExtractionDto {

    private ExtractionDto() {
    }

    public record ExtractionRequest(
            @NotBlank
            @Size(min = 1, max = AppConstants.EXTRACTION_MAX_TEXT_LENGTH)
            String text,
            @Valid ExtractionOptions options
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ExtractionOptions(
            Boolean expand,
            @JsonProperty("min_confidence")
            @DecimalMin("0.0")
            @DecimalMax("1.0")
            Double minConfidence,
            String locale,
            @JsonProperty("max_skills")
            @Min(1)
            @Max(AppConstants.PAGINATION_MAX_LIMIT)
            Integer maxSkills,
            @JsonProperty("expansion_depth")
            @Min(0)
            @Max(AppConstants.TRAVERSAL_MAX_DEPTH)
            Integer expansionDepth
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ExtractedSkill(
            @JsonProperty("skill_id")
            String skillId,
            @JsonProperty("external_id")
            String externalId,
            @JsonProperty("skill_name")
            String skillName,
            double confidence,
            List<String> evidence,
            @JsonProperty("proficiency_hint")
            String proficiencyHint,
            @JsonProperty("context_type")
            String contextType,
            String section,
            @JsonProperty("is_expanded")
            boolean isExpanded,
            @JsonProperty("expansion_type")
            String expansionType
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DiscoveredCandidate(
            @JsonProperty("surface_form")
            String surfaceForm,
            @JsonProperty("suggested_category")
            String suggestedCategory,
            String reason
    ) {
    }

    public record ExtractionMetadata(
            @JsonProperty("chunks_processed")
            int chunksProcessed,
            @JsonProperty("cache_hits")
            int cacheHits,
            @JsonProperty("cache_misses")
            int cacheMisses,
            @JsonProperty("processing_time_ms")
            long processingTimeMs,
            @JsonProperty("model_used")
            String modelUsed,
            @JsonProperty("document_type")
            String documentType,
            @JsonProperty("sections_detected")
            int sectionsDetected,
            @JsonProperty("total_llm_tokens")
            int totalLlmTokens,
            @JsonProperty("total_extracted_before_filter")
            int totalExtractedBeforeFilter
    ) {
    }

    public record ExtractionResponse(
            List<ExtractedSkill> skills,
            @JsonProperty("discovered_candidates")
            List<DiscoveredCandidate> discoveredCandidates,
            ExtractionMetadata metadata
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LlmExtractionOutput(
            @JsonProperty("extracted_skills")
            List<LlmExtractedSkill> extractedSkills,
            @JsonProperty("discovered_candidates")
            List<DiscoveredCandidate> discoveredCandidates
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LlmExtractedSkill(
            @JsonProperty("skill_id")
            String skillId,
            @JsonProperty("skill_name")
            String skillName,
            double confidence,
            List<String> evidence,
            @JsonProperty("proficiency_hint")
            String proficiencyHint,
            @JsonProperty("context_type")
            String contextType,
            String section
    ) {
    }
}
