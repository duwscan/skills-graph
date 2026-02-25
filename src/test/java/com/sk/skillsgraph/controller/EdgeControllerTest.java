package com.sk.skillsgraph.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sk.skillsgraph.dto.EdgeDto.EdgeResponse;
import com.sk.skillsgraph.dto.Enums.EdgeStatus;
import com.sk.skillsgraph.dto.Enums.Provenance;
import com.sk.skillsgraph.dto.Enums.RelationshipType;
import com.sk.skillsgraph.middleware.GlobalExceptionHandler;
import com.sk.skillsgraph.middleware.RequestIdFilter;
import com.sk.skillsgraph.service.EdgeService;
import com.sk.skillsgraph.util.AppExceptions.CycleDetectedException;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EdgeControllerTest {

    private MockMvc mockMvc;
    private EdgeService edgeService;

    @BeforeEach
    void setUp() {
        edgeService = Mockito.mock(EdgeService.class);
        EdgeController controller = new EdgeController(edgeService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void createEdgeReturns201() throws Exception {
        when(edgeService.create(any())).thenReturn(sampleEdge());

        mockMvc.perform(post("/api/edges")
                        .contentType("application/json")
                        .content("""
                                {
                                  "source_skill_id": "skill-1",
                                  "target_skill_id": "skill-2",
                                  "relationship_type": "parent_of"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.relationship_type").value("parent_of"));
    }

    @Test
    void cycleDetectionReturns422() throws Exception {
        when(edgeService.create(any())).thenThrow(new CycleDetectedException("cycle"));

        mockMvc.perform(post("/api/edges")
                        .contentType("application/json")
                        .content("""
                                {
                                  "source_skill_id": "skill-1",
                                  "target_skill_id": "skill-2",
                                  "relationship_type": "parent_of"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(422));
    }

    @Test
    void deprecateEdgeReturns200() throws Exception {
        when(edgeService.deprecate(anyString())).thenReturn(sampleEdge());

        mockMvc.perform(post("/api/edges/edge-1/deprecate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Edge deprecated"));
    }

    @Test
    void deleteEdgeReturns200() throws Exception {
        doNothing().when(edgeService).delete(anyString());

        mockMvc.perform(delete("/api/edges/edge-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Edge deleted"));
    }

    private EdgeResponse sampleEdge() {
        return new EdgeResponse(
                "edge-1",
                "skill-1",
                "skill-2",
                RelationshipType.parent_of,
                1.0D,
                Provenance.human_curated,
                EdgeStatus.active,
                Instant.parse("2026-02-25T00:00:00Z"),
                Instant.parse("2026-02-25T00:00:00Z"),
                "Machine Learning"
        );
    }
}
