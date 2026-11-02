package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.CheckoutHoldRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.GroupedCheckoutRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailCartRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CheckoutDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.CartLine;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.PaymentState;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/** Local demo only: cancellation is displayed as expiry and does not touch real stock or FE-06. */
@Service
@Profile("local")
@RequiredArgsConstructor
public class LocalCheckoutCancellationService {
    private final RetailCartRepository carts;
    private final GroupedCheckoutRepository checkouts;
    private final CheckoutHoldRepository holds;
    private final RetailCheckoutService checkoutViews;

    @Transactional
    public CheckoutDtos.GroupedCheckoutView cancelAsDemoExpiry(Long memberId, String checkoutId) {
        var cart = carts.findLockedByMemberAccountId(memberId)
            .orElseThrow(() -> new RetailNotFoundException("Retail cart does not exist"));
        var checkout = checkouts.lockOwned(checkoutId, memberId)
            .orElseThrow(() -> new RetailNotFoundException("Checkout does not exist"));
        if (checkout.getPaymentState() == PaymentState.EXPIRED) return checkoutViews.toView(checkout);
        if (checkout.getPaymentState() != PaymentState.ACCEPTED_UNPAID || checkout.getPaymentInitiatedAt() != null) {
            throw new RetailConflictException("Checkout cannot be cancelled after payment initiation");
        }
        var hold = holds.findByGroupedCheckoutId(checkoutId)
            .orElseThrow(() -> new RetailConflictException("Local checkout hold does not exist"));
        if (!"LOCAL_HELD".equals(hold.getStatus())) throw new RetailConflictException("Local checkout hold is not active");

        Map<LineKey, Integer> restored = new HashMap<>();
        for (CartLine line : cart.getLines()) restored.merge(new LineKey(line.getLocationId(), line.getVariantId()), line.getQuantity(), Integer::sum);
        checkout.getOrders().forEach(order -> order.getItems().forEach(item ->
            restored.merge(new LineKey(order.getLocationId(), item.getVariantId()), item.getQuantity(), Integer::sum)));
        Map<Long, Integer> variantTotals = new HashMap<>();
        restored.forEach((key, quantity) -> variantTotals.merge(key.variantId(), quantity, Integer::sum));
        if (restored.size() > 50 || variantTotals.values().stream().anyMatch(quantity -> quantity > 99)) {
            throw new RetailConflictException("Cart cannot accommodate the restored order; adjust it before retrying");
        }
        for (var order : checkout.getOrders()) {
            for (var item : order.getItems()) {
                var existing = cart.getLines().stream().filter(line -> line.getLocationId().equals(order.getLocationId())
                    && line.getVariantId().equals(item.getVariantId())).findFirst();
                if (existing.isPresent()) existing.get().changeQuantity(existing.get().getQuantity() + item.getQuantity());
                else cart.addLine(item.getVariantId(), order.getLocationId(), item.getQuantity());
            }
        }
        cart.touch();
        hold.expireLocalDemo();
        checkout.expireLocalDemo();
        return checkoutViews.toView(checkout);
    }

    private record LineKey(Long locationId, Long variantId) { }
}
