package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionOtaSnapshotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSlotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopLocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopPackageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.CapacityHoldRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.CapacityHold;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.CapacityHoldState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.CommitBookingHoldsCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.ReleaseBookingHoldsCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.ReserveBookingCapacityCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopOperationsFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.CapacityHoldService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackage;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackageState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSlot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopOtaResyncFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopSessionOtaSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopOtaSnapshotService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopSessionStartScheduler;
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
import java.util.Locale;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
@ActiveProfiles("test")
class WorkshopOtaSnapshotIT {
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private WorkshopOtaSnapshotService snapshotService;
    @Autowired private WorkshopOtaResyncFacade resyncFacade;
    @Autowired private WorkshopSessionOtaSnapshotRepository snapshotRepository;
    @Autowired private WorkshopSessionRepository sessionRepository;
    @Autowired private WorkshopSlotRepository slotRepository;
    @Autowired private WorkshopLocationRepository locationRepository;
    @Autowired private WorkshopPackageRepository packageRepository;
    @Autowired private CapacityHoldRepository capacityHoldRepository;
    @Autowired private WorkshopOperationsFacade operationsFacade;
    @Autowired private CapacityHoldService capacityHoldService;
    @Autowired private WorkshopSessionStartScheduler sessionStartScheduler;

    private WorkshopSession session;
    private String packageReference;

    @BeforeEach
    void setUp() throws Exception {
        try (var connection = jdbcTemplate.getDataSource().getConnection()) {
            assumeTrue(connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("mysql"),
                    "WorkshopOtaSnapshotIT requires real MySQL evidence");
        }
        // This integration database contains FK-linked operational data from
        // other suites. Do not delete shared parents here; each run creates
        // isolated references and only queries its own session snapshot.
        WorkshopLocation location = locationRepository.save(WorkshopLocation.builder()
                .code("OTA-" + System.nanoTime())
                .displayName("OTA Test Location")
                .addressSnapshot("test")
                .build());
        WorkshopSlot slot = slotRepository.save(WorkshopSlot.builder()
                .location(location).label("OTA Slot").startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0)).defaultCapacity(10).build());
        Instant start = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(2)
                .atTime(9, 0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        session = sessionRepository.save(WorkshopSession.builder()
                .location(location).slot(slot).workshopDate(LocalDate.now().plusDays(2))
                .scheduledStartAt(start).scheduledEndAt(start.plusSeconds(3600))
                .checkInOpensAt(start.minusSeconds(30 * 60L)).capacity(10)
                .state(SessionState.PUBLISHED).build());
        WorkshopPackage publishedPackage = packageRepository.save(WorkshopPackage.builder()
                .name("OTA snapshot test package " + UUID.randomUUID())
                .description("Isolated package used by OTA snapshot integration tests")
                .publicPrice(1L)
                .minParticipants(1)
                .maxParticipants(10)
                .requiredSessionCount(1)
                .sameLocationRequired(true)
                .minimumSameDaySlotGap(0)
                .state(WorkshopPackageState.PUBLISHED)
                .build());
        packageReference = publishedPackage.getId().toString();
    }

    @Test
    void publishesMonotonicSnapshotAndResyncDoesNotAdvanceVersion() {
        var first = snapshotService.publishForObservableChange(
                session, "change-1", Instant.now());
        session.setCapacity(9);
        session = sessionRepository.saveAndFlush(session);
        var second = snapshotService.publishForObservableChange(
                session, "change-2", Instant.now().plusSeconds(1));

        assertThat(first.sourceVersion()).isEqualTo(1);
        assertThat(second.sourceVersion()).isEqualTo(2);
        assertThat(second.sellableAvailability()).isEqualTo(9);
        WorkshopSessionOtaSnapshot resynced = resyncFacade.resync(session.getReference());
        assertThat(resynced.sourceVersion()).isEqualTo(2);
        assertThat(resynced.sessionReference()).isEqualTo(session.getReference());
        assertThat(snapshotRepository.findTopBySessionReferenceOrderBySourceVersionDesc(session.getReference()))
                .get().extracting(value -> value.getSourceVersion()).isEqualTo(2L);
    }

    @Test
    void unchangedObservableSnapshotDoesNotAdvanceVersionOrPublishAgain() {
        var first = snapshotService.publishForObservableChange(session, "initial", Instant.now());
        var unchanged = snapshotService.publishForObservableChange(session, "internal-only", Instant.now().plusSeconds(5));

        assertThat(first.sourceVersion()).isEqualTo(1);
        assertThat(unchanged.sourceVersion()).isEqualTo(1);
        assertThat(unchanged.changeReference()).isEqualTo("initial");
        assertThat(publicationCount(session.getReference())).isEqualTo(1);
    }

    @Test
    void scheduleAndSaleStateChangesEachPublishOneCompleteNewVersion() {
        Instant now = Instant.now();
        var published = snapshotService.publishForObservableChange(session, "published", now);

        session.setScheduledStartAt(session.getScheduledStartAt().plusSeconds(1800));
        session.setScheduledEndAt(session.getScheduledEndAt().plusSeconds(1800));
        session = sessionRepository.saveAndFlush(session);
        var rescheduled = snapshotService.publishForObservableChange(session, "rescheduled", now.plusSeconds(1));

        session.setState(SessionState.SALES_PAUSED);
        session = sessionRepository.saveAndFlush(session);
        var paused = snapshotService.publishForObservableChange(session, "sales-paused", now.plusSeconds(2));

        session.setState(SessionState.PUBLISHED);
        session = sessionRepository.saveAndFlush(session);
        var resumed = snapshotService.publishForObservableChange(session, "sales-resumed", now.plusSeconds(3));

        assertThat(published.sourceVersion()).isEqualTo(1);
        assertThat(rescheduled.sourceVersion()).isEqualTo(2);
        assertThat(rescheduled.scheduledStart()).isEqualTo(session.getScheduledStartAt());
        assertThat(rescheduled.scheduledEnd()).isEqualTo(session.getScheduledEndAt());
        assertThat(paused.sourceVersion()).isEqualTo(3);
        assertThat(paused.saleState()).isEqualTo("SALES_PAUSED");
        assertThat(resumed.sourceVersion()).isEqualTo(4);
        assertThat(resumed.saleState()).isEqualTo("PUBLISHED");
        assertThat(publicationCount(session.getReference())).isEqualTo(4);
    }

    @Test
    void scheduledStartPublishesClosedSnapshotAtomicallyAndIsIdempotent() {
        session.setScheduledStartAt(Instant.now().minusSeconds(1));
        session.setScheduledEndAt(Instant.now().plusSeconds(3599));
        session = sessionRepository.saveAndFlush(session);

        sessionStartScheduler.transitionDueSessions();
        sessionStartScheduler.transitionDueSessions();

        WorkshopSession persisted = sessionRepository.findByReference(session.getReference()).orElseThrow();
        assertThat(persisted.getState()).isEqualTo(SessionState.IN_PROGRESS);
        assertThat(latestSnapshot().saleState()).isEqualTo("CLOSED");
        assertThat(latestSnapshot().sourceVersion()).isEqualTo(1);
        assertThat(publicationCount(session.getReference())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM operations_audit_events WHERE session_id = ? AND action = ?",
                Integer.class, session.getId(), "SESSION_AUTO_STARTED")).isEqualTo(1);
    }

    @Test
    void attendanceFinalizationDoesNotRepublishAlreadyClosedSaleState() {
        session.setState(SessionState.IN_PROGRESS);
        session = sessionRepository.saveAndFlush(session);
        var inProgress = snapshotService.publishForObservableChange(session, "started", Instant.now());

        session.setState(SessionState.ATTENDANCE_FINALIZED);
        session = sessionRepository.saveAndFlush(session);
        var finalized = snapshotService.publishForObservableChange(session, "finalized", Instant.now().plusSeconds(1));

        assertThat(inProgress.saleState()).isEqualTo("CLOSED");
        assertThat(finalized.saleState()).isEqualTo("CLOSED");
        assertThat(finalized.sourceVersion()).isEqualTo(inProgress.sourceVersion());
        assertThat(finalized.changeReference()).isEqualTo("started");
        assertThat(publicationCount(session.getReference())).isEqualTo(1);
    }

    @Test
    void snapshotContainsOnlyDeliverySafeFields() {
        WorkshopSessionOtaSnapshot snapshot = snapshotService.publishForObservableChange(
                session, "safe-change", Instant.now());
        assertThat(snapshot.saleState()).isEqualTo("PUBLISHED");
        assertThat(snapshot.sellableAvailability()).isEqualTo(10);
        assertThat(snapshot.changeReference()).isEqualTo("safe-change");
        assertThat(snapshot.toString()).doesNotContain("booking", "ticket", "PII", "qr");
    }

    @Test
    void draftSessionDoesNotCreateSnapshotOrPersistentPublication() {
        session.setState(SessionState.DRAFT);
        session = sessionRepository.saveAndFlush(session);

        assertThat(snapshotService.publishForObservableChange(session, "draft-only", Instant.now())).isNull();
        assertThat(snapshotRepository.findTopBySessionReferenceOrderBySourceVersionDesc(session.getReference())).isEmpty();
        assertThat(publicationCount(session.getReference())).isZero();
    }

    @Test
    void holdCreateReleaseAndExpiryPublishAvailabilityChanges() {
        ReserveBookingCapacityCommand reserve = reserveCommand(2);
        var held = operationsFacade.reserveBookingCapacity(reserve);

        assertThat(held.status().name()).isEqualTo("ACCEPTED");
        assertThat(latestSnapshot().sourceVersion()).isEqualTo(1);
        assertThat(latestSnapshot().sellableAvailability()).isEqualTo(8);

        operationsFacade.releaseBookingHolds(new ReleaseBookingHoldsCommand(
                reserve.bookingReference(), held.holdReferences(), UUID.randomUUID().toString(), UUID.randomUUID().toString()));

        assertThat(latestSnapshot().sourceVersion()).isEqualTo(2);
        assertThat(latestSnapshot().sellableAvailability()).isEqualTo(10);

        ReserveBookingCapacityCommand expiringReserve = reserveCommand(3);
        var expiringHold = operationsFacade.reserveBookingCapacity(expiringReserve);
        assertThat(latestSnapshot().sourceVersion()).isEqualTo(3);
        assertThat(latestSnapshot().sellableAvailability()).isEqualTo(7);

        CapacityHold hold = capacityHoldRepository.findByReference(expiringHold.holdReferences().getFirst().toString())
                .orElseThrow();
        hold.setExpiresAt(Instant.now().minusSeconds(1));
        capacityHoldRepository.saveAndFlush(hold);
        assertThat(capacityHoldService.expirePendingHolds()).isEqualTo(1);
        assertThat(latestSnapshot().sourceVersion()).isEqualTo(4);
        assertThat(latestSnapshot().sellableAvailability()).isEqualTo(10);
        assertThat(publicationCount(session.getReference())).isEqualTo(4);
    }

    @Test
    void releasingSeveralHoldsForOneSessionPublishesOnlyTheCompletePostState() {
        String bookingReference = "OTA-BOOKING-" + UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(600);
        UUID firstReference = createHold(bookingReference, 1, expiresAt);
        UUID secondReference = createHold(bookingReference, 2, expiresAt);

        var outcome = operationsFacade.releaseBookingHolds(new ReleaseBookingHoldsCommand(
                bookingReference, List.of(firstReference, secondReference),
                UUID.randomUUID().toString(), UUID.randomUUID().toString()));

        assertThat(outcome.status().name()).isEqualTo("ACCEPTED");
        assertThat(latestSnapshot().sourceVersion()).isEqualTo(1);
        assertThat(latestSnapshot().sellableAvailability()).isEqualTo(10);
        assertThat(snapshotCount()).isEqualTo(1);
        assertThat(publicationCount(session.getReference())).isEqualTo(1);
    }

    @Test
    void expiringSeveralHoldsForOneSessionPublishesOnlyTheCompletePostState() {
        String bookingReference = "OTA-BOOKING-" + UUID.randomUUID();
        Instant expiredAt = Instant.now().minusSeconds(10);
        UUID firstReference = createHold(bookingReference, 1, expiredAt);
        UUID secondReference = createHold(bookingReference, 2, expiredAt);

        // This shared local integration schema may contain unrelated expired
        // holds from earlier suites. Assert our two records were expired while
        // allowing the global sweep to process those records as well.
        assertThat(capacityHoldService.expirePendingHolds()).isGreaterThanOrEqualTo(2);
        assertThat(capacityHoldRepository.findByReference(firstReference.toString()).orElseThrow().getState())
                .isEqualTo(CapacityHoldState.EXPIRED);
        assertThat(capacityHoldRepository.findByReference(secondReference.toString()).orElseThrow().getState())
                .isEqualTo(CapacityHoldState.EXPIRED);

        assertThat(latestSnapshot().sourceVersion()).isEqualTo(1);
        assertThat(latestSnapshot().sellableAvailability()).isEqualTo(10);
        assertThat(snapshotCount()).isEqualTo(1);
        assertThat(publicationCount(session.getReference())).isEqualTo(1);
    }

    @Test
    void holdToTicketCommitKeepsPublishedAvailabilityAndDoesNotEmitAnotherVersion() {
        ReserveBookingCapacityCommand reserve = reserveCommand(2);
        var held = operationsFacade.reserveBookingCapacity(reserve);
        WorkshopSessionOtaSnapshot beforeCommit = latestSnapshot();

        var committed = operationsFacade.commitBookingHolds(new CommitBookingHoldsCommand(
                reserve.bookingReference(), held.holdReferences(), UUID.randomUUID().toString(), UUID.randomUUID().toString()));

        assertThat(committed.status().name()).isEqualTo("ACCEPTED");
        assertThat(latestSnapshot().sourceVersion()).isEqualTo(beforeCommit.sourceVersion());
        assertThat(latestSnapshot().sellableAvailability()).isEqualTo(beforeCommit.sellableAvailability());
        assertThat(publicationCount(session.getReference())).isEqualTo(1);
    }

    private ReserveBookingCapacityCommand reserveCommand(int participants) {
        String bookingReference = "OTA-BOOKING-" + UUID.randomUUID();
        return new ReserveBookingCapacityCommand(bookingReference, packageReference,
                List.of(UUID.fromString(session.getReference())), participants,
                UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }

    private WorkshopSessionOtaSnapshot latestSnapshot() {
        return resyncFacade.resync(session.getReference());
    }

    private UUID createHold(String bookingReference, int ticketCount, Instant expiresAt) {
        UUID reference = UUID.randomUUID();
        capacityHoldRepository.saveAndFlush(CapacityHold.builder()
                .reference(reference.toString())
                .session(session)
                .bookingReference(bookingReference)
                .requestedTicketCount(ticketCount)
                .state(CapacityHoldState.ACTIVE)
                .expiresAt(expiresAt)
                .build());
        return reference;
    }

    private int snapshotCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workshop_session_ota_snapshots WHERE session_reference = ?",
                Integer.class, session.getReference());
    }

    private int publicationCount(String sessionReference) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_publication WHERE event_type = ? AND serialized_event LIKE ?",
                Integer.class,
                "edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopSessionOtaSnapshotChanged",
                "%" + sessionReference + "%");
    }
}
