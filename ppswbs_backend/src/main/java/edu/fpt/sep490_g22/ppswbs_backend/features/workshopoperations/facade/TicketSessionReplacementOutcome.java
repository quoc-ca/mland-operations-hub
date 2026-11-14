package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.util.List;

/**
 * Result of {@link ReplaceTicketSessionsCommand}. On refusal {@code failingLineIndexes} names the lines that
 * caused it, so the caller can show the customer which ticket blocked the whole change.
 */
public record TicketSessionReplacementOutcome(
        OutcomeStatus status,
        List<Integer> failingLineIndexes,
        RejectionReason reason
) {
    public TicketSessionReplacementOutcome {
        failingLineIndexes = failingLineIndexes == null ? List.of() : List.copyOf(failingLineIndexes);
    }

    public static TicketSessionReplacementOutcome accepted() {
        return new TicketSessionReplacementOutcome(OutcomeStatus.ACCEPTED, List.of(), null);
    }

    public static TicketSessionReplacementOutcome rejected(RejectionReason reason, List<Integer> failingLineIndexes) {
        return new TicketSessionReplacementOutcome(OutcomeStatus.REJECTED, failingLineIndexes, reason);
    }

    public static TicketSessionReplacementOutcome inProgress() {
        return new TicketSessionReplacementOutcome(OutcomeStatus.IN_PROGRESS, List.of(), null);
    }
}
