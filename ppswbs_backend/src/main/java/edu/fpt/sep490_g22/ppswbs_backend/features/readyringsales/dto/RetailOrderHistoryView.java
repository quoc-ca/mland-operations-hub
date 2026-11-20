package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailOrderCodes;

import java.time.Instant;
import java.util.List;

/** Member-safe purchase facts and current catalogue display information. */
public record RetailOrderHistoryView(String id, Long locationId, String fulfilmentMethod,
    String paymentState, String fulfilmentState, String subtotal, String shippingCharge,
    String payableAmount, Instant orderedAt, List<Item> items, String checkoutId,
    String paymentMethod, Instant counterPaymentDeadline, String orderCode, String discountAmount,
    int checkoutOrderCount, Instant paymentDeadline, Instant paidAt, Instant completedAt) {
    /** {@code paidAt} and {@code completedAt} (received by the Member) are shown in the order details (Session 2026-11-20). */
    public RetailOrderHistoryView(String id, Long locationId, String fulfilmentMethod,
                                  String paymentState, String fulfilmentState, String subtotal, String shippingCharge,
                                  String payableAmount, Instant orderedAt, List<Item> items, String checkoutId,
                                  String paymentMethod, Instant counterPaymentDeadline, String orderCode,
                                  String discountAmount, int checkoutOrderCount, Instant paymentDeadline) {
        this(id, locationId, fulfilmentMethod, paymentState, fulfilmentState, subtotal, shippingCharge, payableAmount,
            orderedAt, items, checkoutId, paymentMethod, counterPaymentDeadline, orderCode, discountAmount,
            checkoutOrderCount, paymentDeadline, null, null);
    }
    /** {@code paymentDeadline}: until when an unpaid VNPay order is held (Session 2026-11-18); null otherwise. */
    public RetailOrderHistoryView(String id, Long locationId, String fulfilmentMethod,
                                  String paymentState, String fulfilmentState, String subtotal, String shippingCharge,
                                  String payableAmount, Instant orderedAt, List<Item> items, String checkoutId,
                                  String paymentMethod, Instant counterPaymentDeadline, String orderCode,
                                  String discountAmount, int checkoutOrderCount) {
        this(id, locationId, fulfilmentMethod, paymentState, fulfilmentState, subtotal, shippingCharge, payableAmount,
            orderedAt, items, checkoutId, paymentMethod, counterPaymentDeadline, orderCode, discountAmount,
            checkoutOrderCount, null, null, null);
    }
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
    /** {@code productId} lets the screens show the product's public image; null when unknown. */
    public record Item(Long variantId, String productName, String sku, Integer size,
                       int quantity, String unitPrice, String lineTotal, Long productId) {
        public Item(Long variantId, String productName, String sku, Integer size,
                    int quantity, String unitPrice, String lineTotal) {
            this(variantId, productName, sku, size, quantity, unitPrice, lineTotal, null);
        }
    }
}
