package com.sk.skillsgraph.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sk.skillsgraph.config.AppProperties;
import com.sk.skillsgraph.middleware.RequestIdFilter;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HealthControllerTest {

    private MockMvc mockMvc;
    private JdbcTemplate jdbcTemplate;
    private StringRedisTemplate redisTemplate;
    private RedisConnectionFactory redisConnectionFactory;
    private RedisConnection redisConnection;

    @BeforeEach
    void setUp() {
        jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        redisTemplate = Mockito.mock(StringRedisTemplate.class);
        redisConnectionFactory = Mockito.mock(RedisConnectionFactory.class);
        redisConnection = Mockito.mock(RedisConnection.class);

        AppProperties appProperties = new AppProperties(
                "test-anthropic-key",
                "test-openai-key",
                "jdbc:postgresql://localhost:5432/skills_graph",
                "bolt://localhost:7687",
                "redis://localhost:6379",
                "",
                List.of("*"),
                "test",
                "0.1.0"
        );

        when(redisTemplate.getConnectionFactory()).thenReturn(redisConnectionFactory);
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);

        HealthController controller = new HealthController(jdbcTemplate, redisTemplate, appProperties);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void healthIsOkWhenDependenciesAreConnected() throws Exception {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(redisConnection.ping()).thenReturn("PONG");

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Health check completed"))
                .andExpect(jsonPath("$.request_id").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("ok"))
                .andExpect(jsonPath("$.data.version").value("0.1.0"))
                .andExpect(jsonPath("$.data.db").value("connected"))
                .andExpect(jsonPath("$.data.redis").value("connected"))
                .andExpect(jsonPath("$.data.uptime_seconds").isNumber());
    }

    @Test
    void healthIsDegradedWhenDatabaseIsDisconnected() throws Exception {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new IllegalStateException("database unavailable"));
        when(redisConnection.ping()).thenReturn("PONG");

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("degraded"))
                .andExpect(jsonPath("$.data.db").value("disconnected"))
                .andExpect(jsonPath("$.data.redis").value("connected"));
    }
}
