package com.sk.skillsgraph.controller;

import com.sk.skillsgraph.config.AppProperties;
import com.sk.skillsgraph.dto.ApiResponse;
import com.sk.skillsgraph.dto.ApiResponseEntity;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final AppProperties appProperties;
    private final Instant startedAt = Instant.now();

    public HealthController(JdbcTemplate jdbcTemplate,
                            StringRedisTemplate redisTemplate,
                            AppProperties appProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.appProperties = appProperties;
    }

    @GetMapping("/actuator/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health(HttpServletRequest request) {
        String dbStatus = dependencyStatus(this::checkDatabase);
        String redisStatus = dependencyStatus(this::checkRedis);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", ("connected".equals(dbStatus) && "connected".equals(redisStatus)) ? "ok" : "degraded");
        body.put("version", appProperties.version());
        body.put("db", dbStatus);
        body.put("redis", redisStatus);
        body.put("uptime_seconds", Duration.between(startedAt, Instant.now()).toSeconds());
        return ApiResponseEntity.success(HttpStatus.OK, "Health check completed", body, request);
    }

    private String dependencyStatus(Runnable checker) {
        try {
            checker.run();
            return "connected";
        } catch (Exception ignored) {
            return "disconnected";
        }
    }

    private void checkDatabase() {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        if (result == null || result != 1) {
            throw new IllegalStateException("Unexpected database probe result");
        }
    }

    private void checkRedis() {
        String pong = redisTemplate.getConnectionFactory().getConnection().ping();
        if (!"PONG".equalsIgnoreCase(pong)) {
            throw new IllegalStateException("Unexpected redis probe result");
        }
    }
}
