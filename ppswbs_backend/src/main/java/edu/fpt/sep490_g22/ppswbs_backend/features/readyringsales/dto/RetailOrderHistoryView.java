package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto;

import java.time.Instant;
import java.util.List;

/** Member-safe purchase facts and current catalogue display information. */
public record RetailOrderHistoryView(String id, Long locationId, String fulfilmentMethod,
    String paymentState, String fulfilmentState, String subtotal, String shippingCharge,
    String payableAmount, Instant orderedAt, List<Item> items) {
    public record Item(Long variantId, String productName, String sku, Integer size,
                       int quantity, String unitPrice, String lineTotal) { }
}
