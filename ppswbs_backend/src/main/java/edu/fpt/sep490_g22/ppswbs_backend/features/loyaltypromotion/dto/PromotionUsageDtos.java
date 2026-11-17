package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto;

/**
 * Current usage of a programme (T045, OpenAPI {@code UsageSummary}). A voucher reports its limits and counters; an
 * automatic discount, which has no quota, reports how many settled transactions used it. Amounts are decimal strings.
 */
public final class PromotionUsageDtos {

    private PromotionUsageDtos() {
    }

    /** Marker for the two shapes of the usage summary. */
    public sealed interface Summary permits ProgramUsage, AutoUsage {
    }

    public record ProgramUsage(String programId, String totalLimit, String perMemberLimit, String heldCount,
                               String consumedCount, String committedDiscountAmount) implements Summary {
    }

    public record AutoUsage(String programId, String acceptedCount, String committedDiscountAmount) implements Summary {
    }
}
