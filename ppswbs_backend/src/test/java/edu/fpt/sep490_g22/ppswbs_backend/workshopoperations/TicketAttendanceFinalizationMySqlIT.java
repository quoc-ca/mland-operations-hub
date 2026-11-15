package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketAttendanceFinalizedEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.BookingAttendanceOutcomeService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.TicketAttendanceFinalizationPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.AttendanceCorrectionRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopLifecycleException;
import java.util.Optional;
import static org.mockito.Mockito.when;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 003.2 T096(c) / T092 on MySQL: the per-ticket attendance-finalization event at the 24-hour window edge, once
 * per ticket, on replay, under overlapping claims and with a duplicate event for the same ticket.
 */
@SpringBootTest
@ActiveProfiles("workshopoperations-mysql-test")
@org.springframework.test.context.TestPropertySource(properties = {"workshop.operations.auth.allow-test-tokens=true"})
class TicketAttendanceFinalizationMySqlIT {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private WorkshopLocationRepository locationRepository;
    @Autowired private WorkshopSlotRepository slotRepository;
    @Autowired private WorkshopSessionRepository sessionRepository;
    @Autowired private AttendanceTicketRepository ticketRepository;
    @Autowired private TicketSessionEntitlementRepository entitlementRepository;
    @Autowired private TicketAttendanceFinalizationOutboxRepository outbox;
    @Autowired private BookingAttendanceOutcomeService outcomes;
    @Autowired private edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.AttendanceCorrectionService corrections;

    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @MockBean private AccountFacade accountFacade;
    @MockBean private TokenVerificationService tokenVerificationService;

    private WorkshopLocation location;
    private WorkshopSlot slot;
    private int day;

    @BeforeEach
    void setUp() throws Exception {
        try (var connection = jdbcTemplate.getDataSource().getConnection()) {
            assumeTrue(connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql"),
                    "TicketAttendanceFinalizationMySqlIT requires real MySQL evidence");
        }
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : List.of("workshop_ticket_attendance_finalization_outbox", "attendance_actions",
                "ticket_session_entitlements", "attendance_tickets", "capacity_holds", "operations_audit_events",
                "workshop_sessions", "workshop_slots", "workshop_locations")) {
            jdbcTemplate.execute("TRUNCATE TABLE " + table);
        }
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        location = locationRepository.save(WorkshopLocation.builder().code("LOC-T092-01")
                .displayName("Finalization Hub").addressSnapshot("3 Test St").active(true).build());
        slot = slotRepository.save(WorkshopSlot.builder().reference(UUID.randomUUID().toString()).location(location)
                .label("Morning").startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(12, 0)).defaultCapacity(10)
                .active(true).build());
        day = 0;
    }

    @Test
    @DisplayName("T092-1: no row before the window edge, one exactly at it, none more after it")
    void windowEdgeCreatesOneRowAndReplayCreatesNoMore() {
        Instant finalizedAt = Instant.parse("2026-11-04T12:00:00Z");
        Instant edge = finalizedAt.plus(24, ChronoUnit.HOURS);
        String ticket = ticketWithNoShow("BKG-EDGE", 1, finalizedAt);

        inTx(publisherAt(edge.minusSeconds(1))::createDuePublications);
        assertThat(outbox.existsByTicketReferenceAndEventType(ticket, TicketAttendanceFinalizationPublisher.EVENT_TYPE))
                .isFalse();

        inTx(publisherAt(edge)::createDuePublications);
        inTx(publisherAt(edge.plusSeconds(3600))::createDuePublications);
        inTx(publisherAt(edge.plusSeconds(7200))::createDuePublications);

        assertThat(rowsFor(ticket)).isEqualTo(1);
        assertThat(outbox.findAll()).filteredOn(row -> ticket.equals(row.getTicketReference()))
                .singleElement().extracting(TicketAttendanceFinalizationOutboxEvent::getWindowClosedAt).isEqualTo(edge);
    }

    @Test
    @DisplayName("T092-2: two tickets of one booking line close their own windows, one row each")
    void eachTicketOfALineHasItsOwnWindow() {
        Instant now = Instant.parse("2026-11-10T12:00:00Z");
        String old = ticketWithNoShow("BKG-LINE", 1, now.minus(30, ChronoUnit.HOURS));
        String recent = ticketWithNoShow("BKG-LINE", 2, now.minus(1, ChronoUnit.HOURS));

        inTx(publisherAt(now)::createDuePublications);

        assertThat(rowsFor(old)).isEqualTo(1);
        assertThat(rowsFor(recent)).isZero();
        inTx(publisherAt(now.plus(24, ChronoUnit.HOURS))::createDuePublications);
        assertThat(rowsFor(recent)).isEqualTo(1);
        assertThat(rowsFor(old)).isEqualTo(1);
    }

    @Test
    @DisplayName("T092-3: overlapping publish runs deliver a row once")
    void overlappingClaimsPublishOnce() throws Exception {
        String ticket = "TKT-OVERLAP-" + UUID.randomUUID();
        Instant at = Instant.parse("2026-11-05T12:00:00Z");
        outbox.saveAndFlush(TicketAttendanceFinalizationOutboxEvent.builder().eventId("ev-" + UUID.randomUUID())
                .ticketReference(ticket).eventType(TicketAttendanceFinalizationPublisher.EVENT_TYPE)
                .windowClosedAt(at).createdAt(at).build());
        AtomicInteger delivered = new AtomicInteger();
        ApplicationEventPublisher counting = event -> {
            if (event instanceof TicketAttendanceFinalizedEvent e && e.ticketReference().equals(ticket)) {
                delivered.incrementAndGet();
            }
        };
        TicketAttendanceFinalizationPublisher publisher = new TicketAttendanceFinalizationPublisher(
                entitlementRepository, outbox, outcomes, counting, clockProvider(at));

        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Void>> runs = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Callable<Void> run = () -> { go.await(); inTx(publisher::publishPending); return null; };
            runs.add(pool.submit(run));
        }
        go.countDown();
        for (Future<Void> run : runs) run.get();
        pool.shutdown();

        assertThat(delivered.get()).isEqualTo(1);
        assertThat(outbox.findTop50ByPublishedAtIsNullOrderByIdAsc()).noneMatch(row -> ticket.equals(row.getTicketReference()));
    }

    @Test
    @DisplayName("T092-4: a second event for the same ticket and type is refused by the database")
    void aDuplicateEventForTheSameTicketIsRefused() {
        String ticket = "TKT-DUP-" + UUID.randomUUID();
        Instant at = Instant.parse("2026-11-05T12:00:00Z");
        outbox.saveAndFlush(TicketAttendanceFinalizationOutboxEvent.builder().eventId("ev-" + UUID.randomUUID())
                .ticketReference(ticket).eventType(TicketAttendanceFinalizationPublisher.EVENT_TYPE)
                .windowClosedAt(at).createdAt(at).build());

        assertThatThrownBy(() -> outbox.saveAndFlush(TicketAttendanceFinalizationOutboxEvent.builder()
                .eventId("ev-" + UUID.randomUUID()).ticketReference(ticket)
                .eventType(TicketAttendanceFinalizationPublisher.EVENT_TYPE)
                .windowClosedAt(at).createdAt(at).build())).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(rowsFor(ticket)).isEqualTo(1);
    }

    @Test
    @DisplayName("T107: the correction window is per ticket; two tickets of one line close independently (MySQL)")
    void correctionWindowIsPerTicket() {
        Instant now = Instant.now();
        when(accountFacade.resolveAccount("t107-manager")).thenReturn(Optional.of(new AccountSummary(
                401L, ActorRole.MANAGER, ActorStatus.ACTIVE, "t107@mland.vn", now, now)));
        when(accountFacade.getAccountStatus(401L)).thenReturn(Optional.of(ActorStatus.ACTIVE));
        when(accountFacade.getLocationScope(401L)).thenReturn(List.of(location.getId()));
        when(tokenVerificationService.verify("test-uid:t107-manager")).thenReturn(
                new VerifiedIdentity("t107-manager", "t107@mland.vn", true, now));
        when(accountFacade.resolveActor("t107-manager", true)).thenReturn(Optional.of(new CurrentAccountActor(
                401L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of(location.getId()))));
        String recent = ticketWithNoShow("BKG-T107", 1, now.minus(1, ChronoUnit.HOURS));
        String old = ticketWithNoShow("BKG-T107", 2, now.minus(30, ChronoUnit.HOURS));
        String recentCode = ticketRepository.findByReference(recent).orElseThrow().getTicketCode();
        String oldCode = ticketRepository.findByReference(old).orElseThrow().getTicketCode();
        String recentSession = sessionReferenceOf(recent);
        String oldSession = sessionReferenceOf(old);
        String manager = "Bearer test-uid:t107-manager";

        assertThatThrownBy(() -> corrections.correctFinalizedAttendance(manager, oldSession, correction(oldCode)))
                .isInstanceOf(WorkshopLifecycleException.class);
        assertThat(entitlementRepository.findActiveByTicketReference(old).getFirst().getAttendanceState())
                .isEqualTo(AttendanceState.NO_SHOW);

        corrections.correctFinalizedAttendance(manager, recentSession, correction(recentCode));
        assertThat(entitlementRepository.findActiveByTicketReference(recent).getFirst().getAttendanceState())
                .isEqualTo(AttendanceState.CHECKED_IN);
    }

    private static AttendanceCorrectionRequest correction(String ticketCode) {
        return AttendanceCorrectionRequest.builder().ticketCode(ticketCode)
                .correctedAttendanceState(AttendanceState.CHECKED_IN).reasonCode("RECORDING_ERROR")
                .reasonNote("Window check per ticket").build();
    }

    private TicketAttendanceFinalizationPublisher publisherAt(Instant now) {
        return new TicketAttendanceFinalizationPublisher(entitlementRepository, outbox, outcomes, event -> { },
                clockProvider(now));
    }

    @SuppressWarnings("unchecked")
    private static org.springframework.beans.factory.ObjectProvider<Clock> clockProvider(Instant now) {
        var provider = org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(provider.getIfAvailable(org.mockito.ArgumentMatchers.any(java.util.function.Supplier.class)))
                .thenReturn(Clock.fixed(now, ZoneOffset.UTC));
        return provider;
    }

    private String sessionReferenceOf(String ticketReference) {
        return jdbcTemplate.queryForObject("SELECT s.reference FROM workshop_sessions s JOIN ticket_session_entitlements e "
                + "ON e.session_id = s.id JOIN attendance_tickets t ON t.id = e.ticket_id WHERE t.reference = ?",
                String.class, ticketReference);
    }

    private void inTx(Runnable work) {
        new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> work.run());
    }

    private int rowsFor(String ticketReference) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM workshop_ticket_attendance_finalization_outbox "
                + "WHERE ticket_reference = ?", Integer.class, ticketReference);
    }

    /** One ACTIVE ticket with a single NO_SHOW entitlement on its own finalized session. */
    private String ticketWithNoShow(String bookingReference, int sequence, Instant sessionFinalizedAt) {
        LocalDate date = LocalDate.of(2026, 11, 1).plusDays(day++);
        WorkshopSession session = sessionRepository.saveAndFlush(WorkshopSession.builder()
                .reference(UUID.randomUUID().toString()).location(location).slot(slot).workshopDate(date)
                .scheduledStartAt(sessionFinalizedAt.minus(3, ChronoUnit.HOURS))
                .scheduledEndAt(sessionFinalizedAt.minus(30, ChronoUnit.MINUTES))
                .checkInOpensAt(sessionFinalizedAt.minus(4, ChronoUnit.HOURS))
                .checkInOpeningSource(CheckInOpeningSource.DEFAULT)
                .attendanceFinalizedAt(sessionFinalizedAt).capacity(10).state(SessionState.ATTENDANCE_FINALIZED).build());
        AttendanceTicket ticket = ticketRepository.saveAndFlush(AttendanceTicket.builder()
                .reference(UUID.randomUUID().toString()).bookingReference(bookingReference).ticketSequence(sequence)
                .ticketCode("TK-" + UUID.randomUUID().toString().substring(0, 8))
                .qrReference("qr-" + UUID.randomUUID()).validityState(TicketValidityState.ACTIVE).build());
        entitlementRepository.saveAndFlush(TicketSessionEntitlement.builder().ticket(ticket).session(session)
                .attendanceState(AttendanceState.NO_SHOW).build());
        return ticket.getReference();
    }
}
