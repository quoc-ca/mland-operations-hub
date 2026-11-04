package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service;

public class AddressLimitExceededException extends RuntimeException {
    public AddressLimitExceededException(String message) {
        super(message);
    }
}

