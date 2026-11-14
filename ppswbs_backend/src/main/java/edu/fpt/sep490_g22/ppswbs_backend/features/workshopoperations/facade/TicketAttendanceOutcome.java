package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.time.Instant;

/**
 * Terminal attendance outcome of one ticket (003.2 T092 / 003.3 FR-032).
 * Outcome is present only when terminal is true.
 * {@code windowClosesAt} is present once the ticket's required sessions are finalized,
 * even before the 24-hour correction window closes.
 */
public record TicketAttendanceOutcome(boolean terminal, Outcome outcome, Instant windowClosesAt) {
    public enum Outcome { ATTENDED, NO_SHOW, PARTIAL }

    public TicketAttendanceOutcome(boolean terminal, Outcome outcome) {
        this(terminal, outcome, null);
    }

    public static TicketAttendanceOutcome pending() {
        return new TicketAttendanceOutcome(false, null, null);
    }

    public static TicketAttendanceOutcome pending(Instant windowClosesAt) {
        return new TicketAttendanceOutcome(false, null, windowClosesAt);
    }
}

