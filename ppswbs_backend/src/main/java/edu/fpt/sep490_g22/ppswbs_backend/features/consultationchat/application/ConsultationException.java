package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.application;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ConsultationException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Integer retryAfterSeconds;

    public ConsultationException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryAfterSeconds = null;
    }

    public ConsultationException(HttpStatus status, String code, String message, Integer retryAfterSeconds) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static ConsultationException badRequest(String code, String message) {
        return new ConsultationException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ConsultationException unauthorized(String code, String message) {
        return new ConsultationException(HttpStatus.UNAUTHORIZED, code, message);
    }

    public static ConsultationException forbidden(String code, String message) {
        return new ConsultationException(HttpStatus.FORBIDDEN, code, message);
    }

    public static ConsultationException notFound(String code, String message) {
        return new ConsultationException(HttpStatus.NOT_FOUND, code, message);
    }

    public static ConsultationException conflict(String code, String message) {
        return new ConsultationException(HttpStatus.CONFLICT, code, message);
    }

    public static ConsultationException rateLimited(String code, String message, int retryAfterSeconds) {
        return new ConsultationException(HttpStatus.TOO_MANY_REQUESTS, code, message, retryAfterSeconds);
    }

    public static ConsultationException unavailable(String code, String message) {
        return new ConsultationException(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }
}
