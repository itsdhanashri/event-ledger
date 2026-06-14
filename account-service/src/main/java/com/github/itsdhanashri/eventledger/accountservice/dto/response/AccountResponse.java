package com.github.itsdhanashri.eventledger.accountservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.github.itsdhanashri.eventledger.accountservice.model.TransactionType;

public record AccountResponse(
        String accountId,
        BigDecimal balance,
        String currency,
        Instant createdAt,
        Instant updatedAt,
        List<RecentTransactionResponse> recentTransactions
) {
    public record RecentTransactionResponse(
            String eventId,
            TransactionType type,
            BigDecimal amount,
            Instant eventTimestamp
    ) {
    }
}
