package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.TargetAccountSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketRedeliveryOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.WorkshopBookingNotificationFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.*;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T131 (FR-007b, data-model "Denied-attempt audit"): a refused sensitive action writes one DENIED
 * {@code OperationsAuditEvent} (actor, session, correlation id, code and status only, no PII) in its own transaction,
 * while state conflicts, 401, 404 and normal check-in refusals write nothing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "workshopoperations-mysql-test"})
@TestPropertySource(properties = {"workshop.operations.auth.allow-test-tokens=true"})
class DeniedAttemptAuditIT {

    private static final String MANAGER = "Bearer test-uid:manager";
    private static final String STAFF = "Bearer test-uid:staff";             // assigned to the session
    private static final String OTHER_STAFF = "Bearer test-uid:staff-other"; // in scope, not assigned
    private static final String TICKET_CODE = "TICKET-DENIED-1";
    private static final String BOOKING = "BOOK-DENIED-1";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private WorkshopLocationRepository locationRepository;
    @Autowired private WorkshopSlotRepository slotRepository;
    @Autowired private WorkshopSessionRepository sessionRepository;
    @Autowired private SessionStaffAssignmentRepository staffAssignmentRepository;
    @Autowired private AttendanceTicketRepository ticketRepository;
    @Autowired private TicketSessionEntitlementRepository entitlementRepository;
    @Autowired private OperationsAuditEventRepository auditRepository;
    @MockBean private AccountFacade accountFacade;
    @MockBean private TokenVerificationService tokenVerificationService;
    @MockBean private WorkshopBookingFacade workshopBookingFacade;
    @MockBean private WorkshopBookingNotificationFacade workshopBookingNotificationFacade;

    private WorkshopLocation location;
    private WorkshopSession session;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : List.of("attendance_actions", "ticket_session_entitlements", "attendance_tickets",
                "ticket_resend_throttles", "capacity_holds", "session_staff_assignments", "cancellation_handoffs",
                "operations_audit_events", "workshop_sessions", "workshop_slots", "workshop_locations")) {
            jdbcTemplate.execute("TRUNCATE TABLE " + table);
        }
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");

        location = locationRepository.save(WorkshopLocation.builder().code("LOC-DENY").displayName("Deny Location")
                .addressSnapshot("1 Test Street").active(true).build());
        WorkshopSlot slot = slotRepository.save(WorkshopSlot.builder().reference(UUID.randomUUID().toString())
                .location(location).label("Morning").startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(12, 0))
                .defaultCapacity(20).active(true).build());
        session = sessionRepository.save(WorkshopSession.builder().reference(UUID.randomUUID().toString())
                .location(location).slot(slot).workshopDate(LocalDate.now().plusDays(2)).capacity(20)
                .state(SessionState.PUBLISHED)
                .scheduledStartAt(Instant.now().plusSeconds(3600)).scheduledEndAt(Instant.now().plusSeconds(7200))
                .checkInOpensAt(Instant.now().minusSeconds(60)).checkInOpeningSource(CheckInOpeningSource.DEFAULT).build());
        staffAssignmentRepository.save(SessionStaffAssignment.builder().session(session).accountId(101L)
                .assignedById(10L).assignedAt(Instant.now()).active(true).build());

        AttendanceTicket ticket = ticketRepository.save(AttendanceTicket.builder().reference(UUID.randomUUID().toString())
                .bookingReference(BOOKING).ticketSequence(1).ticketCode(TICKET_CODE).qrReference("QR-" + TICKET_CODE)
                .validityState(TicketValidityState.ACTIVE).build());
        entitlementRepository.save(TicketSessionEntitlement.builder().ticket(ticket).session(session)
                .attendanceState(AttendanceState.NOT_CHECKED_IN).build());

        account("manager", 10L, ActorRole.MANAGER);
        account("staff", 101L, ActorRole.STAFF);
        account("staff-other", 102L, ActorRole.STAFF);
        when(accountFacade.getTargetAccount(anyLong())).thenReturn(Optional.empty());
        when(accountFacade.getTargetAccount(101L)).thenReturn(Optional.of(
                new TargetAccountSnapshot(101L, ActorRole.STAFF, ActorStatus.ACTIVE, List.of(location.getId()))));
        when(workshopBookingFacade.requestBookingTicketRedelivery(any())).thenReturn(TicketRedeliveryOutcome.accepted());
    }

    private void account(String uid, long id, ActorRole role) {
        when(accountFacade.resolveAccount(uid)).thenReturn(Optional.of(
                new AccountSummary(id, role, ActorStatus.ACTIVE, uid + "@mland.vn", Instant.now(), Instant.now())));
        when(accountFacade.getAccountStatus(id)).thenReturn(Optional.of(ActorStatus.ACTIVE));
        when(accountFacade.getLocationScope(id)).thenReturn(List.of(location.getId()));
        when(tokenVerificationService.verify("test-uid:" + uid))
                .thenReturn(new VerifiedIdentity(uid, uid + "@mland.vn", true, Instant.now()));
        when(accountFacade.resolveActor(uid, true)).thenReturn(Optional.of(
                new CurrentAccountActor(id, role, ActorStatus.ACTIVE, true, List.of(location.getId()))));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String token, String body) {
        return b.header("Authorization", token).header("Idempotency-Key", UUID.randomUUID().toString())
                .header("X-Correlation-Id", "corr-t131").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String path(String suffix) {
        return "/api/v1/workshop-sessions/" + session.getReference() + suffix;
    }

    private static com.fasterxml.jackson.databind.JsonNode readJson(String json) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
    }

    private List<OperationsAuditEvent> denied() {
        return auditRepository.findAll().stream().filter(e -> "DENIED".equals(e.getOutcome())).toList();
    }

    private void assertSingleDenied(String action, Long actorId, boolean hasSession, String code, int status)
            throws Exception {
        List<OperationsAuditEvent> rows = denied();
        assertThat(rows).as(action).hasSize(1);
        OperationsAuditEvent row = rows.get(0);
        assertThat(row.getAction()).isEqualTo(action);
        assertThat(row.getActorId()).isEqualTo(actorId);
        assertThat(row.getSessionId()).isEqualTo(hasSession ? session.getId() : null);
        assertThat(row.getCorrelationId()).isEqualTo("corr-t131");
        com.fasterxml.jackson.databind.JsonNode meta = readJson(row.getMetadata());
        assertThat(meta.get("code").asText()).isEqualTo(code);
        assertThat(meta.get("status").asInt()).isEqualTo(status);
        assertThat(row.getMetadata())
                .doesNotContain(TICKET_CODE).doesNotContain(BOOKING).doesNotContain("@");
        assertThat(row.getOccurredAt()).isNotNull();
    }

    @Test
    @DisplayName("T131: each listed action refused with 403 writes exactly one DENIED row and changes nothing")
    void forbiddenRefusalsAreAuditedPerAction() throws Exception {
        Map<String, MockHttpServletRequestBuilder> cases = new LinkedHashMap<>();
        cases.put("MANAGER_BOOKING_LOOKUP", get("/api/v1/workshop-booking-lookups/" + BOOKING)
                .header("Authorization", STAFF).header("X-Correlation-Id", "corr-t131"));
        cases.put("CHECK_IN_REVERSED", json(post(path("/check-in-reversals")), STAFF,
                "{\"ticketCode\":\"" + TICKET_CODE + "\",\"reasonCode\":\"RECORDING_ERROR\",\"reasonNote\":\"typo\"}"));
        cases.put("SESSION_CANCELLATION_REQUESTED", json(post(path("/cancellation-requests")), STAFF,
                "{\"reasonCode\":\"FACILITY_ISSUE\",\"reasonNote\":\"leak\"}"));
        cases.put("SESSION_STAFF_ASSIGNED", json(put(path("/staff-assignments")), STAFF, "{\"accountId\":101}"));
        cases.put("SESSION_STAFF_REMOVED", delete(path("/staff-assignments")).param("accountId", "101")
                .header("Authorization", STAFF).header("Idempotency-Key", UUID.randomUUID().toString())
                .header("X-Correlation-Id", "corr-t131"));
        cases.put("TICKET_CHECK_IN_OVERRIDE", json(post(path("/check-in-overrides")), OTHER_STAFF,
                "{\"ticketCode\":\"" + TICKET_CODE + "\",\"reasonCode\":\"CUSTOMER_DEVICE_UNAVAILABLE\"}"));
        cases.put("ATTENDANCE_FINALIZED", json(post(path("/attendance-finalizations")), OTHER_STAFF,
                "{\"confirmedSessionEnded\":true}"));
        cases.put("TICKET_REDELIVERY_REQUESTED", json(post(path("/ticket-resends")), OTHER_STAFF,
                "{\"bookingCode\":\"" + BOOKING + "\"}"));

        for (Map.Entry<String, MockHttpServletRequestBuilder> c : cases.entrySet()) {
            auditRepository.deleteAll();
            mockMvc.perform(c.getValue()).andExpect(status().isForbidden());
            boolean unassignedActor = List.of("TICKET_CHECK_IN_OVERRIDE", "ATTENDANCE_FINALIZED",
                    "TICKET_REDELIVERY_REQUESTED").contains(c.getKey());
            // The lookup route has no session in its path.
            assertSingleDenied(c.getKey(), unassignedActor ? 102L : 101L,
                    !c.getKey().equals("MANAGER_BOOKING_LOOKUP"), "FORBIDDEN", 403);
        }
        // No business change: the refused assign/remove left the single assignment, and no ticket moved.
        assertThat(staffAssignmentRepository.findBySessionIdAndActiveTrue(session.getId())).hasSize(1);
        assertThat(entitlementRepository.findAll())
                .allMatch(e -> e.getAttendanceState() == AttendanceState.NOT_CHECKED_IN);
    }

    @Test
    @DisplayName("T131: a Staff attendance correction on a finalized session (403) is audited")
    void attendanceCorrectionRefusalIsAudited() throws Exception {
        // The correction route checks the session state (409) before the authority, so it must be finalized.
        session.setState(SessionState.ATTENDANCE_FINALIZED);
        sessionRepository.saveAndFlush(session);
        mockMvc.perform(json(post(path("/attendance-corrections")), STAFF,
                        "{\"ticketCode\":\"" + TICKET_CODE + "\",\"correctedAttendanceState\":\"CHECKED_IN\","
                                + "\"reasonCode\":\"RECORDING_ERROR\",\"reasonNote\":\"typo\"}"))
                .andExpect(status().isForbidden());
        assertSingleDenied("ATTENDANCE_CORRECTED", 101L, true, "FORBIDDEN", 403);
    }

    @Test
    @DisplayName("T131: an ineligible Staff target (409 STAFF_NOT_ELIGIBLE) is audited with its reason")
    void staffNotEligibleIsAudited() throws Exception {
        mockMvc.perform(json(put(path("/staff-assignments")), MANAGER, "{\"accountId\":555}"))
                .andExpect(status().isConflict());
        assertSingleDenied("SESSION_STAFF_ASSIGNED", 10L, true, "STAFF_NOT_ELIGIBLE", 409);
        assertThat(readJson(denied().get(0).getMetadata()).get("reason").asText()).isEqualTo("ACCOUNT_NOT_FOUND");
    }

    @Test
    @DisplayName("T131: resend 409 NO_ELIGIBLE_TICKET and the fourth resend (429) are audited; accepted ones are not DENIED")
    void resendRefusalsAreAudited() throws Exception {
        mockMvc.perform(json(post(path("/ticket-resends")), STAFF, "{\"bookingCode\":\"BOOK-UNKNOWN\"}"))
                .andExpect(status().isConflict());
        assertSingleDenied("TICKET_REDELIVERY_REQUESTED", 101L, true, "INVALID_STATE", 409);
        assertThat(readJson(denied().get(0).getMetadata()).get("reason").asText()).isEqualTo("NO_ELIGIBLE_TICKET");
        assertThat(denied().get(0).getMetadata())
                .doesNotContain("BOOK-UNKNOWN");

        auditRepository.deleteAll();
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(json(post(path("/ticket-resends")), STAFF, "{\"bookingCode\":\"" + BOOKING + "\"}"))
                    .andExpect(status().isAccepted());
        }
        assertThat(denied()).isEmpty();
        mockMvc.perform(json(post(path("/ticket-resends")), STAFF, "{\"bookingCode\":\"" + BOOKING + "\"}"))
                .andExpect(status().isTooManyRequests());
        assertSingleDenied("TICKET_REDELIVERY_REQUESTED", 101L, true, "RATE_LIMITED", 429);
    }

    @Test
    @DisplayName("T131: 401, 404, a refused normal check-in and a state conflict write no DENIED row")
    void unlistedRefusalsAreNotAudited() throws Exception {
        mockMvc.perform(get("/api/v1/workshop-booking-lookups/" + BOOKING)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/workshop-booking-lookups/NO-SUCH").header("Authorization", MANAGER))
                .andExpect(status().isNotFound());
        mockMvc.perform(json(post(path("/check-ins")), OTHER_STAFF, "{\"ticketCode\":\"" + TICKET_CODE + "\"}"))
                .andExpect(status().isForbidden());
        // Reversing a ticket that was never checked in is a state conflict, not an authority refusal.
        mockMvc.perform(json(post(path("/check-in-reversals")), MANAGER,
                "{\"ticketCode\":\"" + TICKET_CODE + "\",\"reasonCode\":\"RECORDING_ERROR\",\"reasonNote\":\"typo\"}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(409, 400));
        assertThat(denied()).isEmpty();
    }
}
