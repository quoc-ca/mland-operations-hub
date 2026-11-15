package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ExcessPaymentRefundPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.LatePaymentRefundPort.LatePaymentRefundOutcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 003.3 side of {@link ExcessPaymentRefundPort} (003.1 FR-013a, T124): opens one group-level 100% bank-refund request
 * with reason EXCESS_OR_DUPLICATE_MONEY in the caller's transaction. Idempotent per evidence reference. A missing
 * refund account is not an error: the request starts in PENDING_ACCOUNT. Never calls the VNPay Refund API.
 */
@Component
@Slf4j
class ExcessPaymentRefundPortAdapter implements ExcessPaymentRefundPort {

    private final BankRefundService bankRefunds;

    /** Lazy: Booking calls this port from inside its own evidence transaction. */
    ExcessPaymentRefundPortAdapter(@Lazy BankRefundService bankRefunds) {
        this.bankRefunds = bankRefunds;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public LatePaymentRefundOutcome openExcessPaymentRefund(OpenExcessPaymentRefundCommand command) {
        if (command == null || isBlank(command.groupCode()) || isBlank(command.evidenceReference())) {
            return rejected("REFERENCES_REQUIRED");
        }
        if (command.amountVnd() <= 0) {
            return rejected("AMOUNT_INVALID");
        }
        BankRefundService.OpenedRefund opened = bankRefunds.openExcessPaymentRefund(command.groupCode(),
                command.evidenceReference(), command.amountVnd(), command.correlationReference());
        if (opened.request().getAmountVnd() != command.amountVnd()) {
            return rejected("SOURCE_CONFLICT");
        }
        String status = opened.rawAccountLinkToken() == null ? LatePaymentRefundOutcome.PRIOR_EQUIVALENT
                : LatePaymentRefundOutcome.ACCEPTED;
        log.info("Excess-payment bank refund {} opened: {}", opened.request().getRequestReference(), status);
        return new LatePaymentRefundOutcome(status, opened.request().getRequestReference(),
                opened.request().getStatus().name(), null);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static LatePaymentRefundOutcome rejected(String reason) {
        return new LatePaymentRefundOutcome(LatePaymentRefundOutcome.REJECTED, null, null, reason);
    }
}
