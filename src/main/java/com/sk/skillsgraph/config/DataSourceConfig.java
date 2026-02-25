package com.sk.skillsgraph.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class DataSourceConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataSourceConfig.class);

    private HikariDataSource hikariDataSource;

    @Bean
    @Primary
    public DataSource dataSource(
            DataSourceProperties properties,
            @Value("${spring.datasource.hikari.maximum-pool-size:10}") int maxPoolSize,
            @Value("${spring.datasource.hikari.connection-timeout:30000}") long connectionTimeoutMs
    ) {
        HikariDataSource dataSource = properties
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();

        dataSource.setPoolName("skills-graph-hikari");
        dataSource.setMaximumPoolSize(maxPoolSize);
        dataSource.setConnectionTimeout(connectionTimeoutMs);

        this.hikariDataSource = dataSource;
        LOGGER.info("Configured PostgreSQL pool with max size {}", maxPoolSize);
        return dataSource;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @PreDestroy
    public void closeDatabase() {
        if (hikariDataSource != null && !hikariDataSource.isClosed()) {
            LOGGER.info("Closing PostgreSQL connection pool");
            hikariDataSource.close();
        }
    }
}
