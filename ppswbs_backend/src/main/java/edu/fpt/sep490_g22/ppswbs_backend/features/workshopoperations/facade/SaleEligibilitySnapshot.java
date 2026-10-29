package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.time.Instant;
import java.time.LocalDate;

public record SaleEligibilitySnapshot(
    boolean customerSelectable,
    String saleState,
    Long locationReference,
    Instant scheduledStart,
    Instant scheduledEnd,
    int remainingCapacity,
    Long sessionId,
    LocalDate workshopDate
) {
    public SaleEligibilitySnapshot(boolean customerSelectable, String saleState, Long locationReference,
                                   Instant scheduledStart, Instant scheduledEnd, int remainingCapacity) {
        this(customerSelectable, saleState, locationReference, scheduledStart, scheduledEnd, remainingCapacity,
                null, null);
    }
}
