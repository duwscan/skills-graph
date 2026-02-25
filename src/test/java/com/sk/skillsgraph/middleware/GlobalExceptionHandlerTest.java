package com.sk.skillsgraph.middleware;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sk.skillsgraph.util.AppExceptions.DuplicateSkillException;
import com.sk.skillsgraph.util.AppExceptions.SkillNotFoundException;
import com.sk.skillsgraph.util.AppExceptions.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ExceptionThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void skillNotFoundMapsTo404() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.request_id").isNotEmpty());
    }

    @Test
    void duplicateMapsTo409() throws Exception {
        mockMvc.perform(get("/test/duplicate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void validationMapsTo400() throws Exception {
        mockMvc.perform(get("/test/validation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void unknownMapsTo500() throws Exception {
        mockMvc.perform(get("/test/runtime"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(500));
    }

    @RestController
    static class ExceptionThrowingController {

        @GetMapping("/test/not-found")
        public String notFound() {
            throw new SkillNotFoundException("SK-1");
        }

        @GetMapping("/test/duplicate")
        public String duplicate() {
            throw new DuplicateSkillException("SK-2");
        }

        @GetMapping("/test/validation")
        public String validation() {
            throw new ValidationException("Invalid payload");
        }

        @GetMapping("/test/runtime")
        public String runtime() {
            throw new RuntimeException("Boom");
        }
    }
}
