package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Outcome of a group reserve. On acceptance {@code lineHoldReferences} has one entry per requested line, in order,
 * holding that line's hold references (one per session). On rejection nothing is held and {@code failingLines}
 * lists the zero-based indexes of the lines that could not be served, so the cart can show them.
 */
public record GroupCapacityOutcome(
        OutcomeStatus status,
        List<List<UUID>> lineHoldReferences,
        Instant expiresAt,
        RejectionReason reason,
        List<Integer> failingLines
) {
    public GroupCapacityOutcome {
        lineHoldReferences = lineHoldReferences == null ? List.of()
                : lineHoldReferences.stream().map(List::copyOf).toList();
        failingLines = failingLines == null ? List.of() : List.copyOf(failingLines);
    }

    public static GroupCapacityOutcome accepted(List<List<UUID>> lineHoldReferences, Instant expiresAt) {
        return new GroupCapacityOutcome(OutcomeStatus.ACCEPTED, lineHoldReferences, expiresAt, null, List.of());
    }

    public static GroupCapacityOutcome rejected(RejectionReason reason, List<Integer> failingLines) {
        return new GroupCapacityOutcome(OutcomeStatus.REJECTED, List.of(), null, reason, failingLines);
    }

    public static GroupCapacityOutcome inProgress() {
        return new GroupCapacityOutcome(OutcomeStatus.IN_PROGRESS, List.of(), null, null, List.of());
    }
}
