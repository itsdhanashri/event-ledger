package com.github.itsdhanashri.eventledger.gateway.dto.request;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EventRequest(
        @NotBlank(message = "eventId is required")
        @Size(max = 100, message = "eventId must be at most 100 characters")
        String eventId,

        @NotBlank(message = "accountId is required")
        @Size(max = 100, message = "accountId must be at most 100 characters")
        String accountId,

        @NotNull(message = "type is required")
        @Pattern(regexp = "CREDIT|DEBIT", flags = Pattern.Flag.CASE_INSENSITIVE,
                message = "type must be CREDIT or DEBIT")
        String type,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "amount must be greater than 0")
        @Digits(integer = 15, fraction = 2, message = "amount must have at most 2 decimal places")
        BigDecimal amount,

        @NotBlank(message = "currency is required")
        @Size(min = 3, max = 3, message = "currency must be a 3-character ISO 4217 code")
        String currency,

        @NotNull(message = "eventTimestamp is required")
        Instant eventTimestamp,

        Map<String, Object> metadata
) {
}
