package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementChargeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementEvidenceConclusionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopLoyaltyOutcomeProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * 003.2 T109 (decided B): the booking-wide Loyalty path is gone with the single direct flow (003.1 FR-046). A
 * folio with a booking group is decided per ticket (see {@code GroupTicketLoyaltyOutcomeTest}); any other folio is
 * only marked processed. No booking-wide attendance outcome is read any more.
 */
@ExtendWith(MockitoExtension.class)
class WorkshopLoyaltyOutcomeProcessorTest {
    @Mock SettlementFinalBillRepository bills;
    @Mock SettlementChargeRepository charges;
    @Mock SettlementEvidenceConclusionRepository evidence;
    @Mock WorkshopBookingSettlementAdapter booking;
    @Mock WorkshopOperationsSettlementAdapter operations;
    @Mock LoyaltyFacade loyalty;

    @Test
    void aFolioWithoutAGroupIsMarkedProcessedAndNothingElseIsRead() {
        SettlementFinalBill bill = readyBill("FINALIZED");

        processor().process("BK-1");

        assertThat(bill.getLoyaltyProcessedAt()).isNotNull();
        verifyNoInteractions(loyalty, booking, operations);
    }

    @Test
    void aProcessedFolioIsSkippedEntirely() {
        SettlementFinalBill bill = readyBill("FINALIZED");
        processor().process("BK-1");
        assertThat(bill.getLoyaltyProcessedAt()).isNotNull();

        clearInvocations(bills);
        processor().process("BK-1");

        verify(bills, never()).save(any());
        verifyNoInteractions(loyalty, booking, operations);
    }

    @Test
    void anUnknownFolioStateIsLeftAlone() {
        SettlementFinalBill bill = readyBill("SOMETHING_ELSE");

        processor().process("BK-1");

        assertThat(bill.getLoyaltyProcessedAt()).isNull();
        verifyNoInteractions(loyalty, booking, operations);
    }

    @Test
    void aMissingFolioIsIgnored() {
        when(bills.findByBookingReferenceForUpdate("BK-1")).thenReturn(Optional.empty());

        processor().process("BK-1");

        verifyNoInteractions(loyalty, booking, operations);
    }

    private SettlementFinalBill readyBill(String lifecycle) {
        SettlementFinalBill bill = SettlementFinalBill.builder().bookingReference("BK-1").billReference("BILL-1")
                .lifecycleStatus(lifecycle).remainingAmountVnd(0L).acceptedDepositAmountVnd(50L)
                .postPromotionAmountVnd(100L).currencyCode("VND").build();
        when(bills.findByBookingReferenceForUpdate("BK-1")).thenReturn(Optional.of(bill));
        return bill;
    }

    private WorkshopLoyaltyOutcomeProcessor processor() {
        return new WorkshopLoyaltyOutcomeProcessor(bills, charges, evidence, booking, operations, loyalty);
    }
}
