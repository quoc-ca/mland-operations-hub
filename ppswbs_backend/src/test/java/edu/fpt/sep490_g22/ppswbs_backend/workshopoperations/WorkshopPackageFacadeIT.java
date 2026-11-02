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
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.CustomerSessionAvailabilityResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.CapacityHoldOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.PublishedPackageSelectionQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.RejectionReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.ReserveBookingCapacityCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SaleEligibilityQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopOperationsFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopPackageDiscoveryService;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
@ActiveProfiles({"test", "workshopoperations-mysql-test"})
class WorkshopPackageFacadeIT {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private WorkshopOperationsFacade operationsFacade;
    @Autowired private WorkshopPackageDiscoveryService discoveryService;
    @Autowired private WorkshopLocationRepository locationRepository;
    @Autowired private WorkshopPackageRepository packageRepository;
    @Autowired private WorkshopSessionRepository sessionRepository;
    @Autowired private WorkshopSlotRepository slotRepository;

    @BeforeEach
    void requireRealMySql() throws Exception {
        try (var connection = jdbcTemplate.getDataSource().getConnection()) {
            assumeTrue(connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql"),
                    "WorkshopPackageFacadeIT requires real MySQL/Flyway evidence");
        }
    }

    @Test
    void publishedPackageSnapshotIsPublicSafeAndRetiredPackageIsNotReturned() {
        WorkshopPackage published = createPackage(WorkshopPackageState.PUBLISHED, 1, 4, 1, true, 0);

        var snapshot = operationsFacade.getPublishedPackageSelection(
                new PublishedPackageSelectionQuery(published.getId().toString()));

        assertThat(snapshot.packageReference()).isEqualTo(published.getId().toString());
        assertThat(snapshot.publicPerParticipantPrice()).isEqualTo(published.getPublicPrice());
        assertThat(snapshot.minParticipants()).isEqualTo(1);
        assertThat(snapshot.maxParticipants()).isEqualTo(4);
        assertThat(snapshot.requiredSessionCount()).isEqualTo(1);
        assertThat(snapshot.sameLocationRequired()).isTrue();
        assertThat(snapshot.minimumSameDaySlotGap()).isZero();
        assertThat(snapshot.packageName()).isEqualTo(published.getName());

        published.setState(WorkshopPackageState.RETIRED);
        packageRepository.save(published);
        assertThat(operationsFacade.getPublishedPackageSelection(
                new PublishedPackageSelectionQuery(published.getId().toString()))).isNull();
    }

    @Test
    void saleEligibilitySnapshotUsesCanonicalUuidAndUnknownReferenceReturnsNoFacts() {
        WorkshopLocation location = location("PKG-SNAPSHOT");
        WorkshopSession session = createSession(location, 3, 10, LocalDate.now(ZONE).plusDays(8));
        UUID sessionReference = UUID.fromString(session.getReference());

        var snapshot = operationsFacade.getSaleEligibility(new SaleEligibilityQuery(sessionReference));

        assertThat(snapshot.sessionReference()).isEqualTo(sessionReference);
        assertThat(snapshot.locationReference()).isEqualTo(location.getId());
        assertThat(snapshot.workshopDate()).isEqualTo(session.getWorkshopDate());
        assertThat(java.util.Arrays.stream(snapshot.getClass().getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)).doesNotContain("sessionId");
        assertThat(operationsFacade.getSaleEligibility(new SaleEligibilityQuery(UUID.randomUUID()))).isNull();
    }

    @Test
    void bookingFacadeProjectionContainsOnlyApprovedPackageSelectionFacts() {
        assertThat(java.util.Arrays.stream(
                        edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.PublishedPackageSelectionSnapshot.class
                                .getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .containsExactly("packageReference", "publicPerParticipantPrice", "minParticipants",
                        "maxParticipants", "requiredSessionCount", "sameLocationRequired",
                        "minimumSameDaySlotGap", "packageName");
    }

    @Test
    void reserveRejectsPackageRetiredAfterCustomerDiscovery() {
        WorkshopPackage published = createPackage(WorkshopPackageState.PUBLISHED, 1, 4, 1, true, 0);
        WorkshopSession session = createSession(location("PKG-STALE"), 3, 10, LocalDate.now(ZONE).plusDays(8));
        assertThat(operationsFacade.getPublishedPackageSelection(
                new PublishedPackageSelectionQuery(published.getId().toString()))).isNotNull();

        published.setState(WorkshopPackageState.RETIRED);
        packageRepository.save(published);
        String booking = bookingReference("PKG-STALE");
        CapacityHoldOutcome outcome = reserve(published, List.of(session), 1, booking);

        assertThat(outcome.status().name()).isEqualTo("REJECTED");
        assertThat(outcome.reason()).isEqualTo(RejectionReason.PACKAGE_NOT_PUBLISHED);
        assertThat(holdsFor(booking)).isZero();
    }

    @Test
    void reserveWithoutPackageReferenceIsRejectedWithoutCreatingHolds() {
        WorkshopLocation location = location("PKG-MISSING-REF");
        WorkshopSession session = createSession(location, 3, 10, LocalDate.now(ZONE).plusDays(8));
        String booking = bookingReference("PKG-MISSING-REF");

        @SuppressWarnings("deprecation")
        ReserveBookingCapacityCommand legacyCommand = new ReserveBookingCapacityCommand(
                booking,
                List.of(UUID.fromString(session.getReference())),
                1,
                "op-" + UUID.randomUUID(),
                "corr-" + UUID.randomUUID());
        CapacityHoldOutcome outcome = operationsFacade.reserveBookingCapacity(legacyCommand);

        assertThat(outcome.status().name()).isEqualTo("REJECTED");
        assertThat(outcome.reason()).isEqualTo(RejectionReason.PACKAGE_SELECTION_INVALID);
        assertThat(holdsFor(booking)).isZero();
    }

    @Test
    void reserveRechecksParticipantBoundsAndRequiredSessionCount() {
        WorkshopPackage published = createPackage(WorkshopPackageState.PUBLISHED, 2, 4, 2, true, 0);
        WorkshopLocation location = location("PKG-BOUNDS");
        WorkshopSession first = createSession(location, 3, 10, LocalDate.now(ZONE).plusDays(8));
        WorkshopSession second = createSession(location, 4, 10, LocalDate.now(ZONE).plusDays(9));

        String belowMinimumBooking = bookingReference("PKG-BOUNDS-MIN");
        String wrongCountBooking = bookingReference("PKG-BOUNDS-COUNT");
        CapacityHoldOutcome belowMinimum = reserve(published, List.of(first, second), 1, belowMinimumBooking);
        CapacityHoldOutcome wrongSessionCount = reserve(published, List.of(first), 2, wrongCountBooking);

        assertPackageSelectionRejected(belowMinimum);
        assertPackageSelectionRejected(wrongSessionCount);
        assertThat(holdsFor(belowMinimumBooking) + holdsFor(wrongCountBooking)).isZero();
    }

    @Test
    void reserveRechecksSameLocationRule() {
        WorkshopPackage published = createPackage(WorkshopPackageState.PUBLISHED, 1, 4, 2, true, 0);
        WorkshopSession first = createSession(location("PKG-LOC-A"), 3, 10, LocalDate.now(ZONE).plusDays(8));
        WorkshopSession second = createSession(location("PKG-LOC-B"), 3, 10, LocalDate.now(ZONE).plusDays(9));

        String booking = bookingReference("PKG-LOC");
        CapacityHoldOutcome outcome = reserve(published, List.of(first, second), 1, booking);

        assertPackageSelectionRejected(outcome);
        assertThat(holdsFor(booking)).isZero();
    }

    @Test
    void reserveRequiresConfiguredSlotGapForSameDaySessions() {
        WorkshopPackage published = createPackage(WorkshopPackageState.PUBLISHED, 1, 4, 2, true, 1);
        WorkshopLocation location = location("PKG-GAP");
        LocalDate day = LocalDate.now(ZONE).plusDays(10);
        WorkshopSession first = createSession(location, 2, 10, day);
        WorkshopSession adjacent = createSession(location, 3, 10, day);

        String rejectedBooking = bookingReference("PKG-GAP-ADJ");
        CapacityHoldOutcome rejected = reserve(published, List.of(first, adjacent), 1, rejectedBooking);
        WorkshopSession nonAdjacent = createSession(location, 5, 10, day);
        String acceptedBooking = bookingReference("PKG-GAP-OK");
        CapacityHoldOutcome accepted = reserve(published, List.of(first, nonAdjacent), 1, acceptedBooking);

        assertPackageSelectionRejected(rejected);
        assertThat(accepted.status().name()).isEqualTo("ACCEPTED");
        assertThat(holdsFor(rejectedBooking)).isZero();
        assertThat(holdsFor(acceptedBooking)).isEqualTo(2);
    }

    @Test
    void reserveIsAllOrNoneWhenAnyRequiredSessionHasInsufficientCapacity() {
        WorkshopPackage published = createPackage(WorkshopPackageState.PUBLISHED, 1, 4, 2, true, 0);
        WorkshopLocation location = location("PKG-CAPACITY");
        WorkshopSession first = createSession(location, 3, 10, LocalDate.now(ZONE).plusDays(8));
        WorkshopSession second = createSession(location, 3, 1, LocalDate.now(ZONE).plusDays(9));
        String booking = bookingReference("PKG-CAPACITY");

        CapacityHoldOutcome outcome = reserve(published, List.of(first, second), 2, booking);

        assertThat(outcome.status().name()).isEqualTo("REJECTED");
        assertThat(outcome.reason()).isEqualTo(RejectionReason.INSUFFICIENT_CAPACITY);
        assertThat(holdsFor(booking)).isZero();
    }

    @Test
    void customerDiscoveryReturnsOnlyPublishedFutureSelectableSessionsAndExactCapacitySnapshot() {
        WorkshopPackage published = createPackage(WorkshopPackageState.PUBLISHED, 1, 4, 1, true, 0);
        WorkshopLocation location = location("PKG-DISCOVERY");
        WorkshopSession selectable = createSession(location, 3, 2, LocalDate.now(ZONE).plusDays(8));
        WorkshopSession soldOut = createSession(location, 4, 1, LocalDate.now(ZONE).plusDays(8));
        WorkshopSession notPublished = createSession(location, 5, 10, LocalDate.now(ZONE).plusDays(8));
        notPublished.setState(SessionState.DRAFT);
        sessionRepository.saveAndFlush(notPublished);
        WorkshopSession past = createSession(location, 6, 10, LocalDate.now(ZONE).minusDays(1));

        assertThat(reserve(published, List.of(selectable), 1).status().name()).isEqualTo("ACCEPTED");
        assertThat(reserve(published, List.of(soldOut), 1).status().name()).isEqualTo("ACCEPTED");

        List<CustomerSessionAvailabilityResponse> result = discoveryService.discoverSessions(
                published.getId(), location.getId(), LocalDate.now(ZONE), LocalDate.now(ZONE).plusDays(30));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().sessionReference()).isEqualTo(UUID.fromString(selectable.getReference()));
        assertThat(result.getFirst().locationId()).isEqualTo(location.getId());
        assertThat(result.getFirst().customerSelectable()).isTrue();
        assertThat(result.getFirst().saleState()).isEqualTo(SessionState.PUBLISHED.name());
        assertThat(result.getFirst().remainingCapacity()).isEqualTo(1);
        assertThat(result.getFirst().sessionReference().toString()).hasSize(36);
        assertThat(result).noneMatch(availability -> availability.sessionReference().equals(UUID.fromString(soldOut.getReference()))
                || availability.sessionReference().equals(UUID.fromString(notPublished.getReference()))
                || availability.sessionReference().equals(UUID.fromString(past.getReference())));
    }

    @Test
    void concurrentPackageReservesCannotOversellTheLastSeat() throws Exception {
        WorkshopPackage published = createPackage(WorkshopPackageState.PUBLISHED, 1, 4, 1, true, 0);
        WorkshopSession oneSeatSession = createSession(location("PKG-RACE"), 3, 1,
                LocalDate.now(ZONE).plusDays(12));
        String firstBooking = bookingReference("PKG-RACE-A");
        String secondBooking = bookingReference("PKG-RACE-B");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<CapacityHoldOutcome> first = executor.submit(() -> concurrentReserve(
                    published, oneSeatSession, firstBooking, ready, start));
            Future<CapacityHoldOutcome> second = executor.submit(() -> concurrentReserve(
                    published, oneSeatSession, secondBooking, ready, start));
            ready.await();
            start.countDown();

            List<CapacityHoldOutcome> outcomes = List.of(first.get(), second.get());
            assertThat(outcomes.stream().filter(outcome -> outcome.status().name().equals("ACCEPTED"))).hasSize(1);
            assertThat(outcomes.stream().filter(outcome -> outcome.reason() == RejectionReason.INSUFFICIENT_CAPACITY))
                    .hasSize(1);
            assertThat(holdsFor(firstBooking) + holdsFor(secondBooking)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private CapacityHoldOutcome concurrentReserve(WorkshopPackage workshopPackage, WorkshopSession session,
                                                  String booking, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        start.await();
        return reserve(workshopPackage, List.of(session), 1, booking);
    }

    private CapacityHoldOutcome reserve(WorkshopPackage workshopPackage, List<WorkshopSession> sessions,
                                       int participants) {
        return reserve(workshopPackage, sessions, participants, bookingReference("PKG-RESERVE"));
    }

    private CapacityHoldOutcome reserve(WorkshopPackage workshopPackage, List<WorkshopSession> sessions,
                                       int participants, String booking) {
        return operationsFacade.reserveBookingCapacity(new ReserveBookingCapacityCommand(
                booking,
                workshopPackage.getId().toString(),
                sessions.stream().map(session -> UUID.fromString(session.getReference())).toList(),
                participants,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString()));
    }

    private WorkshopPackage createPackage(WorkshopPackageState state, int min, int max, int required,
                                          boolean sameLocation, int gap) {
        return packageRepository.save(WorkshopPackage.builder()
                .name("Package " + UUID.randomUUID())
                .description("Generic package facade integration test")
                .publicPrice(450_000L)
                .minParticipants(min)
                .maxParticipants(max)
                .requiredSessionCount(required)
                .sameLocationRequired(sameLocation)
                .minimumSameDaySlotGap(gap)
                .state(state)
                .build());
    }

    private WorkshopLocation location(String prefix) {
        return locationRepository.saveAndFlush(WorkshopLocation.builder()
                .code(prefix + "-" + UUID.randomUUID())
                .displayName("Package Test Location")
                .addressSnapshot("test")
                .build());
    }

    private WorkshopSession createSession(WorkshopLocation location, int slotIndex, int capacity, LocalDate day) {
        LocalTime start = LocalTime.of(8 + slotIndex, 0);
        WorkshopSlot slot = slotRepository.saveAndFlush(WorkshopSlot.builder()
                .reference(UUID.randomUUID().toString())
                .location(location)
                .label("Package test slot " + slotIndex)
                .startTime(start)
                .endTime(start.plusMinutes(45))
                .defaultCapacity(Math.max(1, capacity))
                .active(true)
                .build());
        Instant scheduledStart = day.atTime(start).atZone(ZONE).toInstant();
        return sessionRepository.saveAndFlush(WorkshopSession.builder()
                .reference(UUID.randomUUID().toString())
                .location(location)
                .slot(slot)
                .workshopDate(day)
                .scheduledStartAt(scheduledStart)
                .scheduledEndAt(scheduledStart.plusSeconds(45 * 60L))
                .checkInOpensAt(scheduledStart.minusSeconds(30 * 60L))
                .capacity(capacity)
                .state(SessionState.PUBLISHED)
                .build());
    }

    private int holdsFor(String bookingReference) {
        return jdbcTemplate.queryForObject(
                "select count(*) from capacity_holds where booking_reference = ?", Integer.class, bookingReference);
    }

    private static String bookingReference(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private static void assertPackageSelectionRejected(CapacityHoldOutcome outcome) {
        assertThat(outcome.status().name()).isEqualTo("REJECTED");
        assertThat(outcome.reason()).isEqualTo(RejectionReason.PACKAGE_SELECTION_INVALID);
    }
}
