package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Only a verified terminal failure can release a started VNPay checkout. */
@Service @Profile("!local") @RequiredArgsConstructor
public class RetailConfirmedUnpaidExpiryService {
    private final PaymentOwnerRegistrationPort owners;
    private final PaymentIngressOutcomeQueryPort outcomes;
    private final VerifiedQueryOutcomePort queries;
    private final RetailCartRepository carts;
    private final GroupedCheckoutRepository checkouts;
    private final RetailVariantHoldRepository holdRepository;
    private final RetailVariantHoldLifecycleService holds;
    private final CatalogueSalesFacade catalogue;
    private final AvailabilityGuardPort guard;
    private final LoyaltyFacade loyalty;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean expire(Long memberId, String checkoutId, String merchantReference) {
        return expire(memberId, checkoutId, merchantReference, null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean expire(Long memberId, String checkoutId, String merchantReference, String queryReference) {
        var owner = owners.lockRetailPayment(merchantReference).orElse(null);
        if (owner == null || !owner.checkoutReference().equals(checkoutId) || !"VND".equals(owner.currencyCode())) return false;
        var evidence = outcomes.findVerifiedTerminalOutcome(merchantReference).orElse(null);
        String proof;
        if (evidence != null) {
            if (!"02".equals(evidence.transactionStatus()) || !"VND".equals(evidence.currencyCode())
                    || evidence.amountVnd() != owner.amountVnd() || evidence.evidenceReference() == null
                    || evidence.evidenceReference().isBlank()) return false;
            proof = evidence.evidenceReference();
        } else {
            if (queryReference == null) return false;
            var query = queries.findVerifiedQuery(queryReference).orElse(null);
            if (query == null || !query.responseSignatureVerified() || !"00".equals(query.providerResponseCode())
                    || !"02".equals(query.transactionStatus()) || !merchantReference.equals(query.merchantReference())
                    || query.amountVnd() == null || query.amountVnd() != owner.amountVnd()) return false;
            proof = "QDR-" + queryReference;
        }
        var cart = carts.findLockedByMemberAccountId(memberId)
                .orElseThrow(() -> new RetailNotFoundException("Retail cart does not exist"));
        var checkout = checkouts.lockOwned(checkoutId, memberId)
                .orElseThrow(() -> new RetailNotFoundException("Checkout does not exist"));
        if (checkout.getPaymentState() != PaymentState.PAYMENT_INITIATED || checkout.getPaymentInitiatedAt() == null
                || checkout.getPayableAmount().compareTo(BigDecimal.valueOf(owner.amountVnd())) != 0) return false;
        var quantities = RetailBranchOrderKeys.quantities(checkout);
        List<BranchVariant> branchKeys = quantities.keySet().stream().sorted().toList();
        List<Long> ids = RetailBranchOrderKeys.variantIds(quantities);
        if (ids.isEmpty()) throw new RetailConflictException("Checkout has no items");
        return catalogue.withLockedHistoricalBranchContext(checkoutId, branchKeys, context ->
                guard.withBranchGuard(branchKeys, context.asOf(), snapshots -> {
                    var current = holds.requireBranchHolds(checkoutId, branchKeys);
                    Instant deadline = current.getFirst().getExpiresAt();
                    if (current.stream().anyMatch(h -> !deadline.equals(h.getExpiresAt()) || h.getStatus() != RetailVariantHoldStatus.HELD))
                        throw new RetailConflictException("Checkout inventory holds are inconsistent");
                    Instant now = Instant.now();
                    if (now.isBefore(deadline)) return false;
                    current.forEach(holds::expire);
                    RetailCartRestoration.restore(cart, checkout);
                    checkout.expireUnpaid();
                    checkouts.saveAndFlush(checkout);
                    var outcome = new AuthoritativeOutcome(SourceKind.RETAIL, checkoutId, "readyringsales", "expiry-" + checkoutId,
                            checkout.getVersion(), OutcomeKind.UNPAID_EXPIRY_CONFIRMED, now, deadline, checkoutId, true, proof);
                    loyalty.releaseHeldBenefits(outcome);
                    loyalty.finishIneligibleEarn(outcome);
                    return true;
                }));
    }
}
