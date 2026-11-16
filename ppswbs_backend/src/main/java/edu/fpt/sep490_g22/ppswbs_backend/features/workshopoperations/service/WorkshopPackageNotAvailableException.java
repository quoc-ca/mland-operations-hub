package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

/** 003.2 FR-039: public discovery asked for a Package at a location where its pair is INACTIVE (409). */
public class WorkshopPackageNotAvailableException extends RuntimeException {
    public WorkshopPackageNotAvailableException(String message) {
        super(message);
    }
}
