package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

public class WorkshopUnauthorizedException extends RuntimeException {
    public WorkshopUnauthorizedException(String message) {
        super(message);
    }

    public WorkshopUnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}
