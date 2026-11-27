package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketBookingRef;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.TicketFolioStateQueryAdapter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** FR-062: only a finalized folio, found by the booking ticket code, counts. */
class TicketFolioStateQueryAdapterTest {

    private final WorkshopBookingSettlementAdapter booking = mock(WorkshopBookingSettlementAdapter.class);
    private final SettlementFinalBillRepository bills = mock(SettlementFinalBillRepository.class);

    @ParameterizedTest
    @CsvSource({"FINALIZED,true", "OPEN,false", "CLOSED,false"})
    void readsTheFolioOfTheBookingTicket(String status, boolean finalized) {
        TicketBookingRef ticket = mock(TicketBookingRef.class);
        when(ticket.ticketCode()).thenReturn("GRP-A-L0-T1");
        when(booking.findTicketBookingByOperationsReference("ref-1")).thenReturn(Optional.of(ticket));
        SettlementFinalBill bill = mock(SettlementFinalBill.class);
        when(bill.getLifecycleStatus()).thenReturn(status);
        when(bills.findByBookingReference("GRP-A-L0-T1")).thenReturn(Optional.of(bill));

        assertThat(new TicketFolioStateQueryAdapter(booking, bills).isTicketFolioFinalized("ref-1")).isEqualTo(finalized);
    }
}
