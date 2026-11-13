package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.LatePaymentRefundPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.LatePaymentReviewSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 003.3 side of {@link LatePaymentRefundPort} (003.1 FR-040, decision 2026-11-13). Rechecks the amount against
 * the late payment Booking verified, then opens one group-level bank-refund request (reason
 * LATE_PAYMENT_NOT_RECONFIRMED, source = review reference) in the caller's transaction. A missing refund account is
 * not an error: the request starts in PENDING_ACCOUNT. Never calls the VNPay Refund API.
 */
@Component
@Slf4j
class LatePaymentRefundPortAdapter implements LatePaymentRefundPort {

    private final WorkshopBookingFacade bookings;
    private final BankRefundService bankRefunds;

    /** Both are lazy: Booking calls this port from inside its own facade, and Settlement reaches Booking back. */
    LatePaymentRefundPortAdapter(@org.springframework.context.annotation.Lazy WorkshopBookingFacade bookings,
                                 @org.springframework.context.annotation.Lazy BankRefundService bankRefunds) {
        this.bookings = bookings;
        this.bankRefunds = bankRefunds;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public LatePaymentRefundOutcome openLatePaymentRefund(OpenLatePaymentRefundCommand command) {
        if (command == null || command.reviewReference() == null || command.groupCode() == null) {
            return rejected("REFERENCES_REQUIRED");
        }
        LatePaymentReviewSnapshot review = bookings.findLatePaymentReview(command.reviewReference()).orElse(null);
        if (review == null || !review.groupCode().equals(command.groupCode())) {
            return rejected("EVIDENCE_NOT_FOUND");
        }
        if (command.amountVnd() != review.amountVnd()
                || (command.evidenceReference() != null
                && !command.evidenceReference().equals(review.verifiedEvidenceReference()))) {
            return rejected("AMOUNT_MISMATCH");
        }
        BankRefundService.OpenedRefund opened = bankRefunds.openLatePaymentRefund(review.groupCode(),
                review.reviewReference(), command.amountVnd(), review.amountVnd(), command.correlationReference());
        if (opened == null || opened.request().getAmountVnd() != command.amountVnd()) {
            return rejected("SOURCE_CONFLICT");
        }
        var request = opened.request();
        String status = opened.rawAccountLinkToken() == null ? LatePaymentRefundOutcome.PRIOR_EQUIVALENT
                : LatePaymentRefundOutcome.ACCEPTED;
        log.info("Late-payment bank refund {} for review {}: {}", request.getRequestReference(),
                review.reviewReference(), status);
        return new LatePaymentRefundOutcome(status, request.getRequestReference(), request.getStatus().name(), null);
    }

    private static LatePaymentRefundOutcome rejected(String reason) {
        return new LatePaymentRefundOutcome(LatePaymentRefundOutcome.REJECTED, null, null, reason);
    }
}
