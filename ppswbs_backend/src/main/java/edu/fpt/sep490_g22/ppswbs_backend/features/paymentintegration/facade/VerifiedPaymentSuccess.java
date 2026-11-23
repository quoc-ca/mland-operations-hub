package edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.facade;

import java.time.Instant;

public record VerifiedPaymentSuccess(
        PaymentOwnerKind ownerKind,
        String ownerReference,
        String paymentReference,
        long amountVnd,
        String currencyCode,
        String gatewayTransactionReference,
        String correlationId,
        Instant verifiedAt
) {
    public VerifiedPaymentSuccess(
            PaymentOwnerKind ownerKind,
            String ownerReference,
            String paymentReference,
            String gatewayTransactionReference,
            long amountVnd,
            String currencyCode
    ) {
        this(ownerKind, ownerReference, paymentReference, amountVnd, currencyCode,
                gatewayTransactionReference, "VNPAY-" + gatewayTransactionReference, Instant.now());
    }
}
