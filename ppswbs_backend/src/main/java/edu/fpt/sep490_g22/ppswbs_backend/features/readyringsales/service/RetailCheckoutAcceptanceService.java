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
        List<OrderItem> items = new ArrayList<>();
        for (var branch : quote.getBranches().stream().sorted(Comparator.comparing(RetailCheckoutQuoteBranch::getLocationId)).toList()) {
            BigInteger branchDiscount = remainingDiscount.min(branch.getSubtotal());
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
        Instant deadline = now.plus(15, ChronoUnit.MINUTES);
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
            // The current hold tables are intentionally variant-scoped. FE-02's branch guard keeps this bridge safe
            // until the dedicated branch-hold migration is scheduled.
            for (Long id : variantIds) {
                createdHolds.add(holds.createHeld(checkout, id, quantitiesByVariant.get(id), deadline));
            }
            List<SourceLine> lines = quote.getBranches().stream().flatMap(branch -> branch.getItems().stream())
                .map(item -> new SourceLine(item.getProductId(), item.getVariantId(), item.getQuantity(), item.getUnitPrice())).toList();
            var selection = new BenefitSelection(SelectionChoice.valueOf(quote.getSelectionChoice()), quote.getVoucherCode(), quote.getRequestedPoints());
            var trusted = new TrustedSourceContext(SourceKind.RETAIL, memberId, now, lines, null, quote.getShippingTotal());
            var accepted = loyalty.reserveAcceptedTransaction(new AcceptedSourceContext(trusted, checkout.getId(), checkout.getVersion(),
                true, now, deadline, quote.getId(), quote.getTermsDigest()), selection, key);
            if (!accepted.grossAmount().equals(quote.getMerchandiseSubtotal())
                || !accepted.programDiscount().equals(quote.getProgramDiscount())
                || !accepted.pointDiscount().equals(quote.getPointsDiscount())
                || !accepted.grossAmount().subtract(accepted.programDiscount()).subtract(accepted.pointDiscount())
                    .add(quote.getShippingTotal()).equals(quote.getPayableTotal())) {
                throw new RetailConflictException("Benefit terms changed; review a new quote");
            }
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
            cart.getLines().clear();
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
