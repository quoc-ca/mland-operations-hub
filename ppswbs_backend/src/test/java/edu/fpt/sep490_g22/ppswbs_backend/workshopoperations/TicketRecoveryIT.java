package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.common.booking.BookingTicketRedeliveryPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.WorkshopBookingNotificationFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.CheckInOverrideRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.TicketResendRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** T040: MySQL integration coverage for ticket recovery and manual override. */
@SpringBootTest
@ActiveProfiles("workshopoperations-mysql-test")
@TestPropertySource(properties = "workshop.operations.auth.allow-test-tokens=true")
class TicketRecoveryIT {

    private static final String STAFF_TOKEN = "Bearer test-uid:recovery-staff";
    private static final String WRONG_LOCATION_TOKEN = "Bearer test-uid:recovery-wrong-location";

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired WorkshopLocationRepository locationRepository;
    @Autowired WorkshopSlotRepository slotRepository;
    @Autowired WorkshopSessionRepository sessionRepository;
    @Autowired AttendanceTicketRepository ticketRepository;
    @Autowired TicketSessionEntitlementRepository entitlementRepository;
    @Autowired TicketResendThrottleRepository throttleRepository;
    @Autowired AttendanceActionRepository actionRepository;
    @Autowired OperationsAuditEventRepository auditRepository;
    @Autowired SessionStaffAssignmentRepository staffAssignmentRepository;
    @Autowired TicketResendService ticketResendService;
    @Autowired CheckInOverrideService checkInOverrideService;

    @MockBean AccountFacade accountFacade;
    @MockBean WorkshopBookingFacade workshopBookingFacade;
    @MockBean WorkshopBookingNotificationFacade workshopBookingNotificationFacade;

    private WorkshopLocation location;
    private WorkshopLocation otherLocation;
    private WorkshopSession session;
    private WorkshopSession otherSession;
    private AttendanceTicket activeTicket;

    @BeforeEach
    void setUp() {
        String product;
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            product = connection.getMetaData().getDatabaseProductName();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to inspect integration database", e);
        }
        Assumptions.assumeTrue("MySQL".equalsIgnoreCase(product),
                "TicketRecoveryIT requires a real MySQL database; found " + product);

        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbcTemplate.execute("TRUNCATE TABLE attendance_actions");
        jdbcTemplate.execute("TRUNCATE TABLE ticket_session_entitlements");
        jdbcTemplate.execute("TRUNCATE TABLE attendance_tickets");
        jdbcTemplate.execute("TRUNCATE TABLE ticket_resend_throttles");
        jdbcTemplate.execute("TRUNCATE TABLE operations_audit_events");
        jdbcTemplate.execute("TRUNCATE TABLE session_staff_assignments");
        jdbcTemplate.execute("TRUNCATE TABLE workshop_sessions");
        jdbcTemplate.execute("TRUNCATE TABLE workshop_slots");
        jdbcTemplate.execute("TRUNCATE TABLE workshop_locations");
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");

        location = locationRepository.save(location("RECOVERY-A"));
        otherLocation = locationRepository.save(location("RECOVERY-B"));
        WorkshopSlot slot = slotRepository.save(slot(location));
        WorkshopSlot otherSlot = slotRepository.save(slot(otherLocation));
        session = sessionRepository.save(session(location, slot));
        otherSession = sessionRepository.save(session(otherLocation, otherSlot));
        // A Staff member acts on a session only while actively assigned to it (owner decision 2026-11-06).
        staffAssignmentRepository.save(SessionStaffAssignment.builder().session(session).accountId(801L)
                .assignedById(10L).assignedAt(Instant.now()).active(true).build());

        activeTicket = ticketRepository.save(ticket("BOOK-RECOVERY", "TICKET-RECOVERY-1"));
        entitlementRepository.save(entitlement(activeTicket, session, AttendanceState.NOT_CHECKED_IN));
        entitlementRepository.save(entitlement(activeTicket, otherSession, AttendanceState.NOT_CHECKED_IN));

        when(accountFacade.resolveAccount("recovery-staff")).thenReturn(Optional.of(
                new AccountSummary(801L, ActorRole.STAFF, ActorStatus.ACTIVE, "staff@test", Instant.now(), Instant.now())));
        when(accountFacade.getLocationScope(801L)).thenReturn(List.of(location.getId()));
        when(accountFacade.resolveAccount("recovery-wrong-location")).thenReturn(Optional.of(
                new AccountSummary(802L, ActorRole.STAFF, ActorStatus.ACTIVE, "wrong@test", Instant.now(), Instant.now())));
        when(accountFacade.getLocationScope(802L)).thenReturn(List.of(otherLocation.getId()));
        when(workshopBookingFacade.requestBookingTicketRedelivery(any()))
                .thenReturn(TicketRedeliveryOutcome.accepted());
    }

    @Test
    void resendDelegatesOnlyActiveUnusedOpaqueReferencesAcrossEntitlements() {
        BookingTicketRedeliveryPort.RedeliveryOutcome result = ticketResendService.requestResend(
                STAFF_TOKEN, "corr-recovery-1", "op-recovery-1", session.getReference(),
                TicketResendRequest.builder().bookingCode("BOOK-RECOVERY").build());

        assertThat(result.status()).isEqualTo(BookingTicketRedeliveryPort.RedeliveryOutcome.Status.ACCEPTED);
        ArgumentCaptor<RequestBookingTicketRedeliveryCommand> captor =
                ArgumentCaptor.forClass(RequestBookingTicketRedeliveryCommand.class);
        verify(workshopBookingFacade).requestBookingTicketRedelivery(captor.capture());
        assertThat(captor.getValue().bookingReference()).isEqualTo("BOOK-RECOVERY");
        assertThat(captor.getValue().activeUnusedTicketReferences())
                .containsExactly(activeTicket.getReference());
        assertThat(auditRepository.findBySessionIdOrderByOccurredAtDesc(session.getId()))
                .anyMatch(event -> "TICKET_REDELIVERY_REQUESTED".equals(event.getAction()));
    }

    private TicketResendRequest resendRequest() {
        return TicketResendRequest.builder().bookingCode("BOOK-RECOVERY").build();
    }

    private void moveWindowStart(long secondsAgo) {
        TicketResendThrottle t = throttleRepository.findAll().get(0);
        t.setWindowStartedAt(Instant.now().minusSeconds(secondsAgo));
        throttleRepository.saveAndFlush(t);
    }

    private void resend(String tag) {
        ticketResendService.requestResend(STAFF_TOKEN, "corr-" + tag, "op-" + tag, session.getReference(), resendRequest());
    }

    @Test
    void resendThrottleRejectsFourthRequestWithRetryAfterAndSendsNothing() {
        for (int i = 0; i < 3; i++) {
            resend("t" + i);
        }
        clearInvocations(workshopBookingFacade);
        assertThatThrownBy(() -> resend("t4"))
                .isInstanceOfSatisfying(WorkshopRateLimitException.class, ex ->
                        assertThat(ex.getRetryAfterSeconds()).isBetween(1L, 900L));
        verifyNoInteractions(workshopBookingFacade);
        Integer throttleRows = jdbcTemplate.queryForObject(
                "select count(*) from ticket_resend_throttles where booking_reference = ?",
                Integer.class, "BOOK-RECOVERY");
        assertThat(throttleRows).isEqualTo(1);
    }

    @Test
    void retryAfterIsRemainingWindowRoundedUpAndRequestAfterWindowIsAccepted() {
        resend("w1");
        resend("w2");
        resend("w3");
        // The window started 14 min 30 s ago: 30 s remain.
        moveWindowStart(870);
        assertThatThrownBy(() -> resend("w4"))
                .isInstanceOfSatisfying(WorkshopRateLimitException.class, ex ->
                        assertThat(ex.getRetryAfterSeconds()).isBetween(28L, 31L));
        // The window ended 1 s ago: the next request is accepted and starts a new window.
        moveWindowStart(901);
        resend("w5");
        assertThat(throttleRepository.findAll().get(0).getRequestCount())
                .isEqualTo(1);
    }

    @Test
    void resendRepeatSendsANewDeliveryEachTimeUntilTheLimit() {
        resend("r1");
        resend("r2");
        resend("r3");
        verify(workshopBookingFacade, times(3)).requestBookingTicketRedelivery(any());
    }

    @Test
    void bookingWithNoActiveUnusedTicketIsRefusedWithReasonAndConsumesNoSlot() {
        activeTicket.setValidityState(TicketValidityState.CANCELLED);
        ticketRepository.save(activeTicket);
        for (int i = 0; i < 5; i++) {
            int n = i;
            assertThatThrownBy(() -> resend("n" + n))
                    .isInstanceOfSatisfying(WorkshopTicketResendRefusedException.class, ex ->
                            assertThat(ex.getReason()).isEqualTo(WorkshopTicketResendRefusedException.Reason.NO_ELIGIBLE_TICKET));
        }
        verifyNoInteractions(workshopBookingFacade);
        Integer rows = jdbcTemplate.queryForObject("select count(*) from ticket_resend_throttles", Integer.class);
        assertThat(rows).isZero();
    }

    @Test
    void unknownBookingIsRefusedLikeABookingWithNoEligibleTicket() {
        assertThatThrownBy(() -> ticketResendService.requestResend(STAFF_TOKEN, "corr-x", "op-x", session.getReference(),
                TicketResendRequest.builder().bookingCode("BOOK-DOES-NOT-EXIST").build()))
                .isInstanceOfSatisfying(WorkshopTicketResendRefusedException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo(WorkshopTicketResendRefusedException.Reason.NO_ELIGIBLE_TICKET));
    }

    @Test
    void bookingRefusalConsumesNoSlotSoThreeAcceptedRequestsStillFit() {
        reset(workshopBookingFacade);
        when(workshopBookingFacade.requestBookingTicketRedelivery(any()))
                .thenReturn(TicketRedeliveryOutcome.rejected(BookingRejectionReason.INVALID_STATE));
        for (int i = 0; i < 4; i++) {
            int n = i;
            assertThatThrownBy(() -> resend("b" + n))
                    .isInstanceOfSatisfying(WorkshopTicketResendRefusedException.class, ex ->
                            assertThat(ex.getReason()).isEqualTo(
                                    WorkshopTicketResendRefusedException.Reason.REDELIVERY_REJECTED_BY_BOOKING));
        }
        assertThat(jdbcTemplate.queryForObject("select count(*) from ticket_resend_throttles", Integer.class)).isZero();

        reset(workshopBookingFacade);
        when(workshopBookingFacade.requestBookingTicketRedelivery(any())).thenReturn(TicketRedeliveryOutcome.accepted());
        resend("a1");
        resend("a2");
        resend("a3");
        assertThatThrownBy(() -> resend("a4")).isInstanceOf(WorkshopRateLimitException.class);
    }

    @Test
    void wrongLocationStaffCannotResendOrOverride() {
        assertThatThrownBy(() -> ticketResendService.requestResend(
                WRONG_LOCATION_TOKEN, "corr-wrong", "op-wrong", session.getReference(),
                TicketResendRequest.builder().bookingCode("BOOK-RECOVERY").build()))
                .isInstanceOf(WorkshopLocationScopeException.class);
        assertThatThrownBy(() -> checkInOverrideService.overrideCheckIn(
                WRONG_LOCATION_TOKEN, "corr-wrong-override", session.getReference(),
                CheckInOverrideRequest.builder().ticketCode(activeTicket.getTicketCode())
                        .reasonCode("TICKET_REDELIVERY_UNAVAILABLE").reasonNote("mail unavailable")
                        .evidenceReference("corr-failed-redelivery").build()))
                .isInstanceOf(WorkshopLocationScopeException.class);
    }

    @Test
    void overrideChecksInUnusedTicketAndPersistsReasonEvidence() {
        var result = checkInOverrideService.overrideCheckIn(
                STAFF_TOKEN, "corr-override", session.getReference(),
                CheckInOverrideRequest.builder().ticketCode(activeTicket.getTicketCode())
                        .reasonCode("TICKET_REDELIVERY_UNAVAILABLE").reasonNote("delivery unavailable")
                        .evidenceReference("corr-failed-redelivery").build());

        assertThat(result.getCheckInResult().name()).isEqualTo("CHECKED_IN");
        TicketSessionEntitlement stored = entitlementRepository
                .findByTicketIdAndSessionId(activeTicket.getId(), session.getId()).orElseThrow();
        assertThat(stored.getAttendanceState()).isEqualTo(AttendanceState.CHECKED_IN);
        assertThat(actionRepository.findByTicketIdAndSessionIdOrderByOccurredAtDesc(
                activeTicket.getId(), session.getId()))
                .anyMatch(action -> action.getActionType() == AttendanceActionType.CHECK_IN_OVERRIDE
                        && "TICKET_REDELIVERY_UNAVAILABLE".equals(action.getReasonCode()));
    }

    @Test
    void resendExcludesCancelledRevokedRefundedUsedAndNoShowTickets() {
        AttendanceTicket cancelled = ticketRepository.save(ticket("BOOK-RECOVERY", "TICKET-CANCELLED"));
        cancelled.setValidityState(TicketValidityState.CANCELLED);
        ticketRepository.save(cancelled);
        AttendanceTicket revoked = ticketRepository.save(ticket("BOOK-RECOVERY", "TICKET-REVOKED"));
        revoked.setValidityState(TicketValidityState.REVOKED);
        ticketRepository.save(revoked);
        AttendanceTicket refunded = ticketRepository.save(ticket("BOOK-RECOVERY", "TICKET-REFUNDED"));
        refunded.setValidityState(TicketValidityState.REFUNDED);
        ticketRepository.save(refunded);
        AttendanceTicket used = ticketRepository.save(ticket("BOOK-RECOVERY", "TICKET-USED"));
        entitlementRepository.save(entitlement(used, session, AttendanceState.CHECKED_IN));
        AttendanceTicket noShow = ticketRepository.save(ticket("BOOK-RECOVERY", "TICKET-NOSHOW"));
        entitlementRepository.save(entitlement(noShow, session, AttendanceState.NO_SHOW));

        ticketResendService.requestResend(STAFF_TOKEN, "corr-filter", "op-filter", session.getReference(),
                TicketResendRequest.builder().bookingCode("BOOK-RECOVERY").build());

        ArgumentCaptor<RequestBookingTicketRedeliveryCommand> captor =
                ArgumentCaptor.forClass(RequestBookingTicketRedeliveryCommand.class);
        verify(workshopBookingFacade).requestBookingTicketRedelivery(captor.capture());
        assertThat(captor.getValue().activeUnusedTicketReferences())
                .containsExactly(activeTicket.getReference());
    }

    private WorkshopLocation location(String code) {
        return WorkshopLocation.builder().code(code).displayName(code).addressSnapshot("test")
                .active(true).build();
    }

    private WorkshopSlot slot(WorkshopLocation location) {
        return WorkshopSlot.builder().reference(UUID.randomUUID().toString()).location(location)
                .label("Recovery Slot").startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(12, 0)).active(true).build();
    }

    private WorkshopSession session(WorkshopLocation location, WorkshopSlot slot) {
        LocalDate date = LocalDate.now().plusDays(2);
        Instant start = date.atTime(slot.getStartTime()).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        Instant end = date.atTime(slot.getEndTime()).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        return WorkshopSession.builder().reference(UUID.randomUUID().toString()).location(location).slot(slot)
                .workshopDate(date).scheduledStartAt(start).scheduledEndAt(end)
                .checkInOpensAt(start.minus(30, ChronoUnit.MINUTES)).checkInOpeningSource(CheckInOpeningSource.DEFAULT)
                .capacity(10).state(SessionState.PUBLISHED).build();
    }

    private AttendanceTicket ticket(String booking, String code) {
        return AttendanceTicket.builder().reference(UUID.randomUUID().toString()).bookingReference(booking)
                .ticketSequence(1).ticketCode(code).qrReference("QR-" + code).validityState(TicketValidityState.ACTIVE)
                .build();
    }

    private TicketSessionEntitlement entitlement(AttendanceTicket ticket, WorkshopSession session, AttendanceState state) {
        return TicketSessionEntitlement.builder().ticket(ticket).session(session).attendanceState(state).build();
    }
}
