package com.sk.skillsgraph.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.lettuce.core.resource.Delay;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
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
}
