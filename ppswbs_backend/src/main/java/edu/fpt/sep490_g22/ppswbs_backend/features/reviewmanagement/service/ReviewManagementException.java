package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ReviewManagementException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public ReviewManagementException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static ReviewManagementException badRequest(String code, String message) {
        return new ReviewManagementException(code, message, HttpStatus.BAD_REQUEST);
    }

    public static ReviewManagementException unauthorized(String code, String message) {
        return new ReviewManagementException(code, message, HttpStatus.UNAUTHORIZED);
    }

    public static ReviewManagementException forbidden(String code, String message) {
        return new ReviewManagementException(code, message, HttpStatus.FORBIDDEN);
    }

    public static ReviewManagementException notFound(String code, String message) {
        return new ReviewManagementException(code, message, HttpStatus.NOT_FOUND);
    }

    public static ReviewManagementException conflict(String code, String message) {
        return new ReviewManagementException(code, message, HttpStatus.CONFLICT);
    }

    public static ReviewManagementException unprocessable(String code, String message) {
        return new ReviewManagementException(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}

