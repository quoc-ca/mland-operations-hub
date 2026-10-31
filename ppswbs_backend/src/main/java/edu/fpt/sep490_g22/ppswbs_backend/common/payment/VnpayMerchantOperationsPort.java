package edu.fpt.sep490_g22.ppswbs_backend.common.payment;

/** Technical boundary for provider-owned QueryDR and refund operations. */
public interface VnpayMerchantOperationsPort {
    VnpayMerchantOperationResult queryTransaction(VnpayQueryDrCommand command);

    VnpayMerchantOperationResult requestFullRefund(VnpayRefundCommand command);
}
