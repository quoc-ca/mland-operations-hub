package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response.ErrorResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestControllerAdvice(basePackages = "edu.fpt.sep490_g22.ppswbs_backend")
@Slf4j
public class AccountExceptionHandler {

    @ExceptionHandler(AccountSuspendedException.class)
    public ResponseEntity<ErrorResponse> handleSuspended(AccountSuspendedException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED", "Account has been suspended", request, null);
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<ErrorResponse> handleEmailNotVerified(EmailNotVerifiedException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED", "Email is not verified", request, null);
    }

    @ExceptionHandler(RoleAssignmentForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(RoleAssignmentForbiddenException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage(), request, null);
    }

    @ExceptionHandler({InvalidTokenException.class, TokenRevokedException.class, TokenExpiredException.class})
    public ResponseEntity<ErrorResponse> handleUnauthorized(RuntimeException ex, HttpServletRequest request) {
        String code = "UNAUTHORIZED";
        if (ex instanceof TokenExpiredException) code = "TOKEN_EXPIRED";
        else if (ex instanceof TokenRevokedException) code = "TOKEN_REVOKED";
        else if (ex instanceof InvalidTokenException) code = "INVALID_TOKEN";
        return buildResponse(HttpStatus.UNAUTHORIZED, code, ex.getMessage(), request, null);
    }

    @ExceptionHandler(OptimisticLockConflictException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockConflictException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, "OPTIMISTIC_LOCK_CONFLICT", ex.getMessage(), request, null);
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyConflict(IdempotencyKeyConflictException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT", ex.getMessage(), request, null);
    }

    @ExceptionHandler(InvitationCooldownActiveException.class)
    public ResponseEntity<ErrorResponse> handleCooldown(InvitationCooldownActiveException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, "INVITATION_COOLDOWN_ACTIVE", ex.getMessage(), request, null);
    }

    @ExceptionHandler(RevocationPendingException.class)
    public ResponseEntity<ErrorResponse> handleRevocationPending(RevocationPendingException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, "REVOCATION_PENDING", ex.getMessage(), request, null);
    }

    @ExceptionHandler(LocationValidationException.class)
    public ResponseEntity<ErrorResponse> handleLocationValidation(LocationValidationException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, "LOCATION_NOT_ACTIVE", ex.getMessage(), request, null);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", ex.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldErrorDetail> fieldErrors = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.add(new ErrorResponse.FieldErrorDetail(fe.getField(), fe.getDefaultMessage()));
        }
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation failed", request, fieldErrors);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage(), request, null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleConflict(IllegalStateException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, "INVALID_STATE", ex.getMessage(), request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        log.error("Unhandled error correlationId={}: {}", correlationId, ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", request, null);
    }

    private ResponseEntity<ErrorResponse> buildResponse(
        HttpStatus status,
        String code,
        String message,
        HttpServletRequest request,
        List<ErrorResponse.FieldErrorDetail> fieldErrors
    ) {
        String correlationId = resolveCorrelationId(request);
        ErrorResponse body = ErrorResponse.builder()
            .code(code)
            .message(message)
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .fieldErrors(fieldErrors)
            .build();
        return ResponseEntity.status(status).body(body);
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String header = request.getHeader("X-Correlation-Id");
        return (header != null && !header.isBlank()) ? header.trim() : UUID.randomUUID().toString();
    }
}
