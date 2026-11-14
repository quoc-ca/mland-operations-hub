package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

import java.time.Instant;

/** Minimal accepted Member Loyalty source context; contains no account or contact data. */
public record WorkshopBookingLoyaltyOutcomeSource(boolean memberOrigin, String snapshotRef,
                                                   long nextOutcomeVersion, Instant originalDeadline) {}
