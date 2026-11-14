package edu.fpt.sep490_g22.ppswbs_backend.common.booking;

import java.time.Instant;

/**
 * One confirmed ticket of a booking group (003.1 FR-039). Published once per ticket in the confirming
 * transaction so Settlement opens one OPEN folio per ticket (003.3 FR-030, T093). PII-free: the amounts are
 * the ticket's own final price, what the single booking-time payment collected for it and what is left for
 * checkout.
 */
public record MlandTicketConfirmedEvent(String groupReference, String ticketReference, long finalPriceVnd,
                                        long collectedAtBookingVnd, long remainingVnd, Instant confirmedAt) {
}
