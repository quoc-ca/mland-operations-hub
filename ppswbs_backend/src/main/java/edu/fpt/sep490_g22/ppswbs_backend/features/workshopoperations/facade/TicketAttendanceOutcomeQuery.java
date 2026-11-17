package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

/**
 * PII-free read of one ticket's terminal attendance outcome (003.3 FR-030). A booking group holds several
 * people under one booking reference, so a ticket's own outcome cannot be read with
 * the removed booking-wide query: {@code ticketReference} is the identity Operations issued for that
 * ticket, which 003.1 stores on the ticket booking at confirmation.
 */
public record TicketAttendanceOutcomeQuery(String ticketReference) {}
