package com.github.itsdhanashri.eventledger.accountservice.dto.response;

import java.time.Instant;
import java.util.Map;

public record HealthResponse(
        String service,
        String status,
        Instant timestamp,
        String version,
        Map<String, Object> checks
) {
}
