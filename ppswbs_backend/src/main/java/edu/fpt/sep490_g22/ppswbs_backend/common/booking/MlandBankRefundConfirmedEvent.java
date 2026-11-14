package edu.fpt.sep490_g22.ppswbs_backend.common.booking;

import java.time.Instant;

/**
 * A bank refund that a Manager has matched against the statement (003.3 FR-039). Published inside the
 * confirming transaction so the refund allocation that was handed to the bank flow can be closed and the
 * business consequences applied — which FR-039 allows only after {@code REFUNDED}. PII-free: references and
 * an amount, never an account number.
 */
public record MlandBankRefundConfirmedEvent(String requestReference, String bookingReference,
                                            String sourceReference, long amountVnd, Instant confirmedAt) {
}
