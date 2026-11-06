package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingOutcomeStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingRejectionReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingServiceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingServiceOutcomeResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.FinalBalanceResolutionOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SettlementReadiness;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBillLifecycleStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinalBookingBillServiceTest {
    @Mock SettlementFinalBillRepository bills;
    @Mock WorkshopBookingSettlementAdapter booking;
    @Mock WorkshopOperationsSettlementAdapter operations;
    @Mock WorkshopSettlementAuthorizationService authorization;
    @Mock SettlementBusinessAuditRepository audits;

    @Test
    void existingBillIsReturnedWithoutRecalculatingOrCallingUpstream() {
        SettlementFinalBill existing = SettlementFinalBill.builder().bookingReference("BK-1")
                .billReference("BILL-1").postPromotionAmountVnd(80_000L).acceptedDepositAmountVnd(40_000L)
                .remainingAmountVnd(40_000L).currencyCode("VND").lifecycleStatus("OPEN").build();
        when(bills.findByBookingReference("BK-1")).thenReturn(Optional.of(existing));
        SettlementFinalBill result = new FinalBookingBillService(bills, booking, operations, authorization, audits).create("BK-1", "OP-1", "CORR-1");
        assertThat(result).isSameAs(existing);
        verifyNoInteractions(booking, operations);
    }

    @Test
    void createThrowsConflictIfBillNotFound() {
        when(bills.findByBookingReference("BK-1")).thenReturn(Optional.empty());
        FinalBookingBillService service = new FinalBookingBillService(bills, booking, operations, authorization, audits);
        assertThatThrownBy(() -> service.create("BK-1", "OP-1", "CORR-1"))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessage("FINAL_BILL_NOT_FOUND");
    }

    @Test
    void finalizesZeroBalanceForAuthorizedStaff() {
        SettlementFinalBill bill = openBill();
        bill.setRemainingAmountVnd(0L);
        when(bills.findByBookingReference("BK-1")).thenReturn(Optional.of(bill));
        when(operations.readiness("BK-1")).thenReturn(new SettlementReadiness(true));
        when(bills.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(booking.recordFinalBalanceResolved(any(), any(), any(), any())).thenReturn(FinalBalanceResolutionOutcome.accepted());
        when(booking.recordBookingServiceOutcome(any(), eq(BookingServiceOutcome.COMPLETED), any(), any(), any()))
                .thenReturn(BookingServiceOutcomeResult.accepted());

        var service = new FinalBookingBillService(bills, booking, operations, authorization, audits);
        var result = service.finalizeAuthorized("BK-1", 0L, "OPS-FINALIZE-1", "CORR-FINALIZE-1");

        assertThat(result.getLifecycleStatus()).isEqualTo("FINALIZED");
        verify(booking).recordFinalBalanceResolved(eq("BK-1"), eq("BILL-1"), any(), any());
        verify(booking).recordBookingServiceOutcome(eq("BK-1"), eq(BookingServiceOutcome.COMPLETED), eq("BILL-1"), any(), any());
    }

    @Test
    void rejectsFinalizationWhileAnyBalanceRemainsEvenForManager() {
        when(bills.findByBookingReference("BK-1")).thenReturn(Optional.of(openBill()));
        when(operations.readiness("BK-1")).thenReturn(new SettlementReadiness(true));
        var service = new FinalBookingBillService(bills, booking, operations, authorization, audits);
        assertThatThrownBy(() -> service.finalizeAuthorized("BK-1", 40_000L))
                .isInstanceOf(WorkshopSettlementConflictException.class).hasMessage("OUTSTANDING_BALANCE");
        verify(bills, never()).saveAndFlush(any());
        verify(booking, never()).recordFinalBalanceResolved(any(), any(), any(), any());
    }

    @Test
    void finalizationRecordsAuditWhenBookingRejectsServiceOutcome() {
        SettlementFinalBill bill = openBill();
        bill.setRemainingAmountVnd(0L);
        when(bills.findByBookingReference("BK-1")).thenReturn(Optional.of(bill));
        when(operations.readiness("BK-1")).thenReturn(new SettlementReadiness(true));
        when(bills.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(booking.recordFinalBalanceResolved(any(), any(), any(), any())).thenReturn(FinalBalanceResolutionOutcome.accepted());
        when(booking.recordBookingServiceOutcome(any(), eq(BookingServiceOutcome.COMPLETED), any(), any(), any()))
                .thenReturn(BookingServiceOutcomeResult.rejected(BookingRejectionReason.INVALID_STATE));

        var service = new FinalBookingBillService(bills, booking, operations, authorization, audits);
        var result = service.finalizeAuthorized("BK-1", 0L, "OPS-FINALIZE-1", "CORR-FINALIZE-1");

        assertThat(result.getLifecycleStatus()).isEqualTo("FINALIZED");
        verify(audits).save(argThat(audit -> "BOOKING_SERVICE_OUTCOME_REJECTED".equals(audit.getActionCode())));
    }

    private static SettlementFinalBill openBill() {
        return SettlementFinalBill.builder().billReference("BILL-1").bookingReference("BK-1")
                .postPromotionAmountVnd(80_000L).acceptedDepositAmountVnd(40_000L)
                .remainingAmountVnd(40_000L).currencyCode("VND").lifecycleStatus("OPEN").build();
    }
}
