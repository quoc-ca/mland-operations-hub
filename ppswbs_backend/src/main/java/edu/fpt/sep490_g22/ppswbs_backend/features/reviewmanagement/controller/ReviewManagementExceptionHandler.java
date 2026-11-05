package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.ReviewErrorResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.ReviewManagementException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
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
@RestControllerAdvice(basePackages = "edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReviewManagementExceptionHandler {

    @ExceptionHandler(ReviewManagementException.class)
    public ResponseEntity<ReviewErrorResponse> handleReviewException(
            ReviewManagementException ex, HttpServletRequest request) {
        log.warn("ReviewManagementException: code={}, message={}", ex.getCode(), ex.getMessage());
        ReviewErrorResponse error = ReviewErrorResponse.builder()
                .code(ex.getCode())
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .correlationId(UUID.randomUUID().toString())
                .build();
        return new ResponseEntity<>(error, ex.getStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ReviewErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> details = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            details.add(fieldError.getField() + ": " + fieldError.getDefaultMessage());
        }

        ReviewErrorResponse error = ReviewErrorResponse.builder()
                .code("REVIEW_INPUT_INVALID")
                .message("Dữ liệu gửi lên không hợp lệ.")
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .correlationId(UUID.randomUUID().toString())
                .details(details)
                .build();

        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ReviewErrorResponse> handleOptimisticLockException(
            OptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("OptimisticLockingFailureException in review management", ex);
        ReviewErrorResponse error = ReviewErrorResponse.builder()
                .code("REVIEW_STATE_CONFLICT")
                .message("Xung đột phiên bản dữ liệu (Optimistic Lock). Vui lòng làm mới trang.")
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .correlationId(UUID.randomUUID().toString())
                .build();

        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }
}

