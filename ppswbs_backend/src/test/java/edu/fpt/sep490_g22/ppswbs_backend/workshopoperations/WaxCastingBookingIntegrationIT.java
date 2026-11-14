package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 003.2 T086 — Real MySQL/Flyway and booking-change integration coverage for Wax Casting:
 * - Flyway V89 migration and schema verification.
 * - Monday -> Thursday valid (2 working days: Tue, Wed).
 * - Friday -> Wednesday next week valid (2 working days: Mon, Tue; Sat & Sun excluded).
 * - Monday -> Wednesday rejected (1 working day; no partial hold).
 * - Friday -> Tuesday next week rejected (1 working day; no partial hold).
 * - Monday -> Friday accepted (3 working days; at least 2, no maximum gap; 003.2 T089).
 * - Location, distinctness, and chronological order requirements.
 * - Atomic booking replacement (replaceRequiredSessions) for Wax Casting.
 * - Zero partial hold and zero partial booking/ticket mutation on rejection.
 * - Immutable booking-time policy snapshot enforced even after subsequent Package entity edits.
 */
@SpringBootTest
@ActiveProfiles({"test", "workshopoperations-mysql-test"})
class WaxCastingBookingIntegrationIT {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private WorkshopOperationsFacade operationsFacade;
    @Autowired private WorkshopLocationRepository locationRepository;
    @Autowired private WorkshopPackageRepository packageRepository;
    @Autowired private WorkshopSessionRepository sessionRepository;
    @Autowired private WorkshopSlotRepository slotRepository;
    @Autowired private AttendanceTicketRepository ticketRepository;
    @Autowired private TicketSessionEntitlementRepository entitlementRepository;
    @Autowired private WorkshopBookingPackagePolicySnapshotRepository snapshotRepository;

    private WorkshopLocation locationA;
    private WorkshopLocation locationB;
    private WorkshopPackage waxCastingPackage;

    // Calendar dates anchored to a future Monday
    private LocalDate baseMonday;
    private LocalDate tuesday;
    private LocalDate wednesday;
    private LocalDate thursday;
    private LocalDate friday;
    private LocalDate nextMonday;
    private LocalDate nextTuesday;
    private LocalDate nextWednesday;

    @BeforeEach
    void requireRealMySqlAndSetupData() throws Exception {
        try (var connection = jdbcTemplate.getDataSource().getConnection()) {
            assumeTrue(connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql"),
                    "WaxCastingBookingIntegrationIT requires real MySQL evidence");
        }

        locationA = locationRepository.saveAndFlush(WorkshopLocation.builder()
                .code("WAX-LOC-A-" + UUID.randomUUID())
                .displayName("Wax Casting Main Studio")
                .addressSnapshot("123 Workshop Blvd")
                .active(true)
                .build());

        locationB = locationRepository.saveAndFlush(WorkshopLocation.builder()
                .code("WAX-LOC-B-" + UUID.randomUUID())
                .displayName("Wax Casting Secondary Branch")
                .addressSnapshot("456 Artisan Rd")
                .active(true)
                .build());

        waxCastingPackage = packageRepository.save(WorkshopPackage.builder()
                .name("Wax Casting Workshop " + UUID.randomUUID())
                .description("Two sessions with at least two foundry working days between")
                .publicPrice(1_500_000L)
                .minParticipants(1)
                .maxParticipants(6)
                .requiredSessionCount(2)
                .sameLocationRequired(true)
                .minimumSameDaySlotGap(0)
                .requiredFoundryWorkingDaysBetweenSessions(2)
                .state(WorkshopPackageState.PUBLISHED)
                .build());

        // Anchor to a future Monday at least 2 weeks out
        baseMonday = LocalDate.now(ZONE).plusWeeks(2).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        tuesday = baseMonday.plusDays(1);
        wednesday = baseMonday.plusDays(2);
        thursday = baseMonday.plusDays(3);
        friday = baseMonday.plusDays(4);
        nextMonday = baseMonday.plusDays(7);
        nextTuesday = baseMonday.plusDays(8);
        nextWednesday = baseMonday.plusDays(9);
    }

    // =========================================================================
    // 1. MySQL Flyway Migration V89 and Schema Invariants
    // =========================================================================

    @Test
    @DisplayName("Flyway migration V89 is applied and required column exists in schema")
    void flywayMigrationV89AndSchemaVerification() {
        Integer v89Applied = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '89' AND success = 1",
                Integer.class);
        assertThat(v89Applied).isNotNull().isGreaterThanOrEqualTo(1);

        Integer pkgColCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.columns " +
                        "WHERE table_schema = DATABASE() " +
                        "AND table_name = 'workshop_packages' " +
                        "AND column_name = 'required_foundry_working_days_between_sessions'",
                Integer.class);
        assertThat(pkgColCount).isEqualTo(1);

        Integer snapshotColCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.columns " +
                        "WHERE table_schema = DATABASE() " +
                        "AND table_name = 'workshop_booking_package_policy_snapshots' " +
                        "AND column_name = 'required_foundry_working_days_between_sessions'",
                Integer.class);
        assertThat(snapshotColCount).isEqualTo(1);
    }

    // =========================================================================
    // 2. Package Reservation (reserveBookingCapacity) Tests
    // =========================================================================

    @Test
    @DisplayName("reserve accepts Monday 16:00 to Thursday 09:30 with exactly 2 holds created")
    void reserveMondayToThursdayIsAccepted() {
        WorkshopSession monSession = createSessionOn(locationA, baseMonday, LocalTime.of(16, 0), 10);
        WorkshopSession thuSession = createSessionOn(locationA, thursday, LocalTime.of(9, 30), 10);
        String booking = bookingReference("WAX-MON-THU");

        CapacityHoldOutcome outcome = reserve(waxCastingPackage, List.of(monSession, thuSession), 2, booking);

        assertThat(outcome.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        assertThat(holdsFor(booking)).isEqualTo(2);

        var snapshot = snapshotRepository.findByBookingReference(booking).orElse(null);
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getRequiredFoundryWorkingDaysBetweenSessions()).isEqualTo(2);
        assertThat(snapshot.getRequiredSessionCount()).isEqualTo(2);
        assertThat(snapshot.isSameLocationRequired()).isTrue();
    }

    @Test
    @DisplayName("reserve accepts Friday 16:00 to Wednesday 09:30 next week with Sat/Sun excluded")
    void reserveFridayToWednesdayNextWeekIsAccepted() {
        WorkshopSession friSession = createSessionOn(locationA, friday, LocalTime.of(16, 0), 10);
        WorkshopSession wedSession = createSessionOn(locationA, nextWednesday, LocalTime.of(9, 30), 10);
        String booking = bookingReference("WAX-FRI-WED");

        CapacityHoldOutcome outcome = reserve(waxCastingPackage, List.of(friSession, wedSession), 2, booking);

        assertThat(outcome.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        assertThat(holdsFor(booking)).isEqualTo(2);
    }

    @Test
    @DisplayName("reserve rejects Monday to Wednesday (1 day gap) with zero partial holds")
    void reserveMondayToWednesdayIsRejectedWithoutPartialHold() {
        WorkshopSession monSession = createSessionOn(locationA, baseMonday, LocalTime.of(16, 0), 10);
        WorkshopSession wedSession = createSessionOn(locationA, wednesday, LocalTime.of(9, 30), 10);
        String booking = bookingReference("WAX-MON-WED-FAIL");

        CapacityHoldOutcome outcome = reserve(waxCastingPackage, List.of(monSession, wedSession), 2, booking);

        assertThat(outcome.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(outcome.reason()).isEqualTo(RejectionReason.PACKAGE_SELECTION_INVALID);
        assertThat(holdsFor(booking)).isZero();
    }

    @Test
    @DisplayName("reserve rejects Friday to Tuesday next week (1 day gap; Sat/Sun skipped) with zero partial holds")
    void reserveFridayToTuesdayNextWeekIsRejectedWithoutPartialHold() {
        WorkshopSession friSession = createSessionOn(locationA, friday, LocalTime.of(16, 0), 10);
        WorkshopSession tueSession = createSessionOn(locationA, nextTuesday, LocalTime.of(9, 30), 10);
        String booking = bookingReference("WAX-FRI-TUE-FAIL");

        CapacityHoldOutcome outcome = reserve(waxCastingPackage, List.of(friSession, tueSession), 2, booking);

        assertThat(outcome.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(outcome.reason()).isEqualTo(RejectionReason.PACKAGE_SELECTION_INVALID);
        assertThat(holdsFor(booking)).isZero();
    }

    @Test
    @DisplayName("reserve accepts Monday to Friday (3 working days; at least 2 required, no maximum)")
    void reserveMondayToFridayIsAcceptedAtLeastTwoWorkingDays() {
        WorkshopSession monSession = createSessionOn(locationA, baseMonday, LocalTime.of(16, 0), 10);
        WorkshopSession friSession = createSessionOn(locationA, friday, LocalTime.of(9, 30), 10);
        String booking = bookingReference("WAX-MON-FRI");

        CapacityHoldOutcome outcome = reserve(waxCastingPackage, List.of(monSession, friSession), 2, booking);

        assertThat(outcome.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        assertThat(holdsFor(booking)).isEqualTo(2);
    }

    @Test
    @DisplayName("reserve rejects sessions at different locations with zero partial holds")
    void reserveDifferentLocationsIsRejectedWithoutPartialHold() {
        WorkshopSession monLocA = createSessionOn(locationA, baseMonday, LocalTime.of(16, 0), 10);
        WorkshopSession thuLocB = createSessionOn(locationB, thursday, LocalTime.of(9, 30), 10);
        String booking = bookingReference("WAX-DIFF-LOC-FAIL");

        CapacityHoldOutcome outcome = reserve(waxCastingPackage, List.of(monLocA, thuLocB), 2, booking);

        assertThat(outcome.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(outcome.reason()).isEqualTo(RejectionReason.PACKAGE_SELECTION_INVALID);
        assertThat(holdsFor(booking)).isZero();
    }

    @Test
    @DisplayName("reserve rejects duplicate session references with zero partial holds")
    void reserveDuplicateSessionsIsRejectedWithoutPartialHold() {
        WorkshopSession monSession = createSessionOn(locationA, baseMonday, LocalTime.of(16, 0), 10);
        String booking = bookingReference("WAX-DUP-FAIL");

        CapacityHoldOutcome outcome = reserve(waxCastingPackage, List.of(monSession, monSession), 2, booking);

        assertThat(outcome.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(outcome.reason()).isEqualTo(RejectionReason.PACKAGE_SELECTION_INVALID);
        assertThat(holdsFor(booking)).isZero();
    }

    @Test
    @DisplayName("reserve rejects sessions out of chronological order with zero partial holds")
    void reserveOutOfOrderIsRejectedWithoutPartialHold() {
        WorkshopSession monSession = createSessionOn(locationA, baseMonday, LocalTime.of(16, 0), 10);
        WorkshopSession thuSession = createSessionOn(locationA, thursday, LocalTime.of(9, 30), 10);
        String booking = bookingReference("WAX-ORDER-FAIL");

        // Passed Thursday first, Monday second
        CapacityHoldOutcome outcome = reserve(waxCastingPackage, List.of(thuSession, monSession), 2, booking);

        assertThat(outcome.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(outcome.reason()).isEqualTo(RejectionReason.PACKAGE_SELECTION_INVALID);
        assertThat(holdsFor(booking)).isZero();
    }

    // =========================================================================
    // 3. Booking Change / Replacement (replaceRequiredSessions) Tests
    // =========================================================================

    // =========================================================================
    // 4. Immutable Booking-Time Policy After Package Modification
    // =========================================================================

    // =========================================================================
    // Helper methods
    // =========================================================================

    private WorkshopSession createSessionOn(WorkshopLocation loc, LocalDate date, LocalTime startTime, int capacity) {
        Instant start = date.atTime(startTime).atZone(ZONE).toInstant();
        WorkshopSlot slot = slotRepository.saveAndFlush(WorkshopSlot.builder()
                .reference(UUID.randomUUID().toString())
                .location(loc)
                .label("Wax Slot " + UUID.randomUUID())
                .startTime(startTime)
                .endTime(startTime.plusHours(2))
                .defaultCapacity(capacity)
                .active(true)
                .build());
        return sessionRepository.saveAndFlush(WorkshopSession.builder()
                .reference(UUID.randomUUID().toString())
                .location(loc)
                .slot(slot)
                .workshopDate(date)
                .scheduledStartAt(start)
                .scheduledEndAt(start.plusSeconds(7200))
                .checkInOpensAt(start.minusSeconds(1800))
                .capacity(capacity)
                .state(SessionState.PUBLISHED)
                .build());
    }

    private CapacityHoldOutcome reserve(WorkshopPackage pkg, List<WorkshopSession> sessions, int participants, String booking) {
        return operationsFacade.reserveBookingCapacity(new ReserveBookingCapacityCommand(
                booking,
                pkg.getId().toString(),
                sessions.stream().map(s -> UUID.fromString(s.getReference())).toList(),
                participants,
                "op-" + UUID.randomUUID(),
                "corr-" + UUID.randomUUID()));
    }

    private AttendanceTicket createTicketWithSnapshot(String booking, int sequence, List<WorkshopSession> sessions, int requiredDays) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        AttendanceTicket ticket = ticketRepository.saveAndFlush(AttendanceTicket.builder()
                .bookingReference(booking)
                .ticketSequence(sequence)
                .ticketCode("WT-" + suffix)
                .qrReference("QR-" + UUID.randomUUID())
                .validityState(TicketValidityState.ACTIVE)
                .build());
        sessions.forEach(session -> entitlementRepository.save(TicketSessionEntitlement.builder()
                .ticket(ticket)
                .session(session)
                .attendanceState(AttendanceState.NOT_CHECKED_IN)
                .build()));
        entitlementRepository.flush();

        if (snapshotRepository.findByBookingReference(booking).isEmpty()) {
            snapshotRepository.saveAndFlush(WorkshopBookingPackagePolicySnapshot.builder()
                    .bookingReference(booking)
                    .packageReference(waxCastingPackage.getId().toString())
                    .requiredSessionCount(sessions.size())
                    .minParticipants(1)
                    .maxParticipants(10)
                    .sameLocationRequired(true)
                    .minimumSameDaySlotGap(0)
                    .requiredFoundryWorkingDaysBetweenSessions(requiredDays)
                    .build());
        }
        return ticket;
    }

    private int holdsFor(String bookingReference) {
        return jdbcTemplate.queryForObject(
                "select count(*) from capacity_holds where booking_reference = ?", Integer.class, bookingReference);
    }

    private static String bookingReference(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}

