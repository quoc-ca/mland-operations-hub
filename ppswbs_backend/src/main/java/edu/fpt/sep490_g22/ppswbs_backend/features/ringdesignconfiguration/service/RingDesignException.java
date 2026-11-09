package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.service;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.List;

@Getter
public class RingDesignException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<String> details;

    public RingDesignException(HttpStatus status, String code, String message) {
        this(status, code, message, Collections.emptyList());
    }

    public RingDesignException(HttpStatus status, String code, String message, List<String> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details != null ? details : Collections.emptyList();
    }

    public static RingDesignException badRequest(String code, String message) {
        return new RingDesignException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static RingDesignException badRequest(String code, String message, List<String> details) {
        return new RingDesignException(HttpStatus.BAD_REQUEST, code, message, details);
    }

    public static RingDesignException sessionExpired(String message) {
        return new RingDesignException(HttpStatus.UNAUTHORIZED, "DESIGN_SESSION_EXPIRED", message);
    }

    public static RingDesignException accessDenied(String message) {
        return new RingDesignException(HttpStatus.FORBIDDEN, "DESIGN_ACCESS_DENIED", message);
    }

    public static RingDesignException notFound(String message) {
        return new RingDesignException(HttpStatus.NOT_FOUND, "DESIGN_NOT_FOUND", message);
    }

    public static RingDesignException conflict(String code, String message) {
        return new RingDesignException(HttpStatus.CONFLICT, code, message);
    }

    public static RingDesignException versionConflict(String message) {
        return new RingDesignException(HttpStatus.CONFLICT, "DESIGN_VERSION_CONFLICT", message);
    }

    public static RingDesignException businessInvalid(String code, String message, List<String> details) {
        return new RingDesignException(HttpStatus.UNPROCESSABLE_ENTITY, code, message, details);
    }

    public static RingDesignException rulesUnavailable(String message) {
        return new RingDesignException(HttpStatus.SERVICE_UNAVAILABLE, "DESIGN_RULES_UNAVAILABLE", message);
    }
}

