package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api;

import edu.fpt.sep490_g22.ppswbs_backend.common.CorrelationIdFilter;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaForbiddenException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaLocationScopeException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaMappingLifecycleException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaResourceNotFoundException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaUnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@RestControllerAdvice(basePackages = "edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OtaExceptionHandler {

    @ExceptionHandler(OtaUnauthorizedException.class)
    public ResponseEntity<OtaApiError> unauthorized(OtaUnauthorizedException exception, HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Identity authentication failed", request);
    }

    @ExceptionHandler(OtaLocationScopeException.class)
    public ResponseEntity<OtaApiError> locationScope(OtaLocationScopeException exception, HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "LOCATION_SCOPE_DENIED", "Location access is not authorized", request);
    }

    @ExceptionHandler(OtaForbiddenException.class)
    public ResponseEntity<OtaApiError> forbidden(OtaForbiddenException exception, HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "FORBIDDEN", "The actor is not authorized for this action", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<OtaApiError> invalidRequest(IllegalArgumentException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "The request contains an invalid value", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<OtaApiError> invalidBody(MethodArgumentNotValidException exception,
                                                    HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "The request contains an invalid value", request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<OtaApiError> malformedRequest(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "The request contains an invalid value", request);
    }

    @ExceptionHandler({DataIntegrityViolationException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<OtaApiError> concurrentConflict(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "CONFLICT", "The OTA data changed; reload before retrying", request);
    }

    @ExceptionHandler(OtaMappingLifecycleException.class)
    public ResponseEntity<OtaApiError> conflict(OtaMappingLifecycleException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "CONFLICT", "The requested OTA action conflicts with current state", request);
    }

    @ExceptionHandler(OtaResourceNotFoundException.class)
    public ResponseEntity<OtaApiError> notFound(OtaResourceNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested OTA resource was not found", request);
    }

    private ResponseEntity<OtaApiError> response(HttpStatus status, String code, String message,
                                                  HttpServletRequest request) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(new OtaApiError(code, message, CorrelationIdFilter.current(request)));
    }
}
