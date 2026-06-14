package com.github.itsdhanashri.eventledger.gateway.dto.request;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionRequest(
        String eventId,
        String type,
        BigDecimal amount,
        String currency,
        Instant eventTimestamp
) {
}
