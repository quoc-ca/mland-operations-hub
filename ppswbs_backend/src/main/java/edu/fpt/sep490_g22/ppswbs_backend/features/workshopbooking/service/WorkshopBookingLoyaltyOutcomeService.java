package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AuthoritativeOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.OutcomeKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.MemberWorkshopBookingQuoteRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.WorkshopBooking;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

/** Applies only source-owned Loyalty outcomes for Member quote bookings. */
@Service
@RequiredArgsConstructor
public class WorkshopBookingLoyaltyOutcomeService {
    private final MemberWorkshopBookingQuoteRepository quotes;
    private final LoyaltyFacade loyalty;

    public void settleVerifiedDeposit(WorkshopBooking booking, String verifiedEvidenceReference) {
        if (booking.getMemberAccountId() == null) return;
        var quote = quotes.findByAcceptedBookingCode(booking.getBookingCode()).orElse(null);
        if (quote == null || !booking.getMemberAccountId().equals(quote.getMemberAccountId())) return;

        Instant now = Instant.now();
        loyalty.settleAcceptedBenefits(new AuthoritativeOutcome(
                SourceKind.WORKSHOP,
                booking.getBookingCode(),
                "workshopbooking",
                "deposit-" + verifiedEvidenceReference,
                booking.getVersion(),
                OutcomeKind.WORKSHOP_DEPOSIT_ACCEPTED,
                now,
                booking.getHoldExpiresAt(),
                quote.getQuoteRef(),
                true,
                verifiedEvidenceReference));
    }

    /** Called only after every deposit attempt has a matching, verified failure outcome. */
    public void finishConfirmedUnpaidExpiry(WorkshopBooking booking, String verifiedEvidenceReference) {
        if (booking.getMemberAccountId() == null) return;
        var quote = quotes.findByAcceptedBookingCode(booking.getBookingCode()).orElse(null);
        if (quote == null || !booking.getMemberAccountId().equals(quote.getMemberAccountId())) return;
        Instant now = Instant.now();
        long version = booking.getVersion() == null ? 0L : booking.getVersion();
        String key = "unpaid-expiry-" + booking.getBookingCode();
        loyalty.releaseHeldBenefits(new AuthoritativeOutcome(SourceKind.WORKSHOP,
                booking.getBookingCode(), "workshopbooking", key, version,
                OutcomeKind.UNPAID_EXPIRY_CONFIRMED, now, booking.getHoldExpiresAt(),
                quote.getQuoteRef(), true, verifiedEvidenceReference));
        loyalty.finishIneligibleEarn(new AuthoritativeOutcome(SourceKind.WORKSHOP,
                booking.getBookingCode(), "workshopbooking", key + "-earn", version + 1,
                OutcomeKind.UNPAID_EXPIRY_CONFIRMED, now, booking.getHoldExpiresAt(),
                quote.getQuoteRef(), true, verifiedEvidenceReference));
    }
}
