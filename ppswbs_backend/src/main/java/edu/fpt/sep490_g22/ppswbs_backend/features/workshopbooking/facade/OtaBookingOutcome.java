package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

import java.util.List;
import java.util.UUID;

public record OtaBookingOutcome(Status status, String bookingReference,
                                List<UUID> ticketReferences, String rejectionReason) {
    public enum Status { ACCEPTED, REJECTED }
    public OtaBookingOutcome { ticketReferences = ticketReferences == null ? List.of() : List.copyOf(ticketReferences); }
    public static OtaBookingOutcome accepted(String ref, List<UUID> tickets) { return new OtaBookingOutcome(Status.ACCEPTED, ref, tickets, null); }
    public static OtaBookingOutcome rejected(String reason) { return new OtaBookingOutcome(Status.REJECTED, null, List.of(), reason); }
}
