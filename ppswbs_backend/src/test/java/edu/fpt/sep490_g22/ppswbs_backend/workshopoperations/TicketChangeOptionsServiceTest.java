package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.AttendanceTicketRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketSessionEntitlementRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceTicket;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketSalesChannel;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketSessionEntitlement;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketValidityState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.ListTicketChangeOptionsQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketChangeOptions;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketReplacementType;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.TicketChangeOptionsService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.TicketReplacementRules;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.TicketSessionReplacementService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopPackageReservationPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 003.3 FR-044a / T176: the listing of the sessions a ticket may move to (decided 2026-11-16). */
class TicketChangeOptionsServiceTest {

    private static final String BOOKING_LINE = "GRP-1-L1";

    private final AttendanceTicketRepository tickets = mock(AttendanceTicketRepository.class);
    private final TicketSessionEntitlementRepository entitlements = mock(TicketSessionEntitlementRepository.class);
    private final WorkshopSessionRepository sessions = mock(WorkshopSessionRepository.class);
    private final WorkshopPackageReservationPolicy policy = mock(WorkshopPackageReservationPolicy.class);
    private final TicketSessionReplacementService replacement = mock(TicketSessionReplacementService.class);
    private final TicketChangeOptionsService service = new TicketChangeOptionsService(tickets, entitlements,
            sessions, policy, replacement, new TicketReplacementRules());

    private final AtomicLong ids = new AtomicLong(1);
    private final WorkshopLocation branch = WorkshopLocation.builder().id(7L).build();
    private final WorkshopLocation otherBranch = WorkshopLocation.builder().id(8L).build();
    private AttendanceTicket ticket;

    @BeforeEach
    void setUp() {
        ticket = AttendanceTicket.builder().id(100L).reference(UUID.randomUUID().toString())
                .bookingReference(BOOKING_LINE).validityState(TicketValidityState.ACTIVE)
                .salesChannel(TicketSalesChannel.DIRECT).build();
        when(tickets.findByReference(ticket.getReference())).thenReturn(Optional.of(ticket));
        when(policy.sameLocationRequired(BOOKING_LINE)).thenReturn(Optional.of(true));
        when(policy.isValidTicketReplacement(eq(BOOKING_LINE), any())).thenReturn(true);
        when(replacement.remainingCapacity(any(), any())).thenReturn(5);
    }

    private WorkshopSession session(int hoursFromNow, SessionState state) {
        return session(hoursFromNow, state, branch);
    }

    private WorkshopSession session(int hoursFromNow, SessionState state, WorkshopLocation location) {
        Instant start = Instant.now().plus(hoursFromNow, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
        return WorkshopSession.builder().id(ids.getAndIncrement()).reference(UUID.randomUUID().toString())
                .location(location).workshopDate(LocalDate.ofInstant(start, ZoneId.of("Asia/Ho_Chi_Minh")))
                .scheduledStartAt(start).scheduledEndAt(start.plus(2, ChronoUnit.HOURS)).capacity(20)
                .state(state).rescheduleQuotaUsed(0).build();
    }

    private void ticketHolds(WorkshopSession... held) {
        when(entitlements.findByTicketId(ticket.getId())).thenReturn(Arrays.stream(held)
                .map(session -> TicketSessionEntitlement.builder().ticket(ticket).session(session)
                        .attendanceState(AttendanceState.NOT_CHECKED_IN).build()).toList());
    }

    private void branchOffers(WorkshopSession... offered) {
        when(sessions.findByLocationIdAndStateAndWorkshopDateGreaterThanEqualOrderByScheduledStartAtAsc(
                eq(7L), eq(SessionState.PUBLISHED), any(LocalDate.class))).thenReturn(List.of(offered));
    }

    private static UUID ref(WorkshopSession session) {
        return UUID.fromString(session.getReference());
    }

    private TicketChangeOptions list(TicketReplacementType type, WorkshopSession... from) {
        return service.list(new ListTicketChangeOptionsQuery(UUID.fromString(ticket.getReference()), type,
                Arrays.stream(from).map(TicketChangeOptionsServiceTest::ref).toList()));
    }

    @Test
    @DisplayName("lists a candidate with its exact seats and quota left; full or no-quota sessions are not listed")
    void listsCandidatesWithSeatsAndQuota() {
        WorkshopSession old = session(80, SessionState.PUBLISHED);
        WorkshopSession open = session(100, SessionState.PUBLISHED);
        WorkshopSession full = session(120, SessionState.PUBLISHED);
        WorkshopSession noQuota = session(140, SessionState.PUBLISHED);
        noQuota.setRescheduleQuotaOverride(1);
        noQuota.setRescheduleQuotaUsed(1);
        ticketHolds(old);
        branchOffers(old, open, full, noQuota);
        when(replacement.remainingCapacity(eq(open), any())).thenReturn(3);
        when(replacement.remainingCapacity(eq(full), any())).thenReturn(0);

        TicketChangeOptions result = list(TicketReplacementType.CUSTOMER_ADVANCE, old);

        assertThat(result.status()).isEqualTo(TicketChangeOptions.Status.OK);
        TicketChangeOptions.FromSession entry = result.perFromSession().get(0);
        assertThat(entry.sourceChangeable()).isTrue();
        assertThat(entry.options()).extracting(TicketChangeOptions.Option::sessionReference)
                .containsExactly(ref(open));
        assertThat(entry.options().get(0).remainingCapacity()).isEqualTo(3);
        assertThat(entry.options().get(0).remainingRescheduleQuota()).isEqualTo(open.remainingRescheduleQuota());
        assertThat(entry.options().get(0).reasonCode()).isNull();
        assertThat(result.pairs()).isEmpty();
    }

    @Test
    @DisplayName("a two-session ticket is answered one session at a time and the candidate keeps its place")
    void twoSessionTicketKeepsPosition() {
        WorkshopSession first = session(50, SessionState.PUBLISHED);
        WorkshopSession second = session(100, SessionState.PUBLISHED);
        WorkshopSession beforeSecond = session(70, SessionState.PUBLISHED);
        WorkshopSession afterSecond = session(130, SessionState.PUBLISHED);
        ticketHolds(first, second);
        branchOffers(first, second, beforeSecond, afterSecond);

        TicketChangeOptions result = list(TicketReplacementType.CUSTOMER_ADVANCE, first);

        assertThat(result.perFromSession()).hasSize(1);
        // afterSecond would swap the order of the ticket's two sessions, so only beforeSecond keeps first's place.
        assertThat(result.perFromSession().get(0).options()).extracting(TicketChangeOptions.Option::sessionReference)
                .containsExactly(ref(beforeSecond));
        assertThat(result.pairs()).isEmpty();
    }

    @Test
    @DisplayName("without from, every current session is listed on its own")
    void noFromListsEverySession() {
        WorkshopSession first = session(50, SessionState.PUBLISHED);
        WorkshopSession second = session(100, SessionState.PUBLISHED);
        ticketHolds(first, second);
        branchOffers(first, second);

        TicketChangeOptions result = list(TicketReplacementType.CUSTOMER_ADVANCE);

        assertThat(result.perFromSession()).extracting(TicketChangeOptions.FromSession::sessionReference)
                .containsExactly(ref(first), ref(second));
    }

    @Test
    @DisplayName("two from sessions also return the valid pairs, in start order")
    void twoFromReturnPairs() {
        WorkshopSession first = session(50, SessionState.PUBLISHED);
        WorkshopSession second = session(100, SessionState.PUBLISHED);
        WorkshopSession a = session(150, SessionState.PUBLISHED);
        WorkshopSession b = session(200, SessionState.PUBLISHED);
        ticketHolds(first, second);
        branchOffers(first, second, a, b);

        TicketChangeOptions result = list(TicketReplacementType.CUSTOMER_ADVANCE, first, second);

        assertThat(result.pairs()).hasSize(1);
        TicketChangeOptions.Pair pair = result.pairs().get(0);
        assertThat(pair.first().sessionReference()).isEqualTo(ref(a));
        assertThat(pair.second().sessionReference()).isEqualTo(ref(b));
        assertThat(pair.second().remainingCapacity()).isPositive();
        assertThat(pair.reasonCode()).isNull();
    }

    @Test
    @DisplayName("a candidate that breaks the package rules is not listed")
    void packageRuleViolationIsLeftOut() {
        WorkshopSession old = session(80, SessionState.PUBLISHED);
        WorkshopSession ok = session(100, SessionState.PUBLISHED);
        WorkshopSession bad = session(120, SessionState.PUBLISHED);
        ticketHolds(old);
        branchOffers(old, ok, bad);
        when(policy.isValidTicketReplacement(eq(BOOKING_LINE), eq(List.of(bad)))).thenReturn(false);

        assertThat(list(TicketReplacementType.CUSTOMER_ADVANCE, old).perFromSession().get(0).options())
                .extracting(TicketChangeOptions.Option::sessionReference).containsExactly(ref(ok));
    }

    @Test
    @DisplayName("another branch is listed only when the package does not require one branch")
    void otherBranchOnlyWhenAllowed() {
        WorkshopSession old = session(80, SessionState.PUBLISHED);
        WorkshopSession elsewhere = session(100, SessionState.PUBLISHED, otherBranch);
        ticketHolds(old);
        branchOffers(old);
        when(policy.sameLocationRequired(BOOKING_LINE)).thenReturn(Optional.of(false));
        when(sessions.findByStateAndWorkshopDateGreaterThanEqualOrderByScheduledStartAtAsc(
                eq(SessionState.PUBLISHED), any(LocalDate.class))).thenReturn(List.of(old, elsewhere));

        TicketChangeOptions.Option option = list(TicketReplacementType.CUSTOMER_ADVANCE, old)
                .perFromSession().get(0).options().get(0);

        assertThat(option.sessionReference()).isEqualTo(ref(elsewhere));
        assertThat(option.locationReference()).isEqualTo(8L);
    }

    @Test
    @DisplayName("a started session cannot be changed in advance and lists nothing")
    void startedSourceIsNotChangeableInAdvance() {
        WorkshopSession started = session(-1, SessionState.IN_PROGRESS);
        ticketHolds(started);
        branchOffers(session(100, SessionState.PUBLISHED));

        TicketChangeOptions.FromSession entry = list(TicketReplacementType.CUSTOMER_ADVANCE, started)
                .perFromSession().get(0);

        assertThat(entry.sourceChangeable()).isFalse();
        assertThat(entry.sourceReasonCode()).isEqualTo("INVALID_REPLACEMENT");
        assertThat(entry.options()).isEmpty();
    }

    @Test
    @DisplayName("late arrival: a running session is changeable and only targets inside end + 7 days are listed")
    void lateArrivalWindow() {
        WorkshopSession running = session(-1, SessionState.IN_PROGRESS);
        WorkshopSession inside = session(24 * 3, SessionState.PUBLISHED);
        WorkshopSession outside = session(24 * 10, SessionState.PUBLISHED);
        ticketHolds(running);
        branchOffers(inside, outside);

        TicketChangeOptions.FromSession entry = list(TicketReplacementType.CUSTOMER_LATE_ARRIVAL, running)
                .perFromSession().get(0);

        assertThat(entry.sourceChangeable()).isTrue();
        assertThat(entry.options()).extracting(TicketChangeOptions.Option::sessionReference)
                .containsExactly(ref(inside));
    }

    @Test
    @DisplayName("refuses an OTA ticket, an inactive ticket, an unknown ticket and a from session of another ticket")
    void refusals() {
        WorkshopSession held = session(80, SessionState.PUBLISHED);
        ticketHolds(held);

        assertThat(list(TicketReplacementType.CUSTOMER_ADVANCE, session(90, SessionState.PUBLISHED)).status())
                .isEqualTo(TicketChangeOptions.Status.FROM_SESSION_NOT_OF_TICKET);

        ticket.setValidityState(TicketValidityState.REVOKED);
        assertThat(list(TicketReplacementType.CUSTOMER_ADVANCE, held).status())
                .isEqualTo(TicketChangeOptions.Status.TICKET_NOT_ACTIVE);

        ticket.setValidityState(TicketValidityState.ACTIVE);
        ticket.setSalesChannel(TicketSalesChannel.OTA);
        assertThat(list(TicketReplacementType.CUSTOMER_ADVANCE, held).status())
                .isEqualTo(TicketChangeOptions.Status.OTA_TICKET_NOT_SUPPORTED);

        assertThat(service.list(new ListTicketChangeOptionsQuery(UUID.randomUUID(),
                TicketReplacementType.CUSTOMER_ADVANCE, List.of())).status())
                .isEqualTo(TicketChangeOptions.Status.TICKET_NOT_FOUND);
    }
}
