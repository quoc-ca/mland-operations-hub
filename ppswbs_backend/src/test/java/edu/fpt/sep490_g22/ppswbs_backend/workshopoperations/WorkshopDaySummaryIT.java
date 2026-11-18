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
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.AttendanceTicketRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketSessionEntitlementRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopLocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSlotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceTicket;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.CheckInOpeningSource;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketSessionEntitlement;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketValidityState;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 013 Manager Home R-1 (T023) on MySQL through the real security chain. A far-future day isolates the fixture from
 * rows other suites leave in the shared test schema. Sessions include the cancelled one; "expected" and "checked in"
 * count only people with a valid (ACTIVE) ticket on a session that is not cancelled; Manager only; a read writes
 * no audit row.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "workshopoperations-mysql-test"})
@TestPropertySource(properties = {"workshop.operations.auth.allow-test-tokens=true"})
class WorkshopDaySummaryIT {

    private static final String URL = "/api/v1/workshop-day-summaries";
    private static final String MANAGER = "Bearer test-uid:mgr";
    private static final String STAFF = "Bearer test-uid:staff";
    private static final LocalDate DAY = LocalDate.of(2091, 3, 14);
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mockMvc;
    @Autowired private WorkshopLocationRepository locations;
    @Autowired private WorkshopSlotRepository slots;
    @Autowired private WorkshopSessionRepository sessions;
    @Autowired private AttendanceTicketRepository tickets;
    @Autowired private TicketSessionEntitlementRepository entitlements;
    @Autowired private OperationsAuditEventRepository audits;
    @MockBean private AccountFacade accountFacade;
    @MockBean private TokenVerificationService tokenVerificationService;

    private WorkshopLocation locationA;
    private WorkshopLocation locationB;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        locationA = locations.save(WorkshopLocation.builder().code("DS-A-" + suffix).displayName("Day summary A")
                .addressSnapshot("A").active(true).build());
        locationB = locations.save(WorkshopLocation.builder().code("DS-B-" + suffix).displayName("Day summary B")
                .addressSnapshot("B").active(true).build());
        List<Long> scope = List.of(locationA.getId(), locationB.getId());
        when(tokenVerificationService.verify("test-uid:mgr")).thenReturn(new VerifiedIdentity("mgr", "m@mland.vn", true, Instant.now()));
        when(tokenVerificationService.verify("test-uid:staff")).thenReturn(new VerifiedIdentity("staff", "s@mland.vn", true, Instant.now()));
        when(accountFacade.resolveAccount("mgr")).thenReturn(Optional.of(
                new AccountSummary(10L, ActorRole.MANAGER, ActorStatus.ACTIVE, "m@mland.vn", Instant.now(), Instant.now())));
        when(accountFacade.getAccountStatus(10L)).thenReturn(Optional.of(ActorStatus.ACTIVE));
        when(accountFacade.getLocationScope(10L)).thenReturn(scope);
        when(accountFacade.resolveActor("mgr", true)).thenReturn(Optional.of(
                new CurrentAccountActor(10L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, scope)));
        when(accountFacade.resolveAccount("staff")).thenReturn(Optional.of(
                new AccountSummary(101L, ActorRole.STAFF, ActorStatus.ACTIVE, "s@mland.vn", Instant.now(), Instant.now())));
        when(accountFacade.getAccountStatus(101L)).thenReturn(Optional.of(ActorStatus.ACTIVE));
        when(accountFacade.getLocationScope(101L)).thenReturn(scope);
        when(accountFacade.resolveActor("staff", true)).thenReturn(Optional.of(
                new CurrentAccountActor(101L, ActorRole.STAFF, ActorStatus.ACTIVE, true, scope)));
    }

    private WorkshopSession session(WorkshopLocation location, LocalDate day, SessionState state) {
        WorkshopSlot slot = slots.save(WorkshopSlot.builder().reference(UUID.randomUUID().toString()).location(location)
                .label("S").startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(12, 0)).active(true).build());
        return sessions.save(WorkshopSession.builder().reference(UUID.randomUUID().toString()).location(location)
                .slot(slot).workshopDate(day)
                .scheduledStartAt(day.atTime(9, 0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant())
                .scheduledEndAt(day.atTime(12, 0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant())
                .checkInOpensAt(Instant.now()).checkInOpeningSource(CheckInOpeningSource.DEFAULT).capacity(10).state(state).build());
    }

    private void ticket(WorkshopSession session, TicketValidityState validity, AttendanceState attendance) {
        String id = UUID.randomUUID().toString();
        AttendanceTicket ticket = tickets.save(AttendanceTicket.builder().reference(id).bookingReference("DS-BK-" + id)
                .ticketSequence(1).ticketCode("TK-" + id.substring(0, 12)).qrReference("QR-" + id).validityState(validity).build());
        entitlements.save(TicketSessionEntitlement.builder().ticket(ticket).session(session).attendanceState(attendance).build());
    }

    private JsonNode read(String query, String token, int expected) throws Exception {
        var request = get(URL + query);
        if (token != null) {
            request = request.header("Authorization", token);
        }
        return JSON.readTree(mockMvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("sessions include the cancelled one; expected and checked in count valid tickets on not-cancelled sessions only")
    void countsSessionsCancelledAndTheRoster() throws Exception {
        WorkshopSession a1 = session(locationA, DAY, SessionState.PUBLISHED);
        WorkshopSession a2 = session(locationA, DAY, SessionState.CANCELLED);
        WorkshopSession b1 = session(locationB, DAY, SessionState.IN_PROGRESS);
        session(locationA, DAY.plusDays(1), SessionState.PUBLISHED); // another day: never counted
        ticket(a1, TicketValidityState.ACTIVE, AttendanceState.CHECKED_IN);
        ticket(a1, TicketValidityState.ACTIVE, AttendanceState.NOT_CHECKED_IN);
        ticket(a1, TicketValidityState.REFUNDED, AttendanceState.NOT_CHECKED_IN); // not valid: not expected
        ticket(a2, TicketValidityState.ACTIVE, AttendanceState.NOT_CHECKED_IN);  // cancelled session: not expected
        ticket(b1, TicketValidityState.ACTIVE, AttendanceState.CHECKED_IN);
        long auditsBefore = audits.count();

        JsonNode all = read("?date=" + DAY, MANAGER, 200);
        assertThat(all.get("date").asText()).isEqualTo(DAY.toString());
        assertThat(all.get("sessions").asLong()).isGreaterThanOrEqualTo(3);

        JsonNode a = read("?date=" + DAY + "&locationId=" + locationA.getId(), MANAGER, 200);
        assertThat(a.get("sessions").asLong()).isEqualTo(2);
        assertThat(a.get("cancelledSessions").asLong()).isEqualTo(1);
        assertThat(a.get("expected").asLong()).isEqualTo(2);
        assertThat(a.get("checkedIn").asLong()).isEqualTo(1);

        JsonNode b = read("?date=" + DAY + "&locationId=" + locationB.getId(), MANAGER, 200);
        assertThat(b.get("sessions").asLong()).isEqualTo(1);
        assertThat(b.get("cancelledSessions").asLong()).isZero();
        assertThat(b.get("expected").asLong()).isEqualTo(1);
        assertThat(b.get("checkedIn").asLong()).isEqualTo(1);

        assertThat(audits.count()).as("a read writes no audit row").isEqualTo(auditsBefore);
    }

    @Test
    @DisplayName("a day without sessions is all zeros, and the numbers agree with the session list for the same day")
    void emptyDayAndAgreementWithTheSessionList() throws Exception {
        JsonNode empty = read("?date=2091-03-20&locationId=" + locationA.getId(), MANAGER, 200);
        assertThat(empty.get("sessions").asLong()).isZero();
        assertThat(empty.get("expected").asLong()).isZero();

        session(locationA, DAY, SessionState.PUBLISHED);
        session(locationA, DAY, SessionState.CANCELLED);
        JsonNode list = JSON.readTree(mockMvc.perform(get("/api/v1/workshop-sessions?from=" + DAY + "&to=" + DAY + "&locationId=" + locationA.getId())
                .header("Authorization", MANAGER)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode summary = read("?date=" + DAY + "&locationId=" + locationA.getId(), MANAGER, 200);
        assertThat(summary.get("sessions").asLong()).isEqualTo(list.size());
        long cancelled = 0;
        for (JsonNode item : list) {
            if ("CANCELLED".equals(item.get("state").asText())) {
                cancelled++;
            }
        }
        assertThat(summary.get("cancelledSessions").asLong()).isEqualTo(cancelled);
    }

    @Test
    @DisplayName("only a Manager reads it: Staff gets 403 and no number, an unauthenticated caller 401")
    void managerOnly() throws Exception {
        assertThat(read("?date=" + DAY, STAFF, 403).has("sessions")).isFalse();
        read("?date=" + DAY, null, 401);
    }
}
