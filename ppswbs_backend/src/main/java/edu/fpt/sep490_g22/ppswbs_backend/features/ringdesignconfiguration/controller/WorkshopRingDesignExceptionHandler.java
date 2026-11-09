package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto.RingDesignErrorResponseDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.service.RingDesignException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestControllerAdvice(basePackages = "edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration")
@Slf4j
public class WorkshopRingDesignExceptionHandler {

    @ExceptionHandler(RingDesignException.class)
    public ResponseEntity<RingDesignErrorResponseDto> handleRingDesignException(
            RingDesignException ex,
            HttpServletRequest request
    ) {
        String correlationId = resolveCorrelationId(request);
        log.warn("RingDesignException: status={}, code={}, message={}", ex.getStatus(), ex.getCode(), ex.getMessage());

        RingDesignErrorResponseDto errorDto = RingDesignErrorResponseDto.builder()
                .code(ex.getCode())
                .message(ex.getMessage())
                .correlationId(correlationId)
                .details(ex.getDetails())
                .build();

        return ResponseEntity.status(ex.getStatus()).body(errorDto);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RingDesignErrorResponseDto> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        String correlationId = resolveCorrelationId(request);
        List<String> details = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            details.add(fe.getField() + ": " + fe.getDefaultMessage());
        }

        RingDesignErrorResponseDto errorDto = RingDesignErrorResponseDto.builder()
                .code("DESIGN_INPUT_INVALID")
                .message("Dữ liệu đầu vào không hợp lệ.")
                .correlationId(correlationId)
                .details(details)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorDto);
    }

    @ExceptionHandler({MissingRequestHeaderException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<RingDesignErrorResponseDto> handleMissingParamHeaderException(
            Exception ex,
            HttpServletRequest request
    ) {
        String correlationId = resolveCorrelationId(request);
        RingDesignErrorResponseDto errorDto = RingDesignErrorResponseDto.builder()
                .code("DESIGN_INPUT_INVALID")
                .message(ex.getMessage())
                .correlationId(correlationId)
                .details(List.of(ex.getMessage()))
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorDto);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RingDesignErrorResponseDto> handleGeneralException(
            Exception ex,
            HttpServletRequest request
    ) {
        String correlationId = resolveCorrelationId(request);
        log.error("Unhandled exception in ring design configuration: correlationId={}", correlationId, ex);

        RingDesignErrorResponseDto errorDto = RingDesignErrorResponseDto.builder()
                .code("INTERNAL_SERVER_ERROR")
                .message("Đã xảy ra lỗi nội bộ máy chủ.")
                .correlationId(correlationId)
                .details(List.of(ex.getClass().getSimpleName() + ": " + ex.getMessage()))
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorDto);
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String correlationId = request.getHeader("X-Correlation-Id");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        return correlationId;
    }
}

