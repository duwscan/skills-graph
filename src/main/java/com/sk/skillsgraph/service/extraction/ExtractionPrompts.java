package com.sk.skillsgraph.service.extraction;

import com.sk.skillsgraph.config.AppConstants;
import com.sk.skillsgraph.service.extraction.ExtractionTypes.CandidateSkill;
import com.sk.skillsgraph.service.extraction.ExtractionTypes.Chunk;
import java.util.List;

public final class ExtractionPrompts {

    private ExtractionPrompts() {
    }

    public static final String SYSTEM_PROMPT = """
            You are a skills extraction engine.
            Extract only skills that exist in the provided candidate list.
            Do not invent skill IDs. If uncertain, lower confidence or skip.
            Return strict JSON matching the provided schema.

            Confidence rules:
            - Explicit mention in text: 0.8-1.0
            - Strong implied mention: 0.6-0.8
            - Weak hint: 0.4-0.6
            - Never output below 0.3

            Few-shot examples:
            Input: "Built ML pipelines in Python and TensorFlow"
            Output: extracted_skills includes Python/TensorFlow with explicit evidence.

            Input: "Shipped production inference APIs and model monitoring"
            Output: extracted_skills may include MLOps-related skill from candidates with lower confidence.

            Input: "The office has free snacks and team events"
            Output: extracted_skills should be empty.
            """;

    public static String buildPrompt(Chunk chunk, List<CandidateSkill> candidates) {
        List<CandidateSkill> limited = candidates == null
                ? List.of()
                : candidates.stream().limit(AppConstants.RAG_CANDIDATE_LIMIT).toList();

        StringBuilder builder = new StringBuilder();
        builder.append("Candidate skills (ID -> name):\n");
        if (limited.isEmpty()) {
            builder.append("(none)\n");
        } else {
            for (CandidateSkill candidate : limited) {
                builder.append("[")
                        .append(candidate.id())
                        .append("] ")
                        .append(candidate.canonicalName());
                if (candidate.description() != null && !candidate.description().isBlank()) {
                    builder.append(" - ").append(candidate.description());
                }
                builder.append('\n');
            }
        }

        builder.append("\nChunk metadata:\n");
        builder.append("- chunk_index: ").append(chunk.index()).append('\n');
        if (chunk.section() != null) {
            builder.append("- section_name: ").append(chunk.section().name()).append('\n');
            builder.append("- section_type: ").append(chunk.section().type()).append('\n');
        } else {
            builder.append("- section_name: generic\n");
        }

        builder.append("\nChunk text:\n");
        builder.append(chunk.text()).append('\n');

        builder.append("\nReturn JSON with fields: extracted_skills[], discovered_candidates[].\n");
        return builder.toString();
    }
}
