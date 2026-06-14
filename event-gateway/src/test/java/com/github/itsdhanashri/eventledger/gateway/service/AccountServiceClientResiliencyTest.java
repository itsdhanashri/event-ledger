package com.github.itsdhanashri.eventledger.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.itsdhanashri.eventledger.gateway.dto.request.EventRequest;
import com.github.itsdhanashri.eventledger.gateway.exception.AccountServiceException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class AccountServiceClientResiliencyTest {

    @Test
    void circuitOpensAfterRepeatedFailures() {
        // configure a small circuit breaker window so test triggers quickly
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50)
                .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);

        AtomicInteger calls = new AtomicInteger();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    calls.incrementAndGet();
                    // Always return 500 to simulate downstream failure
                    return Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR)
                            .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                            .body("server error")
                            .build());
                })
                .build();

        AccountServiceClient client = new AccountServiceClient(webClient, registry, new SimpleMeterRegistry());

        // Repeatedly call and expect AccountServiceException as downstream returns 500
        for (int i = 0; i < 4; i++) {
            assertThrows(AccountServiceException.class, () -> client.applyTransaction(request(), "trace-1"));
        }

        // After failures, the circuit state for 'accountService' should be OPEN
        var cb = registry.circuitBreaker("accountService");
        assertThat(cb.getState().name()).isEqualTo("OPEN");

        // When circuit is open, further calls should fail fast (still throw AccountServiceException)
        assertThrows(AccountServiceException.class, () -> client.applyTransaction(request(), "trace-1"));
    }

    private EventRequest request() {
        return new EventRequest("evt-001", "acct-123", "CREDIT", new BigDecimal("150.00"), "USD",
                Instant.parse("2026-05-15T14:02:11Z"), null);
    }
}

