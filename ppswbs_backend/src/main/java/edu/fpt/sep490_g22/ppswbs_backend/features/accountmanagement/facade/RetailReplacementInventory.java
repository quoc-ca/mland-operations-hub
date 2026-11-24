package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Safe retail projection for an ADMIN's MEMBER-collision review, never a JPA entity. */
public record RetailReplacementInventory(
        AccountId accountId,
        MemberReplacementInventoryStatus status,
        MemberReplacementDataOrigin dataOrigin,
        String sourceVersion,
        Instant observedAt,
        List<CartLine> cartLines,
        List<OrderSummary> orders,
        List<ObligationBlocker> blockingReferences
) {
    public RetailReplacementInventory {
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(status);
        Objects.requireNonNull(dataOrigin);
        Objects.requireNonNull(observedAt);
        cartLines = List.copyOf(cartLines);
        orders = List.copyOf(orders);
        blockingReferences = List.copyOf(blockingReferences);
        if (dataOrigin == MemberReplacementDataOrigin.DEMO
                && (status != MemberReplacementInventoryStatus.UNAVAILABLE || sourceVersion != null)) {
            throw new IllegalArgumentException("Demo inventory cannot authorize MEMBER retirement");
        }
    }

    public record CartLine(String productReference, String variantReference, String displayName,
                           int quantity, Instant updatedAt) { }

    public record OrderSummary(String orderId, String displayCode, Instant acceptedAt,
                               String paymentState, String fulfilmentState, Instant completedAt,
                               String itemSummary) { }

    public record ObligationBlocker(String reference, String reasonCode, String message) { }
}
