package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

/** Authoritative lifecycle outcome verified by the source owner (interfaces section 4). */
public enum OutcomeKind {
    RETAIL_FULLY_PAID,
    RETAIL_ZERO_ACCEPTED,
    /**
     * The Member cancelled a retail order before any payment, confirmed by FE-08 under its order lock (no payment
     * pending or captured). Releases held voucher/points and closes the earn, like an unpaid expiry (2026-10-30).
     */
    RETAIL_CANCELLED_BEFORE_PAYMENT,
    WORKSHOP_DEPOSIT_ACCEPTED,
    WORKSHOP_COMPLETED_AND_FULLY_PAID,
    UNPAID_EXPIRY_CONFIRMED,
    VALID_WORKSHOP_CANCELLATION,
    WORKSHOP_NO_SHOW_FINAL,
    /**
     * One settlement part (branch order) of a retail checkout accepted with parts was paid, for example cash confirmed
     * by Staff at the counter (Session 2026-11-12, D-011). Settles and earns that part only; the payment method does
     * not matter to loyalty.
     */
    RETAIL_BRANCH_PAID,
    /** One part was cancelled before any payment for it (FE-08 confirms under its lock). Releases that part only. */
    RETAIL_BRANCH_CANCELLED_BEFORE_PAYMENT,
    /** One part reached its deadline unpaid (FE-08 confirms). Releases that part only. */
    RETAIL_BRANCH_UNPAID_EXPIRY,
    /**
     * One ticket of a workshop cart is checked out and fully paid after the attendance-correction window (Session
     * 2026-11-12, D-012, 003.3 FR-030). Earns that ticket's share; {@code partRef} is the 003.1 ticket reference.
     */
    WORKSHOP_TICKET_COMPLETED_AND_FULLY_PAID,
    /**
     * The buyer validly cancelled one ticket at least 24 hours before its first session (003.3 FR-042). Gives back
     * the ticket's used points; the group's voucher use comes back once every ticket ended by a valid or Mland
     * cancellation.
     */
    WORKSHOP_TICKET_VALID_CANCELLATION,
    /** Mland cancelled one ticket (003.3 FR-043). Gives back its used points and the group's voucher use once. */
    WORKSHOP_TICKET_MLAND_CANCELLATION,
    /**
     * One ticket is a final no-show, or was cancelled later than 24 hours before its first session, which counts as a
     * no-show (003.3 FR-030, FR-042). Nothing is given back; the ticket earns nothing and blocks the group's voucher
     * return for valid cancellations.
     */
    WORKSHOP_TICKET_NO_SHOW_FINAL;

    /** True for the outcomes that apply to one settlement part and carry {@code partRef}. */
    public boolean isPartOutcome() {
        return this == RETAIL_BRANCH_PAID || this == RETAIL_BRANCH_CANCELLED_BEFORE_PAYMENT
                || this == RETAIL_BRANCH_UNPAID_EXPIRY || isWorkshopTicketOutcome();
    }

    /** True for the per-ticket outcomes of a workshop cart (Session 2026-11-12, D-012). */
    public boolean isWorkshopTicketOutcome() {
        return this == WORKSHOP_TICKET_COMPLETED_AND_FULLY_PAID || this == WORKSHOP_TICKET_VALID_CANCELLATION
                || this == WORKSHOP_TICKET_MLAND_CANCELLATION || this == WORKSHOP_TICKET_NO_SHOW_FINAL;
    }
}
