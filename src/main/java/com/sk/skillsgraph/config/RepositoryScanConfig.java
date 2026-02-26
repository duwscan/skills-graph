package com.sk.skillsgraph.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "com.sk.skillsgraph.repository.jpa")
@EnableNeo4jRepositories(basePackages = "com.sk.skillsgraph.repository.neo4j")
@EnableRedisRepositories(basePackages = "com.sk.skillsgraph.redis")
public class RepositoryScanConfig {
}
