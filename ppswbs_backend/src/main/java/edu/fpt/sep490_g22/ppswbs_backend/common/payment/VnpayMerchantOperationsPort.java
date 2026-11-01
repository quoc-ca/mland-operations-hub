package edu.fpt.sep490_g22.ppswbs_backend.common.payment;

/** Technical boundary for provider-owned QueryDR and refund operations. */
public interface VnpayMerchantOperationsPort {
    VnpayMerchantOperationResult queryTransaction(VnpayQueryDrCommand command);

    /** Reconciles a specific refund using the provider transaction number returned by VNPay. */
    VnpayMerchantOperationResult queryRefund(VnpayRefundReconciliationCommand command);

    /** Requests a full (02) or partial (03) refund for one original payment. */
    default VnpayMerchantOperationResult requestRefund(VnpayRefundCommand command) {
        return requestFullRefund(command);
    }

    /** Compatibility entry point for callers that explicitly require a full refund. */
    VnpayMerchantOperationResult requestFullRefund(VnpayRefundCommand command);
}
