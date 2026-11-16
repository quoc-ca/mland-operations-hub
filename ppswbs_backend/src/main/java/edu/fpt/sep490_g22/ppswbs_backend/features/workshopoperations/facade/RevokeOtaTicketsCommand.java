package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Revokes named tickets of an OTA-origin booking after the OTA confirmed their cancellation
 * (003.2 T123, FR-036 amendment; callable by 003.4 only). {@code operationReference} is the OTA event reference.
 * All named tickets or none; idempotent per {@code operationReference}.
 */
public record RevokeOtaTicketsCommand(
        String bookingReference,
        List<UUID> ticketReferences,
        String operationReference,
        String correlationReference
) {
    public RevokeOtaTicketsCommand {
        Objects.requireNonNull(bookingReference, "bookingReference must not be null");
        Objects.requireNonNull(ticketReferences, "ticketReferences must not be null");
        Objects.requireNonNull(operationReference, "operationReference must not be null");
        Objects.requireNonNull(correlationReference, "correlationReference must not be null");
        ticketReferences = List.copyOf(ticketReferences);
    }
}
