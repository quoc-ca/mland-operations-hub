package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.CheckInRequest;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 003.2 T093 / FR-033: QR revoke and reissue for a recipient change.
 * - a reissue revokes the active QR immediately and issues a new opaque QR for the same entitlements;
 * - check-in refuses the revoked QR with 409 TICKET_QR_REVOKED and accepts the new one;
 * - a replay of the same operation does not rotate twice;
 * - a reissue is refused once any entitlement of the ticket is checked in;
 * - audit never carries a QR value.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("workshopoperations-mysql-test")
@TestPropertySource(properties = {"workshop.operations.auth.allow-test-tokens=true"})
class TicketQrReissueIT {

    private static final String STAFF_TOKEN = "Bearer test-uid:qr-staff";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private WorkshopLocationRepository locationRepository;
    @Autowired private WorkshopSlotRepository slotRepository;
    @Autowired private WorkshopPackageRepository packageRepository;
    @Autowired private WorkshopSessionRepository sessionRepository;
    @Autowired private SessionStaffAssignmentRepository staffAssignmentRepository;
    @Autowired private AttendanceTicketRepository ticketRepository;
    @Autowired private TicketSessionEntitlementRepository entitlementRepository;
    @Autowired private OperationsAuditEventRepository auditEventRepository;
    @Autowired private WorkshopOperationsFacade operationsFacade;

    @MockBean private AccountFacade accountFacade;
    @MockBean private TokenVerificationService tokenVerificationService;

    private WorkshopLocation location;
    private WorkshopSlot slot;

    @BeforeEach
    void setUp() throws Exception {
        try (var connection = jdbcTemplate.getDataSource().getConnection()) {
            assumeTrue(connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql"),
                    "TicketQrReissueIT requires real MySQL evidence");
        }
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : List.of("facade_command_idempotency_records", "attendance_actions",
                "ticket_qr_generations", "ticket_session_entitlements", "attendance_tickets", "capacity_holds",
                "session_operational_leads", "session_staff_assignments", "cancellation_handoffs",
                "operations_audit_events", "workshop_sessions", "workshop_slots", "workshop_locations")) {
            jdbcTemplate.execute("TRUNCATE TABLE " + table);
        }
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");

        location = locationRepository.save(WorkshopLocation.builder().code("LOC-QR-01")
                .displayName("QR Hub").addressSnapshot("1 Test St").active(true).build());
        slot = slotRepository.save(WorkshopSlot.builder().reference(UUID.randomUUID().toString())
                .location(location).label("Morning").startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(12, 0))
                .defaultCapacity(10).active(true).build());

        when(accountFacade.resolveAccount("qr-staff")).thenReturn(Optional.of(
                new AccountSummary(201L, ActorRole.STAFF, ActorStatus.ACTIVE, "qrstaff@mland.vn", Instant.now(), Instant.now())));
        when(accountFacade.getAccountStatus(201L)).thenReturn(Optional.of(ActorStatus.ACTIVE));
        when(accountFacade.getLocationScope(201L)).thenReturn(List.of(location.getId()));
        when(tokenVerificationService.verify("test-uid:qr-staff")).thenReturn(
                new VerifiedIdentity("qr-staff", "qrstaff@mland.vn", true, Instant.now()));
        when(accountFacade.resolveActor("qr-staff", true)).thenReturn(Optional.of(
                new CurrentAccountActor(201L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(location.getId()))));
    }

    @Test
    @DisplayName("T093-1: reissue revokes the old QR at once; check-in refuses it and accepts the new QR")
    void reissue_revokesOldQr_andCheckInAcceptsOnlyNewQr() throws Exception {
        WorkshopSession session = publishedSession();
        AttendanceTicket ticket = issueTicket(session);
        String oldQr = ticket.getQrReference();

        TicketQrReissueOutcome outcome = reissue(ticket, "op-qr-" + UUID.randomUUID());

        assertThat(outcome.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        assertThat(outcome.qrGeneration()).isEqualTo(2);
        assertThat(outcome.qrReference()).isNotBlank().isNotEqualTo(oldQr);
        AttendanceTicket reloaded = ticketRepository.findByReference(ticket.getReference()).orElseThrow();
        assertThat(reloaded.getQrReference()).isEqualTo(outcome.qrReference());
        assertThat(reloaded.getValidityState()).isEqualTo(TicketValidityState.ACTIVE);
        assertThat(entitlementRepository.findByTicketId(ticket.getId())).hasSize(1);

        scan(session, oldQr)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TICKET_QR_REVOKED"));
        assertThat(entitlementRepository.findByTicketId(ticket.getId()).getFirst().getAttendanceState())
                .isEqualTo(AttendanceState.NOT_CHECKED_IN);

        scan(session, outcome.qrReference())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkInResult").value("CHECKED_IN"));
    }

    @Test
    @DisplayName("T093-2: replaying the same operation does not rotate the QR a second time")
    void replay_sameOperation_returnsSameGenerationWithoutSecondRotation() {
        AttendanceTicket ticket = issueTicket(publishedSession());
        String op = "op-qr-" + UUID.randomUUID();

        TicketQrReissueOutcome first = reissue(ticket, op);
        TicketQrReissueOutcome replay = reissue(ticket, op);

        assertThat(replay.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        assertThat(replay.qrGeneration()).isEqualTo(first.qrGeneration());
        assertThat(replay.qrReference()).isEqualTo(first.qrReference());
        assertThat(count("ticket_qr_generations")).isEqualTo(1);
        assertThat(ticketRepository.findByReference(ticket.getReference()).orElseThrow().getQrGeneration()).isEqualTo(2);
    }

    @Test
    @DisplayName("T093-3: a reissue after any check-in is refused and leaves the QR unchanged")
    void reissue_afterCheckIn_isRefusedWithoutSideEffect() throws Exception {
        WorkshopSession session = publishedSession();
        AttendanceTicket ticket = issueTicket(session);
        scan(session, ticket.getQrReference()).andExpect(status().isOk());

        TicketQrReissueOutcome outcome = reissue(ticket, "op-qr-" + UUID.randomUUID());

        assertThat(outcome.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(outcome.reason()).isEqualTo(RejectionReason.TICKET_ALREADY_CHECKED_IN);
        assertThat(outcome.qrReference()).isNull();
        AttendanceTicket reloaded = ticketRepository.findByReference(ticket.getReference()).orElseThrow();
        assertThat(reloaded.getQrReference()).isEqualTo(ticket.getQrReference());
        assertThat(reloaded.getQrGeneration()).isEqualTo(1);
        assertThat(count("ticket_qr_generations")).isZero();
    }

    @Test
    @DisplayName("T093-4: an unknown ticket is refused with TICKET_NOT_FOUND")
    void reissue_unknownTicket_isRefused() {
        TicketQrReissueOutcome outcome = operationsFacade.reissueTicketQr(new ReissueTicketQrCommand(
                UUID.randomUUID(), QrRevokeReason.RECIPIENT_CHANGED, "op-qr-" + UUID.randomUUID(), "corr-qr"));

        assertThat(outcome.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(outcome.reason()).isEqualTo(RejectionReason.TICKET_NOT_FOUND);
    }

    @Test
    @DisplayName("T093-5: audit records the rotation without any QR value")
    void reissue_auditCarriesNoQrValue() {
        AttendanceTicket ticket = issueTicket(publishedSession());
        String oldQr = ticket.getQrReference();

        TicketQrReissueOutcome outcome = reissue(ticket, "op-qr-" + UUID.randomUUID());

        List<OperationsAuditEvent> audits = auditEventRepository.findAll().stream()
                .filter(a -> "REISSUE_TICKET_QR".equals(a.getAction())).toList();
        assertThat(audits).hasSize(1);
        assertThat(audits.getFirst().getMetadata()).contains(ticket.getTicketCode())
                .doesNotContain(oldQr).doesNotContain(outcome.qrReference());
    }

    private TicketQrReissueOutcome reissue(AttendanceTicket ticket, String operationReference) {
        return operationsFacade.reissueTicketQr(new ReissueTicketQrCommand(UUID.fromString(ticket.getReference()),
                QrRevokeReason.RECIPIENT_CHANGED, operationReference, "corr-qr"));
    }

    private org.springframework.test.web.servlet.ResultActions scan(WorkshopSession session, String qr) throws Exception {
        return mockMvc.perform(post("/api/v1/workshop-sessions/{sessionId}/check-ins", session.getReference())
                .header("Authorization", STAFF_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(CheckInRequest.builder().qrToken(qr).build())));
    }

    private int count(String table) {
        Integer n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return n == null ? 0 : n;
    }

    private WorkshopSession publishedSession() {
        LocalDate date = LocalDate.now().plusDays(2);
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        WorkshopSession session = sessionRepository.save(WorkshopSession.builder()
                .reference(UUID.randomUUID().toString()).location(location).slot(slot).workshopDate(date)
                .scheduledStartAt(date.atTime(slot.getStartTime()).atZone(zone).toInstant())
                .scheduledEndAt(date.atTime(slot.getEndTime()).atZone(zone).toInstant())
                .checkInOpensAt(Instant.now().minus(30, ChronoUnit.MINUTES))
                .checkInOpeningSource(CheckInOpeningSource.DEFAULT)
                .capacity(10).state(SessionState.PUBLISHED).build());
        staffAssignmentRepository.save(SessionStaffAssignment.builder().session(session).accountId(201L)
                .assignedById(10L).assignedAt(Instant.now()).active(true).build());
        return session;
    }

    private AttendanceTicket issueTicket(WorkshopSession session) {
        String bookingRef = "BK-QR-" + UUID.randomUUID().toString().substring(0, 8);
        Long packageId = packageRepository.save(WorkshopPackage.builder().name("QR test package")
                .description("Synthetic fixture").publicPrice(1000L).minParticipants(1).maxParticipants(10)
                .requiredSessionCount(1).sameLocationRequired(true).minimumSameDaySlotGap(0)
                .state(WorkshopPackageState.PUBLISHED).build()).getId();
        CapacityHoldOutcome hold = operationsFacade.reserveBookingCapacity(new ReserveBookingCapacityCommand(
                bookingRef, String.valueOf(packageId), List.of(UUID.fromString(session.getReference())), 1,
                "op-hold-" + UUID.randomUUID(), "corr-hold"));
        assertThat(hold.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        CapacityCommitOutcome commit = operationsFacade.commitBookingHolds(new CommitBookingHoldsCommand(
                bookingRef, hold.holdReferences(), "op-comm-" + UUID.randomUUID(), "corr-comm"));
        assertThat(commit.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        return ticketRepository.findByBookingReference(bookingRef).getFirst();
    }
}
