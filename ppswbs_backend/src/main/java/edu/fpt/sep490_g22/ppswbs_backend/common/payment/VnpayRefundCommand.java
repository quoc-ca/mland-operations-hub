package edu.fpt.sep490_g22.ppswbs_backend.common.payment;

public record VnpayRefundCommand(
        String operationReference,
        String merchantReference,
        String providerTransactionNumber,
        String originalTransactionDate,
        long amountVnd,
        String orderInfo,
        String createdBy,
        String ipAddress) {
}
