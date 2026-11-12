package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AuthoritativeOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.OutcomeKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.AvailabilityGuardPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.BranchVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.GroupedCheckoutRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailCashCollectionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailOrderRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailVariantHoldRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CheckoutDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.GroupedCheckout;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.PaymentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailCashCollection;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailOrder;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailVariantHoldStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Branch Staff confirm the exact counter cash payment of a store-pickup cash checkout (FR-08-045).
 *
 * <p>The confirmation pays the branch order once: when the checkout holds variant-level inventory (real
 * acceptance path) the holds are sold, the benefits settled and the earned points granted exactly as for a
 * verified VNPay payment; on the checkout-level local/demo path only the payment state changes, mirroring the
 * local payment adapter. Handover is recorded afterwards with the existing pickup operations.
 */
@Service
@RequiredArgsConstructor
public class RetailCashCollectionService {
    static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private final GroupedCheckoutRepository checkouts;
    private final RetailOrderRepository orders;
    private final RetailCashCollectionRepository collections;
    private final RetailVariantHoldRepository holdRepository;
    private final RetailVariantHoldLifecycleService holds;
    private final CatalogueSalesFacade catalogue;
    private final AvailabilityGuardPort guard;
    private final LoyaltyFacade loyalty;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CheckoutDtos.CashCollectionView confirm(CurrentAccountActor actor, String orderId, String amountReceived,
                                                   String idempotencyKey) {
        requireActiveStaffOrManager(actor);
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new RetailConflictException("A valid Idempotency-Key is required");
        }
        var replay = collections.findByConfirmedByAccountIdAndIdempotencyKey(actor.accountId(), idempotencyKey);
        if (replay.isPresent()) {
            if (!replay.get().getRetailOrderId().equals(orderId)) {
                throw new RetailConflictException("Idempotency-Key was used for another order");
            }
            return view(replay.get(), PaymentState.PAID.name());
        }
        RetailOrder unlocked = orders.findById(orderId)
            .orElseThrow(() -> new RetailNotFoundException("Retail order does not exist"));
        if (!actor.activeLocationIds().contains(unlocked.getLocationId())) {
            throw new RetailAccessDeniedException("Order is outside the assigned branches");
        }
        GroupedCheckout checkout = checkouts.lockById(unlocked.getGroupedCheckout().getId())
            .orElseThrow(() -> new RetailNotFoundException("Checkout does not exist"));
        var existing = collections.findByRetailOrderId(orderId);
        if (existing.isPresent()) return view(existing.get(), checkout.getPaymentState().name());

        RetailOrder order = checkout.getOrders().stream().filter(candidate -> candidate.getId().equals(orderId)).findFirst()
            .orElseThrow(() -> new RetailNotFoundException("Retail order does not exist"));
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (!checkout.isCounterCash()) throw new RetailConflictException("This order is not paid in cash at the counter");
        if (checkout.getPaymentState() != PaymentState.ACCEPTED_UNPAID) {
            throw new RetailConflictException("This order can no longer be paid in cash");
        }
        if (checkout.getOrders().size() != 1 || !"PICKUP".equals(order.getFulfilmentMethod())) {
            throw new RetailConflictException("Cash at the counter supports one store-pickup branch order");
        }
        if (checkout.getCounterPaymentDeadline() == null || !now.isBefore(checkout.getCounterPaymentDeadline())) {
            throw new RetailConflictException("The pickup deadline has passed; the order is no longer held");
        }
        BigDecimal received = parseAmount(amountReceived);
        BigDecimal due = order.getPayableAmount();
        if (due.signum() <= 0 || due.compareTo(checkout.getPayableAmount()) != 0) {
            throw new RetailConflictException("Order amount is inconsistent with its checkout");
        }
        if (received.compareTo(due) < 0) throw new RetailConflictException("Cash received is less than the amount due");

        var collection = new RetailCashCollection(checkout.getId(), order.getId(), order.getLocationId(),
            LocalDate.ofInstant(now, VIETNAM), due, received, actor.accountId(), now, idempotencyKey);
        String proof = "CASH-" + collection.getId();
        if (holdRepository.findByGroupedCheckoutId(checkout.getId()).isEmpty()) {
            // Checkout-level (local/demo) acceptance path: no variant holds or loyalty reservation exist.
            checkout.markPaidWithProof(proof);
            checkouts.saveAndFlush(checkout);
        } else {
            settleHeldCheckout(checkout, proof, now);
        }
        collections.saveAndFlush(collection);
        return view(collection, checkout.getPaymentState().name());
    }

    private void settleHeldCheckout(GroupedCheckout checkout, String proof, Instant now) {
        String checkoutId = checkout.getId();
        Map<BranchVariant, Integer> quantities = RetailBranchOrderKeys.quantities(checkout);
        List<BranchVariant> branchKeys = quantities.keySet().stream().sorted().toList();
        List<Long> ids = RetailBranchOrderKeys.variantIds(quantities);
        if (ids.isEmpty()) throw new RetailConflictException("Checkout has no items");
        try {
            catalogue.withLockedHistoricalBranchContext(checkoutId, branchKeys, context -> {
                guard.withBranchGuard(branchKeys, context.asOf(), snapshots -> {
                    var locked = holds.requireBranchHolds(checkoutId, branchKeys);
                    Instant deadline = locked.getFirst().getExpiresAt();
                    if (locked.stream().anyMatch(hold -> hold.getStatus() != RetailVariantHoldStatus.HELD
                        || !deadline.equals(hold.getExpiresAt()) || !now.isBefore(hold.getExpiresAt()))) {
                        throw new RetailConflictException("The order is no longer held for cash payment");
                    }
                    locked.forEach(holds::settle);
                    checkout.markPaidWithProof(proof);
                    checkouts.saveAndFlush(checkout);
                    var outcome = new AuthoritativeOutcome(SourceKind.RETAIL, checkoutId, "readyringsales", "paid-" + proof,
                        checkout.getVersion(), OutcomeKind.RETAIL_FULLY_PAID, now, deadline, checkoutId, true, proof);
                    loyalty.settleAcceptedBenefits(outcome);
                    loyalty.grantEarnedPoints(outcome);
                    return null;
                });
                for (BranchVariant key : branchKeys) {
                    catalogue.recordAcceptedSale(UUID.nameUUIDFromBytes(("retail-paid:" + checkoutId + ":" + key.variantId()
                            + ":" + key.locationId()).getBytes(StandardCharsets.UTF_8)),
                        key.variantId(), key.locationId(), BigInteger.valueOf(quantities.get(key)));
                }
                return null;
            });
        } catch (LoyaltyException exception) {
            if (exception.code() == LoyaltyErrorCode.DEPENDENCY_UNAVAILABLE || exception.code() == LoyaltyErrorCode.RETRYABLE_CONTENTION) {
                throw new RetailIntegrationUnavailableException("Loyalty integration is unavailable");
            }
            throw new RetailConflictException("Order benefits cannot be settled in their current state");
        }
    }

    private static BigDecimal parseAmount(String raw) {
        if (raw == null || !raw.matches("^[1-9][0-9]{0,15}$")) throw new RetailConflictException("Cash received must be a positive VND amount");
        return new BigDecimal(raw);
    }

    static void requireActiveStaffOrManager(CurrentAccountActor actor) {
        if (actor == null || actor.accountId() == null) throw new RetailUnauthorizedException("An authenticated staff actor is required");
        if (actor.status() != ActorStatus.ACTIVE || (actor.role() != ActorRole.STAFF && actor.role() != ActorRole.MANAGER)) {
            throw new RetailAccessDeniedException("Only active Staff or Manager can confirm counter cash");
        }
        if (actor.activeLocationIds().isEmpty()) throw new RetailAccessDeniedException("No assigned branch location for this actor");
    }

    static CheckoutDtos.CashCollectionView view(RetailCashCollection row, String paymentState) {
        return new CheckoutDtos.CashCollectionView(row.getId(), row.getRetailOrderId(), row.getLocationId(),
            money(row.getAppliedAmount()), money(row.getAmountReceived()), money(row.getChangeReturned()),
            row.getConfirmedByAccountId(), row.getConfirmedAt().toString(), row.getBusinessDate().toString(), paymentState);
    }

    static String money(BigDecimal amount) { return amount.stripTrailingZeros().toPlainString(); }
}
