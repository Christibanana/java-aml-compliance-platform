package com.example.aml.common;

import com.example.aml.screening.ScreeningProviderException;
import com.example.aml.integration.ExternalIntegrationException;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    public record ErrorBody(Instant timestamp, int status, String code, String message, String path, Map<String,String> fields) {}

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorBody> badRequest(IllegalArgumentException ex, HttpServletRequest req) {
        return response(HttpStatus.BAD_REQUEST, "BUSINESS_VALIDATION_FAILED", ex.getMessage(), req, Map.of());
    }
    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ErrorBody> notFound(NoSuchElementException ex, HttpServletRequest req) {
        return response(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage(), req, Map.of());
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorBody> validation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String,String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return response(HttpStatus.BAD_REQUEST, "REQUEST_VALIDATION_FAILED", "请求参数校验失败", req, fields);
    }
    @ExceptionHandler(ScreeningProviderException.class)
    ResponseEntity<ErrorBody> providerFailure(ScreeningProviderException ex, HttpServletRequest req) {
        Map<String,String> fields = ex.requestId() == null ? Map.of() : Map.of("requestId", ex.requestId().toString());
        return response(HttpStatus.BAD_GATEWAY, "SCREENING_PROVIDER_FAILED", ex.getMessage(), req, fields);
    }
    @ExceptionHandler(ExternalIntegrationException.class)
    ResponseEntity<ErrorBody> integrationFailure(ExternalIntegrationException ex, HttpServletRequest req) {
        return response(HttpStatus.BAD_GATEWAY, "EXTERNAL_INTEGRATION_FAILED", ex.getMessage(), req, Map.of());
    }
    private ResponseEntity<ErrorBody> response(HttpStatus status, String code, String message, HttpServletRequest req, Map<String,String> fields) {
        return ResponseEntity.status(status).body(new ErrorBody(Instant.now(), status.value(), code, message, req.getRequestURI(), fields));
    }
}
