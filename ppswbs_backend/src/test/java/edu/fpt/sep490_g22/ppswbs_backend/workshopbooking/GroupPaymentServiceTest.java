package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentGatewayCheckoutPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentOwnerRegistrationPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.GroupPaymentAttemptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.TicketBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.TicketPriceSnapshotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingCreationRejectedException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingIdempotencyClaim;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingGroupRecoveryService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingIdempotencyService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.WorkshopBookingProblemException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.GroupPaymentAttempt;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.PaymentAttemptStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.GroupPaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 003.1 T109 / FR-036: the gateway amount is recomputed from the ticket snapshots before the URL is made. */
class GroupPaymentServiceTest {

    private static final String KEY = "0123456789abcdef0123";

    private BookingGroupRepository groups;
    private GroupPaymentAttemptRepository attempts;
    private TicketBookingRepository tickets;
    private TicketPriceSnapshotRepository snapshots;
    private PaymentGatewayCheckoutPort gateway;
    private PaymentOwnerRegistrationPort owners;
    private GroupPaymentService service;
    private BookingGroupRecoveryService recovery;
    private BookingGroup group;

    @BeforeEach
    void setUp() {
        groups = mock(BookingGroupRepository.class);
        attempts = mock(GroupPaymentAttemptRepository.class);
        tickets = mock(TicketBookingRepository.class);
        snapshots = mock(TicketPriceSnapshotRepository.class);
        gateway = mock(PaymentGatewayCheckoutPort.class);
        owners = mock(PaymentOwnerRegistrationPort.class);
        BookingIdempotencyService idempotency = mock(BookingIdempotencyService.class);
        when(idempotency.acquireClaim(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(BookingIdempotencyClaim.live(1L, 1L));
        recovery = mock(BookingGroupRecoveryService.class);
        service = new GroupPaymentService(groups, attempts, tickets, snapshots, gateway, owners, idempotency,
                new ObjectMapper().findAndRegisterModules(), recovery);
        group = BookingGroup.builder().id(1L).groupCode("GRP-P1").groupStatus(BookingStatus.PENDING_PAYMENT)
                .paymentMode(GroupPaymentMode.VNPAY).groupCollectionVnd(30_000)
                .paymentDeadlineAt(Instant.now().plusSeconds(300)).build();
        when(groups.findByGroupCode("GRP-P1")).thenReturn(Optional.of(group));
        when(groups.findByGroupCodeForUpdate("GRP-P1")).thenReturn(Optional.of(group));
        when(attempts.lockByGroupIdAndStatuses(eq(1L), anyCollection())).thenReturn(List.of());
        when(attempts.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(gateway.createDepositUrl(anyString(), anyString(), anyLong(), any(), any(), any()))
                .thenReturn("https://pay.example/test");
    }

    private void tickets(long... collected) {
        java.util.ArrayList<TicketBooking> list = new java.util.ArrayList<>();
        for (int i = 0; i < collected.length; i++) {
            TicketBooking t = TicketBooking.builder().id(10L + i).group(group).build();
            list.add(t);
            when(snapshots.findByTicketId(10L + i)).thenReturn(Optional.of(
                    TicketPriceSnapshot.builder().ticket(t).collectedAtBookingVnd(collected[i]).build()));
        }
        when(tickets.findByGroupIdOrderByLineIndexAscParticipantOrdinalAsc(1L)).thenReturn(list);
    }

    @Test
    void matchingSnapshotsCreateTheUrlForTheRecomputedAmount() {
        tickets(10_000, 20_000);

        var response = service.initiate("GRP-P1", KEY, "127.0.0.1", new BookingGroupRecoveryService.Actor(null), "a@b.test", null);

        assertThat(response.amountVnd()).isEqualTo(30_000);
        verify(gateway).createDepositUrl(anyString(), eq("GRP-P1"), eq(30_000L), any(), any(), any());
    }

    @Test
    void theMerchantReferenceOfARealGroupCodeFitsTheVarchar64Column() {
        // A real group code is "GRP-" + 20 hex characters (BookingGroupCreationService), 24 characters in all.
        String realCode = "GRP-37867B4FE853458192BE";
        group.setGroupCode(realCode);
        when(groups.findByGroupCode(realCode)).thenReturn(Optional.of(group));
        when(groups.findByGroupCodeForUpdate(realCode)).thenReturn(Optional.of(group));
        tickets(30_000);

        service.initiate(realCode, KEY, "127.0.0.1", new BookingGroupRecoveryService.Actor(null), "a@b.test", null);

        var reference = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(gateway).createDepositUrl(reference.capture(), eq(realCode), eq(30_000L), any(), any(), any());
        assertThat(reference.getValue()).startsWith(realCode + "-").hasSizeLessThanOrEqualTo(64);
    }

    @Test
    void aStoredTotalThatDiffersFromTheSnapshotsIsRefusedAndNoUrlIsMade() {
        tickets(10_000, 15_000);

        assertThatThrownBy(() -> service.initiate("GRP-P1", KEY, "127.0.0.1", new BookingGroupRecoveryService.Actor(null), "a@b.test", null))
                .isInstanceOfSatisfying(BookingCreationRejectedException.class,
                        e -> assertThat(e.getCode()).isEqualTo("GROUP_COLLECTION_MISMATCH"));
        verifyNoInteractions(gateway, owners);
    }

    @Test
    void aCollectionBelowTheGatewayMinimumIsRefused() {
        group = BookingGroup.builder().id(1L).groupCode("GRP-P1").groupStatus(BookingStatus.PENDING_PAYMENT)
                .paymentMode(GroupPaymentMode.VNPAY).groupCollectionVnd(4_999)
                .paymentDeadlineAt(Instant.now().plusSeconds(300)).build();
        when(groups.findByGroupCodeForUpdate("GRP-P1")).thenReturn(Optional.of(group));
        tickets(4_999);

        assertThatThrownBy(() -> service.initiate("GRP-P1", KEY, "127.0.0.1", new BookingGroupRecoveryService.Actor(null), "a@b.test", null))
                .isInstanceOfSatisfying(BookingCreationRejectedException.class,
                        e -> assertThat(e.getCode()).isEqualTo("GROUP_COLLECTION_BELOW_MINIMUM"));
        verifyNoInteractions(gateway, owners);
    }

    private static final BookingGroupRecoveryService.Actor GUEST = new BookingGroupRecoveryService.Actor(null);

    private void deadlineIn(long seconds) {
        group.setPaymentDeadlineAt(Instant.now().plusSeconds(seconds));
    }

    /** T125 / FR-012a: the payment deadline is hold - 2 min; minute 9 is inside it, minutes 10 and 11 are not. */
    @Test
    void retryAtMinuteNineIsAllowedButMinutesTenAndElevenAreRefusedWithPaymentWindowClosed() {
        tickets(30_000);
        deadlineIn(60); // minute 9 of a 10-minute window
        assertThat(service.initiate("GRP-P1", KEY, "127.0.0.1", GUEST, "a@b.test", null).amountVnd())
                .isEqualTo(30_000);

        for (long secondsAfterDeadline : new long[] {0, 60}) { // minute 10 and minute 11
            deadlineIn(-secondsAfterDeadline);
            assertThatThrownBy(() -> service.initiate("GRP-P1", KEY, "127.0.0.1", GUEST, "a@b.test", null))
                    .isInstanceOfSatisfying(BookingCreationRejectedException.class,
                            e -> assertThat(e.getCode()).isEqualTo("PAYMENT_WINDOW_CLOSED"));
        }
        verify(gateway, times(1)).createDepositUrl(anyString(), anyString(), anyLong(), any(), any(), any());
        verify(attempts, times(1)).saveAndFlush(any());
    }

    /** T126 / FR-012a: a live PENDING attempt is returned as is; a retry never opens a second PENDING attempt. */
    @Test
    void aRetryWhileAnAttemptIsPendingReturnsItAndOpensNoSecondOne() {
        tickets(30_000);
        GroupPaymentAttempt live = GroupPaymentAttempt.builder().group(group).attemptReference("GATT-1")
                .merchantReference("GRP-P1-m1").amountVnd(30_000).paymentUrlSnapshot("https://pay.example/live")
                .attemptStatus(PaymentAttemptStatus.PENDING).expiresAt(Instant.now().plusSeconds(120)).build();
        when(attempts.lockByGroupIdAndStatuses(eq(1L), anyCollection())).thenReturn(List.of(live));

        for (int i = 0; i < 5; i++) {
            assertThat(service.initiate("GRP-P1", KEY, "127.0.0.1", GUEST, "a@b.test", null).attemptReference())
                    .isEqualTo("GATT-1");
        }
        verify(attempts, never()).saveAndFlush(any());
        verifyNoInteractions(gateway, owners);
    }

    /** T126: an expired PENDING attempt is closed first, so the group still never has two PENDING; no count limit. */
    @Test
    void anExpiredPendingAttemptIsClosedBeforeTheNextOneAndTheNumberOfAttemptsIsNotLimited() {
        tickets(30_000);
        List<GroupPaymentAttempt> history = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            history.add(0, GroupPaymentAttempt.builder().group(group).attemptReference("GATT-OLD-" + i)
                    .merchantReference("GRP-P1-old" + i).amountVnd(30_000)
                    .attemptStatus(i == 5 ? PaymentAttemptStatus.PENDING : PaymentAttemptStatus.EXPIRED)
                    .expiresAt(Instant.now().minusSeconds(30)).build());
        }
        when(attempts.lockByGroupIdAndStatuses(eq(1L), anyCollection())).thenReturn(history);

        var response = service.initiate("GRP-P1", KEY, "127.0.0.1", GUEST, "a@b.test", null);

        assertThat(history.get(0).getAttemptStatus()).isEqualTo(PaymentAttemptStatus.EXPIRED);
        assertThat(response.attemptReference()).startsWith("GATT-").isNotEqualTo("GATT-OLD-5");
    }

    /** FR-032 "One live attempt" (T169): after FAILED a new attempt with a new merchantReference; hold never extended. */
    @Test
    void afterAFailedAttemptTheNextRequestCreatesANewAttemptWithTheSameDeadline() {
        tickets(30_000);
        GroupPaymentAttempt failed = GroupPaymentAttempt.builder().group(group).attemptReference("GATT-F")
                .merchantReference("GRP-P1-failed").amountVnd(30_000)
                .attemptStatus(PaymentAttemptStatus.FAILED).expiresAt(group.getPaymentDeadlineAt()).build();
        when(attempts.lockByGroupIdAndStatuses(eq(1L), anyCollection())).thenReturn(List.of(failed));

        var response = service.initiate("GRP-P1", KEY, "127.0.0.1", GUEST, "a@b.test", null);

        assertThat(response.attemptReference()).isNotEqualTo("GATT-F");
        assertThat(response.merchantReference()).isNotEqualTo("GRP-P1-failed");
        assertThat(response.expiresAt()).isEqualTo(group.getPaymentDeadlineAt());
        assertThat(failed.getAttemptStatus()).isEqualTo(PaymentAttemptStatus.FAILED);
    }

    /** An INITIATED attempt is live too and is handed back. */
    @Test
    void aLiveInitiatedAttemptIsHandedBack() {
        tickets(30_000);
        GroupPaymentAttempt live = GroupPaymentAttempt.builder().group(group).attemptReference("GATT-I")
                .merchantReference("GRP-P1-init").amountVnd(30_000).paymentUrlSnapshot("https://pay.example/i")
                .attemptStatus(PaymentAttemptStatus.INITIATED).expiresAt(Instant.now().plusSeconds(120)).build();
        when(attempts.lockByGroupIdAndStatuses(eq(1L), anyCollection())).thenReturn(List.of(live));

        assertThat(service.initiate("GRP-P1", KEY, "127.0.0.1", GUEST, "a@b.test", null).attemptReference())
                .isEqualTo("GATT-I");
        verifyNoInteractions(gateway, owners);
    }

    /** T131 / FR-032: a provider failure answers 503 PAYMENT_LINK_UNAVAILABLE, creates no attempt, extends nothing. */
    @Test
    void aProviderFailureAnswers503AndCreatesNoAttemptOrOwner() {
        tickets(30_000);
        Instant deadline = group.getPaymentDeadlineAt();
        when(gateway.createDepositUrl(anyString(), anyString(), anyLong(), any(), any(), any()))
                .thenThrow(new IllegalStateException("provider down"));

        assertThatThrownBy(() -> service.initiate("GRP-P1", KEY, "127.0.0.1", GUEST, "a@b.test", null))
                .isInstanceOfSatisfying(WorkshopBookingProblemException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getCode()).isEqualTo("PAYMENT_LINK_UNAVAILABLE");
                });
        verify(attempts, never()).saveAndFlush(any());
        verifyNoInteractions(owners);
        assertThat(group.getPaymentDeadlineAt()).isEqualTo(deadline);
        assertThat(group.getGroupStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
    }

    /** T134 / FR-048: the buyer check runs before anything is claimed or created; its refusal stops the call. */
    @Test
    void aRefusedBuyerProofCreatesNoClaimAttemptOrUrl() {
        tickets(30_000);
        when(recovery.requireBuyer(any(), any(), any(), any()))
                .thenThrow(new WorkshopBookingProblemException(org.springframework.http.HttpStatus.NOT_FOUND,
                        "BOOKING_NOT_FOUND", "Booking not found"));

        assertThatThrownBy(() -> service.initiate("GRP-P1", KEY, "127.0.0.1", GUEST, "wrong@b.test", null))
                .isInstanceOfSatisfying(WorkshopBookingProblemException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND));
        verifyNoInteractions(gateway, owners);
        verify(attempts, never()).saveAndFlush(any());
    }
}
