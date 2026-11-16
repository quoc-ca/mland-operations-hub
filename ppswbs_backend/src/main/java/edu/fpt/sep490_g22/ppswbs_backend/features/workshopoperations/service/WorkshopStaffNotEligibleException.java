package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

/** The target account of a Staff assignment is not eligible (409 STAFF_NOT_ELIGIBLE with a reason). T130. */
public class WorkshopStaffNotEligibleException extends RuntimeException {

    public enum Reason { ACCOUNT_NOT_FOUND, NOT_STAFF, NOT_ACTIVE, LOCATION_NOT_IN_SCOPE }

    private final Reason reason;

    public WorkshopStaffNotEligibleException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
