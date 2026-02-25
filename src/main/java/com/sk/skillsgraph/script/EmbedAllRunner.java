package com.sk.skillsgraph.script;

import com.sk.skillsgraph.config.AppConstants;
import com.sk.skillsgraph.service.EmbeddingService;
import com.sk.skillsgraph.service.EmbeddingService.AliasEmbeddingPayload;
import com.sk.skillsgraph.service.EmbeddingService.SkillEmbeddingPayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class EmbedAllRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmbedAllRunner.class);

    private final Neo4jClient neo4jClient;
    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingService embeddingService;

    public EmbedAllRunner(Neo4jClient neo4jClient, JdbcTemplate jdbcTemplate, EmbeddingService embeddingService) {
        this.neo4jClient = neo4jClient;
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingService = embeddingService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("embed-all")) {
            return;
        }

        List<SkillEmbeddingPayload> skillPayloads = loadMissingSkillPayloads();
        int embeddedSkills = processInBatches(skillPayloads, embeddingService::embedSkillBatch, "skills");

        List<AliasEmbeddingPayload> aliasPayloads = loadMissingAliasPayloads();
        int embeddedAliases = processInBatches(aliasPayloads, embeddingService::embedAliasBatch, "aliases");

        LOGGER.info(
                "Embed-all completed: embedded {}/{} skills, {}/{} aliases",
                embeddedSkills,
                skillPayloads.size(),
                embeddedAliases,
                aliasPayloads.size()
        );
    }

    private List<SkillEmbeddingPayload> loadMissingSkillPayloads() {
        List<SkillEmbeddingPayload> allSkills = neo4jClient.query("""
                        MATCH (s:Skill)
                        RETURN s.id AS id,
                               s.canonicalName AS canonicalName,
                               s.description AS description,
                               coalesce(s.status, 'active') AS status
                        ORDER BY s.canonicalName ASC
                        """)
                .fetch()
                .all()
                .stream()
                .map(row -> new SkillEmbeddingPayload(
                        asString(row.get("id")),
                        asString(row.get("canonicalName")),
                        asString(row.get("description")),
                        normalizeStatus(asString(row.get("status")))
                ))
                .toList();

        Set<String> existingEmbeddingIds = new HashSet<>(jdbcTemplate.query(
                "SELECT skill_id FROM skill_embeddings",
                (rs, rowNum) -> rs.getString("skill_id")
        ));

        List<SkillEmbeddingPayload> missing = new ArrayList<>();
        for (SkillEmbeddingPayload payload : allSkills) {
            if (!existingEmbeddingIds.contains(payload.skillId())) {
                missing.add(payload);
            }
        }
        return missing;
    }

    private List<AliasEmbeddingPayload> loadMissingAliasPayloads() {
        List<AliasEmbeddingPayload> allAliases = neo4jClient.query("""
                        MATCH (:Skill)-[:HAS_ALIAS]->(a:Alias)
                        RETURN a.id AS id, a.surfaceForm AS surfaceForm
                        ORDER BY a.surfaceForm ASC
                        """)
                .fetch()
                .all()
                .stream()
                .map(row -> new AliasEmbeddingPayload(
                        asString(row.get("id")),
                        asString(row.get("surfaceForm"))
                ))
                .filter(payload -> payload.aliasId() != null && payload.surfaceForm() != null && !payload.surfaceForm().isBlank())
                .toList();

        Set<String> existingEmbeddingIds = new HashSet<>(jdbcTemplate.query(
                "SELECT alias_id FROM alias_embeddings",
                (rs, rowNum) -> rs.getString("alias_id")
        ));

        List<AliasEmbeddingPayload> missing = new ArrayList<>();
        for (AliasEmbeddingPayload payload : allAliases) {
            if (!existingEmbeddingIds.contains(payload.aliasId())) {
                missing.add(payload);
            }
        }
        return missing;
    }

    private <T> int processInBatches(List<T> payloads, java.util.function.Consumer<List<T>> batchConsumer, String kind) {
        if (payloads.isEmpty()) {
            LOGGER.info("Embed-all skipped for {}: no missing records", kind);
            return 0;
        }

        int embedded = 0;
        for (int start = 0; start < payloads.size(); start += AppConstants.EMBEDDING_BATCH_SIZE) {
            int end = Math.min(start + AppConstants.EMBEDDING_BATCH_SIZE, payloads.size());
            List<T> batch = payloads.subList(start, end);
            batchConsumer.accept(batch);
            embedded += batch.size();
            LOGGER.info("Embedded {}/{} {}", embedded, payloads.size(), kind);
        }
        return embedded;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "active";
        }
        return status.toLowerCase(Locale.ROOT);
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
