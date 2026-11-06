package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingServiceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingServiceOutcomeResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.BookingAttendanceFinalizedEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.BookingAttendanceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.BookingAttendanceOutcome.Outcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBillLifecycleStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.BookingAttendanceFinalizedEventConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingAttendanceFinalizedEventConsumerTest {

    @Mock
    private SettlementFinalBillRepository bills;
    @Mock
    private SettlementBusinessAuditRepository audits;
    @Mock
    private WorkshopOperationsSettlementAdapter operations;
    @Mock
    private WorkshopBookingSettlementAdapter booking;

    private BookingAttendanceFinalizedEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new BookingAttendanceFinalizedEventConsumer(operations, bills, booking, audits);
    }

    @Test
    void nonTerminalAttendanceOutcomeDoesNotCloseFolio() {
        String bookingRef = "BK-ATT-1";
        when(operations.attendanceOutcome(bookingRef))
                .thenReturn(new BookingAttendanceOutcome(false, Outcome.NO_SHOW));

        consumer.onAttendanceFinalized(new BookingAttendanceFinalizedEvent(bookingRef, Instant.now()));

        verifyNoInteractions(bills, audits, booking);
    }

    @Test
    void terminalNoShowClosesOpenFolioAndSendsBookingOutcome() {
        String bookingRef = "BK-ATT-2";
        SettlementFinalBill bill = SettlementFinalBill.builder()
                .billReference("BILL-ATT-2")
                .bookingReference(bookingRef)
                .postPromotionAmountVnd(100_000L)
                .acceptedDepositAmountVnd(50_000L)
                .remainingAmountVnd(50_000L)
                .currencyCode("VND")
                .lifecycleStatus(SettlementFinalBillLifecycleStatus.OPEN.name())
                .build();
        when(operations.attendanceOutcome(bookingRef))
                .thenReturn(new BookingAttendanceOutcome(true, Outcome.NO_SHOW));
        when(bills.findByBookingReference(bookingRef)).thenReturn(Optional.of(bill));
        when(booking.recordBookingServiceOutcome(eq(bookingRef), eq(BookingServiceOutcome.NO_SHOW), eq("BILL-ATT-2"), any(), any()))
                .thenReturn(BookingServiceOutcomeResult.accepted());

        consumer.onAttendanceFinalized(new BookingAttendanceFinalizedEvent(bookingRef, Instant.now()));

        assertThat(bill.getLifecycleStatus()).isEqualTo(SettlementFinalBillLifecycleStatus.CLOSED.name());
        verify(bills).saveAndFlush(bill);
        verify(audits).save(argThat(audit -> "DIRECT_FOLIO_CLOSED_NO_SHOW".equals(audit.getActionCode())));
        verify(booking).recordBookingServiceOutcome(eq(bookingRef), eq(BookingServiceOutcome.NO_SHOW), eq("BILL-ATT-2"), any(), any());
    }

    @Test
    void terminalAttendedOrPartialLeavesFolioOpenForCheckout() {
        String bookingRef = "BK-ATT-3";
        when(operations.attendanceOutcome(bookingRef))
                .thenReturn(new BookingAttendanceOutcome(true, Outcome.PARTIAL));

        consumer.onAttendanceFinalized(new BookingAttendanceFinalizedEvent(bookingRef, Instant.now()));

        verify(bills, never()).saveAndFlush(any());
        verifyNoInteractions(booking, audits);
    }

    @Test
    void terminalNoShowWhenAlreadyClosedIsIdempotent() {
        String bookingRef = "BK-ATT-4";
        SettlementFinalBill bill = SettlementFinalBill.builder()
                .billReference("BILL-ATT-4")
                .bookingReference(bookingRef)
                .postPromotionAmountVnd(100_000L)
                .acceptedDepositAmountVnd(50_000L)
                .remainingAmountVnd(50_000L)
                .currencyCode("VND")
                .lifecycleStatus(SettlementFinalBillLifecycleStatus.CLOSED.name())
                .build();
        when(operations.attendanceOutcome(bookingRef))
                .thenReturn(new BookingAttendanceOutcome(true, Outcome.NO_SHOW));
        when(bills.findByBookingReference(bookingRef)).thenReturn(Optional.of(bill));

        consumer.onAttendanceFinalized(new BookingAttendanceFinalizedEvent(bookingRef, Instant.now()));

        verify(bills, never()).saveAndFlush(any());
        verifyNoInteractions(booking, audits);
    }
}
