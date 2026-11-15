package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Every valid equivalent replacement for one cancelled session of a ticket, ordered by start; empty when none.
 * Read only: no seat is held (003.3 T112, decided 2026-11-14).
 */
public record EquivalentReplacementSessions(List<Candidate> candidates) {
    public EquivalentReplacementSessions {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    public static EquivalentReplacementSessions none() {
        return new EquivalentReplacementSessions(List.of());
    }

    public record Candidate(UUID sessionReference, Instant startAt, Instant endAt) {
    }
}
