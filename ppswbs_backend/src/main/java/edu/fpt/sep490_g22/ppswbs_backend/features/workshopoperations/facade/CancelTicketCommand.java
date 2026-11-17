package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.util.Objects;
import java.util.UUID;

/**
 * Cancels one ticket of a booking group (003.2 FR-031): its seats in every future session are released and
 * its future entitlements revoked. No other ticket of the group and no attendance history is touched.
 * Idempotent per {@code operationReference}.
 */
public record CancelTicketCommand(
        UUID ticketReference,
        String cancellationCaseReference,
        String operationReference,
        String correlationReference
) {
    public CancelTicketCommand {
        Objects.requireNonNull(ticketReference, "ticketReference must not be null");
        Objects.requireNonNull(cancellationCaseReference, "cancellationCaseReference must not be null");
        Objects.requireNonNull(operationReference, "operationReference must not be null");
        Objects.requireNonNull(correlationReference, "correlationReference must not be null");
    }
}
