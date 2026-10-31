package edu.fpt.sep490_g22.ppswbs_backend.common.payment;

/** Redacted operation state. It never contains a checksum, secret, or raw response body. */
public record VnpayMerchantOperationResult(
        String operationReference,
        String operationKind,
        String status,
        String providerResponseCode,
        String transactionType,
        String transactionStatus,
        String providerTransactionNumber,
        String merchantReference,
        Long amountVnd,
        boolean responseSignatureVerified) {
}
