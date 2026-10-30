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
        if (quote.getPayableTotal().signum() == 0) {
            throw new RetailIntegrationUnavailableException("Zero-total checkout outcome is not configured");
        }
        if (quote.getBranches().stream().anyMatch(branch -> !"PICKUP".equals(branch.getFulfilmentMethod()))) {
            throw new RetailIntegrationUnavailableException("Shipping checkout is not configured");
        }
        List<Long> variantIds = quote.getBranches().stream().flatMap(branch -> branch.getItems().stream())
            .map(RetailCheckoutQuoteItem::getVariantId).distinct().sorted().toList();
        if (variantIds.isEmpty()) throw new RetailConflictException("Checkout quote is empty");
        try {
            return catalogue.withLockedSaleContext(variantIds, context -> acceptLocked(memberId, idempotencyKey, request, cart, quote, context, variantIds));
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
        RetailCart cart, RetailCheckoutQuote quote, SaleContext context, List<Long> variantIds) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        validator.validate(quote, cart, request, context, now);
        Map<Long, SaleVariantFact> facts = context.variants().stream().collect(Collectors.toMap(SaleVariantFact::variantId, fact -> fact));
        Map<Long, Integer> quantities = new HashMap<>();
        quote.getBranches().forEach(branch -> branch.getItems().forEach(item -> quantities.merge(item.getVariantId(), item.getQuantity(), Integer::sum)));
        var checkout = new GroupedCheckout(memberId, key, quote.getCartVersion(), decimal(quote.getMerchandiseSubtotal()),
            decimal(quote.getShippingTotal()), decimal(quote.getProgramDiscount().add(quote.getPointsDiscount())));
        BigInteger remainingDiscount = quote.getProgramDiscount().add(quote.getPointsDiscount());
        List<OrderItem> items = new ArrayList<>();
        for (var branch : quote.getBranches().stream().sorted(Comparator.comparing(RetailCheckoutQuoteBranch::getLocationId)).toList()) {
            BigInteger branchDiscount = remainingDiscount.min(branch.getSubtotal());
            remainingDiscount = remainingDiscount.subtract(branchDiscount);
            var order = new RetailOrder(checkout, branch.getLocationId(), branch.getFulfilmentMethod(), decimal(branch.getSubtotal()),
                decimal(branch.getShippingCharge()), decimal(branchDiscount));
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
        return guard.withGuard(variantIds, context.asOf(), snapshots -> {
            for (Long id : variantIds) {
                var available = facts.get(id).remainingUnsold().subtract(snapshots.get(id).heldQuantity());
                if (available.compareTo(BigInteger.valueOf(quantities.get(id))) < 0) {
                    throw new RetailConflictException("Product quantity is no longer available");
                }
                holds.createHeld(checkout, id, quantities.get(id), deadline);
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
            quote.accept(checkout.getId(), now);
            cart.getLines().clear();
            cart.touch();
            return views.toView(checkout);
        });
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
                || !Objects.equals(choice.shippingQuoteId(), branch.getShippingQuoteId())) return false;
        }
        return choices.isEmpty();
    }

    private static BigDecimal decimal(BigInteger value) { return new BigDecimal(value); }
}
