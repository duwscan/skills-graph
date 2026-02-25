package com.sk.skillsgraph.controller;

import com.sk.skillsgraph.dto.ApiResponse;
import com.sk.skillsgraph.dto.ApiResponseEntity;
import com.sk.skillsgraph.dto.ExtractionDto.ExtractionRequest;
import com.sk.skillsgraph.dto.ExtractionDto.ExtractionResponse;
import com.sk.skillsgraph.middleware.RequestIdFilter;
import com.sk.skillsgraph.service.extraction.SkillExtractionPipeline;
import com.sk.skillsgraph.util.AppExceptions.ExtractionBusyException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/extract")
public class ExtractionController {

    private final SkillExtractionPipeline extractionPipeline;

    public ExtractionController(SkillExtractionPipeline extractionPipeline) {
        this.extractionPipeline = extractionPipeline;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ExtractionResponse>> extract(
            @Valid @RequestBody ExtractionRequest input,
            HttpServletRequest request
    ) {
        try {
            ExtractionResponse response = extractionPipeline.extract(input);
            return ApiResponseEntity.success(HttpStatus.OK, "Extraction completed", response, request);
        } catch (ExtractionBusyException busyException) {
            ApiResponse<ExtractionResponse> body = new ApiResponse<>(
                    false,
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    busyException.getMessage(),
                    null,
                    resolveRequestId(request),
                    Instant.now()
            );
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", Integer.toString(busyException.retryAfterSeconds()))
                    .body(body);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return requestId == null ? "" : requestId.toString();
    }
}
