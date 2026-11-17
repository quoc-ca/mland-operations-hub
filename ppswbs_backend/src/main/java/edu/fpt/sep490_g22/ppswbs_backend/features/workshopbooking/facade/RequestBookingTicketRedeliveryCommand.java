package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

import java.util.List;

public record RequestBookingTicketRedeliveryCommand(
    String bookingReference,
    List<String> activeUnusedTicketReferences,
    String operationReference,
    String correlationReference
) {
    public RequestBookingTicketRedeliveryCommand {
        if (bookingReference == null || bookingReference.isBlank()) {
            throw new IllegalArgumentException("bookingReference must not be blank");
        }
        if (activeUnusedTicketReferences == null || activeUnusedTicketReferences.isEmpty()) {
            throw new IllegalArgumentException("activeUnusedTicketReferences must not be empty");
        }
        if (operationReference == null || operationReference.isBlank()) {
            throw new IllegalArgumentException("operationReference must not be blank");
        }
    }
}
