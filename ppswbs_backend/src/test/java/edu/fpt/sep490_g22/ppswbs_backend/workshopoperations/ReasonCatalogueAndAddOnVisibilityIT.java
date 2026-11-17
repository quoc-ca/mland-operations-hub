package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopAddOnRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopLocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopPackageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopAddOn;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopAddOnState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackage;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T142 (spec FR-007a, FR-013; analysis 2026-11-15) against MySQL:
 * (a) the standardized reason catalogue is enforced on every route that takes a reason, an unknown reason is
 *     400 VALIDATION_ERROR, and OTHER_REQUIRES_NOTE with neither note nor evidence is 400 VALIDATION_ERROR;
 * (b) a Manager reads DRAFT and RETIRED add-ons through the administration route, the public route returns only
 *     ACTIVE ones, and Staff and Admin get 403 on the administration route.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "workshopoperations-mysql-test"})
@TestPropertySource(properties = {"workshop.operations.auth.allow-test-tokens=true"})
class ReasonCatalogueAndAddOnVisibilityIT {

    private static final String MGR = "Bearer test-uid:mgr";
    private static final String STAFF = "Bearer test-uid:staff";
    private static final String ADMIN = "Bearer test-uid:admin";
    private static final String SESSION = "00000000-0000-4000-8000-000000000001";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private WorkshopLocationRepository locationRepository;
    @Autowired private WorkshopPackageRepository packageRepository;
    @Autowired private WorkshopAddOnRepository addOnRepository;
    @MockBean private AccountFacade accountFacade;
    @MockBean private TokenVerificationService tokenVerificationService;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : List.of("workshop_addons", "facade_command_idempotency_records", "operations_audit_events")) {
            jdbcTemplate.execute("DELETE FROM " + table);
        }
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        actor("mgr", 10L, ActorRole.MANAGER);
        actor("staff", 101L, ActorRole.STAFF);
        actor("admin", 1L, ActorRole.ADMIN);
    }

    private void actor(String uid, long id, ActorRole role) {
        when(accountFacade.resolveAccount(uid)).thenReturn(Optional.of(
                new AccountSummary(id, role, ActorStatus.ACTIVE, uid + "@mland.vn", Instant.now(), Instant.now())));
        when(accountFacade.getAccountStatus(id)).thenReturn(Optional.of(ActorStatus.ACTIVE));
        when(accountFacade.getLocationScope(id)).thenReturn(List.of());
        when(tokenVerificationService.verify("test-uid:" + uid)).thenReturn(
                new VerifiedIdentity(uid, uid + "@mland.vn", true, Instant.now()));
        when(accountFacade.resolveActor(uid, true)).thenReturn(Optional.of(
                new CurrentAccountActor(id, role, ActorStatus.ACTIVE, true, List.of())));
    }

    // ---------------------------------------------------------------- (a) reason catalogue

    private static MockHttpServletRequestBuilder post(String path, String body) {
        return MockMvcRequestBuilders.post(path, SESSION).header("Authorization", MGR)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    /** The five routes that take a reason, each built from a reason fragment such as {@code "reasonCode":"X"}. */
    private Map<String, Function<String, MockHttpServletRequestBuilder>> reasonedRoutes() {
        Map<String, Function<String, MockHttpServletRequestBuilder>> routes = new LinkedHashMap<>();
        routes.put("check-in reversal", reason -> post("/api/v1/workshop-sessions/{id}/check-in-reversals",
                "{\"ticketCode\":\"TKT-1\"," + reason + "}"));
        routes.put("attendance correction", reason -> post("/api/v1/workshop-sessions/{id}/attendance-corrections",
                "{\"ticketCode\":\"TKT-1\",\"correctedAttendanceState\":\"NO_SHOW\"," + reason + "}"));
        routes.put("check-in override", reason -> post("/api/v1/workshop-sessions/{id}/check-in-overrides",
                "{\"ticketCode\":\"TKT-1\"," + reason + "}"));
        routes.put("session cancellation", reason -> post("/api/v1/workshop-sessions/{id}/cancellation-requests",
                "{" + reason + "}").header("X-Operation-Reference", UUID.randomUUID().toString()));
        return routes;
    }

    @Test
    @DisplayName("T142a: an unknown reason code is 400 VALIDATION_ERROR on every reasoned route")
    void unknownReasonCodeIsValidationError() throws Exception {
        for (var route : reasonedRoutes().entrySet()) {
            mockMvc.perform(route.getValue().apply("\"reasonCode\":\"MADE_UP_REASON\",\"reasonNote\":\"note\""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
            mockMvc.perform(route.getValue().apply("\"reasonNote\":\"note\""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    @DisplayName("T142a: OTHER_REQUIRES_NOTE without note or evidence is 400 VALIDATION_ERROR on every reasoned route")
    void otherRequiresNoteWithoutNoteOrEvidenceIsValidationError() throws Exception {
        List<String> notRefused = new ArrayList<>();
        for (var route : reasonedRoutes().entrySet()) {
            for (String fragment : List.of("\"reasonCode\":\"OTHER_REQUIRES_NOTE\"",
                    "\"reasonCode\":\"OTHER_REQUIRES_NOTE\",\"reasonNote\":\"  \",\"evidenceReference\":\"\"")) {
                var result = mockMvc.perform(route.getValue().apply(fragment)).andReturn().getResponse();
                if (result.getStatus() != 400 || !result.getContentAsString().contains("VALIDATION_ERROR")) {
                    notRefused.add(route.getKey() + " [" + fragment + "] -> " + result.getStatus());
                }
            }
            // Control: the same route with a note is NOT refused for its reason (the unknown session gives 404).
            int withNote = mockMvc.perform(route.getValue().apply("\"reasonCode\":\"OTHER_REQUIRES_NOTE\",\"reasonNote\":\"checked by hand\""))
                    .andReturn().getResponse().getStatus();
            assertThat(withNote).as(route.getKey() + " with a note").isNotEqualTo(400);
            int withEvidence = mockMvc.perform(route.getValue().apply("\"reasonCode\":\"OTHER_REQUIRES_NOTE\",\"evidenceReference\":\"CASE-77\""))
                    .andReturn().getResponse().getStatus();
            assertThat(withEvidence).as(route.getKey() + " with evidence").isNotEqualTo(400);
        }
        assertThat(notRefused).as("routes that accepted OTHER_REQUIRES_NOTE without a note or evidence").isEmpty();
    }

    @Test
    @DisplayName("T142a: reschedule-quota change requires a reason (400 VALIDATION_ERROR when blank or missing)")
    void rescheduleQuotaChangeRequiresAReason() throws Exception {
        // The approved contract (RescheduleQuotaRequest) takes a free-text `reason`, not a catalogue code.
        for (String body : List.of("{\"quota\":1}", "{\"quota\":1,\"reason\":\"   \"}")) {
            mockMvc.perform(MockMvcRequestBuilders.put("/api/v1/workshop-sessions/{id}/reschedule-quota", SESSION)
                            .header("Authorization", MGR).header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    // ---------------------------------------------------------------- (b) add-on visibility

    @Test
    @DisplayName("T142b: Manager reads DRAFT, ACTIVE and RETIRED add-ons; public route returns ACTIVE only; Staff and Admin get 403")
    void addOnVisibilityByRouteAndRole() throws Exception {
        WorkshopLocation location = locationRepository.save(WorkshopLocation.builder().code("LOC-T142-" + UUID.randomUUID().toString().substring(0, 6))
                .displayName("Add-on Hub").addressSnapshot("1 Test St").active(true).build());
        Long packageId = packageRepository.save(WorkshopPackage.builder().name("Add-on package").description("fixture")
                .publicPrice(1000L).minParticipants(1).maxParticipants(10).requiredSessionCount(1)
                .sameLocationRequired(true).minimumSameDaySlotGap(0)
                .state(edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackageState.PUBLISHED)
                .build()).getId();
        Map<WorkshopAddOnState, String> refs = new LinkedHashMap<>();
        for (WorkshopAddOnState state : List.of(WorkshopAddOnState.DRAFT, WorkshopAddOnState.ACTIVE, WorkshopAddOnState.RETIRED)) {
            String reference = UUID.randomUUID().toString();
            refs.put(state, reference);
            addOnRepository.save(WorkshopAddOn.builder().reference(reference).packageId(packageId).locationId(location.getId())
                    .description(state + " kit").priceVnd(15_000L).state(state).build());
        }
        String publicRoute = "/api/v1/workshop-packages/" + packageId + "/locations/" + location.getId() + "/add-ons";
        String adminRoute = "/api/v1/workshop-package-management/packages/" + packageId + "/locations/" + location.getId() + "/add-ons";

        // Public route: ACTIVE only, no Authorization needed.
        String publicBody = mockMvc.perform(MockMvcRequestBuilders.get(publicRoute))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].addOnReference").value(refs.get(WorkshopAddOnState.ACTIVE)))
                .andReturn().getResponse().getContentAsString();
        assertThat(publicBody).doesNotContain(refs.get(WorkshopAddOnState.DRAFT)).doesNotContain(refs.get(WorkshopAddOnState.RETIRED));

        // Administration route as Manager: all three states.
        String adminBody = mockMvc.perform(MockMvcRequestBuilders.get(adminRoute).header("Authorization", MGR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        assertThat(adminBody).contains("\"DRAFT\"").contains("\"ACTIVE\"").contains("\"RETIRED\"")
                .contains(refs.get(WorkshopAddOnState.DRAFT)).contains(refs.get(WorkshopAddOnState.RETIRED));

        // Staff and Admin are refused; a missing token is 401.
        mockMvc.perform(MockMvcRequestBuilders.get(adminRoute).header("Authorization", STAFF)).andExpect(status().isForbidden());
        mockMvc.perform(MockMvcRequestBuilders.get(adminRoute).header("Authorization", ADMIN)).andExpect(status().isForbidden());
        mockMvc.perform(MockMvcRequestBuilders.get(adminRoute)).andExpect(status().isUnauthorized());
    }
}
