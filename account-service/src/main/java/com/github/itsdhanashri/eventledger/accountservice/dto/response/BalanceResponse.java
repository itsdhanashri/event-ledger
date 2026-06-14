package com.github.itsdhanashri.eventledger.accountservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record BalanceResponse(
        String accountId,
        BigDecimal balance,
        String currency,
        long transactionCount,
        Instant lastUpdated
) {
}
