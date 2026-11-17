package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import com.fasterxml.jackson.databind.JsonNode;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 003.2 T141 / FR-007c on MySQL: every Manager-only action that creates or changes data writes one
 * {@link OperationsAuditEvent} with actor, time, correlation ID and the prior/new values. Reversal, correction,
 * cancellation, direct cancellation and the booking lookup are asserted in WorkshopClosureIT and
 * ManagerBookingLookupIT; location edit is in WorkshopLocationUpdateIT.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("workshopoperations-mysql-test")
@TestPropertySource(properties = {"workshop.operations.auth.allow-test-tokens=true"})
class ManagerActionAuditIT {

    private static final String MANAGER = "Bearer test-uid:manager";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockBean private AccountFacade accountFacade;
    @MockBean private TokenVerificationService tokenVerificationService;
    @Autowired private WorkshopLocationRepository locationRepository;
    @Autowired private WorkshopSlotRepository slotRepository;
    @Autowired private WorkshopSessionRepository sessionRepository;
    @Autowired private OperationsAuditEventRepository auditEventRepository;

    private WorkshopLocation location;
    private WorkshopSlot slot;

    @BeforeEach
    void setUp() throws Exception {
        try (var connection = jdbcTemplate.getDataSource().getConnection()) {
            assumeTrue(connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql"),
                    "ManagerActionAuditIT requires real MySQL evidence (WORKSHOP_OPERATIONS_TEST_JDBC_*)");
        }
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : List.of("attendance_actions", "ticket_session_entitlements", "attendance_tickets", "capacity_holds",
                "session_staff_assignments", "cancellation_handoffs", "operations_audit_events", "facade_command_idempotency",
                "workshop_sessions", "workshop_slots", "workshop_locations")) {
            try { jdbcTemplate.execute("TRUNCATE TABLE " + table); } catch (RuntimeException ignored) { /* table absent on this profile */ }
        }
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");

        location = locationRepository.save(WorkshopLocation.builder().code("LOC-AUD").displayName("Audit Location")
                .addressSnapshot("1 Audit Street").active(true).build());
        slot = slotRepository.save(WorkshopSlot.builder().reference(UUID.randomUUID().toString()).location(location)
                .label("Morning").startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(12, 0)).active(true).build());

        when(accountFacade.resolveAccount("manager")).thenReturn(Optional.of(
                new AccountSummary(10L, ActorRole.MANAGER, ActorStatus.ACTIVE, "manager@mland.vn", Instant.now(), Instant.now())));
        when(accountFacade.getAccountStatus(10L)).thenReturn(Optional.of(ActorStatus.ACTIVE));
        when(accountFacade.getLocationScope(10L)).thenReturn(List.of(location.getId()));
        when(tokenVerificationService.verify("test-uid:manager"))
                .thenReturn(new VerifiedIdentity("manager", "manager@mland.vn", true, Instant.now()));
        when(accountFacade.resolveActor("manager", true)).thenReturn(Optional.of(
                new CurrentAccountActor(10L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of(location.getId()))));
        when(accountFacade.getTargetAccount(anyLong())).thenReturn(Optional.empty());
        when(accountFacade.getTargetAccount(101L)).thenReturn(Optional.of(
                new TargetAccountSnapshot(101L, ActorRole.STAFF, ActorStatus.ACTIVE, List.of(location.getId()))));
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        MockHttpServletRequestBuilder withKey = builder.header("Authorization", MANAGER)
                .header("Idempotency-Key", UUID.randomUUID().toString());
        return body == null ? withKey : withKey.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private List<OperationsAuditEvent> events(String action) {
        return auditEventRepository.findAll().stream().filter(e -> action.equals(e.getAction())).toList();
    }

    private OperationsAuditEvent single(String action) {
        List<OperationsAuditEvent> found = events(action);
        assertThat(found).as("exactly one %s event", action).hasSize(1);
        OperationsAuditEvent event = found.get(0);
        assertThat(event.getActorId()).isEqualTo(10L);
        assertThat(event.getOutcome()).isEqualTo("SUCCESS");
        assertThat(event.getCorrelationId()).isNotBlank();
        assertThat(event.getOccurredAt()).isNotNull();
        return event;
    }

    private WorkshopSession session(SessionState state, int dayOffset) {
        return sessionRepository.save(WorkshopSession.builder().reference(UUID.randomUUID().toString()).location(location).slot(slot)
                .workshopDate(LocalDate.now().plusDays(dayOffset)).capacity(20).state(state)
                .scheduledStartAt(Instant.now().plusSeconds(3600)).scheduledEndAt(Instant.now().plusSeconds(7200))
                .checkInOpensAt(Instant.now().minusSeconds(60)).checkInOpeningSource(CheckInOpeningSource.DEFAULT).build());
    }

    @Test
    @DisplayName("T141: create location writes LOCATION_CREATED with the created values")
    void createLocation() throws Exception {
        mockMvc.perform(json(post("/api/v1/workshop-locations"), "{\"code\":\"LOC-NEW\",\"name\":\"New\",\"address\":\"9 Road\",\"active\":true}"))
                .andExpect(status().isCreated());
        OperationsAuditEvent event = single("LOCATION_CREATED");
        assertThat(event.getSessionId()).isNull();
        JsonNode metadata = objectMapper.readTree(event.getMetadata());
        assertThat(metadata.at("/new/code").asText()).isEqualTo("LOC-NEW");
        assertThat(metadata.at("/new/name").asText()).isEqualTo("New");
    }

    @Test
    @DisplayName("T141: create, change and stop a slot write one event per change with prior and new values")
    void slotCreateChangeAndStop() throws Exception {
        mockMvc.perform(json(post("/api/v1/workshop-locations/" + location.getId() + "/slots"),
                        "{\"label\":\"Evening\",\"startsAtLocal\":\"18:00\",\"endsAtLocal\":\"20:00\",\"active\":true}"))
                .andExpect(status().isCreated());
        JsonNode created = objectMapper.readTree(single("SLOT_CREATED").getMetadata());
        assertThat(created.at("/new/label").asText()).isEqualTo("Evening");
        String reference = created.get("slotReference").asText();

        mockMvc.perform(json(patch("/api/v1/workshop-slots/" + reference), "{\"label\":\"Late Evening\",\"startsAtLocal\":\"18:00\",\"endsAtLocal\":\"20:00\",\"active\":true}"))
                .andExpect(status().isOk());
        JsonNode changed = objectMapper.readTree(single("SLOT_UPDATED").getMetadata());
        assertThat(changed.at("/prior/label").asText()).isEqualTo("Evening");
        assertThat(changed.at("/new/label").asText()).isEqualTo("Late Evening");

        mockMvc.perform(json(patch("/api/v1/workshop-slots/" + reference), "{\"label\":\"Late Evening\",\"startsAtLocal\":\"18:00\",\"endsAtLocal\":\"20:00\",\"active\":false}")).andExpect(status().isOk());
        JsonNode stopped = objectMapper.readTree(single("SLOT_DEACTIVATED").getMetadata());
        assertThat(stopped.at("/prior/active").asBoolean()).isTrue();
        assertThat(stopped.at("/new/active").asBoolean()).isFalse();
        assertThat(events("SLOT_UPDATED")).hasSize(1);
    }

    @Test
    @DisplayName("T141: create a session individually writes one SESSION_CREATED event tied to the session")
    void createSessionIndividually() throws Exception {
        mockMvc.perform(json(post("/api/v1/workshop-sessions"), "{\"locationId\":" + location.getId() + ",\"slotId\":\"" + slot.getReference()
                        + "\",\"workshopDate\":\"" + LocalDate.now().plusDays(5) + "\",\"capacity\":8}"))
                .andExpect(status().isCreated());
        OperationsAuditEvent event = single("SESSION_CREATED");
        assertThat(event.getSessionId()).isNotNull();
        JsonNode metadata = objectMapper.readTree(event.getMetadata());
        assertThat(metadata.get("capacity").asInt()).isEqualTo(8);
        assertThat(metadata.get("state").asText()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("T141: bulk generation writes one SESSION_CREATED event per created session")
    void createSessionsInBulk() throws Exception {
        mockMvc.perform(json(post("/api/v1/workshop-sessions/bulk"), "{\"locationId\":" + location.getId() + ",\"slotIds\":[\"" + slot.getReference()
                        + "\"],\"from\":\"" + LocalDate.now().plusDays(2) + "\",\"to\":\"" + LocalDate.now().plusDays(4)
                        + "\",\"capacity\":10,\"weekdays\":[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\",\"FRIDAY\",\"SATURDAY\",\"SUNDAY\"],\"initialState\":\"DRAFT\"}"))
                .andExpect(status().is2xxSuccessful());
        assertThat(events("SESSION_CREATED")).hasSize(3);
        assertThat(events("SESSION_CREATED")).allSatisfy(e -> {
            assertThat(e.getActorId()).isEqualTo(10L);
            assertThat(e.getSessionId()).isNotNull();
        });
        assertThat(sessionRepository.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("T141: change a session's capacity writes SESSION_CAPACITY_CHANGED with prior and new capacity; an unchanged value writes none")
    void changeCapacity() throws Exception {
        WorkshopSession session = session(SessionState.DRAFT, 6);
        mockMvc.perform(json(patch("/api/v1/workshop-sessions/" + session.getReference()), "{\"capacity\":20}")).andExpect(status().isOk());
        assertThat(events("SESSION_CAPACITY_CHANGED")).isEmpty();
        mockMvc.perform(json(patch("/api/v1/workshop-sessions/" + session.getReference()), "{\"capacity\":14}")).andExpect(status().isOk());
        OperationsAuditEvent event = single("SESSION_CAPACITY_CHANGED");
        assertThat(event.getSessionId()).isEqualTo(session.getId());
        JsonNode metadata = objectMapper.readTree(event.getMetadata());
        assertThat(metadata.get("priorCapacity").asInt()).isEqualTo(20);
        assertThat(metadata.get("newCapacity").asInt()).isEqualTo(14);
    }

    @Test
    @DisplayName("T141: publish, pause and resume each write one SESSION_STATE_TRANSITION with prior and new state")
    void publishPauseResume() throws Exception {
        WorkshopSession session = session(SessionState.DRAFT, 7);
        String path = "/api/v1/workshop-sessions/" + session.getReference() + "/state-transitions";
        for (String target : List.of("PUBLISHED", "SALES_PAUSED", "PUBLISHED")) {
            mockMvc.perform(json(post(path), "{\"targetState\":\"" + target + "\"}")).andExpect(status().isOk());
        }
        List<OperationsAuditEvent> transitions = events("SESSION_STATE_TRANSITION").stream()
                .sorted(java.util.Comparator.comparing(OperationsAuditEvent::getId)).toList();
        assertThat(transitions).hasSize(3);
        String[][] expected = {{"DRAFT", "PUBLISHED"}, {"PUBLISHED", "SALES_PAUSED"}, {"SALES_PAUSED", "PUBLISHED"}};
        for (int i = 0; i < 3; i++) {
            JsonNode metadata = objectMapper.readTree(transitions.get(i).getMetadata());
            assertThat(metadata.get("fromState").asText()).isEqualTo(expected[i][0]);
            assertThat(metadata.get("toState").asText()).isEqualTo(expected[i][1]);
            assertThat(transitions.get(i).getActorId()).isEqualTo(10L);
            assertThat(transitions.get(i).getCorrelationId()).isNotBlank();
        }
    }

    @Test
    @DisplayName("T141: set and clear the reschedule quota each write RESCHEDULE_QUOTA_SET")
    void setAndClearQuota() throws Exception {
        WorkshopSession session = session(SessionState.PUBLISHED, 8);
        String path = "/api/v1/workshop-sessions/" + session.getReference() + "/reschedule-quota";
        mockMvc.perform(json(put(path), "{\"quota\":3,\"reason\":\"VIP request\"}")).andExpect(status().isOk());
        mockMvc.perform(json(put(path), "{\"quota\":null,\"reason\":\"back to default\"}")).andExpect(status().isOk());
        List<OperationsAuditEvent> found = events("RESCHEDULE_QUOTA_SET");
        assertThat(found).hasSize(2).allSatisfy(e -> {
            assertThat(e.getActorId()).isEqualTo(10L);
            assertThat(e.getSessionId()).isEqualTo(session.getId());
        });
    }

    @Test
    @DisplayName("T141: assign and revoke a Staff member each write one event")
    void assignAndRevokeStaff() throws Exception {
        WorkshopSession session = session(SessionState.DRAFT, 9);
        String path = "/api/v1/workshop-sessions/" + session.getReference() + "/staff-assignments";
        mockMvc.perform(json(put(path), "{\"accountId\":101}")).andExpect(status().is2xxSuccessful());
        assertThat(single("SESSION_STAFF_ASSIGNED").getSessionId()).isEqualTo(session.getId());
        mockMvc.perform(json(delete(path).param("accountId", "101"), null)).andExpect(status().is2xxSuccessful());
        assertThat(events("SESSION_STAFF_REMOVED").stream().filter(e -> "SUCCESS".equals(e.getOutcome()))).hasSize(1);
    }
}
