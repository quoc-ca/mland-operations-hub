package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto;

import java.util.List;

/** {@code Ticket} of the cart contract after a recipient command; carries the masked recipient email only. */
public record TicketRecipientResponse(
        String ticketCode,
        String status,
        String collectionKind,
        String recipientEmailMasked,
        String attendanceStatus,
        List<String> sessionReferences,
        Integer onlineRecipientChangesRemaining
) {
}
