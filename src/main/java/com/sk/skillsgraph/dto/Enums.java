package com.sk.skillsgraph.dto;

public final class Enums {

    private Enums() {
    }

    public enum SkillStatus {
        candidate,
        active,
        deprecated,
        merged
    }

    public enum SkillCategory {
        domain,
        tool,
        certification,
        soft_skill,
        methodology,
        language,
        root
    }

    public enum RelationshipType {
        parent_of,
        child_of,
        related_to,
        requires,
        superseded_by
    }

    public enum Provenance {
        human_curated,
        llm_predicted,
        embedding_similarity,
        empirical
    }

    public enum AliasSource {
        curated,
        llm_discovered,
        user_submitted
    }

    public enum EdgeStatus {
        active,
        pending_review,
        rejected,
        deprecated
    }
}
