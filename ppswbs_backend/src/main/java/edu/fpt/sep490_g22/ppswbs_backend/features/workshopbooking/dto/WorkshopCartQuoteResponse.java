package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Per-ticket breakdown of a quoted cart (003.1 FR-033..FR-037). Amounts are whole VND. The group collection
 * is what one payment collects at booking; {@code paymentMode} says how that payment is made (FR-036).
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
        long groupCollectionVnd,
        long remainingAtCheckoutVnd,
        String paymentMode) {

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
