package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ReleaseBookingHoldsCommand(
    String bookingReference,
    List<UUID> expectedHoldReferences,
    String operationReference,
    String correlationReference
) {
    public ReleaseBookingHoldsCommand {
        Objects.requireNonNull(bookingReference, "bookingReference must not be null");
        Objects.requireNonNull(expectedHoldReferences, "expectedHoldReferences must not be null");
        Objects.requireNonNull(operationReference, "operationReference must not be null");
        Objects.requireNonNull(correlationReference, "correlationReference must not be null");
        expectedHoldReferences = List.copyOf(expectedHoldReferences);
    }
}
