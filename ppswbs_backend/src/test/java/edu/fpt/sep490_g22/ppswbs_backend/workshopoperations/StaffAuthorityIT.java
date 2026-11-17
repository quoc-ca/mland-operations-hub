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
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.SessionStaffAssignmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopLocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSlotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.CheckInOpeningSource;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSlot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopSessionStartScheduler;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 003.2 T129 / SC-004 (Admin holds no Workshop Operations business authority, Manager creates locations) and
 * T128 (an Account facade failure fails closed with 503 DEPENDENCY_UNAVAILABLE and writes nothing).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"workshop.operations.auth.allow-test-tokens=true"})
class StaffAuthorityIT {

    private static final String ADMIN = "Bearer test-uid:admin";
    private static final String MANAGER = "Bearer test-uid:manager";
    private static final String STAFF = "Bearer test-uid:staff";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockBean private AccountFacade accountFacade;
    @MockBean private TokenVerificationService tokenVerificationService;
    @Autowired private WorkshopLocationRepository locationRepository;
    @Autowired private WorkshopSlotRepository slotRepository;
    @Autowired private WorkshopSessionRepository sessionRepository;
    @Autowired private SessionStaffAssignmentRepository staffAssignmentRepository;
    @Autowired private OperationsAuditEventRepository auditEventRepository;
    @Autowired private WorkshopSessionStartScheduler startScheduler;

    private WorkshopLocation location;
    private WorkshopSession session;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : List.of("attendance_actions", "ticket_session_entitlements", "attendance_tickets", "capacity_holds",
                "session_staff_assignments", "cancellation_handoffs", "operations_audit_events", "facade_command_idempotency",
                "workshop_sessions", "workshop_slots", "workshop_locations")) {
            try { jdbcTemplate.execute("TRUNCATE TABLE " + table); } catch (RuntimeException ignored) { /* table absent on this profile */ }
        }
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");

        location = locationRepository.save(WorkshopLocation.builder().code("LOC-AUTH").displayName("Auth Location")
                .addressSnapshot("1 Test Street").active(true).build());
        WorkshopSlot slot = slotRepository.save(WorkshopSlot.builder().reference(UUID.randomUUID().toString()).location(location)
                .label("Morning").startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(12, 0)).defaultCapacity(20).active(true).build());
        LocalDate date = LocalDate.now().plusDays(2);
        session = sessionRepository.save(WorkshopSession.builder().reference(UUID.randomUUID().toString()).location(location).slot(slot)
                .workshopDate(date).capacity(20).state(SessionState.PUBLISHED)
                .scheduledStartAt(Instant.now().plusSeconds(3600)).scheduledEndAt(Instant.now().plusSeconds(7200))
                .checkInOpensAt(Instant.now().minusSeconds(60)).checkInOpeningSource(CheckInOpeningSource.DEFAULT).build());

        account("admin", 90L, ActorRole.ADMIN);
        account("manager", 10L, ActorRole.MANAGER);
        account("staff", 101L, ActorRole.STAFF);
        when(accountFacade.getTargetAccount(anyLong())).thenReturn(Optional.empty());
        when(accountFacade.getTargetAccount(101L)).thenReturn(Optional.of(
                new TargetAccountSnapshot(101L, ActorRole.STAFF, ActorStatus.ACTIVE, List.of(location.getId()))));
    }

    private void account(String uid, long id, ActorRole role) {
        when(accountFacade.resolveAccount(uid)).thenReturn(Optional.of(
                new AccountSummary(id, role, ActorStatus.ACTIVE, uid + "@mland.vn", Instant.now(), Instant.now())));
        when(accountFacade.getAccountStatus(id)).thenReturn(Optional.of(ActorStatus.ACTIVE));
        when(accountFacade.getLocationScope(id)).thenReturn(List.of(location.getId()));
        when(tokenVerificationService.verify("test-uid:" + uid)).thenReturn(new VerifiedIdentity(uid, uid + "@mland.vn", true, Instant.now()));
        when(accountFacade.resolveActor(uid, true)).thenReturn(Optional.of(
                new CurrentAccountActor(id, role, ActorStatus.ACTIVE, true, List.of(location.getId()))));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String token, String body) {
        return builder.header("Authorization", token).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String sessionPath(String suffix) {
        return "/api/v1/workshop-sessions/" + session.getReference() + suffix;
    }

    // ---- T129 / SC-004 ----------------------------------------------------------------------------------------

    @Test
    @DisplayName("T129: a Manager creates a location (201)")
    void managerCreatesALocation() throws Exception {
        mockMvc.perform(json(post("/api/v1/workshop-locations"), MANAGER,
                        "{\"code\":\"LOC-NEW\",\"name\":\"New Location\",\"address\":\"2 Test Street\",\"active\":true}"))
                .andExpect(status().isCreated());
        assertThat(locationRepository.findByCode("LOC-NEW")).isPresent();
    }

    @Test
    @DisplayName("T129: Admin and Staff get 403 on location create")
    void adminAndStaffCannotCreateALocation() throws Exception {
        for (String token : List.of(ADMIN, STAFF)) {
            mockMvc.perform(json(post("/api/v1/workshop-locations"), token,
                            "{\"code\":\"LOC-X\",\"name\":\"X\",\"address\":\"3 Test Street\",\"active\":true}"))
                    .andExpect(status().isForbidden());
        }
        assertThat(locationRepository.findByCode("LOC-X")).isEmpty();
    }

    @Test
    @DisplayName("SC-004 / T138: Admin gets 403 on every Workshop Operations route, including the location list")
    void adminIsRefusedOnEveryOperationsRoute() throws Exception {
        List<MockHttpServletRequestBuilder> routes = List.of(
                get("/api/v1/workshop-locations").header("Authorization", ADMIN),
                get("/api/v1/workshop-sessions").header("Authorization", ADMIN),
                get(sessionPath("")).header("Authorization", ADMIN),
                get(sessionPath("/attendance-roster")).header("Authorization", ADMIN),
                get(sessionPath("/booking-lookups/BK-1")).header("Authorization", ADMIN),
                json(post(sessionPath("/state-transitions")), ADMIN, "{\"targetState\":\"SALES_PAUSED\"}"),
                json(post(sessionPath("/attendance-finalizations")), ADMIN, "{\"confirmedSessionEnded\":true}"),
                json(put(sessionPath("/staff-assignments")), ADMIN, "{\"accountId\":101}"),
                delete(sessionPath("/staff-assignments")).param("accountId", "101")
                        .header("Authorization", ADMIN).header("Idempotency-Key", UUID.randomUUID().toString()),
                json(post(sessionPath("/check-ins")), ADMIN, "{\"ticketCode\":\"TK-1\"}"));
        for (int i = 0; i < routes.size(); i++) {
            assertThat(mockMvc.perform(routes.get(i)).andReturn().getResponse().getStatus()).as("admin route #%d", i).isEqualTo(403);
        }
        assertThat(sessionRepository.findByReference(session.getReference()).orElseThrow().getState()).isEqualTo(SessionState.PUBLISHED);
        assertThat(staffAssignmentRepository.findBySessionIdAndActiveTrue(session.getId())).isEmpty();
    }

    @Test
    @DisplayName("FR-001e/FR-001f: Admin and Staff get 403 on slot create/update and session create/bulk/update; a Manager creates a slot")
    void slotAndSessionAdministrationIsManagerOnly() throws Exception {
        String slotBody = "{\"label\":\"Late\",\"startsAtLocal\":\"14:00\",\"endsAtLocal\":\"16:00\",\"defaultCapacity\":10,\"active\":true}";
        String slotsPath = "/api/v1/workshop-locations/" + location.getId() + "/slots";
        String sessionBody = "{\"locationId\":" + location.getId() + ",\"slotId\":\"" + UUID.randomUUID() + "\",\"workshopDate\":\""
                + LocalDate.now().plusDays(5) + "\",\"capacity\":5}";
        String bulkBody = "{\"locationId\":" + location.getId() + ",\"slotIds\":[\"" + slotRepository.findAll().get(0).getReference() + "\"],\"from\":\"" + LocalDate.now().plusDays(2)
                + "\",\"to\":\"" + LocalDate.now().plusDays(3) + "\",\"weekdays\":[\"MONDAY\"]}";
        long slotsBefore = slotRepository.count();
        long sessionsBefore = sessionRepository.count();
        String existingSlot = slotRepository.findAll().get(0).getReference();
        for (String token : List.of(ADMIN, STAFF)) {
            for (MockHttpServletRequestBuilder route : List.of(
                    json(post(slotsPath), token, slotBody),
                    json(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/workshop-slots/" + existingSlot), token, slotBody),
                    json(post("/api/v1/workshop-sessions"), token, sessionBody),
                    json(post("/api/v1/workshop-sessions/bulk"), token, bulkBody),
                    json(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(sessionPath("")), token, "{\"capacity\":12}"))) {
                mockMvc.perform(route).andExpect(status().isForbidden());
            }
        }
        assertThat(slotRepository.count()).isEqualTo(slotsBefore);
        assertThat(sessionRepository.count()).isEqualTo(sessionsBefore);
        mockMvc.perform(json(post(slotsPath), MANAGER, slotBody)).andExpect(status().isCreated());
    }

    // ---- T128 -------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("T128: an Account facade failure is 503 DEPENDENCY_UNAVAILABLE on check-in, resend, assignment and a Manager write, with no audit or business row")
    void accountFacadeFailureFailsClosed() throws Exception {
        when(accountFacade.resolveAccount(anyString())).thenThrow(new IllegalStateException("account module down"));
        long auditsBefore = auditEventRepository.count();

        List<MockHttpServletRequestBuilder> routes = List.of(
                json(post(sessionPath("/check-ins")), STAFF, "{\"ticketCode\":\"TK-1\"}"),
                json(post(sessionPath("/ticket-resends")), STAFF, "{\"bookingCode\":\"BK-1\"}"),
                json(put(sessionPath("/staff-assignments")), MANAGER, "{\"accountId\":101}"),
                json(post(sessionPath("/state-transitions")), MANAGER, "{\"targetState\":\"SALES_PAUSED\"}"));
        for (MockHttpServletRequestBuilder route : routes) {
            mockMvc.perform(route).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
        }

        assertThat(auditEventRepository.count()).isEqualTo(auditsBefore);
        assertThat(staffAssignmentRepository.findBySessionIdAndActiveTrue(session.getId())).isEmpty();
        assertThat(sessionRepository.findByReference(session.getReference()).orElseThrow().getState()).isEqualTo(SessionState.PUBLISHED);
    }

    @Test
    @DisplayName("T128: the scheduled start still runs while the Account facade is down (it does not call the facade)")
    void scheduledStartDoesNotNeedTheAccountFacade() {
        when(accountFacade.resolveAccount(anyString())).thenThrow(new IllegalStateException("account module down"));
        when(accountFacade.getLocationScope(anyLong())).thenThrow(new IllegalStateException("account module down"));
        session.setScheduledStartAt(Instant.now().minusSeconds(1));
        sessionRepository.saveAndFlush(session);

        startScheduler.transitionDueSessions();

        assertThat(sessionRepository.findByReference(session.getReference()).orElseThrow().getState()).isEqualTo(SessionState.IN_PROGRESS);
    }

    // ---- T130 over HTTP ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("T130: STAFF_NOT_ELIGIBLE carries its reason; a Staff assignee is accepted once")
    void staffNotEligibleOverHttp() throws Exception {
        mockMvc.perform(json(put(sessionPath("/staff-assignments")), MANAGER, "{\"accountId\":555}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAFF_NOT_ELIGIBLE"))
                .andExpect(jsonPath("$.reason").value("ACCOUNT_NOT_FOUND"));

        mockMvc.perform(json(put(sessionPath("/staff-assignments")), MANAGER, "{\"accountId\":101}")).andExpect(status().isOk());
        mockMvc.perform(json(put(sessionPath("/staff-assignments")), MANAGER, "{\"accountId\":101}")).andExpect(status().isOk());
        assertThat(staffAssignmentRepository.findBySessionIdAndActiveTrue(session.getId())).hasSize(1);
    }

    // ---- T115 over HTTP ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("T115: missingStaff in the session response, refusal of the last removal and the confirmed removal, with audits")
    void lastAssignmentFlowOverHttp() throws Exception {
        mockMvc.perform(get(sessionPath("")).header("Authorization", MANAGER))
                .andExpect(status().isOk()).andExpect(jsonPath("$.missingStaff").value(true));

        mockMvc.perform(json(put(sessionPath("/staff-assignments")), MANAGER, "{\"accountId\":101}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.missingStaff").value(false));

        mockMvc.perform(delete(sessionPath("/staff-assignments")).param("accountId", "101")
                        .header("Authorization", MANAGER).header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_STAFF_ASSIGNMENT_CONFIRMATION_REQUIRED"));
        assertThat(staffAssignmentRepository.findBySessionIdAndActiveTrue(session.getId())).hasSize(1);
        assertThat(auditEventRepository.findBySessionIdOrderByOccurredAtDesc(session.getId()))
                .anyMatch(e -> "SESSION_STAFF_REMOVED".equals(e.getAction()) && "DENIED".equals(e.getOutcome()));

        mockMvc.perform(delete(sessionPath("/staff-assignments")).param("accountId", "101").param("confirmLastAssignmentRemoval", "true")
                        .header("Authorization", MANAGER).header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isNoContent());
        assertThat(staffAssignmentRepository.findBySessionIdAndActiveTrue(session.getId())).isEmpty();
        assertThat(auditEventRepository.findBySessionIdOrderByOccurredAtDesc(session.getId()))
                .anyMatch(e -> "SESSION_STAFF_REMOVED".equals(e.getAction()) && "SUCCESS".equals(e.getOutcome()));
    }
}
