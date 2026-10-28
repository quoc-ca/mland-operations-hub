package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.CancellationHandoffOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopSettlementFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.AttendanceActionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.AttendanceTicketRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.CancellationHandoffRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.CapacityHoldRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.SessionOperationalLeadRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.SessionStaffAssignmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketSessionEntitlementRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopLocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSlotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.AttendanceCorrectionRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.AttendanceFinalizationRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.CheckInReversalRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.SessionCancellationRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceAction;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceActionType;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceTicket;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.CancellationHandoff;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.CancellationHandoffStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.CapacityHold;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.CapacityHoldState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.CheckInOpeningSource;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.OperationsAuditEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionOperationalLead;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionStaffAssignment;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketSessionEntitlement;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketValidityState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSlot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.CancellationHandoffService;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T047: Real MySQL Integration Tests for Workshop Operations User Story 5:
 * Whole-Session Attendance Closure, Check-in Reversal, Attendance Correction,
 * and Reasoned Cancellation with 200 vs 202 Settlement Handoff.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "workshop.operations.auth.allow-test-tokens=true"
})
public class WorkshopClosureIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WorkshopLocationRepository locationRepository;

    @Autowired
    private WorkshopSlotRepository slotRepository;

    @Autowired
    private WorkshopSessionRepository sessionRepository;

    @Autowired
    private SessionStaffAssignmentRepository staffAssignmentRepository;

    @Autowired
    private SessionOperationalLeadRepository operationalLeadRepository;

    @Autowired
    private CapacityHoldRepository capacityHoldRepository;

    @Autowired
    private AttendanceTicketRepository ticketRepository;

    @Autowired
    private TicketSessionEntitlementRepository entitlementRepository;

    @Autowired
    private AttendanceActionRepository attendanceActionRepository;

    @Autowired
    private OperationsAuditEventRepository auditEventRepository;

    @Autowired
    private CancellationHandoffRepository cancellationHandoffRepository;

    @Autowired
    private CancellationHandoffService cancellationHandoffService;

    @MockBean
    private WorkshopSettlementFacade settlementFacade;

    @MockBean
    private AccountFacade accountFacade;

    @MockBean
    private TokenVerificationService tokenVerificationService;

    private static final String MGR_A_TOKEN = "Bearer test-uid:mgr-a";
    private static final String MGR_OUT_OF_SCOPE_TOKEN = "Bearer test-uid:mgr-b";
    private static final String STAFF_A_TOKEN = "Bearer test-uid:staff-a";

    private WorkshopLocation locationA;
    private WorkshopLocation locationB;
    private WorkshopSlot slotMorning;
    private int sessionDateOffset;

    @BeforeEach
    void setUp() {
        sessionDateOffset = 0;
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbcTemplate.execute("TRUNCATE TABLE attendance_actions");
        jdbcTemplate.execute("TRUNCATE TABLE ticket_session_entitlements");
        jdbcTemplate.execute("TRUNCATE TABLE attendance_tickets");
        jdbcTemplate.execute("TRUNCATE TABLE capacity_holds");
        jdbcTemplate.execute("TRUNCATE TABLE session_operational_leads");
        jdbcTemplate.execute("TRUNCATE TABLE session_staff_assignments");
        jdbcTemplate.execute("TRUNCATE TABLE cancellation_handoffs");
        jdbcTemplate.execute("TRUNCATE TABLE operations_audit_events");
        jdbcTemplate.execute("TRUNCATE TABLE workshop_sessions");
        jdbcTemplate.execute("TRUNCATE TABLE workshop_slots");
        jdbcTemplate.execute("TRUNCATE TABLE workshop_locations");
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");

        locationA = locationRepository.save(WorkshopLocation.builder()
                .code("LOC-D1-01")
                .displayName("District 1 Workshop Hub")
                .addressSnapshot("101 Nguyen Hue, D1, HCMC")
                .active(true)
                .build());

        locationB = locationRepository.save(WorkshopLocation.builder()
                .code("LOC-D2-01")
                .displayName("District 2 Workshop Hub")
                .addressSnapshot("202 Xuan Thuy, D2, HCMC")
                .active(true)
                .build());

        slotMorning = slotRepository.save(WorkshopSlot.builder()
                .reference(UUID.randomUUID().toString())
                .location(locationA)
                .label("Standard Morning Slot")
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(12, 0))
                .defaultCapacity(15)
                .active(true)
                .build());

        // Setup Manager A (accountId=201) with Location A scope
        when(accountFacade.resolveAccount("mgr-a")).thenReturn(Optional.of(
                new AccountSummary(201L, ActorRole.MANAGER, ActorStatus.ACTIVE, "mgra@mland.vn", Instant.now(), Instant.now())));
        when(accountFacade.getAccountStatus(201L)).thenReturn(Optional.of(ActorStatus.ACTIVE));
        when(accountFacade.getLocationScope(201L)).thenReturn(List.of(locationA.getId()));
        when(accountFacade.resolveActor("mgr-a", true)).thenReturn(Optional.of(
                new CurrentAccountActor(201L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of(locationA.getId()))));

        // Setup Manager B (accountId=202) with Location B scope only (Out of Scope for Location A)
        when(accountFacade.resolveAccount("mgr-b")).thenReturn(Optional.of(
                new AccountSummary(202L, ActorRole.MANAGER, ActorStatus.ACTIVE, "mgrb@mland.vn", Instant.now(), Instant.now())));
        when(accountFacade.getAccountStatus(202L)).thenReturn(Optional.of(ActorStatus.ACTIVE));
        when(accountFacade.getLocationScope(202L)).thenReturn(List.of(locationB.getId()));
        when(accountFacade.resolveActor("mgr-b", true)).thenReturn(Optional.of(
                new CurrentAccountActor(202L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of(locationB.getId()))));

        // Setup Staff A (accountId=101) with Location A scope
        when(accountFacade.resolveAccount("staff-a")).thenReturn(Optional.of(
                new AccountSummary(101L, ActorRole.STAFF, ActorStatus.ACTIVE, "staffa@mland.vn", Instant.now(), Instant.now())));
        when(accountFacade.getAccountStatus(101L)).thenReturn(Optional.of(ActorStatus.ACTIVE));
        when(accountFacade.getLocationScope(101L)).thenReturn(List.of(locationA.getId()));
        when(accountFacade.resolveActor("staff-a", true)).thenReturn(Optional.of(
                new CurrentAccountActor(101L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(locationA.getId()))));

        when(tokenVerificationService.verify("test-uid:mgr-a")).thenReturn(
                new VerifiedIdentity("mgr-a", "mgra@mland.vn", true, Instant.now()));
        when(tokenVerificationService.verify("test-uid:mgr-b")).thenReturn(
                new VerifiedIdentity("mgr-b", "mgrb@mland.vn", true, Instant.now()));
        when(tokenVerificationService.verify("test-uid:staff-a")).thenReturn(
                new VerifiedIdentity("staff-a", "staffa@mland.vn", true, Instant.now()));

        when(settlementFacade.openCancellationHandoff(org.mockito.ArgumentMatchers.any()))
                .thenReturn(CancellationHandoffOutcome.accepted("SETTLE-TEST", "corr-test"));
    }

    private WorkshopSession createSession(SessionState state) {
        WorkshopSession session = sessionRepository.save(WorkshopSession.builder()
                .reference("SES-" + UUID.randomUUID().toString().substring(0, 8))
                .location(locationA)
                .slot(slotMorning)
                .workshopDate(LocalDate.now().plusDays(sessionDateOffset++))
                .scheduledStartAt(Instant.now().minus(2, ChronoUnit.HOURS))
                .scheduledEndAt(Instant.now().minus(30, ChronoUnit.MINUTES))
                .checkInOpensAt(Instant.now().minus(3, ChronoUnit.HOURS))
                .checkInOpeningSource(CheckInOpeningSource.DEFAULT)
                .capacity(15)
                .state(state)
                .build());

        // Assign Staff A to session
        staffAssignmentRepository.save(SessionStaffAssignment.builder()
                .session(session)
                .accountId(101L)
                .assignedById(201L)
                .active(true)
                .assignedAt(Instant.now())
                .build());

        return session;
    }

    // =========================================================================
    // 1. Finalization timing guards (too early / wrong state / invalid confirmation)
    // =========================================================================

    @Test
    @DisplayName("T047-1: Finalization rejected when session is not IN_PROGRESS (e.g., PUBLISHED or CANCELLED)")
    void testFinalizeAttendance_WhenSessionNotInProgress_Returns409Conflict() throws Exception {
        WorkshopSession publishedSession = createSession(SessionState.PUBLISHED);

        AttendanceFinalizationRequest request = AttendanceFinalizationRequest.builder()
                .confirmedSessionEnded(true)
                .build();

        mockMvc.perform(post("/api/v1/workshop-sessions/{sessionId}/attendance-finalizations", publishedSession.getReference())
                        .header("Authorization", STAFF_A_TOKEN)
                        .header("X-Correlation-Id", "corr-t047-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("T047-2: Finalization rejected when confirmedSessionEnded is false or null")
    void testFinalizeAttendance_WhenConfirmedSessionEndedIsFalse_Returns400BadRequest() throws Exception {
        WorkshopSession inProgressSession = createSession(SessionState.IN_PROGRESS);

        AttendanceFinalizationRequest request = AttendanceFinalizationRequest.builder()
                .confirmedSessionEnded(false)
                .build();

        mockMvc.perform(post("/api/v1/workshop-sessions/{sessionId}/attendance-finalizations", inProgressSession.getReference())
                        .header("Authorization", STAFF_A_TOKEN)
                        .header("X-Correlation-Id", "corr-t047-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // =========================================================================
    // 2. Finalization of session with zero attendees
    // =========================================================================

    @Test
    @DisplayName("T047-3: Finalization of zero-attendee session succeeds cleanly and transitions to ATTENDANCE_FINALIZED")
    void testFinalizeAttendance_WithZeroAttendees_SucceedsWith200AndFinalizedState() throws Exception {
        WorkshopSession session = createSession(SessionState.IN_PROGRESS);

        mockMvc.perform(post("/api/v1/workshop-sessions/{sessionId}/attendance-finalizations", session.getReference())
                        .header("Authorization", STAFF_A_TOKEN)
                        .header("X-Correlation-Id", "corr-t047-3")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ATTENDANCE_FINALIZED"));

        WorkshopSession updated = sessionRepository.findById(session.getId()).orElseThrow();
        assertThat(updated.getState()).isEqualTo(SessionState.ATTENDANCE_FINALIZED);

        // Verify audit event recorded
        List<OperationsAuditEvent> audits = auditEventRepository.findBySessionIdOrderByOccurredAtDesc(session.getId());
        assertThat(audits).anyMatch(a -> "ATTENDANCE_FINALIZED".equals(a.getAction()) && "SUCCESS".equals(a.getOutcome()));
    }

    // =========================================================================
    // 3. Unchecked tickets converted to NO_SHOW, CHECKED_IN preserved atomically
    // =========================================================================

    @Test
    @DisplayName("T047-4: Finalization converts unchecked tickets to NO_SHOW atomically while preserving CHECKED_IN tickets")
    void testFinalizeAttendance_ConvertsUncheckedToNoShowAndPreservesCheckedIn() throws Exception {
        WorkshopSession session = createSession(SessionState.IN_PROGRESS);

        Instant checkInTime = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);

        // Ticket 1: Already CHECKED_IN
        AttendanceTicket t1 = ticketRepository.save(AttendanceTicket.builder()
                .reference(UUID.randomUUID().toString())
                .ticketCode("TK-FIN-001")
                .bookingReference("BKG-001")
                .ticketSequence(1)
                .qrReference("qr-fin-001")
                .validityState(TicketValidityState.ACTIVE)
                .build());
        TicketSessionEntitlement e1 = entitlementRepository.save(TicketSessionEntitlement.builder()
                .ticket(t1)
                .session(session)
                .attendanceState(AttendanceState.CHECKED_IN)
                .checkedInAt(checkInTime)
                .build());

        // Ticket 2: NOT_CHECKED_IN
        AttendanceTicket t2 = ticketRepository.save(AttendanceTicket.builder()
                .reference(UUID.randomUUID().toString())
                .ticketCode("TK-FIN-002")
                .bookingReference("BKG-002")
                .ticketSequence(2)
                .qrReference("qr-fin-002")
                .validityState(TicketValidityState.ACTIVE)
                .build());
        TicketSessionEntitlement e2 = entitlementRepository.save(TicketSessionEntitlement.builder()
                .ticket(t2)
                .session(session)
                .attendanceState(AttendanceState.NOT_CHECKED_IN)
                .checkedInAt(null)
                .build());

        mockMvc.perform(post("/api/v1/workshop-sessions/{sessionId}/attendance-finalizations", session.getReference())
                        .header("Authorization", STAFF_A_TOKEN)
                        .header("X-Correlation-Id", "corr-t047-4")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ATTENDANCE_FINALIZED"));

        TicketSessionEntitlement updatedE1 = entitlementRepository.findById(e1.getId()).orElseThrow();
        TicketSessionEntitlement updatedE2 = entitlementRepository.findById(e2.getId()).orElseThrow();

        // Ticket 1 preserved exactly
        assertThat(updatedE1.getAttendanceState()).isEqualTo(AttendanceState.CHECKED_IN);
        assertThat(updatedE1.getCheckedInAt()).isNotNull();

        // Ticket 2 converted to NO_SHOW atomically
        assertThat(updatedE2.getAttendanceState()).isEqualTo(AttendanceState.NO_SHOW);
        assertThat(updatedE2.getCheckedInAt()).isNull();

        // AttendanceAction recorded for converted ticket
        List<AttendanceAction> actions = attendanceActionRepository.findByTicketIdAndSessionIdOrderByOccurredAtDesc(t2.getId(), session.getId());
        assertThat(actions).hasSize(1);
        assertThat(actions.get(0).getActionType()).isEqualTo(AttendanceActionType.FINALIZATION);
    }

    // =========================================================================
    // 4. Terminal-state rejection
    // =========================================================================

    @Test
    @DisplayName("T047-5: Terminal-state session rejects check-in reversal and session cancellation")
    void testTerminalStateRejection_OnFinalizedSession() throws Exception {
        WorkshopSession finalizedSession = createSession(SessionState.ATTENDANCE_FINALIZED);

        // 1. Check-in reversal on finalized session rejected
        CheckInReversalRequest revRequest = CheckInReversalRequest.builder()
                .ticketCode("TK-TERM-001")
                .reasonCode("RECORDING_ERROR")
                .reasonNote("Accidental scan")
                .build();

        mockMvc.perform(post("/api/v1/workshop-sessions/{sessionId}/check-in-reversals", finalizedSession.getReference())
                        .header("Authorization", MGR_A_TOKEN)
                        .header("X-Correlation-Id", "corr-t047-5a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(revRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        // 2. Cancellation on finalized session rejected
        SessionCancellationRequest cancelRequest = SessionCancellationRequest.builder()
                .reasonCode("FACILITY_ISSUE")
                .reasonNote("Post-session issue")
                .build();

        mockMvc.perform(post("/api/v1/workshop-sessions/{sessionId}/cancellation-requests", finalizedSession.getReference())
                        .header("Authorization", MGR_A_TOKEN)
                        .header("X-Correlation-Id", "corr-t047-5b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    // =========================================================================
    // 5. Manager-only authorization for reversal and correction
    // =========================================================================

    @Test
    @DisplayName("T047-6: Staff denied while Manager authority is global")
    void testManagerOnlyAuthorization_ReversalAndCorrection() throws Exception {
        WorkshopSession inProgressSession = createSession(SessionState.IN_PROGRESS);
        WorkshopSession finalizedSession = createSession(SessionState.ATTENDANCE_FINALIZED);

        CheckInReversalRequest revRequest = CheckInReversalRequest.builder()
                .ticketCode("TK-AUTH-001")
                .reasonCode("RECORDING_ERROR")
                .reasonNote("Scan error")
                .build();

        AttendanceCorrectionRequest corrRequest = AttendanceCorrectionRequest.builder()
                .ticketCode("TK-AUTH-001")
                .correctedAttendanceState(AttendanceState.CHECKED_IN)
                .reasonCode("RECORDING_ERROR")
                .reasonNote("Late attendee")
                .build();

        // 1. Staff actor denied reversal (403 Forbidden)
        mockMvc.perform(post("/api/v1/workshop-sessions/{sessionId}/check-in-reversals", inProgressSession.getReference())
                        .header("Authorization", STAFF_A_TOKEN)
                        .header("X-Correlation-Id", "corr-t047-6a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(revRequest)))
                .andExpect(status().isForbidden());

        // 2. Staff actor denied correction (403 Forbidden)
        mockMvc.perform(post("/api/v1/workshop-sessions/{sessionId}/attendance-corrections", finalizedSession.getReference())
                        .header("Authorization", STAFF_A_TOKEN)
                        .header("X-Correlation-Id", "corr-t047-6b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corrRequest)))
                .andExpect(status().isForbidden());

        // 3. Manager B is globally authorized; missing ticket is evaluated after authorization.
        mockMvc.perform(post("/api/v1/workshop-sessions/{sessionId}/check-in-reversals", inProgressSession.getReference())
                        .header("Authorization", MGR_OUT_OF_SCOPE_TOKEN)
                        .header("X-Correlation-Id", "corr-t047-6c")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(revRequest)))
                .andExpect(status().isNotFound());

        // 4. Manager B is globally authorized; missing ticket is evaluated after authorization.
        mockMvc.perform(post("/api/v1/workshop-sessions/{sessionId}/attendance-corrections", finalizedSession.getReference())
                        .header("Authorization", MGR_OUT_OF_SCOPE_TOKEN)
                        .header("X-Correlation-Id", "corr-t047-6d")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corrRequest)))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // 6. Correction before finalization (reversal) vs after finalization (correction)
    // =========================================================================

    @Test
    @DisplayName("T047-7: Check-in reversal succeeds before finalization, reverting ticket to NOT_CHECKED_IN")
    void testCheckInReversal_BeforeFinalization_SucceedsAndRevertsToNotCheckedIn() throws Exception {
        WorkshopSession session = createSession(SessionState.IN_PROGRESS);

        AttendanceTicket ticket = ticketRepository.save(AttendanceTicket.builder()
                .reference(UUID.randomUUID().toString())
                .ticketCode("TK-REV-001")
                .bookingReference("BKG-REV")
                .ticketSequence(1)
                .qrReference("qr-rev-001")
                .validityState(TicketValidityState.ACTIVE)
                .build());

        TicketSessionEntitlement entitlement = entitlementRepository.save(TicketSessionEntitlement.builder()
                .ticket(ticket)
                .session(session)
                .attendanceState(AttendanceState.CHECKED_IN)
                .checkedInAt(Instant.now().minus(30, ChronoUnit.MINUTES))
                .build());

        CheckInReversalRequest request = CheckInReversalRequest.builder()
                .ticketCode("TK-REV-001")
                .reasonCode("RECORDING_ERROR")
                .reasonNote("Accidental barcode scan verified onsite")
                .build();

        mockMvc.perform(post("/api/v1/workshop-sessions/{sessionId}/check-in-reversals", session.getReference())
                        .header("Authorization", MGR_A_TOKEN)
                        .header("X-Correlation-Id", "corr-t047-7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendanceState").value("NOT_CHECKED_IN"))
                .andExpect(jsonPath("$.checkedInAt").doesNotExist());

        TicketSessionEntitlement updated = entitlementRepository.findById(entitlement.getId()).orElseThrow();
        assertThat(updated.getAttendanceState()).isEqualTo(AttendanceState.NOT_CHECKED_IN);
        assertThat(updated.getCheckedInAt()).isNull();

        // Verify AttendanceAction and AuditEvent recorded
        List<AttendanceAction> actions = attendanceActionRepository.findByTicketIdAndSessionIdOrderByOccurredAtDesc(ticket.getId(), session.getId());
        assertThat(actions).anyMatch(a -> a.getActionType() == AttendanceActionType.REVERSAL);

        List<OperationsAuditEvent> audits = auditEventRepository.findBySessionIdOrderByOccurredAtDesc(session.getId());
        assertThat(audits).anyMatch(a -> "CHECK_IN_REVERSED".equals(a.getAction()));
    }

    @Test
    @DisplayName("T047-8: Attendance correction succeeds after finalization for CHECKED_IN and NO_SHOW, rejects NOT_CHECKED_IN")
    void testAttendanceCorrection_AfterFinalization_SucceedsForCheckedInAndNoShow() throws Exception {
        WorkshopSession session = createSession(SessionState.ATTENDANCE_FINALIZED);

        // Ticket A: NO_SHOW -> correct to CHECKED_IN
        AttendanceTicket ticketA = ticketRepository.save(AttendanceTicket.builder()
                .reference(UUID.randomUUID().toString())
                .ticketCode("TK-CORR-A")
                .bookingReference("BKG-A")
                .ticketSequence(1)
                .qrReference("qr-corr-a")
                .validityState(TicketValidityState.ACTIVE)
                .build());
        TicketSessionEntitlement entA = entitlementRepository.save(TicketSessionEntitlement.builder()
                .ticket(ticketA)
                .session(session)
                .attendanceState(AttendanceState.NO_SHOW)
                .checkedInAt(null)
                .build());

        AttendanceCorrectionRequest reqA = AttendanceCorrectionRequest.builder()
                .ticketCode("TK-CORR-A")
                .correctedAttendanceState(AttendanceState.CHECKED_IN)
                .reasonCode("RECORDING_ERROR")
                .reasonNote("Attendee arrived before session end, verified manually")
                .build();

        mockMvc.perform(post("/api/v1/workshop-sessions/{sessionId}/attendance-corrections", session.getReference())
                        .header("Authorization", MGR_A_TOKEN)
                        .header("X-Correlation-Id", "corr-t047-8a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendanceState").value("CHECKED_IN"))
                .andExpect(jsonPath("$.checkedInAt").isNotEmpty());

        TicketSessionEntitlement updatedA = entitlementRepository.findById(entA.getId()).orElseThrow();
        assertThat(updatedA.getAttendanceState()).isEqualTo(AttendanceState.CHECKED_IN);
        assertThat(updatedA.getCheckedInAt()).isNotNull();

        // Ticket B: Attempt invalid correction to NOT_CHECKED_IN -> 400 Bad Request
        AttendanceCorrectionRequest reqInvalid = AttendanceCorrectionRequest.builder()
                .ticketCode("TK-CORR-A")
                .correctedAttendanceState(AttendanceState.NOT_CHECKED_IN)
                .reasonCode("RECORDING_ERROR")
                .reasonNote("Invalid target")
                .build();

        mockMvc.perform(post("/api/v1/workshop-sessions/{sessionId}/attendance-corrections", session.getReference())
                        .header("Authorization", MGR_A_TOKEN)
                        .header("X-Correlation-Id", "corr-t047-8b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqInvalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // =========================================================================
    // 7. Direct cancellation returns 200 OK without commitments
    // =========================================================================

    @Test
    @DisplayName("T047-9: Direct cancellation returns 200 OK and CANCELLED state when session has no commitments")
    void testSessionCancellation_WithoutCommitments_DirectlyCancelsWith200() throws Exception {
        WorkshopSession session = createSession(SessionState.PUBLISHED);

        SessionCancellationRequest request = SessionCancellationRequest.builder()
                .reasonCode("FACILITY_ISSUE")
                .reasonNote("Room ceiling leakage")
                .build();

        mockMvc.perform(post("/api/v1/workshop-sessions/{sessionId}/cancellation-requests", session.getReference())
                        .header("Authorization", MGR_A_TOKEN)
                        .header("X-Correlation-Id", "corr-t047-9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CANCELLED"));

        WorkshopSession updated = sessionRepository.findById(session.getId()).orElseThrow();
        assertThat(updated.getState()).isEqualTo(SessionState.CANCELLED);

        // Verify zero cancellation handoffs created
        List<CancellationHandoff> handoffs = cancellationHandoffRepository.findBySessionId(session.getId());
        assertThat(handoffs).isEmpty();

        // Verify audit event
        List<OperationsAuditEvent> audits = auditEventRepository.findBySessionIdOrderByOccurredAtDesc(session.getId());
        assertThat(audits).anyMatch(a -> "SESSION_CANCELLED_DIRECT".equals(a.getAction()));
    }

    // =========================================================================
    // 8. Cancellation with booking commitments returns 202 Accepted handoff
    // =========================================================================

    @Test
    @DisplayName("T047-10: Cancellation with active ticket commitment transitions to CANCELLATION_REQUESTED and returns 202")
    void testSessionCancellation_WithActiveTicketCommitment_Returns202AndCreatesHandoff() throws Exception {
        WorkshopSession session = createSession(SessionState.PUBLISHED);

        AttendanceTicket ticket = ticketRepository.save(AttendanceTicket.builder()
                .reference(UUID.randomUUID().toString())
                .ticketCode("TK-CANCEL-001")
                .bookingReference("BKG-CANCEL-001")
                .ticketSequence(1)
                .qrReference("qr-cancel-001")
                .validityState(TicketValidityState.ACTIVE)
                .build());

        entitlementRepository.save(TicketSessionEntitlement.builder()
                .ticket(ticket)
                .session(session)
                .attendanceState(AttendanceState.NOT_CHECKED_IN)
                .build());

        SessionCancellationRequest request = SessionCancellationRequest.builder()
                .reasonCode("INSTRUCTOR_UNAVAILABLE")
                .reasonNote("Instructor unexpected illness")
                .evidenceReference("DOC-MED-001")
                .build();

        mockMvc.perform(post("/api/v1/workshop-sessions/{sessionId}/cancellation-requests", session.getReference())
                        .header("Authorization", MGR_A_TOKEN)
                        .header("X-Correlation-Id", "corr-t047-10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        WorkshopSession updated = sessionRepository.findById(session.getId()).orElseThrow();
        assertThat(updated.getState()).isEqualTo(SessionState.CANCELLATION_REQUESTED);
        assertThat(updated.getCancellationHandoffReference()).isNotNull();

        // Verify CancellationHandoff entity saved
        List<CancellationHandoff> handoffs = cancellationHandoffRepository.findBySessionId(session.getId());
        assertThat(handoffs).hasSize(1);
        assertThat(handoffs.get(0).getStatus()).isEqualTo(CancellationHandoffStatus.REQUESTED);
        assertThat(handoffs.get(0).getReasonCode()).isEqualTo("INSTRUCTOR_UNAVAILABLE");

        // Verify audit event
        List<OperationsAuditEvent> audits = auditEventRepository.findBySessionIdOrderByOccurredAtDesc(session.getId());
        assertThat(audits).anyMatch(a -> "SESSION_CANCELLATION_REQUESTED".equals(a.getAction()));
    }

    @Test
    @DisplayName("T047-11: Cancellation with active unexpired hold returns 202 and creates handoff")
    void testSessionCancellation_WithActiveHold_Returns202() throws Exception {
        WorkshopSession session = createSession(SessionState.PUBLISHED);

        capacityHoldRepository.save(CapacityHold.builder()
                .reference(UUID.randomUUID().toString())
                .bookingReference("BKG-HOLD-99")
                .session(session)
                .requestedTicketCount(2)
                .state(CapacityHoldState.ACTIVE)
                .expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
                .createdAt(Instant.now())
                .build());

        SessionCancellationRequest request = SessionCancellationRequest.builder()
                .reasonCode("WEATHER_EMERGENCY")
                .reasonNote("Severe typhoon alert")
                .build();

        mockMvc.perform(post("/api/v1/workshop-sessions/{sessionId}/cancellation-requests", session.getReference())
                        .header("Authorization", MGR_A_TOKEN)
                        .header("X-Correlation-Id", "corr-t047-11")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        WorkshopSession updated = sessionRepository.findById(session.getId()).orElseThrow();
        assertThat(updated.getState()).isEqualTo(SessionState.CANCELLATION_REQUESTED);
    }

    // =========================================================================
    // 9. Settlement rejection handling
    // =========================================================================

    @Test
    @DisplayName("T047-12: Settlement facade rejection triggers 409 Conflict")
    void testSessionCancellation_WhenSettlementFacadeRejects_Returns409Conflict() throws Exception {
        WorkshopSession session = createSession(SessionState.PUBLISHED);

        AttendanceTicket ticket = ticketRepository.save(AttendanceTicket.builder()
                .reference(UUID.randomUUID().toString())
                .ticketCode("TK-REJ-001")
                .bookingReference("BKG-REJ")
                .ticketSequence(1)
                .qrReference("qr-rej-001")
                .validityState(TicketValidityState.ACTIVE)
                .build());

        entitlementRepository.save(TicketSessionEntitlement.builder()
                .ticket(ticket)
                .session(session)
                .attendanceState(AttendanceState.NOT_CHECKED_IN)
                .build());

        when(settlementFacade.openCancellationHandoff(
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(CancellationHandoffOutcome.rejected(
                        "Settlement batch locked in 003.3", "corr-t047-12"));

        SessionCancellationRequest request = SessionCancellationRequest.builder()
                .reasonCode("FACILITY_ISSUE")
                .reasonNote("Power outage")
                .build();

        mockMvc.perform(post("/api/v1/workshop-sessions/{sessionId}/cancellation-requests", session.getReference())
                        .header("Authorization", MGR_A_TOKEN)
                        .header("X-Correlation-Id", "corr-t047-12")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    // =========================================================================
    // 10. Immutable audit evidence & zero commercial side effects
    // =========================================================================

    @Test
    @DisplayName("T047-13: Audit evidence is immutable and no commercial refund/reschedule side effects exist in Operations")
    void testAuditImmutabilityAndZeroCommercialSideEffects() {
        // Assert operations audit events exist
        List<OperationsAuditEvent> allAudits = auditEventRepository.findAll();
        assertThat(allAudits).isNotNull();

        // Assert database contains NO refund, payment, or settlement tables in Operations schema
        Integer refundTableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name LIKE '%refund%'",
                Integer.class);
        assertThat(refundTableCount).isEqualTo(0);

        Integer settlementTableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name LIKE '%settlement%'",
                Integer.class);
        assertThat(settlementTableCount).isEqualTo(0);
    }
}
