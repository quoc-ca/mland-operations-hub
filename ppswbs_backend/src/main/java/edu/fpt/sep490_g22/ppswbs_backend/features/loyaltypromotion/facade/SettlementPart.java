package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One separately settled part of a retail checkout (Session 2026-11-12, D-011): FE-08 splits a checkout into branch
 * orders that are paid, cancelled or expired one by one (cash at the counter). {@code partRef} is the owner's branch
 * order reference; {@code lineIndexes} are the zero-based indexes in {@link TrustedSourceContext#lines()} that belong
 * to this part. Every line belongs to exactly one part.
 */
public record SettlementPart(String partRef, List<Integer> lineIndexes) {

    public static final int MAX_PART_REF_LENGTH = 64;

    public SettlementPart {
        partRef = partRef == null ? "" : partRef.trim();
        if (partRef.isEmpty() || partRef.length() > MAX_PART_REF_LENGTH) {
            throw LoyaltyException.invalidField("parts", "A part reference is required (max 64 characters).");
        }
        if (lineIndexes == null || lineIndexes.isEmpty() || lineIndexes.stream().anyMatch(i -> i == null || i < 0)) {
            throw LoyaltyException.invalidField("parts", "A part needs at least one valid line index.");
        }
        Set<Integer> seen = new HashSet<>();
        for (Integer index : lineIndexes) {
            if (!seen.add(index)) {
                throw LoyaltyException.invalidField("parts", "Line indexes of a part must be distinct.");
            }
        }
        lineIndexes = lineIndexes.stream().sorted().toList();
    }
}
