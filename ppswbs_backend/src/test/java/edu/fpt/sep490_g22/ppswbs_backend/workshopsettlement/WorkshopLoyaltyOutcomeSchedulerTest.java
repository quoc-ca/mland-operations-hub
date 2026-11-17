package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopLoyaltyOutcomeProcessor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopLoyaltyOutcomeScheduler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

class WorkshopLoyaltyOutcomeSchedulerTest {
    private final SettlementFinalBillRepository bills = mock(SettlementFinalBillRepository.class);
    private final WorkshopLoyaltyOutcomeProcessor processor = mock(WorkshopLoyaltyOutcomeProcessor.class);
    private final WorkshopLoyaltyOutcomeScheduler scheduler = new WorkshopLoyaltyOutcomeScheduler(bills, processor);

    @Test
    void scansOnlyFoliosWhoseLoyaltyOutcomeIsUndecidedAndProcessesEachBooking() {
        when(bills.findByLifecycleStatusInAndLoyaltyProcessedAtIsNullOrderByIdAsc(List.of("OPEN", "FINALIZED", "CLOSED")))
                .thenReturn(List.of(bill("BK-1", "OPEN"), bill("BK-2", "FINALIZED"), bill("BK-3", "CLOSED")));

        scheduler.processEligibleWorkshopOutcomes();

        verify(processor).process("BK-1");
        verify(processor).process("BK-2");
        verify(processor).process("BK-3");
    }

    @Test
    void failureForOneBookingDoesNotPreventTheNextCandidateFromBeingProcessed() {
        when(bills.findByLifecycleStatusInAndLoyaltyProcessedAtIsNullOrderByIdAsc(List.of("OPEN", "FINALIZED", "CLOSED")))
                .thenReturn(List.of(bill("BK-1", "OPEN"), bill("BK-2", "FINALIZED")));
        doThrow(new IllegalStateException("internal details")).when(processor).process("BK-1");

        scheduler.processEligibleWorkshopOutcomes();

        verify(processor).process("BK-1");
        verify(processor).process("BK-2");
    }

    private static SettlementFinalBill bill(String booking, String status) {
        return SettlementFinalBill.builder().bookingReference(booking).lifecycleStatus(status).build();
    }
}
