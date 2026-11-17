package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

public class StaleClaimantException extends RuntimeException {
    public StaleClaimantException(String message) {
        super(message);
    }
}
