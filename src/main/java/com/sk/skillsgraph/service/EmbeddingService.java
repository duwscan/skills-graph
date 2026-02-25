package com.sk.skillsgraph.service;

import com.sk.skillsgraph.config.AppConstants;
import com.sk.skillsgraph.config.RedisConfig.RedisCacheHelper;
import com.sk.skillsgraph.util.AppExceptions.ValidationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddingService.class);

    private final EmbeddingModel embeddingModel;
    private final RedisCacheHelper redisCacheHelper;
    private final JdbcTemplate jdbcTemplate;

    public EmbeddingService(EmbeddingModel embeddingModel, RedisCacheHelper redisCacheHelper, JdbcTemplate jdbcTemplate) {
        this.embeddingModel = embeddingModel;
        this.redisCacheHelper = redisCacheHelper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public float[] embedText(String text) {
        String normalized = normalizeText(text);
        String cacheKey = cacheKey(normalized);
        float[] cached = redisCacheHelper.cacheGet(cacheKey, float[].class);
        if (cached != null && cached.length > 0) {
            return cached;
        }

        float[] embedding = embeddingModel.embed(normalized);
        redisCacheHelper.cacheSet(cacheKey, embedding, AppConstants.EMBEDDING_CACHE_TTL_SECONDS);
        return embedding;
    }

    public List<float[]> embedTexts(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        List<float[]> resolved = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            resolved.add(null);
        }

        List<String> misses = new ArrayList<>();
        List<Integer> missIndices = new ArrayList<>();
        for (int index = 0; index < texts.size(); index++) {
            String normalized = normalizeText(texts.get(index));
            String key = cacheKey(normalized);
            float[] cached = redisCacheHelper.cacheGet(key, float[].class);
            if (cached != null && cached.length > 0) {
                resolved.set(index, cached);
                continue;
            }
            misses.add(normalized);
            missIndices.add(index);
        }

        if (!misses.isEmpty()) {
            List<float[]> missEmbeddings = embeddingModel.embed(misses);
            for (int i = 0; i < missEmbeddings.size(); i++) {
                int index = missIndices.get(i);
                String normalized = misses.get(i);
                float[] embedding = missEmbeddings.get(i);
                resolved.set(index, embedding);
                redisCacheHelper.cacheSet(cacheKey(normalized), embedding, AppConstants.EMBEDDING_CACHE_TTL_SECONDS);
            }
        }

        return resolved;
    }

    public void embedSkill(String skillId, String canonicalName, String description, String status) {
        String text = canonicalName + ": " + (description == null ? "" : description);
        upsertSkillEmbedding(skillId, embedText(text), status);
    }

    @Async
    public void embedSkillAsync(String skillId, String canonicalName, String description, String status) {
        try {
            embedSkill(skillId, canonicalName, description, status);
        } catch (Exception exception) {
            LOGGER.warn("Failed to embed skill {}", skillId, exception);
        }
    }

    public void embedAlias(String aliasId, String surfaceForm) {
        upsertAliasEmbedding(aliasId, embedText(surfaceForm));
    }

    @Async
    public void embedAliasAsync(String aliasId, String surfaceForm) {
        try {
            embedAlias(aliasId, surfaceForm);
        } catch (Exception exception) {
            LOGGER.warn("Failed to embed alias {}", aliasId, exception);
        }
    }

    public void upsertSkillEmbedding(String skillId, float[] embedding, String status) {
        String vectorLiteral = toVectorLiteral(embedding);
        String normalizedStatus = status == null || status.isBlank() ? "active" : status;
        jdbcTemplate.update("""
                INSERT INTO skill_embeddings(skill_id, embedding, status, updated_at)
                VALUES (?, CAST(? AS vector), ?, NOW())
                ON CONFLICT (skill_id)
                DO UPDATE SET embedding = EXCLUDED.embedding,
                              status = EXCLUDED.status,
                              updated_at = NOW()
                """, skillId, vectorLiteral, normalizedStatus);
    }

    public void updateSkillStatus(String skillId, String status) {
        String normalizedStatus = status == null || status.isBlank() ? "active" : status;
        jdbcTemplate.update("""
                UPDATE skill_embeddings
                SET status = ?,
                    updated_at = NOW()
                WHERE skill_id = ?
                """, normalizedStatus, skillId);
    }

    public void upsertAliasEmbedding(String aliasId, float[] embedding) {
        String vectorLiteral = toVectorLiteral(embedding);
        jdbcTemplate.update("""
                INSERT INTO alias_embeddings(alias_id, alias_embedding, updated_at)
                VALUES (?, CAST(? AS vector), NOW())
                ON CONFLICT (alias_id)
                DO UPDATE SET alias_embedding = EXCLUDED.alias_embedding,
                              updated_at = NOW()
                """, aliasId, vectorLiteral);
    }

    public void deleteAliasEmbedding(String aliasId) {
        jdbcTemplate.update("DELETE FROM alias_embeddings WHERE alias_id = ?", aliasId);
    }

    public void embedSkillBatch(List<SkillEmbeddingPayload> payloads) {
        if (payloads == null || payloads.isEmpty()) {
            return;
        }
        List<String> texts = payloads.stream()
                .map(payload -> payload.canonicalName() + ": " + (payload.description() == null ? "" : payload.description()))
                .toList();
        List<float[]> embeddings = embedTexts(texts);
        for (int i = 0; i < payloads.size(); i++) {
            SkillEmbeddingPayload payload = payloads.get(i);
            upsertSkillEmbedding(payload.skillId(), embeddings.get(i), payload.status());
        }
    }

    public void embedAliasBatch(List<AliasEmbeddingPayload> payloads) {
        if (payloads == null || payloads.isEmpty()) {
            return;
        }
        List<String> texts = payloads.stream().map(AliasEmbeddingPayload::surfaceForm).toList();
        List<float[]> embeddings = embedTexts(texts);
        for (int i = 0; i < payloads.size(); i++) {
            upsertAliasEmbedding(payloads.get(i).aliasId(), embeddings.get(i));
        }
    }

    private String normalizeText(String text) {
        if (text == null || text.isBlank()) {
            throw new ValidationException("Embedding input text must not be blank", 400);
        }
        return text.trim();
    }

    private String cacheKey(String normalizedText) {
        return "embed:" + sha256Hex(normalizedText);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash embedding cache key", exception);
        }
    }

    private String toVectorLiteral(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            throw new ValidationException("Embedding vector is empty", 400);
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(Float.toString(embedding[i]));
        }
        return builder.append(']').toString();
    }

    public record SkillEmbeddingPayload(String skillId, String canonicalName, String description, String status) {
    }

    public record AliasEmbeddingPayload(String aliasId, String surfaceForm) {
    }
}
