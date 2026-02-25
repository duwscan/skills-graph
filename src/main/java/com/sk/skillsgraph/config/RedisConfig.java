package com.sk.skillsgraph.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.lettuce.core.resource.Delay;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisConfig.class);

    private ClientResources clientResources;
    private LettuceConnectionFactory lettuceConnectionFactory;

    @Bean
    public RedisConnectionFactory redisConnectionFactory(
            DataRedisProperties redisProperties,
            @Value("${spring.data.redis.timeout:10s}") Duration redisTimeout
    ) {
        RedisStandaloneConfiguration serverConfig =
                new RedisStandaloneConfiguration(redisProperties.getHost(), redisProperties.getPort());

        if (redisProperties.getPassword() != null) {
            serverConfig.setPassword(RedisPassword.of(redisProperties.getPassword()));
        }

        clientResources = DefaultClientResources.builder()
                .reconnectDelay(Delay.exponential())
                .build();

        ClientOptions clientOptions = ClientOptions.builder()
                .autoReconnect(true)
                .build();

        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                .clientResources(clientResources)
                .clientOptions(clientOptions)
                .commandTimeout(redisTimeout)
                .build();

        lettuceConnectionFactory = new LettuceConnectionFactory(serverConfig, clientConfiguration);
        LOGGER.info("Configured Redis host {}:{}", redisProperties.getHost(), redisProperties.getPort());
        return lettuceConnectionFactory;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    public RedisCacheHelper redisCacheHelper(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        return new RedisCacheHelper(redisTemplate, objectMapper);
    }

    @PreDestroy
    public void closeRedis() {
        if (lettuceConnectionFactory != null) {
            LOGGER.info("Closing Redis connection factory");
            lettuceConnectionFactory.destroy();
        }
        if (clientResources != null) {
            clientResources.shutdown(1, 2, TimeUnit.SECONDS);
        }
    }

    public static class RedisCacheHelper {

        private final StringRedisTemplate redisTemplate;
        private final ObjectMapper objectMapper;

        public RedisCacheHelper(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
            this.redisTemplate = redisTemplate;
            this.objectMapper = objectMapper;
        }

        public <T> T cacheGet(String key, Class<T> targetType) {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached == null) {
                return null;
            }
            try {
                return objectMapper.readValue(cached, targetType);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Unable to deserialize cached value for key " + key, e);
            }
        }

        public <T> T cacheGet(String key, TypeReference<T> typeReference) {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached == null) {
                return null;
            }
            try {
                return objectMapper.readValue(cached, typeReference);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Unable to deserialize cached value for key " + key, e);
            }
        }

        public void cacheSet(String key, Object value, long ttlSeconds) {
            try {
                String serialized = objectMapper.writeValueAsString(value);
                redisTemplate.opsForValue().set(key, serialized, Duration.ofSeconds(ttlSeconds));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Unable to serialize value for key " + key, e);
            }
        }

        public void cacheDelete(String key) {
            redisTemplate.delete(key);
        }

        public String cacheMakeKey(String... parts) {
            return Arrays.stream(parts)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(part -> !part.isEmpty())
                    .reduce((left, right) -> left + ":" + right)
                    .orElse("");
        }
    }
}
