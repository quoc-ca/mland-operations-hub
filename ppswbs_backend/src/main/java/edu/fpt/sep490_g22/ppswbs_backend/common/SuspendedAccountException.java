package edu.fpt.sep490_g22.ppswbs_backend.common;

import org.springframework.http.HttpStatus;

public class SuspendedAccountException extends ApiException {
    public SuspendedAccountException(String message) {
        super("SUSPENDED_ACCOUNT", message, HttpStatus.FORBIDDEN);
    }
}
