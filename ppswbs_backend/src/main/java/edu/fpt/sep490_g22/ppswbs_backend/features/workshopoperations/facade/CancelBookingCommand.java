package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.util.Objects;

/** Whole-booking cancellation command owned by Workshop Operations. */
public record CancelBookingCommand(
        String bookingReference,
        String cancellationCaseReference,
        String operationReference,
        String correlationReference
) {
    public CancelBookingCommand {
        Objects.requireNonNull(bookingReference, "bookingReference must not be null");
        Objects.requireNonNull(cancellationCaseReference, "cancellationCaseReference must not be null");
        Objects.requireNonNull(operationReference, "operationReference must not be null");
        Objects.requireNonNull(correlationReference, "correlationReference must not be null");
    }
}
