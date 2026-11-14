package edu.fpt.sep490_g22.ppswbs_backend.features.reporting.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.service.ReportingException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.UUID;

/** Safe FE-10 errors: a code, a short message and a correlation ID; no SQL, stack trace or source payload. */
@RestControllerAdvice(basePackages = "edu.fpt.sep490_g22.ppswbs_backend.features.reporting")
public class ReportingExceptionHandler {
    @ExceptionHandler(ReportingException.class)
    ResponseEntity<Map<String, Object>> reporting(ReportingException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.status())
            .body(body(exception.code(), exception.getMessage(), request));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Map<String, Object>> badParameter(MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest()
            .body(body("REPORTING_FILTER_INVALID", "Invalid value for " + exception.getName(), request));
    }

    private static Map<String, Object> body(String code, String message, HttpServletRequest request) {
        String correlationId = request.getHeader("X-Correlation-Id");
        return Map.of("code", code, "message", message,
            "correlationId", correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId);
    }
}
