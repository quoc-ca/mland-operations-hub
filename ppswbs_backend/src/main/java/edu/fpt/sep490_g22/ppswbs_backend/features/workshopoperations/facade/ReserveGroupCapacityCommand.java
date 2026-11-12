package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Reserves capacity for every line of a booking group, all or none (003.2 FR-030).
 * Each line is one booking reference (a package, its ordered required sessions and a ticket count);
 * every ticket takes one seat in each of its sessions, and lines may share sessions.
 */
public record ReserveGroupCapacityCommand(
        String groupReference,
        List<Line> lines,
        String operationReference,
        String correlationReference
) {
    public ReserveGroupCapacityCommand {
        Objects.requireNonNull(groupReference, "groupReference must not be null");
        Objects.requireNonNull(lines, "lines must not be null");
        Objects.requireNonNull(operationReference, "operationReference must not be null");
        Objects.requireNonNull(correlationReference, "correlationReference must not be null");
        lines = List.copyOf(lines);
    }

    /** One cart line; {@code bookingReference} is the reference later used to commit/release its holds. */
    public record Line(String bookingReference, String packageReference,
                       List<UUID> orderedRequiredSessionReferences, int ticketCount) {
        public Line {
            Objects.requireNonNull(bookingReference, "bookingReference must not be null");
            Objects.requireNonNull(orderedRequiredSessionReferences, "sessions must not be null");
            orderedRequiredSessionReferences = List.copyOf(orderedRequiredSessionReferences);
        }
    }
}
