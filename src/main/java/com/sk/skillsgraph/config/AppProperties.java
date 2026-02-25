package com.sk.skillsgraph.config;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app")
@Validated
public record AppProperties(
        @NotBlank String anthropicApiKey,
        @NotBlank String openaiApiKey,
        @NotBlank String databaseUrl,
        @NotBlank String neo4jUri,
        @NotBlank String redisUrl,
        String heliconeApiKey,
        List<String> allowedOrigins,
        String environment,
        String version
) {
    public AppProperties {
        heliconeApiKey = heliconeApiKey == null ? "" : heliconeApiKey;
        allowedOrigins = (allowedOrigins == null || allowedOrigins.isEmpty())
                ? List.of("*")
                : List.copyOf(allowedOrigins);
        environment = environment == null || environment.isBlank() ? "development" : environment;
        version = version == null || version.isBlank() ? "0.1.0" : version;
    }
}
