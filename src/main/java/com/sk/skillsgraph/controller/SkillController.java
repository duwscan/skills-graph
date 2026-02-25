package com.sk.skillsgraph.controller;

import com.sk.skillsgraph.dto.AliasDto.AliasResponse;
import com.sk.skillsgraph.dto.AliasDto.CreateAliasRequest;
import com.sk.skillsgraph.dto.AliasDto.UpdateAliasRequest;
import com.sk.skillsgraph.dto.ApiResponse;
import com.sk.skillsgraph.dto.ApiResponseEntity;
import com.sk.skillsgraph.dto.EdgeDto.EdgeResponse;
import com.sk.skillsgraph.dto.QueryParams.DepthQuery;
import com.sk.skillsgraph.dto.QueryParams.ListSkillsQuery;
import com.sk.skillsgraph.dto.SearchDto.SearchFilters;
import com.sk.skillsgraph.dto.SearchDto.SearchResponse;
import com.sk.skillsgraph.dto.SkillDto.CreateSkillRequest;
import com.sk.skillsgraph.dto.SkillDto.ListSkillsResponse;
import com.sk.skillsgraph.dto.SkillDto.SkillPathNode;
import com.sk.skillsgraph.dto.SkillDto.SkillResponse;
import com.sk.skillsgraph.dto.SkillDto.UpdateSkillRequest;
import com.sk.skillsgraph.service.AliasService;
import com.sk.skillsgraph.service.EdgeService;
import com.sk.skillsgraph.service.HybridSearchService;
import com.sk.skillsgraph.service.SkillService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/skills")
public class SkillController {

    private final SkillService skillService;
    private final AliasService aliasService;
    private final EdgeService edgeService;
    private final HybridSearchService hybridSearchService;

    public SkillController(
            SkillService skillService,
            AliasService aliasService,
            EdgeService edgeService,
            HybridSearchService hybridSearchService
    ) {
        this.skillService = skillService;
        this.aliasService = aliasService;
        this.edgeService = edgeService;
        this.hybridSearchService = hybridSearchService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SkillResponse>> createSkill(
            @Valid @RequestBody CreateSkillRequest input,
            HttpServletRequest request
    ) {
        return ApiResponseEntity.success(HttpStatus.CREATED, "Skill created", skillService.create(input), request);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SkillResponse>> getSkill(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        return ApiResponseEntity.success(HttpStatus.OK, "Skill fetched", skillService.getById(id), request);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<SkillResponse>> updateSkill(
            @PathVariable String id,
            @Valid @RequestBody UpdateSkillRequest input,
            HttpServletRequest request
    ) {
        return ApiResponseEntity.success(HttpStatus.OK, "Skill updated", skillService.update(id, input), request);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ListSkillsResponse>> listSkills(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false) com.sk.skillsgraph.dto.Enums.SkillStatus status,
            @RequestParam(required = false) com.sk.skillsgraph.dto.Enums.SkillCategory category,
            @RequestParam(required = false) String q,
            HttpServletRequest request
    ) {
        ListSkillsQuery query = new ListSkillsQuery(limit, offset, status, category, q);
        return ApiResponseEntity.success(HttpStatus.OK, "Skills listed", skillService.list(query), request);
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<SearchResponse>> searchSkills(
            @RequestParam String q,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) com.sk.skillsgraph.dto.Enums.SkillStatus status,
            @RequestParam(required = false) com.sk.skillsgraph.dto.Enums.SkillCategory category,
            HttpServletRequest request
    ) {
        SearchFilters filters = new SearchFilters(category, status);
        SearchResponse response = hybridSearchService.search(q, filters, limit == null ? 0 : limit);
        return ApiResponseEntity.success(HttpStatus.OK, "Skills searched", response, request);
    }

    @GetMapping("/{id}/ancestors")
    public ResponseEntity<ApiResponse<List<SkillPathNode>>> getAncestors(
            @PathVariable String id,
            @RequestParam(required = false) Integer depth,
            HttpServletRequest request
    ) {
        return ApiResponseEntity.success(
                HttpStatus.OK,
                "Ancestors fetched",
                skillService.getAncestors(id, new DepthQuery(depth)),
                request
        );
    }

    @GetMapping("/{id}/descendants")
    public ResponseEntity<ApiResponse<List<SkillPathNode>>> getDescendants(
            @PathVariable String id,
            @RequestParam(required = false) Integer depth,
            HttpServletRequest request
    ) {
        return ApiResponseEntity.success(
                HttpStatus.OK,
                "Descendants fetched",
                skillService.getDescendants(id, new DepthQuery(depth)),
                request
        );
    }

    @GetMapping("/{id}/related")
    public ResponseEntity<ApiResponse<List<EdgeResponse>>> getRelated(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        return ApiResponseEntity.success(HttpStatus.OK, "Related skills fetched", edgeService.getRelated(id), request);
    }

    @GetMapping("/{id}/edges")
    public ResponseEntity<ApiResponse<Map<String, List<EdgeResponse>>>> getEdges(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        return ApiResponseEntity.success(HttpStatus.OK, "Skill edges fetched", edgeService.getBySkill(id), request);
    }

    @PostMapping("/{id}/aliases")
    public ResponseEntity<ApiResponse<AliasResponse>> createAlias(
            @PathVariable String id,
            @Valid @RequestBody CreateAliasRequest input,
            HttpServletRequest request
    ) {
        return ApiResponseEntity.success(HttpStatus.CREATED, "Alias created", aliasService.create(id, input), request);
    }

    @GetMapping("/{id}/aliases")
    public ResponseEntity<ApiResponse<List<AliasResponse>>> listAliases(
            @PathVariable String id,
            @RequestParam(required = false) String locale,
            HttpServletRequest request
    ) {
        return ApiResponseEntity.success(HttpStatus.OK, "Aliases fetched", aliasService.listBySkill(id, locale), request);
    }

    @PatchMapping("/{id}/aliases/{aliasId}")
    public ResponseEntity<ApiResponse<AliasResponse>> updateAlias(
            @PathVariable String id,
            @PathVariable String aliasId,
            @Valid @RequestBody UpdateAliasRequest input,
            HttpServletRequest request
    ) {
        return ApiResponseEntity.success(HttpStatus.OK, "Alias updated", aliasService.update(id, aliasId, input), request);
    }

    @DeleteMapping("/{id}/aliases/{aliasId}")
    public ResponseEntity<ApiResponse<Void>> deleteAlias(
            @PathVariable String id,
            @PathVariable String aliasId,
            HttpServletRequest request
    ) {
        aliasService.delete(id, aliasId);
        return ApiResponseEntity.success(HttpStatus.OK, "Alias deleted", null, request);
    }
}
