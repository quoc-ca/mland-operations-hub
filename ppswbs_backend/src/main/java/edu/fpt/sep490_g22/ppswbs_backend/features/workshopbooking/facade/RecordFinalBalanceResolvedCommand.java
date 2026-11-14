package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

public record RecordFinalBalanceResolvedCommand(
    String bookingReference,
    String settlementReference,
    String operationReference,
    String correlationReference
) {
    public RecordFinalBalanceResolvedCommand {
        if (bookingReference == null || bookingReference.isBlank()) {
            throw new IllegalArgumentException("bookingReference must not be blank");
        }
        if (settlementReference == null || settlementReference.isBlank()) {
            throw new IllegalArgumentException("settlementReference must not be blank");
        }
        if (operationReference == null || operationReference.isBlank()) {
            throw new IllegalArgumentException("operationReference must not be blank");
        }
    }
}
