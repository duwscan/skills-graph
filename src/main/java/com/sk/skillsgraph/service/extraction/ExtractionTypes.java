package com.sk.skillsgraph.service.extraction;

import java.util.List;

public final class ExtractionTypes {

    private ExtractionTypes() {
    }

    public record SectionInfo(
            String name,
            String type,
            double weight,
            int startOffset,
            int endOffset
    ) {
    }

    public record DocumentAnalysis(
            String documentType,
            List<SectionInfo> sections
    ) {
    }

    public record Chunk(
            String text,
            int index,
            int startOffset,
            int endOffset,
            int tokenEstimate,
            SectionInfo section
    ) {
    }

    public record CandidateSkill(
            String id,
            String externalId,
            String canonicalName,
            String description,
            double similarity
    ) {
    }

    public record ChunkResult(
            List<com.sk.skillsgraph.dto.ExtractionDto.ExtractedSkill> extractedSkills,
            List<com.sk.skillsgraph.dto.ExtractionDto.DiscoveredCandidate> discoveredCandidates,
            boolean cacheHit,
            String modelUsed,
            int llmTokens
    ) {
    }
}
