package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.AttendanceTicketRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketSessionEntitlementRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceTicket;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketSessionEntitlement;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketValidityState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.EquivalentReplacementSessions;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.FindEquivalentReplacementSessionsQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.EquivalentReplacementSessionService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.TicketSessionReplacementService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopPackageReservationPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 003.3 T112 / FR-043: the read-only search for equivalent replacement sessions (decided 2026-11-14). */
class EquivalentReplacementSessionServiceTest {

    private static final String BOOKING_LINE = "GRP-1-L1";

    private final AttendanceTicketRepository tickets = mock(AttendanceTicketRepository.class);
    private final TicketSessionEntitlementRepository entitlements = mock(TicketSessionEntitlementRepository.class);
    private final WorkshopSessionRepository sessions = mock(WorkshopSessionRepository.class);
    private final WorkshopPackageReservationPolicy policy = mock(WorkshopPackageReservationPolicy.class);
    private final TicketSessionReplacementService replacement = mock(TicketSessionReplacementService.class);
    private final EquivalentReplacementSessionService service =
            new EquivalentReplacementSessionService(tickets, entitlements, sessions, policy, replacement);

    private final AtomicLong ids = new AtomicLong(1);
    private final WorkshopLocation branch = WorkshopLocation.builder().id(7L).build();
    private AttendanceTicket ticket;

    @BeforeEach
    void setUp() {
        ticket = AttendanceTicket.builder().id(100L).reference(UUID.randomUUID().toString())
                .bookingReference(BOOKING_LINE).validityState(TicketValidityState.ACTIVE).build();
        when(tickets.findByReference(ticket.getReference())).thenReturn(Optional.of(ticket));
        when(policy.isValidTicketReplacement(eq(BOOKING_LINE), any())).thenReturn(true);
        when(replacement.remainingCapacity(any(), any())).thenReturn(5);
    }

    private WorkshopSession session(int hoursFromNow, SessionState state) {
        Instant start = Instant.now().plus(hoursFromNow, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
        return WorkshopSession.builder().id(ids.getAndIncrement()).reference(UUID.randomUUID().toString())
                .location(branch).workshopDate(LocalDate.ofInstant(start, ZoneId.of("Asia/Ho_Chi_Minh")))
                .scheduledStartAt(start).scheduledEndAt(start.plus(2, ChronoUnit.HOURS)).capacity(20)
                .state(state).rescheduleQuotaUsed(0).build();
    }

    private void ticketHolds(WorkshopSession... held) {
        when(entitlements.findByTicketId(ticket.getId())).thenReturn(java.util.Arrays.stream(held)
                .map(session -> TicketSessionEntitlement.builder().ticket(ticket).session(session).build()).toList());
    }

    private void branchOffers(WorkshopSession... offered) {
        when(sessions.findByLocationIdAndStateAndWorkshopDateGreaterThanEqualOrderByScheduledStartAtAsc(
                eq(7L), eq(SessionState.PUBLISHED), any(LocalDate.class))).thenReturn(List.of(offered));
    }

    private EquivalentReplacementSessions find(WorkshopSession cancelled) {
        return service.find(new FindEquivalentReplacementSessionsQuery(
                UUID.fromString(ticket.getReference()), UUID.fromString(cancelled.getReference())));
    }

    @Test
    @DisplayName("returns every valid session in start order, with no cap")
    void returnsAllValidSessionsInStartOrder() {
        WorkshopSession cancelled = session(30, SessionState.CANCELLATION_REQUESTED);
        WorkshopSession a = session(60, SessionState.PUBLISHED);
        WorkshopSession b = session(90, SessionState.PUBLISHED);
        WorkshopSession c = session(120, SessionState.PUBLISHED);
        WorkshopSession d = session(150, SessionState.PUBLISHED);
        ticketHolds(cancelled);
        branchOffers(a, b, c, d);

        assertThat(find(cancelled).candidates()).extracting(EquivalentReplacementSessions.Candidate::sessionReference)
                .containsExactly(UUID.fromString(a.getReference()), UUID.fromString(b.getReference()),
                        UUID.fromString(c.getReference()), UUID.fromString(d.getReference()));
    }

    @Test
    @DisplayName("leaves out a session that is full, out of reschedule quota, already past, or one the ticket already holds")
    void leavesOutUnsuitableSessions() {
        WorkshopSession cancelled = session(30, SessionState.CANCELLATION_REQUESTED);
        WorkshopSession full = session(60, SessionState.PUBLISHED);
        WorkshopSession noQuota = session(70, SessionState.PUBLISHED);
        noQuota.setRescheduleQuotaOverride(1);
        noQuota.setRescheduleQuotaUsed(1);
        WorkshopSession past = session(-5, SessionState.PUBLISHED);
        WorkshopSession good = session(80, SessionState.PUBLISHED);
        ticketHolds(cancelled);
        branchOffers(full, noQuota, past, good, cancelled);
        when(replacement.remainingCapacity(eq(full), any())).thenReturn(0);

        assertThat(find(cancelled).candidates()).extracting(EquivalentReplacementSessions.Candidate::sessionReference)
                .containsExactly(UUID.fromString(good.getReference()));
    }

    @Test
    @DisplayName("is empty when the package rules refuse every candidate")
    void emptyWhenPackageRulesRefuse() {
        WorkshopSession cancelled = session(30, SessionState.CANCELLATION_REQUESTED);
        ticketHolds(cancelled);
        branchOffers(session(60, SessionState.PUBLISHED), session(90, SessionState.PUBLISHED));
        when(policy.isValidTicketReplacement(eq(BOOKING_LINE), any())).thenReturn(false);

        assertThat(find(cancelled).candidates()).isEmpty();
    }

    @Test
    @DisplayName("a two-session ticket only replaces the cancelled session and keeps its place in the order")
    void twoSessionTicketKeepsOtherSessionInPlace() {
        WorkshopSession first = session(30, SessionState.CANCELLATION_REQUESTED);
        WorkshopSession second = session(100, SessionState.PUBLISHED);
        WorkshopSession beforeSecond = session(60, SessionState.PUBLISHED);
        WorkshopSession afterSecond = session(130, SessionState.PUBLISHED);
        ticketHolds(first, second);
        branchOffers(beforeSecond, second, afterSecond);

        // afterSecond would swap the ticket's order (it would start after the kept session) and is not offered.
        assertThat(find(first).candidates()).extracting(EquivalentReplacementSessions.Candidate::sessionReference)
                .containsExactly(UUID.fromString(beforeSecond.getReference()));
    }

    @Test
    @DisplayName("is empty for an unknown or cancelled ticket and for a session the ticket does not hold")
    void emptyForUnknownTicketOrSession() {
        WorkshopSession held = session(30, SessionState.PUBLISHED);
        WorkshopSession other = session(40, SessionState.PUBLISHED);
        ticketHolds(held);
        branchOffers(session(60, SessionState.PUBLISHED));

        assertThat(find(other).candidates()).isEmpty();

        ticket.setValidityState(TicketValidityState.CANCELLED);
        assertThat(find(held).candidates()).isEmpty();

        when(tickets.findByReference(anyString())).thenReturn(Optional.empty());
        assertThat(service.find(new FindEquivalentReplacementSessionsQuery(UUID.randomUUID(), UUID.randomUUID()))
                .candidates()).isEmpty();
    }
}
