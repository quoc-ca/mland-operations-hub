package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentIngressOutcomeQueryPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VerifiedTerminalPaymentOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationResult;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationsPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayQueryDrCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFolioCollectionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.FolioCollectionMethod;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.FolioCollectionStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFolioCollection;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.ApplyVerifiedSettlementPaymentEvidenceCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Reconciles pending VNPAY parts of ticket folios with what VNPay itself says (003.3 T109, FR-031, FR-032,
 * FR-034), so a part the customer never paid frees the balance for another method without anyone guessing.
 *
 * <ul>
 *   <li>A signed IPN already received for the part with transaction status {@code 02} fails the part.</li>
 *   <li>Without a terminal IPN, once the checkout has expired, a signed QueryDR answer about exactly this
 *       merchant reference and amount decides: {@code 02} fails the part; {@code 00} with a provider
 *       transaction number is applied as verified payment evidence, under the same evidence reference the IPN
 *       would use, so a later IPN is a replay. Every other answer (pending, unknown, unsigned, mismatched)
 *       changes nothing.</li>
 *   <li>QueryDR is asked at most once per {@link #QUERY_INTERVAL} and only for {@link #QUERY_WINDOW} after the
 *       checkout expired, the cadence 003.3 already uses for refund reconciliation; after that the part is left
 *       to the Manager failure route.</li>
 * </ul>
 * Closing the QR or the screen is never treated as failure.
 */
@Service
@Slf4j
public class FolioGatewayReconciliationScheduler {

    static final Duration QUERY_INTERVAL = Duration.ofMinutes(5);
    static final Duration QUERY_WINDOW = Duration.ofHours(24);
    private static final String PAYMENT_FAILED = "02";
    private static final String PAYMENT_SUCCEEDED = "00";

    private final SettlementFolioCollectionRepository collections;
    private final PaymentIngressOutcomeQueryPort ingressOutcomes;
    private final VnpayMerchantOperationsPort merchantOperations;
    private final FolioCollectionService folioCollections;
    private final FinalPaymentService finalPayments;
    private final String merchantApiIpAddress;

    public FolioGatewayReconciliationScheduler(SettlementFolioCollectionRepository collections,
                                               PaymentIngressOutcomeQueryPort ingressOutcomes,
                                               VnpayMerchantOperationsPort merchantOperations,
                                               FolioCollectionService folioCollections,
                                               FinalPaymentService finalPayments,
                                               @Value("${vnpay.merchant-api-ip-address:}") String merchantApiIpAddress) {
        this.collections = collections;
        this.ingressOutcomes = ingressOutcomes;
        this.merchantOperations = merchantOperations;
        this.folioCollections = folioCollections;
        this.finalPayments = finalPayments;
        this.merchantApiIpAddress = merchantApiIpAddress;
    }

    @Scheduled(fixedDelayString = "${workshop.settlement.folio-gateway-reconcile.poll-delay-ms:60000}")
    public void reconcilePendingGatewayParts() {
        Instant now = Instant.now();
        for (SettlementFolioCollection candidate : collections.findTop100ByMethodAndStatusOrderByIdAsc(
                FolioCollectionMethod.VNPAY, FolioCollectionStatus.PENDING_VERIFICATION)) {
            try {
                reconcile(candidate, now);
            } catch (RuntimeException failure) {
                log.warn("Folio gateway reconciliation failed for collection {} ({})",
                        candidate.getCollectionReference(), failure.getClass().getSimpleName());
            }
        }
    }

    void reconcile(SettlementFolioCollection candidate, Instant now) {
        String merchant = candidate.getExternalReference();
        if (merchant == null || merchant.isBlank()) {
            return;
        }
        Optional<VerifiedTerminalPaymentOutcome> ipn = ingressOutcomes.findVerifiedTerminalOutcome(merchant);
        if (ipn.isPresent()) {
            // A successful IPN is dispatched by the ingress itself; only a reported failure is acted on here.
            if (PAYMENT_FAILED.equals(ipn.get().transactionStatus())
                    && ipn.get().amountVnd() == candidate.getAmountVnd()) {
                folioCollections.failGatewayCollectionFromProvider(merchant, "VNPAY_IPN_TRANSACTION_STATUS_02",
                        ipn.get().evidenceReference());
            }
            return;
        }
        Instant expiresAt = candidate.getPaymentExpiresAt();
        if (expiresAt == null || candidate.getProviderCreateDate() == null || now.isBefore(expiresAt)
                || now.isAfter(expiresAt.plus(QUERY_WINDOW))) {
            return;
        }
        if (merchantApiIpAddress == null || merchantApiIpAddress.isBlank()) {
            log.warn("Folio gateway QueryDR skipped: VNPAY_MERCHANT_API_IP_ADDRESS is not configured");
            return;
        }
        long slot = Duration.between(expiresAt, now).toMinutes() / QUERY_INTERVAL.toMinutes();
        String operationReference = "FQDR-" + candidate.getCollectionReference() + "-" + slot;
        VnpayMerchantOperationResult result = merchantOperations.queryTransaction(new VnpayQueryDrCommand(
                operationReference, merchant, candidate.getProviderCreateDate(),
                "Workshop folio " + candidate.getBookingReference(), merchantApiIpAddress));
        if (!aboutThisPart(result, candidate)) {
            return;
        }
        if (PAYMENT_FAILED.equals(result.transactionStatus())) {
            folioCollections.failGatewayCollectionFromProvider(merchant, "VNPAY_QUERYDR_TRANSACTION_STATUS_02",
                    operationReference);
        } else if (PAYMENT_SUCCEEDED.equals(result.transactionStatus())
                && result.providerTransactionNumber() != null && !result.providerTransactionNumber().isBlank()) {
            // Same evidence reference as the ingress builds from the IPN, so the two never count the money twice.
            String evidence = "VNPAY-" + result.providerTransactionNumber();
            var outcome = finalPayments.apply(new ApplyVerifiedSettlementPaymentEvidenceCommand(
                    candidate.getBookingReference(), evidence, merchant, "FINAL_PAYMENT", candidate.getAmountVnd(),
                    "VND", result.providerTransactionNumber(), candidate.getProviderCreateDate(), evidence,
                    evidence));
            log.info("Folio collection {} reconciled as paid by QueryDR ({})", candidate.getCollectionReference(),
                    outcome == null ? "no outcome" : outcome.status());
        }
    }

    /** Only a signed, successful query about exactly this merchant reference and amount counts. */
    private static boolean aboutThisPart(VnpayMerchantOperationResult result, SettlementFolioCollection candidate) {
        return result != null
                && result.responseSignatureVerified()
                && "00".equals(result.providerResponseCode())
                && candidate.getExternalReference().equals(result.merchantReference())
                && result.amountVnd() != null && result.amountVnd() == candidate.getAmountVnd();
    }
}
