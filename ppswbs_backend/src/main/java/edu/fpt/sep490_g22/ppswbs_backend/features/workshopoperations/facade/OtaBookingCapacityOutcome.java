package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.util.List;
import java.util.UUID;

public record OtaBookingCapacityOutcome(
        OutcomeStatus status,
        List<UUID> ticketReferences,
        RejectionReason reason) {

    public OtaBookingCapacityOutcome {
        ticketReferences = ticketReferences == null ? List.of() : List.copyOf(ticketReferences);
    }

    public static OtaBookingCapacityOutcome accepted(List<UUID> ticketReferences) {
        return new OtaBookingCapacityOutcome(OutcomeStatus.ACCEPTED, ticketReferences, null);
    }

    public static OtaBookingCapacityOutcome rejected(RejectionReason reason) {
        return new OtaBookingCapacityOutcome(OutcomeStatus.REJECTED, List.of(), reason);
    }

    public static OtaBookingCapacityOutcome inProgress() {
        return new OtaBookingCapacityOutcome(OutcomeStatus.IN_PROGRESS, List.of(), null);
    }
}
