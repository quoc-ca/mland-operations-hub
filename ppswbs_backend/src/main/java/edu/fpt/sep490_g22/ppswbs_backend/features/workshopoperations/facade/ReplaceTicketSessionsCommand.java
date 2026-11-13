package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Replaces the sessions of each selected ticket, all or none across the selected tickets (003.2 FR-034).
 * Each line names one ticket and its complete new session set, in package order. Idempotent per
 * {@code operationReference}.
 */
public record ReplaceTicketSessionsCommand(
        List<Line> lines,
        TicketReplacementType type,
        String operationReference,
        String correlationReference
) {
    public ReplaceTicketSessionsCommand {
        Objects.requireNonNull(lines, "lines must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(operationReference, "operationReference must not be null");
        Objects.requireNonNull(correlationReference, "correlationReference must not be null");
        lines = List.copyOf(lines);
    }

    public record Line(UUID ticketReference, List<UUID> targetSessionReferences) {
        public Line {
            Objects.requireNonNull(ticketReference, "ticketReference must not be null");
            Objects.requireNonNull(targetSessionReferences, "targetSessionReferences must not be null");
            targetSessionReferences = List.copyOf(targetSessionReferences);
        }
    }
}
