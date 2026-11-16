package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SellingLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SellingLocationPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopLocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSlotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.CheckInOpeningSource;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.OperationsAuditEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSlot;
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
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 003.2 T135 / T137: {@code PATCH /api/v1/workshop-locations/{locationId}} against MySQL (skipped without a MySQL datasource). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("workshopoperations-mysql-test")
@TestPropertySource(properties = {"workshop.operations.auth.allow-test-tokens=true"})
class WorkshopLocationUpdateIT {

    private static final String ADMIN = "Bearer test-uid:admin";
    private static final String MANAGER = "Bearer test-uid:manager";
    private static final String STAFF = "Bearer test-uid:staff";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockBean private AccountFacade accountFacade;
    @MockBean private TokenVerificationService tokenVerificationService;
    @Autowired private WorkshopLocationRepository locationRepository;
    @Autowired private WorkshopSlotRepository slotRepository;
    @Autowired private WorkshopSessionRepository sessionRepository;
    @Autowired private OperationsAuditEventRepository auditEventRepository;
    @Autowired private SellingLocationPort sellingLocationPort;

    private WorkshopLocation location;
    private WorkshopSlot slot;
    private String path;

    @BeforeEach
    void setUp() throws Exception {
        try (var connection = jdbcTemplate.getDataSource().getConnection()) {
            assumeTrue(connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql"),
                    "WorkshopLocationUpdateIT requires real MySQL evidence (WORKSHOP_OPERATIONS_TEST_JDBC_*)");
        }
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : List.of("attendance_actions", "ticket_session_entitlements", "attendance_tickets", "capacity_holds",
                "session_staff_assignments", "cancellation_handoffs", "operations_audit_events", "facade_command_idempotency",
                "workshop_sessions", "workshop_slots", "workshop_locations")) {
            try { jdbcTemplate.execute("TRUNCATE TABLE " + table); } catch (RuntimeException ignored) { /* table absent on this profile */ }
        }
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");

        location = locationRepository.save(WorkshopLocation.builder().code("LOC-EDIT").displayName("Original Name")
                .addressSnapshot("1 Old Street").active(true).build());
        slot = slotRepository.save(WorkshopSlot.builder().reference(UUID.randomUUID().toString()).location(location)
                .label("Morning").startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(12, 0)).defaultCapacity(20).active(true).build());
        path = "/api/v1/workshop-locations/" + location.getId();

        account("admin", 90L, ActorRole.ADMIN);
        account("manager", 10L, ActorRole.MANAGER);
        account("staff", 101L, ActorRole.STAFF);
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

    private static MockHttpServletRequestBuilder patchJson(String url, String token, String key, String body) {
        MockHttpServletRequestBuilder builder = patch(url).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body);
        return key == null ? builder : builder.header("Idempotency-Key", key);
    }

    private MockHttpServletRequestBuilder manager(String body) {
        return patchJson(path, MANAGER, UUID.randomUUID().toString(), body);
    }

    private void session(SessionState state, int dayOffset) {
        sessionRepository.save(WorkshopSession.builder().reference(UUID.randomUUID().toString()).location(location).slot(slot)
                .workshopDate(LocalDate.now().plusDays(dayOffset)).capacity(20).state(state)
                .scheduledStartAt(Instant.now().plusSeconds(3600)).scheduledEndAt(Instant.now().plusSeconds(7200))
                .checkInOpensAt(Instant.now().minusSeconds(60)).checkInOpeningSource(CheckInOpeningSource.DEFAULT).build());
    }

    private List<String> auditActions() {
        return auditEventRepository.findAll().stream().map(OperationsAuditEvent::getAction).sorted().toList();
    }

    private List<Long> sellingIds() {
        return sellingLocationPort.activeSellingLocations().stream().map(SellingLocation::id).toList();
    }

    @Test
    @DisplayName("T137: a Manager renames the location and an audit event records the prior/new name")
    void managerRenames() throws Exception {
        mockMvc.perform(manager("{\"name\":\"  Renamed  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.code").value("LOC-EDIT"))
                .andExpect(jsonPath("$.address").value("1 Old Street"))
                .andExpect(jsonPath("$.active").value(true));
        assertThat(locationRepository.findById(location.getId()).orElseThrow().getDisplayName()).isEqualTo("Renamed");
        assertThat(auditActions()).containsExactly("LOCATION_RENAMED");
        OperationsAuditEvent event = auditEventRepository.findAll().get(0);
        assertThat(event.getActorId()).isEqualTo(10L);
        assertThat(event.getOutcome()).isEqualTo("SUCCESS");
        assertThat(event.getMetadata()).contains("Original Name").contains("Renamed");
    }

    @Test
    @DisplayName("T137: a Manager changes the address; a combined change writes one audit event per applied change")
    void managerChangesAddressAndCombinedChangeAuditsEachApplied() throws Exception {
        mockMvc.perform(manager("{\"address\":\"2 New Street\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.address").value("2 New Street"));
        assertThat(auditActions()).containsExactly("LOCATION_ADDRESS_CHANGED");

        mockMvc.perform(manager("{\"name\":\"Both\",\"address\":\"2 New Street\",\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Both")).andExpect(jsonPath("$.active").value(false));
        // address unchanged on the second call: no extra address event
        assertThat(auditActions()).containsExactly("LOCATION_ADDRESS_CHANGED", "LOCATION_DEACTIVATED", "LOCATION_RENAMED");
    }

    @Test
    @DisplayName("T137: deactivation removes the location from the Product selling list; reactivation restores it")
    void deactivateAndReactivateAffectSellingList() throws Exception {
        assertThat(sellingIds()).contains(location.getId());
        mockMvc.perform(manager("{\"active\":false}")).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        assertThat(sellingIds()).doesNotContain(location.getId());
        mockMvc.perform(manager("{\"active\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        assertThat(sellingIds()).contains(location.getId());
        assertThat(auditActions()).containsExactly("LOCATION_DEACTIVATED", "LOCATION_REACTIVATED");
    }

    @Test
    @DisplayName("T137: a different code is refused 400 VALIDATION_ERROR; the same code is accepted")
    void codeChangeRefused() throws Exception {
        mockMvc.perform(manager("{\"code\":\"LOC-OTHER\",\"name\":\"X\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        WorkshopLocation reloaded = locationRepository.findById(location.getId()).orElseThrow();
        assertThat(reloaded.getCode()).isEqualTo("LOC-EDIT");
        assertThat(reloaded.getDisplayName()).isEqualTo("Original Name");
        assertThat(auditEventRepository.count()).isZero();
        mockMvc.perform(manager("{\"code\":\"LOC-EDIT\",\"name\":\"Same Code\"}")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("T137: an empty body or a blank name is 400 VALIDATION_ERROR")
    void emptyOrBlankRefused() throws Exception {
        mockMvc.perform(manager("{}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(manager("{\"name\":\"   \"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    @DisplayName("T137: Admin and Staff get 403, an unknown id is 404, a missing Idempotency-Key is 400")
    void authorityAndLookupErrors() throws Exception {
        mockMvc.perform(patchJson(path, ADMIN, UUID.randomUUID().toString(), "{\"name\":\"X\"}")).andExpect(status().isForbidden());
        mockMvc.perform(patchJson(path, STAFF, UUID.randomUUID().toString(), "{\"name\":\"X\"}")).andExpect(status().isForbidden());
        mockMvc.perform(patchJson("/api/v1/workshop-locations/999999", MANAGER, UUID.randomUUID().toString(), "{\"name\":\"X\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(patchJson(path, MANAGER, null, "{\"name\":\"X\"}")).andExpect(status().isBadRequest());
        assertThat(locationRepository.findById(location.getId()).orElseThrow().getDisplayName()).isEqualTo("Original Name");
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    @DisplayName("T137: the same Idempotency-Key replays with one audit event; a changed payload is 409 IDEMPOTENCY_CONFLICT")
    void idempotency() throws Exception {
        String key = UUID.randomUUID().toString();
        mockMvc.perform(patchJson(path, MANAGER, key, "{\"name\":\"Once\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Once"));
        mockMvc.perform(patchJson(path, MANAGER, key, "{\"name\":\"Once\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Once"));
        assertThat(auditActions()).containsExactly("LOCATION_RENAMED");
        mockMvc.perform(patchJson(path, MANAGER, key, "{\"name\":\"Different\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        assertThat(locationRepository.findById(location.getId()).orElseThrow().getDisplayName()).isEqualTo("Once");
        assertThat(auditEventRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("T137: deactivation is 409 INVALID_STATE while a not-ended session uses the location; rename and reactivation stay allowed")
    void deactivationRefusedWhileSessionNotEnded() throws Exception {
        session(SessionState.PUBLISHED, 3);
        mockMvc.perform(manager("{\"active\":false}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"))
                .andExpect(jsonPath("$.detail").isNotEmpty());
        assertThat(locationRepository.findById(location.getId()).orElseThrow().isActive()).isTrue();
        assertThat(auditEventRepository.count()).isZero();
        // rename allowed in the same situation
        mockMvc.perform(manager("{\"name\":\"Renamed While Busy\"}")).andExpect(status().isOk());
        assertThat(auditActions()).containsExactly("LOCATION_RENAMED");
        // reactivation is always allowed (location set inactive directly, bypassing the rule)
        WorkshopLocation inactive = locationRepository.findById(location.getId()).orElseThrow();
        inactive.setActive(false);
        locationRepository.save(inactive);
        mockMvc.perform(manager("{\"active\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("T137: deactivation is allowed when only CANCELLED/ATTENDANCE_FINALIZED sessions use the location")
    void deactivationAllowedWhenOnlyEndedSessions() throws Exception {
        session(SessionState.CANCELLED, 3);
        session(SessionState.ATTENDANCE_FINALIZED, 4);
        mockMvc.perform(manager("{\"active\":false}")).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        assertThat(auditActions()).containsExactly("LOCATION_DEACTIVATED");
    }

    @Test
    @DisplayName("FR-012c: the Manager list keeps a deactivated location so it can be reactivated")
    void managerListIncludesInactiveLocation() throws Exception {
        mockMvc.perform(manager("{\"active\":false}")).andExpect(status().isOk());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/workshop-locations")
                        .header("Authorization", MANAGER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(location.getId()))
                .andExpect(jsonPath("$[0].active").value(false));
    }
}
