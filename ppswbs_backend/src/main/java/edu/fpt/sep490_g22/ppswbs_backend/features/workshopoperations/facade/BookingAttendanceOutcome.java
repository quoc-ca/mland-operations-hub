package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.time.Instant;

/**
 * @deprecated Use {@link TicketAttendanceOutcome}.
 */
@Deprecated
public record BookingAttendanceOutcome(boolean terminal, Outcome outcome, Instant windowClosesAt) {
    public enum Outcome { ATTENDED, NO_SHOW, PARTIAL }

    public BookingAttendanceOutcome(boolean terminal, Outcome outcome) {
        this(terminal, outcome, null);
    }

    public static BookingAttendanceOutcome pending() {
        return new BookingAttendanceOutcome(false, null, null);
    }
}
