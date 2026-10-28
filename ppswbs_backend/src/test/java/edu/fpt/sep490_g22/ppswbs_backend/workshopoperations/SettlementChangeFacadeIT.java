package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.AttendanceTicketRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketSessionEntitlementRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopLocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSlotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceTicket;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketSessionEntitlement;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketValidityState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSlot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.ApplyVerifiedRefundConsequenceCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.BookingSettlementReadinessQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.BookingTicketProjectionQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.ReplaceRequiredSessionsCommand;
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
@ActiveProfiles("test")
class SettlementChangeFacadeIT {
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private WorkshopOperationsFacade operationsFacade;
    @Autowired private WorkshopLocationRepository locationRepository;
    @Autowired private WorkshopSlotRepository slotRepository;
    @Autowired private WorkshopSessionRepository sessionRepository;
    @Autowired private AttendanceTicketRepository ticketRepository;
    @Autowired private TicketSessionEntitlementRepository entitlementRepository;

    private WorkshopLocation location;

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
    }

    @Test
    void readinessIsFalseUntilEveryEntitledSessionIsFinalizedAndCancellationIsNotReady() {
        String booking = bookingReference();
        WorkshopSession first = createSession(SessionState.PUBLISHED, 10);
        WorkshopSession second = createSession(SessionState.PUBLISHED, 10);
        createTicket(booking, 1, List.of(first, second));

        assertThat(operationsFacade.getSettlementReadiness(new BookingSettlementReadinessQuery(booking)).ready()).isFalse();

        WorkshopSession managedFirst = sessionRepository.findById(first.getId()).orElseThrow();
        WorkshopSession managedSecond = sessionRepository.findById(second.getId()).orElseThrow();
        managedFirst.setState(SessionState.ATTENDANCE_FINALIZED);
        managedSecond.setState(SessionState.CANCELLED);
        sessionRepository.saveAllAndFlush(List.of(managedFirst, managedSecond));
        assertThat(operationsFacade.getSettlementReadiness(new BookingSettlementReadinessQuery(booking)).ready()).isFalse();

        WorkshopSession refreshedSecond = sessionRepository.findById(second.getId()).orElseThrow();
        refreshedSecond.setState(SessionState.ATTENDANCE_FINALIZED);
        sessionRepository.saveAndFlush(refreshedSecond);
        assertThat(operationsFacade.getSettlementReadiness(new BookingSettlementReadinessQuery(booking)).ready()).isTrue();
        assertThat(operationsFacade.getSettlementReadiness(new BookingSettlementReadinessQuery("missing-" + UUID.randomUUID())).ready())
                .isFalse();
    }

    @Test
    void oneSessionReplacementTransfersEveryParticipantAndPreservesTicketQrAndCurrentProjection() {
        String booking = bookingReference();
        WorkshopSession source = createSession(SessionState.PUBLISHED, 10);
        WorkshopSession target = createSession(SessionState.PUBLISHED, 10);
        AttendanceTicket first = createTicket(booking, 1, List.of(source));
        AttendanceTicket second = createTicket(booking, 2, List.of(source));
        String firstQr = first.getQrReference();
        String secondQr = second.getQrReference();

        var outcome = operationsFacade.replaceRequiredSessions(replaceCommand(booking, List.of(target)));

        assertThat(outcome.status().name()).isEqualTo("ACCEPTED");
        assertThat(entitlementRepository.findByTicketId(first.getId())).extracting(e -> e.getSession().getId())
                .containsExactly(target.getId());
        assertThat(entitlementRepository.findByTicketId(second.getId())).extracting(e -> e.getSession().getId())
                .containsExactly(target.getId());
        assertThat(ticketRepository.findById(first.getId()).orElseThrow().getQrReference()).isEqualTo(firstQr);
        assertThat(ticketRepository.findById(second.getId()).orElseThrow().getQrReference()).isEqualTo(secondQr);

        var projection = operationsFacade.getBookingTicketProjection(new BookingTicketProjectionQuery(booking));
        assertThat(projection.tickets()).hasSize(2);
        assertThat(projection.tickets()).allSatisfy(ticket -> assertThat(ticket.sessionEntitlements())
                .singleElement().satisfies(entitlement -> {
                    assertThat(entitlement.locationReference()).isEqualTo(location.getId());
                    assertThat(entitlement.scheduledStart()).isEqualTo(target.getScheduledStartAt());
                }));
    }

    @Test
    void twoSessionReplacementTransfersTheCompleteSetAtomically() {
        String booking = bookingReference();
        WorkshopSession sourceOne = createSession(SessionState.PUBLISHED, 10);
        WorkshopSession sourceTwo = createSession(SessionState.PUBLISHED, 10);
        WorkshopSession targetOne = createSession(SessionState.PUBLISHED, 10);
        WorkshopSession targetTwo = createSession(SessionState.PUBLISHED, 10);
        AttendanceTicket first = createTicket(booking, 1, List.of(sourceOne, sourceTwo));
        AttendanceTicket second = createTicket(booking, 2, List.of(sourceOne, sourceTwo));
        String originalQr = first.getQrReference();

        var outcome = operationsFacade.replaceRequiredSessions(replaceCommand(booking, List.of(targetOne, targetTwo)));

        assertThat(outcome.status().name()).isEqualTo("ACCEPTED");
        assertThat(entitlementRepository.findByTicketId(first.getId())).extracting(e -> e.getSession().getId())
                .containsExactlyInAnyOrder(targetOne.getId(), targetTwo.getId());
        assertThat(entitlementRepository.findByTicketId(second.getId())).extracting(e -> e.getSession().getId())
                .containsExactlyInAnyOrder(targetOne.getId(), targetTwo.getId());
        assertThat(ticketRepository.findById(first.getId()).orElseThrow().getQrReference()).isEqualTo(originalQr);
        assertThat(entitlementRepository.findByTicketId(first.getId()))
                .allSatisfy(e -> assertThat(e.getAttendanceState()).isEqualTo(AttendanceState.NOT_CHECKED_IN));
    }

    @Test
    void pausedTargetIsRejectedWithoutChangingAnySourceOrTargetEntitlement() {
        String booking = bookingReference();
        WorkshopSession source = createSession(SessionState.PUBLISHED, 10);
        WorkshopSession pausedTarget = createSession(SessionState.SALES_PAUSED, 10);
        AttendanceTicket ticket = createTicket(booking, 1, List.of(source));

        var outcome = operationsFacade.replaceRequiredSessions(replaceCommand(booking, List.of(pausedTarget)));

        assertThat(outcome.status().name()).isEqualTo("REJECTED");
        assertThat(entitlementRepository.findByTicketId(ticket.getId())).extracting(e -> e.getSession().getId())
                .containsExactly(source.getId());
        assertThat(entitlementRepository.findBySessionId(pausedTarget.getId())).isEmpty();
    }

    @Test
    void fullyConsumedPublishedTargetIsNotCustomerSelectable() {
        String booking = bookingReference();
        WorkshopSession source = createSession(SessionState.PUBLISHED, 10);
        WorkshopSession fullTarget = createSession(SessionState.PUBLISHED, 1);
        AttendanceTicket bookingTicket = createTicket(booking, 1, List.of(source));
        AttendanceTicket otherBookingTicket = createTicket(bookingReference(), 1, List.of(fullTarget));

        var outcome = operationsFacade.replaceRequiredSessions(replaceCommand(booking, List.of(fullTarget)));

        assertThat(outcome.status().name()).isEqualTo("REJECTED");
        assertThat(outcome.reason().name()).isEqualTo("INSUFFICIENT_CAPACITY");
        assertThat(entitlementRepository.findByTicketId(bookingTicket.getId())).extracting(e -> e.getSession().getId())
                .containsExactly(source.getId());
        assertThat(entitlementRepository.findByTicketId(otherBookingTicket.getId())).extracting(e -> e.getSession().getId())
                .containsExactly(fullTarget.getId());
    }

    @Test
    void insufficientCapacityInAnyTargetLeavesTheWholeTwoSessionSetUnchanged() {
        String booking = bookingReference();
        WorkshopSession sourceOne = createSession(SessionState.PUBLISHED, 10);
        WorkshopSession sourceTwo = createSession(SessionState.PUBLISHED, 10);
        WorkshopSession targetOne = createSession(SessionState.PUBLISHED, 10);
        WorkshopSession targetTwo = createSession(SessionState.PUBLISHED, 1);
        AttendanceTicket first = createTicket(booking, 1, List.of(sourceOne, sourceTwo));
        AttendanceTicket second = createTicket(booking, 2, List.of(sourceOne, sourceTwo));

        var outcome = operationsFacade.replaceRequiredSessions(replaceCommand(booking, List.of(targetOne, targetTwo)));

        assertThat(outcome.status().name()).isEqualTo("REJECTED");
        assertThat(outcome.reason().name()).isEqualTo("INSUFFICIENT_CAPACITY");
        assertThat(entitlementRepository.findByTicketId(first.getId())).extracting(e -> e.getSession().getId())
                .containsExactlyInAnyOrder(sourceOne.getId(), sourceTwo.getId());
        assertThat(entitlementRepository.findByTicketId(second.getId())).extracting(e -> e.getSession().getId())
                .containsExactlyInAnyOrder(sourceOne.getId(), sourceTwo.getId());
        assertThat(entitlementRepository.findBySessionId(targetOne.getId())).isEmpty();
        assertThat(entitlementRepository.findBySessionId(targetTwo.getId())).isEmpty();
    }

    @Test
    void startedOrAlreadyCheckedInSourceCannotBeReplacedAndAttendanceHistoryIsPreserved() {
        String booking = bookingReference();
        WorkshopSession source = createSession(SessionState.IN_PROGRESS, 10);
        WorkshopSession target = createSession(SessionState.PUBLISHED, 10);
        AttendanceTicket ticket = createTicket(booking, 1, List.of(source));
        TicketSessionEntitlement sourceEntitlement = entitlementRepository.findByTicketId(ticket.getId()).getFirst();
        sourceEntitlement.setAttendanceState(AttendanceState.CHECKED_IN);
        sourceEntitlement.setCheckedInAt(Instant.now());
        entitlementRepository.saveAndFlush(sourceEntitlement);

        var outcome = operationsFacade.replaceRequiredSessions(replaceCommand(booking, List.of(target)));

        assertThat(outcome.status().name()).isEqualTo("REJECTED");
        assertThat(entitlementRepository.findByTicketId(ticket.getId())).singleElement().satisfies(e -> {
            assertThat(e.getSession().getId()).isEqualTo(source.getId());
            assertThat(e.getAttendanceState()).isEqualTo(AttendanceState.CHECKED_IN);
            assertThat(e.getCheckedInAt()).isNotNull();
        });
        assertThat(entitlementRepository.findBySessionId(target.getId())).isEmpty();
    }

    @Test
    void pastSourceCannotBeReplacedEvenWhenItStillAppearsPublished() {
        String booking = bookingReference();
        WorkshopSession source = createSession(SessionState.PUBLISHED, 10);
        WorkshopSession managedSource = sessionRepository.findById(source.getId()).orElseThrow();
        managedSource.setScheduledStartAt(Instant.now().minusSeconds(60));
        sessionRepository.saveAndFlush(managedSource);
        WorkshopSession target = createSession(SessionState.PUBLISHED, 10);
        AttendanceTicket ticket = createTicket(booking, 1, List.of(source));

        var outcome = operationsFacade.replaceRequiredSessions(replaceCommand(booking, List.of(target)));

        assertThat(outcome.status().name()).isEqualTo("REJECTED");
        assertThat(entitlementRepository.findByTicketId(ticket.getId())).extracting(e -> e.getSession().getId())
                .containsExactly(source.getId());
        assertThat(entitlementRepository.findBySessionId(target.getId())).isEmpty();
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
        Instant start = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(3)
                .atTime(9, 0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        WorkshopSlot slot = slotRepository.saveAndFlush(WorkshopSlot.builder()
                .location(location)
                .label("Settlement Slot " + UUID.randomUUID())
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .defaultCapacity(capacity)
                .build());
        return sessionRepository.saveAndFlush(WorkshopSession.builder()
                .location(location)
                .slot(slot)
                .workshopDate(start.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate())
                .scheduledStartAt(start)
                .scheduledEndAt(start.plusSeconds(3600))
                .checkInOpensAt(start.minusSeconds(1800))
                .capacity(capacity)
                .state(state)
                .build());
    }

    private AttendanceTicket createTicket(String booking, int sequence, List<WorkshopSession> sessions) {
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
        return ticket;
    }

    private ReplaceRequiredSessionsCommand replaceCommand(String booking, List<WorkshopSession> targets) {
        return new ReplaceRequiredSessionsCommand(booking,
                targets.stream().map(s -> UUID.fromString(s.getReference())).toList(),
                UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }

    private String bookingReference() {
        return "SETTLEMENT-BOOKING-" + UUID.randomUUID();
    }
}
