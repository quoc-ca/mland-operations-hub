package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

/**
 * The 003.1 identity of a ticket booking, found from its 003.2 ticket reference (003.1 T099, decision 2026-11-13).
 * Carries no contact, payment or attendance data.
 */
public record TicketBookingRef(String ticketCode, String groupCode) {
}
