package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.time.Instant;
import java.util.UUID;

public record SaleEligibilitySnapshot(
    boolean customerSelectable,
    String saleState,
    UUID branchReference,
    Instant scheduledStart,
    Instant scheduledEnd,
    int remainingCapacity
) {}
