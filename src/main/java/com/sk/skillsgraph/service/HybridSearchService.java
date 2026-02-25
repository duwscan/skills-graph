package com.sk.skillsgraph.service;

import com.sk.skillsgraph.config.AppConstants;
import com.sk.skillsgraph.dto.SearchDto.MatchType;
import com.sk.skillsgraph.dto.SearchDto.SearchFilters;
import com.sk.skillsgraph.dto.SearchDto.SearchResponse;
import com.sk.skillsgraph.dto.SearchDto.SearchResult;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;

@Service
public class HybridSearchService {

    private static final int RRF_K = 60;

    private final SearchService searchService;
    private final VectorSearchService vectorSearchService;

    public HybridSearchService(SearchService searchService, VectorSearchService vectorSearchService) {
        this.searchService = searchService;
        this.vectorSearchService = vectorSearchService;
    }

    public SearchResponse search(String query, SearchFilters filters, int limit) {
        long startedAt = System.currentTimeMillis();
        if (query == null || query.isBlank()) {
            return new SearchResponse(List.of(), 0, 0);
        }

        int normalizedLimit = normalizeLimit(limit);
        CompletableFuture<SearchResponse> keywordFuture = CompletableFuture
                .supplyAsync(() -> searchService.search(query, filters, normalizedLimit));
        CompletableFuture<SearchResponse> semanticFuture = CompletableFuture
                .supplyAsync(() -> vectorSearchService.semanticSearch(query, filters, normalizedLimit));

        SearchResponse keywordResponse = keywordFuture.join();
        SearchResponse semanticResponse = semanticFuture.join();

        Map<String, FusionAccumulator> merged = new LinkedHashMap<>();
        applyRrf(keywordResponse.results(), merged, true);
        applyRrf(semanticResponse.results(), merged, false);

        List<SearchResult> fusedResults = merged.values().stream()
                .sorted(Comparator
                        .comparingDouble(FusionAccumulator::score).reversed()
                        .thenComparing(FusionAccumulator::canonicalName, String.CASE_INSENSITIVE_ORDER))
                .limit(normalizedLimit)
                .map(FusionAccumulator::toResult)
                .toList();

        long elapsed = System.currentTimeMillis() - startedAt;
        return new SearchResponse(fusedResults, merged.size(), elapsed);
    }

    private void applyRrf(List<SearchResult> input, Map<String, FusionAccumulator> merged, boolean keyword) {
        for (int i = 0; i < input.size(); i++) {
            SearchResult result = input.get(i);
            int rank = i + 1;
            double increment = 1.0D / (RRF_K + rank);
            merged.compute(result.id(), (id, existing) -> {
                if (existing == null) {
                    return FusionAccumulator.from(result, keyword, !keyword, increment);
                }
                existing.score += increment;
                if (keyword) {
                    existing.keyword = true;
                } else {
                    existing.semantic = true;
                }
                if (existing.highlight == null || existing.highlight.isBlank()) {
                    existing.highlight = result.highlight();
                }
                return existing;
            });
        }
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return AppConstants.PAGINATION_DEFAULT_LIMIT;
        }
        return Math.min(limit, AppConstants.PAGINATION_MAX_LIMIT);
    }

    private static final class FusionAccumulator {
        private final String id;
        private final String externalId;
        private final String canonicalName;
        private final String description;
        private final com.sk.skillsgraph.dto.Enums.SkillCategory category;
        private final com.sk.skillsgraph.dto.Enums.SkillStatus status;
        private double score;
        private boolean keyword;
        private boolean semantic;
        private String highlight;

        private FusionAccumulator(
                String id,
                String externalId,
                String canonicalName,
                String description,
                com.sk.skillsgraph.dto.Enums.SkillCategory category,
                com.sk.skillsgraph.dto.Enums.SkillStatus status,
                double score,
                boolean keyword,
                boolean semantic,
                String highlight
        ) {
            this.id = id;
            this.externalId = externalId;
            this.canonicalName = canonicalName;
            this.description = description;
            this.category = category;
            this.status = status;
            this.score = score;
            this.keyword = keyword;
            this.semantic = semantic;
            this.highlight = highlight;
        }

        private static FusionAccumulator from(SearchResult result, boolean keyword, boolean semantic, double score) {
            return new FusionAccumulator(
                    result.id(),
                    result.externalId(),
                    result.canonicalName(),
                    result.description(),
                    result.category(),
                    result.status(),
                    score,
                    keyword,
                    semantic,
                    result.highlight()
            );
        }

        private double score() {
            return score;
        }

        private String canonicalName() {
            return canonicalName == null ? "" : canonicalName;
        }

        private SearchResult toResult() {
            MatchType matchType;
            if (keyword && semantic) {
                matchType = MatchType.both;
            } else if (semantic) {
                matchType = MatchType.semantic;
            } else {
                matchType = MatchType.keyword;
            }
            return new SearchResult(
                    id,
                    externalId,
                    canonicalName,
                    description,
                    category,
                    status,
                    score,
                    matchType,
                    highlight
            );
        }
    }
}
