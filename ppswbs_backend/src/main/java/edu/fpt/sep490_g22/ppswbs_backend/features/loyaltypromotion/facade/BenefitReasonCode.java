package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

/**
 * Stable code of a preview reason (FR-013, T081). The frontend translates the code (vi/en); {@link #template()} is the
 * English text kept in {@code BenefitPreview.reasons} for callers that only read strings. Placeholders such as
 * {@code {min}} are filled from {@link BenefitReason#params()}.
 */
public enum BenefitReasonCode {
    VOUCHER_NOT_FOUND("The voucher code was not found."),
    VOUCHER_FOR_WORKSHOP("This voucher is for workshop bookings, not for products."),
    VOUCHER_FOR_PRODUCT("This voucher is for products, not for workshop bookings."),
    PROMOTION_PAUSED("This promotion is paused."),
    PROMOTION_NOT_STARTED("This promotion has not started yet."),
    PROMOTION_ENDED("This promotion has ended."),
    VOUCHER_NO_USES_LEFT("This voucher has no uses left."),
    VOUCHER_MEMBER_LIMIT_REACHED("You have already used this voucher the maximum number of times."),
    NO_COVERED_ITEM("No item in the cart is covered by this voucher."),
    /** Session 2026-11-09: covered items exist, but none was added at a branch the voucher is limited to. */
    BRANCH_NOT_COVERED("This voucher does not apply at the branch of these items."),
    BELOW_MINIMUM_AMOUNT("The covered items do not reach the minimum amount of this voucher."),
    NO_AUTO_DISCOUNT("No automatic discount applies to this cart."),
    WORKSHOP_NO_AUTO_DISCOUNT("Workshops have no automatic discount; enter a voucher code instead."),
    WORKSHOP_NOT_COVERED("This voucher does not cover this package, branch, date or session."),
    WORKSHOP_BELOW_MINIMUM_FEE("The package fee does not reach the minimum amount of this voucher."),
    // Text kept verbatim: FE-03's QuoteReasonTranslator matches on it. Since 2026-11-12 only a negative amount
    // (which the calculator cannot produce) raises this reason; zero is valid.
    WORKSHOP_NET_AMOUNT_INVALID("The amount to pay after discounts must be greater than zero."),
    POINTS_NOT_COMBINABLE("Points cannot be used with this voucher."),
    POINTS_OUT_OF_RANGE("Points must be between {min} and {max}."),
    POINTS_BELOW_MINIMUM("Too few points can be used on this order to reach the minimum of {min}.");

    private final String template;

    BenefitReasonCode(String template) {
        this.template = template;
    }

    public String template() {
        return template;
    }
}
