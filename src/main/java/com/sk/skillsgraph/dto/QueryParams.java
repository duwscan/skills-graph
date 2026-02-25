package com.sk.skillsgraph.dto;

import com.sk.skillsgraph.config.AppConstants;
import com.sk.skillsgraph.dto.Enums.SkillCategory;
import com.sk.skillsgraph.dto.Enums.SkillStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public final class QueryParams {

    private QueryParams() {
    }

    public record PaginationQuery(
            @Min(1)
            @Max(AppConstants.PAGINATION_MAX_LIMIT)
            Integer limit,
            @Min(0)
            Integer offset
    ) {
        public int normalizedLimit() {
            if (limit == null) {
                return AppConstants.PAGINATION_DEFAULT_LIMIT;
            }
            return Math.max(1, Math.min(limit, AppConstants.PAGINATION_MAX_LIMIT));
        }

        public int normalizedOffset() {
            if (offset == null) {
                return 0;
            }
            return Math.max(0, offset);
        }
    }

    public record ListSkillsQuery(
            @Min(1)
            @Max(AppConstants.PAGINATION_MAX_LIMIT)
            Integer limit,
            @Min(0)
            Integer offset,
            SkillStatus status,
            SkillCategory category,
            String q
    ) {
        public int normalizedLimit() {
            if (limit == null) {
                return AppConstants.PAGINATION_DEFAULT_LIMIT;
            }
            return Math.max(1, Math.min(limit, AppConstants.PAGINATION_MAX_LIMIT));
        }

        public int normalizedOffset() {
            if (offset == null) {
                return 0;
            }
            return Math.max(0, offset);
        }
    }

    public record DepthQuery(
            @Min(1)
            @Max(AppConstants.TRAVERSAL_MAX_DEPTH)
            Integer depth
    ) {
        public int normalizedDepth() {
            if (depth == null) {
                return AppConstants.TRAVERSAL_DEFAULT_DEPTH;
            }
            if (depth < 1) {
                return AppConstants.TRAVERSAL_DEFAULT_DEPTH;
            }
            return Math.min(depth, AppConstants.TRAVERSAL_MAX_DEPTH);
        }
    }
}
