package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentIngressOutcomeQueryPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationsPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayQueryDrCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingPaymentAttemptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.DepositPaymentRecoveryCandidate;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.PaymentAttemptStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ApplyVerifiedDepositPaymentEvidenceCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingOutcomeStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingRejectionReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.RecordLateDepositEvidenceCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Requests the two bounded QueryDR checks while the Booking hold is still active. */
@Service
@Slf4j
public class DepositPaymentQueryDrRecoveryScheduler {
    private static final DateTimeFormatter PROVIDER_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.ofHours(7));

    private final BookingPaymentAttemptRepository attempts;
    private final PaymentIngressOutcomeQueryPort ingressOutcomes;
    private final VnpayMerchantOperationsPort merchantOperations;
    private final WorkshopBookingFacade booking;
    private final String merchantApiIpAddress;

    public DepositPaymentQueryDrRecoveryScheduler(
            BookingPaymentAttemptRepository attempts,
            PaymentIngressOutcomeQueryPort ingressOutcomes,
            VnpayMerchantOperationsPort merchantOperations,
            WorkshopBookingFacade booking,
            @Value("${vnpay.merchant-api-ip-address:}") String merchantApiIpAddress) {
        this.attempts = attempts;
        this.ingressOutcomes = ingressOutcomes;
        this.merchantOperations = merchantOperations;
        this.booking = booking;
        this.merchantApiIpAddress = merchantApiIpAddress;
    }

    @Scheduled(fixedDelayString = "${workshop.booking.query-dr.poll-delay-ms:15000}")
    public void recoverEligibleDeposits() {
        Instant now = Instant.now();
        List<DepositPaymentRecoveryCandidate> candidates = attempts.findRecoveryCandidates(
                List.of(PaymentAttemptStatus.INITIATED, PaymentAttemptStatus.PENDING), BookingStatus.PENDING_PAYMENT);
        for (DepositPaymentRecoveryCandidate candidate : candidates) {
            try {
                recover(candidate, now);
            } catch (RuntimeException failure) {
                log.warn("Booking QueryDR recovery failed for attemptReference={} ({})",
                        candidate.attemptReference(), failure.getClass().getSimpleName());
            }
        }
    }

    void recover(DepositPaymentRecoveryCandidate candidate, Instant now) {
        if (merchantApiIpAddress == null || merchantApiIpAddress.isBlank()) {
            log.warn("Booking QueryDR recovery skipped: VNPAY_MERCHANT_API_IP_ADDRESS is not configured");
            return;
        }
        Instant holdExpiry = candidate.holdExpiresAt();
        if (holdExpiry == null || !holdExpiry.isAfter(now) || candidate.expiresAt() == null
                || !candidate.expiresAt().isAfter(now) || candidate.providerCreateDate() == null) return;
        if (ingressOutcomes.hasVerifiedTerminalOutcome(candidate.merchantReference())) return;

        for (int minute : List.of(8, 9)) {
            Instant checkpoint = holdExpiry.minus(Duration.ofMinutes(10L - minute));
            if (now.isBefore(checkpoint)) continue;
            if (ingressOutcomes.hasVerifiedTerminalOutcome(candidate.merchantReference())) return;
            String operationRef = "QDR-" + candidate.attemptReference() + "-M" + minute;
            var result = merchantOperations.queryTransaction(new VnpayQueryDrCommand(operationRef,
                    candidate.merchantReference(), candidate.providerCreateDate(),
                    "Workshop deposit " + candidate.bookingCode(), merchantApiIpAddress));
            if (result == null || !result.responseSignatureVerified()
                    || !"00".equals(result.providerResponseCode())
                    || !"00".equals(result.transactionStatus())
                    || !candidate.merchantReference().equals(result.merchantReference())
                    || !candidate.amountVnd().equals(result.amountVnd())
                    || result.providerTransactionNumber() == null || result.providerTransactionNumber().isBlank()) continue;

            String evidence = "VNPAY-QDR-" + result.providerTransactionNumber();
            var applied = booking.applyVerifiedDepositPaymentEvidence(new ApplyVerifiedDepositPaymentEvidenceCommand(
                    candidate.bookingCode(), evidence, candidate.merchantReference(), candidate.amountVnd(), "VND",
                    operationRef, evidence));
            if (applied != null && applied.status() == BookingOutcomeStatus.REJECTED
                    && (applied.reason() == BookingRejectionReason.HOLD_MISSING_OR_EXPIRED
                    || applied.reason() == BookingRejectionReason.INVALID_STATE)) {
                booking.recordLateDepositEvidence(new RecordLateDepositEvidenceCommand(candidate.bookingCode(),
                        candidate.merchantReference(), evidence, result.providerTransactionNumber(),
                        candidate.amountVnd(), "VND"));
                return;
            }
            if (applied != null && applied.status() == BookingOutcomeStatus.ACCEPTED) return;
        }
    }
}
