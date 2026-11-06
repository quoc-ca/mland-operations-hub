package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service;

public class TokenRevokedException extends RuntimeException {
    public TokenRevokedException(String message) {
        super(message);
    }
}
