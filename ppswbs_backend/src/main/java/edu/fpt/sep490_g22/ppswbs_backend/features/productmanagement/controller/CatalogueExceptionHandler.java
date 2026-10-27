package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.controller;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import edu.fpt.sep490_g22.ppswbs_backend.common.CorrelationIdFilter;
import edu.fpt.sep490_g22.ppswbs_backend.common.ErrorResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
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
 * Maps every productmanagement failure to the shared {@link ErrorResponse} with the codes of the
 * FE-02 error contract. Scoped to this module's controllers only, so other features keep their own
 * behavior. Error responses are never cached and never expose entities, payloads or stack traces.
 */
@RestControllerAdvice(basePackageClasses = CatalogueExceptionHandler.class)
class CatalogueExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CatalogueExceptionHandler.class);

    @ExceptionHandler(CatalogueException.class)
    ResponseEntity<ErrorResponse> handleCatalogue(CatalogueException error, HttpServletRequest request) {
        return respond(error.code(), error.fieldErrors(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleInvalidBody(MethodArgumentNotValidException error, HttpServletRequest request) {
        List<ErrorResponse.FieldError> fields = error.getBindingResult().getFieldErrors().stream()
                .map(field -> new ErrorResponse.FieldError(field.getField(), safe(field.getDefaultMessage())))
                .toList();
        return respond(CatalogueErrorCode.VALIDATION_ERROR, fields, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErrorResponse> handleInvalidParameters(HandlerMethodValidationException error,
                                                          HttpServletRequest request) {
        List<ErrorResponse.FieldError> fields = error.getParameterValidationResults().stream()
                .map(result -> new ErrorResponse.FieldError(
                        parameterName(result.getMethodParameter().getParameterName()),
                        result.getResolvableErrors().stream()
                                .map(resolvable -> safe(resolvable.getDefaultMessage()))
                                .collect(Collectors.joining("; "))))
                .toList();
        return respond(CatalogueErrorCode.VALIDATION_ERROR, fields, request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException error, HttpServletRequest request) {
        return respond(CatalogueErrorCode.VALIDATION_ERROR,
                List.of(new ErrorResponse.FieldError(error.getHeaderName(), "This header is required.")), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException error,
                                                         HttpServletRequest request) {
        return respond(CatalogueErrorCode.VALIDATION_ERROR,
                List.of(new ErrorResponse.FieldError(error.getParameterName(), "This parameter is required.")), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException error,
                                                     HttpServletRequest request) {
        return respond(CatalogueErrorCode.VALIDATION_ERROR,
                List.of(new ErrorResponse.FieldError(error.getName(), "The value has the wrong format.")), request);
    }

    /** Unknown JSON fields are rejected as UNKNOWN_FIELD; malformed or wrong-type JSON as VALIDATION_ERROR. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException error, HttpServletRequest request) {
        Throwable cause = error.getMostSpecificCause();
        if (cause instanceof UnrecognizedPropertyException unknown) {
            return respond(CatalogueErrorCode.UNKNOWN_FIELD,
                    List.of(new ErrorResponse.FieldError(path(unknown), "This field is not accepted.")), request);
        }
        if (cause instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
            return respond(CatalogueErrorCode.VALIDATION_ERROR,
                    List.of(new ErrorResponse.FieldError(path(mapping), "The value has the wrong type or format.")),
                    request);
        }
        return respond(CatalogueErrorCode.VALIDATION_ERROR, List.of(), request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ErrorResponse> handleStale(ObjectOptimisticLockingFailureException error, HttpServletRequest request) {
        return respond(CatalogueErrorCode.STALE_VERSION, List.of(), request);
    }

    /** Lock timeout/deadlock: transaction rolled back, client may retry with the same command key. */
    @ExceptionHandler({PessimisticLockingFailureException.class, QueryTimeoutException.class})
    ResponseEntity<ErrorResponse> handleBusy(RuntimeException error, HttpServletRequest request) {
        log.warn("Catalogue busy [{}]: {}", CorrelationIdFilter.current(request), error.getClass().getSimpleName());
        return respond(CatalogueErrorCode.CATALOGUE_BUSY, List.of(), request);
    }

    /** Outcome not confirmed: client must check the command receipt before retrying with the same key. */
    @ExceptionHandler({DataAccessResourceFailureException.class, TransientDataAccessException.class,
            CannotCreateTransactionException.class})
    ResponseEntity<ErrorResponse> handleSaveUnavailable(RuntimeException error, HttpServletRequest request) {
        log.warn("Catalogue save unavailable [{}]: {}", CorrelationIdFilter.current(request),
                error.getClass().getSimpleName());
        return respond(CatalogueErrorCode.CATALOGUE_SAVE_UNAVAILABLE, List.of(), request);
    }

    private static ResponseEntity<ErrorResponse> respond(CatalogueErrorCode code, List<ErrorResponse.FieldError> fields,
                                                         HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(code.name(), code.defaultMessage(),
                CorrelationIdFilter.current(request), Instant.now(), fields);
        return ResponseEntity.status(code.status())
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

    private static String parameterName(String name) {
        return name == null ? "parameter" : name;
    }

    private static String safe(String message) {
        return message == null || message.isBlank() ? "The value is invalid." : message;
    }
}
