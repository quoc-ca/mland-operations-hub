package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.util.List;
import java.util.UUID;

/**
 * Result of {@link CancelTicketCommand}. {@code releasedSessionReferences} are the future sessions whose seat
 * this ticket gave back; sessions already started or finalized keep their attendance record and are not listed.
 */
public record TicketCancellationOutcome(
        OutcomeStatus status,
        UUID ticketReference,
        List<UUID> releasedSessionReferences,
        RejectionReason reason
) {
    public TicketCancellationOutcome {
        releasedSessionReferences = releasedSessionReferences == null ? List.of() : List.copyOf(releasedSessionReferences);
    }

    public static TicketCancellationOutcome accepted(UUID ticketReference, List<UUID> releasedSessionReferences) {
        return new TicketCancellationOutcome(OutcomeStatus.ACCEPTED, ticketReference, releasedSessionReferences, null);
    }

    public static TicketCancellationOutcome rejected(UUID ticketReference, RejectionReason reason) {
        return new TicketCancellationOutcome(OutcomeStatus.REJECTED, ticketReference, List.of(), reason);
    }

    public static TicketCancellationOutcome inProgress(UUID ticketReference) {
        return new TicketCancellationOutcome(OutcomeStatus.IN_PROGRESS, ticketReference, List.of(), null);
    }
}
