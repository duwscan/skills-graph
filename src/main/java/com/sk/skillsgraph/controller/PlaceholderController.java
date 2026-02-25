package com.sk.skillsgraph.controller;

import com.sk.skillsgraph.dto.ApiResponse;
import com.sk.skillsgraph.dto.ApiResponseEntity;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PlaceholderController {

    @GetMapping("/extract")
    public ResponseEntity<ApiResponse<Map<String, Object>>> extractRoot(HttpServletRequest request) {
        return placeholder("extract", request);
    }

    @GetMapping("/review-queue")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reviewQueueRoot(HttpServletRequest request) {
        return placeholder("review-queue", request);
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> placeholder(String routeGroup, HttpServletRequest request) {
        Map<String, Object> body = Map.of(
                "route_group", routeGroup,
                "status", "not_implemented",
                "message", "Phase 4+ endpoints will be implemented in subsequent phases"
        );
        return ApiResponseEntity.success(HttpStatus.OK, "Endpoint placeholder", body, request);
    }
}
