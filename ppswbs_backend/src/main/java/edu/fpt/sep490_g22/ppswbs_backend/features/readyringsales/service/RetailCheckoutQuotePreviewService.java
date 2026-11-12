package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.BranchCartSaleFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.BranchVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailCartRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailCheckoutQuoteRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CheckoutDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCheckoutPorts;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailShippingQuotePort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/** Creates a durable, server-priced quote without reserving stock or benefits. */
@Service
@Profile("!local")
@RequiredArgsConstructor
public class RetailCheckoutQuotePreviewService {
    private static final java.util.regex.Pattern PHONE = java.util.regex.Pattern.compile("^(?:0|\\+84)\\d{9}$");
    private final RetailCartRepository carts;
    private final RetailCheckoutQuoteRepository quotes;
    private final CatalogueSalesFacade catalogue;
    private final LoyaltyFacade loyalty;
    private final RetailShippingQuotePort shippingQuotes;

    @Transactional(readOnly = true)
    public CheckoutDtos.ShippingQuoteView quoteShipping(Long memberId, CheckoutDtos.ShippingQuoteRequest request) {
        var cart = carts.findByMemberAccountId(memberId).orElseThrow(() -> new RetailConflictException("Retail cart is empty"));
        var branch = RetailCartSelection.select(cart, request.cartLineIds()).stream()
            .filter(line -> request.locationId().equals(line.getLocationId())).toList();
        if (branch.isEmpty()) throw new RetailConflictException("Cart has no line for this branch");
        var facts = readBranchFacts(branch.stream().map(this::branchKey).distinct().sorted().toList());
        var lines = branch.stream().map(line -> {
            var fact = facts.get(branchKey(line));
            if (fact == null || !fact.saleEligible() || fact.appliedPrice() == null || !"VND".equals(fact.currency()))
                throw new RetailConflictException("Product terms changed");
            return new RetailCheckoutPorts.PricedLine(line.getVariantId(), line.getLocationId(), line.getQuantity(),
                new java.math.BigDecimal(fact.appliedPrice()));
        }).toList();
        var address = request.recipient();
        var recipient = address == null ? null : new RetailCheckoutPorts.RecipientAddress(address.address(), address.province(),
            address.district(), address.ward(), address.recipientName(), address.recipientPhone());
        var fee = shippingQuotes.quote(memberId, request.locationId(), lines, recipient);
        return new CheckoutDtos.ShippingQuoteView(fee.quoteId(), fee.locationId(), "VND", fee.charge().toPlainString(),
            fee.charge().toPlainString(), fee.expiresAt().toString(), fee.deliveryAvailable(), fee.message());
    }

    @Transactional
    public CheckoutDtos.BenefitPreviewView preview(Long memberId, CheckoutDtos.BenefitPreviewRequest request) {
        RetailCart cart = carts.findByMemberAccountId(memberId)
            .orElseThrow(() -> new RetailConflictException("Retail cart is empty"));
        if (cart.getLines().isEmpty()) throw new RetailConflictException("Retail cart is empty");
        // Only the lines the Member ticked are quoted; the quote items become the checkout's exact contents.
        List<CartLine> selected = RetailCartSelection.select(cart, request.cartLineIds());
        Map<Long, CheckoutDtos.BranchOrderCheckoutInput> choices = choices(request.branchOrders());
        RetailPaymentMethod paymentMethod = RetailPaymentMethods.parse(request.paymentMethod());
        RetailPaymentMethods.requireAllowed(paymentMethod, choices.values().stream()
            .map(CheckoutDtos.BranchOrderCheckoutInput::method).toList());
        Set<Long> locations = selected.stream().map(CartLine::getLocationId).collect(Collectors.toSet());
        if (!locations.equals(choices.keySet())) throw new RetailConflictException("Fulfilment choices must match cart locations");

        List<BranchVariant> keys = selected.stream().map(this::branchKey).distinct().sorted().toList();
        Map<BranchVariant, BranchCartSaleFact> facts = readBranchFacts(keys);
        Map<BranchVariant, Integer> quantities = new HashMap<>();
        selected.forEach(line -> quantities.merge(branchKey(line), line.getQuantity(), Integer::sum));
        for (BranchVariant key : keys) {
            BranchCartSaleFact fact = facts.get(key);
            if (!fact.saleEligible() || fact.appliedPrice() == null || !"VND".equals(fact.currency())
                || fact.availableQuantity().compareTo(BigInteger.valueOf(quantities.get(key))) < 0) {
                throw new RetailConflictException("Cart contains an unavailable product or quantity");
            }
        }

        BenefitSelection selection = selection(request.selection());
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        List<SourceLine> sourceLines = selected.stream().map(line -> {
            BranchCartSaleFact fact = facts.get(branchKey(line));
            return new SourceLine(fact.productId(), fact.variantId(), line.getQuantity(), fact.appliedPrice());
        }).toList();
        BigInteger subtotal = sourceLines.stream().map(SourceLine::lineAmount).reduce(BigInteger.ZERO, BigInteger::add);
        Map<Long, RetailCheckoutPorts.ShippingQuote> shipping = new HashMap<>();
        BigInteger shippingTotal = BigInteger.ZERO;
        Instant expiresAt = now.plus(15, ChronoUnit.MINUTES);
        for (Long location : locations) {
            var choice = choices.get(location);
            if (!"GHTK".equalsIgnoreCase(choice.method())) continue;
            var lines = selected.stream().filter(line -> location.equals(line.getLocationId()))
                .map(line -> new RetailCheckoutPorts.PricedLine(line.getVariantId(), location, line.getQuantity(),
                    new java.math.BigDecimal(facts.get(branchKey(line)).appliedPrice()))).toList();
            var recipient = choice.recipient();
            var fee = shippingQuotes.requireValidQuote(memberId, location, choice.shippingQuoteId(), lines,
                new RetailCheckoutPorts.RecipientAddress(recipient.address(), recipient.province(), recipient.district(),
                    recipient.ward(), recipient.recipientName(), recipient.recipientPhone()));
            if (!fee.deliveryAvailable() || fee.charge() == null || fee.charge().signum() < 0
                || fee.charge().stripTrailingZeros().scale() > 0 || !now.isBefore(fee.expiresAt())) {
                throw new RetailConflictException("Shipping quote changed or expired; request a new quote");
            }
            shipping.put(location, fee);
            shippingTotal = shippingTotal.add(fee.charge().toBigIntegerExact());
            if (fee.expiresAt().isBefore(expiresAt)) expiresAt = fee.expiresAt();
        }
        BenefitPreview result;
        try {
            result = loyalty.previewBenefits(new TrustedSourceContext(SourceKind.RETAIL, memberId, now,
                sourceLines, null, shippingTotal), selection);
        } catch (LoyaltyException exception) {
            if (exception.code() == LoyaltyErrorCode.DEPENDENCY_UNAVAILABLE || exception.code() == LoyaltyErrorCode.RETRYABLE_CONTENTION) {
                throw new RetailIntegrationUnavailableException("Loyalty integration is unavailable");
            }
            throw new RetailConflictException("Benefit selection is invalid or has changed");
        }
        if (result == null || result.termsDigest() == null || result.termsDigest().isBlank()
            || result.programDiscount() == null || result.pointDiscount() == null
            || result.postPromotionAmount() == null || result.pointsUsed() == null || result.maxPoints() == null
            || result.maxPoints().signum() < 0 || result.minRedeemPoints() == null || result.minRedeemPoints().signum() < 0
            || !subtotal.equals(result.grossAmount())
            || !subtotal.subtract(result.programDiscount()).subtract(result.pointDiscount()).equals(result.postPromotionAmount())) {
            throw new RetailIntegrationUnavailableException("Loyalty returned unverifiable preview facts");
        }
        boolean eligible = (selection.choice() != SelectionChoice.VOUCHER || result.programId() != null)
            && selection.requestedPoints().equals(result.pointsUsed());
        if (!eligible) {
            return new CheckoutDtos.BenefitPreviewView(null, "VND", subtotal.toString(), true,
                result.programDiscount().toString(), result.pointDiscount().toString(), result.postPromotionAmount().toString(),
                null, "0", false, result.reasons(), result.maxPoints().toString(), result.minRedeemPoints().toString());
        }
        var quote = new RetailCheckoutQuote(memberId, cart.getId(), cart.getVersion(), selection.choice().name(),
            selection.voucherCode(), selection.requestedPoints(), result.termsDigest(), "VND", subtotal,
            shippingTotal, result.programDiscount(), result.pointDiscount(), result.postPromotionAmount().add(shippingTotal), now, expiresAt);
        RetailPaymentMethods.requirePayableForCash(paymentMethod, new java.math.BigDecimal(quote.getPayableTotal()));
        quote.usePaymentMethod(paymentMethod);
        for (Long location : locations.stream().sorted().toList()) {
            var branchLines = selected.stream().filter(line -> line.getLocationId().equals(location)).toList();
            BigInteger branchSubtotal = branchLines.stream().map(line -> facts.get(branchKey(line)).appliedPrice()
                .multiply(BigInteger.valueOf(line.getQuantity()))).reduce(BigInteger.ZERO, BigInteger::add);
            var choice = choices.get(location);
            var fee = shipping.get(location);
            var branch = new RetailCheckoutQuoteBranch(quote, location, choice.method().toUpperCase(Locale.ROOT),
                fee == null ? null : fee.quoteId(), fee == null ? BigInteger.ZERO : fee.charge().toBigIntegerExact(), branchSubtotal);
            if (fee != null) {
                var recipient = choice.recipient();
                branch.setRecipientSnapshot(recipient.recipientName(), recipient.recipientPhone(), recipient.address(),
                    recipient.province(), recipient.district(), recipient.ward());
            }
            branchLines.forEach(line -> {
                BranchCartSaleFact fact = facts.get(branchKey(line));
                branch.addItem(new RetailCheckoutQuoteItem(branch, fact.productId(), fact.variantId(), line.getQuantity(), fact.appliedPrice()));
            });
            quote.addBranch(branch);
        }
        quotes.saveAndFlush(quote);
        return new CheckoutDtos.BenefitPreviewView(quote.getId(), "VND", subtotal.toString(), true,
            result.programDiscount().toString(), result.pointDiscount().toString(), quote.getPayableTotal().toString(),
            expiresAt.toString(), shippingTotal.toString(), true, result.reasons(), result.maxPoints().toString(), result.minRedeemPoints().toString());
    }

    private Map<BranchVariant, BranchCartSaleFact> readBranchFacts(Collection<BranchVariant> keys) {
        List<BranchVariant> requested = keys.stream().distinct().sorted().toList();
        try {
            List<BranchCartSaleFact> returned = catalogue.readBranchSaleFacts(requested);
            if (returned == null) {
                throw new RetailIntegrationUnavailableException("Catalogue returned no branch sale facts");
            }
            Map<BranchVariant, BranchCartSaleFact> facts = new HashMap<>();
            for (BranchCartSaleFact fact : returned) {
                if (fact == null || !requested.contains(fact.key()) || facts.putIfAbsent(fact.key(), fact) != null) {
                    throw new RetailIntegrationUnavailableException("Catalogue returned unverifiable branch sale facts");
                }
            }
            if (facts.size() != requested.size()) {
                throw new RetailIntegrationUnavailableException("Catalogue returned incomplete branch sale facts");
            }
            return facts;
        } catch (CatalogueSalesException exception) {
            if (exception.reason() == CatalogueSalesException.Reason.NOT_FOUND) {
                throw new RetailConflictException("A cart product is no longer available at its selected branch");
            }
            throw new RetailIntegrationUnavailableException("Catalogue integration is unavailable");
        }
    }

    private BranchVariant branchKey(CartLine line) {
        return new BranchVariant(line.getVariantId(), line.getLocationId());
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
                if (input.shippingQuoteId() == null || input.shippingQuoteId().isBlank() || !validRecipient(input.recipient())) {
                    throw new RetailConflictException("GHTK requires a server-issued shipping quote and recipient details");
                }
                continue;
            }
            if (!"PICKUP".equalsIgnoreCase(input.method()) || input.shippingQuoteId() != null) {
                throw new RetailConflictException("Invalid pickup fulfilment choice");
            }
        }
        return result;
    }

    /** Optional at quote time; required when the Member accepts a GHTK branch order. */
    private boolean validRecipient(CheckoutDtos.RecipientAddressInput recipient) {
        return recipient != null
            && recipient.recipientName() != null && recipient.recipientName().trim().matches("(?s).{1,100}")
            && recipient.recipientPhone() != null && PHONE.matcher(recipient.recipientPhone()).matches()
            && recipient.address() != null && recipient.address().matches("(?s).{10,500}");
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
