package com.sk.skillsgraph.controller;

import com.sk.skillsgraph.dto.ApiResponse;
import com.sk.skillsgraph.dto.ApiResponseEntity;
import com.sk.skillsgraph.dto.EdgeDto.CreateEdgeRequest;
import com.sk.skillsgraph.dto.EdgeDto.EdgeResponse;
import com.sk.skillsgraph.service.EdgeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/edges")
public class EdgeController {

    private final EdgeService edgeService;

    public EdgeController(EdgeService edgeService) {
        this.edgeService = edgeService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EdgeResponse>> createEdge(
            @Valid @RequestBody CreateEdgeRequest input,
            HttpServletRequest request
    ) {
        return ApiResponseEntity.success(HttpStatus.CREATED, "Edge created", edgeService.create(input), request);
    }

    @PostMapping("/{edgeId}/deprecate")
    public ResponseEntity<ApiResponse<EdgeResponse>> deprecateEdge(
            @PathVariable String edgeId,
            HttpServletRequest request
    ) {
        return ApiResponseEntity.success(HttpStatus.OK, "Edge deprecated", edgeService.deprecate(edgeId), request);
    }

    @DeleteMapping("/{edgeId}")
    public ResponseEntity<ApiResponse<Void>> deleteEdge(
            @PathVariable String edgeId,
            HttpServletRequest request
    ) {
        edgeService.delete(edgeId);
        return ApiResponseEntity.success(HttpStatus.OK, "Edge deleted", null, request);
    }
}
