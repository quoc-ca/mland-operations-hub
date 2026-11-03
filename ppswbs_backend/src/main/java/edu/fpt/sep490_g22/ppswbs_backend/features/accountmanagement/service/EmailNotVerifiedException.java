package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service;

public class EmailNotVerifiedException extends RuntimeException {
    public EmailNotVerifiedException(String message) {
        super(message);
    }
}
