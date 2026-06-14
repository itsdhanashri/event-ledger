package com.github.itsdhanashri.eventledger.gateway.controller;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.github.itsdhanashri.eventledger.gateway.dto.response.HealthResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

@RestController
public class HealthController {

    private final MongoTemplate mongoTemplate;
    private final WebClient accountServiceWebClient;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final String version;

    public HealthController(MongoTemplate mongoTemplate,
                            WebClient accountServiceWebClient,
                            CircuitBreakerRegistry circuitBreakerRegistry,
                            @Value("${spring.application.version:1.0.0}") String version) {
        this.mongoTemplate = mongoTemplate;
        this.accountServiceWebClient = accountServiceWebClient;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.version = version;
    }

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        Map<String, Object> checks = new LinkedHashMap<>();
        String mongoStatus = pingMongo(checks);
        checks.put("accountService", accountServiceCheck());
        String status = "UP".equals(mongoStatus) ? "UP" : "DOWN";
        return ResponseEntity.status("UP".equals(status) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(new HealthResponse("event-gateway", status, Instant.now(), version, checks));
    }

    private String pingMongo(Map<String, Object> checks) {
        long started = System.nanoTime();
        String mongoStatus = "UP";
        try {
            mongoTemplate.getDb().runCommand(new org.bson.Document("ping", 1));
        } catch (RuntimeException ex) {
            mongoStatus = "DOWN";
        }
        long responseTimeMs = (System.nanoTime() - started) / 1_000_000;
        checks.put("mongodb", Map.of("status", mongoStatus, "responseTimeMs", responseTimeMs));
        return mongoStatus;
    }

    private Map<String, Object> accountServiceCheck() {
        String status = "UP";
        try {
            accountServiceWebClient.get()
                    .uri("/health")
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(2))
                    .block();
        } catch (RuntimeException ex) {
            status = "DOWN";
        }
        String circuitState = circuitBreakerRegistry.circuitBreaker("accountService").getState().name();
        return Map.of("status", status, "circuitBreakerState", circuitState);
    }
}
