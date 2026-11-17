package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service;

public class RoleAssignmentForbiddenException extends RuntimeException {
    public RoleAssignmentForbiddenException(String message) {
        super(message);
    }
}
