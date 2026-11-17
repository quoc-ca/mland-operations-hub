package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

public record TicketRedeliveryOutcome(
    BookingOutcomeStatus status,
    BookingRejectionReason reason
) {
    public static TicketRedeliveryOutcome accepted() {
        return new TicketRedeliveryOutcome(BookingOutcomeStatus.ACCEPTED, null);
    }

    public static TicketRedeliveryOutcome rejected(BookingRejectionReason reason) {
        return new TicketRedeliveryOutcome(BookingOutcomeStatus.REJECTED, reason);
    }
}
