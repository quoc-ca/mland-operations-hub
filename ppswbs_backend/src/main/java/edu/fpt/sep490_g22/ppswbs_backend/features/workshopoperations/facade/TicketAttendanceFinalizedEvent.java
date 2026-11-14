package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.time.Instant;

/**
 * Reference-only post-commit notification that one ticket's attendance correction window has closed
 * (003.2 T092 / 003.3 T105, BE-DESIGN-0004). Carries no PII and no outcome; the consumer resolves
 * {@code ticketReference} and reads {@link WorkshopOperationsFacade#getTicketAttendanceOutcome}.
 */
public record TicketAttendanceFinalizedEvent(
        String eventId,
        String ticketReference,
        Instant windowClosedAt
) {}

