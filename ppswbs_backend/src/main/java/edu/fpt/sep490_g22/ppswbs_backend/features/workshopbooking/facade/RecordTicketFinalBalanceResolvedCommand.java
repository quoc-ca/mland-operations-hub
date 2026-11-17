package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

/**
 * 003.3 concludes that one ticket's folio is settled (003.3 FR-008 per ticket, BE-DESIGN-0006, decided
 * 2026-11-14 by Hồ Hoàng Quốc): sent when the ticket folio becomes {@code FINALIZED}, which happens only
 * with a zero balance and no collection awaiting verification. Reference-only; no amount, contact or payment data.
 */
public record RecordTicketFinalBalanceResolvedCommand(
        String ticketCode,
        String settlementReference,
        String operationReference,
        String correlationReference
) {
    public RecordTicketFinalBalanceResolvedCommand {
        if (ticketCode == null || ticketCode.isBlank()) {
            throw new IllegalArgumentException("ticketCode must not be blank");
        }
        if (settlementReference == null || settlementReference.isBlank()) {
            throw new IllegalArgumentException("settlementReference must not be blank");
        }
        if (operationReference == null || operationReference.isBlank()) {
            throw new IllegalArgumentException("operationReference must not be blank");
        }
        if (correlationReference == null || correlationReference.isBlank()) {
            throw new IllegalArgumentException("correlationReference must not be blank");
        }
    }
}
