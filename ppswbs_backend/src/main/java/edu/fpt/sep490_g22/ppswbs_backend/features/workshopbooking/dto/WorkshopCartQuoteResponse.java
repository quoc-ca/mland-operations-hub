package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Per-ticket breakdown of a quoted cart (003.1 FR-033..FR-037). Amounts are whole VND. The group collection
 * is what one payment collects at booking; {@code paymentMode} says how that payment is made (FR-036).
 *
 * <p>{@code maxPoints}, {@code minRedeemPoints} and {@code pointValueVnd} are the FE-06 point limits 006 reported
 * for this cart (FR-034), so the screen can show the cap before the Member chooses anything; a Member cart always
 * carries them and a Guest cart, which has no points, reports zeros. {@code reasons} is 006's text for every
 * voucher or point reason, so a refused voucher or a refused point amount says why instead of leaving the customer
 * to guess: the evaluator refuses a requested amount outside [minRedeemPoints, maxPoints] rather than trimming it.
 *
 * <p>{@code voucherDiscountVnd} (cart and ticket) is the programme discount, whichever programme gave it, and
 * {@code appliedDiscount} names that programme (T105, 006 FR-015): {@code AUTO} for the automatic workshop discount
 * every cart receives, Guest included; {@code VOUCHER} when the Member's voucher replaced it; {@code NONE} when no
 * programme discounts the cart.
 */
public record WorkshopCartQuoteResponse(
        String quoteRef,
        Instant expiresAt,
        List<QuotedLine> lines,
        long grossVnd,
        long voucherDiscountVnd,
        long pointsUsed,
        long pointDiscountVnd,
        long finalTotalVnd,
        long maxPoints,
        long minRedeemPoints,
        long pointValueVnd,
        long groupCollectionVnd,
        long remainingAtCheckoutVnd,
        String paymentMode,
        List<String> reasons,
        String appliedDiscount) {

    /** The one programme discount a cart may carry (006 FR-015). */
    public enum AppliedDiscount { NONE, AUTO, VOUCHER }

    public record QuotedLine(
            int lineIndex,
            Long packageId,
            String packageName,
            long pricePerParticipantVnd,
            int participantCount,
            List<QuotedSession> sessions,
            List<QuotedTicket> tickets) {
    }

    public record QuotedSession(
            UUID sessionReference,
            Long locationReference,
            int ordinal,
            Instant scheduledStart,
            Instant scheduledEnd) {
    }

    public record QuotedTicket(
            int participantOrdinal,
            long packagePriceVnd,
            long voucherDiscountVnd,
            long pointsUsed,
            long pointDiscountVnd,
            long finalPriceVnd,
            long collectedAtBookingVnd,
            String collectionKind,
            long remainingVnd) {
    }
}
