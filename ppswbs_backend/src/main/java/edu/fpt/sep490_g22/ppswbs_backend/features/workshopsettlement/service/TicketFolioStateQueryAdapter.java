package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketBookingRef;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketFolioStateQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBillLifecycleStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Answers Operations' folio question (003.2 FR-062); a folio is keyed by the booking ticket code. */
@Component
@RequiredArgsConstructor
public class TicketFolioStateQueryAdapter implements TicketFolioStateQuery {
    private final WorkshopBookingSettlementAdapter booking;
    private final SettlementFinalBillRepository bills;

    @Override
    @Transactional(readOnly = true)
    public boolean isTicketFolioFinalized(String operationsTicketReference) {
        String ticketCode = booking.findTicketBookingByOperationsReference(operationsTicketReference)
                .map(TicketBookingRef::ticketCode)
                .orElse(operationsTicketReference);
        return bills.findByBookingReference(ticketCode)
                .map(bill -> SettlementFinalBillLifecycleStatus.FINALIZED.name().equals(bill.getLifecycleStatus()))
                .orElse(false);
    }
}
