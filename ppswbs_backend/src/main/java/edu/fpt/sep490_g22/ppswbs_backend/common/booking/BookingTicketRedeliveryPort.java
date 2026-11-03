package edu.fpt.sep490_g22.ppswbs_backend.common.booking;

import java.util.List;

/**
 * Neutral application port for requesting delivery of already-issued booking tickets.
 * Feature modules depend on this port instead of depending on each other.
 */
public interface BookingTicketRedeliveryPort {
    RedeliveryOutcome request(RedeliveryCommand command);

    record RedeliveryCommand(
            String bookingReference,
            List<String> activeUnusedTicketReferences,
            String operationReference,
            String correlationReference
    ) {
        public RedeliveryCommand {
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

    record RedeliveryOutcome(Status status, String reason) {
        public enum Status { ACCEPTED, REJECTED }
    }
}
