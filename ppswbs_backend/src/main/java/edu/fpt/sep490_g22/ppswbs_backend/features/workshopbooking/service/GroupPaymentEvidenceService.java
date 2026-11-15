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
    private final edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupRepository groupRepository;
    private final LatePaymentReviewService lateReviews;
    private final edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.GroupExcessPaymentRepository excessPayments;
    private final edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ExcessPaymentRefundPort excessRefunds;
    private final ManagerAlertService managerAlerts;
    private final GroupConfirmationAttemptRunner confirmationRunner;
    private final GroupConfirmationRetryService confirmationRetries;

    /** Whether this merchant reference belongs to a group payment at all. */
    public boolean owns(String merchantReference) {
        return merchantReference != null && attempts.findByMerchantReference(merchantReference).isPresent();
    }

    @Transactional
    public DepositPaymentEvidenceOutcome apply(String bookingReference, String merchantReference, long amountVnd,
                                               String currencyCode, String evidenceReference,
                                               String operationReference, String correlationReference) {
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
        if (attempt.getGroup().getGroupStatus() == BookingStatus.CONFIRMED) {
            return recordExcessPayment(attempt, merchantReference, amountVnd, operationReference, correlationReference);
        }
        // FR-040 (T094): a payment past the hold is a late payment, decided under the group lock, and never reaches
        // the confirmation path.
        if (lateReviews.isLate(attempt.getGroup(), java.time.Instant.now())) {
            return receiveLate(attempt, groupCode, merchantReference, amountVnd, correlationReference);
        }
        // T145: confirmation runs in its own transaction, so a dependency failure (003.2 or 006) cannot roll back the
        // evidence. The caller still commits the ingress receipt, the attempt SUCCESS (verified evidence only) and
        // the WAITING retry row; the group stays PENDING_PAYMENT and nothing is released.
        var result = confirmationRunner.tryConfirm(groupCode, amountVnd, evidenceReference, operationReference,
                correlationReference);
        switch (result.kind()) {
            case HOLD_EXPIRED -> {
                log.warn("Verified group payment for {} arrived after its hold expired", groupCode);
                return receiveLate(attempt, groupCode, merchantReference, amountVnd, correlationReference);
            }
            case DEPENDENCY_UNAVAILABLE -> {
                attempt.setAttemptStatus(PaymentAttemptStatus.SUCCESS);
                attempts.save(attempt);
                confirmationRetries.hold(attempt.getGroup(), attempt, evidenceReference, null, result.failureCode(),
                        correlationReference);
                return DepositPaymentEvidenceOutcome.accepted();
            }
            default -> {
                attempt.setAttemptStatus(PaymentAttemptStatus.SUCCESS);
                attempts.save(attempt);
                confirmationRetries.markConfirmed(attempt.getGroup().getId());
                return DepositPaymentEvidenceOutcome.accepted();
            }
        }
    }

    private DepositPaymentEvidenceOutcome receiveLate(GroupPaymentAttempt attempt, String groupCode,
                                                      String merchantReference, long amountVnd,
                                                      String correlationReference) {
        groupRepository.findByGroupCodeForUpdate(groupCode).orElseThrow();
        attempt.setAttemptStatus(PaymentAttemptStatus.SUCCESS);
        attempts.save(attempt);
        var review = lateReviews.receive(groupCode, merchantReference, amountVnd, correlationReference);
        return edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.LatePaymentReview.AUTO_RECONFIRMED
                .equals(review.getOutcome())
                ? DepositPaymentEvidenceOutcome.accepted()
                : DepositPaymentEvidenceOutcome.rejected(BookingRejectionReason.NOT_READY);
    }

    /**
     * FR-013a (T124): a second verified success with a different merchant reference on a CONFIRMED group is excess or
     * duplicate money. It is recorded in full, a 100% bank refund is opened through 003.3 in this same transaction
     * (a refusal leaves nothing recorded), the Managers are alerted, and the group, tickets, capacity and benefits
     * are not touched. A replay of the same evidence returns the stored outcome and does nothing more.
     */
    private DepositPaymentEvidenceOutcome recordExcessPayment(GroupPaymentAttempt attempt, String merchantReference,
                                                              long amountVnd, String operationReference,
                                                              String correlationReference) {
        if (excessPayments.findByVerifiedEvidenceReference(merchantReference).isPresent()) {
            return DepositPaymentEvidenceOutcome.accepted();
        }
        var group = attempt.getGroup();
        var outcome = excessRefunds.openExcessPaymentRefund(
                new edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ExcessPaymentRefundPort
                        .OpenExcessPaymentRefundCommand(group.getGroupCode(), merchantReference, amountVnd,
                        operationReference, correlationReference));
        if (outcome == null || !outcome.opened()) {
            log.warn("Excess payment for group {} could not open its bank refund: {}", group.getGroupCode(),
                    outcome == null ? "NO_OUTCOME" : outcome.reason());
            return DepositPaymentEvidenceOutcome.rejected(BookingRejectionReason.NOT_READY);
        }
        excessPayments.save(edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.GroupExcessPayment
                .builder()
                .group(group)
                .verifiedEvidenceReference(merchantReference)
                .merchantReference(merchantReference)
                .amountVnd(amountVnd)
                .receivedAt(java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS))
                .refundRequestReference(outcome.refundRequestReference())
                .refundOutcome(outcome.status())
                .correlationReference(correlationReference)
                .build());
        attempt.setAttemptStatus(PaymentAttemptStatus.SUCCESS);
        attempts.save(attempt);
        managerAlerts.alert(group, GroupNotificationService.KIND_EXCESS_PAYMENT_MANAGER_ALERT, merchantReference,
                "MLAND – Tiền thanh toán thừa hoặc trùng, đơn " + group.getGroupCode(),
                "Đơn " + group.getGroupCode() + " đã được xác nhận, nhưng vừa nhận thêm " + amountVnd
                        + " VND từ một giao dịch khác. Hệ thống đã mở yêu cầu hoàn 100% qua chuyển khoản ngân hàng "
                        + "và không thay đổi đơn. Mở hàng chờ hoàn tiền trong màn hình quản lý để theo dõi.",
                correlationReference);
        log.warn("Excess payment of group {} recorded and refunded through 003.3", group.getGroupCode());
        return DepositPaymentEvidenceOutcome.accepted();
    }
}
