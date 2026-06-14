package com.github.itsdhanashri.eventledger.accountservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

import com.github.itsdhanashri.eventledger.accountservice.model.TransactionType;

public record TransactionResponse(
        String eventId,
        String accountId,
        TransactionType type,
        BigDecimal amount,
        String currency,
        Instant eventTimestamp,
        BigDecimal newBalance,
        Instant appliedAt,
        String traceId
) {
}
