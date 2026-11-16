package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

/** A dependency (the Account facade) failed, so the command fails closed with 503 DEPENDENCY_UNAVAILABLE. T128. */
public class WorkshopDependencyUnavailableException extends RuntimeException {
    public WorkshopDependencyUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
