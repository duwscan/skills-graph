package com.sk.skillsgraph.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sk.skillsgraph.dto.ExtractionDto.DiscoveredCandidate;
import com.sk.skillsgraph.dto.ExtractionDto.ExtractedSkill;
import com.sk.skillsgraph.dto.ExtractionDto.ExtractionMetadata;
import com.sk.skillsgraph.dto.ExtractionDto.ExtractionResponse;
import com.sk.skillsgraph.middleware.GlobalExceptionHandler;
import com.sk.skillsgraph.middleware.RequestIdFilter;
import com.sk.skillsgraph.service.extraction.SkillExtractionPipeline;
import com.sk.skillsgraph.util.AppExceptions.ExtractionBusyException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ExtractionControllerTest {

    private MockMvc mockMvc;
    private SkillExtractionPipeline extractionPipeline;

    @BeforeEach
    void setUp() {
        extractionPipeline = Mockito.mock(SkillExtractionPipeline.class);
        ExtractionController controller = new ExtractionController(extractionPipeline);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void extractReturns200() throws Exception {
        ExtractionResponse response = new ExtractionResponse(
                List.of(new ExtractedSkill(
                        "skill-1",
                        "SK-ABC12345",
                        "Python",
                        0.91D,
                        List.of("Python"),
                        "advanced",
                        "requirements",
                        "Requirements",
                        false,
                        null
                )),
                List.of(new DiscoveredCandidate("LangGraph", "tool", "Mentioned in text")),
                new ExtractionMetadata(1, 0, 1, 120L, "fast", "jd", 2, 140, 1)
        );
        when(extractionPipeline.extract(any())).thenReturn(response);

        mockMvc.perform(post("/api/extract")
                        .contentType("application/json")
                        .content("{\"text\":\"Looking for Python and AWS experience\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.skills[0].skill_name").value("Python"))
                .andExpect(jsonPath("$.data.metadata.document_type").value("jd"));
    }

    @Test
    void extractReturns429WithRetryAfter() throws Exception {
        when(extractionPipeline.extract(any())).thenThrow(new ExtractionBusyException("busy", 1));

        mockMvc.perform(post("/api/extract")
                        .contentType("application/json")
                        .content("{\"text\":\"Need Java\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(429));
    }
}
