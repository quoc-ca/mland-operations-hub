package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

public record ApplyVerifiedDepositPaymentEvidenceCommand(
    String bookingReference,
    String verifiedEvidenceReference,
    String merchantReference,
    Long amountVnd,
    String currencyCode,
    String operationReference,
    String correlationReference
) {
    public ApplyVerifiedDepositPaymentEvidenceCommand {
        if (bookingReference == null || bookingReference.isBlank()) {
            throw new IllegalArgumentException("bookingReference must not be blank");
        }
        if (verifiedEvidenceReference == null || verifiedEvidenceReference.isBlank()) {
            throw new IllegalArgumentException("verifiedEvidenceReference must not be blank");
        }
        if (merchantReference == null || merchantReference.isBlank()) throw new IllegalArgumentException("merchantReference must not be blank");
        if (amountVnd == null || amountVnd <= 0) throw new IllegalArgumentException("amountVnd must be positive");
        if (!"VND".equals(currencyCode)) throw new IllegalArgumentException("currencyCode must be VND");
        if (operationReference == null || operationReference.isBlank()) {
            throw new IllegalArgumentException("operationReference must not be blank");
        }
    }
}
