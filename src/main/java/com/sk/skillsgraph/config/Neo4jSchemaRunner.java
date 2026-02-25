package com.sk.skillsgraph.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

@Component
public class Neo4jSchemaRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(Neo4jSchemaRunner.class);

    private final Neo4jClient neo4jClient;
    private final ResourceLoader resourceLoader;

    public Neo4jSchemaRunner(Neo4jClient neo4jClient, ResourceLoader resourceLoader) {
        this.neo4jClient = neo4jClient;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Resource schema = resourceLoader.getResource("classpath:neo4j/schema.cypher");
        if (!schema.exists()) {
            LOGGER.warn("Neo4j schema file not found at classpath:neo4j/schema.cypher");
            return;
        }

        String cypher = new String(schema.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        for (String statement : cypher.split(";")) {
            String trimmed = statement.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            neo4jClient.query(trimmed).run();
        }
        LOGGER.info("Applied Neo4j schema constraints and indexes");
    }
}
