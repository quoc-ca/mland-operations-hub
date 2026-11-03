package edu.fpt.sep490_g22.ppswbs_backend.common.payment;

public record VnpayQueryDrCommand(
        String operationReference,
        String merchantReference,
        String originalTransactionDate,
        String orderInfo,
        String ipAddress) {
}
