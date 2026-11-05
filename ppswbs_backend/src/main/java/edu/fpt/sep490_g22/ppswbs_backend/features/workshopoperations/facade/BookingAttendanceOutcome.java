package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

/** Outcome is present only when terminal is true. */
public record BookingAttendanceOutcome(boolean terminal, Outcome outcome) {
    public enum Outcome { ATTENDED, NO_SHOW, PARTIAL }
    public static BookingAttendanceOutcome pending() { return new BookingAttendanceOutcome(false, null); }
}
