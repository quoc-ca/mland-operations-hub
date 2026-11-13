package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailOrderCodes;

import java.time.Instant;
import java.util.List;

/** Member-safe purchase facts and current catalogue display information. */
public record RetailOrderHistoryView(String id, Long locationId, String fulfilmentMethod,
    String paymentState, String fulfilmentState, String subtotal, String shippingCharge,
    String payableAmount, Instant orderedAt, List<Item> items, String checkoutId,
    String paymentMethod, Instant counterPaymentDeadline, String orderCode) {
    /** The display code (ML + yyMMdd + id prefix) is derived from the id and order time. */
    public RetailOrderHistoryView(String id, Long locationId, String fulfilmentMethod,
                                  String paymentState, String fulfilmentState, String subtotal, String shippingCharge,
                                  String payableAmount, Instant orderedAt, List<Item> items, String checkoutId,
                                  String paymentMethod, Instant counterPaymentDeadline) {
        this(id, locationId, fulfilmentMethod, paymentState, fulfilmentState, subtotal, shippingCharge,
            payableAmount, orderedAt, items, checkoutId, paymentMethod, counterPaymentDeadline,
            RetailOrderCodes.of(id, orderedAt));
    }
    public RetailOrderHistoryView(String id, Long locationId, String fulfilmentMethod,
                                  String paymentState, String fulfilmentState, String subtotal, String shippingCharge,
                                  String payableAmount, Instant orderedAt, List<Item> items) {
        this(id, locationId, fulfilmentMethod, paymentState, fulfilmentState, subtotal, shippingCharge,
            payableAmount, orderedAt, items, null, "VNPAY", null);
    }
    public record Item(Long variantId, String productName, String sku, Integer size,
                       int quantity, String unitPrice, String lineTotal) { }
}
