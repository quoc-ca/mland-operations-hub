package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CartSaleFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailCartRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailCheckoutQuoteRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CheckoutDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Creates a durable, server-priced pickup quote without reserving stock or benefits. */
@Service
@Profile("!local")
@RequiredArgsConstructor
public class RetailCheckoutQuotePreviewService {
    private final RetailCartRepository carts;
    private final RetailCheckoutQuoteRepository quotes;
    private final CatalogueSalesFacade catalogue;
    private final LoyaltyFacade loyalty;

    @Transactional
    public CheckoutDtos.BenefitPreviewView preview(Long memberId, CheckoutDtos.BenefitPreviewRequest request) {
        RetailCart cart = carts.findByMemberAccountId(memberId)
            .orElseThrow(() -> new RetailConflictException("Retail cart is empty"));
        if (cart.getLines().isEmpty()) throw new RetailConflictException("Retail cart is empty");
        Map<Long, CheckoutDtos.BranchOrderCheckoutInput> choices = choices(request.branchOrders());
        Set<Long> locations = cart.getLines().stream().map(CartLine::getLocationId).collect(Collectors.toSet());
        if (!locations.equals(choices.keySet())) throw new RetailConflictException("Fulfilment choices must match cart locations");

        List<Long> ids = cart.getLines().stream().map(CartLine::getVariantId).distinct().sorted().toList();
        Map<Long, CartSaleFact> facts;
        try {
            facts = catalogue.readSaleFacts(ids).stream().collect(Collectors.toMap(CartSaleFact::variantId, Function.identity()));
        } catch (CatalogueSalesException exception) {
            if (exception.reason() == CatalogueSalesException.Reason.NOT_FOUND) {
                throw new RetailConflictException("A cart product is no longer available");
            }
            throw new RetailIntegrationUnavailableException("Catalogue integration is unavailable");
        }
        if (facts.size() != ids.size() || !facts.keySet().containsAll(ids)) {
            throw new RetailIntegrationUnavailableException("Catalogue returned incomplete variant facts");
        }
        Map<Long, Integer> quantities = new HashMap<>();
        cart.getLines().forEach(line -> quantities.merge(line.getVariantId(), line.getQuantity(), Integer::sum));
        for (Long id : ids) {
            CartSaleFact fact = facts.get(id);
            if (!fact.saleEligible() || fact.appliedPrice() == null || !"VND".equals(fact.currency())
                || fact.availableQuantity().compareTo(BigInteger.valueOf(quantities.get(id))) < 0) {
                throw new RetailConflictException("Cart contains an unavailable product or quantity");
            }
        }

        BenefitSelection selection = selection(request.selection());
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        List<SourceLine> sourceLines = cart.getLines().stream().map(line -> {
            CartSaleFact fact = facts.get(line.getVariantId());
            return new SourceLine(fact.productId(), fact.variantId(), line.getQuantity(), fact.appliedPrice());
        }).toList();
        BigInteger subtotal = sourceLines.stream().map(SourceLine::lineAmount).reduce(BigInteger.ZERO, BigInteger::add);
        BenefitPreview result;
        try {
            result = loyalty.previewBenefits(new TrustedSourceContext(SourceKind.RETAIL, memberId, now,
                sourceLines, null, BigInteger.ZERO), selection);
        } catch (LoyaltyException exception) {
            if (exception.code() == LoyaltyErrorCode.DEPENDENCY_UNAVAILABLE || exception.code() == LoyaltyErrorCode.RETRYABLE_CONTENTION) {
                throw new RetailIntegrationUnavailableException("Loyalty integration is unavailable");
            }
            throw new RetailConflictException("Benefit selection is invalid or has changed");
        }
        if (result == null || result.termsDigest() == null || result.termsDigest().isBlank()
            || result.programDiscount() == null || result.pointDiscount() == null
            || result.postPromotionAmount() == null || result.pointsUsed() == null || !subtotal.equals(result.grossAmount())
            || !subtotal.subtract(result.programDiscount()).subtract(result.pointDiscount()).equals(result.postPromotionAmount())) {
            throw new RetailIntegrationUnavailableException("Loyalty returned unverifiable preview facts");
        }
        boolean eligible = (selection.choice() != SelectionChoice.VOUCHER || result.programId() != null)
            && selection.requestedPoints().equals(result.pointsUsed());
        if (!eligible) {
            return new CheckoutDtos.BenefitPreviewView(null, "VND", subtotal.toString(), true,
                result.programDiscount().toString(), result.pointDiscount().toString(), result.postPromotionAmount().toString(),
                null, "0", false, result.reasons());
        }
        Instant expiresAt = now.plus(15, ChronoUnit.MINUTES);
        var quote = new RetailCheckoutQuote(memberId, cart.getId(), cart.getVersion(), selection.choice().name(),
            selection.voucherCode(), selection.requestedPoints(), result.termsDigest(), "VND", subtotal,
            BigInteger.ZERO, result.programDiscount(), result.pointDiscount(), result.postPromotionAmount(), now, expiresAt);
        for (Long location : locations.stream().sorted().toList()) {
            var branchLines = cart.getLines().stream().filter(line -> line.getLocationId().equals(location)).toList();
            BigInteger branchSubtotal = branchLines.stream().map(line -> facts.get(line.getVariantId()).appliedPrice()
                .multiply(BigInteger.valueOf(line.getQuantity()))).reduce(BigInteger.ZERO, BigInteger::add);
            var branch = new RetailCheckoutQuoteBranch(quote, location, "PICKUP", null, BigInteger.ZERO, branchSubtotal);
            branchLines.forEach(line -> {
                CartSaleFact fact = facts.get(line.getVariantId());
                branch.addItem(new RetailCheckoutQuoteItem(branch, fact.productId(), fact.variantId(), line.getQuantity(), fact.appliedPrice()));
            });
            quote.addBranch(branch);
        }
        quotes.saveAndFlush(quote);
        return new CheckoutDtos.BenefitPreviewView(quote.getId(), "VND", subtotal.toString(), true,
            result.programDiscount().toString(), result.pointDiscount().toString(), result.postPromotionAmount().toString(),
            expiresAt.toString(), "0", true, result.reasons());
    }

    private Map<Long, CheckoutDtos.BranchOrderCheckoutInput> choices(List<CheckoutDtos.BranchOrderCheckoutInput> inputs) {
        if (inputs == null || inputs.isEmpty()) throw new RetailConflictException("Select fulfilment for each cart location");
        Map<Long, CheckoutDtos.BranchOrderCheckoutInput> result = new HashMap<>();
        for (var input : inputs) {
            if (input == null || input.locationId() == null || input.locationId() <= 0 || input.method() == null
                || result.putIfAbsent(input.locationId(), input) != null) {
                throw new RetailConflictException("Invalid fulfilment choices");
            }
            if ("GHTK".equalsIgnoreCase(input.method())) {
                throw new RetailIntegrationUnavailableException("GHTK shipping quotes are not configured");
            }
            if (!"PICKUP".equalsIgnoreCase(input.method()) || input.shippingQuoteId() != null) {
                throw new RetailConflictException("Invalid pickup fulfilment choice");
            }
        }
        return result;
    }

    private BenefitSelection selection(CheckoutDtos.BenefitSelection input) {
        try {
            if (input == null || input.choice() == null || input.requestedPoints() == null
                || !input.requestedPoints().matches("^(0|[1-9][0-9]{0,18})$")) {
                throw new IllegalArgumentException("Invalid benefit selection");
            }
            return new BenefitSelection(SelectionChoice.valueOf(input.choice()), input.voucherCode(),
                new BigInteger(input.requestedPoints()));
        } catch (IllegalArgumentException | LoyaltyException exception) {
            throw new RetailConflictException("Benefit selection is invalid");
        }
    }
}
