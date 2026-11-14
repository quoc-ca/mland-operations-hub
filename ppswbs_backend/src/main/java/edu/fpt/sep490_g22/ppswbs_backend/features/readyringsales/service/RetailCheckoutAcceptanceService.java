package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CheckoutDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/** Internal pickup acceptance. Do not expose until payment and hold-release lifecycle is available. */
@Service
@Profile("!local")
@RequiredArgsConstructor
public class RetailCheckoutAcceptanceService {
    private final RetailCartRepository carts;
    private final RetailCheckoutQuoteRepository quotes;
    private final GroupedCheckoutRepository checkouts;
    private final CatalogueSalesFacade catalogue;
    private final AvailabilityGuardPort guard;
    private final RetailVariantHoldLifecycleService holds;
    private final LoyaltyFacade loyalty;
    private final RetailCheckoutQuoteValidator validator;
    private final RetailCheckoutService views;
    private final RetailShipmentCreationService shipmentCreation;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CheckoutDtos.GroupedCheckoutView accept(Long memberId, String idempotencyKey,
                                                    CheckoutDtos.CreateGroupedCheckoutRequest request) {
        if (memberId == null || idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128
            || request == null || request.benefitPreviewId() == null || !Boolean.TRUE.equals(request.benefitTermsAccepted())) {
            throw new RetailConflictException("Invalid checkout request");
        }
        RetailCart cart = carts.findLockedByMemberAccountId(memberId)
            .orElseThrow(() -> new RetailConflictException("Retail cart is empty"));
        RetailCheckoutQuote quote = quotes.lockOwned(request.benefitPreviewId(), memberId)
            .orElseThrow(() -> new RetailNotFoundException("Checkout quote does not exist"));
        var existing = checkouts.findByMemberAccountIdAndIdempotencyKey(memberId, idempotencyKey);
        if (existing.isPresent()) {
            if (!Objects.equals(quote.getAcceptedCheckoutId(), existing.get().getId()) || !sameSelectionAndBranches(quote, request)) {
                throw new RetailConflictException("Checkout key was used for different terms");
            }
            return views.toView(existing.get());
        }
        if (!"OPEN".equals(quote.getStatus())) throw new RetailConflictException("Checkout quote is already accepted");
        List<BranchVariant> branchVariants = quote.getBranches().stream().flatMap(branch -> branch.getItems().stream()
                .map(item -> new BranchVariant(item.getVariantId(), branch.getLocationId())))
            .distinct().sorted().toList();
        if (branchVariants.isEmpty()) throw new RetailConflictException("Checkout quote is empty");
        try {
            return catalogue.withLockedBranchSaleContext(branchVariants,
                context -> acceptLocked(memberId, idempotencyKey, request, cart, quote, context, branchVariants));
        } catch (CatalogueSalesException exception) {
            if (exception.reason() == CatalogueSalesException.Reason.NOT_FOUND || exception.reason() == CatalogueSalesException.Reason.NOT_FOR_SALE) {
                throw new RetailConflictException("Product terms changed; review a new quote");
            }
            throw new RetailIntegrationUnavailableException("Catalogue checkout integration is unavailable");
        } catch (LoyaltyException exception) {
            if (exception.code() == LoyaltyErrorCode.DEPENDENCY_UNAVAILABLE || exception.code() == LoyaltyErrorCode.RETRYABLE_CONTENTION) {
                throw new RetailIntegrationUnavailableException("Loyalty checkout integration is unavailable");
            }
            throw new RetailConflictException("Benefit terms changed; review a new quote");
        }
    }

    private CheckoutDtos.GroupedCheckoutView acceptLocked(Long memberId, String key, CheckoutDtos.CreateGroupedCheckoutRequest request,
        RetailCart cart, RetailCheckoutQuote quote, SaleContext context, List<BranchVariant> branchVariants) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        validator.validate(quote, cart, request, context, now);
        Map<Long, SaleVariantFact> facts = context.variants().stream().collect(Collectors.toMap(SaleVariantFact::variantId, fact -> fact));
        Map<Long, Integer> quantitiesByVariant = new HashMap<>();
        Map<BranchVariant, Integer> quantitiesByBranch = new HashMap<>();
        quote.getBranches().forEach(branch -> branch.getItems().forEach(item -> {
            quantitiesByVariant.merge(item.getVariantId(), item.getQuantity(), Integer::sum);
            quantitiesByBranch.merge(new BranchVariant(item.getVariantId(), branch.getLocationId()), item.getQuantity(), Integer::sum);
        }));
        List<Long> variantIds = quantitiesByVariant.keySet().stream().sorted().toList();
        Map<BranchVariant, BranchStockFact> branchStocks = context.branchStocks().stream()
            .collect(Collectors.toMap(BranchStockFact::key, fact -> fact));
        var checkout = new GroupedCheckout(memberId, key, quote.getCartVersion(), decimal(quote.getMerchandiseSubtotal()),
            decimal(quote.getShippingTotal()), decimal(quote.getProgramDiscount().add(quote.getPointsDiscount())));
        BigInteger remainingDiscount = quote.getProgramDiscount().add(quote.getPointsDiscount());
        List<RetailCheckoutQuoteBranch> sortedBranches = quote.getBranches().stream()
            .sorted(Comparator.comparing(RetailCheckoutQuoteBranch::getLocationId)).toList();
        // Shares decided at preview (voucher only on its branches); quotes made before V124 split by branch amount.
        boolean allocated = sortedBranches.stream().allMatch(branch -> branch.getDiscountAmount() != null)
            && sortedBranches.stream().map(RetailCheckoutQuoteBranch::getDiscountAmount).reduce(BigInteger.ZERO, BigInteger::add)
                .equals(remainingDiscount);
        List<BigInteger> subtotals = sortedBranches.stream().map(RetailCheckoutQuoteBranch::getSubtotal).toList();
        List<BigInteger> shares = allocated
            ? sortedBranches.stream().map(RetailCheckoutQuoteBranch::getDiscountAmount).toList()
            : RetailDiscountAllocation.split(remainingDiscount, subtotals, subtotals);
        List<OrderItem> items = new ArrayList<>();
        int branchIndex = 0;
        for (var branch : sortedBranches) {
            BigInteger branchDiscount = shares.get(branchIndex++);
            if (branchDiscount.compareTo(branch.getSubtotal()) > 0) throw new RetailConflictException("Quote discount is invalid");
            remainingDiscount = remainingDiscount.subtract(branchDiscount);
            var order = new RetailOrder(checkout, branch.getLocationId(), branch.getFulfilmentMethod(), decimal(branch.getSubtotal()),
                decimal(branch.getShippingCharge()), decimal(branchDiscount));
            if (branch.getRecipientName() != null) {
                order.setRecipientSnapshot(branch.getRecipientName(), branch.getRecipientPhone(), branch.getRecipientAddress(),
                    branch.getRecipientProvince(), branch.getRecipientDistrict(), branch.getRecipientWard());
            }
            for (var item : branch.getItems()) {
                var saved = new OrderItem(order, item.getVariantId(), item.getQuantity(), decimal(item.getUnitPrice()));
                order.addItem(saved);
                items.add(saved);
            }
            checkout.addOrder(order);
        }
        if (remainingDiscount.signum() != 0) throw new RetailConflictException("Quote discount is invalid");
        checkouts.saveAndFlush(checkout);
        // FE-02 must not be called from the FE-08 guard callback. Retain media after catalogue locking but before guard acquisition.
        for (OrderItem item : items) {
            catalogue.retainMediaReferences(item.getId(), facts.get(item.getVariantId()).imageMediaIds());
        }
        RetailPaymentMethod paymentMethod = RetailPaymentMethods.parse(quote.getPaymentMethod());
        Instant deadline = RetailPaymentMethods.holdDeadline(paymentMethod, now);
        if (paymentMethod == RetailPaymentMethod.CASH_AT_COUNTER) {
            // Cash is collected at the branch when the goods are picked up; no VNPay attempt is created.
            checkout.useCounterCash(deadline);
            checkouts.saveAndFlush(checkout);
        }
        boolean zeroTotal = quote.getPayableTotal().signum() == 0;
        CheckoutDtos.GroupedCheckoutView view = guard.withBranchGuard(branchVariants, context.asOf(), snapshots -> {
            List<RetailVariantHold> createdHolds = new ArrayList<>();
            for (BranchVariant keyAtBranch : branchVariants) {
                var stock = branchStocks.get(keyAtBranch);
                var snapshot = snapshots.get(keyAtBranch);
                if (stock == null || snapshot == null || !stock.saleEnabled() || !stock.branchActive()
                    || !stock.assignedToBranches() || stock.remainingUnsold().subtract(snapshot.heldQuantity())
                    .compareTo(BigInteger.valueOf(quantitiesByBranch.get(keyAtBranch))) < 0) {
                    throw new RetailConflictException("Product quantity is no longer available");
                }
            }
            // One hold per size x branch (FR-053, T124): a hold at one branch never reduces another branch.
            for (BranchVariant keyAtBranch : branchVariants) {
                createdHolds.add(holds.createHeld(checkout, keyAtBranch.variantId(), keyAtBranch.locationId(),
                    quantitiesByBranch.get(keyAtBranch), deadline));
            }
            // Each line carries its cart branch so FE-06 can match branch-limited product vouchers (006 T088).
            List<SourceLine> lines = quote.getBranches().stream().flatMap(branch -> branch.getItems().stream()
                .map(item -> new SourceLine(item.getProductId(), item.getVariantId(), item.getQuantity(), item.getUnitPrice(),
                    branch.getLocationId()))).toList();
            var selection = new BenefitSelection(SelectionChoice.valueOf(quote.getSelectionChoice()), quote.getVoucherCode(), quote.getRequestedPoints());
            var trusted = new TrustedSourceContext(SourceKind.RETAIL, memberId, now, lines, null, quote.getShippingTotal());
            // Cash: one FE-06 settlement part per branch order, paid, cancelled or expired on its own (D-011).
            List<SettlementPart> parts = checkout.isCounterCash() && !zeroTotal ? settlementParts(checkout, quote) : List.of();
            var accepted = loyalty.reserveAcceptedTransaction(new AcceptedSourceContext(trusted, checkout.getId(), checkout.getVersion(),
                true, now, deadline, quote.getId(), quote.getTermsDigest(), parts), selection, key);
            if (!accepted.grossAmount().equals(quote.getMerchandiseSubtotal())
                || !accepted.programDiscount().equals(quote.getProgramDiscount())
                || !accepted.pointDiscount().equals(quote.getPointsDiscount())
                || !accepted.grossAmount().subtract(accepted.programDiscount()).subtract(accepted.pointDiscount())
                    .add(quote.getShippingTotal()).equals(quote.getPayableTotal())) {
                throw new RetailConflictException("Benefit terms changed; review a new quote");
            }
            if (!parts.isEmpty()) applyPartShares(checkout, accepted.parts());
            if (zeroTotal) {
                createdHolds.forEach(holds::settle);
                checkout.markPaid();
                checkouts.saveAndFlush(checkout);
                shipmentCreation.createShipmentsForPaidCheckout(checkout);
                var outcome = new AuthoritativeOutcome(SourceKind.RETAIL, checkout.getId(), "readyringsales",
                    "zero-" + checkout.getId(), checkout.getVersion(), OutcomeKind.RETAIL_ZERO_ACCEPTED,
                    now, deadline, quote.getId(), true, "zero-total:" + checkout.getId());
                loyalty.settleAcceptedBenefits(outcome);
                loyalty.grantEarnedPoints(outcome);
            }
            quote.accept(checkout.getId(), now);
            // Remove only the lines this checkout bought; unselected lines stay in the cart.
            cart.getLines().removeIf(line -> quantitiesByBranch.containsKey(new BranchVariant(line.getVariantId(), line.getLocationId())));
            cart.touch();
            return views.toView(checkout);
        });
        if (zeroTotal) {
            for (BranchVariant branchVariant : branchVariants) {
                UUID operationRef = UUID.nameUUIDFromBytes(("retail-zero:" + checkout.getId() + ":" + branchVariant.variantId()
                    + ":" + branchVariant.locationId())
                    .getBytes(StandardCharsets.UTF_8));
                catalogue.recordAcceptedSale(operationRef, branchVariant.variantId(), branchVariant.locationId(),
                    BigInteger.valueOf(quantitiesByBranch.get(branchVariant)));
            }
        }
        return view;
    }

    /** One part per branch order; its lines are the branch's quote items, in the order they are sent to FE-06. */
    private static List<SettlementPart> settlementParts(GroupedCheckout checkout, RetailCheckoutQuote quote) {
        Map<Long, String> orderByLocation = checkout.getOrders().stream()
            .collect(Collectors.toMap(RetailOrder::getLocationId, RetailOrder::getId));
        Map<Long, List<Integer>> indexes = new LinkedHashMap<>();
        int index = 0;
        for (var branch : quote.getBranches()) {
            for (var ignored : branch.getItems()) indexes.computeIfAbsent(branch.getLocationId(), location -> new ArrayList<>()).add(index++);
        }
        return indexes.entrySet().stream().map(entry -> new SettlementPart(orderByLocation.get(entry.getKey()), entry.getValue())).toList();
    }

    /** The branch orders take the voucher and points shares FE-06 fixed for each part; the checkout total is unchanged. */
    private void applyPartShares(GroupedCheckout checkout, List<BenefitPartRef> shares) {
        BigDecimal before = checkout.getOrders().stream().map(RetailOrder::getDiscountAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal after = BigDecimal.ZERO;
        for (BenefitPartRef share : shares) {
            RetailOrder order = checkout.getOrders().stream().filter(candidate -> candidate.getId().equals(share.partRef()))
                .findFirst().orElseThrow(() -> new RetailConflictException("Benefit part does not match a branch order"));
            BigDecimal discount = decimal(share.programDiscount().add(share.pointDiscount()));
            if (discount.compareTo(order.getSubtotal()) > 0) throw new RetailConflictException("Benefit part exceeds its branch order");
            order.applyDiscount(discount);
            after = after.add(discount);
        }
        if (shares.size() != checkout.getOrders().size() || before.compareTo(after) != 0) {
            throw new RetailConflictException("Benefit parts do not add up to the checkout discount");
        }
        checkouts.saveAndFlush(checkout);
    }

    private boolean sameSelectionAndBranches(RetailCheckoutQuote quote, CheckoutDtos.CreateGroupedCheckoutRequest request) {
        var selection = request.benefitSelection();
        if (selection == null || !Objects.equals(selection.choice(), quote.getSelectionChoice())
            || !Objects.equals(selection.voucherCode(), quote.getVoucherCode()) || selection.requestedPoints() == null
            || !selection.requestedPoints().matches("^(0|[1-9][0-9]{0,18})$")
            || !new BigInteger(selection.requestedPoints()).equals(quote.getRequestedPoints()) || request.branchOrders() == null) return false;
        Map<Long, CheckoutDtos.BranchOrderCheckoutInput> choices = new HashMap<>();
        for (var choice : request.branchOrders()) {
            if (choice == null || choice.locationId() == null || choices.putIfAbsent(choice.locationId(), choice) != null) return false;
        }
        for (var branch : quote.getBranches()) {
            var choice = choices.remove(branch.getLocationId());
            if (choice == null || !Objects.equals(choice.method(), branch.getFulfilmentMethod())
                || !Objects.equals(choice.shippingQuoteId(), branch.getShippingQuoteId())
                || !branch.matchesRecipient(choice.recipient())) return false;
        }
        return choices.isEmpty();
    }

    private static BigDecimal decimal(BigInteger value) { return new BigDecimal(value); }
}
