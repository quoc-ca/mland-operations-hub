package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AuthoritativeOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.OutcomeKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.AvailabilityGuardPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.BranchVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.GroupedCheckoutRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.GroupedCheckout;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.PaymentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailCart;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailOrder;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailVariantHold;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailVariantHoldStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One branch order of a cash checkout settled on its own (Session 2026-11-13, D-011): paid at its counter, cancelled
 * by the Member, or expired unpaid. Only that branch order's inventory holds, cart lines and FE-06 settlement part are
 * touched; the checkout becomes terminal when its last branch order is decided. Callers hold the checkout row lock
 * (and the cart lock for cancellation or expiry) in their transaction.
 */
@Service
@RequiredArgsConstructor
public class RetailBranchPartService {
    private static final String OWNER = "readyringsales";

    private final GroupedCheckoutRepository checkouts;
    private final RetailVariantHoldLifecycleService holds;
    private final CatalogueSalesFacade catalogue;
    private final AvailabilityGuardPort guard;
    private final LoyaltyFacade loyalty;

    /** True when FE-06 accepted this cash checkout with one settlement part per branch order. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean acceptedWithParts(GroupedCheckout checkout) {
        if (!checkout.isCounterCash()) return false;
        try {
            BenefitResult result = loyalty.findBenefitResult(SourceKind.RETAIL, checkout.getId());
            return result != null && !result.parts().isEmpty();
        } catch (LoyaltyException noBenefitRow) {
            return false;
        }
    }

    /** Sells this branch order's holds, settles and earns its FE-06 part and records the sale. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void pay(GroupedCheckout checkout, RetailOrder order, String proof, Instant now) {
        requireUnpaid(order);
        Map<BranchVariant, Integer> quantities = RetailBranchOrderKeys.quantities(order);
        List<BranchVariant> keys = quantities.keySet().stream().sorted().toList();
        if (keys.isEmpty()) throw new RetailConflictException("Branch order has no items");
        translate(() -> catalogue.withLockedHistoricalBranchContext(checkout.getId(), keys, context -> {
            guard.withBranchGuard(keys, context.asOf(), snapshots -> {
                List<RetailVariantHold> locked = holds.requireBranchHolds(checkout.getId(), keys);
                Instant deadline = locked.getFirst().getExpiresAt();
                if (locked.stream().anyMatch(hold -> hold.getStatus() != RetailVariantHoldStatus.HELD
                    || !now.isBefore(hold.getExpiresAt()))) {
                    throw new RetailConflictException("The order is no longer held for cash payment");
                }
                locked.forEach(holds::settle);
                order.markPaid();
                checkout.resolveFromOrders();
                checkouts.saveAndFlush(checkout);
                var outcome = outcome(checkout, order, OutcomeKind.RETAIL_BRANCH_PAID, "paid-" + proof, now, deadline, proof);
                loyalty.settleAcceptedBenefits(outcome);
                loyalty.grantEarnedPoints(outcome);
                return null;
            });
            for (BranchVariant key : keys) {
                catalogue.recordAcceptedSale(UUID.nameUUIDFromBytes(("retail-paid:" + checkout.getId() + ":" + key.variantId()
                        + ":" + key.locationId()).getBytes(StandardCharsets.UTF_8)),
                    key.variantId(), key.locationId(), BigInteger.valueOf(quantities.get(key)));
            }
            return null;
        }));
    }

    /**
     * Ends an unpaid branch order: releases (cancel) or expires its holds, returns its lines to the cart and releases
     * its FE-06 part. For an expiry before the hold deadline nothing happens and -1 is returned; otherwise the number
     * of pieces that did not fit back into the cart.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int endUnpaid(GroupedCheckout checkout, RetailOrder order, RetailCart cart, boolean expiry, Instant now) {
        requireUnpaid(order);
        List<BranchVariant> keys = RetailBranchOrderKeys.quantities(order).keySet().stream().sorted().toList();
        if (keys.isEmpty()) throw new RetailConflictException("Branch order has no items");
        return translate(() -> catalogue.withLockedHistoricalBranchContext(checkout.getId(), keys, context ->
            guard.withBranchGuard(keys, context.asOf(), snapshots -> {
                List<RetailVariantHold> locked = holds.requireBranchHolds(checkout.getId(), keys);
                Instant deadline = locked.getFirst().getExpiresAt();
                if (locked.stream().anyMatch(hold -> hold.getStatus() != RetailVariantHoldStatus.HELD)) {
                    throw new RetailConflictException("Branch order inventory holds are inconsistent");
                }
                if (expiry && now.isBefore(deadline)) return -1;
                for (RetailVariantHold hold : locked) {
                    if (expiry) holds.expire(hold); else holds.release(hold);
                }
                int unrestored = RetailCartRestoration.restore(cart, List.of(order));
                if (expiry) order.expireUnpaid(); else order.cancel();
                checkout.resolveFromOrders();
                checkouts.saveAndFlush(checkout);
                OutcomeKind kind = expiry ? OutcomeKind.RETAIL_BRANCH_UNPAID_EXPIRY : OutcomeKind.RETAIL_BRANCH_CANCELLED_BEFORE_PAYMENT;
                String key = (expiry ? "expiry-" : "cancel-") + order.getId();
                var outcome = outcome(checkout, order, kind, key, now, deadline, key);
                loyalty.releaseHeldBenefits(outcome);
                loyalty.finishIneligibleEarn(outcome);
                return unrestored;
            })));
    }

    private static void requireUnpaid(RetailOrder order) {
        if (order.getPaymentState() != PaymentState.ACCEPTED_UNPAID) {
            throw new RetailConflictException("This branch order is no longer awaiting payment");
        }
    }

    private static AuthoritativeOutcome outcome(GroupedCheckout checkout, RetailOrder order, OutcomeKind kind, String key,
                                                Instant now, Instant deadline, String proof) {
        return new AuthoritativeOutcome(SourceKind.RETAIL, checkout.getId(), OWNER, key, checkout.getVersion(), kind,
            now, deadline, checkout.getId(), true, proof, order.getId());
    }

    private static <T> T translate(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (LoyaltyException exception) {
            if (exception.code() == LoyaltyErrorCode.DEPENDENCY_UNAVAILABLE || exception.code() == LoyaltyErrorCode.RETRYABLE_CONTENTION) {
                throw new RetailIntegrationUnavailableException("Loyalty integration is unavailable");
            }
            throw new RetailConflictException("Branch order benefits cannot be changed in their current state");
        }
    }
}
