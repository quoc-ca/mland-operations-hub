package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.controller.dto.NotificationErrorResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.NotificationManagementException;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestControllerAdvice(basePackages = "edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.controller")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class NotificationManagementExceptionHandler {

    @ExceptionHandler(NotificationManagementException.class)
    public ResponseEntity<NotificationErrorResponse> handleNotificationException(
            NotificationManagementException ex, HttpServletRequest request) {
        log.warn("NotificationManagementException: code={}, message={}", ex.getCode(), ex.getMessage());
        NotificationErrorResponse error = NotificationErrorResponse.builder()
                .code(ex.getCode())
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .correlationId(UUID.randomUUID().toString())
                .build();
        return new ResponseEntity<>(error, ex.getStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<NotificationErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> details = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            details.add(fieldError.getField() + ": " + fieldError.getDefaultMessage());
        }

        NotificationErrorResponse error = NotificationErrorResponse.builder()
                .code("NOTIFICATION_INPUT_INVALID")
                .message("Dữ liệu gửi lên không hợp lệ.")
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .correlationId(UUID.randomUUID().toString())
                .details(details)
                .build();
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }
}

