package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.util.Objects;
import java.util.UUID;

public record AcceptOtaBookingCommand(
        String bookingReference,
        String packageReference,
        UUID sessionReference,
        int participantCount,
        String operationReference,
        String correlationReference) {

    public AcceptOtaBookingCommand {
        Objects.requireNonNull(bookingReference, "bookingReference must not be null");
        Objects.requireNonNull(packageReference, "packageReference must not be null");
        Objects.requireNonNull(sessionReference, "sessionReference must not be null");
        Objects.requireNonNull(operationReference, "operationReference must not be null");
        Objects.requireNonNull(correlationReference, "correlationReference must not be null");
    }
}
