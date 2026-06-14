package com.github.itsdhanashri.eventledger.gateway.service;

import java.time.Duration;
import java.util.UUID;

import com.github.itsdhanashri.eventledger.gateway.dto.request.EventRequest;
import com.github.itsdhanashri.eventledger.gateway.dto.request.TransactionRequest;
import com.github.itsdhanashri.eventledger.gateway.dto.response.TransactionResponse;
import com.github.itsdhanashri.eventledger.gateway.exception.AccountServiceException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class AccountServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AccountServiceClient.class);

    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    private final MeterRegistry meterRegistry;

    public AccountServiceClient(WebClient accountServiceWebClient,
                                CircuitBreakerRegistry circuitBreakerRegistry,
                                MeterRegistry meterRegistry) {
        this.webClient = accountServiceWebClient;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("accountService");
        this.meterRegistry = meterRegistry;
    }

    public TransactionResponse applyTransaction(EventRequest request, String traceId) {
        try {
            TransactionResponse response = circuitBreaker.executeSupplier(() -> callAccountService(request, traceId));
            meterRegistry.counter("account_service_calls_total", "outcome", "success").increment();
            return response;
        } catch (Exception ex) {
            meterRegistry.counter("account_service_calls_total", "outcome", "failure").increment();
            log.warn("Account Service unavailable for eventId={}", request.eventId(), ex);
            if (ex instanceof AccountServiceException accountServiceException) {
                throw accountServiceException;
            }
            throw new AccountServiceException("Account Service is currently unavailable. Event has not been processed. Please retry.", ex);
        }
    }

    private TransactionResponse callAccountService(EventRequest request, String traceId) {
        TransactionRequest transactionRequest = new TransactionRequest(request.eventId(), request.type(), request.amount(),
                request.currency(), request.eventTimestamp());
        return webClient.post()
                .uri("/accounts/{accountId}/transactions", request.accountId())
                .header("X-Trace-Id", traceId)
                .header("traceparent", buildTraceParent(traceId))
                .bodyValue(transactionRequest)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                        response -> response.bodyToMono(String.class)
                                .defaultIfEmpty("Account Service rejected transaction")
                                .flatMap(body -> Mono.error(new AccountServiceException(body))))
                .onStatus(HttpStatusCode::is5xxServerError,
                        response -> response.bodyToMono(String.class)
                                .defaultIfEmpty("Account Service returned server error")
                                .flatMap(body -> Mono.error(new AccountServiceException(body))))
                .bodyToMono(TransactionResponse.class)
                .timeout(Duration.ofSeconds(5))
                .block();
    }

    private String buildTraceParent(String traceId) {
        String normalized = traceId == null ? "" : traceId.replace("-", "");
        if (!normalized.matches("[0-9a-fA-F]{32}")) {
            normalized = UUID.nameUUIDFromBytes(String.valueOf(traceId).getBytes()).toString().replace("-", "");
        }
        String spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return "00-" + normalized.toLowerCase() + "-" + spanId + "-01";
    }
}
