package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.GroupPaymentAttemptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.GroupPaymentAttempt;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.PaymentAttemptStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingRejectionReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.PaymentFailureOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.RecordDefinitePaymentFailureCommand;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 003.1 FR-032 "Definite failure" (decided 2026-11-19): a verified IPN with a definite-failure code moves the
 * matching {@code INITIATED}/{@code PENDING} attempt to {@code FAILED} under the group row lock. The group stays
 * {@code PENDING_PAYMENT}, {@code UNPAID} and its hold is never touched. Idempotent: a {@code FAILED},
 * {@code SUCCESS} or {@code EXPIRED} attempt is left as it is and the call is acknowledged.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroupPaymentFailureService {

    private final GroupPaymentAttemptRepository attempts;
    private final BookingGroupRepository groups;
    private final EntityManager entityManager;

    @Transactional
    public PaymentFailureOutcome record(RecordDefinitePaymentFailureCommand command) {
        GroupPaymentAttempt attempt = attempts.findByMerchantReference(command.merchantReference()).orElse(null);
        if (attempt == null) {
            return PaymentFailureOutcome.rejected(BookingRejectionReason.NOT_READY);
        }
        groups.findByGroupCodeForUpdate(attempt.getGroup().getGroupCode()).orElseThrow();
        // Re-read the attempt under the lock: MySQL's repeatable-read snapshot may predate a concurrent change.
        entityManager.refresh(attempt, LockModeType.PESSIMISTIC_WRITE);
        if (attempt.getAttemptStatus() == PaymentAttemptStatus.INITIATED
                || attempt.getAttemptStatus() == PaymentAttemptStatus.PENDING) {
            attempt.setAttemptStatus(PaymentAttemptStatus.FAILED);
            attempts.save(attempt);
            log.info("Payment attempt of group {} failed definitively (response {}, status {})",
                    attempt.getGroup().getGroupCode(), command.providerResponseCode(),
                    command.providerTransactionStatus());
        }
        return PaymentFailureOutcome.accepted();
    }
}
