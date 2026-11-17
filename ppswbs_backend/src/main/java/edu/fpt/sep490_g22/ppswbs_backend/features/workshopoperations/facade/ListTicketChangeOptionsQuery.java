package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Asks which sessions a ticket could move to (003.3 FR-044a, read only, nothing held). {@code fromSessionReferences}
 * empty means every current session of the ticket, one by one; two references also ask for the pairs.
 */
public record ListTicketChangeOptionsQuery(UUID ticketReference, TicketReplacementType type,
                                           List<UUID> fromSessionReferences) {
    public ListTicketChangeOptionsQuery {
        Objects.requireNonNull(ticketReference, "ticketReference must not be null");
        Objects.requireNonNull(type, "type must not be null");
        fromSessionReferences = fromSessionReferences == null ? List.of() : List.copyOf(fromSessionReferences);
    }
}
