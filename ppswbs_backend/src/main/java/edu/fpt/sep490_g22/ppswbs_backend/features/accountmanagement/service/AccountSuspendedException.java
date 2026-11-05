package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service;

public class AccountSuspendedException extends RuntimeException {
    public AccountSuspendedException(String message) {
        super(message);
    }
}
