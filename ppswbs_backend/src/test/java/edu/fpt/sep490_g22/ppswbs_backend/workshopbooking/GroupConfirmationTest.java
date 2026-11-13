package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.common.booking.MlandTicketConfirmedEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingRejectionReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.DepositPaymentEvidenceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingOutcomeStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopOperationsAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 003.1 T093: confirming or releasing a whole booking group. Commits every line's holds, takes the QR
 * identities Operations issued, opens one Magic Link and publishes one event per ticket for Settlement.
 */
class GroupConfirmationTest {

    private static final String GROUP = "GRP-TEST0001";
    private static final Instant NOW_HOLD = Instant.now().plusSeconds(600);
    private static final Instant SESSION_END = Instant.parse("2026-11-20T05:00:00Z");

    private BookingGroupRepository groups;
    private BookingGroupLineRepository lines;
    private TicketBookingRepository tickets;
    private TicketPriceSnapshotRepository snapshots;
    private BookingGroupMagicLinkRepository magicLinks;
    private WorkshopOperationsAdapter operations;
    private ApplicationEventPublisher events;
    private GroupLoyaltyOutcomeService loyaltyOutcome;
    private edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.GroupNotificationService notifications;
    private GroupConfirmationService service;

    private BookingGroup group;
    private final List<TicketBooking> ticketRows = new ArrayList<>();
    private final List<BookingGroupLine> lineRows = new ArrayList<>();

    @BeforeEach
    void setUp() {
        groups = mock(BookingGroupRepository.class);
        lines = mock(BookingGroupLineRepository.class);
        tickets = mock(TicketBookingRepository.class);
        snapshots = mock(TicketPriceSnapshotRepository.class);
        magicLinks = mock(BookingGroupMagicLinkRepository.class);
        operations = mock(WorkshopOperationsAdapter.class);
        events = mock(ApplicationEventPublisher.class);
        when(groups.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(lines.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tickets.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(magicLinks.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        loyaltyOutcome = mock(GroupLoyaltyOutcomeService.class);
        notifications = mock(edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.GroupNotificationService.class);
        service = new GroupConfirmationService(groups, lines, tickets, snapshots, magicLinks, operations,
                loyaltyOutcome, events, notifications);
    }

    // ---- internal confirmation ----

    @Test
    void confirmsAFreeGroupWithoutTheGatewayAndIssuesEveryTicketItsQr() {
        givenGroup(GroupPaymentMode.INTERNAL_FREE, 0);
        givenLine(0, "Open Day", 2, 0, 0, TicketCollectionKind.FREE, 0);
        givenCommitAccepted();
        givenIssuedTickets(0, 2);

        var result = service.confirmInternalGroup(GROUP, "op-1", "corr-1");

        assertThat(result.alreadyConfirmed()).isFalse();
        assertThat(result.groupStatus()).isEqualTo(BookingStatus.CONFIRMED.name());
        assertThat(result.rawMagicToken()).isNotBlank();
        assertThat(result.tickets()).hasSize(2)
                .allSatisfy(ticket -> {
                    assertThat(ticket.qrReference()).startsWith("QR-");
                    assertThat(ticket.financialStatus()).isEqualTo(PaymentStatus.FULLY_PAID.name());
                });
        assertThat(group.getConfirmedAt()).isNotNull();
        assertThat(ticketRows).allSatisfy(ticket -> {
            assertThat(ticket.getTicketStatus()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(ticket.getQrReference()).isNotNull();
            assertThat(ticket.getOperationsTicketReference()).isNotNull();
            assertThat(ticket.getConfirmedAt()).isNotNull();
        });
        assertThat(lineRows.get(0).getSessions()).allSatisfy(session ->
                assertThat(session.getHoldStatus()).isEqualTo(LineHoldStatus.COMMITTED));
        verify(magicLinks, times(1)).save(any());
    }

    @Test
    void theMagicLinkOutlivesTheLastSessionAndStoresOnlyAHash() {
        givenGroup(GroupPaymentMode.INTERNAL_FREE, 0);
        givenLine(0, "Open Day", 1, 0, 0, TicketCollectionKind.FREE, 0);
        givenCommitAccepted();
        givenIssuedTickets(0, 1);

        var result = service.confirmInternalGroup(GROUP, "op-1", "corr-1");

        ArgumentCaptor<BookingGroupMagicLink> link = ArgumentCaptor.forClass(BookingGroupMagicLink.class);
        verify(magicLinks).save(link.capture());
        assertThat(link.getValue().getExpiresAt()).isEqualTo(SESSION_END);
        assertThat(link.getValue().getTokenHash()).hasSize(64).isNotEqualTo(result.rawMagicToken());
    }

    @Test
    void publishesOneTicketEventPerTicketSoSettlementOpensOneFolioEach() {
        givenGroup(GroupPaymentMode.INTERNAL_DEFERRED, 0);
        givenLine(0, "Mini Workshop", 2, 3_000, 0, TicketCollectionKind.DEFERRED_BELOW_VNPAY_MINIMUM, 3_000);
        givenCommitAccepted();
        givenIssuedTickets(0, 2);

        service.confirmInternalGroup(GROUP, "op-1", "corr-1");

        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events, times(2)).publishEvent(published.capture());
        assertThat(published.getAllValues()).allSatisfy(event -> {
            MlandTicketConfirmedEvent ticketEvent = (MlandTicketConfirmedEvent) event;
            assertThat(ticketEvent.groupReference()).isEqualTo(GROUP);
            assertThat(ticketEvent.finalPriceVnd()).isEqualTo(3_000);
            assertThat(ticketEvent.collectedAtBookingVnd()).isZero();
            assertThat(ticketEvent.remainingVnd()).isEqualTo(3_000);
        });
    }

    @Test
    void aDeferredTicketStaysUnpaidBecauseNothingWasCollectedYet() {
        givenGroup(GroupPaymentMode.INTERNAL_DEFERRED, 0);
        givenLine(0, "Mini Workshop", 1, 3_000, 0, TicketCollectionKind.DEFERRED_BELOW_VNPAY_MINIMUM, 3_000);
        givenCommitAccepted();
        givenIssuedTickets(0, 1);

        var result = service.confirmInternalGroup(GROUP, "op-1", "corr-1");

        assertThat(result.tickets().get(0).financialStatus()).isEqualTo(PaymentStatus.UNPAID.name());
    }

    @Test
    void aGatewayGroupIsNotConfirmedInternally() {
        givenGroup(GroupPaymentMode.VNPAY, 400_000);

        assertThatThrownBy(() -> service.confirmInternalGroup(GROUP, "op-1", "corr-1"))
                .isInstanceOf(BookingValidationException.class);
        verifyNoInteractions(operations);
    }

    // ---- paid confirmation ----

    @Test
    void confirmsAPaidGroupAndMarksADepositTicketAsDepositPaid() {
        givenGroup(GroupPaymentMode.VNPAY, 400_000);
        givenLine(0, "Clay Workshop", 1, 203_000, 200_000, TicketCollectionKind.DEPOSIT, 3_000);
        givenLine(1, "Wax Casting", 1, 500_000, 200_000, TicketCollectionKind.DEPOSIT, 300_000);
        givenCommitAccepted();
        givenIssuedTickets(0, 1);
        givenIssuedTickets(1, 1);

        var result = service.confirmPaidGroup(GROUP, 400_000, "VNPAY-TX-7", "op-1", "corr-1");

        assertThat(result.tickets()).extracting(GroupConfirmationService.ConfirmedTicket::financialStatus)
                .containsOnly(PaymentStatus.DEPOSIT_PAID.name());
        assertThat(result.tickets()).extracting(GroupConfirmationService.ConfirmedTicket::remainingVnd)
                .containsExactly(3_000L, 300_000L);
        verify(operations).commitBookingHolds(eq(GROUP + "-L0"), anyList(), anyString(), eq("corr-1"));
        verify(operations).commitBookingHolds(eq(GROUP + "-L1"), anyList(), anyString(), eq("corr-1"));
    }

    @Test
    void aFullyCollectedTicketIsFullyPaid() {
        givenGroup(GroupPaymentMode.VNPAY, 150_000);
        givenLine(0, "Clay Workshop", 1, 150_000, 150_000, TicketCollectionKind.FULL, 0);
        givenCommitAccepted();
        givenIssuedTickets(0, 1);

        var result = service.confirmPaidGroup(GROUP, 150_000, "VNPAY-TX-7", "op-1", "corr-1");

        assertThat(result.tickets().get(0).financialStatus()).isEqualTo(PaymentStatus.FULLY_PAID.name());
    }

    @Test
    void anAmountThatDoesNotMatchTheGroupCollectionIsRefused() {
        givenGroup(GroupPaymentMode.VNPAY, 400_000);

        assertThatThrownBy(() -> service.confirmPaidGroup(GROUP, 399_000, "VNPAY-TX-7", "op-1", "corr-1"))
                .isInstanceOf(BookingConflictException.class);
        verifyNoInteractions(operations);
    }

    @Test
    void aSecondConfirmationChangesNothingAndIssuesNoSecondMagicLink() {
        givenGroup(GroupPaymentMode.VNPAY, 150_000);
        givenLine(0, "Clay Workshop", 1, 150_000, 150_000, TicketCollectionKind.FULL, 0);
        givenCommitAccepted();
        givenIssuedTickets(0, 1);
        service.confirmPaidGroup(GROUP, 150_000, "VNPAY-TX-7", "op-1", "corr-1");
        clearInvocations(operations, magicLinks, events);

        var replay = service.confirmPaidGroup(GROUP, 150_000, "VNPAY-TX-7", "op-1", "corr-1");

        assertThat(replay.alreadyConfirmed()).isTrue();
        assertThat(replay.rawMagicToken()).isNull();
        assertThat(replay.tickets()).hasSize(1);
        verifyNoInteractions(operations);
        verifyNoInteractions(magicLinks);
        verifyNoInteractions(events);
    }

    @Test
    void aPaymentThatArrivesAfterTheHoldIsNotConfirmedHere() {
        givenGroup(GroupPaymentMode.VNPAY, 150_000);
        group.setHoldExpiresAt(Instant.now().minusSeconds(60));

        assertThatThrownBy(() -> service.confirmPaidGroup(GROUP, 150_000, "VNPAY-TX-7", "op-1", "corr-1"))
                .isInstanceOf(BookingCreationRejectedException.class)
                .hasFieldOrPropertyWithValue("code", "GROUP_HOLD_EXPIRED");
        verifyNoInteractions(operations);
    }

    @Test
    void aRefusedCommitLeavesTheGroupUnconfirmed() {
        givenGroup(GroupPaymentMode.VNPAY, 150_000);
        givenLine(0, "Clay Workshop", 1, 150_000, 150_000, TicketCollectionKind.FULL, 0);
        when(operations.commitBookingHolds(anyString(), anyList(), anyString(), anyString()))
                .thenReturn(CapacityCommitOutcome.rejected(RejectionReason.HOLD_MISSING_OR_EXPIRED));

        assertThatThrownBy(() -> service.confirmPaidGroup(GROUP, 150_000, "VNPAY-TX-7", "op-1", "corr-1"))
                .isInstanceOf(BookingConflictException.class);
        assertThat(group.getGroupStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        verifyNoInteractions(magicLinks);
        verifyNoInteractions(events);
    }

    @Test
    void anIncompleteTicketSetFromOperationsStopsTheConfirmation() {
        givenGroup(GroupPaymentMode.VNPAY, 150_000);
        givenLine(0, "Clay Workshop", 2, 150_000, 150_000, TicketCollectionKind.FULL, 0);
        givenCommitAccepted();
        givenIssuedTickets(0, 1);

        assertThatThrownBy(() -> service.confirmPaidGroup(GROUP, 300_000, "VNPAY-TX-7", "op-1", "corr-1"))
                .isInstanceOf(BookingConflictException.class);
    }

    @Test
    void confirmingAGroupSettlesItsLoyaltyComponentsOnceWithThePaymentEvidence() {
        givenGroup(GroupPaymentMode.VNPAY, 150_000);
        givenLine(0, "Clay Workshop", 1, 150_000, 150_000, TicketCollectionKind.FULL, 0);
        givenCommitAccepted();
        givenIssuedTickets(0, 1);

        service.confirmPaidGroup(GROUP, 150_000, "VNPAY-TX-7", "op-1", "corr-1");

        verify(loyaltyOutcome).settleConfirmedGroup(group, "VNPAY-TX-7");
    }

    @Test
    void anInternalConfirmationSettlesWithoutPaymentEvidence() {
        givenGroup(GroupPaymentMode.INTERNAL_FREE, 0);
        givenLine(0, "Open Day", 1, 0, 0, TicketCollectionKind.FREE, 0);
        givenCommitAccepted();
        givenIssuedTickets(0, 1);

        service.confirmInternalGroup(GROUP, "op-1", "corr-1");

        verify(loyaltyOutcome).settleConfirmedGroup(group, null);
    }

    @Test
    void aReplayedConfirmationSettlesNothingASecondTime() {
        givenGroup(GroupPaymentMode.VNPAY, 150_000);
        givenLine(0, "Clay Workshop", 1, 150_000, 150_000, TicketCollectionKind.FULL, 0);
        givenCommitAccepted();
        givenIssuedTickets(0, 1);
        service.confirmPaidGroup(GROUP, 150_000, "VNPAY-TX-7", "op-1", "corr-1");
        clearInvocations(loyaltyOutcome);

        service.confirmPaidGroup(GROUP, 150_000, "VNPAY-TX-7", "op-1", "corr-1");

        verifyNoInteractions(loyaltyOutcome);
    }

    // ---- expiry ----

    @Test
    void anExpiredHoldReleasesTheWholeGroup() {
        givenGroup(GroupPaymentMode.VNPAY, 400_000);
        group.setHoldExpiresAt(Instant.now().minusSeconds(30));
        givenLine(0, "Clay Workshop", 1, 203_000, 200_000, TicketCollectionKind.DEPOSIT, 3_000);
        givenLine(1, "Wax Casting", 1, 500_000, 200_000, TicketCollectionKind.DEPOSIT, 300_000);
        when(operations.releaseBookingHolds(anyString(), anyList(), anyString(), anyString()))
                .thenReturn(CapacityReleaseOutcome.accepted());

        assertThat(service.releaseExpiredGroup(GROUP, "op-1", "corr-1")).isTrue();

        // The group's one voucher use and points hold go back with it.
        verify(loyaltyOutcome).releaseExpiredGroup(group);
        assertThat(group.getGroupStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(ticketRows).allSatisfy(ticket ->
                assertThat(ticket.getTicketStatus()).isEqualTo(BookingStatus.EXPIRED));
        assertThat(lineRows).allSatisfy(line -> assertThat(line.getSessions())
                .allSatisfy(session -> assertThat(session.getHoldStatus()).isEqualTo(LineHoldStatus.RELEASED)));
        verify(operations).releaseBookingHolds(eq(GROUP + "-L0"), anyList(), anyString(), anyString());
        verify(operations).releaseBookingHolds(eq(GROUP + "-L1"), anyList(), anyString(), anyString());
    }

    @Test
    void aGroupStillInsideItsHoldIsLeftAlone() {
        givenGroup(GroupPaymentMode.VNPAY, 400_000);

        assertThat(service.releaseExpiredGroup(GROUP, "op-1", "corr-1")).isFalse();
        verifyNoInteractions(operations);
    }

    @Test
    void aConfirmedGroupIsNeverReleased() {
        givenGroup(GroupPaymentMode.VNPAY, 400_000);
        group.setGroupStatus(BookingStatus.CONFIRMED);
        group.setHoldExpiresAt(Instant.now().minusSeconds(30));

        assertThat(service.releaseExpiredGroup(GROUP, "op-1", "corr-1")).isFalse();
        verifyNoInteractions(operations);
    }

    // ---- verified gateway evidence ----

    @Test
    void verifiedEvidenceConfirmsTheGroupAndIsSafeToReplay() {
        givenGroup(GroupPaymentMode.VNPAY, 150_000);
        givenLine(0, "Clay Workshop", 1, 150_000, 150_000, TicketCollectionKind.FULL, 0);
        givenCommitAccepted();
        givenIssuedTickets(0, 1);
        GroupPaymentAttemptRepository attempts = mock(GroupPaymentAttemptRepository.class);
        GroupPaymentAttempt attempt = GroupPaymentAttempt.builder().group(group).amountVnd(150_000)
                .merchantReference("GRP-M1").attemptStatus(PaymentAttemptStatus.PENDING).build();
        when(attempts.findByMerchantReference("GRP-M1")).thenReturn(Optional.of(attempt));
        when(attempts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        GroupPaymentEvidenceService evidence = new GroupPaymentEvidenceService(attempts, service, groups,
                mock(edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.LatePaymentReviewService.class));

        DepositPaymentEvidenceOutcome first = evidence.apply(GROUP, "GRP-M1", 150_000, "VND", "VNPAY-TX-7", "op-1", "corr-1");
        DepositPaymentEvidenceOutcome second = evidence.apply(GROUP, "GRP-M1", 150_000, "VND", "VNPAY-TX-7", "op-1", "corr-1");

        assertThat(first.status()).isEqualTo(BookingOutcomeStatus.ACCEPTED);
        assertThat(second.status()).isEqualTo(BookingOutcomeStatus.ACCEPTED);
        assertThat(attempt.getAttemptStatus()).isEqualTo(PaymentAttemptStatus.SUCCESS);
        assertThat(group.getGroupStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(magicLinks, times(1)).save(any());
        // Email 1 once, to the buyer, with the ticket and its QR; a replay sends nothing more.
        verify(notifications, times(1)).queue(eq(group), isNull(), eq("GROUP_CONFIRMATION"), eq("buyer@example.com"),
                eq("MLAND – Xác nhận đặt chỗ workshop " + GROUP), argThat(body -> !body.contains("Thanh toán của bạn đến sau")),
                argThat(items -> items.size() == 1 && items.getFirst().contains("QR: ")), isNull(), eq("corr-1"));
    }

    @Test
    void evidenceForAnotherAmountOrGroupIsRefused() {
        givenGroup(GroupPaymentMode.VNPAY, 150_000);
        GroupPaymentAttemptRepository attempts = mock(GroupPaymentAttemptRepository.class);
        GroupPaymentAttempt attempt = GroupPaymentAttempt.builder().group(group).amountVnd(150_000)
                .merchantReference("GRP-M1").attemptStatus(PaymentAttemptStatus.PENDING).build();
        when(attempts.findByMerchantReference("GRP-M1")).thenReturn(Optional.of(attempt));
        GroupPaymentEvidenceService evidence = new GroupPaymentEvidenceService(attempts, service, groups,
                mock(edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.LatePaymentReviewService.class));

        assertThat(evidence.apply(GROUP, "GRP-M1", 149_000, "VND", "ev", "op", "corr").reason())
                .isEqualTo(BookingRejectionReason.PAYMENT_MISMATCH);
        assertThat(evidence.apply("GRP-OTHER", "GRP-M1", 150_000, "VND", "ev", "op", "corr").reason())
                .isEqualTo(BookingRejectionReason.PAYMENT_MISMATCH);
        assertThat(evidence.owns("GRP-UNKNOWN")).isFalse();
    }

    // ---- helpers ----

    private void givenGroup(GroupPaymentMode mode, long collection) {
        group = BookingGroup.builder().id(1L).groupCode(GROUP).contactEmail("buyer@example.com")
                .contactPhone("0901234567").groupStatus(BookingStatus.PENDING_PAYMENT).paymentMode(mode)
                .groupCollectionVnd(collection).holdExpiresAt(NOW_HOLD)
                .paymentDeadlineAt(NOW_HOLD.minusSeconds(120)).build();
        when(groups.findByGroupCodeForUpdate(GROUP)).thenReturn(Optional.of(group));
        when(lines.findByGroupIdOrderByLineIndexAsc(1L)).thenReturn(lineRows);
        when(tickets.findByGroupIdOrderByLineIndexAscParticipantOrdinalAsc(1L)).thenReturn(ticketRows);
    }

    private void givenLine(int lineIndex, String packageName, int ticketCount, long finalPrice, long collected,
                           TicketCollectionKind kind, long remaining) {
        BookingGroupLine line = BookingGroupLine.builder().id(10L + lineIndex).group(group).lineIndex(lineIndex)
                .lineReference(GROUP + "-L" + lineIndex).packageId(7L + lineIndex)
                .packageNameSnapshot(packageName).pricePerParticipantVnd(finalPrice).ticketCount(ticketCount)
                .sessions(new ArrayList<>()).build();
        line.addSession(BookingGroupLineSession.builder().ordinal(1)
                .sessionReference(UUID.randomUUID().toString()).locationReference(5L)
                .holdReference(UUID.randomUUID().toString()).holdStatus(LineHoldStatus.RESERVED).build());
        lineRows.add(line);
        for (int ordinal = 1; ordinal <= ticketCount; ordinal++) {
            long ticketId = (lineIndex + 1) * 100L + ordinal;
            TicketBooking ticket = TicketBooking.builder().id(ticketId)
                    .ticketCode(GROUP + "-L" + lineIndex + "-T" + ordinal).group(group).lineIndex(lineIndex)
                    .participantOrdinal(ordinal).packageId(7L + lineIndex).packageNameSnapshot(packageName)
                    .ticketStatus(BookingStatus.PENDING_PAYMENT).financialStatus(PaymentStatus.UNPAID).build();
            ticketRows.add(ticket);
            when(snapshots.findByTicketId(ticketId)).thenReturn(Optional.of(TicketPriceSnapshot.builder()
                    .ticket(ticket).packagePriceVnd(finalPrice).finalPriceVnd(finalPrice)
                    .collectedAtBookingVnd(collected).collectionKind(kind).remainingVnd(remaining).build()));
        }
    }

    private void givenCommitAccepted() {
        when(operations.commitBookingHolds(anyString(), anyList(), anyString(), anyString()))
                .thenReturn(CapacityCommitOutcome.accepted());
    }

    private void givenIssuedTickets(int lineIndex, int count) {
        List<DeliverySafeTicket> issued = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            issued.add(new DeliverySafeTicket(UUID.randomUUID(), "QR-" + lineIndex + "-" + i,
                    "OPS-" + lineIndex + "-" + i,
                    List.of(new SessionEntitlementProjection(UUID.randomUUID(), 5L,
                            Instant.parse("2026-11-20T02:00:00Z"), SESSION_END))));
        }
        when(operations.getBookingTicketProjection(GROUP + "-L" + lineIndex))
                .thenReturn(new DeliverySafeTicketSet(issued));
    }
}
