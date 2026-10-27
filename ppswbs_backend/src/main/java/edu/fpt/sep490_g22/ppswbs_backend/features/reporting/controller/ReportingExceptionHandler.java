package edu.fpt.sep490_g22.ppswbs_backend.features.reporting.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.service.ReportingConflictException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice(basePackages = "edu.fpt.sep490_g22.ppswbs_backend.features.reporting")
public class ReportingExceptionHandler {
    @ExceptionHandler(ReportingConflictException.class)
    ResponseEntity<Map<String, Object>> conflict(ReportingConflictException exception, HttpServletRequest request) {
        String correlationId = request.getHeader("X-Correlation-Id");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("code", "MANAGER_REPORTING_FORBIDDEN", "message", exception.getMessage(), "correlationId", correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId));
    }
}
