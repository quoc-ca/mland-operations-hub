package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
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
 * T121 (owner decision 2026-11-06; FR-010e, FR-012, FR-012a, SC-007): every Staff operation needs the session
 * location in the Staff location scope AND an active assignment to the session, otherwise 403; revoking the assignment
 * takes effect on the next request. One MySQL test drives all eight Staff operations (check-in, booking lookup,
 * roster, resend, override, finalization, state transition, session read) through the same four actors.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "workshopoperations-mysql-test"})
@TestPropertySource(properties = {"workshop.operations.auth.allow-test-tokens=true"})
class StaffSessionAssignmentRuleIT {

    private static final String ASSIGNED = "Bearer test-uid:staff-assigned";
    private static final String UNASSIGNED = "Bearer test-uid:staff-unassigned";
    private static final String OUT_OF_SCOPE = "Bearer test-uid:staff-out-of-scope";
    private static final String REVOKED = "Bearer test-uid:staff-revoked";
    private static final String MANAGER = "Bearer test-uid:mgr";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private WorkshopLocationRepository locationRepository;
    @Autowired private WorkshopSlotRepository slotRepository;
    @Autowired private WorkshopPackageRepository packageRepository;
    @Autowired private WorkshopSessionRepository sessionRepository;
    @Autowired private SessionStaffAssignmentRepository staffAssignmentRepository;
    @Autowired private AttendanceTicketRepository ticketRepository;
    @Autowired private WorkshopOperationsFacade operationsFacade;
    @MockBean private AccountFacade accountFacade;
    @MockBean private TokenVerificationService tokenVerificationService;

    private WorkshopLocation locationA;
    private WorkshopLocation locationB;
    private WorkshopSlot slotA;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : List.of("facade_command_idempotency_records", "attendance_actions", "ticket_session_entitlements",
                "attendance_tickets", "capacity_holds", "session_staff_assignments",
                "cancellation_handoffs", "operations_audit_events", "workshop_sessions", "workshop_slots", "workshop_locations")) {
            jdbcTemplate.execute("TRUNCATE TABLE " + table);
        }
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");

        locationA = locationRepository.save(WorkshopLocation.builder().code("LOC-SA-01").displayName("Assignment Hub A")
                .addressSnapshot("1 Test St").active(true).build());
        locationB = locationRepository.save(WorkshopLocation.builder().code("LOC-SA-02").displayName("Assignment Hub B")
                .addressSnapshot("2 Test St").active(true).build());
        slotA = slotRepository.save(WorkshopSlot.builder().reference(UUID.randomUUID().toString()).location(locationA)
                .label("Morning").startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(12, 0)).active(true).build());

        actor("staff-assigned", 101L, ActorRole.STAFF, locationA.getId());
        actor("staff-unassigned", 102L, ActorRole.STAFF, locationA.getId());
        actor("staff-out-of-scope", 103L, ActorRole.STAFF, locationB.getId());
        actor("staff-revoked", 104L, ActorRole.STAFF, locationA.getId());
        actor("mgr", 10L, ActorRole.MANAGER, locationA.getId());
    }

    private void actor(String uid, long id, ActorRole role, Long scope) {
        when(accountFacade.resolveAccount(uid)).thenReturn(Optional.of(
                new AccountSummary(id, role, ActorStatus.ACTIVE, uid + "@mland.vn", Instant.now(), Instant.now())));
        when(accountFacade.getAccountStatus(id)).thenReturn(Optional.of(ActorStatus.ACTIVE));
        when(accountFacade.getLocationScope(id)).thenReturn(List.of(scope));
        when(tokenVerificationService.verify("test-uid:" + uid)).thenReturn(
                new VerifiedIdentity(uid, uid + "@mland.vn", true, Instant.now()));
        when(accountFacade.resolveActor(uid, true)).thenReturn(Optional.of(
                new CurrentAccountActor(id, role, ActorStatus.ACTIVE, true, List.of(scope))));
    }

    private WorkshopSession session(SessionState state, int daysAhead) {
        LocalDate date = LocalDate.now().plusDays(daysAhead);
        WorkshopSession s = sessionRepository.save(WorkshopSession.builder().reference(UUID.randomUUID().toString())
                .location(locationA).slot(slotA).workshopDate(date)
                .scheduledStartAt(date.atTime(9, 0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant())
                .scheduledEndAt(date.atTime(12, 0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant())
                .checkInOpensAt(Instant.now().minusSeconds(1800)).checkInOpeningSource(CheckInOpeningSource.DEFAULT)
                .capacity(10).state(SessionState.PUBLISHED).build());
        // Active assignments: the assigned Staff, the Staff whose assignment is revoked later, and the out-of-scope
        // Staff (an assignment alone never overrides the location scope).
        for (long accountId : new long[]{101L, 103L, 104L}) {
            staffAssignmentRepository.save(SessionStaffAssignment.builder().session(s).accountId(accountId)
                    .assignedById(10L).assignedAt(Instant.now()).active(true).build());
        }
        if (state != SessionState.PUBLISHED) {
            s.setState(state);
            s = sessionRepository.save(s);
        }
        return s;
    }

    private String issueTicket(WorkshopSession s, String bookingRef) {
        Long packageId = packageRepository.save(WorkshopPackage.builder().name("Assignment package").description("fixture")
                .publicPrice(1000L).minParticipants(1).maxParticipants(10).requiredSessionCount(1)
                .sameLocationRequired(true).minimumSameDaySlotGap(0)
                .state(edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackageState.PUBLISHED)
                .build()).getId();
        PackageAvailabilityFixture.activateEverywhere(jdbcTemplate, packageId);
        CapacityHoldOutcome hold = operationsFacade.reserveBookingCapacity(new ReserveBookingCapacityCommand(bookingRef,
                String.valueOf(packageId), List.of(UUID.fromString(s.getReference())), 1, "op-hold-" + UUID.randomUUID(), "corr"));
        assertThat(hold.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        CapacityCommitOutcome commit = operationsFacade.commitBookingHolds(new CommitBookingHoldsCommand(bookingRef,
                hold.holdReferences(), "op-comm-" + UUID.randomUUID(), "corr"));
        assertThat(commit.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        return ticketRepository.findByBookingReference(bookingRef).get(0).getTicketCode();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        return b.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    /** The Staff operations of T121 as request builders keyed by name; each takes the session reference. */
    private Map<String, Function<String, MockHttpServletRequestBuilder>> operations(String bookingRef, String ticketCode) {
        Map<String, Function<String, MockHttpServletRequestBuilder>> ops = new LinkedHashMap<>();
        ops.put("check-in", ref -> json(MockMvcRequestBuilders.post("/api/v1/workshop-sessions/{id}/check-ins", ref),
                "{\"ticketCode\":\"" + ticketCode + "\"}"));
        ops.put("booking lookup", ref -> MockMvcRequestBuilders.get("/api/v1/workshop-sessions/{id}/booking-lookups/{code}", ref, bookingRef));
        ops.put("roster", ref -> MockMvcRequestBuilders.get("/api/v1/workshop-sessions/{id}/attendance-roster", ref));
        ops.put("resend", ref -> json(MockMvcRequestBuilders.post("/api/v1/workshop-sessions/{id}/ticket-resends", ref)
                .header("Idempotency-Key", UUID.randomUUID().toString()), "{\"bookingCode\":\"" + bookingRef + "\"}"));
        ops.put("override", ref -> json(MockMvcRequestBuilders.post("/api/v1/workshop-sessions/{id}/check-in-overrides", ref),
                "{\"ticketCode\":\"" + ticketCode + "\",\"reasonCode\":\"PHYSICAL_VOUCHER_VERIFIED\"}"));
        ops.put("finalization", ref -> json(MockMvcRequestBuilders.post("/api/v1/workshop-sessions/{id}/attendance-finalizations", ref),
                "{\"confirmedSessionEnded\":true}"));
        ops.put("state transition", ref -> json(MockMvcRequestBuilders.post("/api/v1/workshop-sessions/{id}/state-transitions", ref)
                .header("Idempotency-Key", UUID.randomUUID().toString()), "{\"targetState\":\"IN_PROGRESS\"}"));
        ops.put("session read", ref -> MockMvcRequestBuilders.get("/api/v1/workshop-sessions/{id}", ref));
        return ops;
    }

    private int statusOf(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header("Authorization", token)).andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("T121: every Staff operation needs location scope and an active assignment; revocation is immediate")
    void everyStaffOperationRequiresScopeAndActiveAssignment() throws Exception {
        // IN_PROGRESS so check-in, lookup, roster, resend, override and finalization reach their business checks;
        // the state-transition operation runs against a PUBLISHED session where IN_PROGRESS is a legal target.
        WorkshopSession inProgress = session(SessionState.IN_PROGRESS, 2);
        String bookingRef = "BK-ASG-" + UUID.randomUUID().toString().substring(0, 8);
        String ticketCode = issueTicketOn(inProgress, bookingRef);
        WorkshopSession published = session(SessionState.PUBLISHED, 3);

        Map<String, Function<String, MockHttpServletRequestBuilder>> ops = operations(bookingRef, ticketCode);
        List<String> failures = new ArrayList<>();

        for (var op : ops.entrySet()) {
            String ref = "state transition".equals(op.getKey()) ? published.getReference() : inProgress.getReference();
            // Unassigned Staff in the session location: 403 FORBIDDEN.
            expectForbidden(failures, op.getKey() + " / unassigned", statusOf(op.getValue().apply(ref), UNASSIGNED));
            // Assigned Staff outside the location scope: 403 LOCATION_SCOPE_DENIED (assignment alone is not enough).
            expectForbidden(failures, op.getKey() + " / out of scope", statusOf(op.getValue().apply(ref), OUT_OF_SCOPE));
            // No token: 401.
            if (mockMvc.perform(op.getValue().apply(ref)).andReturn().getResponse().getStatus() != 401) {
                failures.add(op.getKey() + " / no token should be 401");
            }
        }

        // Revocation takes effect on the next request, for every operation.
        for (long id : new long[]{104L}) {
            for (var op : ops.entrySet()) {
                if ("finalization".equals(op.getKey()) || "state transition".equals(op.getKey())) {
                    continue; // these change the session state; the assigned Staff runs them last
                }
                String ref = "state transition".equals(op.getKey()) ? published.getReference() : inProgress.getReference();
                int before = statusOf(op.getValue().apply(ref), REVOKED);
                if (before == 403 || before == 401) {
                    failures.add(op.getKey() + " / assigned-then-revoked: active assignment was refused with " + before);
                }
            }
            deactivate(id);
            for (var op : ops.entrySet()) {
                String ref = "state transition".equals(op.getKey()) ? published.getReference() : inProgress.getReference();
                expectForbidden(failures, op.getKey() + " / revoked", statusOf(op.getValue().apply(ref), REVOKED));
            }
        }

        assertThat(failures).as("Staff operations that did not follow the assignment rule").isEmpty();

        // The assigned Staff and a Manager are not refused (business outcome may be 200/409, never 401/403).
        for (var op : ops.entrySet()) {
            String ref = "state transition".equals(op.getKey()) ? published.getReference() : inProgress.getReference();
            int assigned = statusOf(op.getValue().apply(ref), ASSIGNED);
            assertThat(assigned).as(op.getKey() + " / assigned").isNotIn(401, 403);
        }
        mockMvc.perform(ops.get("roster").apply(inProgress.getReference()).header("Authorization", MANAGER))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("T121: finalization by an unassigned Staff is 403 whatever the session state (authority before state)")
    void unassignedStaffFinalizationIsForbiddenBeforeAnyStateCheck() throws Exception {
        WorkshopSession published = session(SessionState.PUBLISHED, 2);
        mockMvc.perform(json(MockMvcRequestBuilders.post("/api/v1/workshop-sessions/{id}/attendance-finalizations",
                        published.getReference()), "{\"confirmedSessionEnded\":true}").header("Authorization", UNASSIGNED))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private String issueTicketOn(WorkshopSession target, String bookingRef) {
        // Tickets can only be reserved on a PUBLISHED session: flip the state around the issue.
        SessionState wanted = target.getState();
        jdbcTemplate.update("UPDATE workshop_sessions SET state = ? WHERE id = ?", SessionState.PUBLISHED.name(), target.getId());
        String code = issueTicket(target, bookingRef);
        jdbcTemplate.update("UPDATE workshop_sessions SET state = ? WHERE id = ?", wanted.name(), target.getId());
        return code;
    }

    private void deactivate(long accountId) {
        for (SessionStaffAssignment a : staffAssignmentRepository.findByAccountIdAndActiveTrue(accountId)) {
            a.setActive(false);
            staffAssignmentRepository.save(a);
        }
    }

    private static void expectForbidden(List<String> failures, String label, int status) {
        if (status != 403) {
            failures.add(label + " expected 403 but was " + status);
        }
    }
}
