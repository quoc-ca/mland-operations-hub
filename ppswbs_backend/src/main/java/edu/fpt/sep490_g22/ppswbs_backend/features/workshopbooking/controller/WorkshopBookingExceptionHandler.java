package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.ProblemDetailResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * T013: Global exception handler for Workshop Booking module.
 * Maps exceptions into typed RFC 7807 Problem Details with correlation tracking,
 * preventing entity leakage, credential disclosure, and sensitive customer PII exposure.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = {
    "edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking",
    "edu.fpt.sep490_g22.ppswbs_backend.workshopbooking"
})
public class WorkshopBookingExceptionHandler {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String PROBLEM_BASE_URI = "urn:problem-type:workshop-booking:";

    @ExceptionHandler(WorkshopBookingUnauthorizedException.class)
    public ResponseEntity<ProblemDetailResponse> handleUnauthorized(
            WorkshopBookingUnauthorizedException ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        log.warn("Workshop booking auth failure [correlationId={}]: {}", correlationId, ex.getMessage());
        return buildResponse(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized", ex.getMessage(), correlationId);
    }

    @ExceptionHandler(WorkshopBookingLocationScopeException.class)
    public ResponseEntity<ProblemDetailResponse> handleLocationScope(
            WorkshopBookingLocationScopeException ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        log.warn("Workshop booking location scope denial [correlationId={}]: {}", correlationId, ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, "LOCATION_SCOPE_DENIED", "Location Scope Denied", ex.getMessage(), correlationId);
    }

    @ExceptionHandler(WorkshopBookingForbiddenException.class)
    public ResponseEntity<ProblemDetailResponse> handleForbidden(
            WorkshopBookingForbiddenException ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        log.warn("Workshop booking forbidden access [correlationId={}]: {}", correlationId, ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, "FORBIDDEN", "Forbidden", ex.getMessage(), correlationId);
    }

    @ExceptionHandler(BookingNotFoundException.class)
    public ResponseEntity<ProblemDetailResponse> handleNotFound(
            BookingNotFoundException ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        return buildResponse(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource Not Found", ex.getMessage(), correlationId);
    }

    @ExceptionHandler(BookingValidationException.class)
    public ResponseEntity<ProblemDetailResponse> handleValidation(
            BookingValidationException ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation Error", ex.getMessage(), correlationId);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetailResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        if (details.isBlank()) {
            details = "Request parameters failed validation constraints";
        }
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation Error", details, correlationId);
    }

    @ExceptionHandler(BookingIdempotencyConflictException.class)
    public ResponseEntity<ProblemDetailResponse> handleIdempotencyConflict(
            BookingIdempotencyConflictException ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        log.warn("Workshop booking idempotency conflict [correlationId={}]: {}", correlationId, ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "Idempotency Conflict", ex.getMessage(), correlationId);
    }

    @ExceptionHandler(BookingConflictException.class)
    public ResponseEntity<ProblemDetailResponse> handleConflict(
            BookingConflictException ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        log.warn("Workshop booking conflict [correlationId={}]: {}", correlationId, ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "CONFLICT", "Conflict", ex.getMessage(), correlationId);
    }

    @ExceptionHandler(edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingCreationRejectedException.class)
    public ResponseEntity<ProblemDetailResponse> handleCreationRejected(
            edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingCreationRejectedException ex,
            HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        return buildResponse(HttpStatus.CONFLICT, ex.getCode(), "Booking Selection Rejected", ex.getMessage(), correlationId);
    }

    @ExceptionHandler(BookingRateLimitException.class)
    public ResponseEntity<ProblemDetailResponse> handleRateLimit(
            BookingRateLimitException ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        log.warn("Workshop booking rate limit exceeded [correlationId={}]: {}", correlationId, ex.getMessage());
        return buildResponse(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", "Rate Limit Exceeded", ex.getMessage(), correlationId);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetailResponse> handleGeneric(
            Exception ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        log.error("Unhandled booking system error [correlationId={}]: {}", correlationId, ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal Server Error",
                "An unexpected internal error occurred", correlationId);
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        if (request != null) {
            String header = request.getHeader(CORRELATION_ID_HEADER);
            if (header != null && !header.isBlank()) {
                return header.trim();
            }
        }
        return UUID.randomUUID().toString();
    }

    private ResponseEntity<ProblemDetailResponse> buildResponse(
            HttpStatus status, String code, String title, String detail, String correlationId) {
        ProblemDetailResponse response = ProblemDetailResponse.builder()
                .type(URI.create(PROBLEM_BASE_URI + code.toLowerCase().replace('_', '-')))
                .title(title)
                .status(status.value())
                .code(code)
                .detail(detail)
                .correlationId(correlationId)
                .build();
        return ResponseEntity.status(status)
                .header(CORRELATION_ID_HEADER, correlationId)
                .body(response);
    }
}
