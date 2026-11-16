package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto;

import java.time.Instant;
import java.util.List;

/** The Manager's read-only transfer/VNPay reconciliation list (003.3 FR-056, T133). */
public final class ReconciliationDtos {

    private ReconciliationDtos() {
    }

    public enum Method { BANK_TRANSFER, VNPAY, BOOKING_TIME }

    public enum State { CONFIRMED, PENDING_VERIFICATION, VOIDED, EXCESS }

    /**
     * One row of {@code GET /workshop-reconciliation-collections}. One row per payment: {@code ticketCode} is null when
     * a booking-time payment covers several tickets (see {@code ticketCodes}); {@code locationIds} are the canonical
     * workshop_location ids of the payment, several for a multi-location booking-time payment, so per-location totals
     * must not be summed. Money is the contract's whole-VND string.
     */
    public record ReconciliationCollectionDto(
            String collectionId,
            Instant createdAt,
            String ticketCode,
            List<String> ticketCodes,
            String groupCode,
            String amount,
            String excessAmount,
            String refundRequestReference,
            Method method,
            State state,
            String transactionReference,
            String confirmedBy,
            List<String> locationIds,
            Instant confirmedAt,
            String note
    ) {
    }
}
