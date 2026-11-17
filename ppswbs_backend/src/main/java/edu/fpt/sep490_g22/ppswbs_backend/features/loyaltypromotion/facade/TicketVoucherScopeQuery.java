package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

import java.math.BigInteger;
import java.util.List;

/**
 * 003.3 asks whether one ticket of an accepted workshop transaction would stay inside its voucher's scope with a
 * proposed session set (003.3 FR-045, ERROR-LOG BE-DESIGN-0007, request
 * {@code ticket-change-voucher-scope-request.md}). Read-only: holds no quota and changes no snapshot.
 *
 * @param sourceKind       the accepted transaction's kind ({@link SourceKind#WORKSHOP})
 * @param sourceRef        the accepted transaction's reference (the 003.1 group code)
 * @param ticketRef        the ticket reference 003.1 sent at accept ({@code L{line}-T{ordinal}})
 * @param packageId        the ticket's package, which a session change never alters
 * @param proposedSessions every session the ticket would hold after the change
 */
public record TicketVoucherScopeQuery(SourceKind sourceKind, String sourceRef, String ticketRef, long packageId,
                                      List<WorkshopSessionFact> proposedSessions) {

    public TicketVoucherScopeQuery {
        if (sourceKind != SourceKind.WORKSHOP) {
            throw LoyaltyException.invalidField("sourceKind", "Only a workshop ticket has a voucher scope to check.");
        }
        sourceRef = sourceRef == null ? "" : sourceRef.trim();
        if (sourceRef.isEmpty()) {
            throw LoyaltyException.invalidField("sourceRef", "The source reference is required.");
        }
        // Validates the ticket reference, the package and the session set with the cart's own rules.
        WorkshopTicketFact fact = new WorkshopTicketFact(ticketRef, packageId, BigInteger.ZERO, proposedSessions);
        ticketRef = fact.ticketRef();
        proposedSessions = fact.sessions();
    }

    /** The ticket as the scope rule reads it; the price plays no part in scope. */
    public WorkshopTicketFact asTicketFact() {
        return new WorkshopTicketFact(ticketRef, packageId, BigInteger.ZERO, proposedSessions);
    }
}
