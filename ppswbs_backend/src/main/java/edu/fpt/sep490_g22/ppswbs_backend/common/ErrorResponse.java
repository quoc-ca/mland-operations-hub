package edu.fpt.sep490_g22.ppswbs_backend.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Safe, typed error body shared by all REST features.
 *
 * <p>{@code message} must be a safe text: no stack trace, entity, secret, token or avoidable PII.
 * {@code fieldErrors} is omitted when empty.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        String code,
        String message,
        String correlationId,
        Instant timestamp,
        List<FieldError> fieldErrors) {

    public ErrorResponse {
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public static ErrorResponse of(String code, String message, String correlationId) {
        return new ErrorResponse(code, message, correlationId, Instant.now(), List.of());
    }

    public record FieldError(String field, String message) {
    }
}
