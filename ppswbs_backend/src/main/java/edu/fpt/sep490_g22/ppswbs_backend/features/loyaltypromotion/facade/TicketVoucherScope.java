package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

import java.math.BigInteger;

/**
 * Answer to {@link TicketVoucherScopeQuery}.
 *
 * @param voucherApplied whether the accepted transaction carries a voucher that is still held or consumed; an automatic
 *                       workshop discount or points alone are never lost on a session move (FR-045)
 * @param inScope        whether the ticket with the proposed sessions satisfies the accepted voucher revision's package,
 *                       branch, date and session targets (every session must match); {@code true} without a voucher
 * @param lostDiscount   the ticket's allocated voucher share it would lose: its accepted part's programme discount when
 *                       it leaves scope, otherwise zero; never computed anew
 */
public record TicketVoucherScope(boolean voucherApplied, boolean inScope, BigInteger lostDiscount) {

    public static TicketVoucherScope noVoucher() {
        return new TicketVoucherScope(false, true, BigInteger.ZERO);
    }
}
