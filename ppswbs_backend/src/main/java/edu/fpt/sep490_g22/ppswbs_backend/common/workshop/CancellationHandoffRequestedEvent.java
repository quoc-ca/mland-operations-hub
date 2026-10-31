package edu.fpt.sep490_g22.ppswbs_backend.common.workshop;

import java.util.List;

/** Durable handoff payload published after Operations commits cancellation state. */
public record CancellationHandoffRequestedEvent(
        String eventReference,
        String sessionReference,
        List<String> affectedBookingReferences,
        String cancellationReasonReference,
        String operationReference,
        String correlationReference,
        String cancellationCaseReference
) {
    public CancellationHandoffRequestedEvent {
        affectedBookingReferences = List.copyOf(affectedBookingReferences == null
                ? List.of() : affectedBookingReferences);
    }
}
