package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

public record ApplyVerifiedDepositPaymentEvidenceCommand(
    String bookingReference,
    String verifiedEvidenceReference,
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
        if (operationReference == null || operationReference.isBlank()) {
            throw new IllegalArgumentException("operationReference must not be blank");
        }
    }
}
