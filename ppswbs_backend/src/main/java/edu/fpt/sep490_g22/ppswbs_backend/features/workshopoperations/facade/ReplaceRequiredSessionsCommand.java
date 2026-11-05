package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ReplaceRequiredSessionsCommand(
    String bookingReference,
    List<UUID> completeTargetSessionReferences,
    String operationReference,
    String correlationReference
) {
    public ReplaceRequiredSessionsCommand {
        Objects.requireNonNull(bookingReference, "bookingReference must not be null");
        Objects.requireNonNull(completeTargetSessionReferences, "completeTargetSessionReferences must not be null");
        Objects.requireNonNull(operationReference, "operationReference must not be null");
        Objects.requireNonNull(correlationReference, "correlationReference must not be null");
        completeTargetSessionReferences = List.copyOf(completeTargetSessionReferences);
    }
}
