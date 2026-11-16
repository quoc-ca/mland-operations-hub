package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

/** Removing the last active Staff assignment after check-in opens needs confirmation (409). T115. */
public class WorkshopLastStaffAssignmentException extends RuntimeException {
    public WorkshopLastStaffAssignmentException(String message) {
        super(message);
    }
}
