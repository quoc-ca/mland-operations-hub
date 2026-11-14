package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailOrderCodes;

import java.time.Instant;
import java.util.List;

/** Member-safe purchase facts and current catalogue display information. */
public record RetailOrderHistoryView(String id, Long locationId, String fulfilmentMethod,
    String paymentState, String fulfilmentState, String subtotal, String shippingCharge,
    String payableAmount, Instant orderedAt, List<Item> items, String checkoutId,
    String paymentMethod, Instant counterPaymentDeadline, String orderCode, String discountAmount,
    int checkoutOrderCount) {
    /**
     * View without the branch discount share (voucher and points) and the number of branch orders placed together.
     * The discount defaults to "0" and the order is treated as the only one of its checkout.
     */
    public RetailOrderHistoryView(String id, Long locationId, String fulfilmentMethod,
                                  String paymentState, String fulfilmentState, String subtotal, String shippingCharge,
                                  String payableAmount, Instant orderedAt, List<Item> items, String checkoutId,
                                  String paymentMethod, Instant counterPaymentDeadline, String orderCode) {
        this(id, locationId, fulfilmentMethod, paymentState, fulfilmentState, subtotal, shippingCharge, payableAmount,
            orderedAt, items, checkoutId, paymentMethod, counterPaymentDeadline, orderCode, "0", 1);
    }
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
