package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AuthoritativeOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.OutcomeKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.AvailabilityGuardPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.BranchVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.GroupedCheckoutRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailVariantHoldRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CheckoutDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.PaymentState;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Test-only trusted payment simulator. There is deliberately no HTTP or gateway ingress. */
@Service
@Profile("retail-payment-sim")
@RequiredArgsConstructor
public class SimulatedRetailPaymentOutcomeService {
    private final GroupedCheckoutRepository checkouts;
    private final RetailVariantHoldRepository holdRepository;
    private final RetailVariantHoldLifecycleService holds;
    private final CatalogueSalesFacade catalogue;
    private final AvailabilityGuardPort guard;
    private final LoyaltyFacade loyalty;
    private final RetailCheckoutService views;
    private final RetailShipmentCreationService shipmentCreation;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CheckoutDtos.GroupedCheckoutView simulateVerifiedPayment(Long memberId, String checkoutId,
                                                                    BigDecimal amount, String currency, String proof) {
        if (memberId == null || checkoutId == null || amount == null || proof == null || proof.isBlank()
            || proof.length() > 110 || !"VND".equals(currency)) throw new RetailConflictException("Invalid simulated payment evidence");
        var checkout = checkouts.lockOwned(checkoutId, memberId)
            .orElseThrow(() -> new RetailNotFoundException("Checkout does not exist"));
        if (checkout.getPayableAmount().signum() <= 0 || checkout.getPayableAmount().compareTo(amount) != 0
            || !checkout.getCurrency().equals(currency)) throw new RetailConflictException("Payment amount or currency does not match");
        if (checkout.getPaymentState() == PaymentState.PAID) {
            if (!proof.equals(checkout.getPaymentProofRef())) throw new RetailConflictException("Payment evidence conflicts with the completed checkout");
            return views.toView(checkout);
        }
        if (checkout.getPaymentState() != PaymentState.PAYMENT_INITIATED || checkout.getPaymentInitiatedAt() == null) {
            throw new RetailConflictException("Payment cannot be accepted in the current state");
        }
        Map<BranchVariant, Integer> quantities = RetailBranchOrderKeys.quantities(checkout);
        List<BranchVariant> branchKeys = quantities.keySet().stream().sorted().toList();
        List<Long> ids = RetailBranchOrderKeys.variantIds(quantities);
        if (ids.isEmpty()) throw new RetailConflictException("Checkout has no items");
        return catalogue.withLockedHistoricalBranchContext(checkoutId, branchKeys, context -> {
            var view = guard.withBranchGuard(branchKeys, context.asOf(), snapshots -> {
                var lockedHolds = holds.requireBranchHolds(checkoutId, branchKeys);
                Instant now = Instant.now();
                Instant deadline = lockedHolds.getFirst().getExpiresAt();
                if (!now.isBefore(deadline) || lockedHolds.stream().anyMatch(hold -> !deadline.equals(hold.getExpiresAt()))) {
                    throw new RetailConflictException("Payment requires reconciliation after the checkout deadline");
                }
                lockedHolds.forEach(holds::settle);
                checkout.markPaidWithProof(proof);
                checkouts.saveAndFlush(checkout);
                shipmentCreation.createShipmentsForPaidCheckout(checkout);
                var outcome = new AuthoritativeOutcome(SourceKind.RETAIL, checkoutId, "readyringsales", "paid-" + proof,
                    checkout.getVersion(), OutcomeKind.RETAIL_FULLY_PAID, now, deadline, checkoutId, true, proof);
                loyalty.settleAcceptedBenefits(outcome);
                loyalty.grantEarnedPoints(outcome);
                return views.toView(checkout);
            });
            for (BranchVariant key : branchKeys) {
                UUID operationRef = UUID.nameUUIDFromBytes(("retail-paid:" + checkoutId + ":" + key.variantId() + ":" + key.locationId())
                    .getBytes(StandardCharsets.UTF_8));
                catalogue.recordAcceptedSale(operationRef, key.variantId(), key.locationId(), BigInteger.valueOf(quantities.get(key)));
            }
            return view;
        });
    }
}
