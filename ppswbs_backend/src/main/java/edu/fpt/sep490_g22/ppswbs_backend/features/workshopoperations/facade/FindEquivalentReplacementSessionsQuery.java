package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.util.Objects;
import java.util.UUID;

/** Asks which sessions could replace one cancelled session of a ticket (003.3 T112, FR-043). */
public record FindEquivalentReplacementSessionsQuery(UUID ticketReference, UUID cancelledSessionReference) {
    public FindEquivalentReplacementSessionsQuery {
        Objects.requireNonNull(ticketReference, "ticketReference must not be null");
        Objects.requireNonNull(cancelledSessionReference, "cancelledSessionReference must not be null");
    }
}
