package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailOrderRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.RetailOrderHistoryView;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailOrder;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCataloguePort;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RetailOrderQueryService {
    private final RetailOrderRepository orders;
    private final RetailCataloguePort catalogue;

    public List<RetailOrderHistoryView> list(Long memberId) {
        Map<VariantLocationKey, RetailCataloguePort.CartProductFact> facts = new HashMap<>();
        return orders.findOwnedOrders(memberId).stream().map(order -> view(order, facts)).toList();
    }

    public RetailOrderHistoryView find(Long memberId, String orderId) {
        return view(orders.findOwnedOrder(memberId, orderId)
            .orElseThrow(() -> new RetailNotFoundException("Retail order does not exist")), new HashMap<>());
    }

    /** Member-safe view reused by the Staff pickup desk, which adds its own operational facts. */
    RetailOrderHistoryView view(RetailOrder order) { return view(order, new HashMap<>()); }

    private RetailOrderHistoryView view(RetailOrder order,
                                        Map<VariantLocationKey, RetailCataloguePort.CartProductFact> facts) {
        var items = order.getItems().stream().map(item -> {
            var current = facts.computeIfAbsent(new VariantLocationKey(item.getVariantId(), order.getLocationId()), this::currentProduct);
            return new RetailOrderHistoryView.Item(item.getVariantId(), current.productName(), current.sku(), current.size(),
                item.getQuantity(), money(item.getUnitPrice()), money(item.getLineTotal()));
        }).toList();
        // Current checkout creation assigns READY_FOR_PICKUP without a Staff handover; it is not evidence of readiness.
        String fulfilment = "READY_FOR_PICKUP".equals(order.getFulfilmentState()) && !order.isPickupReadyConfirmed()
            ? null : order.getFulfilmentState();
        var checkout = order.getGroupedCheckout();
        BigDecimal discount = order.getDiscountAmount().max(BigDecimal.ZERO);
        int together = (int) Math.max(1L, orders.countByCheckout(checkout.getId()));
        return new RetailOrderHistoryView(order.getId(), order.getLocationId(), order.getFulfilmentMethod(),
            order.getPaymentState().name(), fulfilment, money(order.getSubtotal()),
            money(order.getShippingCharge()), money(order.getPayableAmount()), checkout.getCreatedAt(), items,
            checkout.getId(), checkout.getPaymentMethod(), checkout.getCounterPaymentDeadline(),
            RetailOrderCodes.of(order.getId(), checkout.getCreatedAt()), money(discount), together);
    }

    private RetailCataloguePort.CartProductFact currentProduct(VariantLocationKey key) {
        try { return catalogue.readCartProduct(key.variantId(), key.locationId()); }
        catch (RetailIntegrationUnavailableException ex) {
            return new RetailCataloguePort.CartProductFact(null, null, null, null, null, null, false);
        }
    }

    private static String money(BigDecimal amount) { return amount.stripTrailingZeros().toPlainString(); }

    private record VariantLocationKey(Long variantId, Long locationId) {}
}
