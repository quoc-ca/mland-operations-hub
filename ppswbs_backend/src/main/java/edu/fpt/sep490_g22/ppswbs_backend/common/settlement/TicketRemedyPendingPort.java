package edu.fpt.sep490_g22.ppswbs_backend.common.settlement;

import java.util.Collection;
import java.util.Set;

/**
 * Neutral port: which of these tickets have a Mland replacement-session offer (FR-043) still waiting for the buyer's
 * answer. Workshop Booking shows it as a hint on the ticket; Settlement owns the answer and decides everything.
 */
public interface TicketRemedyPendingPort {
    Set<String> pendingTicketCodes(Collection<String> ticketCodes);
}
