package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.UUID;

@RestControllerAdvice(basePackageClasses = WorkshopSettlementProblemHandler.class)
public class WorkshopSettlementProblemHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<SettlementProblem> validation(MethodArgumentNotValidException error, HttpServletRequest request) {
        List<SettlementProblem.FieldError> fields = error.getBindingResult().getFieldErrors().stream()
                .map(field -> new SettlementProblem.FieldError(field.getField(), safe(field.getDefaultMessage())))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", fields, request);
    }

    @ExceptionHandler(WorkshopSettlementAuthorizationException.class)
    ResponseEntity<SettlementProblem> authorization(WorkshopSettlementAuthorizationException error, HttpServletRequest request) {
        return respond(HttpStatus.FORBIDDEN, error.getMessage(), List.of(), request);
    }

    @ExceptionHandler(WorkshopSettlementNotFoundException.class)
    ResponseEntity<SettlementProblem> notFound(WorkshopSettlementNotFoundException error, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", List.of(), request);
    }

    @ExceptionHandler(WorkshopSettlementConflictException.class)
    ResponseEntity<SettlementProblem> conflict(WorkshopSettlementConflictException error, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, error.getMessage(), List.of(), request);
    }

    @ExceptionHandler(WorkshopSettlementValidationException.class)
    ResponseEntity<SettlementProblem> businessValidation(WorkshopSettlementValidationException error, HttpServletRequest request) {
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, "SETTLEMENT_VALIDATION_ERROR", List.of(), request);
    }

    private static ResponseEntity<SettlementProblem> respond(HttpStatus status, String code,
                                                               List<SettlementProblem.FieldError> fields,
                                                               HttpServletRequest request) {
        String correlation = request.getHeader("X-Correlation-Id");
        if (correlation == null || correlation.isBlank()) correlation = UUID.randomUUID().toString();
        return ResponseEntity.status(status).body(new SettlementProblem(code, correlation, fields));
    }

    private static String safe(String value) { return value == null || value.isBlank() ? "invalid value" : value; }
}
