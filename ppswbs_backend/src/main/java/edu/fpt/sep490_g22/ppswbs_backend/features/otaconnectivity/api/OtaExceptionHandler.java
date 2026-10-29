package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api;

import edu.fpt.sep490_g22.ppswbs_backend.common.CorrelationIdFilter;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaForbiddenException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaLocationScopeException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaUnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    private ResponseEntity<OtaApiError> response(HttpStatus status, String code, String message,
                                                  HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new OtaApiError(code, message, CorrelationIdFilter.current(request)));
    }
}
