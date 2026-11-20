package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingAuditEvidenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupLineRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.TicketBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.WorkshopBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * T119 / FR-050 (decided 2026-11-15, extended 2026-11-16 by Hồ Hoàng Quốc): 003.3 concludes one ticket at a time
 * through {@code recordBookingServiceOutcome} / {@code recordBookingRefundStatus} with the ticket code. Each
 * transition applies once, a replay is accepted without a second write, an out-of-order move is INVALID_STATE, and
 * the other ticket of the group is never touched. Row "confirm" is covered by GroupConfirmationTest and row
 * FULLY_PAID on a FINALIZED folio by TicketFinalBalanceResolvedTest.
 */
@ExtendWith(MockitoExtension.class)
class TicketConclusionStatusMappingTest {

    private static final String T1 = "GRP-20261116-001-L0-T1";
    private static final String T2 = "GRP-20261116-001-L0-T2";

    @Mock private TicketBookingRepository ticketBookingRepository;
    @Mock private BookingGroupLineRepository bookingGroupLineRepository;
    @Mock private BookingAuditEvidenceRepository auditEvidenceRepository;
    @Mock private WorkshopBookingRepository bookingRepository;
    @Mock private edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupRepository bookingGroupRepository;
    @InjectMocks private WorkshopBookingFacadeImpl facade;

    private final BookingGroup group = BookingGroup.builder().id(10L).groupCode("GRP-20261116-001").build();

    private TicketBooking ticket(String code, BookingStatus status, PaymentStatus financial) {
        TicketBooking t = TicketBooking.builder().id((long) Math.abs(code.hashCode())).ticketCode(code).group(group)
                .lineIndex(0).ticketStatus(status).financialStatus(financial).build();
        lenient().when(ticketBookingRepository.findByTicketCodeForUpdate(code)).thenReturn(Optional.of(t));
        return t;
    }

    private BookingServiceOutcomeResult outcome(String code, BookingServiceOutcome o) {
        return facade.recordBookingServiceOutcome(new RecordBookingServiceOutcomeCommand(code, o, "CASE-1", "OP-1", "CORR-1"));
    }

    private BookingRefundStatusResult refund(String code, BookingRefundStatus s) {
        return facade.recordBookingRefundStatus(new RecordBookingRefundStatusCommand(code, s, "CASE-1", "OP-2", "CORR-2"));
    }

    @Test
    @DisplayName("COMPLETED, NO_SHOW and CANCELLED leave a CONFIRMED ticket once, financial status unchanged, audited on the group")
    void eachServiceConclusionAppliesFromConfirmed() {
        for (BookingServiceOutcome o : BookingServiceOutcome.values()) {
            TicketBooking t = ticket(T1, BookingStatus.CONFIRMED, PaymentStatus.DEPOSIT_PAID);
            assertThat(outcome(T1, o).status()).isEqualTo(BookingOutcomeStatus.ACCEPTED);
            assertThat(t.getTicketStatus()).isEqualTo(BookingStatus.valueOf(o.name()));
            assertThat(t.getFinancialStatus()).isEqualTo(PaymentStatus.DEPOSIT_PAID);
        }
        ArgumentCaptor<BookingAuditEvidence> audit = ArgumentCaptor.forClass(BookingAuditEvidence.class);
        verify(auditEvidenceRepository, times(3)).save(audit.capture());
        assertThat(audit.getAllValues()).extracting(BookingAuditEvidence::getAction)
                .containsExactlyInAnyOrder("TICKET_SERVICE_COMPLETED", "TICKET_SERVICE_CANCELLED", "TICKET_SERVICE_NO_SHOW");
        assertThat(audit.getValue().getBookingCode()).isEqualTo("GRP-20261116-001");
        verifyNoInteractions(bookingRepository);
    }

    @Test
    @DisplayName("A replayed service conclusion is accepted without a second write or audit")
    void serviceReplayIsAcceptedOnce() {
        TicketBooking t = ticket(T1, BookingStatus.NO_SHOW, PaymentStatus.FULLY_PAID);
        assertThat(outcome(T1, BookingServiceOutcome.NO_SHOW).status()).isEqualTo(BookingOutcomeStatus.ACCEPTED);
        verify(ticketBookingRepository, never()).save(any());
        verifyNoInteractions(auditEvidenceRepository);
        assertThat(t.getTicketStatus()).isEqualTo(BookingStatus.NO_SHOW);
    }

    @Test
    @DisplayName("A different or pre-confirmation service conclusion is INVALID_STATE and changes nothing")
    void outOfOrderServiceConclusionIsRefused() {
        for (BookingStatus from : new BookingStatus[]{BookingStatus.COMPLETED, BookingStatus.CANCELLED,
                BookingStatus.PENDING_PAYMENT, BookingStatus.EXPIRED}) {
            TicketBooking t = ticket(T1, from, PaymentStatus.UNPAID);
            BookingServiceOutcomeResult r = outcome(T1, BookingServiceOutcome.NO_SHOW);
            assertThat(r.status()).isEqualTo(BookingOutcomeStatus.REJECTED);
            assertThat(r.reason()).isEqualTo(BookingRejectionReason.INVALID_STATE);
            assertThat(t.getTicketStatus()).isEqualTo(from);
        }
        verify(ticketBookingRepository, never()).save(any());
        verifyNoInteractions(auditEvidenceRepository);
    }

    @Test
    @DisplayName("REFUND_PENDING then REFUNDED, once each, from a paid ticket; ticket status unchanged")
    void refundStatusFollowsTheTableAndReplaysAreAccepted() {
        TicketBooking t = ticket(T1, BookingStatus.CONFIRMED, PaymentStatus.FULLY_PAID);
        assertThat(refund(T1, BookingRefundStatus.REFUND_PENDING).status()).isEqualTo(BookingOutcomeStatus.ACCEPTED);
        assertThat(t.getFinancialStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
        assertThat(refund(T1, BookingRefundStatus.REFUND_PENDING).status()).isEqualTo(BookingOutcomeStatus.ACCEPTED);
        assertThat(refund(T1, BookingRefundStatus.REFUNDED).status()).isEqualTo(BookingOutcomeStatus.ACCEPTED);
        assertThat(t.getFinancialStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(refund(T1, BookingRefundStatus.REFUNDED).status()).isEqualTo(BookingOutcomeStatus.ACCEPTED);
        assertThat(t.getTicketStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(ticketBookingRepository, times(2)).save(t);
        verify(auditEvidenceRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("REFUNDED before REFUND_PENDING, or a refund of an unpaid or refunded ticket, is INVALID_STATE")
    void outOfOrderRefundIsRefused() {
        for (PaymentStatus from : new PaymentStatus[]{PaymentStatus.UNPAID, PaymentStatus.DEPOSIT_PAID, PaymentStatus.FULLY_PAID}) {
            TicketBooking t = ticket(T1, BookingStatus.CONFIRMED, from);
            assertThat(refund(T1, BookingRefundStatus.REFUNDED).reason()).isEqualTo(BookingRejectionReason.INVALID_STATE);
            assertThat(t.getFinancialStatus()).isEqualTo(from);
        }
        TicketBooking unpaid = ticket(T1, BookingStatus.CONFIRMED, PaymentStatus.UNPAID);
        assertThat(refund(T1, BookingRefundStatus.REFUND_PENDING).reason()).isEqualTo(BookingRejectionReason.INVALID_STATE);
        assertThat(unpaid.getFinancialStatus()).isEqualTo(PaymentStatus.UNPAID);
        TicketBooking refunded = ticket(T1, BookingStatus.CANCELLED, PaymentStatus.REFUNDED);
        assertThat(refund(T1, BookingRefundStatus.REFUND_PENDING).reason()).isEqualTo(BookingRejectionReason.INVALID_STATE);
        assertThat(refunded.getFinancialStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(ticketBookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Concluding one ticket never changes the other ticket of the group")
    void ticketsAreIndependent() {
        TicketBooking first = ticket(T1, BookingStatus.CONFIRMED, PaymentStatus.FULLY_PAID);
        TicketBooking second = ticket(T2, BookingStatus.CONFIRMED, PaymentStatus.FULLY_PAID);
        outcome(T1, BookingServiceOutcome.CANCELLED);
        refund(T1, BookingRefundStatus.REFUND_PENDING);
        assertThat(first.getTicketStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(first.getFinancialStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
        assertThat(second.getTicketStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(second.getFinancialStatus()).isEqualTo(PaymentStatus.FULLY_PAID);
    }

    @Test
    @DisplayName("Cancelling the last CONFIRMED ticket of a group moves the group to CANCELLED")
    void cancellingEveryTicketCancelsTheGroup() {
        group.setGroupStatus(BookingStatus.CONFIRMED);
        TicketBooking first = ticket(T1, BookingStatus.CANCELLED, PaymentStatus.FULLY_PAID);
        TicketBooking second = ticket(T2, BookingStatus.CONFIRMED, PaymentStatus.FULLY_PAID);
        when(ticketBookingRepository.findByGroupIdOrderByLineIndexAscParticipantOrdinalAsc(10L))
                .thenReturn(java.util.List.of(first, second));

        outcome(T2, BookingServiceOutcome.CANCELLED);

        assertThat(group.getGroupStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(bookingGroupRepository).save(group);
    }

    @Test
    @DisplayName("Cancelling one ticket while another is still CONFIRMED leaves the group CONFIRMED")
    void cancellingOneTicketKeepsTheGroupConfirmed() {
        group.setGroupStatus(BookingStatus.CONFIRMED);
        TicketBooking first = ticket(T1, BookingStatus.CONFIRMED, PaymentStatus.FULLY_PAID);
        TicketBooking second = ticket(T2, BookingStatus.CONFIRMED, PaymentStatus.FULLY_PAID);
        when(ticketBookingRepository.findByGroupIdOrderByLineIndexAscParticipantOrdinalAsc(10L))
                .thenReturn(java.util.List.of(first, second));

        outcome(T1, BookingServiceOutcome.CANCELLED);

        assertThat(group.getGroupStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(bookingGroupRepository, never()).save(any());
    }

    @Test
    @DisplayName("COMPLETED or NO_SHOW on the last ticket never rewrites the group status")
    void nonCancelOutcomeDoesNotTouchTheGroup() {
        group.setGroupStatus(BookingStatus.CONFIRMED);
        ticket(T1, BookingStatus.CONFIRMED, PaymentStatus.FULLY_PAID);

        outcome(T1, BookingServiceOutcome.NO_SHOW);

        assertThat(group.getGroupStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verifyNoInteractions(bookingGroupRepository);
    }

    @Test
    @DisplayName("A reference that is not a ticket code keeps the legacy booking path (NOT_READY when unknown)")
    void nonTicketReferenceFallsThroughToLegacyBooking() {
        when(ticketBookingRepository.findByTicketCodeForUpdate("BK-UNKNOWN")).thenReturn(Optional.empty());
        when(bookingRepository.findLockedByBookingCode("BK-UNKNOWN")).thenReturn(Optional.empty());
        assertThat(outcome("BK-UNKNOWN", BookingServiceOutcome.NO_SHOW).reason()).isEqualTo(BookingRejectionReason.NOT_READY);
        assertThat(refund("BK-UNKNOWN", BookingRefundStatus.REFUNDED).reason()).isEqualTo(BookingRejectionReason.NOT_READY);
    }
}
