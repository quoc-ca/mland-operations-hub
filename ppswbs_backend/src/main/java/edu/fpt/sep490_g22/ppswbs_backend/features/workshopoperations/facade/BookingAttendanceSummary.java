package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

/**
 * What a customer may be told about their attendance, read without locking anything. Unlike
 * {@link BookingAttendanceOutcome} it is available right after the sessions are finalized, while the 24-hour correction
 * window is still open and the outcome is not yet final.
 *
 * @param sessionsFinalized  every session the booking holds a ticket for has its attendance finalized
 * @param hasNoShow          at least one ticket of the booking is recorded as a no-show
 * @param correctionWindowClosed the correction window is over, so the no-show can no longer change
 */
public record BookingAttendanceSummary(boolean sessionsFinalized, boolean hasNoShow, boolean correctionWindowClosed) {
    public static BookingAttendanceSummary notFinalized() {
        return new BookingAttendanceSummary(false, false, false);
    }
}
