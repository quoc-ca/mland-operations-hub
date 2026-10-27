package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.CheckInService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T033: Integration tests for Check-In Service and REST Handlers against real MySQL/Flyway.
 * Verifies:
 * 1. Concurrent scan of same ticket guarantees exactly one CHECKED_IN and one ALREADY_CHECKED_IN with original timestamp;
 * 2. Duplicate sequential scan returns 200 OK with ALREADY_CHECKED_IN and original checkedInAt without duplicate action;
 * 3. Wrong location or session scan rejected with 409 TICKET_CONFLICT;
 * 4. Early scan before check_in_opens_at rejected with 409 CHECK_IN_NOT_OPEN;
 * 5. Unauthorized, unassigned or inactive actors rejected with 401/403;
 * 6. Booking code lookup returns eligible candidate tickets only without checking in; generic 404 when not found;
 * 7. Silverclay cross-session entitlement allows independent check-in across both sessions for 1 ticket;
 * 8. Non-disclosure boundary: no buyer email, phone, or contact PII in scan or lookup responses.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "workshop.operations.auth.allow-test-tokens=true"
})
public class CheckInIT {

    private static final Logger log = LoggerFactory.getLogger(CheckInIT.class);

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
    private WorkshopOperationsFacade operationsFacade;

    @Autowired
    private CheckInService checkInService;

    @MockBean
    private AccountFacade accountFacade;

    @MockBean
    private TokenVerificationService tokenVerificationService;

    private static final String STAFF_A_TOKEN = "Bearer test-uid:staff-a";
    private static final String STAFF_UNASSIGNED_TOKEN = "Bearer test-uid:staff-unassigned";
    private static final String STAFF_WRONG_LOC_TOKEN = "Bearer test-uid:staff-wrong-loc";
    private static final String STAFF_INACTIVE_TOKEN = "Bearer test-uid:staff-inactive";
    private static final String MGR_A_TOKEN = "Bearer test-uid:mgr-a";

    private WorkshopLocation locationA;
    private WorkshopLocation locationB;
    private WorkshopSlot slotMorning;
    private WorkshopSlot slotAfternoon;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbcTemplate.execute("TRUNCATE TABLE facade_command_idempotency_records");
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
                .displayName("District 1 Workshop Hub")
                .addressSnapshot("123 Le Loi, D1, HCMC")
                .active(true)
                .build());

        locationB = locationRepository.save(WorkshopLocation.builder()
                .code("LOC-HCM-02")
                .displayName("District 2 Workshop Hub")
                .addressSnapshot("456 Thao Dien, D2, HCMC")
                .active(true)
                .build());

        slotMorning = slotRepository.save(WorkshopSlot.builder()
                .reference(UUID.randomUUID().toString())
                .location(locationA)
                .label("Morning Slot")
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(12, 0))
                .defaultCapacity(10)
                .active(true)
                .build());

        slotAfternoon = slotRepository.save(WorkshopSlot.builder()
                .reference(UUID.randomUUID().toString())
                .location(locationA)
                .label("Afternoon Slot")
                .startTime(LocalTime.of(13, 0))
                .endTime(LocalTime.of(16, 0))
                .defaultCapacity(10)
                .active(true)
                .build());

        // Setup Staff A (accountId=101) with Location A scope
        when(accountFacade.resolveAccount("staff-a")).thenReturn(Optional.of(
                new AccountSummary(101L, ActorRole.STAFF, ActorStatus.ACTIVE, "staffa@mland.vn", Instant.now(), Instant.now())));
        when(accountFacade.getAccountStatus(101L)).thenReturn(Optional.of(ActorStatus.ACTIVE));
        when(accountFacade.getLocationScope(101L)).thenReturn(List.of(locationA.getId()));

        // Setup Staff Unassigned (accountId=102) with Location A scope
        when(accountFacade.resolveAccount("staff-unassigned")).thenReturn(Optional.of(
                new AccountSummary(102L, ActorRole.STAFF, ActorStatus.ACTIVE, "staffu@mland.vn", Instant.now(), Instant.now())));
        when(accountFacade.getAccountStatus(102L)).thenReturn(Optional.of(ActorStatus.ACTIVE));
        when(accountFacade.getLocationScope(102L)).thenReturn(List.of(locationA.getId()));

        // Setup Staff Wrong Loc (accountId=103) with Location B scope only
        when(accountFacade.resolveAccount("staff-wrong-loc")).thenReturn(Optional.of(
                new AccountSummary(103L, ActorRole.STAFF, ActorStatus.ACTIVE, "staffw@mland.vn", Instant.now(), Instant.now())));
        when(accountFacade.getAccountStatus(103L)).thenReturn(Optional.of(ActorStatus.ACTIVE));
        when(accountFacade.getLocationScope(103L)).thenReturn(List.of(locationB.getId()));

        // Setup Staff Inactive (accountId=104) with SUSPENDED status
        when(accountFacade.resolveAccount("staff-inactive")).thenReturn(Optional.of(
                new AccountSummary(104L, ActorRole.STAFF, ActorStatus.SUSPENDED, "staffi@mland.vn", Instant.now(), Instant.now())));
        when(accountFacade.getAccountStatus(104L)).thenReturn(Optional.of(ActorStatus.SUSPENDED));
        when(accountFacade.getLocationScope(104L)).thenReturn(List.of(locationA.getId()));

        // Setup Manager A (accountId=10) with Location A & B scope
        when(accountFacade.resolveAccount("mgr-a")).thenReturn(Optional.of(
                new AccountSummary(10L, ActorRole.MANAGER, ActorStatus.ACTIVE, "mgra@mland.vn", Instant.now(), Instant.now())));
        when(accountFacade.getAccountStatus(10L)).thenReturn(Optional.of(ActorStatus.ACTIVE));
        when(accountFacade.getLocationScope(10L)).thenReturn(List.of(locationA.getId(), locationB.getId()));

        // TokenVerificationService mocks
        when(tokenVerificationService.verify("test-uid:staff-a")).thenReturn(
                new VerifiedIdentity("staff-a", "staffa@mland.vn", true, Instant.now()));
        when(tokenVerificationService.verify("test-uid:staff-unassigned")).thenReturn(
                new VerifiedIdentity("staff-unassigned", "staffu@mland.vn", true, Instant.now()));
        when(tokenVerificationService.verify("test-uid:staff-wrong-loc")).thenReturn(
                new VerifiedIdentity("staff-wrong-loc", "staffw@mland.vn", true, Instant.now()));
        when(tokenVerificationService.verify("test-uid:staff-inactive")).thenReturn(
                new VerifiedIdentity("staff-inactive", "staffi@mland.vn", true, Instant.now()));
        when(tokenVerificationService.verify("test-uid:mgr-a")).thenReturn(
                new VerifiedIdentity("mgr-a", "mgra@mland.vn", true, Instant.now()));

        when(accountFacade.resolveActor("staff-a", true)).thenReturn(Optional.of(
                new CurrentAccountActor(101L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(locationA.getId()))));
        when(accountFacade.resolveActor("staff-unassigned", true)).thenReturn(Optional.of(
                new CurrentAccountActor(102L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(locationA.getId()))));
        when(accountFacade.resolveActor("staff-wrong-loc", true)).thenReturn(Optional.of(
                new CurrentAccountActor(103L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(locationB.getId()))));
        when(accountFacade.resolveActor("staff-inactive", true)).thenReturn(Optional.of(
                new CurrentAccountActor(104L, ActorRole.STAFF, ActorStatus.SUSPENDED, false, List.of(locationA.getId()))));
        when(accountFacade.resolveActor("mgr-a", true)).thenReturn(Optional.of(
                new CurrentAccountActor(10L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of(locationA.getId(), locationB.getId()))));
    }

    private WorkshopSession createPublishedSession(WorkshopLocation loc, WorkshopSlot slot, Instant checkInOpensAt) {
        LocalDate date = LocalDate.now().plusDays(2);
        WorkshopSession session = sessionRepository.save(WorkshopSession.builder()
                .reference(UUID.randomUUID().toString())
                .location(loc)
                .slot(slot)
                .workshopDate(date)
                .scheduledStartAt(date.atTime(slot.getStartTime()).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant())
                .scheduledEndAt(date.atTime(slot.getEndTime()).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant())
                .checkInOpensAt(checkInOpensAt != null ? checkInOpensAt : Instant.now().minus(30, ChronoUnit.MINUTES))
                .checkInOpeningSource(CheckInOpeningSource.DEFAULT)
                .capacity(10)
                .state(SessionState.PUBLISHED)
                .build());

        // Assign Staff A to session
        staffAssignmentRepository.save(SessionStaffAssignment.builder()
                .session(session)
                .accountId(101L)
                .assignedById(10L)
                .assignedAt(Instant.now())
                .active(true)
                .build());

        return session;
    }

    private DeliverySafeTicketSet issueTicketsForBooking(String bookingRef, List<WorkshopSession> sessions, int seats) {
        List<UUID> sessionRefs = sessions.stream()
                .map(s -> UUID.fromString(s.getReference()))
                .toList();

        CapacityHoldOutcome hold = operationsFacade.reserveBookingCapacity(
                new ReserveBookingCapacityCommand(bookingRef, sessionRefs, seats, "op-hold-" + UUID.randomUUID(), "corr-hold"));
        assertThat(hold.status()).isEqualTo(OutcomeStatus.ACCEPTED);

        CapacityCommitOutcome commit = operationsFacade.commitBookingHolds(
                new CommitBookingHoldsCommand(bookingRef, hold.holdReferences(), "op-comm-" + UUID.randomUUID(), "corr-comm"));
        assertThat(commit.status()).isEqualTo(OutcomeStatus.ACCEPTED);

        return operationsFacade.getBookingTicketProjection(new BookingTicketProjectionQuery(bookingRef));
    }

    private String getQrReference(DeliverySafeTicket ticket) {
        return ticketRepository.findByTicketCode(ticket.ticketCode())
                .orElseThrow()
                .getQrReference();
    }

    // =========================================================================
    // 1. Concurrent Scan of Same Ticket: Atomic Check-In & Idempotent Original Time
    // =========================================================================

    @Test
    @DisplayName("T033-1: Concurrent scan of same ticket results in exactly one CHECKED_IN and one ALREADY_CHECKED_IN with original time")
    void concurrentScans_sameTicket_guaranteesAtomicCheckInAndOriginalTimeOnDuplicate() throws Exception {
        WorkshopSession session = createPublishedSession(locationA, slotMorning, null);
        String bookingRef = "BK-CONC-" + UUID.randomUUID().toString().substring(0, 8);
        DeliverySafeTicketSet ticketSet = issueTicketsForBooking(bookingRef, List.of(session), 1);
        DeliverySafeTicket ticket = ticketSet.tickets().get(0);
        String qrToken = getQrReference(ticket);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        CheckInRequest request = CheckInRequest.builder()
                .qrToken(qrToken)
                .build();

        List<Future<CheckInResult>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                return checkInService.checkIn(STAFF_A_TOKEN, session.getReference(), request);
            }));
        }

        // Release threads simultaneously
        startLatch.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);

        CheckInResult res1 = futures.get(0).get(5, TimeUnit.SECONDS);
        CheckInResult res2 = futures.get(1).get(5, TimeUnit.SECONDS);
        executor.shutdown();

        List<CheckInResultType> types = List.of(res1.getCheckInResult(), res2.getCheckInResult());
        assertThat(types).containsExactlyInAnyOrder(CheckInResultType.CHECKED_IN, CheckInResultType.ALREADY_CHECKED_IN);

        // The ALREADY_CHECKED_IN result must contain the exact same checkedInAt timestamp as the CHECKED_IN result
        assertThat(res1.getCheckedInAt()).isNotNull();
        assertThat(res2.getCheckedInAt()).isNotNull();
        assertThat(res1.getCheckedInAt()).isEqualTo(res2.getCheckedInAt());

        // In DB: exactly one AttendanceAction record exists
        List<AttendanceAction> actions = attendanceActionRepository.findAll();
        assertThat(actions).hasSize(1);
        assertThat(actions.get(0).getActionType()).isEqualTo(AttendanceActionType.CHECK_IN);
        assertThat(actions.get(0).getActorId()).isEqualTo(101L);

        log.info("Concurrent scan test passed: 1 CHECKED_IN, 1 ALREADY_CHECKED_IN with identical timestamp.");
    }

    // =========================================================================
    // 2. Sequential Duplicate Scan Returns 200 OK with ALREADY_CHECKED_IN
    // =========================================================================

    @Test
    @DisplayName("T033-2: Sequential duplicate scan returns 200 OK with ALREADY_CHECKED_IN and original time without second action")
    void duplicateScan_sequential_returns200AlreadyCheckedInWithOriginalTime() throws Exception {
        WorkshopSession session = createPublishedSession(locationA, slotMorning, null);
        String bookingRef = "BK-SEQ-" + UUID.randomUUID().toString().substring(0, 8);
        DeliverySafeTicketSet ticketSet = issueTicketsForBooking(bookingRef, List.of(session), 1);
        DeliverySafeTicket ticket = ticketSet.tickets().get(0);
        String qrToken = getQrReference(ticket);

        CheckInRequest qrRequest = CheckInRequest.builder()
                .qrToken(qrToken)
                .build();

        // 1. First scan via REST
        MvcResult firstResult = mockMvc.perform(post("/api/v1/workshop-sessions/{sessionId}/check-ins", session.getReference())
                        .header("Authorization", STAFF_A_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(qrRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketCode").value(ticket.ticketCode()))
                .andExpect(jsonPath("$.checkInResult").value("CHECKED_IN"))
                .andExpect(jsonPath("$.checkedInAt").isNotEmpty())
                .andReturn();

        JsonNode firstNode = objectMapper.readTree(firstResult.getResponse().getContentAsString());
        String originalTime = firstNode.get("checkedInAt").asText();

        Thread.sleep(50);

        // 2. Second scan via REST (using qrToken)
        mockMvc.perform(post("/api/v1/workshop-sessions/{sessionId}/check-ins", session.getReference())
                        .header("Authorization", STAFF_A_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(qrRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketCode").value(ticket.ticketCode()))
                .andExpect(jsonPath("$.checkInResult").value("ALREADY_CHECKED_IN"))
                .andExpect(jsonPath("$.checkedInAt").value(originalTime));

        // 3. Third scan via REST (using exact ticketCode instead of qrToken)
        CheckInRequest codeRequest = CheckInRequest.builder()
                .ticketCode(ticket.ticketCode())
                .build();

        mockMvc.perform(post("/api/v1/workshop-sessions/{sessionId}/check-ins", session.getReference())
                        .header("Authorization", STAFF_A_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(codeRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketCode").value(ticket.ticketCode()))
                .andExpect(jsonPath("$.checkInResult").value("ALREADY_CHECKED_IN"))
                .andExpect(jsonPath("$.checkedInAt").value(originalTime));

        // Check DB: exactly one AttendanceAction created
        List<AttendanceAction> actions = attendanceActionRepository.findAll();
        assertThat(actions).hasSize(1);
    }

    // =========================================================================
    // 3. Wrong Location or Session Scan Rejected with 409 TICKET_CONFLICT
    // =========================================================================

    @Test
    @DisplayName("T033-3: Scan ticket at wrong session or location is rejected with 409 TICKET_CONFLICT")
    void wrongLocationOrSession_rejectedWithConflict() throws Exception {
        WorkshopSession sessionA = createPublishedSession(locationA, slotMorning, null);

        WorkshopSlot slotB = slotRepository.save(WorkshopSlot.builder()
                .reference(UUID.randomUUID().toString())
                .location(locationB)
                .label("Slot Loc B")
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(12, 0))
                .defaultCapacity(10)
                .active(true)
                .build());
        WorkshopSession sessionB = createPublishedSession(locationB, slotB, null);
        operationalLeadRepository.save(SessionOperationalLead.builder()
                .session(sessionB)
                .managerAccountId(10L)
                .reasonCode("INITIAL_LEAD")
                .assignedAt(Instant.now())
                .active(true)
                .build());

        String bookingRef = "BK-LOCA-" + UUID.randomUUID().toString().substring(0, 8);
        DeliverySafeTicketSet ticketSet = issueTicketsForBooking(bookingRef, List.of(sessionA), 1);
        DeliverySafeTicket ticket = ticketSet.tickets().get(0);
        String qrToken = getQrReference(ticket);

        // Attempt check-in of ticket belonging to Session A at Session B
        CheckInRequest req = CheckInRequest.builder()
                .qrToken(qrToken)
                .build();

        mockMvc.perform(post("/api/v1/workshop-sessions/{sessionId}/check-ins", sessionB.getReference())
                        .header("Authorization", MGR_A_TOKEN) // Manager A has scope and is lead for sessionB
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TICKET_CONFLICT"));

        // Verify no attendance action was recorded
        assertThat(attendanceActionRepository.findAll()).isEmpty();
    }

    // =========================================================================
    // 4. Early Scan Before Check-In Opens Rejected with 409 INVALID_STATE
    // =========================================================================

    @Test
    @DisplayName("T033-4: Early scan before check_in_opens_at is rejected with 409 INVALID_STATE")
    void earlyScan_beforeOpeningTime_rejectedWithInvalidState() throws Exception {
        // Create session with check-in opening 2 hours in the future
        Instant futureOpening = Instant.now().plus(2, ChronoUnit.HOURS);
        WorkshopSession session = createPublishedSession(locationA, slotMorning, futureOpening);

        String bookingRef = "BK-EARLY-" + UUID.randomUUID().toString().substring(0, 8);
        DeliverySafeTicketSet ticketSet = issueTicketsForBooking(bookingRef, List.of(session), 1);
        DeliverySafeTicket ticket = ticketSet.tickets().get(0);
        String qrToken = getQrReference(ticket);

        CheckInRequest req = CheckInRequest.builder()
                .qrToken(qrToken)
                .build();

        mockMvc.perform(post("/api/v1/workshop-sessions/{sessionId}/check-ins", session.getReference())
                        .header("Authorization", STAFF_A_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        assertThat(attendanceActionRepository.findAll()).isEmpty();
    }

    // =========================================================================
    // 5. Inactive or Unauthorized Actor Rejected with 401/403
    // =========================================================================

    @Test
    @DisplayName("T033-5: Inactive, unassigned, or out-of-scope actor is rejected with 401/403")
    void unauthorizedOrInactiveActor_rejectedWithForbiddenOrUnauthorized() throws Exception {
        WorkshopSession session = createPublishedSession(locationA, slotMorning, null);
        String bookingRef = "BK-AUTH-" + UUID.randomUUID().toString().substring(0, 8);
        DeliverySafeTicketSet ticketSet = issueTicketsForBooking(bookingRef, List.of(session), 1);
        DeliverySafeTicket ticket = ticketSet.tickets().get(0);
        String qrToken = getQrReference(ticket);

        CheckInRequest req = CheckInRequest.builder()
                .qrToken(qrToken)
                .build();

        // 1. Missing Authorization header -> 401
        mockMvc.perform(post("/api/v1/workshop-sessions/{sessionId}/check-ins", session.getReference())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());

        // 2. Unassigned staff member -> 403 FORBIDDEN
        mockMvc.perform(post("/api/v1/workshop-sessions/{sessionId}/check-ins", session.getReference())
                        .header("Authorization", STAFF_UNASSIGNED_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // 3. Staff outside location scope -> 403 LOCATION_SCOPE_DENIED
        staffAssignmentRepository.save(SessionStaffAssignment.builder()
                .session(session)
                .accountId(103L)
                .assignedById(10L)
                .assignedAt(Instant.now())
                .active(true)
                .build());

        mockMvc.perform(post("/api/v1/workshop-sessions/{sessionId}/check-ins", session.getReference())
                        .header("Authorization", STAFF_WRONG_LOC_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LOCATION_SCOPE_DENIED"));

        // 4. Inactive actor -> 403 ACCOUNT_SUSPENDED
        mockMvc.perform(post("/api/v1/workshop-sessions/{sessionId}/check-ins", session.getReference())
                        .header("Authorization", STAFF_INACTIVE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
    }

    // =========================================================================
    // 6. Booking Code Lookup Returns Eligible Candidates Only or Generic 404
    // =========================================================================

    @Test
    @DisplayName("T033-6: Booking code lookup returns candidates without checking in; generic 404 if none eligible")
    void bookingCodeLookup_returnsOnlyEligibleCandidates_orGeneric404() throws Exception {
        WorkshopSession session = createPublishedSession(locationA, slotMorning, null);
        String bookingRef = "BK-LOOKUP-" + UUID.randomUUID().toString().substring(0, 8);
        DeliverySafeTicketSet ticketSet = issueTicketsForBooking(bookingRef, List.of(session), 2);

        // 1. Valid lookup returns exactly 2 ticket candidates for this session
        mockMvc.perform(get("/api/v1/workshop-sessions/{sessionId}/booking-lookups/{bookingCode}",
                        session.getReference(), bookingRef)
                        .header("Authorization", STAFF_A_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].ticketCode").isNotEmpty())
                .andExpect(jsonPath("$[0].validityState").value("ACTIVE"))
                .andExpect(jsonPath("$[0].attendanceState").value("NOT_CHECKED_IN"))
                .andExpect(jsonPath("$[0].checkedInAt").isEmpty());

        // Verify lookup did NOT check in the ticket
        List<AttendanceAction> actions = attendanceActionRepository.findAll();
        assertThat(actions).isEmpty();

        // 2. Unknown booking code returns generic 404
        mockMvc.perform(get("/api/v1/workshop-sessions/{sessionId}/booking-lookups/{bookingCode}",
                        session.getReference(), "BK-NONEXISTENT")
                        .header("Authorization", STAFF_A_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("No eligible tickets found for the specified booking code and session"));
    }

    // =========================================================================
    // 7. Silverclay Cross-Session Entitlement: Independent Check-In Across Both Sessions
    // =========================================================================

    @Test
    @DisplayName("T033-7: Silverclay cross-session entitlement allows 1 ticket to check in at Session 1 and Session 2 independently")
    void silverclayCrossSessionEntitlement_independentCheckInAcrossBothSessions() throws Exception {
        // Create 2 published sessions at Location A
        WorkshopSession session1 = createPublishedSession(locationA, slotMorning, null);
        WorkshopSession session2 = createPublishedSession(locationA, slotAfternoon, null);

        // Silverclay booking reserves 1 seat in session1 and 1 seat in session2
        String bookingRef = "BK-SILVER-" + UUID.randomUUID().toString().substring(0, 8);
        DeliverySafeTicketSet ticketSet = issueTicketsForBooking(bookingRef, List.of(session1, session2), 1);

        assertThat(ticketSet.tickets()).hasSize(1);
        DeliverySafeTicket ticket = ticketSet.tickets().get(0);
        assertThat(ticket.sessionEntitlements()).hasSize(2);
        String qrToken = getQrReference(ticket);

        CheckInRequest req = CheckInRequest.builder()
                .qrToken(qrToken)
                .build();

        // 1. Check in at Session 1 -> succeeds
        mockMvc.perform(post("/api/v1/workshop-sessions/{sessionId}/check-ins", session1.getReference())
                        .header("Authorization", STAFF_A_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkInResult").value("CHECKED_IN"));

        // Verify Session 1 entitlement is CHECKED_IN, but Session 2 entitlement is still NOT_CHECKED_IN
        TicketSessionEntitlement ent1 = entitlementRepository.findByTicketIdAndSessionId(
                ticketRepository.findByTicketCode(ticket.ticketCode()).orElseThrow().getId(), session1.getId()).orElseThrow();
        TicketSessionEntitlement ent2 = entitlementRepository.findByTicketIdAndSessionId(
                ticketRepository.findByTicketCode(ticket.ticketCode()).orElseThrow().getId(), session2.getId()).orElseThrow();

        assertThat(ent1.getAttendanceState()).isEqualTo(AttendanceState.CHECKED_IN);
        assertThat(ent2.getAttendanceState()).isEqualTo(AttendanceState.NOT_CHECKED_IN);

        // 2. Check in at Session 2 -> also succeeds independently!
        mockMvc.perform(post("/api/v1/workshop-sessions/{sessionId}/check-ins", session2.getReference())
                        .header("Authorization", STAFF_A_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkInResult").value("CHECKED_IN"));

        ent2 = entitlementRepository.findByTicketIdAndSessionId(
                ticketRepository.findByTicketCode(ticket.ticketCode()).orElseThrow().getId(), session2.getId()).orElseThrow();
        assertThat(ent2.getAttendanceState()).isEqualTo(AttendanceState.CHECKED_IN);

        // 3. Repeat scans at both sessions return ALREADY_CHECKED_IN
        mockMvc.perform(post("/api/v1/workshop-sessions/{sessionId}/check-ins", session1.getReference())
                        .header("Authorization", STAFF_A_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkInResult").value("ALREADY_CHECKED_IN"));

        mockMvc.perform(post("/api/v1/workshop-sessions/{sessionId}/check-ins", session2.getReference())
                        .header("Authorization", STAFF_A_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkInResult").value("ALREADY_CHECKED_IN"));

        // Exactly 2 AttendanceActions recorded in total (1 per session)
        List<AttendanceAction> actions = attendanceActionRepository.findAll();
        assertThat(actions).hasSize(2);
    }

    // =========================================================================
    // 8. Privacy Boundary: No Booking Contact PII Exposed
    // =========================================================================

    @Test
    @DisplayName("T033-8: Neither CheckInResult nor TicketList exposes booking contact PII")
    void privacyBoundary_noBookingContactPiiExposed() throws Exception {
        WorkshopSession session = createPublishedSession(locationA, slotMorning, null);
        String bookingRef = "BK-PRIV-" + UUID.randomUUID().toString().substring(0, 8);
        DeliverySafeTicketSet ticketSet = issueTicketsForBooking(bookingRef, List.of(session), 1);
        DeliverySafeTicket ticket = ticketSet.tickets().get(0);
        String qrToken = getQrReference(ticket);

        CheckInRequest req = CheckInRequest.builder()
                .qrToken(qrToken)
                .build();

        // 1. Scan result JSON check
        MvcResult checkInResult = mockMvc.perform(post("/api/v1/workshop-sessions/{sessionId}/check-ins", session.getReference())
                        .header("Authorization", STAFF_A_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        String checkInJson = checkInResult.getResponse().getContentAsString();
        assertThat(checkInJson)
                .doesNotContain("buyerEmail")
                .doesNotContain("email")
                .doesNotContain("phone")
                .doesNotContain("buyerPhone")
                .doesNotContain("customerName");

        // 2. Booking lookup JSON check
        MvcResult lookupResult = mockMvc.perform(get("/api/v1/workshop-sessions/{sessionId}/booking-lookups/{bookingCode}",
                        session.getReference(), bookingRef)
                        .header("Authorization", STAFF_A_TOKEN))
                .andExpect(status().isOk())
                .andReturn();

        String lookupJson = lookupResult.getResponse().getContentAsString();
        assertThat(lookupJson)
                .doesNotContain("buyerEmail")
                .doesNotContain("email")
                .doesNotContain("phone")
                .doesNotContain("buyerPhone")
                .doesNotContain("customerName")
                .doesNotContain("qrToken")
                .doesNotContain("qrReference");

        // 3. DTO reflection check
        for (Class<?> dtoClass : List.of(CheckInResult.class, Ticket.class, TicketResponse.class)) {
            List<String> fields = Arrays.stream(dtoClass.getDeclaredFields()).map(Field::getName).toList();
            assertThat(fields)
                    .as(dtoClass.getSimpleName() + " must not contain buyer contact fields")
                    .doesNotContain("buyerEmail", "email", "phone", "buyerPhone", "customerName", "contact");
        }
    }
}
