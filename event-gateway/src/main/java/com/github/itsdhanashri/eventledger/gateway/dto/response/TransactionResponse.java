package com.github.itsdhanashri.eventledger.gateway.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        String eventId,
        String accountId,
        String type,
        BigDecimal amount,
        String currency,
        Instant eventTimestamp,
        BigDecimal newBalance,
        Instant appliedAt,
        String traceId
) {
}
