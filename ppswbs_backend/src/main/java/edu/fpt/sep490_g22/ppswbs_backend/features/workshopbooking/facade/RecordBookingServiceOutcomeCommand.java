package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

public record RecordBookingServiceOutcomeCommand(
        String bookingReference,
        BookingServiceOutcome outcome,
        String settlementReference,
        String operationReference,
        String correlationReference) {
    public RecordBookingServiceOutcomeCommand {
        require(bookingReference, "bookingReference");
        if (outcome == null) throw new IllegalArgumentException("outcome must not be null");
        require(settlementReference, "settlementReference");
        require(operationReference, "operationReference");
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
    }
}
