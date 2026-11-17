package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.AttendanceActionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.AttendanceTicketRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.CancellationHandoffRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.SessionStaffAssignmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketSessionEntitlementRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopLocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSlotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.AttendanceCorrectionRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.AttendanceFinalizationRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.CheckInReversalRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.SessionCancellationRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceActionType;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceTicket;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.CheckInOpeningSource;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionStaffAssignment;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketSessionEntitlement;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketValidityState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSlot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.CancellationHandoffOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopSettlementFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.when;

/**
 * 003.2 T140 on MySQL (FR-010kh): check-in reversal, attendance finalization and cancellation request sent twice
 * (second {@code 409 INVALID_STATE}, no second success audit, no second handoff, no denied-attempt audit row), a
 * correction with the value the ticket already has ({@code 200}, unchanged, one audit event) and a parallel pair
 * (one wins, the other {@code 409 INVALID_STATE} or {@code OPTIMISTIC_LOCK_CONFLICT}). The resend repeat is in
 * {@code TicketRecoveryIT}. These routes take no Idempotency-Key and add no new code.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("workshopoperations-mysql-test")
@TestPropertySource(properties = "workshop.operations.auth.allow-test-tokens=true")
class RepeatedOperationalCommandsMySqlIT {

    private static final String MGR_TOKEN = "Bearer test-uid:rep-mgr";
    private static final String STAFF_TOKEN = "Bearer test-uid:rep-staff";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private WorkshopLocationRepository locations;
    @Autowired private WorkshopSlotRepository slots;
    @Autowired private WorkshopSessionRepository sessions;
    @Autowired private SessionStaffAssignmentRepository staffAssignments;
    @Autowired private AttendanceTicketRepository tickets;
    @Autowired private TicketSessionEntitlementRepository entitlements;
    @Autowired private AttendanceActionRepository actions;
    @Autowired private OperationsAuditEventRepository audits;
    @Autowired private CancellationHandoffRepository handoffs;

    @MockBean private WorkshopSettlementFacade settlementFacade;
    @MockBean private AccountFacade accountFacade;
    @MockBean private TokenVerificationService tokenVerificationService;

    private WorkshopLocation location;
    private WorkshopSlot slot;
    private int dateOffset;

    @BeforeEach
    void setUp() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            assumeTrue(connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql"),
                    "RepeatedOperationalCommandsMySqlIT requires real MySQL evidence");
        }
        dateOffset = 0;
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : List.of("attendance_actions", "ticket_session_entitlements", "attendance_tickets",
                "capacity_holds", "session_staff_assignments", "cancellation_handoffs",
                "workshop_cancellation_outbox", "operations_audit_events", "workshop_sessions", "workshop_slots",
                "workshop_locations")) {
            jdbc.execute("TRUNCATE TABLE " + table);
        }
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
        location = locations.save(WorkshopLocation.builder().code("LOC-REP").displayName("Repeat Hub")
                .addressSnapshot("1 Repeat St").active(true).build());
        slot = slots.save(WorkshopSlot.builder().reference(UUID.randomUUID().toString()).location(location)
                .label("Repeat Slot").startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(12, 0)).active(true).build());

        when(accountFacade.resolveAccount("rep-mgr")).thenReturn(Optional.of(
                new AccountSummary(901L, ActorRole.MANAGER, ActorStatus.ACTIVE, "m@mland.vn", Instant.now(), Instant.now())));
        when(accountFacade.getAccountStatus(901L)).thenReturn(Optional.of(ActorStatus.ACTIVE));
        when(accountFacade.getLocationScope(901L)).thenReturn(List.of(location.getId()));
        when(accountFacade.resolveActor("rep-mgr", true)).thenReturn(Optional.of(
                new CurrentAccountActor(901L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of(location.getId()))));
        when(accountFacade.resolveAccount("rep-staff")).thenReturn(Optional.of(
                new AccountSummary(902L, ActorRole.STAFF, ActorStatus.ACTIVE, "s@mland.vn", Instant.now(), Instant.now())));
        when(accountFacade.getAccountStatus(902L)).thenReturn(Optional.of(ActorStatus.ACTIVE));
        when(accountFacade.getLocationScope(902L)).thenReturn(List.of(location.getId()));
        when(accountFacade.resolveActor("rep-staff", true)).thenReturn(Optional.of(
                new CurrentAccountActor(902L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(location.getId()))));
        when(tokenVerificationService.verify("test-uid:rep-mgr")).thenReturn(
                new VerifiedIdentity("rep-mgr", "m@mland.vn", true, Instant.now()));
        when(tokenVerificationService.verify("test-uid:rep-staff")).thenReturn(
                new VerifiedIdentity("rep-staff", "s@mland.vn", true, Instant.now()));
        when(settlementFacade.openCancellationHandoff(org.mockito.ArgumentMatchers.any()))
                .thenReturn(CancellationHandoffOutcome.accepted("SETTLE-REP", "corr-rep"));
    }

    @Test
    @DisplayName("T140-1: a repeated check-in reversal is 409 INVALID_STATE with no second action or audit")
    void repeatedReversal() throws Exception {
        WorkshopSession session = session(SessionState.IN_PROGRESS);
        AttendanceTicket ticket = ticket("TK-REP-REV");
        entitlement(ticket, session, AttendanceState.CHECKED_IN);

        assertThat(reversal(session, "TK-REP-REV", "rev1").getResponse().getStatus()).isEqualTo(200);
        long auditsAfterFirst = auditCount(session);
        long actionsAfterFirst = reversalActions(ticket, session);

        MvcResult second = reversal(session, "TK-REP-REV", "rev2");
        assertInvalidState(second);
        assertThat(auditCount(session)).isEqualTo(auditsAfterFirst);
        assertThat(reversalActions(ticket, session)).isEqualTo(actionsAfterFirst).isEqualTo(1);
        assertNoDeniedAudit(session);
    }

    @Test
    @DisplayName("T140-2: a repeated attendance finalization is 409 INVALID_STATE with no second success audit")
    void repeatedFinalization() throws Exception {
        WorkshopSession session = session(SessionState.IN_PROGRESS);

        assertThat(finalization(session, "fin1").getResponse().getStatus()).isEqualTo(200);
        long auditsAfterFirst = auditCount(session);

        assertInvalidState(finalization(session, "fin2"));
        assertThat(auditCount(session)).isEqualTo(auditsAfterFirst);
        assertThat(audits.findBySessionIdOrderByOccurredAtDesc(session.getId()).stream()
                .filter(a -> "ATTENDANCE_FINALIZED".equals(a.getAction()) && "SUCCESS".equals(a.getOutcome())).count())
                .isEqualTo(1);
        assertNoDeniedAudit(session);
    }

    @Test
    @DisplayName("T140-3: a repeated cancellation request is 409 INVALID_STATE with no second handoff")
    void repeatedCancellationRequest() throws Exception {
        WorkshopSession session = session(SessionState.PUBLISHED);
        entitlement(ticket("TK-REP-CAN"), session, AttendanceState.NOT_CHECKED_IN);

        assertThat(cancellation(session, "can1").getResponse().getStatus()).isEqualTo(202);
        long auditsAfterFirst = auditCount(session);
        long handoffsAfterFirst = handoffs.findBySessionId(session.getId()).size();

        assertInvalidState(cancellation(session, "can2"));
        assertThat(handoffs.findBySessionId(session.getId())).hasSize((int) handoffsAfterFirst).hasSize(1);
        assertThat(auditCount(session)).isEqualTo(auditsAfterFirst);
        assertNoDeniedAudit(session);
    }

    @Test
    @DisplayName("T140-4: a correction with the value the ticket already has is 200, unchanged, with one audit event")
    void correctionWithSameValue() throws Exception {
        WorkshopSession session = session(SessionState.ATTENDANCE_FINALIZED);
        AttendanceTicket ticket = ticket("TK-REP-COR");
        TicketSessionEntitlement ent = entitlement(ticket, session, AttendanceState.NO_SHOW);

        assertThat(correction(session, "TK-REP-COR", AttendanceState.CHECKED_IN, "cor1").getResponse().getStatus())
                .isEqualTo(200);
        TicketSessionEntitlement afterFirst = entitlements.findById(ent.getId()).orElseThrow();
        long auditsAfterFirst = auditCount(session);

        assertThat(correction(session, "TK-REP-COR", AttendanceState.CHECKED_IN, "cor2").getResponse().getStatus())
                .isEqualTo(200);
        TicketSessionEntitlement afterSecond = entitlements.findById(ent.getId()).orElseThrow();
        assertThat(afterSecond.getAttendanceState()).isEqualTo(AttendanceState.CHECKED_IN);
        assertThat(afterSecond.getCheckedInAt()).isEqualTo(afterFirst.getCheckedInAt());
        assertThat(auditCount(session)).isEqualTo(auditsAfterFirst);
        assertThat(auditsAfterFirst).isEqualTo(1);
        assertNoDeniedAudit(session);
    }

    @Test
    @DisplayName("T140-5: two parallel reversals - one wins, the other is a state or optimistic-lock conflict")
    void parallelReversals() throws Exception {
        WorkshopSession session = session(SessionState.IN_PROGRESS);
        AttendanceTicket ticket = ticket("TK-REP-PAR");
        entitlement(ticket, session, AttendanceState.CHECKED_IN);

        List<MvcResult> results = inParallel(
                () -> reversal(session, "TK-REP-PAR", "par-a"), () -> reversal(session, "TK-REP-PAR", "par-b"));

        assertOneWinsOtherConflicts(results);
        assertThat(reversalActions(ticket, session)).isEqualTo(1);
    }

    @Test
    @DisplayName("T140-6: two parallel finalizations - one wins, the other is a state or optimistic-lock conflict")
    void parallelFinalizations() throws Exception {
        WorkshopSession session = session(SessionState.IN_PROGRESS);

        List<MvcResult> results = inParallel(
                () -> finalization(session, "pf-a"), () -> finalization(session, "pf-b"));

        assertOneWinsOtherConflicts(results);
        assertThat(audits.findBySessionIdOrderByOccurredAtDesc(session.getId()).stream()
                .filter(a -> "ATTENDANCE_FINALIZED".equals(a.getAction()) && "SUCCESS".equals(a.getOutcome())).count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("T140-7: two parallel cancellation requests - one wins, no second handoff")
    void parallelCancellationRequests() throws Exception {
        WorkshopSession session = session(SessionState.PUBLISHED);
        entitlement(ticket("TK-REP-PCAN"), session, AttendanceState.NOT_CHECKED_IN);

        List<MvcResult> results = inParallel(
                () -> cancellation(session, "pc-a"), () -> cancellation(session, "pc-b"));

        List<Integer> statuses = results.stream().map(r -> r.getResponse().getStatus()).sorted().toList();
        assertThat(statuses).containsExactly(202, 409);
        assertThat(handoffs.findBySessionId(session.getId())).hasSize(1);
    }

    // --- helpers ---------------------------------------------------------------------------------------------

    private void assertInvalidState(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).path("code").asText())
                .isEqualTo("INVALID_STATE");
    }

    private void assertOneWinsOtherConflicts(List<MvcResult> results) throws Exception {
        List<Integer> statuses = results.stream().map(r -> r.getResponse().getStatus()).sorted().toList();
        assertThat(statuses).containsExactly(200, 409);
        for (MvcResult r : results) {
            if (r.getResponse().getStatus() == 409) {
                assertThat(objectMapper.readTree(r.getResponse().getContentAsString()).path("code").asText())
                        .isIn("INVALID_STATE", "OPTIMISTIC_LOCK_CONFLICT");
            }
        }
    }

    private List<MvcResult> inParallel(Callable<MvcResult> a, Callable<MvcResult> b) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();
        for (Callable<MvcResult> call : List.of(a, b)) {
            futures.add(executor.submit(() -> {
                start.await();
                return call.call();
            }));
        }
        start.countDown();
        List<MvcResult> results = new ArrayList<>();
        for (Future<MvcResult> f : futures) {
            results.add(f.get(30, TimeUnit.SECONDS));
        }
        executor.shutdown();
        return results;
    }

    private long auditCount(WorkshopSession session) {
        return audits.findBySessionIdOrderByOccurredAtDesc(session.getId()).size();
    }

    private long reversalActions(AttendanceTicket ticket, WorkshopSession session) {
        return actions.findByTicketIdAndSessionIdOrderByOccurredAtDesc(ticket.getId(), session.getId()).stream()
                .filter(a -> a.getActionType() == AttendanceActionType.REVERSAL).count();
    }

    private void assertNoDeniedAudit(WorkshopSession session) {
        assertThat(audits.findBySessionIdOrderByOccurredAtDesc(session.getId()))
                .noneMatch(a -> "DENIED".equals(a.getOutcome()));
    }

    private MvcResult reversal(WorkshopSession session, String ticketCode, String corr) throws Exception {
        CheckInReversalRequest body = CheckInReversalRequest.builder().ticketCode(ticketCode)
                .reasonCode("RECORDING_ERROR").reasonNote("Accidental scan verified onsite").build();
        return post(session, "check-in-reversals", MGR_TOKEN, corr, objectMapper.writeValueAsString(body));
    }

    private MvcResult correction(WorkshopSession session, String ticketCode, AttendanceState state, String corr)
            throws Exception {
        AttendanceCorrectionRequest body = AttendanceCorrectionRequest.builder().ticketCode(ticketCode)
                .correctedAttendanceState(state).reasonCode("RECORDING_ERROR")
                .reasonNote("Attendee arrived before the session ended").build();
        return post(session, "attendance-corrections", MGR_TOKEN, corr, objectMapper.writeValueAsString(body));
    }

    private MvcResult finalization(WorkshopSession session, String corr) throws Exception {
        AttendanceFinalizationRequest body = AttendanceFinalizationRequest.builder().confirmedSessionEnded(true).build();
        return post(session, "attendance-finalizations", STAFF_TOKEN, corr, objectMapper.writeValueAsString(body));
    }

    private MvcResult cancellation(WorkshopSession session, String corr) throws Exception {
        SessionCancellationRequest body = SessionCancellationRequest.builder().reasonCode("FACILITY_ISSUE")
                .reasonNote("Room unavailable").build();
        return post(session, "cancellation-requests", MGR_TOKEN, corr, objectMapper.writeValueAsString(body));
    }

    private MvcResult post(WorkshopSession session, String route, String token, String corr, String json)
            throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/workshop-sessions/{sessionId}/" + route, session.getReference())
                        .header("Authorization", token).header("X-Correlation-Id", corr)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andReturn();
    }

    private WorkshopSession session(SessionState state) {
        Instant now = Instant.now();
        WorkshopSession session = sessions.save(WorkshopSession.builder()
                .reference("SES-" + UUID.randomUUID().toString().substring(0, 8)).location(location).slot(slot)
                .workshopDate(LocalDate.now().plusDays(dateOffset++))
                .scheduledStartAt(now.minus(2, ChronoUnit.HOURS)).scheduledEndAt(now.minus(30, ChronoUnit.MINUTES))
                .checkInOpensAt(now.minus(3, ChronoUnit.HOURS)).checkInOpeningSource(CheckInOpeningSource.DEFAULT)
                .attendanceFinalizedAt(state == SessionState.ATTENDANCE_FINALIZED
                        ? now.minus(15, ChronoUnit.MINUTES) : null)
                .capacity(15).state(state).build());
        staffAssignments.save(SessionStaffAssignment.builder().session(session).accountId(902L)
                .assignedById(901L).active(true).assignedAt(Instant.now()).build());
        return session;
    }

    private AttendanceTicket ticket(String code) {
        return tickets.save(AttendanceTicket.builder().reference(UUID.randomUUID().toString())
                .ticketCode(code).bookingReference("BKG-" + code).ticketSequence(1).qrReference("qr-" + code)
                .validityState(TicketValidityState.ACTIVE).build());
    }

    private TicketSessionEntitlement entitlement(AttendanceTicket ticket, WorkshopSession session, AttendanceState s) {
        return entitlements.save(TicketSessionEntitlement.builder().ticket(ticket).session(session)
                .attendanceState(s).checkedInAt(s == AttendanceState.CHECKED_IN
                        ? Instant.now().minus(30, ChronoUnit.MINUTES) : null).build());
    }
}
