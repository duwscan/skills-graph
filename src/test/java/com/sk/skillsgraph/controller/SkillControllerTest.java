package com.sk.skillsgraph.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sk.skillsgraph.dto.AliasDto.AliasResponse;
import com.sk.skillsgraph.dto.EdgeDto.EdgeResponse;
import com.sk.skillsgraph.dto.Enums.AliasSource;
import com.sk.skillsgraph.dto.Enums.EdgeStatus;
import com.sk.skillsgraph.dto.Enums.Provenance;
import com.sk.skillsgraph.dto.Enums.RelationshipType;
import com.sk.skillsgraph.dto.Enums.SkillCategory;
import com.sk.skillsgraph.dto.Enums.SkillStatus;
import com.sk.skillsgraph.dto.SkillDto.ListSkillsResponse;
import com.sk.skillsgraph.dto.SkillDto.SkillResponse;
import com.sk.skillsgraph.dto.SkillDto.SkillSummary;
import com.sk.skillsgraph.middleware.GlobalExceptionHandler;
import com.sk.skillsgraph.middleware.RequestIdFilter;
import com.sk.skillsgraph.service.AliasService;
import com.sk.skillsgraph.service.EdgeService;
import com.sk.skillsgraph.service.SkillService;
import com.sk.skillsgraph.util.AppExceptions.SkillNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SkillControllerTest {

    private MockMvc mockMvc;
    private SkillService skillService;
    private AliasService aliasService;
    private EdgeService edgeService;

    @BeforeEach
    void setUp() {
        skillService = Mockito.mock(SkillService.class);
        aliasService = Mockito.mock(AliasService.class);
        edgeService = Mockito.mock(EdgeService.class);

        SkillController controller = new SkillController(skillService, aliasService, edgeService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void createSkillReturns201() throws Exception {
        when(skillService.create(any())).thenReturn(sampleSkillResponse());

        mockMvc.perform(post("/api/skills")
                        .contentType("application/json")
                        .content("{\"canonical_name\":\"Machine Learning\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.canonical_name").value("Machine Learning"));
    }

    @Test
    void listSkillsReturnsPaginatedPayload() throws Exception {
        ListSkillsResponse response = new ListSkillsResponse(
                List.of(new SkillSummary(
                        "skill-1",
                        "SK-ABC12345",
                        "Machine Learning",
                        "machine-learning",
                        SkillCategory.domain,
                        SkillStatus.active
                )),
                1L,
                20,
                0
        );
        when(skillService.list(any())).thenReturn(response);

        mockMvc.perform(get("/api/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].canonical_name").value("Machine Learning"));
    }

    @Test
    void searchSkillsReturns200() throws Exception {
        when(skillService.list(any())).thenReturn(new ListSkillsResponse(List.of(), 0L, 20, 0));

        mockMvc.perform(get("/api/skills/search").queryParam("q", "mahcine lerning"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void getEdgesReturnsGroupedMap() throws Exception {
        EdgeResponse edge = new EdgeResponse(
                "edge-1",
                "skill-1",
                "skill-2",
                RelationshipType.related_to,
                0.95D,
                Provenance.human_curated,
                EdgeStatus.active,
                Instant.parse("2026-02-25T00:00:00Z"),
                Instant.parse("2026-02-25T00:00:00Z"),
                "Data Science"
        );
        when(edgeService.getBySkill(anyString())).thenReturn(Map.of("related_to", List.of(edge)));

        mockMvc.perform(get("/api/skills/skill-1/edges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.related_to[0].target_skill_name").value("Data Science"));
    }

    @Test
    void updateAliasReturns200() throws Exception {
        AliasResponse alias = new AliasResponse(
                "alias-1",
                "ML",
                "en",
                false,
                AliasSource.curated,
                Instant.parse("2026-02-25T00:00:00Z")
        );
        when(aliasService.update(anyString(), anyString(), any())).thenReturn(alias);

        mockMvc.perform(patch("/api/skills/skill-1/aliases/alias-1")
                        .contentType("application/json")
                        .content("{\"surface_form\":\"ML\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.surface_form").value("ML"));
    }

    @Test
    void deleteAliasReturns200() throws Exception {
        doNothing().when(aliasService).delete(anyString(), anyString());

        mockMvc.perform(delete("/api/skills/skill-1/aliases/alias-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Alias deleted"));
    }

    @Test
    void missingSkillMapsTo404() throws Exception {
        when(skillService.getById(anyString())).thenThrow(new SkillNotFoundException("missing"));

        mockMvc.perform(get("/api/skills/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(404));
    }

    private SkillResponse sampleSkillResponse() {
        return new SkillResponse(
                "skill-1",
                "SK-ABC12345",
                "Machine Learning",
                "machine-learning",
                "Subset of AI",
                SkillCategory.domain,
                SkillStatus.candidate,
                1,
                "human_curated",
                Instant.parse("2026-02-25T00:00:00Z"),
                Instant.parse("2026-02-25T00:00:00Z"),
                List.of(),
                List.of()
        );
    }
}
