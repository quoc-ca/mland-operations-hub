package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
