package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade;

public record ApplyVerifiedSettlementPaymentEvidenceCommand(
        String bookingReference,
        String verifiedEvidenceReference,
        String operationReference,
        String correlationReference) {
    public ApplyVerifiedSettlementPaymentEvidenceCommand {
        require(bookingReference, "bookingReference");
        require(verifiedEvidenceReference, "verifiedEvidenceReference");
        require(operationReference, "operationReference");
        require(correlationReference, "correlationReference");
    }
    private static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
