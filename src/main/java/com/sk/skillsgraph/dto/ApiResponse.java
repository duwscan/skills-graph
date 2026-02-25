package com.sk.skillsgraph.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record ApiResponse<T>(
        boolean success,
        int code,
        String message,
        T data,
        @JsonProperty("request_id") String requestId,
        Instant timestamp
) {
}
