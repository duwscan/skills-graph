package com.sk.skillsgraph.controller;

import com.sk.skillsgraph.dto.ApiResponse;
import com.sk.skillsgraph.dto.ApiResponseEntity;
import com.sk.skillsgraph.dto.SkillDto.SkillSummary;
import com.sk.skillsgraph.service.ChangelogService;
import com.sk.skillsgraph.service.ChangelogService.ChangelogEntry;
import com.sk.skillsgraph.service.ChangelogService.VersionInfo;
import com.sk.skillsgraph.service.SkillService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/taxonomy")
public class TaxonomyController {

    private final SkillService skillService;
    private final ChangelogService changelogService;

    public TaxonomyController(SkillService skillService, ChangelogService changelogService) {
        this.skillService = skillService;
        this.changelogService = changelogService;
    }

    @GetMapping("/roots")
    public ResponseEntity<ApiResponse<List<SkillSummary>>> getRoots(HttpServletRequest request) {
        return ApiResponseEntity.success(HttpStatus.OK, "Root taxonomy fetched", skillService.getRoots(), request);
    }

    @GetMapping("/version")
    public ResponseEntity<ApiResponse<VersionInfo>> getVersion(HttpServletRequest request) {
        return ApiResponseEntity.success(HttpStatus.OK, "Taxonomy version fetched", changelogService.getVersion(), request);
    }

    @GetMapping("/changelog")
    public ResponseEntity<ApiResponse<List<ChangelogEntry>>> getChangelog(
            @RequestParam(required = false) Long since,
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request
    ) {
        return ApiResponseEntity.success(HttpStatus.OK, "Taxonomy changelog fetched", changelogService.list(since, limit), request);
    }
}
