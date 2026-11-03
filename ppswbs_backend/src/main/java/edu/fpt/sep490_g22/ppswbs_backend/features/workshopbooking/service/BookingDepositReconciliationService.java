package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.RecordLateDepositEvidenceCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Reconciles expired deposit attempts without ever reopening a capacity hold. */
@Service
@Slf4j
public class BookingDepositReconciliationService {
    private static final Duration QUERY_WINDOW = Duration.ofMinutes(60);
    private static final Duration QUERY_INTERVAL = Duration.ofMinutes(5);

    private final WorkshopBookingRepository bookings;
    private final BookingPaymentAttemptRepository attempts;
    private final BookingDepositReconciliationRepository reconciliations;
    private final BookingLatePaymentEvidenceRepository latePayments;
    private final BookingHoldReleaseService holdRelease;
    private final WorkshopBookingLoyaltyOutcomeService loyalty;
    private final WorkshopBookingFacade bookingFacade;
    private final PaymentIngressOutcomeQueryPort ingress;
    private final VnpayMerchantOperationsPort merchant;
    private final String merchantApiIpAddress;
    private final TransactionTemplate transaction;

    public BookingDepositReconciliationService(WorkshopBookingRepository bookings,
            BookingPaymentAttemptRepository attempts, BookingDepositReconciliationRepository reconciliations,
            BookingLatePaymentEvidenceRepository latePayments, BookingHoldReleaseService holdRelease,
            WorkshopBookingLoyaltyOutcomeService loyalty, WorkshopBookingFacade bookingFacade,
            PaymentIngressOutcomeQueryPort ingress, VnpayMerchantOperationsPort merchant,
            @Value("${vnpay.merchant-api-ip-address:}") String merchantApiIpAddress,
            PlatformTransactionManager manager) {
        this.bookings = bookings;
        this.attempts = attempts;
        this.reconciliations = reconciliations;
        this.latePayments = latePayments;
        this.holdRelease = holdRelease;
        this.loyalty = loyalty;
        this.bookingFacade = bookingFacade;
        this.ingress = ingress;
        this.merchant = merchant;
        this.merchantApiIpAddress = merchantApiIpAddress;
        this.transaction = new TransactionTemplate(manager);
    }

    @Scheduled(fixedDelayString = "${workshop.booking.expired-deposit-poll-ms:15000}")
    public void reconcileDue() {
        Instant now = Instant.now();
        for (WorkshopBooking booking : bookings.findByBookingStatusAndHoldExpiresAtBefore(
                BookingStatus.PENDING_PAYMENT, now)) {
            try {
                closeExpiredHold(booking.getBookingCode(), now);
            } catch (RuntimeException failure) {
                log.warn("Expired booking closure deferred bookingRef={} type={}",
                        booking.getBookingCode(), failure.getClass().getSimpleName());
            }
        }
        for (BookingDepositReconciliation row : reconciliations
                .findTop100ByLifecycleStatusAndNextQueryAtLessThanEqualOrderByNextQueryAtAsc("PENDING", now)) {
            try {
                reconcileAttempt(row.getAttempt().getId(), now);
            } catch (RuntimeException failure) {
                log.warn("Deposit reconciliation deferred attemptId={} type={}",
                        row.getAttempt().getId(), failure.getClass().getSimpleName());
            }
        }
    }

    /** Safe, redacted daily-list source; transport is a separate Manager API task. */
    public List<Long> dailyUnresolvedAttemptIds(Instant now) {
        return reconciliations.findDailyUnresolved(now.minus(Duration.ofHours(24))).stream()
                .map(row -> row.getAttempt().getId()).toList();
    }

    void closeExpiredHold(String bookingCode, Instant now) {
        transaction.executeWithoutResult(status -> {
            WorkshopBooking booking = bookings.findLockedByBookingCode(bookingCode).orElse(null);
            if (booking == null || booking.getBookingStatus() != BookingStatus.PENDING_PAYMENT
                    || booking.getHoldExpiresAt() == null || booking.getHoldExpiresAt().isAfter(now)) return;
            List<BookingPaymentAttempt> paymentAttempts = attempts.findByBookingIdOrderByCreatedAtDesc(booking.getId());
            holdRelease.expirePendingHold(bookingCode, "EXPIRE-" + bookingCode);
            bookings.flush();
            if (paymentAttempts.isEmpty() && !latePayments.existsByBookingCode(bookingCode))
                loyalty.finishConfirmedUnpaidExpiry(booking, "no-attempt-" + bookingCode);
        });
    }

    void reconcileAttempt(Long attemptId, Instant now) {
        Check check = transaction.execute(status -> {
            BookingPaymentAttempt candidate = attempts.findById(attemptId).orElse(null);
            if (candidate == null) return null;
            bookings.findLockedByBookingCode(candidate.getBooking().getBookingCode());
            BookingDepositReconciliation row = reconciliations.findLockedByAttemptId(attemptId).orElse(null);
            if (row == null || !"PENDING".equals(row.getLifecycleStatus())
                    || row.getNextQueryAt() == null || row.getNextQueryAt().isAfter(now)) return null;
            BookingPaymentAttempt attempt = row.getAttempt();
            if (attempt.getExpiresAt().isAfter(now)) return null;
            if (attempt.getBooking().getBookingStatus() == BookingStatus.CONFIRMED) {
                row.setLifecycleStatus("CONFIRMED");
                row.setNextQueryAt(null);
                row.setUpdatedAt(now);
                return null;
            }
            var terminalIpn = ingress.findVerifiedTerminalOutcome(attempt.getMerchantReference());
            if (terminalIpn.isPresent()) {
                applyTerminalIpn(row, terminalIpn.get(), now);
                if (!"PENDING".equals(row.getLifecycleStatus())) return null;
            }
            if (now.isAfter(attempt.getExpiresAt().plus(QUERY_WINDOW))) {
                row.setLifecycleStatus("UNRESOLVED");
                row.setNextQueryAt(null);
                row.setUpdatedAt(now);
                return null;
            }
            if (merchantApiIpAddress == null || merchantApiIpAddress.isBlank()
                    || attempt.getProviderCreateDate() == null) return null;
            long slot = Duration.between(attempt.getExpiresAt(), now).toMinutes() / QUERY_INTERVAL.toMinutes();
            String operation = "QDR-" + attempt.getAttemptReference() + "-POST-" + slot;
            row.setLastQueryAt(now);
            row.setNextQueryAt(attempt.getExpiresAt().plus(QUERY_INTERVAL.multipliedBy(slot + 1)));
            row.setUpdatedAt(now);
            return new Check(attemptId, attempt.getBooking().getBookingCode(), attempt.getMerchantReference(),
                    attempt.getProviderCreateDate(), attempt.getAttemptAmount(), operation);
        });
        if (check == null) return;
        VnpayMerchantOperationResult result = merchant.queryTransaction(new VnpayQueryDrCommand(
                check.operationReference(), check.merchantReference(), check.providerCreateDate(),
                "Workshop deposit " + check.bookingCode(), merchantApiIpAddress));
        if (result == null || !result.responseSignatureVerified()
                || !"00".equals(result.providerResponseCode())
                || !check.merchantReference().equals(result.merchantReference())
                || !Objects.equals(check.amountVnd(), result.amountVnd())) return;
        if ("02".equals(result.transactionStatus())) {
            transaction.executeWithoutResult(status -> markFailed(check.attemptId(), check.operationReference(), now));
        } else if ("00".equals(result.transactionStatus())
                && result.providerTransactionNumber() != null && !result.providerTransactionNumber().isBlank()) {
            String evidence = "VNPAY-QDR-" + result.providerTransactionNumber();
            bookingFacade.recordLateDepositEvidence(new RecordLateDepositEvidenceCommand(check.bookingCode(),
                    check.merchantReference(), evidence, result.providerTransactionNumber(), check.amountVnd(), "VND"));
            transaction.executeWithoutResult(status -> markLateSuccess(check.attemptId(), evidence, now));
        }
    }

    private void applyTerminalIpn(BookingDepositReconciliation row, VerifiedTerminalPaymentOutcome ipn, Instant now) {
        BookingPaymentAttempt attempt = row.getAttempt();
        if (!attempt.getMerchantReference().equals(ipn.merchantReference())
                || !Objects.equals(attempt.getAttemptAmount(), ipn.amountVnd())
                || !"VND".equals(ipn.currencyCode())) return;
        if ("02".equals(ipn.transactionStatus())) {
            markFailed(attempt.getId(), ipn.evidenceReference(), now);
        } else if ("00".equals(ipn.transactionStatus())) {
            markLateSuccess(attempt.getId(), ipn.evidenceReference(), now);
        }
    }

    private void markFailed(Long attemptId, String proof, Instant now) {
        BookingPaymentAttempt candidate = attempts.findById(attemptId).orElse(null);
        if (candidate == null) return;
        WorkshopBooking booking = bookings.findLockedByBookingCode(candidate.getBooking().getBookingCode()).orElse(null);
        BookingDepositReconciliation row = reconciliations.findLockedByAttemptId(attemptId).orElse(null);
        if (row == null || !"PENDING".equals(row.getLifecycleStatus())) return;
        BookingPaymentAttempt attempt = row.getAttempt();
        if (booking == null || booking.getBookingStatus() != BookingStatus.EXPIRED
                || latePayments.existsByBookingCode(booking.getBookingCode())) return;
        row.setLifecycleStatus("FAILED_VERIFIED");
        row.setVerifiedEvidenceReference(proof);
        row.setNextQueryAt(null);
        row.setUpdatedAt(now);
        reconciliations.saveAndFlush(row);
        attempt.setStatus(PaymentAttemptStatus.FAILED);
        attempts.save(attempt);
        boolean allFailed = attempts.findByBookingIdOrderByCreatedAtDesc(booking.getId()).stream()
                .allMatch(item -> reconciliations.findByAttemptId(item.getId())
                        .map(state -> "FAILED_VERIFIED".equals(state.getLifecycleStatus())).orElse(false));
        if (allFailed) loyalty.finishConfirmedUnpaidExpiry(booking, proof);
    }

    private void markLateSuccess(Long attemptId, String proof, Instant now) {
        BookingDepositReconciliation row = reconciliations.findLockedByAttemptId(attemptId).orElse(null);
        if (row == null || !"PENDING".equals(row.getLifecycleStatus())) return;
        row.setLifecycleStatus("LATE_SUCCESS");
        row.setVerifiedEvidenceReference(proof);
        row.setNextQueryAt(null);
        row.setUpdatedAt(now);
        reconciliations.save(row);
    }

    private record Check(Long attemptId, String bookingCode, String merchantReference,
                         String providerCreateDate, Long amountVnd, String operationReference) {}
}
