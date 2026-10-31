package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto;

import java.util.List;

/**
 * Member-owned read models of My Loyalty (T039, OpenAPI {@code Account}, {@code LoyaltyTransaction},
 * {@code TransactionPage}). Points are decimal strings; changes are signed. No member id is ever accepted from or
 * echoed to the client.
 */
public final class LoyaltyAccountDtos {

    private LoyaltyAccountDtos() {
    }

    public record Account(String balancePoints, String heldPoints, String availablePoints,
                          LoyaltyPolicyDtos.Policy policy) {
    }

    public record Transaction(String id, String kind, String sourceKind, String sourceRef, String balanceChange,
                              String heldChange, String occurredAt, String originalDebitId) {
    }

    public record TransactionPage(List<Transaction> items, String nextCursor) {
    }
}
