package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

import java.util.Objects;
import java.util.UUID;

/** Provider-neutral command for an authenticated, fully-paid OTA booking. */
public record OtaBookingCommand(String providerReference, String externalBookingReference,
                                String packageReference, UUID sessionReference, int participantCount,
                                String operationReference, String correlationReference) {
    public OtaBookingCommand {
        Objects.requireNonNull(providerReference);
        Objects.requireNonNull(externalBookingReference);
        Objects.requireNonNull(packageReference);
        Objects.requireNonNull(sessionReference);
        Objects.requireNonNull(operationReference);
        Objects.requireNonNull(correlationReference);
        if (participantCount < 1) throw new IllegalArgumentException("participantCount must be positive");
    }
}
