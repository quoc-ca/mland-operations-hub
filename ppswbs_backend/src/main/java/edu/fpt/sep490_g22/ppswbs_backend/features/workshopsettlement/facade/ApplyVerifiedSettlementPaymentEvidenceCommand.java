package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade;

public record ApplyVerifiedSettlementPaymentEvidenceCommand(
        String bookingReference,
        String verifiedEvidenceReference,
        String merchantReference,
        String paymentKind,
        Long amountVnd,
        String currencyCode,
        String providerTransactionNumber,
        String originalProviderCreateDate,
        String operationReference,
        String correlationReference) {
    public ApplyVerifiedSettlementPaymentEvidenceCommand(String bookingReference, String verifiedEvidenceReference,
            String merchantReference, String paymentKind, Long amountVnd, String currencyCode,
            String operationReference, String correlationReference) {
        this(bookingReference, verifiedEvidenceReference, merchantReference, paymentKind, amountVnd,
                currencyCode, null, null, operationReference, correlationReference);
    }
    public ApplyVerifiedSettlementPaymentEvidenceCommand {
        require(bookingReference, "bookingReference");
        require(verifiedEvidenceReference, "verifiedEvidenceReference");
        require(merchantReference, "merchantReference");
        require(paymentKind, "paymentKind");
        require(currencyCode, "currencyCode");
        if (!paymentKind.equals("FINAL_PAYMENT") && !paymentKind.equals("REFUND")) {
            throw new IllegalArgumentException("paymentKind must be FINAL_PAYMENT or REFUND");
        }
        if (amountVnd == null || amountVnd <= 0) throw new IllegalArgumentException("amountVnd must be positive");
        if (!currencyCode.equals("VND")) throw new IllegalArgumentException("currencyCode must be VND");
        require(operationReference, "operationReference");
        require(correlationReference, "correlationReference");
        if (paymentKind.equals("FINAL_PAYMENT")) {
            require(providerTransactionNumber, "providerTransactionNumber");
            require(originalProviderCreateDate, "originalProviderCreateDate");
            if (!originalProviderCreateDate.matches("[0-9]{14}"))
                throw new IllegalArgumentException("originalProviderCreateDate must be yyyyMMddHHmmss");
        }
    }
    private static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
