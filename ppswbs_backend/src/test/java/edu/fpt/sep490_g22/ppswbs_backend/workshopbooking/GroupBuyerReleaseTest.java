package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopOperationsAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.GroupConfirmationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.GroupLoyaltyOutcomeService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.GroupNotificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.WorkshopBookingProblemException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.CapacityReleaseOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 003.1 FR-048 (decision 2026-11-13): the buyer releases a PENDING_PAYMENT group before paying. */
class GroupBuyerReleaseTest {

    private BookingGroupLineRepository lines;
    private TicketBookingRepository tickets;
    private WorkshopOperationsAdapter operations;
    private GroupLoyaltyOutcomeService loyalty;
    private GroupConfirmationService service;
    private BookingGroup group;
    private BookingGroupLine line;
    private TicketBooking ticket;

    @BeforeEach
    void setUp() {
        BookingGroupRepository groups = mock(BookingGroupRepository.class);
        lines = mock(BookingGroupLineRepository.class);
        tickets = mock(TicketBookingRepository.class);
        operations = mock(WorkshopOperationsAdapter.class);
        loyalty = mock(GroupLoyaltyOutcomeService.class);
        service = new GroupConfirmationService(groups, lines, tickets, mock(TicketPriceSnapshotRepository.class),
                mock(BookingGroupMagicLinkRepository.class), operations, loyalty,
                mock(ApplicationEventPublisher.class), mock(GroupNotificationService.class));

        group = BookingGroup.builder().id(1L).groupCode("GRP-R1").groupStatus(BookingStatus.PENDING_PAYMENT)
                .paymentMode(GroupPaymentMode.VNPAY).holdExpiresAt(Instant.now().plusSeconds(300)).build();
        line = BookingGroupLine.builder().id(2L).group(group).lineIndex(0).lineReference("LINE-R1")
                .sessions(new ArrayList<>()).build();
        line.getSessions().add(BookingGroupLineSession.builder().line(line).ordinal(1)
                .sessionReference(UUID.randomUUID().toString()).holdReference(UUID.randomUUID().toString())
                .holdStatus(LineHoldStatus.RESERVED).build());
        ticket = TicketBooking.builder().id(3L).group(group).ticketStatus(BookingStatus.PENDING_PAYMENT).build();
        when(lines.findByGroupIdOrderByLineIndexAsc(1L)).thenReturn(List.of(line));
        when(tickets.findByGroupIdOrderByLineIndexAscParticipantOrdinalAsc(1L)).thenReturn(List.of(ticket));
        when(operations.releaseBookingHolds(eq("LINE-R1"), anyList(), anyString(), anyString()))
                .thenReturn(CapacityReleaseOutcome.accepted());
    }

    @Test
    void releasesEveryHoldAndTheBenefitsAndCancelsTheGroup() {
        service.releasePendingGroupByBuyer(group, false, "REL-1", "corr");

        assertThat(group.getGroupStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(ticket.getTicketStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(line.getSessions()).allSatisfy(s -> assertThat(s.getHoldStatus()).isEqualTo(LineHoldStatus.RELEASED));
        verify(loyalty).releaseExpiredGroup(group);
    }

    @Test
    void aStartedPaymentIsNeverOrphaned() {
        assertThatThrownBy(() -> service.releasePendingGroupByBuyer(group, true, "REL-1", "corr"))
                .isInstanceOf(WorkshopBookingProblemException.class).hasMessageContaining("payment");
        assertThat(group.getGroupStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        verifyNoInteractions(operations, loyalty);
    }

    @Test
    void aGroupWhoseHoldRanOutIsNotReleasedByTheBuyer() {
        group.setHoldExpiresAt(Instant.now().minusSeconds(1));

        assertThatThrownBy(() -> service.releasePendingGroupByBuyer(group, false, "REL-1", "corr"))
                .isInstanceOf(WorkshopBookingProblemException.class);
        verifyNoInteractions(operations);
    }
}
