package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

public record RecordBookingRefundStatusCommand(
        String bookingReference,
        BookingRefundStatus status,
        String settlementReference,
        String operationReference,
        String correlationReference) {
    public RecordBookingRefundStatusCommand {
        require(bookingReference, "bookingReference");
        if (status == null) throw new IllegalArgumentException("status must not be null");
        require(settlementReference, "settlementReference");
        require(operationReference, "operationReference");
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
    }
}
