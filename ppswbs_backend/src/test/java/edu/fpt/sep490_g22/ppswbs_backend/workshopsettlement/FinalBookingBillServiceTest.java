package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ConfirmedBookingFinancialProjection;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SettlementReadiness;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinalBookingBillServiceTest {
    @Mock SettlementFinalBillRepository bills;
    @Mock WorkshopBookingSettlementAdapter booking;
    @Mock WorkshopOperationsSettlementAdapter operations;
    @Mock WorkshopSettlementAuthorizationService authorization;

    @Test void createsOneFrozenBillFromUpstreamProjection() {
        when(bills.findByBookingReference("BK-1")).thenReturn(Optional.empty());
        when(operations.readiness("BK-1")).thenReturn(new SettlementReadiness(true));
        when(booking.confirmedProjection("BK-1")).thenReturn(new ConfirmedBookingFinancialProjection(
                "PKG-1", 100_000L, 80_000L, 40_000L, 40_000L, 2, java.util.List.of("S-1"), 7L));
        when(bills.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        FinalBookingBillService service = new FinalBookingBillService(bills, booking, operations, authorization);
        SettlementFinalBill result = service.create("BK-1", "OP-1", "CORR-1");
        assertThat(result.getBookingReference()).isEqualTo("BK-1");
        assertThat(result.getPostPromotionAmountVnd()).isEqualTo(80_000L);
        assertThat(result.getAcceptedDepositAmountVnd()).isEqualTo(40_000L);
        assertThat(result.getRemainingAmountVnd()).isEqualTo(40_000L);
        verify(bills).saveAndFlush(any(SettlementFinalBill.class));
    }

    @Test void existingBillIsReturnedWithoutRecalculatingOrCallingUpstream() {
        SettlementFinalBill existing = SettlementFinalBill.builder().bookingReference("BK-1")
                .billReference("BILL-1").postPromotionAmountVnd(80_000L).acceptedDepositAmountVnd(40_000L)
                .remainingAmountVnd(40_000L).currencyCode("VND").lifecycleStatus("OPEN").build();
        when(bills.findByBookingReference("BK-1")).thenReturn(Optional.of(existing));
        SettlementFinalBill result = new FinalBookingBillService(bills, booking, operations, authorization).create("BK-1", "OP-1", "CORR-1");
        assertThat(result).isSameAs(existing);
        verifyNoInteractions(booking, operations);
    }

    @Test void readinessGateRejectsBeforeReadingFinancialProjection() {
        when(bills.findByBookingReference("BK-1")).thenReturn(Optional.empty());
        when(operations.readiness("BK-1")).thenReturn(new SettlementReadiness(false));
        FinalBookingBillService service = new FinalBookingBillService(bills, booking, operations, authorization);
        assertThatThrownBy(() -> service.create("BK-1", "OP-1", "CORR-1"))
                .isInstanceOf(WorkshopSettlementConflictException.class).hasMessage("SETTLEMENT_NOT_READY");
        verifyNoInteractions(booking);
    }

    @Test void finalizesZeroBalanceForAuthorizedStaff() {
        SettlementFinalBill bill = openBill(); bill.setRemainingAmountVnd(0L);
        when(bills.findByBookingReference("BK-1")).thenReturn(Optional.of(bill));
        when(operations.readiness("BK-1")).thenReturn(new SettlementReadiness(true));
        when(bills.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new FinalBookingBillService(bills, booking, operations, authorization);
        var result = service.finalizeAuthorized("BK-1", 0L);
        assertThat(result.getLifecycleStatus()).isEqualTo("FINALIZED");
    }

    @Test void rejectsFinalizationWhileAnyBalanceRemainsEvenForManager() {
        when(bills.findByBookingReference("BK-1")).thenReturn(Optional.of(openBill()));
        when(operations.readiness("BK-1")).thenReturn(new SettlementReadiness(true));
        var service = new FinalBookingBillService(bills, booking, operations, authorization);
        assertThatThrownBy(() -> service.finalizeAuthorized("BK-1", 40_000L))
                .isInstanceOf(WorkshopSettlementConflictException.class).hasMessage("OUTSTANDING_BALANCE");
        verify(bills, never()).saveAndFlush(any());
    }

    private static SettlementFinalBill openBill() {
        return SettlementFinalBill.builder().billReference("BILL-1").bookingReference("BK-1")
                .postPromotionAmountVnd(80_000L).acceptedDepositAmountVnd(40_000L)
                .remainingAmountVnd(40_000L).currencyCode("VND").lifecycleStatus("OPEN").build();
    }
}
