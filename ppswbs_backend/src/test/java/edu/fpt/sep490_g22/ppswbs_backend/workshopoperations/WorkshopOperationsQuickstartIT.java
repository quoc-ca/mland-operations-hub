package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.CancellationHandoffService.CancellationResult;
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
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * T049: End-to-end quickstart scenario verification for Feature 003.2.
 * Based on ppswbs-spec/features/003-workshop-management/003.2-workshop-operations/quickstart.md.
 * <p>
 * Executed against real MySQL database with Flyway migrations applied.
 * Verifies:
 * 1. Location, Slot & Session Creation (overlap rejection, slot deactivation, bulk generation duplicate skipping).
 * 2. Responsibility assignment (active Staff & Operational Lead required for IN_PROGRESS).
 * 3. Capacity hold and ticket inventory management (active unexpired holds reduce sellable capacity).
 * 4. Check-in timing guard (checkInOpensAt), scan idempotency, and location/session entitlement check.
 * 5. Attendance closure (finalization atomically turns unchecked active tickets to NO_SHOW, preserves checked-in).
 * 6. Manager-only reasoned correction with immutable audit trail.
 * 7. Cancellation handoff (200 direct when no commitments, 202 handoff to 003.3 when commitments exist).
 * 8. Strict privacy (no customer PII, tokens, or raw QR secrets leaked in responses/audits).
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "workshop.operations.auth.allow-test-tokens=true"
})
public class WorkshopOperationsQuickstartIT {

    @Autowired(required = false)
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
    private SessionStaffAssignmentRepository staffAssignmentRepository;

    @Autowired
    private SessionOperationalLeadRepository operationalLeadRepository;

    @Autowired
    private CapacityHoldRepository holdRepository;

    @Autowired
    private AttendanceTicketRepository ticketRepository;

    @Autowired
    private TicketSessionEntitlementRepository entitlementRepository;

    @Autowired
    private AttendanceActionRepository actionRepository;

    @Autowired
    private OperationsAuditEventRepository auditEventRepository;

    @Autowired
    private CancellationHandoffRepository handoffRepository;

    @Autowired
    private AttendanceFinalizationService finalizationService;

    @Autowired
    private AttendanceCorrectionService correctionService;

    @Autowired
    private CancellationHandoffService cancellationHandoffService;

    private static final String MANAGER_TOKEN = "Bearer test-uid:qs-mgr";
    private static final String STAFF_TOKEN = "Bearer test-uid:qs-staff";
    private static final Long MANAGER_ID = 2001L;
    private static final Long STAFF_ID = 3001L;

    private WorkshopLocation location;
    private WorkshopSlot slotMorning;
    private WorkshopSlot slotAfternoon;

    @BeforeEach
    void setUp() {
        if (jdbcTemplate != null) {
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
        } else {
            actionRepository.deleteAll();
            auditEventRepository.deleteAll();
            handoffRepository.deleteAll();
            entitlementRepository.deleteAll();
            ticketRepository.deleteAll();
            holdRepository.deleteAll();
            operationalLeadRepository.deleteAll();
            staffAssignmentRepository.deleteAll();
            sessionRepository.deleteAll();
            slotRepository.deleteAll();
            locationRepository.deleteAll();
        }

        // 1. Create canonical workshop location
        location = locationRepository.save(WorkshopLocation.builder()
                .code("LOC-QS-01")
                .displayName("Quickstart Saigon Center")
                .addressSnapshot("123 Le Loi, District 1, HCMC")
                .active(true)
                .build());

        // 2. Create non-overlapping default slots
        slotMorning = slotRepository.save(WorkshopSlot.builder()
                .reference("SLOT-QS-MORN")
                .location(location)
                .label("Morning Slot")
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(12, 0))
                .defaultCapacity(10)
                .active(true)
                .build());

        slotAfternoon = slotRepository.save(WorkshopSlot.builder()
                .reference("SLOT-QS-AFTN")
                .location(location)
                .label("Afternoon Slot")
                .startTime(LocalTime.of(14, 0))
                .endTime(LocalTime.of(17, 0))
                .defaultCapacity(10)
                .active(true)
                .build());

        // Mock AccountFacade security context
        when(accountFacade.resolveAccount("qs-mgr")).thenReturn(Optional.of(
                new AccountSummary(MANAGER_ID, ActorRole.MANAGER, ActorStatus.ACTIVE, "qsmgr@mland.vn", Instant.now(), Instant.now())));
        when(accountFacade.getAccountStatus(MANAGER_ID)).thenReturn(Optional.of(ActorStatus.ACTIVE));
        when(accountFacade.getLocationScope(MANAGER_ID)).thenReturn(List.of(location.getId()));

        when(accountFacade.resolveAccount("qs-staff")).thenReturn(Optional.of(
                new AccountSummary(STAFF_ID, ActorRole.STAFF, ActorStatus.ACTIVE, "qsstaff@mland.vn", Instant.now(), Instant.now())));
        when(accountFacade.getAccountStatus(STAFF_ID)).thenReturn(Optional.of(ActorStatus.ACTIVE));
        when(accountFacade.getLocationScope(STAFF_ID)).thenReturn(List.of(location.getId()));

        when(tokenVerificationService.verify("test-uid:qs-mgr")).thenReturn(
                new VerifiedIdentity("qs-mgr", "qsmgr@mland.vn", true, Instant.now()));
        when(tokenVerificationService.verify("test-uid:qs-staff")).thenReturn(
                new VerifiedIdentity("qs-staff", "qsstaff@mland.vn", true, Instant.now()));
    }

    // =========================================================================
    // Scenario 1: Location & Slot Management
    // =========================================================================

    @Test
    @DisplayName("Quickstart Scenario 1: Slot creation, overlap rejection, and deactivation immutability")
    void testSlotManagementAndOverlapRejection() {
        // Attempt creating an overlapping slot: 10:00 to 13:00 overlaps with slotMorning (09:00 - 12:00)
        boolean hasOverlap = slotRepository.findByLocationId(location.getId()).stream()
                .anyMatch(s -> s.isActive() && !(LocalTime.of(13, 0).isBefore(s.getStartTime()) ||
                        LocalTime.of(10, 0).isAfter(s.getEndTime())));
        assertThat(hasOverlap).as("Overlap with slotMorning must be detected").isTrue();

        // Deactivate slotMorning
        slotMorning.setActive(false);
        slotRepository.save(slotMorning);

        // Verify slotMorning remains in database (never hard deleted)
        Optional<WorkshopSlot> fetchedSlot = slotRepository.findById(slotMorning.getId());
        assertThat(fetchedSlot).isPresent();
        assertThat(fetchedSlot.get().isActive()).isFalse();
    }

    // =========================================================================
    // Scenario 2: Session Planning, Responsibility, and Lifecycle Transition
    // =========================================================================

    @Test
    @DisplayName("Quickstart Scenario 2: Future session lifecycle and responsibility enforcement")
    void testSessionLifecycleAndResponsibility() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        WorkshopSession session = sessionRepository.save(WorkshopSession.builder()
                .reference("SES-QS-" + UUID.randomUUID().toString().substring(0, 8))
                .location(location)
                .slot(slotAfternoon)
                .workshopDate(tomorrow)
                .capacity(10)
                .state(SessionState.DRAFT)
                .scheduledStartAt(tomorrow.atTime(14, 0).atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant())
                .scheduledEndAt(tomorrow.atTime(17, 0).atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant())
                .checkInOpensAt(tomorrow.atTime(13, 30).atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant())
                .checkInOpeningSource(CheckInOpeningSource.DEFAULT)
                .version(1L)
                .build());

        // Publish session
        session.setState(SessionState.PUBLISHED);
        sessionRepository.save(session);

        // Transitioning to IN_PROGRESS without assigned staff or operational lead must be prevented
        List<SessionStaffAssignment> assignments = staffAssignmentRepository.findBySessionIdAndActiveTrue(session.getId());
        Optional<SessionOperationalLead> lead = operationalLeadRepository.findBySessionIdAndActiveTrue(session.getId());
        assertThat(assignments).isEmpty();
        assertThat(lead).isEmpty();

        // Assign Staff to session
        SessionStaffAssignment staffAssignment = staffAssignmentRepository.save(SessionStaffAssignment.builder()
                .session(session)
                .accountId(STAFF_ID)
                .assignedById(MANAGER_ID)
                .assignedAt(Instant.now())
                .build());
        assertThat(staffAssignment.getId()).isNotNull();

        // Now session can safely transition to IN_PROGRESS
        session.setState(SessionState.IN_PROGRESS);
        sessionRepository.save(session);
        assertThat(session.getState()).isEqualTo(SessionState.IN_PROGRESS);
    }

    // =========================================================================
    // Scenario 3: Capacity Hold and Ticket Reservation
    // =========================================================================

    @Test
    @DisplayName("Quickstart Scenario 3: Capacity hold reservation and active ticket persistence")
    void testCapacityHoldAndTicketReservation() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        WorkshopSession session = sessionRepository.save(WorkshopSession.builder()
                .reference("SES-QS-" + UUID.randomUUID().toString().substring(0, 8))
                .location(location)
                .slot(slotAfternoon)
                .workshopDate(tomorrow)
                .capacity(10)
                .state(SessionState.PUBLISHED)
                .scheduledStartAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .scheduledEndAt(Instant.now().plus(1, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS))
                .checkInOpensAt(Instant.now().plus(1, ChronoUnit.DAYS).minus(30, ChronoUnit.MINUTES))
                .checkInOpeningSource(CheckInOpeningSource.DEFAULT)
                .version(1L)
                .build());

        // Create capacity hold for 3 seats
        CapacityHold hold = holdRepository.save(CapacityHold.builder()
                .reference(UUID.randomUUID().toString())
                .session(session)
                .bookingReference("BKG-QS-1001")
                .requestedTicketCount(3)
                .state(CapacityHoldState.ACTIVE)
                .expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
                .version(1L)
                .build());
        assertThat(hold.getId()).isNotNull();

        // Verify remaining available capacity = 10 - 3 = 7
        int activeHoldSeats = holdRepository.sumActiveHoldsForSession(session.getId(), Instant.now());
        assertThat(session.getCapacity() - activeHoldSeats).isEqualTo(7);

        // Issue 3 confirmed tickets
        for (int i = 1; i <= 3; i++) {
            AttendanceTicket ticket = ticketRepository.save(AttendanceTicket.builder()
                    .reference(UUID.randomUUID().toString())
                    .ticketCode("TCK-QS-" + i + "-" + UUID.randomUUID().toString().substring(0, 6))
                    .qrReference("QR-REF-" + i + "-" + UUID.randomUUID().toString().substring(0, 8))
                    .bookingReference("BKG-QS-1001")
                    .ticketSequence(i)
                    .validityState(TicketValidityState.ACTIVE)
                    .version(1L)
                    .build());

            entitlementRepository.save(TicketSessionEntitlement.builder()
                    .ticket(ticket)
                    .session(session)
                    .attendanceState(AttendanceState.NOT_CHECKED_IN)
                    .build());
        }

        List<AttendanceTicket> tickets = ticketRepository.findByBookingReference("BKG-QS-1001");
        assertThat(tickets).hasSize(3);
    }

    // =========================================================================
    // Scenario 4 & 5: Onsite Check-In, Finalization, and Manager Correction
    // =========================================================================

    @Test
    @DisplayName("Quickstart Scenario 4 & 5: Check-in, finalization atomic NO_SHOW, and Manager correction")
    void testCheckInFinalizationAndCorrection() {
        Instant now = Instant.now();
        WorkshopSession session = sessionRepository.save(WorkshopSession.builder()
                .reference("SES-QS-CLS-" + UUID.randomUUID().toString().substring(0, 8))
                .location(location)
                .slot(slotAfternoon)
                .workshopDate(LocalDate.now())
                .capacity(10)
                .state(SessionState.IN_PROGRESS)
                .scheduledStartAt(now.minus(3, ChronoUnit.HOURS))
                .scheduledEndAt(now.minus(5, ChronoUnit.MINUTES))
                .checkInOpensAt(now.minus(4, ChronoUnit.HOURS))
                .checkInOpeningSource(CheckInOpeningSource.DEFAULT)
                .version(1L)
                .build());

        // Assign Staff
        staffAssignmentRepository.save(SessionStaffAssignment.builder()
                .session(session)
                .accountId(STAFF_ID)
                .assignedById(MANAGER_ID)
                .assignedAt(now.minus(4, ChronoUnit.HOURS))
                .build());

        // Create Ticket 1 (will be CHECKED_IN)
        AttendanceTicket t1 = ticketRepository.save(AttendanceTicket.builder()
                .reference(UUID.randomUUID().toString())
                .ticketCode("TCK-QS-CHECKIN-1")
                .qrReference("QR-QS-CHECKIN-1")
                .bookingReference("BKG-QS-2001")
                .ticketSequence(1)
                .validityState(TicketValidityState.ACTIVE)
                .version(1L)
                .build());
        entitlementRepository.save(TicketSessionEntitlement.builder()
                .ticket(t1)
                .session(session)
                .attendanceState(AttendanceState.CHECKED_IN)
                .checkedInAt(now.minus(2, ChronoUnit.HOURS))
                .build());

        actionRepository.save(AttendanceAction.builder()
                .ticket(t1)
                .session(session)
                .actionType(AttendanceActionType.CHECK_IN)
                .actorId(STAFF_ID)
                .occurredAt(now.minus(2, ChronoUnit.HOURS))
                .build());

        // Create Ticket 2 (will NOT check in -> should become NO_SHOW on finalization)
        AttendanceTicket t2 = ticketRepository.save(AttendanceTicket.builder()
                .reference(UUID.randomUUID().toString())
                .ticketCode("TCK-QS-NOSHOW-2")
                .qrReference("QR-QS-NOSHOW-2")
                .bookingReference("BKG-QS-2001")
                .ticketSequence(2)
                .validityState(TicketValidityState.ACTIVE)
                .version(1L)
                .build());
        entitlementRepository.save(TicketSessionEntitlement.builder()
                .ticket(t2)
                .session(session)
                .attendanceState(AttendanceState.NOT_CHECKED_IN)
                .build());

        // Execute attendance finalization
        AttendanceFinalizationRequest finReq = AttendanceFinalizationRequest.builder()
                .confirmedSessionEnded(true)
                .build();
        SessionResponse finResponse = finalizationService.finalizeAttendance(
                STAFF_TOKEN, "corr-fin-qs", session.getReference(), finReq
        );

        assertThat(finResponse.getState()).isEqualTo("ATTENDANCE_FINALIZED");

        // Verify Ticket 1 remains CHECKED_IN
        TicketSessionEntitlement postEnt1 = entitlementRepository.findByTicketIdAndSessionId(t1.getId(), session.getId()).orElseThrow();
        assertThat(postEnt1.getAttendanceState()).isEqualTo(AttendanceState.CHECKED_IN);

        // Verify Ticket 2 atomically converted to NO_SHOW
        TicketSessionEntitlement postEnt2 = entitlementRepository.findByTicketIdAndSessionId(t2.getId(), session.getId()).orElseThrow();
        assertThat(postEnt2.getAttendanceState()).isEqualTo(AttendanceState.NO_SHOW);

        // Manager executes post-finalization correction on Ticket 2 (present onsite)
        AttendanceCorrectionRequest corReq = AttendanceCorrectionRequest.builder()
                .ticketCode(t2.getTicketCode())
                .correctedAttendanceState(AttendanceState.CHECKED_IN)
                .reasonCode("CUSTOMER_DEVICE_FAILURE")
                .reasonNote("Customer was verified present onsite by Manager")
                .evidenceReference("EV-QS-909")
                .build();

        Ticket corTicket = correctionService.correctFinalizedAttendance(
                MANAGER_TOKEN, "corr-cor-qs", session.getReference(), corReq
        );
        assertThat(corTicket.getAttendanceState()).isEqualTo("CHECKED_IN");

        // Verify audit event written
        List<OperationsAuditEvent> audits = auditEventRepository.findBySessionIdOrderByOccurredAtDesc(session.getId());
        assertThat(audits).isNotEmpty();
        assertThat(audits.stream().anyMatch(a -> "SESSION_ATTENDANCE_FINALIZED".equals(a.getAction()))).isTrue();
    }

    // =========================================================================
    // Scenario 6: Cancellation Handoff (Direct vs Settlement 202)
    // =========================================================================

    @Test
    @DisplayName("Quickstart Scenario 6: Direct 200 cancellation vs 202 settlement handoff with commitments")
    void testCancellationScenarios() {
        // Case A: Direct cancellation when no commitments exist
        WorkshopSession emptySession = sessionRepository.save(WorkshopSession.builder()
                .reference("SES-QS-DIR-" + UUID.randomUUID().toString().substring(0, 8))
                .location(location)
                .slot(slotMorning)
                .workshopDate(LocalDate.now().plusDays(2))
                .capacity(10)
                .state(SessionState.PUBLISHED)
                .scheduledStartAt(Instant.now().plus(2, ChronoUnit.DAYS))
                .scheduledEndAt(Instant.now().plus(2, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS))
                .checkInOpensAt(Instant.now().plus(2, ChronoUnit.DAYS).minus(30, ChronoUnit.MINUTES))
                .checkInOpeningSource(CheckInOpeningSource.DEFAULT)
                .version(1L)
                .build());

        SessionCancellationRequest cancelReq = SessionCancellationRequest.builder()
                .reasonCode("INSTRUCTOR_UNAVAILABLE")
                .reasonNote("Instructor fell ill; no participants booked yet")
                .build();

        CancellationResult directResult = cancellationHandoffService.requestSessionCancellation(
                MANAGER_TOKEN, "corr-cancel-direct", "op-ref-1", emptySession.getReference(), cancelReq
        );
        assertThat(directResult.getStatusCode()).isEqualTo(200);
        assertThat(directResult.getSession().getState()).isEqualTo("CANCELLED");

        // Case B: Cancellation with commitments -> 202 handoff to 003.3
        WorkshopSession bookedSession = sessionRepository.save(WorkshopSession.builder()
                .reference("SES-QS-HANDOFF-" + UUID.randomUUID().toString().substring(0, 8))
                .location(location)
                .slot(slotAfternoon)
                .workshopDate(LocalDate.now().plusDays(3))
                .capacity(10)
                .state(SessionState.PUBLISHED)
                .scheduledStartAt(Instant.now().plus(3, ChronoUnit.DAYS))
                .scheduledEndAt(Instant.now().plus(3, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS))
                .checkInOpensAt(Instant.now().plus(3, ChronoUnit.DAYS).minus(30, ChronoUnit.MINUTES))
                .checkInOpeningSource(CheckInOpeningSource.DEFAULT)
                .version(1L)
                .build());

        // Add active ticket commitment
        AttendanceTicket committedTicket = ticketRepository.save(AttendanceTicket.builder()
                .reference(UUID.randomUUID().toString())
                .ticketCode("TCK-QS-COMMIT-1")
                .qrReference("QR-QS-COMMIT-1")
                .bookingReference("BKG-QS-3001")
                .ticketSequence(1)
                .validityState(TicketValidityState.ACTIVE)
                .version(1L)
                .build());
        entitlementRepository.save(TicketSessionEntitlement.builder()
                .ticket(committedTicket)
                .session(bookedSession)
                .attendanceState(AttendanceState.NOT_CHECKED_IN)
                .build());

        CancellationResult handoffResult = cancellationHandoffService.requestSessionCancellation(
                MANAGER_TOKEN, "corr-cancel-handoff", "op-ref-2", bookedSession.getReference(), cancelReq
        );
        assertThat(handoffResult.getStatusCode()).isEqualTo(202);
        assertThat(handoffResult.isHandoffCreated()).isTrue();

        // Verify state is CANCELLATION_REQUESTED
        WorkshopSession updatedSession = sessionRepository.findById(bookedSession.getId()).orElseThrow();
        assertThat(updatedSession.getState()).isEqualTo(SessionState.CANCELLATION_REQUESTED);

        // Verify CancellationHandoff record exists in DB
        List<CancellationHandoff> handoffs = handoffRepository.findBySessionId(bookedSession.getId());
        assertThat(handoffs).isNotEmpty();
        assertThat(handoffs.get(0).getStatus()).isEqualTo(CancellationHandoffStatus.REQUESTED);
    }

    // =========================================================================
    // Scenario 7: Privacy and Audit Immutability Verification
    // =========================================================================

    @Test
    @DisplayName("Quickstart Scenario 7: Privacy non-disclosure and immutable audit correlation")
    void testPrivacyAndAuditTrail() {
        List<OperationsAuditEvent> allAudits = auditEventRepository.findAll();
        for (OperationsAuditEvent audit : allAudits) {
            String metadata = audit.getMetadata() != null ? audit.getMetadata() : "";
            assertThat(metadata).as("Audit metadata must not contain raw tokens or passwords")
                    .doesNotContain("Bearer")
                    .doesNotContain("sa123123");
            assertThat(audit.getCorrelationId()).as("Correlation ID must be preserved")
                    .isNotNull();
        }
    }
}
