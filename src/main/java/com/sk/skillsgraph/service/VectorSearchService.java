package com.sk.skillsgraph.service;

import com.sk.skillsgraph.config.AppConstants;
import com.sk.skillsgraph.dto.Enums.SkillCategory;
import com.sk.skillsgraph.dto.Enums.SkillStatus;
import com.sk.skillsgraph.dto.SearchDto.MatchType;
import com.sk.skillsgraph.dto.SearchDto.SearchFilters;
import com.sk.skillsgraph.dto.SearchDto.SearchResponse;
import com.sk.skillsgraph.dto.SearchDto.SearchResult;
import com.sk.skillsgraph.repository.SkillRepository;
import com.sk.skillsgraph.util.AppExceptions.ValidationException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class VectorSearchService {

    private final JdbcTemplate jdbcTemplate;
    private final Neo4jClient neo4jClient;
    private final SkillRepository skillRepository;
    private final EmbeddingService embeddingService;

    public VectorSearchService(
            JdbcTemplate jdbcTemplate,
            Neo4jClient neo4jClient,
            SkillRepository skillRepository,
            EmbeddingService embeddingService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.neo4jClient = neo4jClient;
        this.skillRepository = skillRepository;
        this.embeddingService = embeddingService;
    }

    public SearchResponse semanticSearch(String query, SearchFilters filters, int limit) {
        long startedAt = System.currentTimeMillis();
        if (query == null || query.isBlank()) {
            return new SearchResponse(List.of(), 0, 0);
        }

        int normalizedLimit = normalizeLimit(limit);
        float[] queryEmbedding = embeddingService.embedText(query.trim());
        List<SearchResult> results = findSimilarSkills(queryEmbedding, normalizedLimit, filters);
        long elapsed = System.currentTimeMillis() - startedAt;
        return new SearchResponse(results, results.size(), elapsed);
    }

    public List<SearchResult> findSimilarSkills(float[] embedding, int k, SearchFilters filters) {
        if (embedding == null || embedding.length == 0) {
            throw new ValidationException("Embedding vector is required", 400);
        }

        int normalizedLimit = normalizeLimit(k);
        String vectorLiteral = toVectorLiteral(embedding);
        String statusFilter = filters != null && filters.status() != null
                ? filters.status().name()
                : SkillStatus.active.name();
        SkillCategory categoryFilter = filters == null ? null : filters.category();

        List<SimilarityRow> nearest = jdbcTemplate.query("""
                        SELECT skill_id,
                               1 - (embedding <=> CAST(? AS vector)) AS similarity
                        FROM skill_embeddings
                        WHERE embedding IS NOT NULL
                          AND status = ?
                        ORDER BY embedding <=> CAST(? AS vector)
                        LIMIT ?
                        """,
                (rs, rowNum) -> new SimilarityRow(
                        rs.getString("skill_id"),
                        rs.getDouble("similarity")
                ),
                vectorLiteral,
                statusFilter,
                vectorLiteral,
                normalizedLimit
        );

        List<SearchResult> results = new ArrayList<>();
        for (SimilarityRow row : nearest) {
            Optional<com.sk.skillsgraph.domain.Skill> maybeSkill = skillRepository.findById(row.id());
            if (maybeSkill.isEmpty()) {
                continue;
            }

            com.sk.skillsgraph.domain.Skill skill = maybeSkill.get();
            SkillCategory category = parseCategory(skill.getCategory());
            SkillStatus status = parseStatus(skill.getStatus());
            if (categoryFilter != null && category != categoryFilter) {
                continue;
            }

            results.add(new SearchResult(
                    skill.getId(),
                    skill.getExternalId(),
                    skill.getCanonicalName(),
                    skill.getDescription(),
                    category,
                    status,
                    row.similarity(),
                    MatchType.semantic,
                    skill.getCanonicalName()
            ));
        }

        results.sort(Comparator.comparingDouble(SearchResult::score).reversed());
        return results.size() > normalizedLimit ? results.subList(0, normalizedLimit) : results;
    }

    public List<AliasSimilarityResult> findSimilarAliases(float[] embedding, int k) {
        if (embedding == null || embedding.length == 0) {
            throw new ValidationException("Embedding vector is required", 400);
        }

        int normalizedLimit = normalizeLimit(k);
        String vectorLiteral = toVectorLiteral(embedding);
        List<SimilarityRow> nearest = jdbcTemplate.query("""
                        SELECT alias_id,
                               1 - (alias_embedding <=> CAST(? AS vector)) AS similarity
                        FROM alias_embeddings
                        WHERE alias_embedding IS NOT NULL
                        ORDER BY alias_embedding <=> CAST(? AS vector)
                        LIMIT ?
                        """,
                (rs, rowNum) -> new SimilarityRow(
                        rs.getString("alias_id"),
                        rs.getDouble("similarity")
                ),
                vectorLiteral,
                vectorLiteral,
                normalizedLimit
        );
        if (nearest.isEmpty()) {
            return List.of();
        }

        List<String> aliasIds = nearest.stream().map(SimilarityRow::id).toList();
        Map<String, Map<String, Object>> aliasDetails = neo4jClient.query("""
                        UNWIND $aliasIds AS aliasId
                        MATCH (s:Skill)-[:HAS_ALIAS]->(a:Alias {id: aliasId})
                        RETURN a.id AS aliasId,
                               a.surfaceForm AS surfaceForm,
                               s.id AS skillId,
                               s.externalId AS externalId,
                               s.canonicalName AS canonicalName,
                               s.description AS description,
                               s.category AS category,
                               s.status AS status
                        """)
                .bind(aliasIds).to("aliasIds")
                .fetch()
                .all()
                .stream()
                .collect(LinkedHashMap::new, (map, row) -> map.put(asString(row.get("aliasId")), row), Map::putAll);

        List<AliasSimilarityResult> results = new ArrayList<>();
        for (SimilarityRow row : nearest) {
            Map<String, Object> detail = aliasDetails.get(row.id());
            if (detail == null) {
                continue;
            }

            SkillStatus status = parseStatus(asString(detail.get("status")));
            if (status != SkillStatus.active) {
                continue;
            }

            SearchResult skill = new SearchResult(
                    asString(detail.get("skillId")),
                    asString(detail.get("externalId")),
                    asString(detail.get("canonicalName")),
                    asString(detail.get("description")),
                    parseCategory(asString(detail.get("category"))),
                    status,
                    row.similarity(),
                    MatchType.semantic,
                    asString(detail.get("canonicalName"))
            );
            results.add(new AliasSimilarityResult(
                    asString(detail.get("aliasId")),
                    asString(detail.get("surfaceForm")),
                    skill,
                    row.similarity()
            ));
        }

        return results;
    }

    public List<ChunkCandidate> findCandidatesForChunk(float[] chunkEmbedding, Integer k) {
        int limit = k == null ? AppConstants.RAG_CANDIDATE_LIMIT : k;
        List<SearchResult> matches = findSimilarSkills(chunkEmbedding, limit, new SearchFilters(null, SkillStatus.active));
        return matches.stream()
                .map(result -> new ChunkCandidate(
                        result.id(),
                        result.externalId(),
                        result.canonicalName(),
                        result.description(),
                        result.score()
                ))
                .toList();
    }

    public DuplicateCheckResult checkDuplicate(String canonicalName, String description) {
        String normalizedName = canonicalName == null ? "" : canonicalName.trim();
        if (normalizedName.isBlank()) {
            throw new ValidationException("canonical_name must not be blank", 400);
        }

        String candidateText = normalizedName + ": " + (description == null ? "" : description.trim());
        float[] embedding = embeddingService.embedText(candidateText);

        List<SearchResult> skillMatches = findSimilarSkills(
                embedding,
                8,
                new SearchFilters(null, SkillStatus.active)
        );
        List<AliasSimilarityResult> aliasMatches = findSimilarAliases(embedding, 8);

        Map<String, DuplicateAccumulator> merged = new HashMap<>();
        for (SearchResult match : skillMatches) {
            merged.compute(match.id(), (key, existing) -> {
                if (existing == null || match.score() > existing.similarity) {
                    return new DuplicateAccumulator(
                            match.id(),
                            match.externalId(),
                            match.canonicalName(),
                            match.score(),
                            "skill"
                    );
                }
                return existing;
            });
        }

        for (AliasSimilarityResult aliasMatch : aliasMatches) {
            SearchResult skill = aliasMatch.skill();
            merged.compute(skill.id(), (key, existing) -> {
                if (existing == null || aliasMatch.similarity() > existing.similarity) {
                    String via = existing == null ? "alias" : "both";
                    return new DuplicateAccumulator(
                            skill.id(),
                            skill.externalId(),
                            skill.canonicalName(),
                            aliasMatch.similarity(),
                            via
                    );
                }
                if ("skill".equals(existing.via)) {
                    return new DuplicateAccumulator(
                            existing.skillId,
                            existing.externalId,
                            existing.canonicalName,
                            existing.similarity,
                            "both"
                    );
                }
                return existing;
            });
        }

        List<DuplicateMatch> matches = merged.values().stream()
                .sorted(Comparator.comparingDouble((DuplicateAccumulator item) -> item.similarity).reversed())
                .map(item -> new DuplicateMatch(
                        item.skillId,
                        item.externalId,
                        item.canonicalName,
                        item.similarity,
                        item.via
                ))
                .toList();

        double topSimilarity = matches.isEmpty() ? 0.0D : matches.getFirst().similarity();
        boolean duplicate = topSimilarity >= AppConstants.SIMILARITY_DUPLICATE_THRESHOLD;
        boolean requiresReview = !duplicate && topSimilarity >= AppConstants.SIMILARITY_REVIEW_THRESHOLD;
        return new DuplicateCheckResult(duplicate, requiresReview, matches);
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return AppConstants.PAGINATION_DEFAULT_LIMIT;
        }
        return Math.min(limit, AppConstants.PAGINATION_MAX_LIMIT);
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(Float.toString(embedding[i]));
        }
        return builder.append(']').toString();
    }

    private SkillCategory parseCategory(String value) {
        if (value == null || value.isBlank()) {
            return SkillCategory.domain;
        }
        try {
            return SkillCategory.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return SkillCategory.domain;
        }
    }

    private SkillStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return SkillStatus.candidate;
        }
        try {
            return SkillStatus.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return SkillStatus.candidate;
        }
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private record SimilarityRow(String id, double similarity) {
    }

    private static final class DuplicateAccumulator {
        private final String skillId;
        private final String externalId;
        private final String canonicalName;
        private final double similarity;
        private final String via;

        private DuplicateAccumulator(String skillId, String externalId, String canonicalName, double similarity, String via) {
            this.skillId = skillId;
            this.externalId = externalId;
            this.canonicalName = canonicalName;
            this.similarity = similarity;
            this.via = via;
        }
    }

    public record AliasSimilarityResult(
            String aliasId,
            String surfaceForm,
            SearchResult skill,
            double similarity
    ) {
    }

    public record ChunkCandidate(
            String id,
            String externalId,
            String canonicalName,
            String description,
            double similarity
    ) {
    }

    public record DuplicateMatch(
            String skillId,
            String externalId,
            String canonicalName,
            double similarity,
            String via
    ) {
    }

    public record DuplicateCheckResult(
            boolean isDuplicate,
            boolean requiresReview,
            List<DuplicateMatch> matches
    ) {
    }
}
