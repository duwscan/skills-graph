package com.sk.skillsgraph.service.extraction;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sk.skillsgraph.config.AppConstants;
import com.sk.skillsgraph.config.RedisConfig.RedisCacheHelper;
import com.sk.skillsgraph.dto.ExtractionDto.DiscoveredCandidate;
import com.sk.skillsgraph.dto.ExtractionDto.ExtractedSkill;
import com.sk.skillsgraph.dto.ExtractionDto.ExtractionMetadata;
import com.sk.skillsgraph.dto.ExtractionDto.ExtractionOptions;
import com.sk.skillsgraph.dto.ExtractionDto.ExtractionRequest;
import com.sk.skillsgraph.dto.ExtractionDto.ExtractionResponse;
import com.sk.skillsgraph.dto.ExtractionDto.LlmExtractedSkill;
import com.sk.skillsgraph.dto.ExtractionDto.LlmExtractionOutput;
import com.sk.skillsgraph.service.EmbeddingService;
import com.sk.skillsgraph.service.VectorSearchService;
import com.sk.skillsgraph.service.VectorSearchService.ChunkCandidate;
import com.sk.skillsgraph.service.extraction.ExtractionTypes.CandidateSkill;
import com.sk.skillsgraph.service.extraction.ExtractionTypes.Chunk;
import com.sk.skillsgraph.service.extraction.ExtractionTypes.ChunkResult;
import com.sk.skillsgraph.service.extraction.ExtractionTypes.DocumentAnalysis;
import com.sk.skillsgraph.util.AppExceptions.ExtractionBusyException;
import com.sk.skillsgraph.util.AppExceptions.ValidationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class SkillExtractionPipeline {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkillExtractionPipeline.class);
    private static final int RETRY_AFTER_SECONDS = 1;
    private static final TypeReference<CachedChunkResult> CACHED_CHUNK_TYPE = new TypeReference<>() {
    };

    private final DocumentParser documentParser;
    private final SectionDetector sectionDetector;
    private final DocumentChunker documentChunker;
    private final EmbeddingService embeddingService;
    private final VectorSearchService vectorSearchService;
    private final SkillExpansionService skillExpansionService;
    private final RedisCacheHelper redisCacheHelper;
    private final StringRedisTemplate redisTemplate;
    private final Neo4jClient neo4jClient;
    private final ChatClient fastChatClient;
    private final ChatClient standardChatClient;
    private final Semaphore llmSemaphore = new Semaphore(AppConstants.LLM_CONCURRENCY_LIMIT, true);

    public SkillExtractionPipeline(
            DocumentParser documentParser,
            SectionDetector sectionDetector,
            DocumentChunker documentChunker,
            EmbeddingService embeddingService,
            VectorSearchService vectorSearchService,
            SkillExpansionService skillExpansionService,
            RedisCacheHelper redisCacheHelper,
            StringRedisTemplate redisTemplate,
            Neo4jClient neo4jClient,
            @Qualifier("fastChatClient") ChatClient fastChatClient,
            @Qualifier("standardChatClient") ChatClient standardChatClient
    ) {
        this.documentParser = documentParser;
        this.sectionDetector = sectionDetector;
        this.documentChunker = documentChunker;
        this.embeddingService = embeddingService;
        this.vectorSearchService = vectorSearchService;
        this.skillExpansionService = skillExpansionService;
        this.redisCacheHelper = redisCacheHelper;
        this.redisTemplate = redisTemplate;
        this.neo4jClient = neo4jClient;
        this.fastChatClient = fastChatClient;
        this.standardChatClient = standardChatClient;
    }

    public ExtractionResponse extract(ExtractionRequest request) {
        if (request == null || request.text() == null || request.text().isBlank()) {
            throw new ValidationException("text must not be blank", 400);
        }

        long startedAt = System.currentTimeMillis();
        EffectiveOptions options = EffectiveOptions.from(request.options());

        String parsedText = documentParser.parse(request.text());
        if (parsedText.isBlank()) {
            throw new ValidationException("text must contain extractable content", 400);
        }

        DocumentAnalysis analysis = sectionDetector.detect(parsedText);
        List<Chunk> chunks = documentChunker.chunk(parsedText, analysis);
        if (chunks.isEmpty()) {
            return new ExtractionResponse(
                    List.of(),
                    List.of(),
                    new ExtractionMetadata(
                            0, 0, 0, System.currentTimeMillis() - startedAt, "none",
                            analysis.documentType(), analysis.sections().size(), 0, 0
                    )
            );
        }

        List<ExtractedSkill> allExtracted = new ArrayList<>();
        List<DiscoveredCandidate> allDiscovered = new ArrayList<>();
        int cacheHits = 0;
        int cacheMisses = 0;
        int totalTokens = 0;
        Set<String> modelsUsed = new LinkedHashSet<>();

        for (Chunk chunk : chunks) {
            ChunkResult result = processChunk(chunk, analysis.documentType(), options);
            if (result.cacheHit()) {
                cacheHits++;
            } else {
                cacheMisses++;
            }
            if (result.modelUsed() != null && !result.modelUsed().isBlank()) {
                modelsUsed.add(result.modelUsed());
            }
            totalTokens += result.llmTokens();
            allExtracted.addAll(result.extractedSkills());
            allDiscovered.addAll(result.discoveredCandidates());
        }

        List<ExtractedSkill> merged = mergeAndDeduplicate(allExtracted);
        if (options.expand() && options.expansionDepth() > 0) {
            merged.addAll(skillExpansionService.expand(merged, options.expansionDepth()));
            merged = mergeAndDeduplicate(merged);
        }

        int totalBeforeFilter = merged.size();
        List<ExtractedSkill> filtered = merged.stream()
                .filter(skill -> skill.confidence() >= options.minConfidence())
                .sorted(Comparator.comparingDouble(ExtractedSkill::confidence).reversed())
                .limit(options.maxSkills())
                .toList();

        List<DiscoveredCandidate> discoveredCandidates = deduplicateCandidates(allDiscovered);
        recordCoOccurrencesAsync(filtered, analysis.documentType());

        long elapsed = System.currentTimeMillis() - startedAt;
        String modelUsed = modelsUsed.isEmpty() ? "none" : String.join(",", modelsUsed);

        ExtractionMetadata metadata = new ExtractionMetadata(
                chunks.size(),
                cacheHits,
                cacheMisses,
                elapsed,
                modelUsed,
                analysis.documentType(),
                analysis.sections().size(),
                totalTokens,
                totalBeforeFilter
        );
        return new ExtractionResponse(filtered, discoveredCandidates, metadata);
    }

    private ChunkResult processChunk(Chunk chunk, String documentType, EffectiveOptions options) {
        if (chunk.text() == null || chunk.text().isBlank()) {
            return new ChunkResult(List.of(), List.of(), true, "none", 0);
        }

        List<CandidateSkill> candidates = resolveCandidates(chunk.text());

        String cacheKey = cacheKey(chunk.text(), candidates);
        CachedChunkResult cached = redisCacheHelper.cacheGet(cacheKey, CACHED_CHUNK_TYPE);
        if (cached != null) {
            return new ChunkResult(
                    cached.extractedSkills() == null ? List.of() : cached.extractedSkills(),
                    cached.discoveredCandidates() == null ? List.of() : cached.discoveredCandidates(),
                    true,
                    cached.modelUsed() == null ? "cache" : cached.modelUsed(),
                    0
            );
        }

        if (candidates.isEmpty()) {
            return new ChunkResult(List.of(), List.of(), false, "none", 0);
        }

        String selectedModel = selectModel(chunk, documentType, options.locale());
        try {
            LlmCallResult llmResult = callLlm(chunk, candidates, selectedModel);
            List<ExtractedSkill> validated = validateAndMapResults(llmResult.output(), candidates, chunk);
            List<ExtractedSkill> weighted = applyWeighting(validated, chunk);
            List<DiscoveredCandidate> discovered = llmResult.output().discoveredCandidates() == null
                    ? List.of()
                    : llmResult.output().discoveredCandidates();

            CachedChunkResult payload = new CachedChunkResult(weighted, discovered, selectedModel);
            redisCacheHelper.cacheSet(cacheKey, payload, AppConstants.EXTRACTION_CACHE_TTL_SECONDS);

            return new ChunkResult(weighted, discovered, false, selectedModel, llmResult.tokens());
        } catch (ExtractionBusyException busy) {
            throw busy;
        } catch (Exception exception) {
            LOGGER.warn("LLM extraction failed for chunk {}. Falling back to lexical matching.", chunk.index(), exception);
            List<ExtractedSkill> fallback = applyWeighting(fallbackKeywordExtraction(chunk, candidates), chunk);
            CachedChunkResult payload = new CachedChunkResult(fallback, List.of(), selectedModel + ":fallback");
            redisCacheHelper.cacheSet(cacheKey, payload, AppConstants.EXTRACTION_CACHE_TTL_SECONDS);
            return new ChunkResult(fallback, List.of(), false, selectedModel + ":fallback", 0);
        }
    }

    private LlmCallResult callLlm(Chunk chunk, List<CandidateSkill> candidates, String model) {
        String userPrompt = ExtractionPrompts.buildPrompt(chunk, candidates);
        ChatClient chatClient = "fast".equals(model) ? fastChatClient : standardChatClient;
        BeanOutputConverter<LlmExtractionOutput> outputConverter = new BeanOutputConverter<>(LlmExtractionOutput.class);
        String systemPrompt = ExtractionPrompts.SYSTEM_PROMPT + "\n\nOutput format:\n" + outputConverter.getFormat();

        Exception lastException = null;
        for (int attempt = 1; attempt <= AppConstants.EXTRACTION_MAX_RETRIES; attempt++) {
            if (!llmSemaphore.tryAcquire()) {
                throw new ExtractionBusyException("Extraction is at capacity. Retry shortly.", RETRY_AFTER_SECONDS);
            }
            try {
                ResponseEntity<ChatResponse, LlmExtractionOutput> response = chatClient.prompt()
                        .system(systemPrompt)
                        .user(userPrompt)
                        .call()
                        .responseEntity(outputConverter);

                LlmExtractionOutput output = response.entity() == null
                        ? new LlmExtractionOutput(List.of(), List.of())
                        : response.entity();
                int tokens = extractTotalTokens(response.response());
                return new LlmCallResult(output, tokens);
            } catch (Exception exception) {
                lastException = exception;
                LOGGER.warn("LLM extraction attempt {}/{} failed", attempt, AppConstants.EXTRACTION_MAX_RETRIES, exception);
            } finally {
                llmSemaphore.release();
            }
        }
        throw new IllegalStateException("LLM extraction failed after retries", lastException);
    }

    private int extractTotalTokens(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return 0;
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage == null || usage.getTotalTokens() == null) {
            return 0;
        }
        return usage.getTotalTokens();
    }

    private List<ExtractedSkill> validateAndMapResults(
            LlmExtractionOutput output,
            List<CandidateSkill> candidates,
            Chunk chunk
    ) {
        Map<String, CandidateSkill> byId = new HashMap<>();
        for (CandidateSkill candidate : candidates) {
            byId.put(candidate.id(), candidate);
        }

        if (output == null || output.extractedSkills() == null || output.extractedSkills().isEmpty()) {
            return List.of();
        }

        List<ExtractedSkill> mapped = new ArrayList<>();
        for (LlmExtractedSkill item : output.extractedSkills()) {
            if (item == null || item.skillId() == null || item.skillId().isBlank()) {
                continue;
            }
            CandidateSkill candidate = byId.get(item.skillId());
            if (candidate == null) {
                LOGGER.debug("Stripping hallucinated skill_id {}", item.skillId());
                continue;
            }

            List<String> evidence = item.evidence() == null ? List.of() : deduplicateEvidence(item.evidence());
            mapped.add(new ExtractedSkill(
                    candidate.id(),
                    candidate.externalId(),
                    candidate.canonicalName(),
                    clamp(item.confidence()),
                    evidence,
                    item.proficiencyHint(),
                    item.contextType() == null || item.contextType().isBlank() ? chunk.section() == null ? "generic" : chunk.section().type() : item.contextType(),
                    item.section(),
                    false,
                    null
            ));
        }
        return mapped;
    }

    private List<String> deduplicateEvidence(List<String> evidence) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String entry : evidence) {
            if (entry == null) {
                continue;
            }
            String normalized = entry.trim();
            if (!normalized.isBlank()) {
                set.add(normalized);
            }
        }
        return List.copyOf(set);
    }

    private List<ExtractedSkill> applyWeighting(List<ExtractedSkill> skills, Chunk chunk) {
        if (skills == null || skills.isEmpty()) {
            return List.of();
        }
        double sectionWeight = chunk.section() == null ? 1.0D : chunk.section().weight();

        List<ExtractedSkill> weighted = new ArrayList<>(skills.size());
        for (ExtractedSkill skill : skills) {
            double adjusted = clamp(skill.confidence() * sectionWeight);
            weighted.add(new ExtractedSkill(
                    skill.skillId(),
                    skill.externalId(),
                    skill.skillName(),
                    adjusted,
                    skill.evidence(),
                    skill.proficiencyHint(),
                    skill.contextType(),
                    chunk.section() == null ? skill.section() : chunk.section().name(),
                    skill.isExpanded(),
                    skill.expansionType()
            ));
        }
        return weighted;
    }

    private List<ExtractedSkill> mergeAndDeduplicate(List<ExtractedSkill> skills) {
        if (skills == null || skills.isEmpty()) {
            return List.of();
        }

        Map<String, ExtractedSkill> merged = new LinkedHashMap<>();
        for (ExtractedSkill skill : skills) {
            if (skill.skillId() == null || skill.skillId().isBlank()) {
                continue;
            }

            merged.compute(skill.skillId(), (id, existing) -> {
                if (existing == null) {
                    return skill;
                }
                double confidence = Math.max(existing.confidence(), skill.confidence());
                List<String> evidence = new ArrayList<>();
                evidence.addAll(existing.evidence() == null ? List.of() : existing.evidence());
                evidence.addAll(skill.evidence() == null ? List.of() : skill.evidence());
                List<String> dedupedEvidence = deduplicateEvidence(evidence);

                String proficiencyHint = skill.proficiencyHint() != null ? skill.proficiencyHint() : existing.proficiencyHint();
                String contextType = skill.contextType() != null ? skill.contextType() : existing.contextType();
                String section = skill.section() != null ? skill.section() : existing.section();
                boolean isExpanded = existing.isExpanded() && skill.isExpanded();
                String expansionType = existing.expansionType() != null ? existing.expansionType() : skill.expansionType();

                return new ExtractedSkill(
                        existing.skillId(),
                        existing.externalId() == null ? skill.externalId() : existing.externalId(),
                        existing.skillName() == null ? skill.skillName() : existing.skillName(),
                        confidence,
                        dedupedEvidence,
                        proficiencyHint,
                        contextType,
                        section,
                        isExpanded,
                        expansionType
                );
            });
        }

        return merged.values().stream()
                .sorted(Comparator.comparingDouble(ExtractedSkill::confidence).reversed())
                .toList();
    }

    private List<DiscoveredCandidate> deduplicateCandidates(List<DiscoveredCandidate> discoveredCandidates) {
        if (discoveredCandidates == null || discoveredCandidates.isEmpty()) {
            return List.of();
        }
        Map<String, DiscoveredCandidate> bySurface = new LinkedHashMap<>();
        for (DiscoveredCandidate candidate : discoveredCandidates) {
            if (candidate == null || candidate.surfaceForm() == null || candidate.surfaceForm().isBlank()) {
                continue;
            }
            bySurface.putIfAbsent(candidate.surfaceForm().trim().toLowerCase(Locale.ROOT), candidate);
        }
        return List.copyOf(bySurface.values());
    }

    private List<ExtractedSkill> fallbackKeywordExtraction(Chunk chunk, List<CandidateSkill> candidates) {
        String textLower = chunk.text().toLowerCase(Locale.ROOT);
        List<ExtractedSkill> fallback = new ArrayList<>();
        for (CandidateSkill candidate : candidates) {
            if (candidate.canonicalName() == null) {
                continue;
            }
            String needle = candidate.canonicalName().toLowerCase(Locale.ROOT);
            if (needle.isBlank() || !textLower.contains(needle)) {
                continue;
            }
            fallback.add(new ExtractedSkill(
                    candidate.id(),
                    candidate.externalId(),
                    candidate.canonicalName(),
                    0.62D,
                    List.of(candidate.canonicalName()),
                    null,
                    chunk.section() == null ? "generic" : chunk.section().type(),
                    chunk.section() == null ? null : chunk.section().name(),
                    false,
                    null
            ));
        }
        return mergeAndDeduplicate(fallback);
    }

    private void recordCoOccurrencesAsync(List<ExtractedSkill> skills, String documentType) {
        if (skills == null || skills.size() < 2) {
            return;
        }
        List<String> uniqueSkillIds = skills.stream()
                .map(ExtractedSkill::skillId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (uniqueSkillIds.size() < 2) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            for (int i = 0; i < uniqueSkillIds.size(); i++) {
                for (int j = i + 1; j < uniqueSkillIds.size(); j++) {
                    Map<String, String> payload = new LinkedHashMap<>();
                    payload.put("skill_ids", uniqueSkillIds.get(i) + "," + uniqueSkillIds.get(j));
                    payload.put("source_type", documentType == null || documentType.isBlank() ? "generic" : documentType);
                    MapRecord<String, Object, Object> record = StreamRecords.newRecord()
                            .in("co-occurrence:pairs")
                            .ofMap(new HashMap<>(payload));
                    redisTemplate.opsForStream().add(record);
                }
            }
        });
    }

    private String selectModel(Chunk chunk, String documentType, String locale) {
        boolean english = locale == null || locale.isBlank() || locale.toLowerCase(Locale.ROOT).startsWith("en");
        boolean shortChunk = chunk.tokenEstimate() < 500;
        boolean simpleType = "jd".equals(documentType) || "cv".equals(documentType) || "generic".equals(documentType);
        return shortChunk && english && simpleType ? "fast" : "standard";
    }

    private String cacheKey(String chunkText, List<CandidateSkill> candidates) {
        List<String> candidateIds = candidates.stream().map(CandidateSkill::id).sorted().toList();
        String signature = chunkText + "|" + String.join(",", candidateIds);
        return "extract:" + sha256Hex(signature);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash extraction cache key", exception);
        }
    }

    private CandidateSkill toCandidateSkill(ChunkCandidate candidate) {
        return new CandidateSkill(
                candidate.id(),
                candidate.externalId(),
                candidate.canonicalName(),
                candidate.description(),
                candidate.similarity()
        );
    }

    private List<CandidateSkill> resolveCandidates(String chunkText) {
        try {
            float[] chunkEmbedding = embeddingService.embedText(chunkText);
            List<CandidateSkill> semantic = vectorSearchService.findCandidatesForChunk(
                            chunkEmbedding,
                            AppConstants.RAG_CANDIDATE_LIMIT
                    ).stream()
                    .map(this::toCandidateSkill)
                    .toList();
            if (!semantic.isEmpty()) {
                return semantic;
            }
        } catch (Exception exception) {
            LOGGER.warn("Embedding candidate retrieval failed, falling back to lexical retrieval", exception);
        }
        return lexicalCandidates(chunkText, AppConstants.RAG_CANDIDATE_LIMIT);
    }

    private List<CandidateSkill> lexicalCandidates(String chunkText, int limit) {
        List<String> terms = extractSearchTerms(chunkText);
        if (terms.isEmpty()) {
            return List.of();
        }

        return neo4jClient.query("""
                        WITH $terms AS terms
                        MATCH (s:Skill)
                        OPTIONAL MATCH (s)-[:HAS_ALIAS]->(a:Alias)
                        WITH s, terms, collect(toLower(coalesce(a.surfaceForm, ''))) AS aliasForms
                        WITH s, terms, aliasForms,
                             reduce(score = 0,
                                    term IN terms |
                                    score
                                    + CASE WHEN toLower(coalesce(s.canonicalName, '')) CONTAINS term THEN 2 ELSE 0 END
                                    + CASE WHEN ANY(alias IN aliasForms WHERE alias CONTAINS term) THEN 1 ELSE 0 END) AS score
                        WHERE coalesce(s.status, 'active') = 'active'
                          AND score > 0
                        RETURN s.id AS id,
                               s.externalId AS externalId,
                               s.canonicalName AS canonicalName,
                               s.description AS description,
                               toFloat(score) AS score
                        ORDER BY score DESC, canonicalName ASC
                        LIMIT $limit
                        """)
                .bind(terms).to("terms")
                .bind(limit).to("limit")
                .fetch()
                .all()
                .stream()
                .map(row -> new CandidateSkill(
                        asString(row.get("id")),
                        asString(row.get("externalId")),
                        asString(row.get("canonicalName")),
                        asString(row.get("description")),
                        Math.min(1.0D, toDouble(row.get("score"), 0.0D) / 10.0D)
                ))
                .toList();
    }

    private List<String> extractSearchTerms(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String raw : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (raw == null || raw.isBlank() || raw.length() < 3) {
                continue;
            }
            unique.add(raw);
            if (unique.size() >= 25) {
                break;
            }
        }
        return List.copyOf(unique);
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private double toDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private record CachedChunkResult(
            List<ExtractedSkill> extractedSkills,
            List<DiscoveredCandidate> discoveredCandidates,
            String modelUsed
    ) {
    }

    private record LlmCallResult(LlmExtractionOutput output, int tokens) {
    }

    private record EffectiveOptions(
            boolean expand,
            double minConfidence,
            String locale,
            int maxSkills,
            int expansionDepth
    ) {
        private static EffectiveOptions from(ExtractionOptions options) {
            if (options == null) {
                return new EffectiveOptions(
                        true,
                        AppConstants.EXTRACTION_MIN_CONFIDENCE_DEFAULT,
                        "en",
                        AppConstants.PAGINATION_DEFAULT_LIMIT,
                        AppConstants.EXTRACTION_EXPANSION_DEPTH_DEFAULT
                );
            }

            boolean expand = options.expand() == null || options.expand();
            double minConfidence = options.minConfidence() == null
                    ? AppConstants.EXTRACTION_MIN_CONFIDENCE_DEFAULT
                    : Math.max(0.0D, Math.min(1.0D, options.minConfidence()));
            String locale = options.locale() == null || options.locale().isBlank() ? "en" : options.locale().trim();
            int maxSkills = options.maxSkills() == null
                    ? AppConstants.PAGINATION_DEFAULT_LIMIT
                    : Math.max(1, Math.min(options.maxSkills(), AppConstants.PAGINATION_MAX_LIMIT));
            int expansionDepth = options.expansionDepth() == null
                    ? AppConstants.EXTRACTION_EXPANSION_DEPTH_DEFAULT
                    : Math.max(0, Math.min(options.expansionDepth(), AppConstants.TRAVERSAL_MAX_DEPTH));
            return new EffectiveOptions(expand, minConfidence, locale, maxSkills, expansionDepth);
        }
    }
}
