package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.PackageAvailabilityDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopPackageAvailabilityService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import java.sql.DriverManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * T158 (003.2 Phase 18, FR-037/FR-040, WF-02/05/09) on real MySQL. Needs the {@code WORKSHOP_OPERATIONS_TEST_JDBC_*}
 * variables of the {@code workshopoperations-mysql-test} profile (a disposable database; the migration test also
 * creates and drops its own schema on the same server).
 */
@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=12")
@ActiveProfiles("workshopoperations-mysql-test")
class WorkshopPackageAvailabilityIT {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final long ACTOR = 91001L;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private WorkshopPackageAvailabilityService availabilityService;
    @Autowired private WorkshopOperationsFacade facade;
    @Autowired private WorkshopLocationRepository locationRepository;
    @Autowired private WorkshopSlotRepository slotRepository;
    @Autowired private WorkshopSessionRepository sessionRepository;
    @Autowired private WorkshopPackageRepository packageRepository;
    @Autowired private CapacityHoldRepository holdRepository;
    @Autowired private AttendanceTicketRepository ticketRepository;
    @Autowired private WorkshopPackageLocationAvailabilityRepository availabilityRepository;

    private WorkshopLocation location;
    private int sequence;

    @BeforeEach
    void setUp() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            assumeTrue(connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql"),
                    "WorkshopPackageAvailabilityIT requires real MySQL evidence");
        }
        location = locationRepository.saveAndFlush(WorkshopLocation.builder()
                .code("WPLA-" + UUID.randomUUID()).displayName("WPLA Location").addressSnapshot("test").build());
        sequence = 0;
    }

    // ------------------------------------------------------------------ (a) migration seed

    @Test
    @DisplayName("T158(a): V178 applies on a database already at V177 and seeds 8 active rows (Hoan Kiem 5, Tay Ho 3, Da Nang 0)")
    void migrationSeedsTheInitialMatrix() throws Exception {
        String baseUrl = System.getenv("WORKSHOP_OPERATIONS_TEST_JDBC_URL");
        String user = System.getenv("WORKSHOP_OPERATIONS_TEST_JDBC_USER");
        String password = System.getenv("WORKSHOP_OPERATIONS_TEST_JDBC_PASSWORD");
        assumeTrue(baseUrl != null && baseUrl.startsWith("jdbc:mysql:"), "needs WORKSHOP_OPERATIONS_TEST_JDBC_URL");
        String schema = "wpla_mig_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String serverUrl = baseUrl.replaceFirst("(jdbc:mysql://[^/]+/)([^?]*)", "$1");
        String suffix = baseUrl.contains("?") ? baseUrl.substring(baseUrl.indexOf('?')) : "";
        String schemaUrl = serverUrl + schema + suffix;
        try (var admin = DriverManager.getConnection(serverUrl + suffix, user, password == null ? "" : password);
             var statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            try {
                Flyway upToV177 = Flyway.configure().dataSource(schemaUrl, user, password == null ? "" : password)
                        .defaultSchema(schema).locations("classpath:db/migration").target("177").load();
                upToV177.migrate();
                var beforeTables = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
                        schemaUrl, user, password == null ? "" : password));
                assertThat(beforeTables.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? "
                                + "AND table_name = 'workshop_package_location_availability'", Integer.class, schema))
                        .isZero();

                Flyway.configure().dataSource(schemaUrl, user, password == null ? "" : password)
                        .defaultSchema(schema).locations("classpath:db/migration").load().migrate();

                assertThat(beforeTables.queryForObject(
                        "SELECT COUNT(*) FROM " + schema + ".workshop_package_location_availability WHERE active = 1", Integer.class))
                        .isEqualTo(8);
                assertThat(count(beforeTables, schema, "HN-HOANKIEM")).isEqualTo(5);
                assertThat(count(beforeTables, schema, "HN-TAYHO")).isEqualTo(3);
                assertThat(count(beforeTables, schema, "DN-HAICHAU")).isZero();
                assertThat(beforeTables.queryForList(
                        "SELECT p.name FROM " + schema + ".workshop_package_location_availability a "
                                + "JOIN " + schema + ".workshop_packages p ON p.id = a.package_id "
                                + "JOIN " + schema + ".workshop_locations l ON l.id = a.location_id WHERE l.code = 'HN-TAYHO' ORDER BY p.name",
                        String.class))
                        .containsExactly("Signature Workshop - Brass", "Signature Workshop - Silver 925",
                                "Wax Casting Workshop");
            } finally {
                statement.execute("DROP DATABASE IF EXISTS " + schema);
            }
        }
    }

    private static int count(JdbcTemplate template, String schema, String locationCode) {
        return template.queryForObject("SELECT COUNT(*) FROM " + schema + ".workshop_package_location_availability a "
                + "JOIN " + schema + ".workshop_locations l ON l.id = a.location_id WHERE l.code = ? AND a.active = 1",
                Integer.class, locationCode);
    }

    // ------------------------------------------------------------------ (b) constraints

    @Test
    @DisplayName("T158(b): the pair is unique and both foreign keys are enforced")
    void uniquePairAndForeignKeys() {
        long pkg = Long.parseLong(publishedPackage());
        availabilityService.setAvailability(ACTOR, pkg, location.getId(), PackageAvailabilityDtos.State.ACTIVE, null, null);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO workshop_package_location_availability "
                + "(package_id, location_id, active, version, created_at, updated_at) "
                + "VALUES (?, ?, 1, 0, NOW(6), NOW(6))", pkg, location.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO workshop_package_location_availability "
                + "(package_id, location_id, active, version, created_at, updated_at) "
                + "VALUES (?, ?, 1, 0, NOW(6), NOW(6))", -1L, location.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO workshop_package_location_availability "
                + "(package_id, location_id, active, version, created_at, updated_at) "
                + "VALUES (?, ?, 1, 0, NOW(6), NOW(6))", pkg, 987654321L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------ (c) toggle with booking, ticket, hold

    @Test
    @DisplayName("T158(c): toggle INACTIVE while a booking, ticket and unexpired hold exist; nothing is cancelled, the hold still commits, a new reserve is refused without consuming capacity")
    void toggleKeepsExistingBookingTicketAndHold() {
        String pkg = publishedPackage();
        availabilityService.setAvailability(ACTOR, Long.parseLong(pkg), location.getId(),
                PackageAvailabilityDtos.State.ACTIVE, null, null);
        WorkshopSession session = session(10);
        String bookingA = "BK-" + UUID.randomUUID();
        CapacityHoldOutcome holdA = reserve(bookingA, pkg, session, 2);
        assertThat(holdA.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        assertThat(facade.commitBookingHolds(new CommitBookingHoldsCommand(bookingA, holdA.holdReferences(),
                "op-" + UUID.randomUUID(), "corr")).status()).isEqualTo(OutcomeStatus.ACCEPTED);
        String bookingB = "BK-" + UUID.randomUUID();
        CapacityHoldOutcome holdB = reserve(bookingB, pkg, session, 3);
        assertThat(holdB.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        int ticketsBefore = ticketRepository.findByBookingReference(bookingA).size();
        int remainingBefore = remaining(session);
        long auditsBefore = availabilityAudits();

        PackageAvailabilityDtos.Cell cell = availabilityService.setAvailability(ACTOR, Long.parseLong(pkg),
                location.getId(), PackageAvailabilityDtos.State.INACTIVE, null, "corr-toggle");

        assertThat(cell.state()).isEqualTo(PackageAvailabilityDtos.State.INACTIVE);
        assertThat(availabilityAudits()).isEqualTo(auditsBefore + 1);
        assertThat(ticketRepository.findByBookingReference(bookingA)).hasSize(ticketsBefore).allSatisfy(ticket ->
                assertThat(ticket.getValidityState()).isEqualTo(TicketValidityState.ACTIVE));
        assertThat(sessionRepository.findById(session.getId()).orElseThrow().getState()).isEqualTo(SessionState.PUBLISHED);
        assertThat(remaining(session)).isEqualTo(remainingBefore);

        CapacityHoldOutcome refused = reserve("BK-" + UUID.randomUUID(), pkg, session, 1);
        assertThat(refused.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(refused.reason()).isEqualTo(RejectionReason.PACKAGE_NOT_AVAILABLE_AT_LOCATION);
        assertThat(remaining(session)).isEqualTo(remainingBefore);

        // FR-040: the unexpired hold created before the toggle still commits.
        assertThat(facade.commitBookingHolds(new CommitBookingHoldsCommand(bookingB, holdB.holdReferences(),
                "op-" + UUID.randomUUID(), "corr")).status()).isEqualTo(OutcomeStatus.ACCEPTED);
        assertThat(ticketRepository.findByBookingReference(bookingB)).isNotEmpty();
    }

    // ------------------------------------------------------------------ (d) toggle racing reserve

    @Test
    @DisplayName("T158(d): reserves racing a toggle are each ACCEPTED or PACKAGE_NOT_AVAILABLE_AT_LOCATION, never a partial hold")
    void concurrentToggleAndReserveNeverLeaveAPartialHold() throws Exception {
        String pkg = publishedPackage();
        availabilityService.setAvailability(ACTOR, Long.parseLong(pkg), location.getId(),
                PackageAvailabilityDtos.State.ACTIVE, null, null);
        WorkshopSession session = session(40);
        int threads = 8;
        CountDownLatch ready = new CountDownLatch(threads + 1);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads + 1);
        try {
            List<Future<CapacityHoldOutcome>> reserves = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                final String booking = "BK-" + UUID.randomUUID();
                reserves.add(pool.submit(raceCallable(ready, go, () -> reserve(booking, pkg, session, 1))));
            }
            Future<?> toggle = pool.submit(raceCallable(ready, go, () -> availabilityService.setAvailability(ACTOR,
                    Long.parseLong(pkg), location.getId(), PackageAvailabilityDtos.State.INACTIVE, null, "corr-race")));
            ready.await();
            go.countDown();
            toggle.get();
            int accepted = 0;
            for (Future<CapacityHoldOutcome> result : reserves) {
                CapacityHoldOutcome outcome = result.get();
                if (outcome.status() == OutcomeStatus.ACCEPTED) {
                    accepted++;
                } else {
                    assertThat(outcome.status()).isEqualTo(OutcomeStatus.REJECTED);
                    assertThat(outcome.reason()).isEqualTo(RejectionReason.PACKAGE_NOT_AVAILABLE_AT_LOCATION);
                }
            }
            assertThat(40 - remaining(session)).isEqualTo(accepted);
            assertThat(availabilityService.isSellable(Long.parseLong(pkg), location.getId())).isFalse();
        } finally {
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------------------ (e) two Managers, one cell

    @Test
    @DisplayName("T158(e): two Managers editing one cell: one wins, the other gets an optimistic-lock conflict")
    void twoManagersEditingOneCell() throws Exception {
        long pkg = Long.parseLong(publishedPackage());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<PackageAvailabilityDtos.Cell>> writes = new ArrayList<>();
            for (long manager : new long[] {ACTOR, ACTOR + 1}) {
                writes.add(pool.submit(raceCallable(ready, go, () -> availabilityService.setAvailability(manager, pkg,
                        location.getId(), PackageAvailabilityDtos.State.ACTIVE, 0L, "corr-" + manager))));
            }
            ready.await();
            go.countDown();
            int wins = 0;
            int conflicts = 0;
            for (Future<PackageAvailabilityDtos.Cell> write : writes) {
                try {
                    write.get();
                    wins++;
                } catch (java.util.concurrent.ExecutionException failure) {
                    assertThat(failure.getCause()).isInstanceOf(ObjectOptimisticLockingFailureException.class);
                    conflicts++;
                }
            }
            assertThat(wins).isEqualTo(1);
            assertThat(conflicts).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(availabilityRepository.findByPackageIdAndLocationId(pkg, location.getId())).isPresent();
        assertThat(availabilityEvents(pkg)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ (f) audit and cell commit together

    @Test
    @DisplayName("T158(f): when the audit row cannot be written, the cell change rolls back with it")
    void auditFailureRollsBackTheCellChange() {
        long pkg = Long.parseLong(publishedPackage());
        String tooLongCorrelation = "x".repeat(200); // operations_audit_events.correlation_id is VARCHAR(64)

        assertThatThrownBy(() -> availabilityService.setAvailability(ACTOR, pkg, location.getId(),
                PackageAvailabilityDtos.State.ACTIVE, null, tooLongCorrelation))
                .isInstanceOf(RuntimeException.class);

        assertThat(availabilityRepository.findByPackageIdAndLocationId(pkg, location.getId())).isEmpty();
        assertThat(availabilityEvents(pkg)).isZero();
        // and the happy path writes both
        availabilityService.setAvailability(ACTOR, pkg, location.getId(), PackageAvailabilityDtos.State.ACTIVE, null, "ok");
        assertThat(availabilityRepository.findByPackageIdAndLocationId(pkg, location.getId())).isPresent();
        assertThat(availabilityEvents(pkg)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ helpers

    private <T> Callable<T> raceCallable(CountDownLatch ready, CountDownLatch go, Callable<T> body) {
        return () -> {
            ready.countDown();
            go.await();
            return body.call();
        };
    }

    private String publishedPackage() {
        return String.valueOf(packageRepository.save(WorkshopPackage.builder()
                .name("WPLA package " + UUID.randomUUID()).description("fixture").publicPrice(1000L)
                .minParticipants(1).maxParticipants(100).requiredSessionCount(1)
                .sameLocationRequired(false).minimumSameDaySlotGap(0)
                .state(WorkshopPackageState.PUBLISHED).build()).getId());
    }

    private CapacityHoldOutcome reserve(String booking, String pkg, WorkshopSession session, int tickets) {
        return facade.reserveBookingCapacity(new ReserveBookingCapacityCommand(booking, pkg,
                List.of(UUID.fromString(session.getReference())), tickets, "op-" + UUID.randomUUID(), "corr"));
    }

    private int remaining(WorkshopSession session) {
        return facade.getSaleEligibility(new SaleEligibilityQuery(UUID.fromString(session.getReference())))
                .remainingCapacity();
    }

    private long availabilityAudits() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM operations_audit_events WHERE action = ?", Long.class,
                WorkshopPackageAvailabilityService.AUDIT_ACTION);
    }

    private long availabilityEvents(long packageId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM operations_audit_events WHERE action = ? "
                + "AND JSON_EXTRACT(metadata, '$.packageId') = ?", Long.class,
                WorkshopPackageAvailabilityService.AUDIT_ACTION, packageId);
    }

    private WorkshopSession session(int capacity) {
        LocalDate date = LocalDate.now(ZONE).plusDays(10);
        LocalTime startTime = LocalTime.of(8, 0).plusMinutes(sequence++ * 20L);
        Instant start = date.atTime(startTime).atZone(ZONE).toInstant();
        WorkshopSlot slot = slotRepository.saveAndFlush(WorkshopSlot.builder()
                .location(location).label("WPLA slot " + UUID.randomUUID())
                .startTime(startTime).endTime(startTime.plusMinutes(15)).build());
        return sessionRepository.saveAndFlush(WorkshopSession.builder()
                .location(location).slot(slot).workshopDate(date)
                .scheduledStartAt(start).scheduledEndAt(start.plusSeconds(900))
                .checkInOpensAt(start.minusSeconds(1800)).capacity(capacity).state(SessionState.PUBLISHED).build());
    }
}
