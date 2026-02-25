package com.sk.skillsgraph.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sk.skillsgraph.TestSetup;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AppPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class
            ))
            .withUserConfiguration(TestConfig.class);

    @Test
    void missingRequiredPropertyFailsFast() {
        contextRunner
                .withPropertyValues(
                        "app.openai-api-key=" + TestSetup.TEST_OPENAI_KEY,
                        "app.database-url=jdbc:postgresql://localhost:5432/skills_graph",
                        "app.neo4j-uri=bolt://localhost:7687",
                        "app.redis-url=redis://localhost:6379"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                });
    }

    @Test
    void optionalValuesUseDefaults() {
        contextRunner
                .withPropertyValues(
                        "app.anthropic-api-key=" + TestSetup.TEST_ANTHROPIC_KEY,
                        "app.openai-api-key=" + TestSetup.TEST_OPENAI_KEY,
                        "app.database-url=jdbc:postgresql://localhost:5432/skills_graph",
                        "app.neo4j-uri=bolt://localhost:7687",
                        "app.redis-url=redis://localhost:6379"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AppProperties properties = context.getBean(AppProperties.class);
                    assertThat(properties.heliconeApiKey()).isEmpty();
                    assertThat(properties.version()).isEqualTo("0.1.0");
                    assertThat(properties.allowedOrigins()).containsExactly("*");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AppProperties.class)
    static class TestConfig {
    }
}
