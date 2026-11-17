package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

import java.time.Instant;
import java.util.List;

/**
 * Read-only folio context of one ticket inside a booking group (003.1 T107 for 003.3 T099, T106).
 * Replaces confirmedProjection for a ticket folio; carries no contact PII, payment data or QR.
 */
public record TicketFolioContext(
        String ticketCode,
        String groupCode,
        String lineReference,
        String packageReference,
        Long locationReference,
        List<String> sessionReferences,
        Long buyerMemberAccountId,
        String operationsTicketReference,
        Instant firstSessionStart,
        long pointsUsed,
        String partRef,
        long voucherDiscountVnd
) {
    public TicketFolioContext(
            String ticketCode,
            String groupCode,
            String lineReference,
            String packageReference,
            Long locationReference,
            List<String> sessionReferences,
            Long buyerMemberAccountId
    ) {
        this(ticketCode, groupCode, lineReference, packageReference, locationReference, sessionReferences,
                buyerMemberAccountId, null, null, 0L, null, 0L);
    }

    public TicketFolioContext(
            String ticketCode,
            String groupCode,
            String lineReference,
            String packageReference,
            Long locationReference,
            List<String> sessionReferences,
            Long buyerMemberAccountId,
            String operationsTicketReference,
            Instant firstSessionStart,
            long pointsUsed,
            String partRef
    ) {
        this(ticketCode, groupCode, lineReference, packageReference, locationReference, sessionReferences,
                buyerMemberAccountId, operationsTicketReference, firstSessionStart, pointsUsed, partRef, 0L);
    }
}
