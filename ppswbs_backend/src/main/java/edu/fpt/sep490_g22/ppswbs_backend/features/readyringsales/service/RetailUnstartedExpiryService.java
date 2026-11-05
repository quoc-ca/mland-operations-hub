package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AuthoritativeOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.OutcomeKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.AvailabilityGuardPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.GroupedCheckoutRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailCartRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailVariantHoldRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.PaymentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailVariantHoldStatus;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Expires only unpaid checkouts for which no payment was initiated. */
@Service
@Profile("!local")
@RequiredArgsConstructor
public class RetailUnstartedExpiryService {
    private final RetailCartRepository carts;
    private final GroupedCheckoutRepository checkouts;
    private final RetailVariantHoldRepository holdRepository;
    private final RetailVariantHoldLifecycleService holds;
    private final CatalogueSalesFacade catalogue;
    private final AvailabilityGuardPort guard;
    private final LoyaltyFacade loyalty;

    /** Caller must already hold the checkout lock; do not start a payment after its hold deadline. */
    public void requirePaymentBeforeDeadline(String checkoutId) {
        var current = holdRepository.findByGroupedCheckoutId(checkoutId);
        if (current.isEmpty() || current.stream().anyMatch(hold -> hold.getStatus() != RetailVariantHoldStatus.HELD
            || !Instant.now().isBefore(hold.getExpiresAt()))) {
            throw new RetailConflictException("Checkout hold expired or is unavailable; payment cannot be initiated");
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean expire(Long memberId, String checkoutId) {
        var cart = carts.findLockedByMemberAccountId(memberId)
            .orElseThrow(() -> new RetailNotFoundException("Retail cart does not exist"));
        var checkout = checkouts.lockOwned(checkoutId, memberId)
            .orElseThrow(() -> new RetailNotFoundException("Checkout does not exist"));
        if (checkout.getPaymentState() != PaymentState.ACCEPTED_UNPAID || checkout.getPaymentInitiatedAt() != null) return false;
        List<Long> ids = checkout.getOrders().stream().flatMap(order -> order.getItems().stream())
            .map(item -> item.getVariantId()).distinct().sorted().toList();
        if (ids.isEmpty()) throw new RetailConflictException("Checkout has no items");
        return catalogue.withLockedHistoricalContext(checkoutId, ids, context ->
            guard.withGuard(ids, context.asOf(), snapshots -> {
                var current = ids.stream().map(id -> holdRepository.findByGroupedCheckoutIdAndVariantId(checkoutId, id)
                    .orElseThrow(() -> new RetailConflictException("Checkout inventory hold is missing"))).toList();
                Instant deadline = current.getFirst().getExpiresAt();
                if (current.stream().anyMatch(hold -> !deadline.equals(hold.getExpiresAt())
                    || hold.getStatus() != RetailVariantHoldStatus.HELD)) {
                    throw new RetailConflictException("Checkout inventory holds are inconsistent");
                }
                Instant now = Instant.now();
                if (now.isBefore(deadline)) return false;
                current.forEach(holds::expire);
                RetailCartRestoration.restore(cart, checkout);
                checkout.expireUnpaid();
                checkouts.saveAndFlush(checkout);
                var outcome = new AuthoritativeOutcome(SourceKind.RETAIL, checkoutId, "readyringsales",
                    "expiry-" + checkoutId, checkout.getVersion(), OutcomeKind.UNPAID_EXPIRY_CONFIRMED,
                    now, deadline, checkoutId, true, "uninitiated:" + checkoutId);
                loyalty.releaseHeldBenefits(outcome);
                loyalty.finishIneligibleEarn(outcome);
                return true;
            }));
    }
}
