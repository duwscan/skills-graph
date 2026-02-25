package com.sk.skillsgraph.dto;

import com.sk.skillsgraph.middleware.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public final class ApiResponseEntity {

    private ApiResponseEntity() {
    }

    public static <T> ResponseEntity<ApiResponse<T>> success(
            HttpStatus status,
            String message,
            T data,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(status)
                .body(new ApiResponse<>(
                        true,
                        status.value(),
                        resolveMessage(message, status),
                        data,
                        resolveRequestId(request),
                        Instant.now()
                ));
    }

    public static ResponseEntity<ApiResponse<Void>> error(
            HttpStatus status,
            String message,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(status)
                .body(new ApiResponse<>(
                        false,
                        status.value(),
                        resolveMessage(message, status),
                        null,
                        resolveRequestId(request),
                        Instant.now()
                ));
    }

    private static String resolveRequestId(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return requestId == null ? "" : requestId.toString();
    }

    private static String resolveMessage(String message, HttpStatus status) {
        return message == null || message.isBlank() ? status.getReasonPhrase() : message;
    }
}
