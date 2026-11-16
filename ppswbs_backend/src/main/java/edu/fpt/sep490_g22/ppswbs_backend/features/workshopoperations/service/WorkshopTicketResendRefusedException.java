package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

/** T139: a ticket resend refused with 409 INVALID_STATE and a closed {@code reason}; never 400/404. */
public class WorkshopTicketResendRefusedException extends RuntimeException {
    public enum Reason { NO_ELIGIBLE_TICKET, REDELIVERY_REJECTED_BY_BOOKING }

    private final Reason reason;

    public WorkshopTicketResendRefusedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() { return reason; }
}
