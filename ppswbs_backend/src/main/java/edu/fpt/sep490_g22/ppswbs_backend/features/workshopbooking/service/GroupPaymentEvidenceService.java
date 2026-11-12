package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.GroupPaymentAttemptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.GroupPaymentAttempt;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.PaymentAttemptStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingRejectionReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.DepositPaymentEvidenceOutcome;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Applies verified gateway evidence to a booking group's single payment (003.1 FR-038, FR-039).
 *
 * <p>Evidence is matched on the merchant reference of the group's own attempt, so a group payment never
 * reaches the single-booking path and the other way round. Replay is safe: once the attempt is {@code SUCCESS}
 * and the group is {@code CONFIRMED}, further IPN or QueryDR deliveries are accepted and change nothing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroupPaymentEvidenceService {

    private final GroupPaymentAttemptRepository attempts;
    private final GroupConfirmationService confirmation;

    /** Whether this merchant reference belongs to a group payment at all. */
    public boolean owns(String merchantReference) {
        return merchantReference != null && attempts.findByMerchantReference(merchantReference).isPresent();
    }

    @Transactional
    public DepositPaymentEvidenceOutcome apply(String bookingReference, String merchantReference, long amountVnd,
                                               String currencyCode, String operationReference,
                                               String correlationReference) {
        Optional<GroupPaymentAttempt> found = attempts.findByMerchantReference(merchantReference);
        if (found.isEmpty()) {
            return DepositPaymentEvidenceOutcome.rejected(BookingRejectionReason.NOT_READY);
        }
        GroupPaymentAttempt attempt = found.get();
        String groupCode = attempt.getGroup().getGroupCode();
        if (!groupCode.equals(bookingReference) || attempt.getAmountVnd() != amountVnd
                || !"VND".equals(currencyCode)) {
            return DepositPaymentEvidenceOutcome.rejected(BookingRejectionReason.PAYMENT_MISMATCH);
        }
        if (attempt.getAttemptStatus() == PaymentAttemptStatus.SUCCESS
                && attempt.getGroup().getGroupStatus() == BookingStatus.CONFIRMED) {
            return DepositPaymentEvidenceOutcome.accepted();
        }
        try {
            confirmation.confirmPaidGroup(groupCode, amountVnd, operationReference, correlationReference);
        } catch (BookingCreationRejectedException ex) {
            // The hold had already run out. The money is real, so the group waits for the FR-040 late-payment
            // review of T094; NOT_READY keeps the single-booking late-evidence path from being handed a group.
            log.warn("Verified group payment for {} arrived after its hold expired: {}", groupCode, ex.getCode());
            attempt.setAttemptStatus(PaymentAttemptStatus.SUCCESS);
            attempts.save(attempt);
            return DepositPaymentEvidenceOutcome.rejected(BookingRejectionReason.NOT_READY);
        }
        attempt.setAttemptStatus(PaymentAttemptStatus.SUCCESS);
        attempts.save(attempt);
        return DepositPaymentEvidenceOutcome.accepted();
    }
}
