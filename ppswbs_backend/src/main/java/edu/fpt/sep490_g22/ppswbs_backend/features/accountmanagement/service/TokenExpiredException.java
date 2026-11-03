package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service;

public class TokenExpiredException extends RuntimeException {
    public TokenExpiredException(String message) {
        super(message);
    }
}
