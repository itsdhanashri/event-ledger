package com.github.itsdhanashri.eventledger.gateway.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import com.github.itsdhanashri.eventledger.gateway.model.EventStatus;
import com.github.itsdhanashri.eventledger.gateway.model.EventType;

public record EventResponse(
        String eventId,
        String accountId,
        EventType type,
        BigDecimal amount,
        String currency,
        Instant eventTimestamp,
        EventStatus status,
        Instant receivedAt,
        Instant processedAt,
        String traceId,
        Map<String, Object> metadata
) {
}
