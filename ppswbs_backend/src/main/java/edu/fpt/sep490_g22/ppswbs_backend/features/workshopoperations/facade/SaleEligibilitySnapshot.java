package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SaleEligibilitySnapshot(
    UUID sessionReference,
    boolean customerSelectable,
    String saleState,
    Long locationReference,
    Instant scheduledStart,
    Instant scheduledEnd,
    int remainingCapacity,
    LocalDate workshopDate
) {
    public SaleEligibilitySnapshot(boolean customerSelectable, String saleState, Long locationReference,
                                   Instant scheduledStart, Instant scheduledEnd, int remainingCapacity) {
        this(null, customerSelectable, saleState, locationReference, scheduledStart, scheduledEnd,
                remainingCapacity, null);
    }
}
