package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ConfirmedBookingFinancialProjection;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.AttendanceTicketRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketSessionEntitlementRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopBookingPackagePolicySnapshotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopLocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSlotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceTicket;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketSessionEntitlement;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketValidityState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopBookingPackagePolicySnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSlot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.BookingTicketProjectionQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopOperationsFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCaseRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.RequiredSessionChangeCommandDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCase;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.RequiredSessionChangeService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.when;

/** MySQL acceptance coverage for Settlement -> Operations required-session replacement (T027). */
@SpringBootTest
@ActiveProfiles("workshopoperations-mysql-test")
@Transactional
class RequiredSessionChangeIT {
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RequiredSessionChangeService changeService;
    @Autowired private WorkshopOperationsFacade operationsFacade;
    @Autowired private WorkshopLocationRepository locationRepository;
    @Autowired private WorkshopSlotRepository slotRepository;
    @Autowired private WorkshopSessionRepository sessionRepository;
    @Autowired private AttendanceTicketRepository ticketRepository;
    @Autowired private TicketSessionEntitlementRepository entitlementRepository;
    @Autowired private WorkshopBookingPackagePolicySnapshotRepository policySnapshotRepository;
    @Autowired private SettlementCaseRepository settlementCaseRepository;

    @MockitoBean private WorkshopBookingSettlementAdapter booking;

    private WorkshopLocation location;
    private LocalDate workshopDate;

    @BeforeEach
    void requireMySqlAndCreateLocation() throws Exception {
        try (var connection = jdbcTemplate.getDataSource().getConnection()) {
            assumeTrue(connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql"),
                    "RequiredSessionChangeIT requires real MySQL evidence");
        }
        location = createLocation("CHANGE-IT");
        workshopDate = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(4);
    }

    @Test
    void oneSessionReplacementKeepsQrAndProjectsTheCurrentSession() {
        String bookingReference = bookingReference();
        WorkshopSession source = createSession(location, workshopDate, LocalTime.of(9, 0), 10);
        WorkshopSession target = createSession(location, workshopDate, LocalTime.of(11, 0), 10);
        AttendanceTicket ticket = createTicket(bookingReference, List.of(source));
        persistPolicy(bookingReference, 1, 0, true);
        givenConfirmedProjection(bookingReference, List.of(source), 1);

        var result = openChange(bookingReference, List.of(target), "weather closure");

        assertThat(result.status()).isEqualTo("RESOLVED");
        assertThat(ticketRepository.findById(ticket.getId()).orElseThrow().getQrReference())
                .isEqualTo(ticket.getQrReference());
        assertThat(entitlementRepository.findByTicketId(ticket.getId())).singleElement().satisfies(entitlement -> {
            assertThat(entitlement.getSession().getReference()).isEqualTo(target.getReference());
            assertThat(entitlement.getAttendanceState()).isEqualTo(AttendanceState.NOT_CHECKED_IN);
        });
        var currentProjection = operationsFacade.getBookingTicketProjection(
                new BookingTicketProjectionQuery(bookingReference));
        assertThat(currentProjection.tickets()).singleElement().satisfies(projectedTicket -> {
            assertThat(projectedTicket.qrReference()).isEqualTo(ticket.getQrReference());
            assertThat(projectedTicket.sessionEntitlements()).singleElement().satisfies(entitlement -> {
                assertThat(entitlement.sessionReference()).isEqualTo(UUID.fromString(target.getReference()));
                assertThat(entitlement.scheduledStart()).isEqualTo(target.getScheduledStartAt());
            });
        });
    }

    @Test
    void twoSessionSilverclayReplacementUsesFrozenLocationOrderAndGapPolicy() {
        String bookingReference = bookingReference();
        WorkshopSession sourceOne = createSession(location, workshopDate, LocalTime.of(12, 0), 10);
        WorkshopSession sourceTwo = createSession(location, workshopDate, LocalTime.of(13, 0), 10);
        WorkshopSession targetOne = createSession(location, workshopDate, LocalTime.of(9, 0), 10);
        createSlot(location, LocalTime.of(9, 30)); // booking-time Silverclay rule requires one intervening slot
        WorkshopSession targetTwo = createSession(location, workshopDate, LocalTime.of(10, 0), 10);
        AttendanceTicket ticket = createTicket(bookingReference, List.of(sourceOne, sourceTwo));
        persistPolicy(bookingReference, 2, 1, true);
        givenConfirmedProjection(bookingReference, List.of(sourceOne, sourceTwo), 1);

        var result = openChange(bookingReference, List.of(targetOne, targetTwo), "Silverclay schedule change");

        assertThat(result.status()).isEqualTo("RESOLVED");
        assertThat(entitlementRepository.findByTicketId(ticket.getId()))
                .extracting(entitlement -> entitlement.getSession().getReference())
                .containsExactlyInAnyOrder(targetOne.getReference(), targetTwo.getReference());
        assertThat(ticketRepository.findById(ticket.getId()).orElseThrow().getQrReference())
                .isEqualTo(ticket.getQrReference());
    }

    @Test
    void noCapacityRejectsAndLeavesEveryExistingEntitlementAndCaseUntouched() {
        String bookingReference = bookingReference();
        WorkshopSession source = createSession(location, workshopDate, LocalTime.of(9, 0), 10);
        WorkshopSession target = createSession(location, workshopDate, LocalTime.of(11, 0), 1);
        AttendanceTicket ticket = createTicket(bookingReference, List.of(source));
        createTicket(bookingReference() + "-COMPETING", List.of(target));
        persistPolicy(bookingReference, 1, 0, true);
        givenConfirmedProjection(bookingReference, List.of(source), 1);

        assertRejected(bookingReference, List.of(target), "INSUFFICIENT_CAPACITY");

        assertThat(entitlementRepository.findByTicketId(ticket.getId())).singleElement()
                .extracting(entitlement -> entitlement.getSession().getReference()).isEqualTo(source.getReference());
        assertThat(settlementCaseRepository.findAll()).noneMatch(row -> row.getBookingReference().equals(bookingReference));
    }

    @Test
    void missingBookingTimePolicyFailsClosedWithoutChangingTicketSession() {
        String bookingReference = bookingReference();
        WorkshopSession source = createSession(location, workshopDate, LocalTime.of(9, 0), 10);
        WorkshopSession target = createSession(location, workshopDate, LocalTime.of(11, 0), 10);
        AttendanceTicket ticket = createTicket(bookingReference, List.of(source));
        givenConfirmedProjection(bookingReference, List.of(source), 1);

        assertRejected(bookingReference, List.of(target), "INVALID_REPLACEMENT");

        assertThat(policySnapshotRepository.findByBookingReference(bookingReference)).isEmpty();
        assertThat(entitlementRepository.findByTicketId(ticket.getId())).singleElement()
                .extracting(entitlement -> entitlement.getSession().getReference()).isEqualTo(source.getReference());
        assertThat(settlementCaseRepository.findAll()).noneMatch(row -> row.getBookingReference().equals(bookingReference));
    }

    @Test
    void SilverclayRejectsDifferentLocationAndInsufficientSameDayGap() {
        String differentLocationBooking = bookingReference();
        WorkshopSession source = createSession(location, workshopDate, LocalTime.of(12, 0), 10);
        WorkshopSession targetA = createSession(location, workshopDate, LocalTime.of(9, 0), 10);
        WorkshopLocation otherLocation = createLocation("CHANGE-OTHER");
        WorkshopSession targetB = createSession(otherLocation, workshopDate, LocalTime.of(10, 0), 10);
        WorkshopSession sourceB = createSession(location, workshopDate, LocalTime.of(13, 0), 10);
        createTicket(differentLocationBooking, List.of(source, sourceB));
        persistPolicy(differentLocationBooking, 2, 0, true);
        givenConfirmedProjection(differentLocationBooking, List.of(source, sourceB), 1);

        assertRejected(differentLocationBooking, List.of(targetA, targetB), "INVALID_REPLACEMENT");

        String gapBooking = bookingReference();
        WorkshopSession gapSourceA = createSession(location, workshopDate, LocalTime.of(14, 0), 10);
        WorkshopSession gapSourceB = createSession(location, workshopDate, LocalTime.of(15, 0), 10);
        WorkshopSession adjacentA = createSession(location, workshopDate, LocalTime.of(9, 0), 10);
        WorkshopSession adjacentB = createSession(location, workshopDate, LocalTime.of(9, 30), 10);
        createTicket(gapBooking, List.of(gapSourceA, gapSourceB));
        persistPolicy(gapBooking, 2, 1, true);
        givenConfirmedProjection(gapBooking, List.of(gapSourceA, gapSourceB), 1);

        assertRejected(gapBooking, List.of(adjacentA, adjacentB), "INVALID_REPLACEMENT");

        String chronologicalBooking = bookingReference();
        WorkshopSession chronologicalSourceA = createSession(location, workshopDate, LocalTime.of(16, 0), 10);
        WorkshopSession chronologicalSourceB = createSession(location, workshopDate, LocalTime.of(17, 0), 10);
        WorkshopSession chronologicalTargetA = createSession(location, workshopDate, LocalTime.of(9, 0), 10);
        WorkshopSession chronologicalTargetB = createSession(location, workshopDate, LocalTime.of(11, 0), 10);
        createTicket(chronologicalBooking, List.of(chronologicalSourceA, chronologicalSourceB));
        persistPolicy(chronologicalBooking, 2, 1, true);
        givenConfirmedProjection(chronologicalBooking, List.of(chronologicalSourceA, chronologicalSourceB), 1);

        assertRejected(chronologicalBooking, List.of(chronologicalTargetB, chronologicalTargetA), "INVALID_REPLACEMENT");

        String duplicateBooking = bookingReference();
        WorkshopSession duplicateSourceA = createSession(location, workshopDate, LocalTime.of(18, 0), 10);
        WorkshopSession duplicateSourceB = createSession(location, workshopDate, LocalTime.of(19, 0), 10);
        WorkshopSession duplicateTarget = createSession(location, workshopDate, LocalTime.of(9, 0), 10);
        createTicket(duplicateBooking, List.of(duplicateSourceA, duplicateSourceB));
        persistPolicy(duplicateBooking, 2, 0, true);
        givenConfirmedProjection(duplicateBooking, List.of(duplicateSourceA, duplicateSourceB), 1);

        assertThatThrownBy(() -> openChange(duplicateBooking, List.of(duplicateTarget, duplicateTarget), "duplicate target"))
                .isInstanceOf(edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementValidationException.class)
                .hasMessageContaining("distinct");
    }

    private edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.SettlementCaseProjectionDto
    openChange(String bookingReference, List<WorkshopSession> targets, String reason) {
        return changeService.open(bookingReference, "key-" + UUID.randomUUID(), "corr-" + UUID.randomUUID(),
                new RequiredSessionChangeCommandDto(targets.stream().map(WorkshopSession::getReference).toList(), reason, null), manager());
    }

    private void assertRejected(String bookingReference, List<WorkshopSession> targets, String reason) {
        assertThatThrownBy(() -> openChange(bookingReference, targets, "schedule change"))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining(reason);
    }

    private void givenConfirmedProjection(String bookingReference, List<WorkshopSession> sessions, int participantCount) {
        when(booking.confirmedProjection(bookingReference)).thenReturn(new ConfirmedBookingFinancialProjection(
                "silverclay", 100_000L, 100_000L, 50_000L, 50_000L, participantCount,
                sessions.stream().map(WorkshopSession::getReference).toList(), location.getId()));
    }

    private void persistPolicy(String bookingReference, int requiredCount, int minimumGap, boolean sameLocation) {
        policySnapshotRepository.saveAndFlush(WorkshopBookingPackagePolicySnapshot.builder()
                .bookingReference(bookingReference).packageReference("silverclay")
                .requiredSessionCount(requiredCount).minParticipants(1).maxParticipants(20)
                .sameLocationRequired(sameLocation).minimumSameDaySlotGap(minimumGap).build());
    }

    private AttendanceTicket createTicket(String bookingReference, List<WorkshopSession> sessions) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        AttendanceTicket ticket = ticketRepository.saveAndFlush(AttendanceTicket.builder()
                .bookingReference(bookingReference).ticketSequence(1).ticketCode("CHANGE-" + suffix)
                .qrReference("QR-" + UUID.randomUUID()).validityState(TicketValidityState.ACTIVE).build());
        sessions.forEach(session -> entitlementRepository.save(TicketSessionEntitlement.builder()
                .ticket(ticket).session(session).attendanceState(AttendanceState.NOT_CHECKED_IN).build()));
        entitlementRepository.flush();
        return ticket;
    }

    private WorkshopSession createSession(WorkshopLocation sessionLocation, LocalDate date,
                                          LocalTime startTime, int capacity) {
        Instant start = date.atTime(startTime).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        WorkshopSlot slot = slotRepository.saveAndFlush(WorkshopSlot.builder()
                .location(sessionLocation).label("Required Change Slot " + UUID.randomUUID())
                .startTime(startTime).endTime(startTime.plusHours(1)).defaultCapacity(capacity).build());
        return sessionRepository.saveAndFlush(WorkshopSession.builder().location(sessionLocation).slot(slot)
                .workshopDate(date).scheduledStartAt(start).scheduledEndAt(start.plusSeconds(3600))
                .checkInOpensAt(start.minusSeconds(1800)).capacity(capacity).state(SessionState.PUBLISHED).build());
    }

    private WorkshopSlot createSlot(WorkshopLocation slotLocation, LocalTime startTime) {
        return slotRepository.saveAndFlush(WorkshopSlot.builder().location(slotLocation)
                .label("Required Change Gap Slot " + UUID.randomUUID())
                .startTime(startTime).endTime(startTime.plusHours(1)).defaultCapacity(10).build());
    }

    private WorkshopLocation createLocation(String prefix) {
        return locationRepository.saveAndFlush(WorkshopLocation.builder()
                .code(prefix + "-" + UUID.randomUUID()).displayName("Required Change Test")
                .addressSnapshot("test").build());
    }

    private static CurrentAccountActor manager() {
        return new CurrentAccountActor(10L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of());
    }

    private static String bookingReference() {
        return "REQUIRED-CHANGE-" + UUID.randomUUID();
    }
}
