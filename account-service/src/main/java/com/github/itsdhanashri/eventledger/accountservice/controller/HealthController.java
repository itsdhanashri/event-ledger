package com.github.itsdhanashri.eventledger.accountservice.controller;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.github.itsdhanashri.eventledger.accountservice.dto.response.HealthResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final MongoTemplate mongoTemplate;
    private final String version;

    public HealthController(MongoTemplate mongoTemplate,
                            @Value("${spring.application.version:1.0.0}") String version) {
        this.mongoTemplate = mongoTemplate;
        this.version = version;
    }

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        Map<String, Object> checks = new LinkedHashMap<>();
        long started = System.nanoTime();
        String mongoStatus = "UP";
        try {
            mongoTemplate.getDb().runCommand(new org.bson.Document("ping", 1));
        } catch (RuntimeException ex) {
            mongoStatus = "DOWN";
        }
        long responseTimeMs = (System.nanoTime() - started) / 1_000_000;
        checks.put("mongodb", Map.of("status", mongoStatus, "responseTimeMs", responseTimeMs));
        String status = "UP".equals(mongoStatus) ? "UP" : "DOWN";
        return ResponseEntity.status("UP".equals(status) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(new HealthResponse("account-service", status, Instant.now(), version, checks));
    }
}
