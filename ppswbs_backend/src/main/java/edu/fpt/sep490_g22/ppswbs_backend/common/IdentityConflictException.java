package edu.fpt.sep490_g22.ppswbs_backend.common;

import org.springframework.http.HttpStatus;

public class IdentityConflictException extends ApiException {
    public IdentityConflictException(String message) {
        super("IDENTITY_CONFLICT", message, HttpStatus.CONFLICT);
    }
}
