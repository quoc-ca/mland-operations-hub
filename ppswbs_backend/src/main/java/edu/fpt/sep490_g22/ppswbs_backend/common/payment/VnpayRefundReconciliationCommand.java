package edu.fpt.sep490_g22.ppswbs_backend.common.payment;

/** Reconciles one provider refund transaction against its immutable original payment. */
public record VnpayRefundReconciliationCommand(
        String operationReference,
        String merchantReference,
        String refundTransactionNumber,
        String originalTransactionDate,
        long expectedRefundAmountVnd,
        String orderInfo,
        String ipAddress) {
}
