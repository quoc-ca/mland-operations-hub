package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.*;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** QueryDR network calls run outside the source lock and are rechecked before applying. */
@Service @Profile("retail-vnpay & !local")
public class RetailQueryReconciliationService {
    private final PaymentOwnerRegistrationPort owners;
    private final PaymentIngressOutcomeQueryPort outcomes;
    private final VnpayMerchantOperationsPort merchant;
    private final GroupedCheckoutRepository checkouts;
    private final RetailVariantHoldRepository holds;
    private final RetailPaymentReconciliationRepository reconciliation;
    private final RetailConfirmedUnpaidExpiryService expiry;
    private final RetailPaymentEvidenceFacade evidence;
    private final TransactionTemplate transaction;
    private final String merchantIp;
    private final boolean vnpayEnabled;

    public RetailQueryReconciliationService(PaymentOwnerRegistrationPort owners, PaymentIngressOutcomeQueryPort outcomes,
            VnpayMerchantOperationsPort merchant, GroupedCheckoutRepository checkouts, RetailVariantHoldRepository holds,
            RetailPaymentReconciliationRepository reconciliation, RetailConfirmedUnpaidExpiryService expiry,
            RetailPaymentEvidenceFacade evidence, PlatformTransactionManager manager,
            @Value("${vnpay.merchant-api-ip-address:}") String merchantIp,
            @Value("${vnpay.enabled:false}") boolean vnpayEnabled) {
        this.owners = owners; this.outcomes = outcomes; this.merchant = merchant; this.checkouts = checkouts;
        this.holds = holds; this.reconciliation = reconciliation; this.expiry = expiry; this.evidence = evidence;
        this.transaction = new TransactionTemplate(manager); this.merchantIp = merchantIp; this.vnpayEnabled = vnpayEnabled;
    }

    record Query(String checkout, Long member, String merchant, long amount, String providerDate, String operation) { }

    public void reconcile(Long member, String checkout, Instant now) {
        String reference = "RETAIL-" + checkout;
        if (expiry.expire(member, checkout, reference)) return;
        Query query = transaction.execute(status -> {
            var owner = owners.lockRetailPayment(reference).orElse(null);
            if (owner == null || !checkout.equals(owner.checkoutReference()) || outcomes.hasVerifiedTerminalOutcome(reference)) return null;
            var current = checkouts.lockById(checkout).orElse(null);
            if (current == null || !member.equals(current.getMemberAccountId()) || current.getPaymentState() != PaymentState.PAYMENT_INITIATED) return null;
            var currentHolds = holds.findByGroupedCheckoutId(checkout);
            if (currentHolds.isEmpty()) return null;
            Instant deadline = currentHolds.stream().map(RetailVariantHold::getExpiresAt).min(Instant::compareTo).orElseThrow();
            if (now.isBefore(deadline)) return null;
            long minutes = Duration.between(deadline, now).toMinutes();
            if (minutes >= 60) {
                if (reconciliation.findByMerchantReference(reference).isEmpty()) {
                    reconciliation.saveAndFlush(RetailPaymentReconciliation.builder().checkoutId(checkout)
                            .merchantReference(reference).amountVnd(owner.amountVnd()).currencyCode(owner.currencyCode())
                            .reason("QUERY_UNRESOLVED").createdAt(now).build());
                }
                return null;
            }
            if (!vnpayEnabled || merchantIp == null || merchantIp.isBlank() || owner.providerCreateDate() == null) return null;
            String operation = "RQ-" + checkout + "-" + (minutes / 5);
            return new Query(checkout, member, reference, owner.amountVnd(), owner.providerCreateDate(), operation);
        });
        if (query == null) return;
        var result = merchant.queryTransaction(new VnpayQueryDrCommand(query.operation(), query.merchant(),
                query.providerDate(), "Retail checkout " + query.checkout(), merchantIp));
        if (result == null || !result.responseSignatureVerified() || !"00".equals(result.providerResponseCode())
                || !query.merchant().equals(result.merchantReference()) || result.amountVnd() == null
                || result.amountVnd() != query.amount()) return;
        if ("02".equals(result.transactionStatus())) {
            expiry.expire(query.member(), query.checkout(), query.merchant(), query.operation());
        } else if ("00".equals(result.transactionStatus()) && result.providerTransactionNumber() != null
                && !result.providerTransactionNumber().isBlank()) {
            transaction.executeWithoutResult(status -> {
                var owner = owners.lockRetailPayment(query.merchant()).orElseThrow();
                if (!query.checkout().equals(owner.checkoutReference()) || owner.amountVnd() != query.amount())
                    throw new RetailConflictException("Payment owner changed");
                evidence.applyVerifiedPayment(new RetailVerifiedPayment(query.checkout(), query.merchant(),
                        result.providerTransactionNumber(), query.amount(), "VND"));
            });
        }
    }
}
