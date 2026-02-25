package com.sk.skillsgraph.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sk.skillsgraph.dto.Enums.SkillCategory;
import com.sk.skillsgraph.dto.Enums.SkillStatus;
import com.sk.skillsgraph.dto.SkillDto.SkillSummary;
import com.sk.skillsgraph.middleware.RequestIdFilter;
import com.sk.skillsgraph.service.ChangelogService;
import com.sk.skillsgraph.service.ChangelogService.ChangelogEntry;
import com.sk.skillsgraph.service.ChangelogService.VersionInfo;
import com.sk.skillsgraph.service.SkillService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TaxonomyControllerTest {

    private MockMvc mockMvc;
    private SkillService skillService;
    private ChangelogService changelogService;

    @BeforeEach
    void setUp() {
        skillService = Mockito.mock(SkillService.class);
        changelogService = Mockito.mock(ChangelogService.class);
        TaxonomyController controller = new TaxonomyController(skillService, changelogService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void rootsEndpointReturns200() throws Exception {
        when(skillService.getRoots()).thenReturn(List.of(new SkillSummary(
                "skill-root-technology",
                "SK-ROOTTECH",
                "Technology",
                "technology",
                SkillCategory.root,
                SkillStatus.active
        )));

        mockMvc.perform(get("/api/taxonomy/roots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].canonical_name").value("Technology"));
    }

    @Test
    void versionEndpointReturns200() throws Exception {
        when(changelogService.getVersion()).thenReturn(new VersionInfo(10L, 12L, 20L));

        mockMvc.perform(get("/api/taxonomy/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.graphVersion").value(10))
                .andExpect(jsonPath("$.data.totalSkills").value(12))
                .andExpect(jsonPath("$.data.totalEdges").value(20));
    }

    @Test
    void changelogEndpointReturns200() throws Exception {
        when(changelogService.list(any(), any())).thenReturn(List.of(
                new ChangelogEntry(
                        1L,
                        "system",
                        "skill_created",
                        "skill",
                        "skill-1",
                        Map.of("canonical_name", "Machine Learning"),
                        Instant.parse("2026-02-25T00:00:00Z")
                )
        ));

        mockMvc.perform(get("/api/taxonomy/changelog").queryParam("since", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].graphVersion").value(1));
    }
}
