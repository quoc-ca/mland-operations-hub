package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service;

public class InvalidPhoneNumberException extends RuntimeException {
    public InvalidPhoneNumberException(String message) {
        super(message);
    }
}

