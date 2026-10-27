package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.controller;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import edu.fpt.sep490_g22.ppswbs_backend.common.CorrelationIdFilter;
import edu.fpt.sep490_g22.ppswbs_backend.common.ErrorResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.LoyaltyErrorDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps every loyaltypromotion failure to the shared {@link ErrorResponse}. Scoped to this module's
 * controllers only. Responses are never cached and never expose entities, payloads, tokens, member
 * data, raw source proof or stack traces.
 */
@RestControllerAdvice(basePackageClasses = LoyaltyExceptionHandler.class)
class LoyaltyExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(LoyaltyExceptionHandler.class);

    @ExceptionHandler(LoyaltyException.class)
    ResponseEntity<ErrorResponse> handleLoyalty(LoyaltyException error, HttpServletRequest request) {
        List<ErrorResponse.FieldError> fields = error.fieldIssues().stream()
                .map(issue -> new ErrorResponse.FieldError(issue.field(), issue.message()))
                .toList();
        return respond(error.code(), fields, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleInvalidBody(MethodArgumentNotValidException error, HttpServletRequest request) {
        List<ErrorResponse.FieldError> fields = error.getBindingResult().getFieldErrors().stream()
                .map(field -> new ErrorResponse.FieldError(field.getField(), safe(field.getDefaultMessage())))
                .toList();
        return respond(LoyaltyErrorCode.INVALID_INPUT, fields, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErrorResponse> handleInvalidParameters(HandlerMethodValidationException error,
                                                          HttpServletRequest request) {
        List<ErrorResponse.FieldError> fields = error.getParameterValidationResults().stream()
                .map(result -> new ErrorResponse.FieldError(
                        result.getMethodParameter().getParameterName() == null
                                ? "parameter" : result.getMethodParameter().getParameterName(),
                        result.getResolvableErrors().stream()
                                .map(resolvable -> safe(resolvable.getDefaultMessage()))
                                .collect(Collectors.joining("; "))))
                .toList();
        return respond(LoyaltyErrorCode.INVALID_INPUT, fields, request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException error, HttpServletRequest request) {
        return respond(LoyaltyErrorCode.INVALID_INPUT,
                List.of(new ErrorResponse.FieldError(error.getHeaderName(), "This header is required.")), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException error,
                                                         HttpServletRequest request) {
        return respond(LoyaltyErrorCode.INVALID_INPUT,
                List.of(new ErrorResponse.FieldError(error.getParameterName(), "This parameter is required.")), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException error,
                                                     HttpServletRequest request) {
        return respond(LoyaltyErrorCode.INVALID_INPUT,
                List.of(new ErrorResponse.FieldError(error.getName(), "The value has the wrong format.")), request);
    }

    /** Unknown JSON fields and wrong types are INVALID_INPUT with the offending field path. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException error, HttpServletRequest request) {
        Throwable cause = error.getMostSpecificCause();
        if (cause instanceof UnrecognizedPropertyException unknown) {
            return respond(LoyaltyErrorCode.INVALID_INPUT,
                    List.of(new ErrorResponse.FieldError(path(unknown), "This field is not accepted.")), request);
        }
        if (cause instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
            return respond(LoyaltyErrorCode.INVALID_INPUT,
                    List.of(new ErrorResponse.FieldError(path(mapping), "The value has the wrong type or format.")),
                    request);
        }
        return respond(LoyaltyErrorCode.INVALID_INPUT, List.of(), request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ErrorResponse> handleStale(ObjectOptimisticLockingFailureException error, HttpServletRequest request) {
        return respond(LoyaltyErrorCode.STALE_VERSION, List.of(), request);
    }

    /** Lock timeout or deadlock: the transaction rolled back; the client retries the same intent and key. */
    @ExceptionHandler({PessimisticLockingFailureException.class, QueryTimeoutException.class})
    ResponseEntity<ErrorResponse> handleContention(RuntimeException error, HttpServletRequest request) {
        log.warn("Loyalty contention [{}]: {}", CorrelationIdFilter.current(request), error.getClass().getSimpleName());
        return respond(LoyaltyErrorCode.RETRYABLE_CONTENTION, List.of(), request);
    }

    /** Outcome not confirmed: the client checks the committed result before retrying with the same key. */
    @ExceptionHandler({DataAccessResourceFailureException.class, TransientDataAccessException.class,
            CannotCreateTransactionException.class})
    ResponseEntity<ErrorResponse> handleUnavailable(RuntimeException error, HttpServletRequest request) {
        log.warn("Loyalty dependency unavailable [{}]: {}", CorrelationIdFilter.current(request),
                error.getClass().getSimpleName());
        return respond(LoyaltyErrorCode.DEPENDENCY_UNAVAILABLE, List.of(), request);
    }

    private static ResponseEntity<ErrorResponse> respond(LoyaltyErrorCode code, List<ErrorResponse.FieldError> fields,
                                                         HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(code.name(), LoyaltyErrorDtos.defaultMessage(code),
                CorrelationIdFilter.current(request), Instant.now(), fields);
        return ResponseEntity.status(LoyaltyErrorDtos.status(code))
                .header(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    private static String path(JsonMappingException error) {
        return error.getPath().stream()
                .map(reference -> reference.getFieldName() != null
                        ? reference.getFieldName()
                        : "[" + reference.getIndex() + "]")
                .collect(Collectors.joining("."))
                .replace(".[", "[");
    }

    private static String safe(String message) {
        return message == null || message.isBlank() ? "The value is invalid." : message;
    }
}
