package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ReserveBookingCapacityCommand(
    String bookingReference,
    List<UUID> orderedRequiredSessionReferences,
    int participantCount,
    String operationReference,
    String correlationReference
) {
    public ReserveBookingCapacityCommand {
        Objects.requireNonNull(bookingReference, "bookingReference must not be null");
        Objects.requireNonNull(orderedRequiredSessionReferences, "orderedRequiredSessionReferences must not be null");
        Objects.requireNonNull(operationReference, "operationReference must not be null");
        Objects.requireNonNull(correlationReference, "correlationReference must not be null");
        orderedRequiredSessionReferences = List.copyOf(orderedRequiredSessionReferences);
    }
}
