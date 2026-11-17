package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One ticket of a workshop cart (Session 2026-11-12, 003.1 FR-030/FR-033), as the booking owner verified it: an
 * opaque ticket reference owned by 003.1, the package, the ticket price before benefits and every required session of
 * that package. One ticket is one participant; a multi-session package is still one ticket. Voucher scope (package,
 * branch, date, session) is checked per ticket, and every session of the ticket must satisfy it.
 */
public record WorkshopTicketFact(String ticketRef, long packageId, BigInteger ticketPrice,
                                 List<WorkshopSessionFact> sessions) {

    public static final int MAX_TICKET_REF_LENGTH = 64;

    public WorkshopTicketFact {
        ticketRef = ticketRef == null ? "" : ticketRef.trim();
        if (ticketRef.isEmpty() || ticketRef.length() > MAX_TICKET_REF_LENGTH || packageId <= 0) {
            throw LoyaltyException.invalidField("workshop.tickets", "Ticket facts are incomplete.");
        }
        if (ticketPrice == null || ticketPrice.signum() < 0) {
            throw LoyaltyException.invalidField("workshop.tickets", "Ticket price must not be negative.");
        }
        if (sessions == null || sessions.isEmpty()) {
            throw LoyaltyException.invalidField("workshop.tickets", "A ticket needs at least one session.");
        }
        Set<String> seen = new HashSet<>();
        for (WorkshopSessionFact session : sessions) {
            if (session == null || !seen.add(session.sessionRef())) {
                throw LoyaltyException.invalidField("workshop.tickets", "Sessions of a ticket must be distinct.");
            }
        }
        sessions = sessions.stream()
                .sorted(Comparator.comparing(WorkshopSessionFact::workshopDate).thenComparing(WorkshopSessionFact::sessionRef))
                .toList();
    }
}
