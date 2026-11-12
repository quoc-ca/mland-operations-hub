package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentIngressOutcomeQueryPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationResult;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationsPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayQueryDrCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.GroupPaymentAttemptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.GroupPaymentRecoveryCandidate;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.PaymentAttemptStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingOutcomeStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.DepositPaymentEvidenceOutcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Asks VNPay what happened to a group's single payment at minutes 10 and 11 of the 12-minute hold
 * (003.1 FR-032, FR-038), for the case where no IPN arrived.
 *
 * <p>The two checks are the only ones: minute 10 is the moment the payment link dies
 * ({@code paymentDeadlineAt}) and minute 11 is one minute before the hold itself ends, which leaves the last
 * minute to confirm. Neither check extends the hold, and a pending or unknown answer is simply ignored. Once
 * a verified terminal IPN exists for the attempt, nothing is asked at all.
 */
@Service
@Slf4j
public class GroupPaymentQueryDrRecoveryScheduler {

    private final GroupPaymentAttemptRepository attempts;
    private final PaymentIngressOutcomeQueryPort ingressOutcomes;
    private final VnpayMerchantOperationsPort merchantOperations;
    private final GroupPaymentEvidenceService groupEvidence;
    private final String merchantApiIpAddress;

    public GroupPaymentQueryDrRecoveryScheduler(GroupPaymentAttemptRepository attempts,
                                                PaymentIngressOutcomeQueryPort ingressOutcomes,
                                                VnpayMerchantOperationsPort merchantOperations,
                                                GroupPaymentEvidenceService groupEvidence,
                                                @Value("${vnpay.merchant-api-ip-address:}") String merchantApiIpAddress) {
        this.attempts = attempts;
        this.ingressOutcomes = ingressOutcomes;
        this.merchantOperations = merchantOperations;
        this.groupEvidence = groupEvidence;
        this.merchantApiIpAddress = merchantApiIpAddress;
    }

    @Scheduled(fixedDelayString = "${workshop.booking.group-query-dr.poll-delay-ms:15000}")
    public void recoverEligibleGroupPayments() {
        Instant now = Instant.now();
        List<GroupPaymentRecoveryCandidate> candidates = attempts.findRecoveryCandidates(
                List.of(PaymentAttemptStatus.INITIATED, PaymentAttemptStatus.PENDING),
                BookingStatus.PENDING_PAYMENT);
        for (GroupPaymentRecoveryCandidate candidate : candidates) {
            try {
                recover(candidate, now);
            } catch (RuntimeException failure) {
                log.warn("Group QueryDR recovery failed for attemptReference={} ({})",
                        candidate.attemptReference(), failure.getClass().getSimpleName());
            }
        }
    }

    void recover(GroupPaymentRecoveryCandidate candidate, Instant now) {
        if (merchantApiIpAddress == null || merchantApiIpAddress.isBlank()) {
            log.warn("Group QueryDR recovery skipped: VNPAY_MERCHANT_API_IP_ADDRESS is not configured");
            return;
        }
        Instant holdExpiry = candidate.holdExpiresAt();
        if (holdExpiry == null || !holdExpiry.isAfter(now) || candidate.providerCreateDate() == null) {
            // Past the hold the answer belongs to the late-payment review of FR-040, not to this sweep.
            return;
        }
        if (ingressOutcomes.hasVerifiedTerminalOutcome(candidate.merchantReference())) {
            return;
        }
        // Minute 10 is the payment deadline itself; minute 11 is one minute before the hold ends.
        List<Checkpoint> checkpoints = List.of(
                new Checkpoint(10, candidate.paymentDeadlineAt()),
                new Checkpoint(11, holdExpiry.minus(Duration.ofMinutes(1))));
        for (Checkpoint checkpoint : checkpoints) {
            if (checkpoint.at() == null || now.isBefore(checkpoint.at())) {
                continue;
            }
            if (ingressOutcomes.hasVerifiedTerminalOutcome(candidate.merchantReference())) {
                return;
            }
            String operationReference = "QDR-" + candidate.attemptReference() + "-M" + checkpoint.minute();
            VnpayMerchantOperationResult result = merchantOperations.queryTransaction(new VnpayQueryDrCommand(
                    operationReference, candidate.merchantReference(), candidate.providerCreateDate(),
                    "Workshop group " + candidate.groupCode(), merchantApiIpAddress));
            if (!paidInFull(result, candidate)) {
                continue;
            }
            String evidence = "VNPAY-QDR-" + result.providerTransactionNumber();
            DepositPaymentEvidenceOutcome applied = groupEvidence.apply(candidate.groupCode(),
                    candidate.merchantReference(), candidate.amountVnd(), "VND", operationReference, evidence);
            if (applied != null && applied.status() == BookingOutcomeStatus.ACCEPTED) {
                log.info("Recovered group payment {} by QueryDR at minute {}", candidate.groupCode(),
                        checkpoint.minute());
                return;
            }
            // Rejected here means the group had already moved on (expired hold or mismatch); T094 owns that.
            return;
        }
    }

    /** Only a signed, successful, exactly matching answer counts as evidence. */
    private boolean paidInFull(VnpayMerchantOperationResult result, GroupPaymentRecoveryCandidate candidate) {
        return result != null
                && result.responseSignatureVerified()
                && "00".equals(result.providerResponseCode())
                && "00".equals(result.transactionStatus())
                && candidate.merchantReference().equals(result.merchantReference())
                && result.amountVnd() != null && result.amountVnd() == candidate.amountVnd()
                && result.providerTransactionNumber() != null && !result.providerTransactionNumber().isBlank();
    }

    private record Checkpoint(int minute, Instant at) {
    }
}
