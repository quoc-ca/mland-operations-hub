package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade;

import java.util.List;

public sealed interface RosterOutcome {
    record Available(List<InternalRosterEntry> entries) implements RosterOutcome {}
    record Unavailable() implements RosterOutcome {}

    static RosterOutcome available(List<InternalRosterEntry> entries) {
        return new Available(entries != null ? List.copyOf(entries) : List.of());
    }

    static RosterOutcome unavailable() {
        return new Unavailable();
    }
}

