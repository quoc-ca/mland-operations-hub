package edu.fpt.sep490_g22.ppswbs_backend.support.legacybooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AuthoritativeOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.OutcomeKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.MemberWorkshopBookingQuoteRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.WorkshopBooking;
import org.springframework.boot.test.context.TestComponent;

import java.time.Instant;

/**
 * Test stand-in for the source outcomes the removed single-booking flow sent to Loyalty (003.1 decision
 * 2026-11-13). Single direct bookings can no longer be created, but the 003.3 booking-wide settlement and the
 * Loyalty single-booking source stay until the open 003.3 decision; their tests seed a single booking and drive
 * Loyalty with exactly the outcomes the removed {@code WorkshopBookingLoyaltyOutcomeService} produced.
 */
@TestComponent
public class LegacyBookingLoyaltyOutcomes {

    private final MemberWorkshopBookingQuoteRepository quotes;
    private final LoyaltyFacade loyalty;

    public LegacyBookingLoyaltyOutcomes(MemberWorkshopBookingQuoteRepository quotes, LoyaltyFacade loyalty) {
        this.quotes = quotes;
        this.loyalty = loyalty;
    }

    /** WORKSHOP_DEPOSIT_ACCEPTED for a Member quote booking, keyed by the verified evidence. */
    public void settleVerifiedDeposit(WorkshopBooking booking, String verifiedEvidenceReference) {
        if (booking.getMemberAccountId() == null) return;
        var quote = quotes.findByAcceptedBookingCode(booking.getBookingCode()).orElse(null);
        if (quote == null || !booking.getMemberAccountId().equals(quote.getMemberAccountId())) return;
        loyalty.settleAcceptedBenefits(new AuthoritativeOutcome(SourceKind.WORKSHOP, booking.getBookingCode(),
                "workshopbooking", "deposit-" + verifiedEvidenceReference, booking.getVersion(),
                OutcomeKind.WORKSHOP_DEPOSIT_ACCEPTED, Instant.now(), booking.getHoldExpiresAt(), quote.getQuoteRef(),
                true, verifiedEvidenceReference));
    }

    /** UNPAID_EXPIRY_CONFIRMED: releases the held benefits and closes the earn. */
    public void finishConfirmedUnpaidExpiry(WorkshopBooking booking, String verifiedEvidenceReference) {
        if (booking.getMemberAccountId() == null) return;
        var quote = quotes.findByAcceptedBookingCode(booking.getBookingCode()).orElse(null);
        if (quote == null || !booking.getMemberAccountId().equals(quote.getMemberAccountId())) return;
        Instant now = Instant.now();
        long version = booking.getVersion() == null ? 0L : booking.getVersion();
        String key = "unpaid-expiry-" + booking.getBookingCode();
        loyalty.releaseHeldBenefits(new AuthoritativeOutcome(SourceKind.WORKSHOP, booking.getBookingCode(),
                "workshopbooking", key, version, OutcomeKind.UNPAID_EXPIRY_CONFIRMED, now, booking.getHoldExpiresAt(),
                quote.getQuoteRef(), true, verifiedEvidenceReference));
        loyalty.finishIneligibleEarn(new AuthoritativeOutcome(SourceKind.WORKSHOP, booking.getBookingCode(),
                "workshopbooking", key + "-earn", version + 1, OutcomeKind.UNPAID_EXPIRY_CONFIRMED, now,
                booking.getHoldExpiresAt(), quote.getQuoteRef(), true, verifiedEvidenceReference));
    }
}
