package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade;

import java.util.List;

/** Safe cross-feature command for opening a cancellation handoff. */
public record OpenCancellationHandoffCommand(
        String sessionReference,
        List<String> affectedBookingReferences,
        String cancellationReasonReference,
        String operationReference,
        String correlationReference
) {
    public OpenCancellationHandoffCommand {
        affectedBookingReferences = affectedBookingReferences == null
                ? List.of()
                : List.copyOf(affectedBookingReferences);
    }
}
