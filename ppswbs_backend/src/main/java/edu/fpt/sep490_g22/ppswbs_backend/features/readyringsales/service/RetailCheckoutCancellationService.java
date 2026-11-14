package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AuthoritativeOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.OutcomeKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.AvailabilityGuardPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.BranchVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.GroupedCheckoutRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailCartRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailOrderRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailVariantHoldRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CheckoutDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.PaymentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailVariantHold;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!local")
@RequiredArgsConstructor
public class RetailCheckoutCancellationService {
    private final RetailCartRepository carts;
    private final GroupedCheckoutRepository checkouts;
    private final RetailVariantHoldRepository holdRepository;
    private final RetailVariantHoldLifecycleService holds;
    private final CatalogueSalesFacade catalogue;
    private final AvailabilityGuardPort guard;
    private final LoyaltyFacade loyalty;
    private final RetailCheckoutService views;
    private final RetailOrderRepository orders;
    private final RetailBranchPartService branchParts;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CheckoutDtos.CancellationView cancel(Long memberId, String checkoutId) {
        var cart = carts.findLockedByMemberAccountId(memberId)
            .orElseThrow(() -> new RetailNotFoundException("Retail cart does not exist"));
        var checkout = checkouts.lockOwned(checkoutId, memberId)
            .orElseThrow(() -> new RetailNotFoundException("Checkout does not exist"));
        if (checkout.getPaymentState() == PaymentState.CANCELLED) {
            if (checkout.getCancellationUnrestoredQuantity() == null) throw new RetailConflictException("Cancellation result is unavailable");
            return result(checkout, checkout.getCancellationUnrestoredQuantity());
        }
        if (checkout.getPaymentState() != PaymentState.ACCEPTED_UNPAID || checkout.getPaymentInitiatedAt() != null) {
            throw new RetailConflictException("Checkout cannot be cancelled after payment initiation");
        }
        if (branchParts.acceptedWithParts(checkout)) {
            // Cash with per-branch parts: cancel every branch order not yet paid at its counter.
            Instant now = Instant.now();
            int unrestored = 0;
            for (var order : checkout.getOrders().stream().filter(order -> order.getPaymentState() == PaymentState.ACCEPTED_UNPAID).toList()) {
                unrestored += branchParts.endUnpaid(checkout, order, cart, false, now);
            }
            return result(checkout, unrestored);
        }
        var quantities = RetailBranchOrderKeys.quantities(checkout);
        List<BranchVariant> branchKeys = quantities.keySet().stream().sorted().toList();
        List<Long> ids = RetailBranchOrderKeys.variantIds(quantities);
        if (ids.isEmpty()) throw new RetailConflictException("Checkout has no items");
        try {
            return catalogue.withLockedHistoricalBranchContext(checkoutId, branchKeys, context ->
            guard.withBranchGuard(branchKeys, context.asOf(), snapshots -> {
                List<RetailVariantHold> current = holds.requireBranchHolds(checkoutId, branchKeys);
                Instant deadline = current.getFirst().getExpiresAt();
                if (current.stream().anyMatch(hold -> !deadline.equals(hold.getExpiresAt()))) {
                    throw new RetailConflictException("Checkout hold deadlines do not match");
                }
                current.forEach(holds::release);
                int unrestored = RetailCartRestoration.restore(cart, checkout);
                checkout.cancelWithRestoration(unrestored);
                checkouts.saveAndFlush(checkout);
                var outcome = new AuthoritativeOutcome(SourceKind.RETAIL, checkoutId, "readyringsales",
                    "cancel-" + checkoutId, checkout.getVersion(), OutcomeKind.RETAIL_CANCELLED_BEFORE_PAYMENT,
                    Instant.now(), deadline, checkoutId, true, "cancel:" + checkoutId);
                loyalty.releaseHeldBenefits(outcome);
                loyalty.finishIneligibleEarn(outcome);
                return result(checkout, unrestored);
            }));
        } catch (LoyaltyException ex) {
            if (ex.code() == LoyaltyErrorCode.DEPENDENCY_UNAVAILABLE || ex.code() == LoyaltyErrorCode.RETRYABLE_CONTENTION) {
                throw new RetailIntegrationUnavailableException("Loyalty cancellation integration is unavailable");
            }
            throw new RetailConflictException("Checkout benefits cannot be released in their current state");
        }
    }

    /**
     * The Member cancels one cash branch order before Staff confirmed its payment (FR-08-046); other branch orders of
     * the checkout stay as they are. Checkouts accepted without parts are cancelled as a whole.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CheckoutDtos.CancellationView cancelBranchOrder(Long memberId, String orderId) {
        var owned = orders.findOwnedOrder(memberId, orderId)
            .orElseThrow(() -> new RetailNotFoundException("Retail order does not exist"));
        String checkoutId = owned.getGroupedCheckout().getId();
        var cart = carts.findLockedByMemberAccountId(memberId)
            .orElseThrow(() -> new RetailNotFoundException("Retail cart does not exist"));
        var checkout = checkouts.lockOwned(checkoutId, memberId)
            .orElseThrow(() -> new RetailNotFoundException("Checkout does not exist"));
        var order = checkout.getOrders().stream().filter(candidate -> candidate.getId().equals(orderId)).findFirst()
            .orElseThrow(() -> new RetailNotFoundException("Retail order does not exist"));
        if (order.getPaymentState() == PaymentState.CANCELLED) return result(checkout, 0);
        if (!branchParts.acceptedWithParts(checkout)) return cancel(memberId, checkoutId);
        if (checkout.getPaymentInitiatedAt() != null) throw new RetailConflictException("Checkout cannot be cancelled after payment initiation");
        int unrestored = branchParts.endUnpaid(checkout, order, cart, false, Instant.now());
        return result(checkout, unrestored);
    }

    private CheckoutDtos.CancellationView result(edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.GroupedCheckout checkout, int unrestored) {
        String message = unrestored == 0 ? "All items were restored to your cart."
            : "Some items could not be restored because the cart limit was reached.";
        return new CheckoutDtos.CancellationView(views.toView(checkout), unrestored, message);
    }
}
