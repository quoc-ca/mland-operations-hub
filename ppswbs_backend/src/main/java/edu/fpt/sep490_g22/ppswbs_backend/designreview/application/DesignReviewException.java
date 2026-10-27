package edu.fpt.sep490_g22.ppswbs_backend.designreview.application;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class DesignReviewException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final Long retryAfterSeconds;

    public DesignReviewException(String code, String message, HttpStatus status) {
        this(code, message, status, null);
    }

    public DesignReviewException(String code, String message, HttpStatus status, Long retryAfterSeconds) {
        super(message);
        this.code = code;
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static DesignReviewException rateLimited(String code, String message, long retryAfterSeconds) {
        return new DesignReviewException(code, message, HttpStatus.TOO_MANY_REQUESTS, retryAfterSeconds);
    }

    public static DesignReviewException badRequest(String code, String message) {
        return new DesignReviewException(code, message, HttpStatus.BAD_REQUEST);
    }

    public static DesignReviewException unprocessable(String code, String message) {
        return new DesignReviewException(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public static DesignReviewException forbidden(String code, String message) {
        return new DesignReviewException(code, message, HttpStatus.FORBIDDEN);
    }

    public static DesignReviewException unauthorized(String code, String message) {
        return new DesignReviewException(code, message, HttpStatus.UNAUTHORIZED);
    }

    public static DesignReviewException notFound(String code, String message) {
        return new DesignReviewException(code, message, HttpStatus.NOT_FOUND);
    }

    public static DesignReviewException conflict(String code, String message) {
        return new DesignReviewException(code, message, HttpStatus.CONFLICT);
    }

    public static DesignReviewException serviceUnavailable(String code, String message) {
        return new DesignReviewException(code, message, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
