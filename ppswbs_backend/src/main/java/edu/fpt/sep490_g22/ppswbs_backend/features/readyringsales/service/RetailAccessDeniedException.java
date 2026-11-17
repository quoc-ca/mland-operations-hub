package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

public class RetailAccessDeniedException extends RuntimeException {
    public RetailAccessDeniedException(String message) {
        super(message);
    }
}
