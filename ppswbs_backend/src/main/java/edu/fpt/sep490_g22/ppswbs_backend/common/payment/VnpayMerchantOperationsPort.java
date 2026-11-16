package edu.fpt.sep490_g22.ppswbs_backend.common.payment;

/** Technical boundary for the provider-owned QueryDR operation. VNPay Refund is not used (refunds are the manual bank flow, 003.3 FR-038a). */
public interface VnpayMerchantOperationsPort {
    VnpayMerchantOperationResult queryTransaction(VnpayQueryDrCommand command);
}
