package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.time.Instant;

public record SessionEntitlementProjection(
    Long locationReference,
    Instant scheduledStart,
    Instant scheduledEnd
) {}
