package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.util.List;
import java.util.UUID;

/** Result of {@link RevokeOtaTicketsCommand}; on acceptance, the revoked ticket references in command order. */
public record OtaTicketRevocationOutcome(
        OutcomeStatus status,
        List<UUID> revokedTicketReferences,
        RejectionReason reason
) {
    public OtaTicketRevocationOutcome {
        revokedTicketReferences = revokedTicketReferences == null ? List.of() : List.copyOf(revokedTicketReferences);
    }

    public static OtaTicketRevocationOutcome accepted(List<UUID> revokedTicketReferences) {
        return new OtaTicketRevocationOutcome(OutcomeStatus.ACCEPTED, revokedTicketReferences, null);
    }

    public static OtaTicketRevocationOutcome rejected(RejectionReason reason) {
        return new OtaTicketRevocationOutcome(OutcomeStatus.REJECTED, List.of(), reason);
    }

    public static OtaTicketRevocationOutcome inProgress() {
        return new OtaTicketRevocationOutcome(OutcomeStatus.IN_PROGRESS, List.of(), null);
    }
}
