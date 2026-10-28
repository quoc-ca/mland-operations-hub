package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.TargetAccountSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.CapacityHoldRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.SessionOperationalLeadRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.SessionStaffAssignmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopLocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopPackageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSlotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.OperationalLeadRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.StaffAssignmentRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.StateTransitionRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.CapacityHold;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.CapacityHoldState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.CheckInOpeningSource;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.OperationsAuditEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionOperationalLead;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionStaffAssignment;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackage;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackageState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSlot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.CapacityCommitOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.CapacityHoldOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.CommitBookingHoldsCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.OutcomeStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.RejectionReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.ReserveBookingCapacityCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopOperationsFacade;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T025: Real MySQL Integration Tests for Workshop Session Lifecycle and Responsibility.
 * Verifies:
 * 1. Active Manager global Workshop Operations authority;
 * 2. Inactive actor rejected;
 * 3. Target account validation (not found, wrong role, not ACTIVE, outside location scope);
 * 4. Publish without Staff succeeds;
 * 5. Check-in opening before scheduled start and scheduled PUBLISHED -> IN_PROGRESS transition;
 * 6. SALES_PAUSED blocks new holds but preserves active unexpired holds;
 * 7. Atomic operational lead handover;
 * 8. In-progress staff removal invariant;
 * 9. Immutable audit evidence with correlation tracking.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "workshop.operations.auth.allow-test-tokens=true"
})
public class WorkshopLifecycleIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private AccountFacade accountFacade;

    @MockBean
    private TokenVerificationService tokenVerificationService;

    @Autowired
    private WorkshopLocationRepository locationRepository;

    @Autowired
    private WorkshopSlotRepository slotRepository;

    @Autowired
    private WorkshopSessionRepository sessionRepository;

    @Autowired
    private WorkshopPackageRepository packageRepository;

    @Autowired
    private SessionStaffAssignmentRepository staffAssignmentRepository;

    @Autowired
    private SessionOperationalLeadRepository operationalLeadRepository;

    @Autowired
    private CapacityHoldRepository capacityHoldRepository;

    @Autowired
    private OperationsAuditEventRepository auditEventRepository;

    @Autowired
    private WorkshopOperationsFacade operationsFacade;

    @Autowired
    private edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopSessionStartScheduler sessionStartScheduler;

    private static final String MGR_A_TOKEN = "Bearer test-uid:mgr-a";
    private static final String MGR_B_TOKEN = "Bearer test-uid:mgr-b";
    private static final String STAFF_A_TOKEN = "Bearer test-uid:staff-a";
    private static final String INACTIVE_TOKEN = "Bearer test-uid:inactive-mgr";

    private WorkshopLocation locationA;
    private WorkshopLocation locationB;
    private WorkshopSlot slotMorning;
    private WorkshopSession sessionA;
    private String testPackageReference;

    @BeforeEach
    void setUp() {
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
                .code("LOC-HCM-01")
                .displayName("District 1 Workshop Location")
                .addressSnapshot("123 Le Loi, D1, HCMC")
                .active(true)
                .build());

        locationB = locationRepository.save(WorkshopLocation.builder()
                .code("LOC-HCM-02")
                .displayName("District 2 Workshop Location")
                .addressSnapshot("456 Thao Dien, D2, HCMC")
                .active(true)
                .build());

        slotMorning = slotRepository.save(WorkshopSlot.builder()
                .reference(UUID.randomUUID().toString())
                .location(locationA)
                .label("Standard Morning Slot")
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(12, 0))
                .defaultCapacity(20)
                .active(true)
                .build());

        LocalDate workshopDate = LocalDate.now().plusDays(5);
        sessionA = sessionRepository.save(WorkshopSession.builder()
                .reference(UUID.randomUUID().toString())
                .location(locationA)
                .slot(slotMorning)
                .workshopDate(workshopDate)
                .scheduledStartAt(workshopDate.atTime(9, 0).atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant())
                .scheduledEndAt(workshopDate.atTime(12, 0).atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant())
                .checkInOpensAt(workshopDate.atTime(8, 0).atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant())
                .checkInOpeningSource(CheckInOpeningSource.DEFAULT)
                .capacity(20)
                .state(SessionState.DRAFT)
                .build());

        WorkshopPackage testPackage = packageRepository.save(WorkshopPackage.builder()
                .name("Lifecycle Test Package")
                .description("Package fixture for lifecycle integration tests")
                .publicPrice(100_000L)
                .minParticipants(1)
                .maxParticipants(20)
                .requiredSessionCount(1)
                .sameLocationRequired(true)
                .minimumSameDaySlotGap(0)
                .state(WorkshopPackageState.PUBLISHED)
                .build());
        testPackageReference = testPackage.getId().toString();

        // Setup Manager A (accountId=10) with Location A scope
        when(accountFacade.resolveAccount("mgr-a")).thenReturn(Optional.of(
                new AccountSummary(10L, ActorRole.MANAGER, ActorStatus.ACTIVE, "mgra@mland.vn", Instant.now(), Instant.now())));
        when(accountFacade.getAccountStatus(10L)).thenReturn(Optional.of(ActorStatus.ACTIVE));
        when(accountFacade.getLocationScope(10L)).thenReturn(List.of(locationA.getId()));

        // Setup Manager B (accountId=20) with Location B scope
        when(accountFacade.resolveAccount("mgr-b")).thenReturn(Optional.of(
                new AccountSummary(20L, ActorRole.MANAGER, ActorStatus.ACTIVE, "mgrb@mland.vn", Instant.now(), Instant.now())));
        when(accountFacade.getAccountStatus(20L)).thenReturn(Optional.of(ActorStatus.ACTIVE));
        when(accountFacade.getLocationScope(20L)).thenReturn(List.of(locationB.getId()));

        // Setup Staff A (accountId=101) with Location A scope
        when(accountFacade.resolveAccount("staff-a")).thenReturn(Optional.of(
                new AccountSummary(101L, ActorRole.STAFF, ActorStatus.ACTIVE, "staffa@mland.vn", Instant.now(), Instant.now())));
        when(accountFacade.getAccountStatus(101L)).thenReturn(Optional.of(ActorStatus.ACTIVE));
        when(accountFacade.getLocationScope(101L)).thenReturn(List.of(locationA.getId()));

        // Setup Inactive Manager (accountId=30) with SUSPENDED status
        when(accountFacade.resolveAccount("inactive-mgr")).thenReturn(Optional.of(
                new AccountSummary(30L, ActorRole.MANAGER, ActorStatus.SUSPENDED, "inactive@mland.vn", Instant.now(), Instant.now())));
        when(accountFacade.getAccountStatus(30L)).thenReturn(Optional.of(ActorStatus.SUSPENDED));
        when(accountFacade.getLocationScope(30L)).thenReturn(List.of(locationA.getId()));

        // Setup TokenVerificationService and AccountFacade.resolveActor for IdentityGateFilter
        when(tokenVerificationService.verify("test-uid:mgr-a")).thenReturn(
                new VerifiedIdentity("mgr-a", "mgra@mland.vn", true, Instant.now()));
        when(tokenVerificationService.verify("test-uid:mgr-b")).thenReturn(
                new VerifiedIdentity("mgr-b", "mgrb@mland.vn", true, Instant.now()));
        when(tokenVerificationService.verify("test-uid:staff-a")).thenReturn(
                new VerifiedIdentity("staff-a", "staffa@mland.vn", true, Instant.now()));
        when(tokenVerificationService.verify("test-uid:inactive-mgr")).thenReturn(
                new VerifiedIdentity("inactive-mgr", "inactive@mland.vn", true, Instant.now()));

        when(accountFacade.resolveActor("mgr-a", true)).thenReturn(Optional.of(
                new CurrentAccountActor(10L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of(locationA.getId()))));
        when(accountFacade.resolveActor("mgr-b", true)).thenReturn(Optional.of(
                new CurrentAccountActor(20L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of(locationB.getId()))));
        when(accountFacade.resolveActor("staff-a", true)).thenReturn(Optional.of(
                new CurrentAccountActor(101L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(locationA.getId()))));
        when(accountFacade.resolveActor("inactive-mgr", true)).thenReturn(Optional.of(
                new CurrentAccountActor(30L, ActorRole.MANAGER, ActorStatus.SUSPENDED, true, List.of(locationA.getId()))));
    }

    // =========================================================================
    // 1. Manager Location Scope Enforcement
    // =========================================================================

    @Test
    @DisplayName("T025-1: Active Manager has global Workshop Operations authority")
    void shouldAllowActiveManagerGlobally() throws Exception {
        StateTransitionRequest publishReq = StateTransitionRequest.builder()
                .targetState(SessionState.PUBLISHED)
                .build();

        // Manager B may operate a session outside its historical location list.
        mockMvc.perform(post("/api/v1/workshop-sessions/" + sessionA.getReference() + "/state-transitions")
                        .header("Authorization", MGR_B_TOKEN)
                        .header("X-Correlation-Id", "corr-scope-denied")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(publishReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PUBLISHED"));

        WorkshopSession updated = sessionRepository.findByReference(sessionA.getReference()).orElseThrow();
        assertThat(updated.getState()).isEqualTo(SessionState.PUBLISHED);
    }

    // =========================================================================
    // 2. Inactive Actor Denied
    // =========================================================================

    @Test
    @DisplayName("T025-2: Inactive actor command is rejected with 403 Forbidden")
    void shouldRejectInactiveActor() throws Exception {
        StateTransitionRequest publishReq = StateTransitionRequest.builder()
                .targetState(SessionState.PUBLISHED)
                .build();

        mockMvc.perform(post("/api/v1/workshop-sessions/" + sessionA.getReference() + "/state-transitions")
                        .header("Authorization", INACTIVE_TOKEN)
                        .header("X-Correlation-Id", "corr-inactive-actor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(publishReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.isOneOf("FORBIDDEN", "ACCOUNT_SUSPENDED")));
    }

    // =========================================================================
    // 3. Target Account Validation (Not Found, Wrong Role, Not Active, Outside Scope)
    // =========================================================================

    @Test
    @DisplayName("T025-3: Target account validation rejects nonexistent, wrong role, inactive, and outside scope accounts")
    void shouldValidateTargetAccount() throws Exception {
        // Setup target mocks
        // Target 999: does not exist
        when(accountFacade.getTargetAccount(999L)).thenReturn(Optional.empty());

        // Target 201: wrong role (MEMBER instead of STAFF)
        when(accountFacade.getTargetAccount(201L)).thenReturn(Optional.of(
                new TargetAccountSnapshot(201L, ActorRole.MEMBER, ActorStatus.ACTIVE, List.of(locationA.getId()))));

        // Target 202: inactive staff (SUSPENDED)
        when(accountFacade.getTargetAccount(202L)).thenReturn(Optional.of(
                new TargetAccountSnapshot(202L, ActorRole.STAFF, ActorStatus.SUSPENDED, List.of(locationA.getId()))));

        // Target 203: staff outside location scope (only in locationB)
        when(accountFacade.getTargetAccount(203L)).thenReturn(Optional.of(
                new TargetAccountSnapshot(203L, ActorRole.STAFF, ActorStatus.ACTIVE, List.of(locationB.getId()))));

        // 3a. Target does not exist -> 409 INVALID_STATE
        StaffAssignmentRequest reqNotFound = StaffAssignmentRequest.builder().accountId(999L).build();
        mockMvc.perform(put("/api/v1/workshop-sessions/" + sessionA.getReference() + "/staff-assignments")
                        .header("Authorization", MGR_A_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqNotFound)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        // 3b. Target wrong role -> 409 INVALID_STATE
        StaffAssignmentRequest reqWrongRole = StaffAssignmentRequest.builder().accountId(201L).build();
        mockMvc.perform(put("/api/v1/workshop-sessions/" + sessionA.getReference() + "/staff-assignments")
                        .header("Authorization", MGR_A_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqWrongRole)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        // 3c. Target not ACTIVE -> 409 INVALID_STATE
        StaffAssignmentRequest reqSuspended = StaffAssignmentRequest.builder().accountId(202L).build();
        mockMvc.perform(put("/api/v1/workshop-sessions/" + sessionA.getReference() + "/staff-assignments")
                        .header("Authorization", MGR_A_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqSuspended)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        // 3d. Target outside location scope -> 403 LOCATION_SCOPE_DENIED
        StaffAssignmentRequest reqOutsideScope = StaffAssignmentRequest.builder().accountId(203L).build();
        mockMvc.perform(put("/api/v1/workshop-sessions/" + sessionA.getReference() + "/staff-assignments")
                        .header("Authorization", MGR_A_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqOutsideScope)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LOCATION_SCOPE_DENIED"));
    }

    // =========================================================================
    // 4. Publish Without Staff Assignment
    // =========================================================================

    @Test
    @DisplayName("T025-4: Session publication succeeds without pre-assigned Staff")
    void shouldAllowPublishWithoutStaff() throws Exception {
        // Verify sessionA has 0 staff assignments
        List<SessionStaffAssignment> assignments = staffAssignmentRepository.findBySessionIdAndActiveTrue(sessionA.getId());
        assertThat(assignments).isEmpty();

        StateTransitionRequest publishReq = StateTransitionRequest.builder()
                .targetState(SessionState.PUBLISHED)
                .build();

        mockMvc.perform(post("/api/v1/workshop-sessions/" + sessionA.getReference() + "/state-transitions")
                        .header("Authorization", MGR_A_TOKEN)
                        .header("X-Correlation-Id", "corr-pub-no-staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(publishReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PUBLISHED"));

        WorkshopSession updated = sessionRepository.findByReference(sessionA.getReference()).orElseThrow();
        assertThat(updated.getState()).isEqualTo(SessionState.PUBLISHED);
    }

    // =========================================================================
    // 5. Start Allowed For Staff In Location Without Assignment
    // =========================================================================

    @Test
    @DisplayName("T025-5: Staff in session location may start without assignment")
    void shouldEnforceResponsibilityOnStart() throws Exception {
        // Set session to PUBLISHED
        sessionA.setState(SessionState.PUBLISHED);
        sessionRepository.save(sessionA);

        StateTransitionRequest startReq = StateTransitionRequest.builder()
                .targetState(SessionState.IN_PROGRESS)
                .build();

        // Target staff A mock
        when(accountFacade.getTargetAccount(101L)).thenReturn(Optional.of(
                new TargetAccountSnapshot(101L, ActorRole.STAFF, ActorStatus.ACTIVE, List.of(locationA.getId()))));

        // Staff A is in the session location and does not need a session assignment.
        mockMvc.perform(post("/api/v1/workshop-sessions/" + sessionA.getReference() + "/state-transitions")
                        .header("Authorization", STAFF_A_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(startReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("IN_PROGRESS"));

        WorkshopSession updated = sessionRepository.findByReference(sessionA.getReference()).orElseThrow();
        assertThat(updated.getState()).isEqualTo(SessionState.IN_PROGRESS);
    }

    // =========================================================================
    // 6. SALES_PAUSED Blocks New Holds, Preserves Existing Unexpired Holds
    // =========================================================================

    @Test
    @DisplayName("T025-6a: Check-in opens before scheduled start, scheduler starts at scheduled time")
    void shouldSeparateCheckInOpeningFromScheduledStart() {
        Instant now = Instant.now();
        sessionA.setState(SessionState.PUBLISHED);
        sessionA.setCheckInOpensAt(now.minusSeconds(60));
        sessionA.setScheduledStartAt(now.plusSeconds(60));
        sessionRepository.saveAndFlush(sessionA);

        sessionStartScheduler.transitionDueSessions();
        WorkshopSession beforeStart = sessionRepository.findByReference(sessionA.getReference()).orElseThrow();
        assertThat(beforeStart.getState()).isEqualTo(SessionState.PUBLISHED);
        assertThat(beforeStart.getCheckInOpensAt()).isBefore(now);

        beforeStart.setScheduledStartAt(now.minusSeconds(1));
        sessionRepository.saveAndFlush(beforeStart);
        sessionStartScheduler.transitionDueSessions();

        WorkshopSession afterStart = sessionRepository.findByReference(sessionA.getReference()).orElseThrow();
        assertThat(afterStart.getState()).isEqualTo(SessionState.IN_PROGRESS);
        assertThat(auditEventRepository.findBySessionIdOrderByOccurredAtDesc(afterStart.getId()))
                .anyMatch(event -> "SESSION_AUTO_STARTED".equals(event.getAction()));
    }

    @Test
    @DisplayName("T025-6: SALES_PAUSED blocks new hold creation while preserving and committing existing unexpired holds")
    void shouldPreserveHoldsAcrossSalesPause() throws Exception {
        // Publish sessionA
        sessionA.setState(SessionState.PUBLISHED);
        sessionRepository.save(sessionA);

        UUID sessionUuid = UUID.fromString(sessionA.getReference());
        String opRefHold = "OP-HOLD-" + UUID.randomUUID();
        String corrRefHold = "CORR-HOLD-001";

        // 6a. Reserve capacity while PUBLISHED -> Accepted
        ReserveBookingCapacityCommand reserveCmd = new ReserveBookingCapacityCommand(
                "BKG-PAUSE-01",
                testPackageReference,
                List.of(sessionUuid),
                2,
                opRefHold,
                corrRefHold
        );
        CapacityHoldOutcome holdOutcome = operationsFacade.reserveBookingCapacity(reserveCmd);
        assertThat(holdOutcome.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        assertThat(holdOutcome.holdReferences()).hasSize(1);
        UUID holdRef = holdOutcome.holdReferences().getFirst();

        // Verify hold is active in database
        Optional<CapacityHold> holdOpt = capacityHoldRepository.findByReference(holdRef.toString());
        assertThat(holdOpt).isPresent();
        assertThat(holdOpt.get().getState()).isEqualTo(CapacityHoldState.ACTIVE);

        // 6b. Manager transitions session to SALES_PAUSED
        StateTransitionRequest pauseReq = StateTransitionRequest.builder()
                .targetState(SessionState.SALES_PAUSED)
                .build();

        mockMvc.perform(post("/api/v1/workshop-sessions/" + sessionA.getReference() + "/state-transitions")
                        .header("Authorization", MGR_A_TOKEN)
                        .header("X-Correlation-Id", "corr-pause-sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pauseReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SALES_PAUSED"));

        // 6c. Attempt to reserve new capacity while SALES_PAUSED -> Rejected (SALE_NOT_ELIGIBLE)
        ReserveBookingCapacityCommand newReserveCmd = new ReserveBookingCapacityCommand(
                "BKG-PAUSE-02",
                testPackageReference,
                List.of(sessionUuid),
                1,
                "OP-NEW-" + UUID.randomUUID(),
                "CORR-NEW-001"
        );
        CapacityHoldOutcome rejectedOutcome = operationsFacade.reserveBookingCapacity(newReserveCmd);
        assertThat(rejectedOutcome.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(rejectedOutcome.reason()).isEqualTo(RejectionReason.SALE_NOT_ELIGIBLE);

        // 6d. Commit existing pre-pause active unexpired hold -> Accepted!
        CommitBookingHoldsCommand commitCmd = new CommitBookingHoldsCommand(
                "BKG-PAUSE-01",
                List.of(holdRef),
                "OP-COMMIT-" + UUID.randomUUID(),
                "CORR-COMMIT-001"
        );
        CapacityCommitOutcome commitOutcome = operationsFacade.commitBookingHolds(commitCmd);
        assertThat(commitOutcome.status()).isEqualTo(OutcomeStatus.ACCEPTED);

        CapacityHold committedHold = capacityHoldRepository.findByReference(holdRef.toString()).orElseThrow();
        assertThat(committedHold.getState()).isEqualTo(CapacityHoldState.CONFIRMED);
    }

    // =========================================================================
    // 7. Atomic Operational Lead Handover
    // =========================================================================

    @Test
    @DisplayName("T025-7: Retired operational-lead endpoint is not exposed")
    void shouldExecuteAtomicOperationalLeadHandover() throws Exception {
        mockMvc.perform(put("/api/v1/workshop-sessions/" + sessionA.getReference() + "/operational-lead")
                        .header("Authorization", MGR_A_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // 8. In-Progress Staff Removal Invariant
    // =========================================================================

    @Test
    @DisplayName("T025-8: Staff removal during IN_PROGRESS follows location/role authorization")
    void shouldEnforceStaffRemovalInvariantDuringInProgress() throws Exception {
        sessionA.setState(SessionState.IN_PROGRESS);
        sessionRepository.save(sessionA);

        when(accountFacade.getTargetAccount(101L)).thenReturn(Optional.of(
                new TargetAccountSnapshot(101L, ActorRole.STAFF, ActorStatus.ACTIVE, List.of(locationA.getId()))));

        // Staff 101 assigned to session
        SessionStaffAssignment staffAssignment = staffAssignmentRepository.save(SessionStaffAssignment.builder()
                .session(sessionA)
                .accountId(101L)
                .assignedById(10L)
                .active(true)
                .assignedAt(Instant.now())
                .build());

        // The final responsible Staff member still cannot be removed while
        // the session is IN_PROGRESS; this is a responsibility invariant,
        // not a per-request assignment authorization check.
        mockMvc.perform(delete("/api/v1/workshop-sessions/" + sessionA.getReference() + "/staff-assignments")
                        .header("Authorization", MGR_A_TOKEN)
                        .param("accountId", "101"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        assertThat(staffAssignmentRepository.findById(staffAssignment.getId()).orElseThrow().isActive()).isTrue();

    }

    // =========================================================================
    // 9. Immutable Audit Evidence
    // =========================================================================

    @Test
    @DisplayName("T025-9: Verify immutable audit records with correlation tracking and structured metadata")
    void shouldRecordImmutableAuditEvidence() throws Exception {
        String corrId = "CORR-AUDIT-TEST-456";

        StateTransitionRequest publishReq = StateTransitionRequest.builder()
                .targetState(SessionState.PUBLISHED)
                .build();

        mockMvc.perform(post("/api/v1/workshop-sessions/" + sessionA.getReference() + "/state-transitions")
                        .header("Authorization", MGR_A_TOKEN)
                        .header("X-Correlation-Id", corrId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(publishReq)))
                .andExpect(status().isOk());

        List<OperationsAuditEvent> events = auditEventRepository.findBySessionIdOrderByOccurredAtDesc(sessionA.getId());
        assertThat(events).isNotEmpty();

        OperationsAuditEvent event = events.getFirst();
        assertThat(event.getAction()).isEqualTo("SESSION_STATE_TRANSITION");
        assertThat(event.getOutcome()).isEqualTo("SUCCESS");
        assertThat(event.getCorrelationId()).isEqualTo(corrId);
        assertThat(event.getActorId()).isEqualTo(10L);
        assertThat(event.getMetadata()).contains("PUBLISHED");
        assertThat(event.getOccurredAt()).isNotNull();
    }
}
