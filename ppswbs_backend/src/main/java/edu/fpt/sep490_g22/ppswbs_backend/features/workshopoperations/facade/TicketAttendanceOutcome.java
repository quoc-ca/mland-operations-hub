package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.time.Instant;

/**
 * Attendance of one ticket (003.2 T092 / 003.3 FR-032).
 * Outcome is present only when terminal is true.
 * {@code windowClosesAt} is present once the ticket's required sessions are finalized,
 * even before the 24-hour correction window closes.
 * {@code live} is a display-only view of what is already recorded while the outcome is not final; it never
 * drives settlement. It is null when nothing has been recorded yet.
 */
public record TicketAttendanceOutcome(boolean terminal, Outcome outcome, Instant windowClosesAt, Live live) {
    public enum Outcome { ATTENDED, NO_SHOW, PARTIAL }

    /** CHECKED_IN: scanned in, sessions still open; ATTENDED / NO_SHOW_PENDING: finalized, correction window open. */
    public enum Live { CHECKED_IN, ATTENDED, NO_SHOW_PENDING }

    public TicketAttendanceOutcome(boolean terminal, Outcome outcome, Instant windowClosesAt) {
        this(terminal, outcome, windowClosesAt, null);
    }

    public TicketAttendanceOutcome(boolean terminal, Outcome outcome) {
        this(terminal, outcome, null, null);
    }

    public static TicketAttendanceOutcome pending() {
        return new TicketAttendanceOutcome(false, null, null, null);
    }

    public static TicketAttendanceOutcome pending(Instant windowClosesAt) {
        return new TicketAttendanceOutcome(false, null, windowClosesAt, null);
    }

    public static TicketAttendanceOutcome pending(Instant windowClosesAt, Live live) {
        return new TicketAttendanceOutcome(false, null, windowClosesAt, live);
    }
}
