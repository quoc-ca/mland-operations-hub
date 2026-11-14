package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

import java.time.Instant;
import java.util.List;

/**
 * Minimal Loyalty context of one booking group, with the ticket identities Settlement needs to read each
 * ticket's own attendance (003.3 FR-030). Contains no account or contact data.
 *
 * <p>A group holds exactly one accepted Loyalty transaction, so there is one earn for the whole group;
 * {@code memberOrigin} is false when the group reserved no benefit at all.
 */
public record BookingGroupLoyaltyOutcomeSource(boolean memberOrigin, String snapshotRef, long nextOutcomeVersion,
                                               Instant originalDeadline, List<GroupTicket> tickets) {

    public BookingGroupLoyaltyOutcomeSource {
        tickets = tickets == null ? List.of() : List.copyOf(tickets);
    }

    /** One ticket of the group: its own code, operations identity, and 003.1 ticketRef (L{line}-T{ordinal}). */
    public record GroupTicket(String ticketCode, String operationsTicketReference, String ticketRef) {
        public GroupTicket(String ticketCode, String operationsTicketReference) {
            this(ticketCode, operationsTicketReference, null);
        }
    }

    public static BookingGroupLoyaltyOutcomeSource none() {
        return new BookingGroupLoyaltyOutcomeSource(false, null, 0L, null, List.of());
    }
}
