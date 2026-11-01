package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.time.Instant;

public record BookingCancellationEligibilitySnapshot(boolean eligible, String reason,
        String earliestRequiredSessionReference, Instant earliestRequiredSessionStart,
        boolean hasCheckedInTicket, boolean hasNoShow) {
    public static BookingCancellationEligibilitySnapshot rejected(String reason) {
        return new BookingCancellationEligibilitySnapshot(false, reason, null, null, false, false);
    }
}
