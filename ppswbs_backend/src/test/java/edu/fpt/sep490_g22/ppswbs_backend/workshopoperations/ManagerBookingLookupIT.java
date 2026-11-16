package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T116 (FR-003b, Clarifications 2026-11-15 Q4): Manager-only audited cross-session booking lookup against MySQL.
 * Staff, Admin and anonymous callers are refused; an unknown code is 404 RESOURCE_NOT_FOUND; the body never carries
 * contact data, a QR value or payment data; every success writes one MANAGER_BOOKING_LOOKUP audit row.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "workshopoperations-mysql-test"})
@TestPropertySource(properties = {"workshop.operations.auth.allow-test-tokens=true"})
class ManagerBookingLookupIT {

    private static final String ROUTE = "/api/v1/workshop-booking-lookups/{bookingCode}";
    private static final String MGR = "Bearer test-uid:mgr-a";
    private static final String STAFF = "Bearer test-uid:staff-a";
    private static final String ADMIN = "Bearer test-uid:admin-a";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private WorkshopLocationRepository locationRepository;
    @Autowired private WorkshopSlotRepository slotRepository;
    @Autowired private WorkshopPackageRepository packageRepository;
    @Autowired private WorkshopSessionRepository sessionRepository;
    @Autowired private AttendanceTicketRepository ticketRepository;
    @Autowired private AttendanceActionRepository attendanceActionRepository;
    @Autowired private OperationsAuditEventRepository auditEventRepository;
    @Autowired private WorkshopOperationsFacade operationsFacade;
    @MockBean private AccountFacade accountFacade;
    @MockBean private TokenVerificationService tokenVerificationService;

    private WorkshopLocation locationA;
    private WorkshopLocation locationB;
    private WorkshopSlot slotA;
    private WorkshopSlot slotB;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : List.of("facade_command_idempotency_records", "attendance_actions", "ticket_session_entitlements",
                "attendance_tickets", "capacity_holds", "session_staff_assignments",
                "cancellation_handoffs", "operations_audit_events", "workshop_sessions", "workshop_slots", "workshop_locations")) {
            jdbcTemplate.execute("TRUNCATE TABLE " + table);
        }
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");

        locationA = locationRepository.save(WorkshopLocation.builder().code("LOC-LK-01").displayName("Lookup Hub A")
                .addressSnapshot("1 Test St").active(true).build());
        locationB = locationRepository.save(WorkshopLocation.builder().code("LOC-LK-02").displayName("Lookup Hub B")
                .addressSnapshot("2 Test St").active(true).build());
        slotA = slotRepository.save(WorkshopSlot.builder().reference(UUID.randomUUID().toString()).location(locationA)
                .label("Morning").startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(12, 0)).defaultCapacity(10).active(true).build());
        slotB = slotRepository.save(WorkshopSlot.builder().reference(UUID.randomUUID().toString()).location(locationB)
                .label("Morning B").startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(12, 0)).defaultCapacity(10).active(true).build());

        account("mgr-a", 10L, ActorRole.MANAGER);
        account("staff-a", 101L, ActorRole.STAFF);
        account("admin-a", 1L, ActorRole.ADMIN);
    }

    private void account(String uid, long id, ActorRole role) {
        when(accountFacade.resolveAccount(uid)).thenReturn(Optional.of(
                new AccountSummary(id, role, ActorStatus.ACTIVE, uid + "@mland.vn", Instant.now(), Instant.now())));
        when(accountFacade.getAccountStatus(id)).thenReturn(Optional.of(ActorStatus.ACTIVE));
        when(accountFacade.getLocationScope(id)).thenReturn(List.of(locationA.getId(), locationB.getId()));
        when(tokenVerificationService.verify("test-uid:" + uid)).thenReturn(
                new VerifiedIdentity(uid, uid + "@mland.vn", true, Instant.now()));
        when(accountFacade.resolveActor(uid, true)).thenReturn(Optional.of(new CurrentAccountActor(
                id, role, ActorStatus.ACTIVE, true, List.of(locationA.getId(), locationB.getId()))));
    }

    private WorkshopSession session(WorkshopLocation loc, WorkshopSlot slot, int daysAhead) {
        LocalDate date = LocalDate.now().plusDays(daysAhead);
        return sessionRepository.save(WorkshopSession.builder().reference(UUID.randomUUID().toString()).location(loc).slot(slot)
                .workshopDate(date)
                .scheduledStartAt(date.atTime(slot.getStartTime()).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant())
                .scheduledEndAt(date.atTime(slot.getEndTime()).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant())
                .checkInOpensAt(Instant.now().minusSeconds(1800)).checkInOpeningSource(CheckInOpeningSource.DEFAULT)
                .capacity(10).state(SessionState.PUBLISHED).build());
    }

    private void issue(String bookingRef, List<WorkshopSession> sessions, int seats) {
        Long packageId = packageRepository.save(WorkshopPackage.builder().name("Lookup package").description("fixture")
                .publicPrice(1000L).minParticipants(1).maxParticipants(10).requiredSessionCount(sessions.size())
                .sameLocationRequired(false).minimumSameDaySlotGap(0)
                .state(edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackageState.PUBLISHED)
                .build()).getId();
        CapacityHoldOutcome hold = operationsFacade.reserveBookingCapacity(new ReserveBookingCapacityCommand(bookingRef,
                String.valueOf(packageId), sessions.stream().map(s -> UUID.fromString(s.getReference())).toList(),
                seats, "op-hold-" + UUID.randomUUID(), "corr-hold"));
        assertThat(hold.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        CapacityCommitOutcome commit = operationsFacade.commitBookingHolds(new CommitBookingHoldsCommand(bookingRef,
                hold.holdReferences(), "op-comm-" + UUID.randomUUID(), "corr-comm"));
        assertThat(commit.status()).isEqualTo(OutcomeStatus.ACCEPTED);
    }

    private static boolean isLookupAudit(OperationsAuditEvent e) {
        return "MANAGER_BOOKING_LOOKUP".equals(e.getAction());
    }

    @Test
    @DisplayName("T116: Manager gets every ticket across sessions and locations, any validity state, and a success audit row")
    void managerSeesFullTicketSetAcrossSessionsAndLocations_andIsAudited() throws Exception {
        WorkshopSession s1 = session(locationA, slotA, 2);
        WorkshopSession s2 = session(locationB, slotB, 3);
        String bookingRef = "BK-MGR-" + UUID.randomUUID().toString().substring(0, 8);
        issue(bookingRef, List.of(s1, s2), 2);
        AttendanceTicket cancelled = ticketRepository.findByBookingReference(bookingRef).get(0);
        cancelled.setValidityState(TicketValidityState.CANCELLED);
        ticketRepository.save(cancelled);

        MvcResult result = mockMvc.perform(get(ROUTE, bookingRef).header("Authorization", MGR).header("X-Correlation-Id", "corr-t116"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingCode").value(bookingRef))
                .andExpect(jsonPath("$.tickets.length()").value(4))
                .andReturn();

        JsonNode tickets = objectMapper.readTree(result.getResponse().getContentAsString()).get("tickets");
        List<String> sessionIds = new ArrayList<>();
        List<Long> locationIds = new ArrayList<>();
        List<String> validity = new ArrayList<>();
        for (JsonNode t : tickets) {
            sessionIds.add(t.get("sessionId").asText());
            locationIds.add(t.get("locationId").asLong());
            validity.add(t.get("validityState").asText());
            assertThat(t.get("attendanceState").asText()).isEqualTo("NOT_CHECKED_IN");
        }
        assertThat(sessionIds).containsOnly(s1.getReference(), s2.getReference());
        assertThat(locationIds).containsOnly(locationA.getId(), locationB.getId());
        assertThat(validity).contains("ACTIVE", "CANCELLED");

        List<OperationsAuditEvent> audit = auditEventRepository.findByActorIdOrderByOccurredAtDesc(10L).stream()
                .filter(ManagerBookingLookupIT::isLookupAudit).toList();
        assertThat(audit).hasSize(1);
        assertThat(audit.get(0).getOutcome()).isEqualTo("SUCCESS");
        assertThat(audit.get(0).getCorrelationId()).isEqualTo("corr-t116");
        assertThat(audit.get(0).getMetadata()).contains(bookingRef).doesNotContain("@");
        assertThat(attendanceActionRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("T116: response carries no contact data, QR value or payment data")
    void lookupExposesNoPii() throws Exception {
        WorkshopSession s1 = session(locationA, slotA, 2);
        String bookingRef = "BK-PII-" + UUID.randomUUID().toString().substring(0, 8);
        issue(bookingRef, List.of(s1), 1);
        String qr = ticketRepository.findByBookingReference(bookingRef).get(0).getQrReference();

        String body = mockMvc.perform(get(ROUTE, bookingRef).header("Authorization", MGR))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(body);
        List<String> rootFields = new ArrayList<>();
        root.fieldNames().forEachRemaining(rootFields::add);
        List<String> ticketFields = new ArrayList<>();
        root.get("tickets").get(0).fieldNames().forEachRemaining(ticketFields::add);
        assertThat(rootFields).containsExactlyInAnyOrder("bookingCode", "tickets");
        assertThat(ticketFields).containsExactlyInAnyOrder(
                "ticketCode", "sessionId", "locationId", "validityState", "attendanceState", "checkedInAt");
        assertThat(body).doesNotContain("@").doesNotContainIgnoringCase("email").doesNotContainIgnoringCase("phone")
                .doesNotContainIgnoringCase("payment").doesNotContain(qr);
    }

    @Test
    @DisplayName("T116: Staff, Admin and anonymous callers are refused and write no success audit")
    void nonManagerCallersAreRefused() throws Exception {
        WorkshopSession s1 = session(locationA, slotA, 2);
        String bookingRef = "BK-DENY-" + UUID.randomUUID().toString().substring(0, 8);
        issue(bookingRef, List.of(s1), 1);

        mockMvc.perform(get(ROUTE, bookingRef).header("Authorization", STAFF)).andExpect(status().isForbidden());
        mockMvc.perform(get(ROUTE, bookingRef).header("Authorization", ADMIN)).andExpect(status().isForbidden());
        mockMvc.perform(get(ROUTE, bookingRef)).andExpect(status().isUnauthorized());

        // No success audit on refusal; the Staff and Admin 403s are denied-attempt audits (T131), the 401 is not audited.
        List<OperationsAuditEvent> lookupAudits = auditEventRepository.findAll().stream()
                .filter(ManagerBookingLookupIT::isLookupAudit).toList();
        assertThat(lookupAudits).noneMatch(e -> "SUCCESS".equals(e.getOutcome()));
        assertThat(lookupAudits).hasSize(2).allMatch(e -> "DENIED".equals(e.getOutcome()));
    }

    @Test
    @DisplayName("T116: unknown booking code is 404 RESOURCE_NOT_FOUND and writes no success audit")
    void unknownCodeIsNotFound() throws Exception {
        mockMvc.perform(get(ROUTE, "BK-NONEXISTENT").header("Authorization", MGR))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        assertThat(auditEventRepository.findAll()).noneMatch(ManagerBookingLookupIT::isLookupAudit);
    }
}
