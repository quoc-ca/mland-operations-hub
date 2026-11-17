package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Management read model of a programme (T028, OpenAPI {@code Program} and {@code ProgramPage}). Every number is a
 * decimal string; no entity, counter internals or personal data is exposed. {@code code}, {@code totalLimit} and
 * {@code perMemberLimit} are null for an automatic discount.
 */
public final class PromotionReadDtos {

    private PromotionReadDtos() {
    }

    public record Program(String id, String version, String revision, String kind, String scope, String code,
                          String status, String name, String startAt, String endAt, Discount discount,
                          String minimumEligibleAmount, boolean allowPoints, Targets targets, String totalLimit,
                          String perMemberLimit, String createdAt,
                          @JsonInclude(JsonInclude.Include.NON_NULL) String targetCheck,
                          @JsonInclude(JsonInclude.Include.NON_NULL) List<TargetWarning> targetWarnings) {

        /** The same programme with the target check of its detail read (getPromotionProgram only). */
        public Program withTargetCheck(String check, List<TargetWarning> warnings) {
            return new Program(id, version, revision, kind, scope, code, status, name, startAt, endAt, discount,
                    minimumEligibleAmount, allowPoints, targets, totalLimit, perMemberLimit, createdAt, check, warnings);
        }
    }

    /**
     * A product or size in a product programme's scope that is no longer usable for new purchases (spec clarification
     * 2026-10-26): {@code state} is DELETED (soft-deleted or gone), NOT_PUBLIC (unpublished, hidden or its category
     * inactive) or NOT_ON_SALE (public but no size on sale). The programme keeps running; the Manager decides.
     */
    public record TargetWarning(String dimension, String targetKey, String state) {
    }

    /** PERCENT carries {@code percentage}; FIXED carries {@code amount}. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Discount(String kind, String percentage, String amount, String maxDiscountAmount) {
    }

    /** Only the dimensions the programme uses are present. */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Targets(List<String> productIds, List<String> variantIds, List<String> packageIds,
                          List<String> branchIds, List<String> workshopDates, List<String> sessionIds) {
    }

    public record ProgramPage(List<Program> items, String nextCursor) {
    }
}
