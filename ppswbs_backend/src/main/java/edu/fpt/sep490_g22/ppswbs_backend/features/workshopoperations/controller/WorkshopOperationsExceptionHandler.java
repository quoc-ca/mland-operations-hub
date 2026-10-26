package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.ProblemDetailResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopCapacityConflictException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopDuplicateSessionException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopForbiddenException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopLifecycleException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopLocationScopeException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopNotFoundException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopSlotConflictException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopTicketConflictException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopUnauthorizedException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopValidationException;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Global exception handler for Workshop Operations module.
 * Maps operational exceptions into typed RFC 7807 Problem Details
 * with correlation tracking, preventing entity leakage and PII disclosure.
 */
@Slf4j
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = {
    "edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations",
    "edu.fpt.sep490_g22.ppswbs_backend.workshopoperations"
})
public class WorkshopOperationsExceptionHandler {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String PROBLEM_BASE_URI = "urn:problem-type:workshop-operations:";

    @ExceptionHandler(WorkshopUnauthorizedException.class)
    public ResponseEntity<ProblemDetailResponse> handleUnauthorized(
            WorkshopUnauthorizedException ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        log.warn("Workshop auth failure [correlationId={}]: {}", correlationId, ex.getMessage());
        return buildResponse(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized", ex.getMessage(), correlationId);
    }

    @ExceptionHandler(WorkshopLocationScopeException.class)
    public ResponseEntity<ProblemDetailResponse> handleLocationScope(
            WorkshopLocationScopeException ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        log.warn("Workshop location scope denial [correlationId={}]: {}", correlationId, ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, "LOCATION_SCOPE_DENIED", "Location Scope Denied", ex.getMessage(), correlationId);
    }

    @ExceptionHandler(WorkshopForbiddenException.class)
    public ResponseEntity<ProblemDetailResponse> handleForbidden(
            WorkshopForbiddenException ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        log.warn("Workshop forbidden access [correlationId={}]: {}", correlationId, ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, "FORBIDDEN", "Forbidden", ex.getMessage(), correlationId);
    }

    @ExceptionHandler(WorkshopNotFoundException.class)
    public ResponseEntity<ProblemDetailResponse> handleNotFound(
            WorkshopNotFoundException ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        return buildResponse(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource Not Found", ex.getMessage(), correlationId);
    }

    @ExceptionHandler(WorkshopValidationException.class)
    public ResponseEntity<ProblemDetailResponse> handleValidation(
            WorkshopValidationException ex, HttpServletRequest request) {
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

    @ExceptionHandler(WorkshopLifecycleException.class)
    public ResponseEntity<ProblemDetailResponse> handleLifecycle(
            WorkshopLifecycleException ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        log.warn("Workshop lifecycle rejection [correlationId={}]: {}", correlationId, ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "INVALID_STATE", "Invalid Lifecycle State", ex.getMessage(), correlationId);
    }

    @ExceptionHandler(WorkshopCapacityConflictException.class)
    public ResponseEntity<ProblemDetailResponse> handleCapacityConflict(
            WorkshopCapacityConflictException ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        return buildResponse(HttpStatus.CONFLICT, "INSUFFICIENT_CAPACITY", "Insufficient Capacity", ex.getMessage(), correlationId);
    }

    @ExceptionHandler(WorkshopTicketConflictException.class)
    public ResponseEntity<ProblemDetailResponse> handleTicketConflict(
            WorkshopTicketConflictException ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        return buildResponse(HttpStatus.CONFLICT, "TICKET_CONFLICT", "Ticket Conflict", ex.getMessage(), correlationId);
    }

    @ExceptionHandler(WorkshopSlotConflictException.class)
    public ResponseEntity<ProblemDetailResponse> handleSlotConflict(
            WorkshopSlotConflictException ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        log.warn("Workshop slot conflict [correlationId={}]: {}", correlationId, ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "SLOT_OVERLAP_CONFLICT", "Slot Overlap Conflict", ex.getMessage(), correlationId);
    }

    @ExceptionHandler(WorkshopDuplicateSessionException.class)
    public ResponseEntity<ProblemDetailResponse> handleDuplicateSession(
            WorkshopDuplicateSessionException ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        log.warn("Workshop duplicate session [correlationId={}]: {}", correlationId, ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "DUPLICATE_SESSION", "Duplicate Session Conflict", ex.getMessage(), correlationId);
    }

    @ExceptionHandler({OptimisticLockException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ProblemDetailResponse> handleOptimisticLock(
            Exception ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        log.warn("Optimistic lock conflict [correlationId={}]", correlationId);
        return buildResponse(HttpStatus.CONFLICT, "OPTIMISTIC_LOCK_CONFLICT", "Conflict", "The record was updated by another transaction. Please retry.", correlationId);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetailResponse> handleIllegalState(
            IllegalStateException ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        return buildResponse(HttpStatus.CONFLICT, "INVALID_STATE", "Invalid State", ex.getMessage(), correlationId);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetailResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Bad Request", ex.getMessage(), correlationId);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetailResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        log.error("Unhandled error in workshop operations [correlationId={}]: {}", correlationId, ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal Server Error",
                "An unexpected server error occurred. Correlation ID: " + correlationId, correlationId);
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        if (request != null) {
            String headerVal = request.getHeader(CORRELATION_ID_HEADER);
            if (headerVal != null && !headerVal.isBlank()) {
                return headerVal;
            }
        }
        return UUID.randomUUID().toString();
    }

    private ResponseEntity<ProblemDetailResponse> buildResponse(
            HttpStatus status, String code, String title, String detail, String correlationId) {
        ProblemDetailResponse body = ProblemDetailResponse.builder()
                .type(URI.create(PROBLEM_BASE_URI + code.toLowerCase().replace('_', '-')))
                .title(title)
                .status(status.value())
                .code(code)
                .detail(detail)
                .correlationId(correlationId)
                .build();
        return ResponseEntity.status(status).body(body);
    }
}
