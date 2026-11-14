package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingAuditEvidenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupLineRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.TicketBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 003.3 FR-008 per ticket (BE-DESIGN-0006, decided 2026-11-14): 003.3 tells 003.1 that one ticket folio is
 * settled, and only that ticket becomes FULLY_PAID.
 */
@ExtendWith(MockitoExtension.class)
class TicketFinalBalanceResolvedTest {

    private static final String TICKET = "GRP-20261114-001-L0-T1";

    @Mock private TicketBookingRepository ticketBookingRepository;
    @Mock private BookingGroupLineRepository bookingGroupLineRepository;
    @Mock private BookingAuditEvidenceRepository auditEvidenceRepository;
    @InjectMocks private WorkshopBookingFacadeImpl facade;

    private static RecordTicketFinalBalanceResolvedCommand command() {
        return new RecordTicketFinalBalanceResolvedCommand(TICKET, "BILL-1", "TICKET-SETTLED:BILL-1", "CORR-1");
    }

    private static TicketBooking ticket(BookingStatus status, PaymentStatus financial) {
        BookingGroup group = BookingGroup.builder().id(10L).groupCode("GRP-20261114-001").build();
        return TicketBooking.builder().id(100L).ticketCode(TICKET).group(group).lineIndex(0)
                .ticketStatus(status).financialStatus(financial).build();
    }

    @Test
    void aConfirmedTicketWithABalanceBecomesFullyPaidAndIsAuditedOnItsGroup() {
        TicketBooking ticket = ticket(BookingStatus.CONFIRMED, PaymentStatus.DEPOSIT_PAID);
        when(ticketBookingRepository.findByTicketCodeForUpdate(TICKET)).thenReturn(Optional.of(ticket));
        when(ticketBookingRepository.findByTicketCode(TICKET)).thenReturn(Optional.of(ticket));
        BookingGroupLine line = BookingGroupLine.builder().lineIndex(0).lineReference("LINE-1").packageId(7L)
                .sessions(List.of(BookingGroupLineSession.builder().ordinal(1).sessionReference("S-1")
                        .locationReference(3L).scheduledStart(Instant.parse("2026-11-20T02:00:00Z")).build()))
                .build();
        when(bookingGroupLineRepository.findByGroupIdOrderByLineIndexAsc(10L)).thenReturn(List.of(line));

        FinalBalanceResolutionOutcome outcome = facade.recordTicketFinalBalanceResolved(command());

        assertThat(outcome.status()).isEqualTo(BookingOutcomeStatus.ACCEPTED);
        assertThat(ticket.getFinancialStatus()).isEqualTo(PaymentStatus.FULLY_PAID);
        verify(ticketBookingRepository).save(ticket);
        ArgumentCaptor<BookingAuditEvidence> audit = ArgumentCaptor.forClass(BookingAuditEvidence.class);
        verify(auditEvidenceRepository).save(audit.capture());
        assertThat(audit.getValue().getBookingCode()).isEqualTo("GRP-20261114-001");
        assertThat(audit.getValue().getLocationId()).isEqualTo(3L);
        assertThat(audit.getValue().getAction()).isEqualTo("TICKET_FINAL_BALANCE_RESOLVED");
        assertThat(audit.getValue().getActionReason()).contains(TICKET).contains("BILL-1");
    }

    @Test
    void aCompletedTicketIsAcceptedToo() {
        TicketBooking ticket = ticket(BookingStatus.COMPLETED, PaymentStatus.UNPAID);
        when(ticketBookingRepository.findByTicketCodeForUpdate(TICKET)).thenReturn(Optional.of(ticket));
        when(ticketBookingRepository.findByTicketCode(TICKET)).thenReturn(Optional.of(ticket));

        assertThat(facade.recordTicketFinalBalanceResolved(command()).status()).isEqualTo(BookingOutcomeStatus.ACCEPTED);
        assertThat(ticket.getFinancialStatus()).isEqualTo(PaymentStatus.FULLY_PAID);
    }

    @Test
    void aRepeatIsAcceptedWithoutASecondWriteOrAudit() {
        TicketBooking ticket = ticket(BookingStatus.CONFIRMED, PaymentStatus.FULLY_PAID);
        when(ticketBookingRepository.findByTicketCodeForUpdate(TICKET)).thenReturn(Optional.of(ticket));

        assertThat(facade.recordTicketFinalBalanceResolved(command()).status()).isEqualTo(BookingOutcomeStatus.ACCEPTED);
        verify(ticketBookingRepository, never()).save(any());
        verifyNoInteractions(auditEvidenceRepository);
    }

    @Test
    void anUnknownTicketIsNotFound() {
        when(ticketBookingRepository.findByTicketCodeForUpdate(TICKET)).thenReturn(Optional.empty());

        FinalBalanceResolutionOutcome outcome = facade.recordTicketFinalBalanceResolved(command());

        assertThat(outcome.status()).isEqualTo(BookingOutcomeStatus.REJECTED);
        assertThat(outcome.reason()).isEqualTo(BookingRejectionReason.BOOKING_NOT_FOUND);
        verifyNoInteractions(auditEvidenceRepository);
    }

    @Test
    void aCancelledTicketOrOneBeingRefundedIsRefusedAndUnchanged() {
        TicketBooking cancelled = ticket(BookingStatus.CANCELLED, PaymentStatus.DEPOSIT_PAID);
        when(ticketBookingRepository.findByTicketCodeForUpdate(TICKET)).thenReturn(Optional.of(cancelled));
        assertThat(facade.recordTicketFinalBalanceResolved(command()).reason())
                .isEqualTo(BookingRejectionReason.INVALID_STATE);
        assertThat(cancelled.getFinancialStatus()).isEqualTo(PaymentStatus.DEPOSIT_PAID);

        TicketBooking refunding = ticket(BookingStatus.CONFIRMED, PaymentStatus.REFUND_PENDING);
        when(ticketBookingRepository.findByTicketCodeForUpdate(TICKET)).thenReturn(Optional.of(refunding));
        assertThat(facade.recordTicketFinalBalanceResolved(command()).reason())
                .isEqualTo(BookingRejectionReason.INVALID_STATE);
        assertThat(refunding.getFinancialStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);

        verify(ticketBookingRepository, never()).save(any());
        verifyNoInteractions(auditEvidenceRepository);
    }

    @Test
    void theCommandNeedsEveryReference() {
        assertThatThrownBy(() -> new RecordTicketFinalBalanceResolvedCommand(" ", "B", "O", "C"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RecordTicketFinalBalanceResolvedCommand("T", "B", "O", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
