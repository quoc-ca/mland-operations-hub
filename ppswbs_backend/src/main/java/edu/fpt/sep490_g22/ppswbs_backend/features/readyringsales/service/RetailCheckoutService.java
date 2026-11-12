package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.GroupedCheckoutRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailCartRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CheckoutDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.CartLine;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.CheckoutHold;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.GroupedCheckout;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.OrderItem;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailCart;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailOrder;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailBenefitPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCheckoutPorts;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCheckoutPricingPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailShippingQuotePort;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RetailCheckoutService {
    private final RetailCartRepository cartRepository;
    private final GroupedCheckoutRepository checkoutRepository;
    private final RetailCheckoutPricingPort pricingPort;
    private final RetailBenefitPort benefitPort;
    private final RetailShippingQuotePort shippingQuotePort;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public CheckoutDtos.BenefitPreviewView previewBenefits(Long memberAccountId, CheckoutDtos.BenefitPreviewRequest request) {
        RetailCart cart = requireCart(memberAccountId);
        var paymentMethod = RetailPaymentMethods.parse(request.paymentMethod());
        if (request.branchOrders() != null && !request.branchOrders().isEmpty()) {
            RetailPaymentMethods.requireAllowed(paymentMethod, request.branchOrders().stream()
                .map(CheckoutDtos.BranchOrderCheckoutInput::method).toList());
        }
        List<RetailCheckoutPorts.PricedLine> lines = pricingPort.price(RetailCartSelection.select(cart, request.cartLineIds()));
        RetailCheckoutPorts.BenefitPreview preview = benefitPort.preview(memberAccountId, lines, toSelection(request.selection()));
        BigDecimal subtotal = subtotal(lines);
        BigDecimal shipping = previewShipping(memberAccountId, request.branchOrders(), lines);
        BigDecimal payable = subtotal.subtract(preview.discountAmount()).subtract(preview.pointsDiscountAmount()).add(shipping);
        return new CheckoutDtos.BenefitPreviewView(preview.previewId(), "VND", money(subtotal), true,
            money(preview.discountAmount()), money(preview.pointsDiscountAmount()),
            money(payable), preview.expiresAt().toString(), money(shipping));
    }

    @Transactional(readOnly = true)
    public CheckoutDtos.ShippingQuoteView quoteShipping(Long memberAccountId, CheckoutDtos.ShippingQuoteRequest request) {
        RetailCart cart = requireCart(memberAccountId);
        List<RetailCheckoutPorts.PricedLine> lines = pricingPort.price(RetailCartSelection.select(cart, request.cartLineIds())).stream()
            .filter(line -> line.locationId().equals(request.locationId())).toList();
        if (lines.isEmpty()) throw new RetailConflictException("Cart has no line for the selected location");
        var recipient = request.recipient() == null ? null : new RetailCheckoutPorts.RecipientAddress(
            request.recipient().address(), request.recipient().province(), request.recipient().district(), request.recipient().ward());
        RetailCheckoutPorts.ShippingQuote quote = shippingQuotePort.quote(memberAccountId, request.locationId(), lines, recipient);
        return new CheckoutDtos.ShippingQuoteView(quote.quoteId(), quote.locationId(), "VND", money(quote.charge()), money(quote.charge()),
            quote.expiresAt().toString(), quote.deliveryAvailable(), quote.message());
    }

    @Transactional
    public CheckoutDtos.GroupedCheckoutView createCheckout(Long memberAccountId, String idempotencyKey,
                                                             CheckoutDtos.CreateGroupedCheckoutRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new RetailConflictException("A valid Idempotency-Key is required");
        }
        var replay = checkoutRepository.findByMemberAccountIdAndIdempotencyKey(memberAccountId, idempotencyKey);
        if (replay.isPresent()) return toView(replay.get());

        RetailCart cart = cartRepository.findLockedByMemberAccountId(memberAccountId)
            .orElseThrow(() -> new RetailConflictException("Retail cart is empty"));
        if (cart.getLines().isEmpty()) throw new RetailConflictException("Retail cart is empty");
        List<CartLine> selected = RetailCartSelection.select(cart, request.cartLineIds());
        Map<Long, CheckoutDtos.BranchOrderCheckoutInput> choices = choicesByLocation(request.branchOrders());
        var paymentMethod = RetailPaymentMethods.parse(request.paymentMethod());
        RetailPaymentMethods.requireAllowed(paymentMethod, choices.values().stream()
            .map(CheckoutDtos.BranchOrderCheckoutInput::method).toList());
        Set<Long> cartLocations = selected.stream().map(CartLine::getLocationId).collect(Collectors.toSet());
        if (!cartLocations.equals(choices.keySet())) throw new RetailConflictException("Checkout choices must match every selected cart location exactly once");

        List<RetailCheckoutPorts.PricedLine> priced = pricingPort.price(selected);
        BigDecimal subtotal = subtotal(priced);
        RetailCheckoutPorts.BenefitPreview benefit = benefitPort.requireValidPreview(memberAccountId, request.benefitPreviewId(),
            toSelection(request.benefitSelection()), subtotal);
        BigDecimal discount = benefit.discountAmount().add(benefit.pointsDiscountAmount());
        Map<Long, BigDecimal> shippingByLocation = shippingByLocation(memberAccountId, choices, priced);
        BigDecimal shipping = shippingByLocation.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        GroupedCheckout checkout = new GroupedCheckout(memberAccountId, idempotencyKey, cart.getVersion() == null ? 0L : cart.getVersion(), subtotal, shipping, discount);
        List<Long> locations = cartLocations.stream().sorted().toList();
        for (int index = 0; index < locations.size(); index++) {
            Long locationId = locations.get(index);
            List<RetailCheckoutPorts.PricedLine> branchLines = priced.stream().filter(line -> line.locationId().equals(locationId)).toList();
            BigDecimal branchSubtotal = subtotal(branchLines);
            BigDecimal branchDiscount = index == 0 ? discount : BigDecimal.ZERO;
            RetailOrder order = new RetailOrder(checkout, locationId, choices.get(locationId).method().toUpperCase(), branchSubtotal,
                shippingByLocation.get(locationId), branchDiscount);
            var recipientInput = choices.get(locationId).recipient();
            if ("GHTK".equalsIgnoreCase(choices.get(locationId).method()) && recipientInput != null) {
                order.setRecipientSnapshot(recipientInput.recipientName(), recipientInput.recipientPhone(),
                    recipientInput.address(), recipientInput.province(), recipientInput.district(), recipientInput.ward());
            }
            branchLines.forEach(line -> order.addItem(new OrderItem(order, line.variantId(), line.quantity(), line.unitPrice())));
            checkout.addOrder(order);
        }
        RetailPaymentMethods.requirePayableForCash(paymentMethod, checkout.getPayableAmount());
        Instant holdDeadline = RetailPaymentMethods.holdDeadline(paymentMethod, Instant.now());
        if (paymentMethod == edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailPaymentMethod.CASH_AT_COUNTER) {
            checkout.useCounterCash(holdDeadline);
        }
        checkoutRepository.saveAndFlush(checkout);
        entityManager.persist(new CheckoutHold(checkout, holdDeadline));
        RetailCartSelection.removeCheckedOut(cart, selected);
        return toView(checkout);
    }

    @Transactional(readOnly = true)
    public GroupedCheckout requireOwnedCheckout(Long memberAccountId, String checkoutId) {
        return checkoutRepository.findByIdAndMemberAccountId(checkoutId, memberAccountId)
            .orElseThrow(() -> new RetailNotFoundException("Grouped checkout does not exist"));
    }

    public GroupedCheckout lockOwnedCheckout(Long memberAccountId, String checkoutId) {
        return checkoutRepository.lockOwned(checkoutId, memberAccountId)
            .orElseThrow(() -> new RetailNotFoundException("Grouped checkout does not exist"));
    }

    private RetailCart requireCart(Long memberAccountId) {
        return cartRepository.findByMemberAccountId(memberAccountId).orElseThrow(() -> new RetailConflictException("Retail cart is empty"));
    }
    private Map<Long, CheckoutDtos.BranchOrderCheckoutInput> choicesByLocation(List<CheckoutDtos.BranchOrderCheckoutInput> choices) {
        Map<Long, CheckoutDtos.BranchOrderCheckoutInput> values = new HashMap<>();
        for (CheckoutDtos.BranchOrderCheckoutInput choice : choices) {
            if (values.put(choice.locationId(), choice) != null) throw new RetailConflictException("Each location can be checked out once");
            if (!"PICKUP".equalsIgnoreCase(choice.method()) && !"GHTK".equalsIgnoreCase(choice.method())) throw new RetailConflictException("Fulfilment method is not supported");
        }
        return values;
    }
    private Map<Long, BigDecimal> shippingByLocation(Long memberId, Map<Long, CheckoutDtos.BranchOrderCheckoutInput> choices,
                                                      List<RetailCheckoutPorts.PricedLine> priced) {
        Map<Long, BigDecimal> result = new HashMap<>();
        choices.forEach((locationId, choice) -> {
            if ("PICKUP".equalsIgnoreCase(choice.method())) result.put(locationId, BigDecimal.ZERO);
            else {
                if (choice.shippingQuoteId() == null || choice.shippingQuoteId().isBlank()) throw new RetailConflictException("GHTK requires a server-issued shipping quote");
                var branchLines = priced.stream().filter(line -> locationId.equals(line.locationId())).toList();
                var address = choice.recipient();
                var recipient = address == null ? null : new RetailCheckoutPorts.RecipientAddress(
                    address.address(), address.province(), address.district(), address.ward());
                result.put(locationId, shippingQuotePort.requireValidQuote(memberId, locationId, choice.shippingQuoteId(), branchLines, recipient).charge());
            }
        });
        return result;
    }
    private BigDecimal previewShipping(Long memberId, List<CheckoutDtos.BranchOrderCheckoutInput> inputs,
                                       List<RetailCheckoutPorts.PricedLine> priced) {
        if (inputs == null || inputs.isEmpty()) return BigDecimal.ZERO;
        Map<Long, CheckoutDtos.BranchOrderCheckoutInput> choices = choicesByLocation(inputs);
        Set<Long> locations = priced.stream().map(RetailCheckoutPorts.PricedLine::locationId).collect(Collectors.toSet());
        if (!locations.equals(choices.keySet())) {
            throw new RetailConflictException("Fulfilment choices must match every cart location exactly once");
        }
        return shippingByLocation(memberId, choices, priced).values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private RetailCheckoutPorts.BenefitSelection toSelection(CheckoutDtos.BenefitSelection selection) {
        try { return new RetailCheckoutPorts.BenefitSelection(selection.choice().toUpperCase(), selection.voucherCode(), new BigDecimal(selection.requestedPoints())); }
        catch (NumberFormatException exception) { throw new RetailConflictException("requestedPoints must be a decimal string"); }
    }
    private BigDecimal subtotal(List<RetailCheckoutPorts.PricedLine> lines) { return lines.stream().map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity()))).reduce(BigDecimal.ZERO, BigDecimal::add); }
    private String money(BigDecimal amount) { return amount.stripTrailingZeros().toPlainString(); }
    public CheckoutDtos.GroupedCheckoutView toView(GroupedCheckout checkout) {
        return new CheckoutDtos.GroupedCheckoutView(checkout.getId(), checkout.getOrders().stream().map(order -> new CheckoutDtos.RetailOrderView(
            order.getId(), order.getLocationId(), money(order.getSubtotal()), money(order.getShippingCharge()), money(order.getPayableAmount()),
            order.getPaymentState().name(), order.getFulfilmentState())).toList(), money(checkout.getMerchandiseSubtotal()), money(checkout.getShippingCharge()),
            money(checkout.getPayableAmount()), checkout.getPaymentState().name(), checkout.getPaymentInitiatedAt() == null ? null : checkout.getPaymentInitiatedAt().toString(),
            checkout.getPaymentMethod(), checkout.getCounterPaymentDeadline() == null ? null : checkout.getCounterPaymentDeadline().toString());
    }
}
