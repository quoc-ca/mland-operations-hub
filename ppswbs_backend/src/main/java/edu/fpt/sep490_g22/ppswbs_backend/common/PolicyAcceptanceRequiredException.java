package edu.fpt.sep490_g22.ppswbs_backend.common;

import org.springframework.http.HttpStatus;

public class PolicyAcceptanceRequiredException extends ApiException {
    public PolicyAcceptanceRequiredException(String message) {
        super("POLICY_ACCEPTANCE_REQUIRED", message, HttpStatus.FORBIDDEN);
    }
}
