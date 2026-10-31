package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.RetailErrorResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailAccessDeniedException;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailConflictException;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailIntegrationUnavailableException;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailNotFoundException;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailUnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

@RestControllerAdvice(basePackages = "edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales")
public class RetailExceptionHandler {

    @ExceptionHandler(RetailUnauthorizedException.class)
    ResponseEntity<RetailErrorResponse> unauthorized(RetailUnauthorizedException exception, HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Retail authentication is required", request);
    }

    @ExceptionHandler(RetailAccessDeniedException.class)
    ResponseEntity<RetailErrorResponse> forbidden(RetailAccessDeniedException exception, HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "FORBIDDEN", "The account cannot perform this retail action", request);
    }

    @ExceptionHandler(RetailNotFoundException.class)
    ResponseEntity<RetailErrorResponse> notFound(RetailNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested retail resource was not found", request);
    }

    @ExceptionHandler(RetailConflictException.class)
    ResponseEntity<RetailErrorResponse> conflict(RetailConflictException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "RETAIL_CONFLICT", exception.getMessage(), request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<RetailErrorResponse> staleWrite(OptimisticLockingFailureException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "RETAIL_CONFLICT", "The cart changed; refresh before retrying", request);
    }

    @ExceptionHandler(RetailIntegrationUnavailableException.class)
    ResponseEntity<RetailErrorResponse> unavailable(RetailIntegrationUnavailableException exception, HttpServletRequest request) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "RETAIL_INTEGRATION_UNAVAILABLE", "Retail integration is unavailable", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<RetailErrorResponse> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        FieldError error = exception.getBindingResult().getFieldError();
        String message = error == null ? "Request validation failed" : error.getField() + " is invalid";
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request);
    }

    private ResponseEntity<RetailErrorResponse> response(
        HttpStatus status,
        String code,
        String message,
        HttpServletRequest request
    ) {
        String correlationId = request.getHeader("X-Correlation-Id");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        return ResponseEntity.status(status).body(new RetailErrorResponse(code, message, correlationId));
    }
}
