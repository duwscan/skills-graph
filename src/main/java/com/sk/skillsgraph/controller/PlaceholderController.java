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

    @GetMapping("/skills")
    public ResponseEntity<ApiResponse<Map<String, Object>>> skillsRoot(HttpServletRequest request) {
        return placeholder("skills", request);
    }

    @GetMapping("/edges")
    public ResponseEntity<ApiResponse<Map<String, Object>>> edgesRoot(HttpServletRequest request) {
        return placeholder("edges", request);
    }

    @GetMapping("/extract")
    public ResponseEntity<ApiResponse<Map<String, Object>>> extractRoot(HttpServletRequest request) {
        return placeholder("extract", request);
    }

    @GetMapping("/taxonomy")
    public ResponseEntity<ApiResponse<Map<String, Object>>> taxonomyRoot(HttpServletRequest request) {
        return placeholder("taxonomy", request);
    }

    @GetMapping("/review-queue")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reviewQueueRoot(HttpServletRequest request) {
        return placeholder("review-queue", request);
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> placeholder(String routeGroup, HttpServletRequest request) {
        Map<String, Object> body = Map.of(
                "route_group", routeGroup,
                "status", "not_implemented",
                "message", "Phase 2+ endpoints will be implemented in subsequent phases"
        );
        return ApiResponseEntity.success(HttpStatus.OK, "Endpoint placeholder", body, request);
    }
}
