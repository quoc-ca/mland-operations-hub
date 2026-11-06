package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class RetailPaymentEvidenceService implements RetailPaymentEvidenceFacade {
    private final GroupedCheckoutRepository checkouts;
    private final RetailVariantHoldRepository holdRepository;
    private final RetailVariantHoldLifecycleService holds;
    private final RetailPaymentReconciliationRepository reconciliation;
    private final CatalogueSalesFacade catalogue;
    private final AvailabilityGuardPort guard;
    private final LoyaltyFacade loyalty;
    private final RetailShipmentCreationService shipmentCreation;

    @Override @Transactional(isolation = Isolation.READ_COMMITTED)
    public void applyVerifiedPayment(RetailVerifiedPayment payment) {
        if (payment == null || payment.checkoutId() == null || payment.merchantReference() == null
                || payment.merchantReference().isBlank() || payment.providerTransaction() == null
                || payment.providerTransaction().isBlank() || payment.amountVnd() <= 0 || !"VND".equals(payment.currency()))
            throw new RetailConflictException("Invalid verified payment evidence");
        var checkout = checkouts.lockById(payment.checkoutId())
                .orElseThrow(() -> new RetailNotFoundException("Checkout does not exist"));
        String proof = "VNPAY-" + payment.providerTransaction();
        if (checkout.getPayableAmount().compareTo(BigDecimal.valueOf(payment.amountVnd())) != 0
                || !checkout.getCurrency().equals(payment.currency()))
            throw new RetailConflictException("Payment amount or currency does not match");
        if (checkout.getPaymentState() == PaymentState.PAID && proof.equals(checkout.getPaymentProofRef())) return;
        var existing = reconciliation.findByMerchantReference(payment.merchantReference());
        if (existing.isPresent()) {
            var row = existing.get();
            if ("QUERY_UNRESOLVED".equals(row.getReason()) && row.getProviderTransaction() == null
                    && row.getCheckoutId().equals(payment.checkoutId()) && row.getAmountVnd().equals(payment.amountVnd())) {
                row.recordLatePayment(payment.providerTransaction());
                reconciliation.saveAndFlush(row);
                return;
            }
            if (!row.getCheckoutId().equals(payment.checkoutId()) || !row.getProviderTransaction().equals(payment.providerTransaction())
                    || !row.getAmountVnd().equals(payment.amountVnd())) throw new RetailConflictException("Payment evidence conflicts");
            return;
        }
        var current = holdRepository.findByGroupedCheckoutId(payment.checkoutId());
        Instant now = Instant.now();
        boolean valid = checkout.getPaymentState() == PaymentState.PAYMENT_INITIATED
                && checkout.getPaymentInitiatedAt() != null && !current.isEmpty()
                && current.stream().allMatch(h -> h.getStatus() == RetailVariantHoldStatus.HELD && now.isBefore(h.getExpiresAt()))
                && current.stream().map(RetailVariantHold::getExpiresAt).distinct().count() == 1;
        if (!valid) {
            reconciliation.saveAndFlush(RetailPaymentReconciliation.builder().checkoutId(payment.checkoutId())
                    .merchantReference(payment.merchantReference()).providerTransaction(payment.providerTransaction())
                    .amountVnd(payment.amountVnd()).currencyCode(payment.currency()).reason("LATE_OR_INVALID_STATE")
                    .createdAt(now).build());
            return;
        }
        Map<Long, Integer> quantities = new HashMap<>();
        checkout.getOrders().forEach(o -> o.getItems().forEach(i -> quantities.merge(i.getVariantId(), i.getQuantity(), Integer::sum)));
        List<Long> ids = quantities.keySet().stream().sorted().toList();
        if (ids.isEmpty()) throw new RetailConflictException("Checkout has no items");
        catalogue.withLockedHistoricalContext(payment.checkoutId(), ids, context -> {
            guard.withGuard(ids, context.asOf(), snapshots -> {
                var locked = ids.stream().map(id -> holdRepository.findByGroupedCheckoutIdAndVariantId(payment.checkoutId(), id)
                        .orElseThrow(() -> new RetailConflictException("Checkout inventory hold is missing"))).toList();
                Instant deadline = locked.getFirst().getExpiresAt();
                if (locked.stream().anyMatch(h -> h.getStatus() != RetailVariantHoldStatus.HELD
                        || !deadline.equals(h.getExpiresAt()) || !now.isBefore(h.getExpiresAt())))
                    throw new RetailConflictException("Checkout hold changed during payment");
                locked.forEach(holds::settle);
                checkout.markPaidWithProof(proof);
                checkouts.saveAndFlush(checkout);
                shipmentCreation.createShipmentsForPaidCheckout(checkout);
                var outcome = new AuthoritativeOutcome(SourceKind.RETAIL, payment.checkoutId(), "readyringsales", "paid-" + proof,
                        checkout.getVersion(), OutcomeKind.RETAIL_FULLY_PAID, now, deadline, payment.checkoutId(), true, proof);
                loyalty.settleAcceptedBenefits(outcome);
                loyalty.grantEarnedPoints(outcome);
                return null;
            });
            for (Long id : ids) catalogue.recordAcceptedSale(UUID.nameUUIDFromBytes(
                    ("retail-paid:" + payment.checkoutId() + ":" + id).getBytes(StandardCharsets.UTF_8)),
                    id, BigInteger.valueOf(quantities.get(id)));
            return null;
        });
    }
}
