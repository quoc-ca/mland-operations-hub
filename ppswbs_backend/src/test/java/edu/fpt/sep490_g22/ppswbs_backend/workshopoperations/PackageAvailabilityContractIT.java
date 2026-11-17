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
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopLocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopPackageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSlotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackage;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackageState;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T159 (003.2 Phase 18, FR-038/FR-039/FR-044, WF-01..WF-04, SC-010/SC-012): the two matrix routes and the discovery
 * 409 over HTTP on real MySQL ({@code WORKSHOP_OPERATIONS_TEST_JDBC_*}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("workshopoperations-mysql-test")
@TestPropertySource(properties = {"workshop.operations.auth.allow-test-tokens=true"})
class PackageAvailabilityContractIT {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String MANAGER = "Bearer test-uid:pa-manager";
    private static final String STAFF = "Bearer test-uid:pa-staff";
    private static final String ADMIN = "Bearer test-uid:pa-admin";
    private static final String FLAKY = "Bearer test-uid:pa-flaky";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private WorkshopLocationRepository locationRepository;
    @Autowired private WorkshopPackageRepository packageRepository;
    @Autowired private WorkshopSlotRepository slotRepository;
    @Autowired private WorkshopSessionRepository sessionRepository;
    @MockBean private AccountFacade accountFacade;
    @MockBean private TokenVerificationService tokenVerificationService;

    private WorkshopLocation location;
    private WorkshopPackage workshopPackage;

    @BeforeEach
    void setUp() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            assumeTrue(connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql"),
                    "PackageAvailabilityContractIT requires real MySQL evidence");
        }
        location = locationRepository.saveAndFlush(WorkshopLocation.builder()
                .code("PAC-" + UUID.randomUUID()).displayName("Contract Location").addressSnapshot("test").build());
        workshopPackage = packageRepository.save(WorkshopPackage.builder()
                .name("PAC package " + UUID.randomUUID()).description("fixture").publicPrice(1000L)
                .minParticipants(1).maxParticipants(10).requiredSessionCount(1).sameLocationRequired(false)
                .minimumSameDaySlotGap(0).state(WorkshopPackageState.PUBLISHED).build());
        actor("pa-manager", 10001L, ActorRole.MANAGER);
        actor("pa-staff", 10002L, ActorRole.STAFF);
        actor("pa-admin", 10003L, ActorRole.ADMIN);
        when(tokenVerificationService.verify("test-uid:pa-flaky"))
                .thenReturn(new VerifiedIdentity("pa-flaky", "pa-flaky@example.invalid", true, Instant.now()));
        when(accountFacade.resolveActor("pa-flaky", true)).thenReturn(Optional.of(
                new CurrentAccountActor(10004L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of())));
        when(accountFacade.resolveAccount("pa-flaky")).thenThrow(new IllegalStateException("account service down"));
    }

    private void actor(String uid, long id, ActorRole role) {
        when(tokenVerificationService.verify("test-uid:" + uid))
                .thenReturn(new VerifiedIdentity(uid, uid + "@example.invalid", true, Instant.now()));
        when(accountFacade.resolveActor(uid, true)).thenReturn(Optional.of(
                new CurrentAccountActor(id, role, ActorStatus.ACTIVE, true, List.of())));
        when(accountFacade.resolveAccount(uid)).thenReturn(Optional.of(
                new AccountSummary(id, role, ActorStatus.ACTIVE, uid + "@example.invalid", Instant.now(), Instant.now())));
        when(accountFacade.getAccountStatus(id)).thenReturn(Optional.of(ActorStatus.ACTIVE));
    }

    private MockHttpServletRequestBuilder write(String auth, String idempotencyKey, long packageId, long locationId,
                                                String body) {
        MockHttpServletRequestBuilder builder = put("/api/v1/workshop-packages/{p}/locations/{l}/availability",
                packageId, locationId).contentType(MediaType.APPLICATION_JSON).content(body);
        if (auth != null) builder.header("Authorization", auth);
        if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);
        return builder;
    }

    @Test
    @DisplayName("GET matrix: Manager 200 with every Package x location; Staff and Admin 403 with a denied-attempt audit; no token 401")
    void matrixReadIsManagerOnly() throws Exception {
        MvcResult ok = mockMvc.perform(get("/api/v1/workshop-package-availability").header("Authorization", MANAGER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.packages").isArray())
                .andExpect(jsonPath("$.locations").isArray())
                .andExpect(jsonPath("$.cells").isArray())
                .andExpect(jsonPath("$.cells[?(@.packageId == " + workshopPackage.getId() + " && @.locationId == "
                        + location.getId() + ")].state").value(org.hamcrest.Matchers.contains("INACTIVE")))
                .andExpect(jsonPath("$.cells[?(@.packageId == " + workshopPackage.getId() + " && @.locationId == "
                        + location.getId() + ")].version").value(org.hamcrest.Matchers.contains(0)))
                .andReturn();
        JsonNode matrix = objectMapper.readTree(ok.getResponse().getContentAsString());
        assertThat(matrix.get("cells").size())
                .isEqualTo(matrix.get("packages").size() * matrix.get("locations").size());

        long deniedBefore = deniedAudits("PACKAGE_AVAILABILITY_MATRIX_READ");
        for (String denied : List.of(STAFF, ADMIN)) {
            MvcResult result = mockMvc.perform(get("/api/v1/workshop-package-availability")
                            .header("Authorization", denied).header("X-Correlation-Id", "pac-corr-" + denied.length()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                    .andReturn();
            assertThat(result.getResponse().getContentAsString()).doesNotContain("cells", "pa-staff", "pa-admin",
                    "example.invalid");
        }
        assertThat(deniedAudits("PACKAGE_AVAILABILITY_MATRIX_READ")).isEqualTo(deniedBefore + 2);
        String deniedMetadata = jdbc.queryForObject("SELECT metadata FROM operations_audit_events WHERE action = ? "
                + "AND outcome = 'DENIED' ORDER BY id DESC LIMIT 1", String.class, "PACKAGE_AVAILABILITY_MATRIX_READ");
        assertThat(deniedMetadata).contains("FORBIDDEN").doesNotContain("example.invalid", "pa-staff", "pa-admin");

        mockMvc.perform(get("/api/v1/workshop-package-availability")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT: Manager sets a cell; Staff/Admin 403 with a denied audit; missing token 401; unknown Package or location 404")
    void putAuthorizationAndNotFound() throws Exception {
        String body = "{\"state\":\"ACTIVE\"}";
        long deniedBefore = deniedAudits("WORKSHOP_PACKAGE_AVAILABILITY_CHANGED");
        for (String denied : List.of(STAFF, ADMIN)) {
            mockMvc.perform(write(denied, UUID.randomUUID().toString(), workshopPackage.getId(), location.getId(), body))
                    .andExpect(status().isForbidden());
        }
        assertThat(deniedAudits("WORKSHOP_PACKAGE_AVAILABILITY_CHANGED")).isEqualTo(deniedBefore + 2);
        mockMvc.perform(write(null, UUID.randomUUID().toString(), workshopPackage.getId(), location.getId(), body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(write(MANAGER, UUID.randomUUID().toString(), 987654321L, location.getId(), body))
                .andExpect(status().isNotFound());
        mockMvc.perform(write(MANAGER, UUID.randomUUID().toString(), workshopPackage.getId(), 987654321L, body))
                .andExpect(status().isNotFound());
        mockMvc.perform(write(MANAGER, UUID.randomUUID().toString(), workshopPackage.getId(), location.getId(), body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.packageId").value(workshopPackage.getId()))
                .andExpect(jsonPath("$.locationId").value(location.getId()));
    }

    @Test
    @DisplayName("PUT: same key and content replays the saved result; different content is IDEMPOTENCY_CONFLICT; a stale version is OPTIMISTIC_LOCK_CONFLICT; the same state writes no second audit")
    void putIdempotencyAndOptimisticLock() throws Exception {
        long packageId = workshopPackage.getId();
        long locationId = location.getId();
        String key = UUID.randomUUID().toString();
        mockMvc.perform(write(MANAGER, key, packageId, locationId, "{\"state\":\"ACTIVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("ACTIVE"));
        assertThat(auditEvents(packageId)).isEqualTo(1);

        mockMvc.perform(write(MANAGER, key, packageId, locationId, "{\"state\":\"ACTIVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("ACTIVE"));
        assertThat(auditEvents(packageId)).isEqualTo(1);
        mockMvc.perform(write(MANAGER, key, packageId, locationId, "{\"state\":\"INACTIVE\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));

        // the same state again with a new key is a no-op: current cell, no second audit event
        mockMvc.perform(write(MANAGER, UUID.randomUUID().toString(), packageId, locationId, "{\"state\":\"ACTIVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0));
        assertThat(auditEvents(packageId)).isEqualTo(1);

        mockMvc.perform(write(MANAGER, UUID.randomUUID().toString(), packageId, locationId,
                        "{\"state\":\"INACTIVE\",\"version\":7}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OPTIMISTIC_LOCK_CONFLICT"));
        mockMvc.perform(write(MANAGER, UUID.randomUUID().toString(), packageId, locationId,
                        "{\"state\":\"INACTIVE\",\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("INACTIVE"))
                .andExpect(jsonPath("$.version").value(1));
        assertThat(auditEvents(packageId)).isEqualTo(2);

        mockMvc.perform(write(MANAGER, null, packageId, locationId, "{\"state\":\"ACTIVE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Discovery: an INACTIVE pair is 409 PACKAGE_NOT_AVAILABLE_AT_LOCATION and absent from /locations; an ACTIVE pair lists the location and sessions carry locationName")
    void discoveryFollowsThePair() throws Exception {
        WorkshopSession session = session();
        mockMvc.perform(get("/api/v1/workshop-packages/{p}/sessions", workshopPackage.getId())
                        .queryParam("locationId", location.getId().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PACKAGE_NOT_AVAILABLE_AT_LOCATION"));
        mockMvc.perform(get("/api/v1/workshop-packages/{p}/locations", workshopPackage.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + location.getId() + ")]").doesNotExist());

        mockMvc.perform(write(MANAGER, UUID.randomUUID().toString(), workshopPackage.getId(), location.getId(),
                "{\"state\":\"ACTIVE\"}")).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/workshop-packages/{p}/locations", workshopPackage.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + location.getId() + ")]").exists());
        mockMvc.perform(get("/api/v1/workshop-packages/{p}/sessions", workshopPackage.getId())
                        .queryParam("locationId", location.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionReference").value(session.getReference()))
                .andExpect(jsonPath("$[0].locationId").value(location.getId()))
                .andExpect(jsonPath("$[0].locationName").value("Contract Location"));
    }

    @Test
    @DisplayName("503 DEPENDENCY_UNAVAILABLE (fail closed) when the Account facade fails")
    void accountFacadeFailureFailsClosed() throws Exception {
        mockMvc.perform(get("/api/v1/workshop-package-availability").header("Authorization", FLAKY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
        mockMvc.perform(write(FLAKY, UUID.randomUUID().toString(), workshopPackage.getId(), location.getId(),
                        "{\"state\":\"ACTIVE\"}"))
                .andExpect(status().isServiceUnavailable());
    }

    private WorkshopSession session() {
        LocalDate date = LocalDate.now(ZONE).plusDays(12);
        LocalTime startTime = LocalTime.of(9, 0);
        Instant start = date.atTime(startTime).atZone(ZONE).toInstant();
        WorkshopSlot slot = slotRepository.saveAndFlush(WorkshopSlot.builder().location(location)
                .label("PAC slot " + UUID.randomUUID()).startTime(startTime).endTime(startTime.plusMinutes(30)).build());
        return sessionRepository.saveAndFlush(WorkshopSession.builder().location(location).slot(slot)
                .workshopDate(date).scheduledStartAt(start).scheduledEndAt(start.plusSeconds(1800))
                .checkInOpensAt(start.minusSeconds(1800)).capacity(5).state(SessionState.PUBLISHED).build());
    }

    private long deniedAudits(String action) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM operations_audit_events WHERE action = ? AND outcome = 'DENIED'",
                Long.class, action);
    }

    private long auditEvents(long packageId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM operations_audit_events WHERE action = "
                + "'WORKSHOP_PACKAGE_AVAILABILITY_CHANGED' AND outcome = 'SUCCESS' "
                + "AND JSON_EXTRACT(metadata, '$.packageId') = ?", Long.class, packageId);
    }
}
