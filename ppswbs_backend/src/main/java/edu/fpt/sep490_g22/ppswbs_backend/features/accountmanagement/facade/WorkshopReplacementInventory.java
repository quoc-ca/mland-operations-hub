package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Safe Booking projection for an ADMIN's MEMBER-collision review, never a JPA entity. */
public record WorkshopReplacementInventory(
        AccountId accountId,
        MemberReplacementInventoryStatus status,
        MemberReplacementDataOrigin dataOrigin,
        String sourceVersion,
        Instant observedAt,
        List<BookingSummary> bookings,
        List<ObligationBlocker> blockingReferences
) {
    public WorkshopReplacementInventory {
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(status);
        Objects.requireNonNull(dataOrigin);
        Objects.requireNonNull(observedAt);
        bookings = List.copyOf(bookings);
        blockingReferences = List.copyOf(blockingReferences);
        if (dataOrigin == MemberReplacementDataOrigin.DEMO
                && (status != MemberReplacementInventoryStatus.UNAVAILABLE || sourceVersion != null)) {
            throw new IllegalArgumentException("Demo inventory cannot authorize MEMBER retirement");
        }
    }

    public record BookingSummary(String bookingReference, Instant acceptedAt, String serviceState,
                                 String financialState, String packageSummary, Instant outcomeAt) { }

    public record ObligationBlocker(String reference, String reasonCode, String message) { }
}
