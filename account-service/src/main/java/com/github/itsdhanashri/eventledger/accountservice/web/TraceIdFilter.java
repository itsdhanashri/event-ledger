package com.github.itsdhanashri.eventledger.accountservice.web;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_ATTRIBUTE = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = extractTraceId(request);
        request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);
        MDC.put("traceId", traceId);
        MDC.put("service", "account-service");
        response.setHeader("X-Trace-Id", traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private String extractTraceId(HttpServletRequest request) {
        String traceparent = request.getHeader("traceparent");
        if (traceparent != null && traceparent.length() >= 55) {
            String[] parts = traceparent.split("-");
            if (parts.length >= 2 && !parts[1].isBlank()) {
                return parts[1];
            }
        }
        String traceId = request.getHeader("X-Trace-Id");
        return traceId == null || traceId.isBlank() ? UUID.randomUUID().toString().replace("-", "") : traceId;
    }
}
