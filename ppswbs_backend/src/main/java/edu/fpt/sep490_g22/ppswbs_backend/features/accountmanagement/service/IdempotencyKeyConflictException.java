package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service;

public class IdempotencyKeyConflictException extends RuntimeException {
    public IdempotencyKeyConflictException(String message) {
        super(message);
    }
}
