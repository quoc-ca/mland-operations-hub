package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SaleContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SaleVariantFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CheckoutDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.CartLine;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailCart;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailCheckoutQuote;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailCheckoutQuoteBranch;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Validates an owned, locked quote against the locked cart and FE-02 locked sale facts. */
@Component
public class RetailCheckoutQuoteValidator {
    public void validate(RetailCheckoutQuote quote, RetailCart cart, CheckoutDtos.CreateGroupedCheckoutRequest request,
                         SaleContext context, Instant now) {
        if (quote == null || cart == null || request == null || context == null || now == null
            || !Boolean.TRUE.equals(request.benefitTermsAccepted()) || !"OPEN".equals(quote.getStatus())
            || !now.isBefore(quote.getExpiresAt()) || !Objects.equals(request.benefitPreviewId(), quote.getId())
            || !Objects.equals(cart.getId(), quote.getCartId()) || !Objects.equals(cart.getMemberAccountId(), quote.getMemberAccountId())
            || cart.getVersion() == null || cart.getVersion() != quote.getCartVersion()) {
            throw stale();
        }
        var selected = request.benefitSelection();
        if (selected == null || !Objects.equals(selected.choice(), quote.getSelectionChoice())
            || !Objects.equals(selected.voucherCode(), quote.getVoucherCode()) || selected.requestedPoints() == null
            || !selected.requestedPoints().matches("^(0|[1-9][0-9]{0,18})$")
            || !new BigInteger(selected.requestedPoints()).equals(quote.getRequestedPoints())) {
            throw stale();
        }
        Map<Long, CheckoutDtos.BranchOrderCheckoutInput> choices = new HashMap<>();
        if (request.branchOrders() == null) throw stale();
        for (var choice : request.branchOrders()) {
            if (choice == null || choice.locationId() == null || choices.putIfAbsent(choice.locationId(), choice) != null) throw stale();
        }
        Map<LineKey, Integer> currentLines = new HashMap<>();
        for (CartLine line : cart.getLines()) {
            if (currentLines.put(new LineKey(line.getLocationId(), line.getVariantId()), line.getQuantity()) != null) throw stale();
        }
        Map<Long, SaleVariantFact> saleFacts = new HashMap<>();
        for (var fact : context.variants()) {
            if (saleFacts.put(fact.variantId(), fact) != null) throw stale();
        }
        Map<LineKey, Integer> quotedLines = new HashMap<>();
        for (RetailCheckoutQuoteBranch branch : quote.getBranches()) {
            var choice = choices.remove(branch.getLocationId());
            if (choice == null || !Objects.equals(choice.method(), branch.getFulfilmentMethod())
                || !Objects.equals(choice.shippingQuoteId(), branch.getShippingQuoteId())) throw stale();
            for (var item : branch.getItems()) {
                var key = new LineKey(branch.getLocationId(), item.getVariantId());
                if (quotedLines.put(key, item.getQuantity()) != null) throw stale();
                SaleVariantFact fact = saleFacts.get(item.getVariantId());
                if (fact == null || fact.productId() != item.getProductId() || fact.productDeleted()
                    || !fact.publiclyEligible() || !fact.saleEnabled() || !Objects.equals(fact.appliedPrice(), item.getUnitPrice())
                    || !Objects.equals(fact.currency(), quote.getCurrency())) throw stale();
            }
        }
        if (!choices.isEmpty() || quotedLines.isEmpty() || !quotedLines.equals(currentLines)
            || !saleFacts.keySet().equals(quotedLines.keySet().stream().map(LineKey::variantId).collect(java.util.stream.Collectors.toSet()))) {
            throw stale();
        }
    }

    private RetailConflictException stale() {
        return new RetailConflictException("Checkout quote changed or expired; review a new quote");
    }
    private record LineKey(Long locationId, Long variantId) { }
}
