package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.ComponentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.RestoreVoucherCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.VoucherRestorationOutcome;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * restoreVoucherForCancelledTicket (006 T110; 003.3 FR-042/FR-043, T155). Wraps the per-ticket cancellation of T023 /
 * D-012 instead of copying it: the ticket's points come back once per ticket, the group voucher once per group. The
 * validity rule is the one of every cancellation ({@link VoucherValidity}, FR-035): a voucher that is consumed and
 * whose accepted revision window has ended is not given back and never extended ({@code EXPIRED}); a held voucher is
 * always released.
 */
@Service
public class LoyaltyTicketRestorationService {

    private final LoyaltyPartLifecycleService partLifecycle;
    private final VoucherValidity validity;
    private final Clock clock;

    public LoyaltyTicketRestorationService(LoyaltyPartLifecycleService partLifecycle, VoucherValidity validity,
                                           ObjectProvider<Clock> clock) {
        this.partLifecycle = partLifecycle;
        this.validity = validity;
        this.clock = clock.getIfAvailable(Clock::systemUTC);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public VoucherRestorationOutcome restore(RestoreVoucherCommand command) {
        Instant now = Instant.now(clock);
        LoyaltyPartLifecycleService.TicketRestoration result = partLifecycle.cancelTicketByMland(
                command.groupCode(), command.ticketCode(), command.partRef(), now, benefit -> validity.consumedAndEnded(benefit, now));
        if (result.expired()) {
            return VoucherRestorationOutcome.EXPIRED;
        }
        boolean before = result.voucherBefore() == ComponentState.RESTORED
                || result.voucherBefore() == ComponentState.RELEASED;
        boolean after = result.voucherAfter() == ComponentState.RESTORED
                || result.voucherAfter() == ComponentState.RELEASED;
        if (before || !after) {
            // Already given back by an earlier call, or the group never used a voucher: nothing more to say.
            return VoucherRestorationOutcome.ALREADY_RESTORED;
        }
        return VoucherRestorationOutcome.RESTORED;
    }
}
