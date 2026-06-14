package com.github.itsdhanashri.eventledger.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    public MetricsConfig(MeterRegistry meterRegistry, CircuitBreakerRegistry circuitBreakerRegistry) {
        Gauge.builder("circuit_breaker_state", circuitBreakerRegistry.circuitBreaker("accountService"),
                        circuitBreaker -> switch (circuitBreaker.getState()) {
                            case CLOSED -> 0;
                            case OPEN -> 1;
                            case HALF_OPEN -> 2;
                            default -> 3;
                        })
                .tag("name", "accountService")
                .register(meterRegistry);
    }
}
