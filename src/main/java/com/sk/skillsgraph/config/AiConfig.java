package com.sk.skillsgraph.config;

import com.sk.skillsgraph.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiConfig.class);

    @Bean("fastChatClient")
    public ChatClient fastChatClient(AnthropicChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("Model tier: fast extraction (claude-haiku-4-5-20251001).")
                .build();
    }

    @Bean("standardChatClient")
    public ChatClient standardChatClient(AnthropicChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("Model tier: standard extraction (claude-sonnet-4-5-20250929).")
                .build();
    }

    @Bean
    @ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${app.helicone-api-key:}')")
    public HeliconeAnthropicSettings heliconeAnthropicSettings(AppProperties properties) {
        String headerValue = "Bearer " + properties.heliconeApiKey();
        LOGGER.info("Helicone proxy enabled for Anthropic requests");
        return new HeliconeAnthropicSettings("https://anthropic.helicone.ai", headerValue);
    }

    public record HeliconeAnthropicSettings(String baseUrl, String authHeader) {
    }
}
