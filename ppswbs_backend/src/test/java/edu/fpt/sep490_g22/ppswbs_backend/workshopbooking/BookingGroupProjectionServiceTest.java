package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupLineRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.TicketBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.TicketPriceSnapshotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.TicketRecipientRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.BookingGroupView;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopOperationsAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingGroupProjectionService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketAttendanceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.DeliverySafeTicket;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.DeliverySafeTicketSet;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SessionEntitlementProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import edu.fpt.sep490_g22.ppswbs_backend.common.settlement.TicketBalancePort;
import edu.fpt.sep490_g22.ppswbs_backend.common.settlement.TicketRemedyPendingPort;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 003.1 FR-042/FR-049: the buyer's per-ticket view of a group. */
class BookingGroupProjectionServiceTest {

    private static final UUID TICKET_REF = UUID.randomUUID();
    private static final UUID BOOKED_SESSION = UUID.randomUUID();
    private static final UUID REPLACED_SESSION = UUID.randomUUID();
    private static final Instant START = Instant.parse("2026-12-01T02:00:00Z");

    private BookingGroupLineRepository lines;
    private TicketBookingRepository tickets;
    private TicketPriceSnapshotRepository snapshots;
    private TicketRecipientRepository recipients;
    private WorkshopOperationsAdapter operations;
    private TicketRemedyPendingPort remedyPending;
    private TicketBalancePort balancePort;
    private edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.GroupPaymentAttemptRepository attempts;
    private BookingGroupProjectionService service;
    private BookingGroup group;
    private TicketBooking ticket;

    @BeforeEach
    void setUp() {
        lines = mock(BookingGroupLineRepository.class);
        tickets = mock(TicketBookingRepository.class);
        snapshots = mock(TicketPriceSnapshotRepository.class);
        recipients = mock(TicketRecipientRepository.class);
        operations = mock(WorkshopOperationsAdapter.class);
        remedyPending = mock(TicketRemedyPendingPort.class);
        attempts = mock(edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.GroupPaymentAttemptRepository.class);
        when(remedyPending.pendingTicketCodes(any())).thenReturn(java.util.Set.of());
        balancePort = mock(TicketBalancePort.class);
        when(balancePort.balancesOf(any())).thenReturn(java.util.Map.of());
        service = new BookingGroupProjectionService(lines, tickets, snapshots, recipients, operations, remedyPending,
                balancePort, attempts);

        group = BookingGroup.builder().id(1L).groupCode("GRP-P1").groupStatus(BookingStatus.CONFIRMED)
                .paymentMode(GroupPaymentMode.VNPAY).groupCollectionVnd(200_000).build();
        BookingGroupLine line = BookingGroupLine.builder().id(10L).group(group).lineIndex(0).lineReference("LINE-1")
                .packageId(3L).packageNameSnapshot("Basic Ring").pricePerParticipantVnd(500_000).ticketCount(1)
                .sessions(new ArrayList<>()).build();
        line.getSessions().add(BookingGroupLineSession.builder().line(line).ordinal(1)
                .sessionReference(BOOKED_SESSION.toString()).locationReference(4L).scheduledStart(START).build());
        when(lines.findByGroupIdOrderByLineIndexAsc(1L)).thenReturn(List.of(line));
        ticket = TicketBooking.builder().id(100L).group(group).ticketCode("TK-1").lineIndex(0).participantOrdinal(1)
                .packageId(3L).packageNameSnapshot("Basic Ring").operationsTicketReference(TICKET_REF.toString())
                .qrReference("QR-1").ticketStatus(BookingStatus.CONFIRMED).financialStatus(PaymentStatus.DEPOSIT_PAID)
                .build();
        when(tickets.findByGroupIdOrderByLineIndexAscParticipantOrdinalAsc(1L)).thenReturn(List.of(ticket));
        when(snapshots.findByTicketId(100L)).thenReturn(Optional.of(TicketPriceSnapshot.builder()
                .finalPriceVnd(500_000).collectedAtBookingVnd(200_000).remainingVnd(300_000)
                .collectionKind(TicketCollectionKind.DEPOSIT).build()));
        when(recipients.findByTicketId(100L)).thenReturn(Optional.of(TicketRecipient.builder()
                .recipientEmail("friend@example.com").changeCount(1).build()));
        when(operations.getCurrentEntitlementProjection("LINE-1")).thenReturn(new DeliverySafeTicketSet(List.of(
                new DeliverySafeTicket(TICKET_REF, "QR-1", "TK-1", List.of(new SessionEntitlementProjection(
                        REPLACED_SESSION, 4L, START.plusSeconds(86_400), START.plusSeconds(90_000), "MLAND Tây Hồ"))))));
        when(operations.getTicketAttendanceOutcome(TICKET_REF.toString()))
                .thenReturn(new TicketAttendanceOutcome(true, TicketAttendanceOutcome.Outcome.ATTENDED));
    }

    @Test
    void theOwningMemberSeesPricesRecipientAttendanceQrAndTicketReference() {
        BookingGroupView.TicketView view = service.project(group, true).tickets().get(0);

        assertThat(view.collectionKind()).isEqualTo("DEPOSIT");
        assertThat(view.collectedAtBookingVnd()).isEqualTo(200_000);
        assertThat(view.remainingVnd()).isEqualTo(300_000);
        assertThat(view.recipientEmailMasked()).isNotEqualTo("friend@example.com").contains("@");
        assertThat(view.onlineRecipientChangesRemaining()).isEqualTo(2);
        assertThat(view.attendanceStatus()).isEqualTo("ATTENDED");
        assertThat(view.qrReference()).isEqualTo("QR-1");
        assertThat(view.ticketReference()).isEqualTo(TICKET_REF.toString());
    }

    @Test
    void theViewCarriesTheFolioMoneyPositionAfterAnAddOnAndALaterPayment() {
        when(balancePort.balancesOf(any())).thenReturn(java.util.Map.of(
                "TK-1", new TicketBalancePort.TicketBalance(600_000, 100_000, 600_000, 0)));

        BookingGroupView.TicketView view = service.project(group, true).tickets().get(0);

        assertThat(view.currentTotalVnd()).isEqualTo(600_000);
        assertThat(view.currentAddOnVnd()).isEqualTo(100_000);
        assertThat(view.currentPaidVnd()).isEqualTo(600_000);
        assertThat(view.currentBalanceDueVnd()).isZero();
        assertThat(view.remainingVnd()).isEqualTo(300_000); // the booking-time figure is left as it was
    }

    @Test
    void theCurrentMoneyPositionIsAbsentWhenSettlementHasNoFolioOrCannotAnswer() {
        assertThat(service.project(group, true).tickets().get(0).currentBalanceDueVnd()).isNull();

        when(balancePort.balancesOf(any())).thenThrow(new IllegalStateException("down"));
        assertThat(service.project(group, true).tickets().get(0).currentBalanceDueVnd()).isNull();
    }

    @Test
    void theViewTellsWhetherALivePaymentAttemptExists() {
        assertThat(service.project(group, false).hasLivePaymentAttempt()).isFalse();

        when(attempts.existsByGroupIdAndAttemptStatusIn(eq(1L), anyCollection())).thenReturn(true);

        assertThat(service.project(group, false).hasLivePaymentAttempt()).isTrue();
    }

    @Test
    void aGuestViewNeverCarriesTheTicketReference() {
        assertThat(service.project(group, false).tickets().get(0).ticketReference()).isNull();
    }

    @Test
    void theCurrentScheduleFromOperationsReplacesTheBookingTimeOne() {
        BookingGroupView.SessionView session = service.project(group, true).tickets().get(0).sessions().get(0);

        assertThat(session.sessionReference()).isEqualTo(REPLACED_SESSION);
        assertThat(session.scheduledEnd()).isNotNull();
    }

    @Test
    void theCurrentSessionCarriesTheLocationNameFromOperationsAndTheBookingTimeCopyHasNone() {
        assertThat(service.project(group, true).tickets().get(0).sessions().get(0).locationName()).isEqualTo("MLAND Tây Hồ");

        group.setGroupStatus(BookingStatus.PENDING_PAYMENT);
        ticket.setTicketStatus(BookingStatus.PENDING_PAYMENT);
        assertThat(service.project(group, true).tickets().get(0).sessions().get(0).locationName()).isNull();
    }

    @Test
    void aPendingGroupShowsTheBookedScheduleAndNoQrOrAttendance() {
        group.setGroupStatus(BookingStatus.PENDING_PAYMENT);
        ticket.setTicketStatus(BookingStatus.PENDING_PAYMENT);

        BookingGroupView.TicketView view = service.project(group, true).tickets().get(0);

        assertThat(view.sessions().get(0).sessionReference()).isEqualTo(BOOKED_SESSION);
        assertThat(view.qrReference()).isNull();
        assertThat(view.attendanceStatus()).isNull();
        assertThat(view.ticketReference()).isNull();
        verify(operations, never()).getCurrentEntitlementProjection(any());
    }

    @Test
    void anOperationsFailureFallsBackToTheBookedSchedule() {
        when(operations.getCurrentEntitlementProjection("LINE-1")).thenThrow(new IllegalStateException("down"));

        assertThat(service.project(group, true).tickets().get(0).sessions().get(0).sessionReference())
                .isEqualTo(BOOKED_SESSION);
    }

    @Test
    void flagsATicketWithAnOfferWaitingForTheOwningMemberOnly() {
        when(remedyPending.pendingTicketCodes(any())).thenReturn(java.util.Set.of("TK-1"));

        assertThat(service.project(group, true).tickets().get(0).mlandRemedyPending()).isTrue();
        assertThat(service.project(group, false).tickets().get(0).mlandRemedyPending()).isFalse();
    }

    @Test
    void aSettlementFailureNeverBreaksTheViewAndFlagsNothing() {
        when(remedyPending.pendingTicketCodes(any())).thenThrow(new IllegalStateException("down"));

        assertThat(service.project(group, true).tickets().get(0).mlandRemedyPending()).isFalse();
    }
}
