package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service;

public class OptimisticLockConflictException extends RuntimeException {
    public OptimisticLockConflictException(String message) {
        super(message);
    }
}
