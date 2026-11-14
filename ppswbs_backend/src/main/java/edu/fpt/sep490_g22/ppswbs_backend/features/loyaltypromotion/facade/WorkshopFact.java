package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Workshop fact supplied by the booking owner. Two shapes:
 * <ul>
 *   <li><b>Single package</b> (original contract): one package bought once, with every required session the Member
 *       selected (a Silverclay booking has two). The package fee covers all sessions; the discount cap never
 *       multiplies by participants or sessions. {@code tickets} is empty.</li>
 *   <li><b>Cart</b> (Session 2026-11-12, 003.1 FR-030/FR-033), built with {@link #cart(List)}: one or more tickets,
 *       possibly of different packages. Voucher scope is checked per ticket; a ticket outside the scope is not
 *       discounted while the others still are. {@code tickets} keeps the cart order (the order owner's tie-break),
 *       {@code participantCount} is the number of tickets, {@code grossPackageAmount} is the sum of ticket prices,
 *       {@code packageId} is the first ticket's package and {@code sessions} is the distinct union of every ticket's
 *       sessions; use {@code tickets} for anything per ticket.</li>
 * </ul>
 * Sessions are kept in a stable order (date, then reference) so the same booking always gives the same quote digest.
 */
public record WorkshopFact(long packageId, int participantCount, BigInteger grossPackageAmount,
                           List<WorkshopSessionFact> sessions, List<WorkshopTicketFact> tickets) {

    private static final Comparator<WorkshopSessionFact> SESSION_ORDER =
            Comparator.comparing(WorkshopSessionFact::workshopDate).thenComparing(WorkshopSessionFact::sessionRef);

    public WorkshopFact {
        if (tickets != null && tickets.stream().anyMatch(Objects::isNull)) {
            throw LoyaltyException.invalidField("workshop.tickets", "Tickets must be distinct.");
        }
        tickets = tickets == null ? List.of() : List.copyOf(tickets);
        if (!tickets.isEmpty()) {
            sessions = union(tickets);
            requireCartConsistency(packageId, participantCount, grossPackageAmount, tickets);
        }
        if (packageId <= 0 || participantCount <= 0) {
            throw LoyaltyException.invalidField("workshop", "Workshop facts are incomplete.");
        }
        if (grossPackageAmount == null || grossPackageAmount.signum() < 0) {
            throw LoyaltyException.invalidField("workshop", "Package fee must not be negative.");
        }
        if (sessions == null || sessions.isEmpty()) {
            throw LoyaltyException.invalidField("workshop.sessions", "At least one session is required.");
        }
        Set<String> seen = new HashSet<>();
        for (WorkshopSessionFact session : sessions) {
            if (session == null || !seen.add(session.sessionRef())) {
                throw LoyaltyException.invalidField("workshop.sessions", "Sessions must be distinct.");
            }
        }
        sessions = sessions.stream().sorted(SESSION_ORDER).toList();
    }

    /** Single-package shape (original contract), kept so existing callers keep compiling and keep their digest. */
    public WorkshopFact(long packageId, int participantCount, BigInteger grossPackageAmount,
                        List<WorkshopSessionFact> sessions) {
        this(packageId, participantCount, grossPackageAmount, sessions, List.of());
    }

    /** Convenience for a one-session package. */
    public static WorkshopFact singleSession(long packageId, int participantCount, BigInteger grossPackageAmount,
                                             WorkshopSessionFact session) {
        return new WorkshopFact(packageId, participantCount, grossPackageAmount, List.of(session));
    }

    /** A cart of one or more tickets in cart order (Session 2026-11-12). */
    public static WorkshopFact cart(List<WorkshopTicketFact> tickets) {
        if (tickets == null || tickets.isEmpty()) {
            throw LoyaltyException.invalidField("workshop.tickets", "A cart needs at least one ticket.");
        }
        if (tickets.stream().anyMatch(Objects::isNull)) {
            throw LoyaltyException.invalidField("workshop.tickets", "Tickets must be distinct.");
        }
        BigInteger total = tickets.stream().map(WorkshopTicketFact::ticketPrice).reduce(BigInteger.ZERO, BigInteger::add);
        return new WorkshopFact(tickets.get(0).packageId(), tickets.size(), total, null, tickets);
    }

    /** True for the cart shape; the single-package shape has no tickets. */
    public boolean isCart() {
        return !tickets.isEmpty();
    }

    private static void requireCartConsistency(long packageId, int participantCount, BigInteger gross,
                                               List<WorkshopTicketFact> tickets) {
        Set<String> refs = new HashSet<>();
        BigInteger total = BigInteger.ZERO;
        for (WorkshopTicketFact ticket : tickets) {
            if (ticket == null || !refs.add(ticket.ticketRef())) {
                throw LoyaltyException.invalidField("workshop.tickets", "Tickets must be distinct.");
            }
            total = total.add(ticket.ticketPrice());
        }
        if (participantCount != tickets.size() || gross == null || gross.compareTo(total) != 0
                || packageId != tickets.get(0).packageId()) {
            throw LoyaltyException.invalidField("workshop.tickets",
                    "Cart totals must match its tickets (one ticket per participant).");
        }
    }

    /** Distinct sessions of every ticket; two tickets of the same session share it. */
    private static List<WorkshopSessionFact> union(List<WorkshopTicketFact> tickets) {
        Map<String, WorkshopSessionFact> byRef = new LinkedHashMap<>();
        for (WorkshopTicketFact ticket : tickets) {
            for (WorkshopSessionFact session : ticket.sessions()) {
                WorkshopSessionFact known = byRef.putIfAbsent(session.sessionRef(), session);
                if (known != null && !known.equals(session)) {
                    throw LoyaltyException.invalidField("workshop.tickets",
                            "The same session must have the same branch and date on every ticket.");
                }
            }
        }
        return new ArrayList<>(byRef.values());
    }
}
