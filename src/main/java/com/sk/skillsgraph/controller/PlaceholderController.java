package com.sk.skillsgraph.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PlaceholderController {

    @GetMapping("/skills")
    public Map<String, Object> skillsRoot() {
        return placeholder("skills");
    }

    @GetMapping("/edges")
    public Map<String, Object> edgesRoot() {
        return placeholder("edges");
    }

    @GetMapping("/extract")
    public Map<String, Object> extractRoot() {
        return placeholder("extract");
    }

    @GetMapping("/taxonomy")
    public Map<String, Object> taxonomyRoot() {
        return placeholder("taxonomy");
    }

    @GetMapping("/review-queue")
    public Map<String, Object> reviewQueueRoot() {
        return placeholder("review-queue");
    }

    private Map<String, Object> placeholder(String routeGroup) {
        return Map.of(
                "route_group", routeGroup,
                "status", "not_implemented",
                "message", "Phase 2+ endpoints will be implemented in subsequent phases"
        );
    }
}
