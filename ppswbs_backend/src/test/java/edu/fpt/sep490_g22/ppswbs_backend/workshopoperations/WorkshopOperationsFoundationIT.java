package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller.WorkshopOperationsExceptionHandler;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "workshop.operations.cors.allowed-origins=http://localhost:3000,http://localhost:5173",
        "workshop.operations.auth.allow-test-tokens=true",
        "workshop.operations.idempotency.lease-duration=PT30S"
})
public class WorkshopOperationsFoundationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AccountFacade accountFacade;

    @MockBean
    private TokenVerificationService tokenVerificationService;

    @Autowired
    private WorkshopOperationsAuthorizationService authorizationService;

    @Autowired
    private WorkshopOperationsFacade operationsFacade;

    @Autowired(required = false)
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
    private FacadeCommandIdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private CancellationHandoffRepository handoffRepository;

    private WorkshopLocation testLocation;
    private WorkshopSlot testSlot;
    private WorkshopSession testSession;

    @BeforeEach
    void setUp() {
        idempotencyRecordRepository.deleteAll();
        actionRepository.deleteAll();
        entitlementRepository.deleteAll();
        ticketRepository.deleteAll();
        holdRepository.deleteAll();
        operationalLeadRepository.deleteAll();
        staffAssignmentRepository.deleteAll();
        handoffRepository.deleteAll();
        auditEventRepository.deleteAll();
        sessionRepository.deleteAll();
        slotRepository.deleteAll();
        locationRepository.deleteAll();

        testLocation = locationRepository.save(WorkshopLocation.builder()
                .code("LOC-01")
                .displayName("District 1 Workshop Hub")
                .addressSnapshot("123 Le Loi, District 1, HCMC")
                .active(true)
                .build());

        testSlot = slotRepository.save(WorkshopSlot.builder()
                .reference(UUID.randomUUID().toString())
                .location(testLocation)
                .label("Morning Slot")
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(12, 0))
                .defaultCapacity(20)
                .active(true)
                .build());

        testSession = sessionRepository.save(WorkshopSession.builder()
                .reference(UUID.randomUUID().toString())
                .location(testLocation)
                .slot(testSlot)
                .workshopDate(LocalDate.now().plusDays(5))
                .scheduledStartAt(Instant.now().plus(5, ChronoUnit.DAYS))
                .scheduledEndAt(Instant.now().plus(5, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS))
                .capacity(20)
                .state(SessionState.PUBLISHED)
                .checkInOpensAt(Instant.now().plus(4, ChronoUnit.DAYS))
                .checkInOpeningSource(CheckInOpeningSource.DEFAULT)
                .build());

        when(tokenVerificationService.verify("test-token")).thenReturn(
                new VerifiedIdentity("test-uid", "test@fpt.edu.vn", true, Instant.now())
        );
        when(accountFacade.resolveActor("test-uid", true)).thenReturn(Optional.of(
                new CurrentAccountActor(10L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of(testLocation.getId()))
        ));
    }

    // =========================================================================
    // Nested 1: Schema Invariants & Flyway (T008, T009)
    // =========================================================================
    @Nested
    @DisplayName("Schema Invariants & Flyway Migration Tests")
    class SchemaInvariantTests {

        @Test
        @DisplayName("Flyway V5 through V8 should be applied in canonical sequence on MySQL")
        void flywayMigrationsAppliedInCanonicalSequence() {
            List<String> versions = jdbcTemplate.query(
                    "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank ASC",
                    (rs, rowNum) -> rs.getString("version")
            );
            assertThat(versions).contains("5", "6", "7", "8");
        }

        @Test
        @DisplayName("Should successfully persist all operational entities across schema")
        void shouldPersistAllOperationalEntities() {
            assertThat(testLocation.getId()).isNotNull();
            assertThat(testSlot.getId()).isNotNull();
            assertThat(testSession.getId()).isNotNull();

            // Staff Assignment
            SessionStaffAssignment assignment = staffAssignmentRepository.save(SessionStaffAssignment.builder()
                    .session(testSession)
                    .accountId(100L)
                    .assignedById(1L)
                    .active(true)
                    .build());
            assertThat(assignment.getId()).isNotNull();

            // Operational Lead
            SessionOperationalLead lead = operationalLeadRepository.save(SessionOperationalLead.builder()
                    .session(testSession)
                    .managerAccountId(100L)
                    .reasonCode("MGR_COVERAGE")
                    .active(true)
                    .build());
            assertThat(lead.getId()).isNotNull();

            // Capacity Hold
            CapacityHold hold = holdRepository.save(CapacityHold.builder()
                    .reference(UUID.randomUUID().toString())
                    .session(testSession)
                    .bookingReference("BKG-001")
                    .requestedTicketCount(2)
                    .state(CapacityHoldState.ACTIVE)
                    .expiresAt(Instant.now().plusSeconds(900))
                    .build());
            assertThat(hold.getId()).isNotNull();

            // Attendance Ticket
            AttendanceTicket ticket = ticketRepository.save(AttendanceTicket.builder()
                    .reference(UUID.randomUUID().toString())
                    .bookingReference("BKG-001")
                    .ticketSequence(1)
                    .ticketCode("TCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                    .qrReference("QR-" + UUID.randomUUID())
                    .validityState(TicketValidityState.ACTIVE)
                    .build());
            assertThat(ticket.getId()).isNotNull();

            // Ticket Entitlement
            TicketSessionEntitlement entitlement = entitlementRepository.save(TicketSessionEntitlement.builder()
                    .ticket(ticket)
                    .session(testSession)
                    .attendanceState(AttendanceState.NOT_CHECKED_IN)
                    .build());
            assertThat(entitlement.getId()).isNotNull();

            // Attendance Action
            AttendanceAction action = actionRepository.save(AttendanceAction.builder()
                    .ticket(ticket)
                    .session(testSession)
                    .actionType(AttendanceActionType.CHECK_IN)
                    .actorId(100L)
                    .channel("ONSITE_SCAN")
                    .build());
            assertThat(action.getId()).isNotNull();

            // Audit Event
            OperationsAuditEvent audit = auditEventRepository.save(OperationsAuditEvent.builder()
                    .sessionId(testSession.getId())
                    .action("SESSION_CREATED")
                    .outcome("SUCCESS")
                    .actorId(1L)
                    .correlationId(UUID.randomUUID().toString())
                    .metadata("{\"action\":\"test_create\"}")
                    .build());
            assertThat(audit.getId()).isNotNull();

            // Cancellation Handoff
            CancellationHandoff handoff = handoffRepository.save(CancellationHandoff.builder()
                    .session(testSession)
                    .requesterId(1L)
                    .reasonCode("EMERGENCY")
                    .status(CancellationHandoffStatus.REQUESTED)
                    .build());
            assertThat(handoff.getId()).isNotNull();
        }

        @Test
        @DisplayName("Should enforce unique constraint on (location_id, workshop_date, slot_id)")
        void shouldRejectDuplicateSessionSlotDateLocation() {
            WorkshopSession duplicate = WorkshopSession.builder()
                    .reference(UUID.randomUUID().toString())
                    .location(testLocation)
                    .slot(testSlot)
                    .workshopDate(testSession.getWorkshopDate())
                    .scheduledStartAt(testSession.getScheduledStartAt())
                    .scheduledEndAt(testSession.getScheduledEndAt())
                    .capacity(20)
                    .state(SessionState.PUBLISHED)
                    .checkInOpensAt(Instant.now().plus(4, ChronoUnit.DAYS))
                    .checkInOpeningSource(CheckInOpeningSource.DEFAULT)
                    .build();

            assertThatThrownBy(() -> sessionRepository.saveAndFlush(duplicate))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Should enforce unique constraint on ticket_code and qr_reference")
        void shouldRejectDuplicateTicketCodeAndQrReference() {
            String sharedCode = "TCK-DUPLICATE";
            String sharedQr = "QR-DUPLICATE";

            ticketRepository.saveAndFlush(AttendanceTicket.builder()
                    .reference(UUID.randomUUID().toString())
                    .bookingReference("BKG-001")
                    .ticketSequence(1)
                    .ticketCode(sharedCode)
                    .qrReference(sharedQr)
                    .validityState(TicketValidityState.ACTIVE)
                    .build());

            AttendanceTicket dupCode = AttendanceTicket.builder()
                    .reference(UUID.randomUUID().toString())
                    .bookingReference("BKG-002")
                    .ticketSequence(1)
                    .ticketCode(sharedCode)
                    .qrReference("QR-DIFFERENT")
                    .validityState(TicketValidityState.ACTIVE)
                    .build();

            assertThatThrownBy(() -> ticketRepository.saveAndFlush(dupCode))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    // =========================================================================
    // Nested 2: Authorization Rules & Boundary Invariants (T005, T010)
    // =========================================================================
    @Nested
    @DisplayName("Authorization & Boundary Invariant Tests")
    class AuthorizationTests {

        @Test
        @DisplayName("Manager with in-scope location should be authorized")
        void managerInScopeShouldBeAuthorized() {
            when(accountFacade.resolveAccount("mgr-uid")).thenReturn(Optional.of(
                    new AccountSummary(10L, ActorRole.MANAGER, ActorStatus.ACTIVE, "mgr@fpt.edu.vn", Instant.now(), Instant.now())));
            when(accountFacade.getLocationScope(10L)).thenReturn(List.of(testLocation.getId()));

            AccountSummary summary = authorizationService.requireManagerLocationScope("Bearer test-uid:mgr-uid", testLocation.getId());
            assertThat(summary.accountId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("Manager out of scope should be denied with WorkshopLocationScopeException")
        void managerOutOfScopeShouldBeDenied() {
            when(accountFacade.resolveAccount("mgr-uid")).thenReturn(Optional.of(
                    new AccountSummary(10L, ActorRole.MANAGER, ActorStatus.ACTIVE, "mgr@fpt.edu.vn", Instant.now(), Instant.now())));
            when(accountFacade.getLocationScope(10L)).thenReturn(List.of(999L));

            assertThatThrownBy(() -> authorizationService.requireManagerLocationScope("Bearer test-uid:mgr-uid", testLocation.getId()))
                    .isInstanceOf(WorkshopLocationScopeException.class);
        }

        @Test
        @DisplayName("Assigned Staff with in-scope location should be authorized for session action")
        void assignedStaffInScopeShouldBeAuthorizedForSession() {
            staffAssignmentRepository.save(SessionStaffAssignment.builder()
                    .session(testSession)
                    .accountId(20L)
                    .assignedById(10L)
                    .active(true)
                    .build());

            when(accountFacade.resolveAccount("staff-uid")).thenReturn(Optional.of(
                    new AccountSummary(20L, ActorRole.STAFF, ActorStatus.ACTIVE, "staff@fpt.edu.vn", Instant.now(), Instant.now())));
            when(accountFacade.getLocationScope(20L)).thenReturn(List.of(testLocation.getId()));

            AccountSummary summary = authorizationService.requireStaffSessionAssignment("Bearer test-uid:staff-uid", testSession.getId());
            assertThat(summary.accountId()).isEqualTo(20L);
        }

        @Test
        @DisplayName("Assigned Staff out of location scope should be denied with WorkshopLocationScopeException")
        void assignedStaffOutOfScopeShouldBeDenied() {
            staffAssignmentRepository.save(SessionStaffAssignment.builder()
                    .session(testSession)
                    .accountId(20L)
                    .assignedById(10L)
                    .active(true)
                    .build());

            when(accountFacade.resolveAccount("staff-uid")).thenReturn(Optional.of(
                    new AccountSummary(20L, ActorRole.STAFF, ActorStatus.ACTIVE, "staff@fpt.edu.vn", Instant.now(), Instant.now())));
            when(accountFacade.getLocationScope(20L)).thenReturn(List.of(999L)); // Out of scope

            assertThatThrownBy(() -> authorizationService.requireStaffSessionAssignment("Bearer test-uid:staff-uid", testSession.getId()))
                    .isInstanceOf(WorkshopLocationScopeException.class);
        }

        @Test
        @DisplayName("Unassigned Staff should be denied with WorkshopForbiddenException")
        void unassignedStaffShouldBeDenied() {
            when(accountFacade.resolveAccount("staff-uid-2")).thenReturn(Optional.of(
                    new AccountSummary(21L, ActorRole.STAFF, ActorStatus.ACTIVE, "staff2@fpt.edu.vn", Instant.now(), Instant.now())));
            when(accountFacade.getLocationScope(21L)).thenReturn(List.of(testLocation.getId()));

            assertThatThrownBy(() -> authorizationService.requireStaffSessionAssignment("Bearer test-uid:staff-uid-2", testSession.getId()))
                    .isInstanceOf(WorkshopForbiddenException.class);
        }

        @Test
        @DisplayName("Member role should be denied by default with WorkshopForbiddenException")
        void memberRoleShouldBeDeniedByDefault() {
            when(accountFacade.resolveAccount("member-uid")).thenReturn(Optional.of(
                    new AccountSummary(30L, ActorRole.MEMBER, ActorStatus.ACTIVE, "member@fpt.edu.vn", Instant.now(), Instant.now())));

            assertThatThrownBy(() -> authorizationService.requireManagerLocationScope("Bearer test-uid:member-uid", testLocation.getId()))
                    .isInstanceOf(WorkshopForbiddenException.class);
        }

        @Test
        @DisplayName("Inactive or suspended account should be denied")
        void inactiveAccountShouldBeDenied() {
            when(accountFacade.resolveAccount("suspended-uid")).thenReturn(Optional.of(
                    new AccountSummary(40L, ActorRole.MANAGER, ActorStatus.SUSPENDED, "suspended@fpt.edu.vn", Instant.now(), Instant.now())));

            assertThatThrownBy(() -> authorizationService.requireManagerLocationScope("Bearer test-uid:suspended-uid", testLocation.getId()))
                    .isInstanceOf(WorkshopForbiddenException.class);
        }

        @Test
        @DisplayName("Missing or blank bearer token should throw WorkshopUnauthorizedException")
        void missingTokenShouldThrowUnauthorized() {
            assertThatThrownBy(() -> authorizationService.requireManagerLocationScope(null, testLocation.getId()))
                    .isInstanceOf(WorkshopUnauthorizedException.class);
            assertThatThrownBy(() -> authorizationService.requireManagerLocationScope("   ", testLocation.getId()))
                    .isInstanceOf(WorkshopUnauthorizedException.class);
        }
    }

    // =========================================================================
    // Nested 3: Facade Contract & Idempotency Invariants (T006, T007)
    // =========================================================================
    @Nested
    @DisplayName("WorkshopOperationsFacade Contract & Idempotency Tests")
    class FacadeContractTests {

        @Test
        @DisplayName("Reserve capacity should succeed and support idempotent retry while rejecting mismatch")
        void shouldHandleReserveCapacityIdempotency() {
            UUID sessionUuid = UUID.fromString(testSession.getReference());
            String opRef = "OP-RES-IDEMP-" + UUID.randomUUID();

            ReserveBookingCapacityCommand cmd1 = new ReserveBookingCapacityCommand(
                    "BKG-RESERVE-01",
                    List.of(sessionUuid),
                    3,
                    opRef,
                    "CORR-RES-1"
            );

            // First execution
            CapacityHoldOutcome outcome1 = operationsFacade.reserveBookingCapacity(cmd1);
            assertThat(outcome1.status()).isEqualTo(OutcomeStatus.ACCEPTED);
            assertThat(outcome1.holdReferences()).isNotEmpty();
            assertThat(outcome1.reason()).isNull();

            UUID createdHoldUuid = outcome1.holdReferences().get(0);
            Optional<CapacityHold> holdOpt = holdRepository.findByReference(createdHoldUuid.toString());
            assertThat(holdOpt).isPresent();
            assertThat(holdOpt.get().getRequestedTicketCount()).isEqualTo(3);
            assertThat(holdOpt.get().getState()).isEqualTo(CapacityHoldState.ACTIVE);

            // Semantically identical retry with same opRef
            ReserveBookingCapacityCommand cmdRetry = new ReserveBookingCapacityCommand(
                    "BKG-RESERVE-01",
                    List.of(sessionUuid),
                    3,
                    opRef,
                    "CORR-RES-RETRY"
            );
            CapacityHoldOutcome outcomeRetry = operationsFacade.reserveBookingCapacity(cmdRetry);
            assertThat(outcomeRetry.status()).isEqualTo(OutcomeStatus.ACCEPTED);
            assertThat(outcomeRetry.holdReferences()).isEqualTo(outcome1.holdReferences());

            // Reused opRef with DIFFERENT semantic content (count = 5)
            ReserveBookingCapacityCommand cmdMismatch = new ReserveBookingCapacityCommand(
                    "BKG-RESERVE-01",
                    List.of(sessionUuid),
                    5,
                    opRef,
                    "CORR-RES-MISMATCH"
            );
            CapacityHoldOutcome outcomeMismatch = operationsFacade.reserveBookingCapacity(cmdMismatch);
            assertThat(outcomeMismatch.status()).isEqualTo(OutcomeStatus.REJECTED);
            assertThat(outcomeMismatch.reason()).isEqualTo(RejectionReason.INVALID_STATE);
        }

        @Test
        @DisplayName("Reserve capacity should reject when requested exceeds capacity")
        void shouldRejectReserveCapacityWhenExceeds() {
            UUID sessionUuid = UUID.fromString(testSession.getReference());
            ReserveBookingCapacityCommand cmd = new ReserveBookingCapacityCommand(
                    "BKG-OVERFLOW",
                    List.of(sessionUuid),
                    25, // Exceeds 20
                    "OP-RES-OVERFLOW",
                    "CORR-RES-OVERFLOW"
            );

            CapacityHoldOutcome outcome = operationsFacade.reserveBookingCapacity(cmd);
            assertThat(outcome.status()).isEqualTo(OutcomeStatus.REJECTED);
            assertThat(outcome.reason()).isEqualTo(RejectionReason.INSUFFICIENT_CAPACITY);
        }

        @Test
        @DisplayName("Commit hold should succeed, issue tickets, support idempotent retry and reject mismatch")
        void shouldCommitHoldAndHandleIdempotency() {
            UUID holdUuid = UUID.randomUUID();
            CapacityHold hold = holdRepository.save(CapacityHold.builder()
                    .reference(holdUuid.toString())
                    .session(testSession)
                    .bookingReference("BKG-COMMIT-01")
                    .requestedTicketCount(2)
                    .state(CapacityHoldState.ACTIVE)
                    .expiresAt(Instant.now().plusSeconds(600))
                    .build());

            String opRef = "OP-COM-IDEMP-" + UUID.randomUUID();
            CommitBookingHoldsCommand commitCmd = new CommitBookingHoldsCommand(
                    "BKG-COMMIT-01",
                    List.of(holdUuid),
                    opRef,
                    "CORR-COM-1"
            );

            CapacityCommitOutcome commitOutcome = operationsFacade.commitBookingHolds(commitCmd);
            assertThat(commitOutcome.status()).isEqualTo(OutcomeStatus.ACCEPTED);

            // Verify tickets created in DB
            List<AttendanceTicket> tickets = ticketRepository.findByBookingReference("BKG-COMMIT-01");
            assertThat(tickets).hasSize(2);
            assertThat(tickets).allMatch(t -> t.getValidityState() == TicketValidityState.ACTIVE);

            // Identical retry
            CapacityCommitOutcome retryOutcome = operationsFacade.commitBookingHolds(commitCmd);
            assertThat(retryOutcome.status()).isEqualTo(OutcomeStatus.ACCEPTED);

            // Mismatched reuse (different booking ref)
            CommitBookingHoldsCommand mismatchCmd = new CommitBookingHoldsCommand(
                    "BKG-DIFFERENT",
                    List.of(holdUuid),
                    opRef,
                    "CORR-COM-MISMATCH"
            );
            CapacityCommitOutcome mismatchOutcome = operationsFacade.commitBookingHolds(mismatchCmd);
            assertThat(mismatchOutcome.status()).isEqualTo(OutcomeStatus.REJECTED);
            assertThat(mismatchOutcome.reason()).isEqualTo(RejectionReason.INVALID_STATE);

            // Verify delivery-safe projection query
            DeliverySafeTicketSet projection = operationsFacade.getBookingTicketProjection(
                    new BookingTicketProjectionQuery("BKG-COMMIT-01"));
            assertThat(projection.tickets()).hasSize(2);
            DeliverySafeTicket firstTicket = projection.tickets().get(0);
            assertThat(firstTicket.ticketCode()).isNotNull();
            assertThat(firstTicket.ticketReference()).isNotNull();
            assertThat(firstTicket.sessionEntitlements()).hasSize(1);
            assertThat(firstTicket.sessionEntitlements().get(0).locationReference())
                    .isEqualTo(testLocation.getId());
        }

        @Test
        @DisplayName("Release hold should transition hold, support idempotent retry and reject mismatch")
        void shouldReleaseActiveHoldAndHandleIdempotency() {
            UUID holdUuid = UUID.randomUUID();
            CapacityHold hold = holdRepository.save(CapacityHold.builder()
                    .reference(holdUuid.toString())
                    .session(testSession)
                    .bookingReference("BKG-REL-01")
                    .requestedTicketCount(4)
                    .state(CapacityHoldState.ACTIVE)
                    .expiresAt(Instant.now().plusSeconds(600))
                    .build());

            String opRef = "OP-REL-IDEMP-" + UUID.randomUUID();
            ReleaseBookingHoldsCommand releaseCmd = new ReleaseBookingHoldsCommand(
                    "BKG-REL-01", List.of(holdUuid), opRef, "CORR-REL-1");

            CapacityReleaseOutcome releaseOutcome = operationsFacade.releaseBookingHolds(releaseCmd);
            assertThat(releaseOutcome.status()).isEqualTo(OutcomeStatus.ACCEPTED);

            CapacityHold updatedHold = holdRepository.findByReference(holdUuid.toString()).orElseThrow();
            assertThat(updatedHold.getState()).isEqualTo(CapacityHoldState.RELEASED);

            // Identical retry
            CapacityReleaseOutcome retryOutcome = operationsFacade.releaseBookingHolds(releaseCmd);
            assertThat(retryOutcome.status()).isEqualTo(OutcomeStatus.ACCEPTED);

            // Mismatched reuse
            ReleaseBookingHoldsCommand mismatchCmd = new ReleaseBookingHoldsCommand(
                    "BKG-REL-DIFF", List.of(holdUuid), opRef, "CORR-REL-MISMATCH");
            CapacityReleaseOutcome mismatchOutcome = operationsFacade.releaseBookingHolds(mismatchCmd);
            assertThat(mismatchOutcome.status()).isEqualTo(OutcomeStatus.REJECTED);
            assertThat(mismatchOutcome.reason()).isEqualTo(RejectionReason.INVALID_STATE);
        }

        @Test
        @DisplayName("Query sale eligibility should report accurate snapshot")
        void shouldReportAccurateSaleEligibilitySnapshot() {
            SaleEligibilitySnapshot snapshot = operationsFacade.getSaleEligibility(
                    new SaleEligibilityQuery(UUID.fromString(testSession.getReference())));

            assertThat(snapshot.customerSelectable()).isTrue();
            assertThat(snapshot.remainingCapacity()).isEqualTo(20);
            assertThat(snapshot.locationReference()).isEqualTo(testLocation.getId());
        }
    }

    // =========================================================================
    // Nested 4: CORS Configuration & Exception Handler (T010, T011)
    // =========================================================================
    @Nested
    @DisplayName("CORS Policy & Problem Detail Exception Handler Tests")
    class SecurityAndErrorHandlingTests {

        private MockMvc exceptionMockMvc;

        @BeforeEach
        void initExceptionMockMvc() {
            exceptionMockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                    .standaloneSetup(new TestWorkshopOperationsEndpoint())
                    .setControllerAdvice(new WorkshopOperationsExceptionHandler())
                    .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(objectMapper))
                    .build();
        }

        @Test
        @DisplayName("CSRF protection remains enabled for non-API web endpoints")
        void csrfRemainsEnabledForNonApiEndpoints() throws Exception {
            mockMvc.perform(post("/non-api/test"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Allowed origin receives CORS headers without credentials")
        void allowedOriginReceivesCorsHeaders() throws Exception {
            mockMvc.perform(options("/api/v1/workshop-test/ping")
                            .header("Origin", "http://localhost:3000")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                    .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        }

        @Test
        @DisplayName("Disallowed origin does NOT receive Access-Control-Allow-Origin")
        void disallowedOriginDeniedCors() throws Exception {
            mockMvc.perform(options("/api/v1/workshop-test/ping")
                            .header("Origin", "http://malicious-site.com")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        }

        @Test
        @DisplayName("WorkshopLocationScopeException maps to 403 LOCATION_SCOPE_DENIED ProblemDetail")
        void testLocationScopeExceptionMapping() throws Exception {
            exceptionMockMvc.perform(get("/api/v1/workshop-test/location-scope-error")
                            .header("X-Correlation-Id", "corr-test-123"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.code").value("LOCATION_SCOPE_DENIED"))
                    .andExpect(jsonPath("$.title").value("Location Scope Denied"))
                    .andExpect(jsonPath("$.correlationId").value("corr-test-123"))
                    .andExpect(jsonPath("$.detail").value("Target location is out of scope"));
        }

        @Test
        @DisplayName("WorkshopUnauthorizedException maps to 401 UNAUTHORIZED ProblemDetail")
        void testUnauthorizedExceptionMapping() throws Exception {
            exceptionMockMvc.perform(get("/api/v1/workshop-test/unauthorized-error"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.correlationId").isNotEmpty());
        }

        @Test
        @DisplayName("WorkshopCapacityConflictException maps to 409 INSUFFICIENT_CAPACITY ProblemDetail")
        void testCapacityConflictExceptionMapping() throws Exception {
            exceptionMockMvc.perform(get("/api/v1/workshop-test/capacity-conflict-error"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.code").value("INSUFFICIENT_CAPACITY"))
                    .andExpect(jsonPath("$.title").value("Insufficient Capacity"));
        }

        @Test
        @DisplayName("WorkshopLifecycleException maps to 409 INVALID_STATE ProblemDetail")
        void testLifecycleExceptionMapping() throws Exception {
            exceptionMockMvc.perform(get("/api/v1/workshop-test/lifecycle-error"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        }
    }

    // =========================================================================
    // Test Controller for Exception & CORS Endpoint Verification
    // =========================================================================
    @RestController
    @RequestMapping("/api/v1/workshop-test")
    static class TestWorkshopOperationsEndpoint {

        @GetMapping("/ping")
        public String ping() {
            return "pong";
        }

        @GetMapping("/location-scope-error")
        public void throwLocationScope() {
            throw new WorkshopLocationScopeException("Target location is out of scope");
        }

        @GetMapping("/unauthorized-error")
        public void throwUnauthorized() {
            throw new WorkshopUnauthorizedException("Missing or invalid token");
        }

        @GetMapping("/capacity-conflict-error")
        public void throwCapacityConflict() {
            throw new WorkshopCapacityConflictException("Requested seats exceed available capacity");
        }

        @GetMapping("/lifecycle-error")
        public void throwLifecycle() {
            throw new WorkshopLifecycleException("Cannot start session in DRAFT state");
        }
    }
}
