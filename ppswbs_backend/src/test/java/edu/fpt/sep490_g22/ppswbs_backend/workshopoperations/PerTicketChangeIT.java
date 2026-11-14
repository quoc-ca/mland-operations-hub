package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.RescheduleQuotaRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.SessionResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.when;

/**
 * 003.2 T091/T094/T095 on MySQL: per-ticket cancellation (FR-031), per-ticket session replacement (FR-034)
 * and the per-session reschedule quota (FR-035).
 */
@SpringBootTest
@ActiveProfiles("workshopoperations-mysql-test")
@TestPropertySource(properties = {"workshop.operations.auth.allow-test-tokens=true"})
class PerTicketChangeIT {

    private static final String MANAGER_TOKEN = "Bearer test-uid:ptc-manager";

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private WorkshopLocationRepository locationRepository;
    @Autowired private WorkshopSlotRepository slotRepository;
    @Autowired private WorkshopPackageRepository packageRepository;
    @Autowired private WorkshopSessionRepository sessionRepository;
    @Autowired private AttendanceTicketRepository ticketRepository;
    @Autowired private TicketSessionEntitlementRepository entitlementRepository;
    @Autowired private TicketEntitlementReleaseRepository releaseRepository;
    @Autowired private TicketSessionReplacementRepository replacementRepository;
    @Autowired private WorkshopOperationsFacade facade;
    @Autowired private WorkshopSessionService sessionService;

    @MockBean private AccountFacade accountFacade;
    @MockBean private TokenVerificationService tokenVerificationService;

    private WorkshopLocation location;
    private WorkshopSlot morning;
    private WorkshopSlot afternoon;
    private Long packageId;

    @BeforeEach
    void setUp() throws Exception {
        try (var connection = jdbcTemplate.getDataSource().getConnection()) {
            assumeTrue(connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql"),
                    "PerTicketChangeIT requires real MySQL evidence");
        }
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : List.of("facade_command_idempotency_records", "attendance_actions",
                "ticket_session_replacements", "ticket_entitlement_releases", "ticket_qr_generations",
                "ticket_session_entitlements", "attendance_tickets", "capacity_holds",
                "workshop_booking_package_policy_snapshots", "session_operational_leads",
                "session_staff_assignments", "cancellation_handoffs", "operations_audit_events",
                "workshop_sessions", "workshop_slots", "workshop_locations")) {
            jdbcTemplate.execute("TRUNCATE TABLE " + table);
        }
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");

        location = locationRepository.save(WorkshopLocation.builder().code("LOC-PTC-01")
                .displayName("Per-ticket Hub").addressSnapshot("2 Test St").active(true).build());
        morning = slotRepository.save(WorkshopSlot.builder().reference(UUID.randomUUID().toString())
                .location(location).label("Morning").startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(12, 0))
                .defaultCapacity(10).active(true).build());
        afternoon = slotRepository.save(WorkshopSlot.builder().reference(UUID.randomUUID().toString())
                .location(location).label("Afternoon").startTime(LocalTime.of(14, 0)).endTime(LocalTime.of(17, 0))
                .defaultCapacity(10).active(true).build());
        packageId = packageRepository.save(WorkshopPackage.builder().name("Per-ticket test package")
                .description("Synthetic fixture").publicPrice(1000L).minParticipants(1).maxParticipants(10)
                .requiredSessionCount(1).sameLocationRequired(true).minimumSameDaySlotGap(0)
                .state(WorkshopPackageState.PUBLISHED).build()).getId();

        when(accountFacade.resolveAccount("ptc-manager")).thenReturn(Optional.of(new AccountSummary(
                301L, ActorRole.MANAGER, ActorStatus.ACTIVE, "ptcmanager@mland.vn", Instant.now(), Instant.now())));
        when(accountFacade.getAccountStatus(301L)).thenReturn(Optional.of(ActorStatus.ACTIVE));
        when(accountFacade.getLocationScope(301L)).thenReturn(List.of(location.getId()));
        when(tokenVerificationService.verify("test-uid:ptc-manager")).thenReturn(
                new VerifiedIdentity("ptc-manager", "ptcmanager@mland.vn", true, Instant.now()));
        when(accountFacade.resolveActor("ptc-manager", true)).thenReturn(Optional.of(new CurrentAccountActor(
                301L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of(location.getId()))));
    }

    // ---- T091: per-ticket cancellation ----

    @Test
    @DisplayName("T091-1: cancelling one ticket frees its seat and leaves the other ticket of the group untouched")
    void cancelTicket_releasesOnlyItsOwnSeat() {
        WorkshopSession session = publishedSession(2, morning, 5);
        List<AttendanceTicket> group = confirmGroup(session, 2);
        AttendanceTicket first = group.get(0);
        AttendanceTicket second = group.get(1);
        int remainingBefore = remainingCapacity(session);

        TicketCancellationOutcome outcome = facade.cancelTicket(new CancelTicketCommand(
                UUID.fromString(first.getReference()), "case-ptc-1", "op-" + UUID.randomUUID(), "corr-ptc"));

        assertThat(outcome.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        assertThat(outcome.releasedSessionReferences())
                .containsExactly(UUID.fromString(session.getReference()));
        assertThat(ticketRepository.findByReference(first.getReference()).orElseThrow().getValidityState())
                .isEqualTo(TicketValidityState.CANCELLED);
        assertThat(entitlementRepository.findByTicketId(first.getId())).isEmpty();
        assertThat(releaseRepository.findByTicketIdOrderByIdAsc(first.getId())).hasSize(1);

        // The other ticket keeps its seat, its QR and its entitlement.
        AttendanceTicket survivor = ticketRepository.findByReference(second.getReference()).orElseThrow();
        assertThat(survivor.getValidityState()).isEqualTo(TicketValidityState.ACTIVE);
        assertThat(survivor.getQrReference()).isEqualTo(second.getQrReference());
        assertThat(entitlementRepository.findByTicketId(second.getId())).hasSize(1);
        assertThat(remainingCapacity(session)).isEqualTo(remainingBefore + 1);
    }

    @Test
    @DisplayName("T091-2: replaying the same cancellation reports the same seats and frees nothing twice")
    void cancelTicket_replay_isIdempotent() {
        WorkshopSession session = publishedSession(3, morning, 5);
        AttendanceTicket ticket = confirmGroup(session, 1).getFirst();
        String operation = "op-" + UUID.randomUUID();

        TicketCancellationOutcome first = facade.cancelTicket(new CancelTicketCommand(
                UUID.fromString(ticket.getReference()), "case-ptc-2", operation, "corr-ptc"));
        int remainingAfterFirst = remainingCapacity(session);
        TicketCancellationOutcome replay = facade.cancelTicket(new CancelTicketCommand(
                UUID.fromString(ticket.getReference()), "case-ptc-2", operation, "corr-ptc"));

        assertThat(replay.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        assertThat(replay.releasedSessionReferences()).isEqualTo(first.releasedSessionReferences());
        assertThat(releaseRepository.findByTicketIdOrderByIdAsc(ticket.getId())).hasSize(1);
        assertThat(remainingCapacity(session)).isEqualTo(remainingAfterFirst);
    }

    @Test
    @DisplayName("T091-3: a checked-in session keeps its attendance record and is not released")
    void cancelTicket_keepsAttendanceHistory() {
        WorkshopSession session = publishedSession(4, morning, 5);
        AttendanceTicket ticket = confirmGroup(session, 1).getFirst();
        TicketSessionEntitlement entitlement = entitlementRepository.findByTicketId(ticket.getId()).getFirst();
        entitlement.setAttendanceState(AttendanceState.CHECKED_IN);
        entitlement.setCheckedInAt(Instant.now());
        entitlementRepository.saveAndFlush(entitlement);

        TicketCancellationOutcome outcome = facade.cancelTicket(new CancelTicketCommand(
                UUID.fromString(ticket.getReference()), "case-ptc-3", "op-" + UUID.randomUUID(), "corr-ptc"));

        assertThat(outcome.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        assertThat(outcome.releasedSessionReferences()).isEmpty();
        assertThat(entitlementRepository.findByTicketId(ticket.getId())).hasSize(1);
        assertThat(releaseRepository.findByTicketIdOrderByIdAsc(ticket.getId())).isEmpty();
    }

    // ---- T095: the reschedule quota ----

    @Test
    @DisplayName("T095-1: the default quota is floor(25% x capacity) with a minimum of 1 and follows the capacity")
    void rescheduleQuota_defaultFollowsCapacity() {
        WorkshopSession twelveSeats = publishedSession(5, morning, 12);
        WorkshopSession twoSeats = publishedSession(6, afternoon, 2);

        assertThat(twelveSeats.effectiveRescheduleQuota()).isEqualTo(3);
        assertThat(twoSeats.effectiveRescheduleQuota()).isEqualTo(1);

        twelveSeats.setCapacity(20);
        WorkshopSession saved = sessionRepository.saveAndFlush(twelveSeats);
        assertThat(saved.effectiveRescheduleQuota()).isEqualTo(5);
    }

    @Test
    @DisplayName("T095-2: a Manager-set quota is kept when the capacity changes and clearing it restores the default")
    void rescheduleQuota_managerOverrideIsKept() {
        WorkshopSession session = publishedSession(7, morning, 12);

        SessionResponse overridden = sessionService.setRescheduleQuota(MANAGER_TOKEN, session.getReference(),
                RescheduleQuotaRequest.builder().quota(0).reason("Closed to customer changes").build());
        assertThat(overridden.getRescheduleQuota()).isZero();
        assertThat(overridden.getRescheduleQuotaOverridden()).isTrue();

        WorkshopSession stored = sessionRepository.findByReference(session.getReference()).orElseThrow();
        stored.setCapacity(40);
        assertThat(sessionRepository.saveAndFlush(stored).effectiveRescheduleQuota()).isZero();

        SessionResponse cleared = sessionService.setRescheduleQuota(MANAGER_TOKEN, session.getReference(),
                RescheduleQuotaRequest.builder().quota(null).reason("Back to the default").build());
        assertThat(cleared.getRescheduleQuotaOverridden()).isFalse();
        assertThat(cleared.getRescheduleQuota()).isEqualTo(10);
    }

    // ---- T094: per-ticket session replacement ----

    @Test
    @DisplayName("T094-1: an advance change moves one ticket, counts one quota and leaves the group's other ticket")
    void replace_advance_movesOneTicketAndCountsQuota() {
        WorkshopSession source = publishedSession(8, morning, 5);
        WorkshopSession target = publishedSession(9, afternoon, 5);
        List<AttendanceTicket> group = confirmGroup(source, 2);
        AttendanceTicket moving = group.get(0);
        AttendanceTicket staying = group.get(1);
        int quotaBefore = target.remainingRescheduleQuota();

        TicketSessionReplacementOutcome outcome = facade.replaceTicketSessions(new ReplaceTicketSessionsCommand(
                List.of(new ReplaceTicketSessionsCommand.Line(UUID.fromString(moving.getReference()),
                        List.of(UUID.fromString(target.getReference())))),
                TicketReplacementType.CUSTOMER_ADVANCE, "op-" + UUID.randomUUID(), "corr-ptc"));

        assertThat(outcome.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        assertThat(sessionOf(moving)).isEqualTo(target.getId());
        assertThat(sessionOf(staying)).isEqualTo(source.getId());
        // The QR and the ticket itself are untouched by a replacement.
        assertThat(ticketRepository.findByReference(moving.getReference()).orElseThrow().getQrReference())
                .isEqualTo(moving.getQrReference());
        assertThat(replacementRepository.findByTicketIdOrderByIdAsc(moving.getId())).singleElement()
                .satisfies(record -> {
                    assertThat(record.getReplacementType()).isEqualTo(TicketSessionReplacementType.CUSTOMER_ADVANCE);
                    assertThat(record.isQuotaConsumed()).isTrue();
                });
        WorkshopSession reloadedTarget = sessionRepository.findByReference(target.getReference()).orElseThrow();
        assertThat(reloadedTarget.getRescheduleQuotaUsed()).isEqualTo(1);
        assertThat(reloadedTarget.remainingRescheduleQuota()).isEqualTo(quotaBefore - 1);
    }

    @Test
    @DisplayName("T094-2: a quota of 0 refuses the change and names the failing line, with no partial move")
    void replace_quotaExhausted_isRefusedAllOrNone() {
        WorkshopSession source = publishedSession(10, morning, 5);
        WorkshopSession target = publishedSession(11, afternoon, 5);
        sessionService.setRescheduleQuota(MANAGER_TOKEN, target.getReference(),
                RescheduleQuotaRequest.builder().quota(0).reason("No customer changes").build());
        List<AttendanceTicket> group = confirmGroup(source, 2);

        TicketSessionReplacementOutcome outcome = facade.replaceTicketSessions(new ReplaceTicketSessionsCommand(
                List.of(new ReplaceTicketSessionsCommand.Line(UUID.fromString(group.get(0).getReference()),
                                List.of(UUID.fromString(target.getReference()))),
                        new ReplaceTicketSessionsCommand.Line(UUID.fromString(group.get(1).getReference()),
                                List.of(UUID.fromString(target.getReference())))),
                TicketReplacementType.CUSTOMER_ADVANCE, "op-" + UUID.randomUUID(), "corr-ptc"));

        assertThat(outcome.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(outcome.reason()).isEqualTo(RejectionReason.RESCHEDULE_QUOTA_EXHAUSTED);
        assertThat(outcome.failingLineIndexes()).containsExactly(0, 1);
        assertThat(sessionOf(group.get(0))).isEqualTo(source.getId());
        assertThat(sessionOf(group.get(1))).isEqualTo(source.getId());
        assertThat(replacementRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("T094-3: a Mland replacement neither checks nor consumes the quota")
    void replace_mland_isExemptFromQuota() {
        WorkshopSession source = publishedSession(12, morning, 5);
        WorkshopSession target = publishedSession(13, afternoon, 5);
        sessionService.setRescheduleQuota(MANAGER_TOKEN, target.getReference(),
                RescheduleQuotaRequest.builder().quota(0).reason("No customer changes").build());
        AttendanceTicket ticket = confirmGroup(source, 1).getFirst();

        TicketSessionReplacementOutcome outcome = facade.replaceTicketSessions(new ReplaceTicketSessionsCommand(
                List.of(new ReplaceTicketSessionsCommand.Line(UUID.fromString(ticket.getReference()),
                        List.of(UUID.fromString(target.getReference())))),
                TicketReplacementType.MLAND, "op-" + UUID.randomUUID(), "corr-ptc"));

        assertThat(outcome.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        assertThat(sessionOf(ticket)).isEqualTo(target.getId());
        WorkshopSession reloadedTarget = sessionRepository.findByReference(target.getReference()).orElseThrow();
        assertThat(reloadedTarget.getRescheduleQuotaUsed()).isZero();
        assertThat(replacementRepository.findByTicketIdOrderByIdAsc(ticket.getId())).singleElement()
                .satisfies(record -> assertThat(record.isQuotaConsumed()).isFalse());
    }

    @Test
    @DisplayName("T094-4: a late arrival may leave a running session, but only within the old end plus 7 days")
    void replace_lateArrival_windowEdges() {
        AttendanceTicket ticket = confirmGroup(publishedSession(1, afternoon, 10), 1).getFirst();
        WorkshopSession running = markRunning(sessionRepository.findById(sessionOf(ticket)).orElseThrow());
        WorkshopSession tooLate = publishedSession(9, morning, 5);   // 9 days out: outside the window
        WorkshopSession inWindow = publishedSession(6, morning, 5);  // 6 days out: inside it

        TicketSessionReplacementOutcome refused = facade.replaceTicketSessions(new ReplaceTicketSessionsCommand(
                List.of(new ReplaceTicketSessionsCommand.Line(UUID.fromString(ticket.getReference()),
                        List.of(UUID.fromString(tooLate.getReference())))),
                TicketReplacementType.CUSTOMER_LATE_ARRIVAL, "op-" + UUID.randomUUID(), "corr-ptc"));

        assertThat(refused.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(refused.reason()).isEqualTo(RejectionReason.LATE_ARRIVAL_WINDOW_EXPIRED);
        assertThat(sessionOf(ticket)).isEqualTo(running.getId());

        TicketSessionReplacementOutcome accepted = facade.replaceTicketSessions(new ReplaceTicketSessionsCommand(
                List.of(new ReplaceTicketSessionsCommand.Line(UUID.fromString(ticket.getReference()),
                        List.of(UUID.fromString(inWindow.getReference())))),
                TicketReplacementType.CUSTOMER_LATE_ARRIVAL, "op-" + UUID.randomUUID(), "corr-ptc"));

        assertThat(accepted.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        assertThat(sessionOf(ticket)).isEqualTo(inWindow.getId());
    }

    @Test
    @DisplayName("T094-5: an advance change out of a running session is refused")
    void replace_advanceFromRunningSession_isRefused() {
        AttendanceTicket ticket = confirmGroup(publishedSession(1, afternoon, 10), 1).getFirst();
        WorkshopSession running = markRunning(sessionRepository.findById(sessionOf(ticket)).orElseThrow());
        WorkshopSession target = publishedSession(5, morning, 5);

        TicketSessionReplacementOutcome outcome = facade.replaceTicketSessions(new ReplaceTicketSessionsCommand(
                List.of(new ReplaceTicketSessionsCommand.Line(UUID.fromString(ticket.getReference()),
                        List.of(UUID.fromString(target.getReference())))),
                TicketReplacementType.CUSTOMER_ADVANCE, "op-" + UUID.randomUUID(), "corr-ptc"));

        assertThat(outcome.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(outcome.reason()).isEqualTo(RejectionReason.INVALID_REPLACEMENT);
        assertThat(sessionOf(ticket)).isEqualTo(running.getId());
    }

    @Test
    @DisplayName("T094-6: a full target session is refused on capacity, before the quota is even considered")
    void replace_fullTarget_isRefusedOnCapacity() {
        WorkshopSession source = publishedSession(8, morning, 5);
        WorkshopSession target = publishedSession(9, afternoon, 1);
        AttendanceTicket occupant = confirmGroup(target, 1).getFirst();
        assertThat(occupant.getValidityState()).isEqualTo(TicketValidityState.ACTIVE);
        AttendanceTicket ticket = confirmGroup(source, 1).getFirst();

        TicketSessionReplacementOutcome outcome = facade.replaceTicketSessions(new ReplaceTicketSessionsCommand(
                List.of(new ReplaceTicketSessionsCommand.Line(UUID.fromString(ticket.getReference()),
                        List.of(UUID.fromString(target.getReference())))),
                TicketReplacementType.CUSTOMER_ADVANCE, "op-" + UUID.randomUUID(), "corr-ptc"));

        assertThat(outcome.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(outcome.reason()).isEqualTo(RejectionReason.INSUFFICIENT_CAPACITY);
        assertThat(sessionOf(ticket)).isEqualTo(source.getId());
    }

    @Test
    @DisplayName("T094-7: a cancelled ticket cannot be moved")
    void replace_cancelledTicket_isRefused() {
        WorkshopSession source = publishedSession(8, morning, 5);
        WorkshopSession target = publishedSession(9, afternoon, 5);
        AttendanceTicket ticket = confirmGroup(source, 1).getFirst();
        facade.cancelTicket(new CancelTicketCommand(UUID.fromString(ticket.getReference()),
                "case-ptc-7", "op-" + UUID.randomUUID(), "corr-ptc"));

        TicketSessionReplacementOutcome outcome = facade.replaceTicketSessions(new ReplaceTicketSessionsCommand(
                List.of(new ReplaceTicketSessionsCommand.Line(UUID.fromString(ticket.getReference()),
                        List.of(UUID.fromString(target.getReference())))),
                TicketReplacementType.CUSTOMER_ADVANCE, "op-" + UUID.randomUUID(), "corr-ptc"));

        assertThat(outcome.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(outcome.reason()).isEqualTo(RejectionReason.INVALID_STATE);
        assertThat(outcome.failingLineIndexes()).containsExactly(0);
    }

    // ---- fixtures ----

    private Long sessionOf(AttendanceTicket ticket) {
        List<TicketSessionEntitlement> entitlements = entitlementRepository.findByTicketId(ticket.getId());
        return entitlements.isEmpty() ? null : entitlements.getFirst().getSession().getId();
    }

    private int remainingCapacity(WorkshopSession session) {
        WorkshopSession reloaded = sessionRepository.findByReference(session.getReference()).orElseThrow();
        Integer holds = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(requested_ticket_count), 0) FROM capacity_holds WHERE session_id = ? "
                        + "AND state IN ('ACTIVE', 'CONFIRMED')", Integer.class, reloaded.getId());
        int seats = entitlementRepository.countActiveConfirmedSeatsForSession(reloaded.getId());
        return reloaded.getCapacity() - Math.max(holds == null ? 0 : holds, seats);
    }

    private WorkshopSession publishedSession(int daysAhead, WorkshopSlot slot, int capacity) {
        LocalDate date = LocalDate.now().plusDays(daysAhead);
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        return sessionRepository.saveAndFlush(WorkshopSession.builder()
                .reference(UUID.randomUUID().toString()).location(location).slot(slot).workshopDate(date)
                .scheduledStartAt(date.atTime(slot.getStartTime()).atZone(zone).toInstant())
                .scheduledEndAt(date.atTime(slot.getEndTime()).atZone(zone).toInstant())
                .checkInOpensAt(date.atTime(slot.getStartTime()).atZone(zone).toInstant().minus(1, ChronoUnit.HOURS))
                .checkInOpeningSource(CheckInOpeningSource.DEFAULT)
                .capacity(capacity).state(SessionState.PUBLISHED).build());
    }

    /**
     * Moves an already-booked session to "started an hour ago and not yet ended": the late-arrival source of
     * FR-034. A group can only be reserved into a published future session, so the ticket is confirmed first.
     */
    private WorkshopSession markRunning(WorkshopSession session) {
        Instant now = Instant.now();
        WorkshopSession running = sessionRepository.findByReference(session.getReference()).orElseThrow();
        running.setScheduledStartAt(now.minus(1, ChronoUnit.HOURS));
        running.setScheduledEndAt(now.plus(1, ChronoUnit.HOURS));
        running.setCheckInOpensAt(now.minus(2, ChronoUnit.HOURS));
        running.setWorkshopDate(LocalDate.now());
        running.setState(SessionState.IN_PROGRESS);
        return sessionRepository.saveAndFlush(running);
    }

    /** One booking group of {@code ticketCount} one-ticket lines in the same session, confirmed. */
    private List<AttendanceTicket> confirmGroup(WorkshopSession session, int ticketCount) {
        String groupReference = "GRP-" + UUID.randomUUID().toString().substring(0, 8);
        List<ReserveGroupCapacityCommand.Line> lines = new java.util.ArrayList<>();
        for (int index = 0; index < ticketCount; index++) {
            lines.add(new ReserveGroupCapacityCommand.Line(groupReference + "-L" + index,
                    String.valueOf(packageId), List.of(UUID.fromString(session.getReference())), 1));
        }
        GroupCapacityOutcome reserved = facade.reserveGroupCapacity(new ReserveGroupCapacityCommand(
                groupReference, lines, "op-grp-" + UUID.randomUUID(), "corr-grp"));
        assertThat(reserved.status()).isEqualTo(OutcomeStatus.ACCEPTED);

        List<AttendanceTicket> tickets = new java.util.ArrayList<>();
        for (int index = 0; index < ticketCount; index++) {
            String bookingReference = groupReference + "-L" + index;
            CapacityCommitOutcome committed = facade.commitBookingHolds(new CommitBookingHoldsCommand(
                    bookingReference, reserved.lineHoldReferences().get(index),
                    "op-commit-" + UUID.randomUUID(), "corr-grp"));
            assertThat(committed.status()).isEqualTo(OutcomeStatus.ACCEPTED);
            tickets.add(ticketRepository.findByBookingReference(bookingReference).getFirst());
        }
        return tickets;
    }
}
