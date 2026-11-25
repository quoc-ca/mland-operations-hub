package edu.fpt.sep490_g22.ppswbs_backend.common.settlement;

import java.util.Collection;
import java.util.Map;

/**
 * Neutral port: the money position a ticket's folio holds right now. Workshop Booking only shows it next to the
 * booking-time price; Settlement owns the folio and the balance rule. A ticket without a folio is simply absent.
 */
public interface TicketBalancePort {

    /**
     * @param totalVnd      price after promotion plus confirmed add-on charges
     * @param addOnVnd      confirmed add-on charges
     * @param paidVnd       everything settled so far, deposit included
     * @param balanceDueVnd what is still owed, never negative
     */
    record TicketBalance(long totalVnd, long addOnVnd, long paidVnd, long balanceDueVnd) {
    }

    Map<String, TicketBalance> balancesOf(Collection<String> ticketCodes);
}
