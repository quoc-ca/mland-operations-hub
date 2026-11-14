package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingServiceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingServiceOutcomeResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketBookingRef;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketAttendanceFinalizedEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketAttendanceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketAttendanceOutcome.Outcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBillLifecycleStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.TicketAttendanceFinalizedEventConsumer;
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
class TicketAttendanceFinalizedEventConsumerTest {

    @Mock
    private SettlementFinalBillRepository bills;
    @Mock
    private SettlementBusinessAuditRepository audits;
    @Mock
    private WorkshopOperationsSettlementAdapter operations;
    @Mock
    private WorkshopBookingSettlementAdapter booking;

    private TicketAttendanceFinalizedEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new TicketAttendanceFinalizedEventConsumer(operations, bills, booking, audits);
    }

    /** Evidence 5: A consumer dedups a redelivered eventId. */
    @Test
    void redeliveredEventIdIsDeduplicatedAndProducesNoSideEffects() {
        String eventId = "ev-dedup-123";
        String ticketRef = "OPS-TKT-DEDUP";
        when(audits.existsByCorrelationReference(eventId)).thenReturn(true);

        consumer.onAttendanceFinalized(new TicketAttendanceFinalizedEvent(eventId, ticketRef, Instant.now()));

        verifyNoInteractions(operations, bills, booking);
    }

    @Test
    void nonTerminalAttendanceOutcomeDoesNotCloseFolio() {
        String ticketRef = "OPS-TKT-1";
        when(audits.existsByCorrelationReference("ev-1")).thenReturn(false);
        when(operations.ticketAttendanceOutcome(ticketRef))
                .thenReturn(new TicketAttendanceOutcome(false, Outcome.NO_SHOW, Instant.now().plusSeconds(3600)));

        consumer.onAttendanceFinalized(new TicketAttendanceFinalizedEvent("ev-1", ticketRef, Instant.now()));

        verifyNoInteractions(bills, booking);
    }

    @Test
    void terminalNoShowClosesOpenFolioAndAudits() {
        String ticketRef = "OPS-TKT-2";
        String ticketCode = "TKT-CODE-2";
        String eventId = "ev-2";
        SettlementFinalBill bill = SettlementFinalBill.builder()
                .billReference("BILL-ATT-2")
                .bookingReference(ticketCode)
                .postPromotionAmountVnd(100_000L)
                .acceptedDepositAmountVnd(50_000L)
                .remainingAmountVnd(50_000L)
                .currencyCode("VND")
                .lifecycleStatus(SettlementFinalBillLifecycleStatus.OPEN.name())
                .build();

        when(audits.existsByCorrelationReference(eventId)).thenReturn(false);
        when(operations.ticketAttendanceOutcome(ticketRef))
                .thenReturn(new TicketAttendanceOutcome(true, Outcome.NO_SHOW, Instant.now()));
        when(booking.findTicketBookingByOperationsReference(ticketRef))
                .thenReturn(Optional.of(new TicketBookingRef(ticketCode, "GRP-1")));
        when(bills.findByBookingReference(ticketCode)).thenReturn(Optional.of(bill));
        when(booking.recordBookingServiceOutcome(eq(ticketCode), eq(BookingServiceOutcome.NO_SHOW), eq("BILL-ATT-2"), any(), any()))
                .thenReturn(BookingServiceOutcomeResult.accepted());

        consumer.onAttendanceFinalized(new TicketAttendanceFinalizedEvent(eventId, ticketRef, Instant.now()));

        assertThat(bill.getLifecycleStatus()).isEqualTo(SettlementFinalBillLifecycleStatus.CLOSED.name());
        verify(bills).saveAndFlush(bill);
        verify(audits).save(argThat(audit -> "TICKET_FOLIO_CLOSED_NO_SHOW".equals(audit.getActionCode())
                && eventId.equals(audit.getCorrelationReference())));
        verify(booking).recordBookingServiceOutcome(eq(ticketCode), eq(BookingServiceOutcome.NO_SHOW), eq("BILL-ATT-2"), any(), any());
    }

    @Test
    void terminalAttendedOrPartialLeavesFolioOpenForCheckout() {
        String ticketRef = "OPS-TKT-3";
        String ticketCode = "TKT-CODE-3";
        String eventId = "ev-3";
        SettlementFinalBill bill = SettlementFinalBill.builder()
                .billReference("BILL-ATT-3")
                .bookingReference(ticketCode)
                .lifecycleStatus(SettlementFinalBillLifecycleStatus.OPEN.name())
                .build();

        when(audits.existsByCorrelationReference(eventId)).thenReturn(false);
        when(operations.ticketAttendanceOutcome(ticketRef))
                .thenReturn(new TicketAttendanceOutcome(true, Outcome.PARTIAL, Instant.now()));
        when(booking.findTicketBookingByOperationsReference(ticketRef))
                .thenReturn(Optional.of(new TicketBookingRef(ticketCode, "GRP-1")));
        when(bills.findByBookingReference(ticketCode)).thenReturn(Optional.of(bill));

        consumer.onAttendanceFinalized(new TicketAttendanceFinalizedEvent(eventId, ticketRef, Instant.now()));

        assertThat(bill.getLifecycleStatus()).isEqualTo(SettlementFinalBillLifecycleStatus.OPEN.name());
        verify(bills, never()).saveAndFlush(any());
        verify(audits).save(argThat(audit -> "TICKET_ATTENDANCE_CHECKOUT_ELIGIBLE".equals(audit.getActionCode())
                && eventId.equals(audit.getCorrelationReference())));
    }

    @Test
    void terminalNoShowWhenAlreadyClosedIsIdempotent() {
        String ticketRef = "OPS-TKT-4";
        String ticketCode = "TKT-CODE-4";
        String eventId = "ev-4";
        SettlementFinalBill bill = SettlementFinalBill.builder()
                .billReference("BILL-ATT-4")
                .bookingReference(ticketCode)
                .lifecycleStatus(SettlementFinalBillLifecycleStatus.CLOSED.name())
                .build();

        when(audits.existsByCorrelationReference(eventId)).thenReturn(false);
        when(operations.ticketAttendanceOutcome(ticketRef))
                .thenReturn(new TicketAttendanceOutcome(true, Outcome.NO_SHOW, Instant.now()));
        when(booking.findTicketBookingByOperationsReference(ticketRef))
                .thenReturn(Optional.of(new TicketBookingRef(ticketCode, "GRP-1")));
        when(bills.findByBookingReference(ticketCode)).thenReturn(Optional.of(bill));

        consumer.onAttendanceFinalized(new TicketAttendanceFinalizedEvent(eventId, ticketRef, Instant.now()));

        verify(bills, never()).saveAndFlush(any());
        verify(audits).save(argThat(audit -> "TICKET_FOLIO_ALREADY_CLOSED".equals(audit.getActionCode())
                && eventId.equals(audit.getCorrelationReference())));
    }
}

