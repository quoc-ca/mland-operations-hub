package edu.fpt.sep490_g22.ppswbs_backend.features.reporting.service;

import org.springframework.http.HttpStatus;

/** A safe, typed FE-10 failure: the HTTP status and a stable code, never source details. */
public class ReportingException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ReportingException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ReportingException invalidFilter(String message) {
        return new ReportingException(HttpStatus.BAD_REQUEST, "REPORTING_FILTER_INVALID", message);
    }

    public static ReportingException unauthenticated() {
        return new ReportingException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Authentication is required");
    }

    public static ReportingException forbidden(String message) {
        return new ReportingException(HttpStatus.FORBIDDEN, "MANAGER_REPORTING_FORBIDDEN", message);
    }

    public static ReportingException sourcesUnavailable() {
        return new ReportingException(HttpStatus.SERVICE_UNAVAILABLE, "REPORTING_SOURCES_UNAVAILABLE",
            "Dashboard data is temporarily unavailable");
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
}
