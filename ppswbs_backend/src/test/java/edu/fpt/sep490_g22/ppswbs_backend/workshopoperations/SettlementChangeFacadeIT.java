package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.AttendanceTicketRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketSessionEntitlementRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopLocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSlotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopBookingPackagePolicySnapshotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceTicket;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketSessionEntitlement;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketValidityState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopBookingPackagePolicySnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSlot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.ApplyVerifiedRefundConsequenceCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.BookingSettlementReadinessQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.BookingTicketProjectionQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopOperationsFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
@ActiveProfiles("workshopoperations-mysql-test")
class SettlementChangeFacadeIT {
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private WorkshopOperationsFacade operationsFacade;
    @Autowired private WorkshopLocationRepository locationRepository;
    @Autowired private WorkshopSlotRepository slotRepository;
    @Autowired private WorkshopSessionRepository sessionRepository;
    @Autowired private AttendanceTicketRepository ticketRepository;
    @Autowired private TicketSessionEntitlementRepository entitlementRepository;
    @Autowired private WorkshopBookingPackagePolicySnapshotRepository policySnapshotRepository;

    private WorkshopLocation location;
    private int sessionSequence;

    @BeforeEach
    void requireMySqlAndCreateLocation() throws Exception {
        try (var connection = jdbcTemplate.getDataSource().getConnection()) {
            assumeTrue(connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql"),
                    "SettlementChangeFacadeIT requires real MySQL evidence");
        }
        location = locationRepository.saveAndFlush(WorkshopLocation.builder()
                .code("SETTLEMENT-" + UUID.randomUUID())
                .displayName("Settlement Test Location")
                .addressSnapshot("test")
                .build());
        sessionSequence = 0;
    }

    @Test
    void verifiedRefundRevokesTicketProjectionReleasesCapacityAndRetainsAttendanceEvidence() {
        String booking = bookingReference();
        WorkshopSession session = createSession(SessionState.PUBLISHED, 10);
        AttendanceTicket ticket = createTicket(booking, 1, List.of(session));
        TicketSessionEntitlement evidence = entitlementRepository.findByTicketId(ticket.getId()).getFirst();
        evidence.setAttendanceState(AttendanceState.CHECKED_IN);
        evidence.setCheckedInAt(Instant.now());
        entitlementRepository.saveAndFlush(evidence);

        var outcome = operationsFacade.applyVerifiedRefundConsequence(new ApplyVerifiedRefundConsequenceCommand(
                booking, "verified-refund-" + UUID.randomUUID(), UUID.randomUUID().toString(), UUID.randomUUID().toString()));

        assertThat(outcome.status().name()).isEqualTo("ACCEPTED");
        assertThat(ticketRepository.findById(ticket.getId()).orElseThrow().getValidityState()).isEqualTo(TicketValidityState.REFUNDED);
        assertThat(entitlementRepository.findByTicketId(ticket.getId())).singleElement().satisfies(e -> {
            assertThat(e.getAttendanceState()).isEqualTo(AttendanceState.CHECKED_IN);
            assertThat(e.getCheckedInAt()).isNotNull();
        });
        assertThat(operationsFacade.getBookingTicketProjection(new BookingTicketProjectionQuery(booking)).tickets()).isEmpty();
        assertThat(operationsFacade.getSaleEligibility(new edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SaleEligibilityQuery(
                UUID.fromString(session.getReference()))).remainingCapacity()).isEqualTo(10);
    }

    private WorkshopSession createSession(SessionState state, int capacity) {
        LocalTime startTime = LocalTime.of(9, 0).plusMinutes(sessionSequence++ * 30L);
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(3);
        return createSessionAt(state, capacity, date, startTime, location);
    }

    private WorkshopSession createSessionAt(SessionState state, int capacity, LocalDate date,
                                            LocalTime startTime, WorkshopLocation sessionLocation) {
        Instant start = date.atTime(startTime).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        WorkshopSlot slot = slotRepository.saveAndFlush(WorkshopSlot.builder()
                .location(sessionLocation)
                .label("Settlement Slot " + UUID.randomUUID())
                .startTime(startTime)
                .endTime(startTime.plusHours(1))
                .defaultCapacity(capacity)
                .build());
        return sessionRepository.saveAndFlush(WorkshopSession.builder()
                .location(sessionLocation)
                .slot(slot)
                .workshopDate(date)
                .scheduledStartAt(start)
                .scheduledEndAt(start.plusSeconds(3600))
                .checkInOpensAt(start.minusSeconds(1800))
                .capacity(capacity)
                .state(state)
                .build());
    }

    private WorkshopSlot createSlot(LocalTime startTime) {
        return slotRepository.saveAndFlush(WorkshopSlot.builder()
                .location(location)
                .label("Settlement Gap Slot " + UUID.randomUUID())
                .startTime(startTime)
                .endTime(startTime.plusHours(1))
                .defaultCapacity(10)
                .build());
    }

    private AttendanceTicket createTicket(String booking, int sequence, List<WorkshopSession> sessions) {
        return createTicket(booking, sequence, sessions, true, sessions.size(), 0);
    }

    private AttendanceTicket createTicket(String booking, int sequence, List<WorkshopSession> sessions,
                                          boolean persistPolicy, int requiredSessions, int minimumGap) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        AttendanceTicket ticket = ticketRepository.saveAndFlush(AttendanceTicket.builder()
                .bookingReference(booking)
                .ticketSequence(sequence)
                .ticketCode("ST-" + suffix)
                .qrReference("QR-" + UUID.randomUUID())
                .validityState(TicketValidityState.ACTIVE)
                .build());
        sessions.forEach(session -> entitlementRepository.save(TicketSessionEntitlement.builder()
                .ticket(ticket)
                .session(session)
                .attendanceState(AttendanceState.NOT_CHECKED_IN)
                .build()));
        entitlementRepository.flush();
        if (persistPolicy && policySnapshotRepository.findByBookingReference(booking).isEmpty()) {
            policySnapshotRepository.saveAndFlush(WorkshopBookingPackagePolicySnapshot.builder()
                    .bookingReference(booking)
                    .packageReference("1")
                    .requiredSessionCount(requiredSessions)
                    .minParticipants(1)
                    .maxParticipants(20)
                    .sameLocationRequired(true)
                    .minimumSameDaySlotGap(minimumGap)
                    .build());
        }
        return ticket;
    }

    private String bookingReference() {
        return "SETTLEMENT-BOOKING-" + UUID.randomUUID();
    }
}
