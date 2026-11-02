package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

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
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.AcceptOtaBookingCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.OtaBookingCapacityOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.RejectionReason;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
@ActiveProfiles({"test", "workshopoperations-mysql-test"})
class OtaBookingFacadeIT {

    private static final ZoneId WORKSHOP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private WorkshopOperationsFacade operationsFacade;
    @Autowired private WorkshopLocationRepository locationRepository;
    @Autowired private WorkshopPackageRepository packageRepository;
    @Autowired private WorkshopSessionRepository sessionRepository;
    @Autowired private WorkshopSlotRepository slotRepository;

    @BeforeEach
    void requireRealMySql() throws Exception {
        try (var connection = jdbcTemplate.getDataSource().getConnection()) {
            assumeTrue(connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql"),
                    "OtaBookingFacadeIT requires real MySQL evidence");
        }
    }

    @Test
    void acceptanceCommitsCapacityAndIssuesOneTicketPerParticipantAndReplaysOriginalSet() {
        WorkshopPackage basicRing = createPackage(1, true, 0, 1, 4);
        WorkshopSession session = createSession(4);
        String bookingReference = "OTA-" + UUID.randomUUID();
        String operationReference = UUID.randomUUID().toString();
        AcceptOtaBookingCommand command = command(bookingReference, basicRing, session, 3,
                operationReference);

        OtaBookingCapacityOutcome accepted = operationsFacade.acceptOtaBooking(command);

        assertThat(accepted.status().name()).isEqualTo("ACCEPTED");
        assertThat(accepted.ticketReferences()).hasSize(3).doesNotHaveDuplicates();
        assertThat(bookingTicketCount(bookingReference)).isEqualTo(3);
        assertThat(activeTicketCount(bookingReference)).isEqualTo(3);
        assertThat(entitlementCount(bookingReference)).isEqualTo(3);
        assertThat(confirmedHoldCount(bookingReference)).isEqualTo(1);

        OtaBookingCapacityOutcome replay = operationsFacade.acceptOtaBooking(command);

        assertThat(replay).isEqualTo(accepted);
        assertThat(bookingTicketCount(bookingReference)).isEqualTo(3);
        assertThat(confirmedHoldCount(bookingReference)).isEqualTo(1);
        assertThat(idempotencyRecordCount(operationReference)).isEqualTo(1);

        OtaBookingCapacityOutcome changedReuse = operationsFacade.acceptOtaBooking(
                command(bookingReference, basicRing, session, 2, operationReference));
        assertThat(changedReuse.status().name()).isEqualTo("REJECTED");
        assertThat(changedReuse.reason()).isEqualTo(RejectionReason.INVALID_STATE);
        assertThat(bookingTicketCount(bookingReference)).isEqualTo(3);
        assertThat(confirmedHoldCount(bookingReference)).isEqualTo(1);
    }

    @Test
    void insufficientCapacityCreatesNoHoldOrTicket() {
        WorkshopPackage basicRing = createPackage(1, true, 0, 1, 4);
        WorkshopSession session = createSession(1);
        String bookingReference = "OTA-FULL-" + UUID.randomUUID();

        OtaBookingCapacityOutcome outcome = operationsFacade.acceptOtaBooking(
                command(bookingReference, basicRing, session, 2, UUID.randomUUID().toString()));

        assertThat(outcome.status().name()).isEqualTo("REJECTED");
        assertThat(outcome.reason()).isEqualTo(RejectionReason.INSUFFICIENT_CAPACITY);
        assertThat(holdCount(bookingReference)).isZero();
        assertThat(bookingTicketCount(bookingReference)).isZero();
    }

    @Test
    void nonBasicRingPackageConfigurationAndInvalidParticipantCountAreRejectedWithoutEffects() {
        WorkshopPackage silverclay = createPackage(2, true, 1, 2, 4);
        WorkshopSession session = createSession(4);
        String bookingReference = "OTA-INVALID-" + UUID.randomUUID();

        OtaBookingCapacityOutcome wrongPackage = operationsFacade.acceptOtaBooking(
                command(bookingReference, silverclay, session, 2, UUID.randomUUID().toString()));
        OtaBookingCapacityOutcome invalidParticipants = operationsFacade.acceptOtaBooking(
                command("OTA-INVALID-PARTICIPANTS-" + UUID.randomUUID(),
                        createPackage(1, true, 0, 2, 4), session, 1, UUID.randomUUID().toString()));

        assertThat(wrongPackage.status().name()).isEqualTo("REJECTED");
        assertThat(wrongPackage.reason()).isEqualTo(RejectionReason.PACKAGE_SELECTION_INVALID);
        assertThat(invalidParticipants.status().name()).isEqualTo("REJECTED");
        assertThat(invalidParticipants.reason()).isEqualTo(RejectionReason.PACKAGE_SELECTION_INVALID);
        assertThat(holdCount(bookingReference)).isZero();
        assertThat(bookingTicketCount(bookingReference)).isZero();
    }

    private AcceptOtaBookingCommand command(String bookingReference, WorkshopPackage workshopPackage,
                                             WorkshopSession session, int participants, String operationReference) {
        return new AcceptOtaBookingCommand(bookingReference, workshopPackage.getId().toString(),
                UUID.fromString(session.getReference()), participants, operationReference,
                "corr-" + UUID.randomUUID());
    }

    private WorkshopPackage createPackage(int requiredSessions, boolean sameLocation, int slotGap,
                                          int minParticipants, int maxParticipants) {
        return packageRepository.save(WorkshopPackage.builder()
                .name("Basic Ring " + UUID.randomUUID())
                .description("OTA facade integration fixture")
                .publicPrice(500_000L)
                .minParticipants(minParticipants)
                .maxParticipants(maxParticipants)
                .requiredSessionCount(requiredSessions)
                .sameLocationRequired(sameLocation)
                .minimumSameDaySlotGap(slotGap)
                .state(WorkshopPackageState.PUBLISHED)
                .build());
    }

    private WorkshopSession createSession(int capacity) {
        String suffix = UUID.randomUUID().toString();
        WorkshopLocation location = locationRepository.saveAndFlush(WorkshopLocation.builder()
                .code("OTA-" + suffix)
                .displayName("OTA test location")
                .addressSnapshot("test")
                .active(true)
                .build());
        LocalTime startTime = LocalTime.of(10, 0);
        WorkshopSlot slot = slotRepository.saveAndFlush(WorkshopSlot.builder()
                .reference(UUID.randomUUID().toString())
                .location(location)
                .label("OTA test slot")
                .startTime(startTime)
                .endTime(startTime.plusHours(1))
                .defaultCapacity(capacity)
                .active(true)
                .build());
        LocalDate workshopDate = LocalDate.now(WORKSHOP_ZONE).plusDays(14);
        Instant scheduledStart = workshopDate.atTime(startTime).atZone(WORKSHOP_ZONE).toInstant();
        return sessionRepository.saveAndFlush(WorkshopSession.builder()
                .reference(UUID.randomUUID().toString())
                .location(location)
                .slot(slot)
                .workshopDate(workshopDate)
                .scheduledStartAt(scheduledStart)
                .scheduledEndAt(scheduledStart.plusSeconds(3600))
                .checkInOpensAt(scheduledStart.minusSeconds(1800))
                .capacity(capacity)
                .state(SessionState.PUBLISHED)
                .build());
    }

    private int holdCount(String bookingReference) {
        return jdbcTemplate.queryForObject(
                "select count(*) from capacity_holds where booking_reference = ?", Integer.class, bookingReference);
    }

    private int confirmedHoldCount(String bookingReference) {
        return jdbcTemplate.queryForObject(
                "select count(*) from capacity_holds where booking_reference = ? and state = 'CONFIRMED'",
                Integer.class, bookingReference);
    }

    private int bookingTicketCount(String bookingReference) {
        return jdbcTemplate.queryForObject(
                "select count(*) from attendance_tickets where booking_reference = ?", Integer.class, bookingReference);
    }

    private int activeTicketCount(String bookingReference) {
        return jdbcTemplate.queryForObject(
                "select count(*) from attendance_tickets where booking_reference = ? and validity_state = 'ACTIVE'",
                Integer.class, bookingReference);
    }

    private int entitlementCount(String bookingReference) {
        return jdbcTemplate.queryForObject(
                "select count(*) from ticket_session_entitlements tse "
                        + "join attendance_tickets t on t.id = tse.ticket_id where t.booking_reference = ?",
                Integer.class, bookingReference);
    }

    private int idempotencyRecordCount(String operationReference) {
        return jdbcTemplate.queryForObject(
                "select count(*) from facade_command_idempotency_records "
                        + "where facade_action = 'FACADE_ACCEPT_OTA_BOOKING' and operation_reference = ?",
                Integer.class, operationReference);
    }
}
