package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.web;

import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.application.ConsultationException;
import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.application.dto.ErrorResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

@RestControllerAdvice(basePackages = "edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.web")
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class ConsultationExceptionHandler {

    @ExceptionHandler(ConsultationException.class)
    public ResponseEntity<ErrorResponseDto> handleConsultationException(
            ConsultationException ex, HttpServletRequest request
    ) {
        String correlationId = resolveCorrelationId(request);
        log.warn("Consultation business error: status={}, code={}, message={}", ex.getStatus(), ex.getCode(), ex.getMessage());

        HttpHeaders headers = new HttpHeaders();
        if (ex.getRetryAfterSeconds() != null) {
            headers.set("Retry-After", String.valueOf(ex.getRetryAfterSeconds()));
        }

        ErrorResponseDto body = ErrorResponseDto.builder()
                .code(ex.getCode())
                .message(ex.getMessage())
                .correlationId(correlationId)
                .retryAfterSeconds(ex.getRetryAfterSeconds())
                .build();

        return new ResponseEntity<>(body, headers, ex.getStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request
    ) {
        String correlationId = resolveCorrelationId(request);
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Dữ liệu đầu vào không hợp lệ.");

        log.warn("Validation error on consultation request: {}", message);

        ErrorResponseDto body = ErrorResponseDto.builder()
                .code("CHAT_CONTEXT_INVALID")
                .message(message)
                .correlationId(correlationId)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponseDto> handleMissingHeader(
            MissingRequestHeaderException ex, HttpServletRequest request
    ) {
        String correlationId = resolveCorrelationId(request);
        String headerName = ex.getHeaderName();
        String code = "CHAT_CONTEXT_INVALID";
        if ("If-Match-Version".equalsIgnoreCase(headerName)) {
            code = "CHAT_STATE_CONFLICT";
        }

        log.warn("Missing header on consultation request: {}", headerName);

        ErrorResponseDto body = ErrorResponseDto.builder()
                .code(code)
                .message("Thiếu header bắt buộc: " + headerName)
                .correlationId(correlationId)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String cid = request.getHeader("X-Correlation-Id");
        return cid != null && !cid.isBlank() ? cid.trim() : UUID.randomUUID().toString();
    }
}
