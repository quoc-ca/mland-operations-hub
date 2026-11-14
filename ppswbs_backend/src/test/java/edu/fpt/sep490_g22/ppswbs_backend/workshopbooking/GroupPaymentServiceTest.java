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
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingIdempotencyService;
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
        service = new GroupPaymentService(groups, attempts, tickets, snapshots, gateway, owners, idempotency,
                new ObjectMapper().findAndRegisterModules());
        group = BookingGroup.builder().id(1L).groupCode("GRP-P1").groupStatus(BookingStatus.PENDING_PAYMENT)
                .paymentMode(GroupPaymentMode.VNPAY).groupCollectionVnd(30_000)
                .paymentDeadlineAt(Instant.now().plusSeconds(300)).build();
        when(groups.findByGroupCodeForUpdate("GRP-P1")).thenReturn(Optional.of(group));
        when(attempts.findByGroupIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
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

        var response = service.initiate("GRP-P1", KEY, "127.0.0.1");

        assertThat(response.amountVnd()).isEqualTo(30_000);
        verify(gateway).createDepositUrl(anyString(), eq("GRP-P1"), eq(30_000L), any(), any(), any());
    }

    @Test
    void aStoredTotalThatDiffersFromTheSnapshotsIsRefusedAndNoUrlIsMade() {
        tickets(10_000, 15_000);

        assertThatThrownBy(() -> service.initiate("GRP-P1", KEY, "127.0.0.1"))
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

        assertThatThrownBy(() -> service.initiate("GRP-P1", KEY, "127.0.0.1"))
                .isInstanceOfSatisfying(BookingCreationRejectedException.class,
                        e -> assertThat(e.getCode()).isEqualTo("GROUP_COLLECTION_BELOW_MINIMUM"));
        verifyNoInteractions(gateway, owners);
    }
}
