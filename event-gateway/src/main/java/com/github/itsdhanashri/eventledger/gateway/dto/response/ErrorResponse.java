package com.github.itsdhanashri.eventledger.gateway.dto.response;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        int status,
        String error,
        String message,
        String traceId,
        Instant timestamp,
        List<FieldErrorResponse> errors,
        Integer retryAfterSeconds
) {
    public record FieldErrorResponse(String field, String message) {
    }
}
