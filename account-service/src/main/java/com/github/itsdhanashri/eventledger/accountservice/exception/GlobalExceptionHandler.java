package com.github.itsdhanashri.eventledger.accountservice.exception;

import java.time.Instant;
import java.util.List;

import com.github.itsdhanashri.eventledger.accountservice.dto.response.ErrorResponse;
import com.github.itsdhanashri.eventledger.accountservice.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldErrorResponse> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponse.FieldErrorResponse(error.getField(), error.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Validation failed", request, errors, null);
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(AccountNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, List.of(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled account-service error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected Account Service error", request, List.of(), null);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request,
                                                List<ErrorResponse.FieldErrorResponse> errors,
                                                Integer retryAfterSeconds) {
        String traceId = (String) request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
        ErrorResponse body = new ErrorResponse(status.value(), status.getReasonPhrase(), message, traceId, Instant.now(),
                errors, retryAfterSeconds);
        return ResponseEntity.status(status).body(body);
    }
}
