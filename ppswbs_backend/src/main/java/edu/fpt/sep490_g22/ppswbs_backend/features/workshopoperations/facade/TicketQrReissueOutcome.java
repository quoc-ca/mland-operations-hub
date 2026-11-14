package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.util.UUID;

/**
 * Result of {@link ReissueTicketQrCommand}. {@code qrReference} is the new active opaque QR and is only
 * present when ACCEPTED; callers must not log or audit it.
 */
public record TicketQrReissueOutcome(
        OutcomeStatus status,
        UUID ticketReference,
        Integer qrGeneration,
        String qrReference,
        RejectionReason reason
) {
    public static TicketQrReissueOutcome accepted(UUID ticketReference, int qrGeneration, String qrReference) {
        return new TicketQrReissueOutcome(OutcomeStatus.ACCEPTED, ticketReference, qrGeneration, qrReference, null);
    }

    public static TicketQrReissueOutcome rejected(UUID ticketReference, RejectionReason reason) {
        return new TicketQrReissueOutcome(OutcomeStatus.REJECTED, ticketReference, null, null, reason);
    }

    public static TicketQrReissueOutcome inProgress(UUID ticketReference) {
        return new TicketQrReissueOutcome(OutcomeStatus.IN_PROGRESS, ticketReference, null, null, null);
    }
}
