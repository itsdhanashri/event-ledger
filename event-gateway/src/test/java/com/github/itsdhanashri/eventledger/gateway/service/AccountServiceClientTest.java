package com.github.itsdhanashri.eventledger.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import com.github.itsdhanashri.eventledger.gateway.dto.request.EventRequest;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class AccountServiceClientTest {

    @Test
    void propagatesTraceHeadersToAccountService() {
        AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    capturedRequest.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.CREATED)
                            .header(HttpHeaders.CONTENT_TYPE, "application/json")
                            .body("{\"eventId\":\"evt-001\",\"accountId\":\"acct-123\",\"type\":\"CREDIT\",\"amount\":150.00,\"currency\":\"USD\",\"eventTimestamp\":\"2026-05-15T14:02:11Z\",\"newBalance\":150.00,\"appliedAt\":\"2026-05-15T14:03:01Z\",\"traceId\":\"abc123abc123abc123abc123abc123ab\"}")
                            .build());
                })
                .build();
        AccountServiceClient client = new AccountServiceClient(webClient, CircuitBreakerRegistry.ofDefaults(), new SimpleMeterRegistry());
        String traceId = "abc123abc123abc123abc123abc123ab";

        client.applyTransaction(request(), traceId);

        ClientRequest request = capturedRequest.get();
        assertThat(request.url().getPath()).isEqualTo("/accounts/acct-123/transactions");
        assertThat(request.headers().getFirst("X-Trace-Id")).isEqualTo(traceId);
        assertThat(request.headers().getFirst("traceparent")).startsWith("00-" + traceId + "-");
    }

    private EventRequest request() {
        return new EventRequest("evt-001", "acct-123", "CREDIT", new BigDecimal("150.00"), "USD",
                Instant.parse("2026-05-15T14:02:11Z"), null);
    }
}
