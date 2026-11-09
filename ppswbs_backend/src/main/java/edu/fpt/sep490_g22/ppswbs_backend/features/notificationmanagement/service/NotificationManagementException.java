package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class NotificationManagementException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public NotificationManagementException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static NotificationManagementException badRequest(String code, String message) {
        return new NotificationManagementException(code, message, HttpStatus.BAD_REQUEST);
    }

    public static NotificationManagementException unauthorized(String code, String message) {
        return new NotificationManagementException(code, message, HttpStatus.UNAUTHORIZED);
    }

    public static NotificationManagementException forbidden(String code, String message) {
        return new NotificationManagementException(code, message, HttpStatus.FORBIDDEN);
    }

    public static NotificationManagementException notFound(String code, String message) {
        return new NotificationManagementException(code, message, HttpStatus.NOT_FOUND);
    }

    public static NotificationManagementException conflict(String code, String message) {
        return new NotificationManagementException(code, message, HttpStatus.CONFLICT);
    }

    public static NotificationManagementException unprocessable(String code, String message) {
        return new NotificationManagementException(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}

