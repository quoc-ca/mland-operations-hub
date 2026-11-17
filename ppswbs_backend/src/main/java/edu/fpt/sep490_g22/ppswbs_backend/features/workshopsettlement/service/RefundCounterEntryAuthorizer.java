package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * T152 (FR-040, FR-032, CHK025): who may enter a refund account at the counter. A Manager, or an active Staff member
 * whose location scope contains the ticket's location and who is assigned to a session the ticket holds
 * ({@code requireManagerOrAssignedStaff}, the 2026-11-06 session-assignment rule, which an OTA add-on overcharge
 * follows too). A request of a whole group (a late payment never re-confirmed, excess money on a group) has no ticket
 * and is only answered through the account link, so the counter refuses it for every role (403).
 */
@Service
@RequiredArgsConstructor
public class RefundCounterEntryAuthorizer {

    private final WorkshopSettlementAuthorizationService authorization;
    private final WorkshopBookingSettlementAdapter booking;
    private final BankRefundService bankRefunds;

    @Transactional(readOnly = true)
    public CurrentAccountActor requireCounterEntry(CurrentAccountActor actor, String refundId) {
        authorization.requireManagerOrAnyStaff(actor);
        SettlementRefundRequest request = bankRefunds.require(refundId);
        String group = request.getGroupReference();
        if (group != null && group.equals(request.getBookingReference())) {
            throw new WorkshopSettlementAuthorizationException("COUNTER_ENTRY_NOT_ALLOWED");
        }
        var ticket = booking.getTicketFolioContext(request.getBookingReference())
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        return authorization.requireManagerOrAssignedStaff(actor, ticket.locationReference(), ticket.lineReference());
    }
}
