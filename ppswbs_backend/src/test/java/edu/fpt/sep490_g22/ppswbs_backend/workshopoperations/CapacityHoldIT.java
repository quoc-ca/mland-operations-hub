package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.CapacityHoldService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopCapacityConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T028 Verification: Integration Tests for CapacityHoldService and Capacity Holds.
 * Verifies real MySQL/Flyway execution for:
 * 1. Sequential duplicate reserve, commit, release (Idempotency ledger);
 * 2. Concurrent reserve (Pessimistic locking, never oversell);
 * 3. Insufficient capacity all-or-nothing (Multi-session atomic reserve);
 * 4. Pause-after-hold then commit (SALES_PAUSED rejects new holds, permits pre-pause commit);
 * 5. Expiry (Expired hold rejected on commit, swept by expirePendingHolds);
 * 6. Duplicate release is idempotent (No double-release of capacity);
 * 7. Capacity-reduction protection (Rejects capacity reduction below committed seats).
 */
@SpringBootTest
@ActiveProfiles("test")
public class CapacityHoldIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WorkshopLocationRepository locationRepository;

    @Autowired
    private WorkshopSlotRepository slotRepository;

    @Autowired
    private WorkshopSessionRepository sessionRepository;

    @Autowired
    private CapacityHoldRepository capacityHoldRepository;

    @Autowired
    private CapacityHoldService capacityHoldService;

    @Autowired
    private WorkshopOperationsFacade operationsFacade;

    @MockBean
    private AccountFacade accountFacade;

    @MockBean
    private TokenVerificationService tokenVerificationService;

    private WorkshopLocation locationA;
    private WorkshopSlot slotMorning;
    private WorkshopSlot slotAfternoon;
    private WorkshopSession session1;
    private WorkshopSession session2;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbcTemplate.execute("TRUNCATE TABLE facade_command_idempotency_records");
        jdbcTemplate.execute("TRUNCATE TABLE ticket_session_entitlements");
        jdbcTemplate.execute("TRUNCATE TABLE attendance_tickets");
        jdbcTemplate.execute("TRUNCATE TABLE capacity_holds");
        jdbcTemplate.execute("TRUNCATE TABLE session_operational_leads");
        jdbcTemplate.execute("TRUNCATE TABLE session_staff_assignments");
        jdbcTemplate.execute("TRUNCATE TABLE operations_audit_events");
        jdbcTemplate.execute("TRUNCATE TABLE workshop_sessions");
        jdbcTemplate.execute("TRUNCATE TABLE workshop_slots");
        jdbcTemplate.execute("TRUNCATE TABLE workshop_locations");
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");

        locationA = locationRepository.save(WorkshopLocation.builder()
                .code("LOC-HCM-01")
                .displayName("District 1 Workshop Hub")
                .addressSnapshot("123 Le Loi, D1, HCMC")
                .active(true)
                .build());

        slotMorning = slotRepository.save(WorkshopSlot.builder()
                .reference(UUID.randomUUID().toString())
                .location(locationA)
                .label("Morning Slot")
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(12, 0))
                .defaultCapacity(10)
                .active(true)
                .build());

        slotAfternoon = slotRepository.save(WorkshopSlot.builder()
                .reference(UUID.randomUUID().toString())
                .location(locationA)
                .label("Afternoon Slot")
                .startTime(LocalTime.of(13, 0))
                .endTime(LocalTime.of(16, 0))
                .defaultCapacity(10)
                .active(true)
                .build());

        LocalDate sessionDate = LocalDate.now().plusDays(5);

        session1 = sessionRepository.save(WorkshopSession.builder()
                .reference(UUID.randomUUID().toString())
                .location(locationA)
                .slot(slotMorning)
                .workshopDate(sessionDate)
                .scheduledStartAt(Instant.now().plus(5, ChronoUnit.DAYS))
                .scheduledEndAt(Instant.now().plus(5, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS))
                .checkInOpensAt(Instant.now().plus(5, ChronoUnit.DAYS).minus(1, ChronoUnit.HOURS))
                .checkInOpeningSource(CheckInOpeningSource.DEFAULT)
                .capacity(10)
                .state(SessionState.PUBLISHED)
                .build());

        session2 = sessionRepository.save(WorkshopSession.builder()
                .reference(UUID.randomUUID().toString())
                .location(locationA)
                .slot(slotAfternoon)
                .workshopDate(sessionDate)
                .scheduledStartAt(Instant.now().plus(5, ChronoUnit.DAYS).plus(4, ChronoUnit.HOURS))
                .scheduledEndAt(Instant.now().plus(5, ChronoUnit.DAYS).plus(7, ChronoUnit.HOURS))
                .checkInOpensAt(Instant.now().plus(5, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS))
                .checkInOpeningSource(CheckInOpeningSource.DEFAULT)
                .capacity(10)
                .state(SessionState.PUBLISHED)
                .build());
    }

    // =========================================================================
    // 1. Sequential Duplicate (Idempotency Ledger)
    // =========================================================================

    @Test
    @DisplayName("T028-1: Sequential duplicate reserve, commit and release return original outcome or reject mismatch")
    void sequentialDuplicate_returnsOriginalOutcome_andRejectsMismatch() {
        String opRef = "op-seq-reserve-01";
        ReserveBookingCapacityCommand reserveCmd = new ReserveBookingCapacityCommand(
                "BKG-SEQ-001",
                List.of(UUID.fromString(session1.getReference())),
                2,
                opRef,
                "CORR-SEQ-01"
        );

        // First reserve call
        CapacityHoldOutcome outcome1 = operationsFacade.reserveBookingCapacity(reserveCmd);
        assertThat(outcome1.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        assertThat(outcome1.holdReferences()).hasSize(1);
        UUID holdRef = outcome1.holdReferences().getFirst();

        // Exact retry with identical command: returns cached original outcome
        CapacityHoldOutcome outcome2 = operationsFacade.reserveBookingCapacity(reserveCmd);
        assertThat(outcome2.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        assertThat(outcome2.holdReferences()).isEqualTo(outcome1.holdReferences());
        assertThat(outcome2.expiresAt()).isEqualTo(outcome1.expiresAt());

        // Reused opRef with different fingerprint (participantCount = 4)
        ReserveBookingCapacityCommand mismatchCmd = new ReserveBookingCapacityCommand(
                "BKG-SEQ-001",
                List.of(UUID.fromString(session1.getReference())),
                4,
                opRef,
                "CORR-SEQ-01"
        );
        CapacityHoldOutcome mismatchOutcome = operationsFacade.reserveBookingCapacity(mismatchCmd);
        assertThat(mismatchOutcome.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(mismatchOutcome.reason()).isEqualTo(RejectionReason.INVALID_STATE);

        // Commit hold
        String commitOpRef = "op-seq-commit-01";
        CommitBookingHoldsCommand commitCmd = new CommitBookingHoldsCommand(
                "BKG-SEQ-001",
                List.of(holdRef),
                commitOpRef,
                "CORR-SEQ-02"
        );
        CapacityCommitOutcome commit1 = operationsFacade.commitBookingHolds(commitCmd);
        assertThat(commit1.status()).isEqualTo(OutcomeStatus.ACCEPTED);

        // Retry commit with same opRef
        CapacityCommitOutcome commit2 = operationsFacade.commitBookingHolds(commitCmd);
        assertThat(commit2.status()).isEqualTo(OutcomeStatus.ACCEPTED);

        // Release holds
        String releaseOpRef = "op-seq-release-01";
        ReleaseBookingHoldsCommand releaseCmd = new ReleaseBookingHoldsCommand(
                "BKG-SEQ-001",
                List.of(holdRef),
                releaseOpRef,
                "CORR-SEQ-03"
        );
        CapacityReleaseOutcome release1 = operationsFacade.releaseBookingHolds(releaseCmd);
        assertThat(release1.status()).isEqualTo(OutcomeStatus.ACCEPTED);

        // Retry release with same opRef
        CapacityReleaseOutcome release2 = operationsFacade.releaseBookingHolds(releaseCmd);
        assertThat(release2.status()).isEqualTo(OutcomeStatus.ACCEPTED);
    }

    // =========================================================================
    // 2. Concurrent Reserve (Never Oversell)
    // =========================================================================

    @Test
    @DisplayName("T028-2: Concurrent reserve requests lock session and never oversell capacity")
    void concurrentReserve_neverOversellsCapacity() throws Exception {
        // Session capacity = 10
        // 5 concurrent threads each trying to reserve 3 seats (total 15 seats demanded)
        // Exactly 3 threads must succeed (3 x 3 = 9 <= 10)
        // Exactly 2 threads must be rejected with INSUFFICIENT_CAPACITY
        int threadCount = 5;
        int requestedSeats = 3;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        AtomicInteger acceptedCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    ReserveBookingCapacityCommand cmd = new ReserveBookingCapacityCommand(
                            "BKG-CONCURRENT-" + index,
                            List.of(UUID.fromString(session1.getReference())),
                            requestedSeats,
                            "op-concurrent-" + index,
                            "corr-concurrent-" + index
                    );
                    CapacityHoldOutcome outcome = operationsFacade.reserveBookingCapacity(cmd);
                    if (outcome.status() == OutcomeStatus.ACCEPTED) {
                        acceptedCount.incrementAndGet();
                    } else if (outcome.status() == OutcomeStatus.REJECTED && outcome.reason() == RejectionReason.INSUFFICIENT_CAPACITY) {
                        rejectedCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            }));
        }

        // Release all threads simultaneously
        startLatch.countDown();
        boolean completed = endLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(acceptedCount.get()).isEqualTo(3);
        assertThat(rejectedCount.get()).isEqualTo(2);

        // Total held seats in DB must be exactly 9 (3 x 3) and <= capacity 10
        int activeHolds = capacityHoldRepository.sumActiveHoldsForSession(session1.getId(), Instant.now());
        assertThat(activeHolds).isEqualTo(9);
        assertThat(activeHolds).isLessThanOrEqualTo(session1.getCapacity());
    }

    // =========================================================================
    // 3. Insufficient Capacity All-or-Nothing (Multi-Session Reserve)
    // =========================================================================

    @Test
    @DisplayName("T028-3: Multi-session reserve is all-or-nothing: shortage in one session creates no holds for any session")
    void insufficientCapacity_allOrNothing() {
        // Session 1 has capacity 10, remaining 10
        // Session 2: pre-consume capacity to leave only 1 seat
        CapacityHold existingHold = CapacityHold.builder()
                .reference(UUID.randomUUID().toString())
                .session(session2)
                .bookingReference("BKG-EXISTING-01")
                .requestedTicketCount(9)
                .state(CapacityHoldState.ACTIVE)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();
        capacityHoldRepository.save(existingHold);

        // Request 2 seats across [session1, session2]
        // Session 1 has 10 >= 2, but Session 2 has only 1 remaining < 2
        String bookingRef = "BKG-ALL-OR-NOTHING-01";
        ReserveBookingCapacityCommand cmd = new ReserveBookingCapacityCommand(
                bookingRef,
                List.of(UUID.fromString(session1.getReference()), UUID.fromString(session2.getReference())),
                2,
                "op-all-or-nothing-01",
                "corr-all-or-nothing-01"
        );

        CapacityHoldOutcome outcome = operationsFacade.reserveBookingCapacity(cmd);
        assertThat(outcome.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(outcome.reason()).isEqualTo(RejectionReason.INSUFFICIENT_CAPACITY);

        // All-or-nothing check: NO holds created in Session 1 or Session 2 for this booking
        List<CapacityHold> createdHolds = capacityHoldRepository.findByBookingReference(bookingRef);
        assertThat(createdHolds).isEmpty();

        int session1ActiveHolds = capacityHoldRepository.sumActiveHoldsForSession(session1.getId(), Instant.now());
        assertThat(session1ActiveHolds).isZero();
    }

    // =========================================================================
    // 4. Pause-after-hold then Commit
    // =========================================================================

    @Test
    @DisplayName("T028-4: SALES_PAUSED rejects new holds, but active pre-pause holds can still be committed")
    void pauseAfterHold_allowsCommit_butRejectsNewHold() {
        // 1. Session is PUBLISHED: reserve 2 seats
        String bookingRef = "BKG-PAUSE-TEST-01";
        ReserveBookingCapacityCommand reserveCmd = new ReserveBookingCapacityCommand(
                bookingRef,
                List.of(UUID.fromString(session1.getReference())),
                2,
                "op-pause-reserve-01",
                "corr-pause-01"
        );

        CapacityHoldOutcome reserveOutcome = operationsFacade.reserveBookingCapacity(reserveCmd);
        assertThat(reserveOutcome.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        UUID holdRef = reserveOutcome.holdReferences().getFirst();

        // 2. Transition session to SALES_PAUSED
        session1.setState(SessionState.SALES_PAUSED);
        sessionRepository.save(session1);

        // 3. New reservation on SALES_PAUSED session must be rejected with SALE_NOT_ELIGIBLE
        ReserveBookingCapacityCommand rejectedCmd = new ReserveBookingCapacityCommand(
                "BKG-PAUSE-NEW-01",
                List.of(UUID.fromString(session1.getReference())),
                1,
                "op-pause-new-reserve-01",
                "corr-pause-02"
        );
        CapacityHoldOutcome newReserveOutcome = operationsFacade.reserveBookingCapacity(rejectedCmd);
        assertThat(newReserveOutcome.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(newReserveOutcome.reason()).isEqualTo(RejectionReason.SALE_NOT_ELIGIBLE);

        // 4. Pre-pause hold can still be committed
        CommitBookingHoldsCommand commitCmd = new CommitBookingHoldsCommand(
                bookingRef,
                List.of(holdRef),
                "op-pause-commit-01",
                "corr-pause-03"
        );
        CapacityCommitOutcome commitOutcome = operationsFacade.commitBookingHolds(commitCmd);
        assertThat(commitOutcome.status()).isEqualTo(OutcomeStatus.ACCEPTED);

        // Hold state transitioned to CONFIRMED
        CapacityHold committedHold = capacityHoldRepository.findByReference(holdRef.toString()).orElseThrow();
        assertThat(committedHold.getState()).isEqualTo(CapacityHoldState.CONFIRMED);
    }

    // =========================================================================
    // 5. Expiry Sweeping and Commit Rejection
    // =========================================================================

    @Test
    @DisplayName("T028-5: Expired hold is rejected on commit and swept by expirePendingHolds")
    void expiredHold_rejectedOnCommit_andSweptByExpirePendingHolds() {
        // Create an expired active hold directly in DB
        String holdRef = UUID.randomUUID().toString();
        CapacityHold expiredHold = CapacityHold.builder()
                .reference(holdRef)
                .session(session1)
                .bookingReference("BKG-EXPIRED-01")
                .requestedTicketCount(3)
                .state(CapacityHoldState.ACTIVE)
                .expiresAt(Instant.now().minus(5, ChronoUnit.MINUTES)) // Already expired 5 mins ago
                .build();
        capacityHoldRepository.save(expiredHold);

        // Attempting to commit expired hold -> REJECTED with HOLD_MISSING_OR_EXPIRED
        CommitBookingHoldsCommand commitCmd = new CommitBookingHoldsCommand(
                "BKG-EXPIRED-01",
                List.of(UUID.fromString(holdRef)),
                "op-expired-commit-01",
                "corr-expired-01"
        );
        CapacityCommitOutcome commitOutcome = operationsFacade.commitBookingHolds(commitCmd);
        assertThat(commitOutcome.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(commitOutcome.reason()).isEqualTo(RejectionReason.HOLD_MISSING_OR_EXPIRED);

        // Run expiry sweep
        int sweptCount = capacityHoldService.expirePendingHolds();
        assertThat(sweptCount).isGreaterThanOrEqualTo(1);

        // Hold state is now EXPIRED
        CapacityHold sweptHold = capacityHoldRepository.findByReference(holdRef).orElseThrow();
        assertThat(sweptHold.getState()).isEqualTo(CapacityHoldState.EXPIRED);

        // Expired hold no longer consumes session capacity
        int activeHolds = capacityHoldRepository.sumActiveHoldsForSession(session1.getId(), Instant.now());
        assertThat(activeHolds).isZero();
    }

    // =========================================================================
    // 6. Duplicate Release is Idempotent
    // =========================================================================

    @Test
    @DisplayName("T028-6: Release of active hold succeeds; duplicate or late release is idempotent")
    void duplicateRelease_isIdempotent() {
        String bookingRef = "BKG-RELEASE-01";
        ReserveBookingCapacityCommand reserveCmd = new ReserveBookingCapacityCommand(
                bookingRef,
                List.of(UUID.fromString(session1.getReference())),
                4,
                "op-release-reserve-01",
                "corr-rel-01"
        );

        CapacityHoldOutcome reserveOutcome = operationsFacade.reserveBookingCapacity(reserveCmd);
        assertThat(reserveOutcome.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        UUID holdRef = reserveOutcome.holdReferences().getFirst();

        // Active holds before release = 4
        assertThat(capacityHoldRepository.sumActiveHoldsForSession(session1.getId(), Instant.now())).isEqualTo(4);

        // Release hold
        ReleaseBookingHoldsCommand releaseCmd = new ReleaseBookingHoldsCommand(
                bookingRef,
                List.of(holdRef),
                "op-rel-cmd-01",
                "corr-rel-02"
        );
        CapacityReleaseOutcome releaseOutcome1 = operationsFacade.releaseBookingHolds(releaseCmd);
        assertThat(releaseOutcome1.status()).isEqualTo(OutcomeStatus.ACCEPTED);

        // Active holds after release = 0
        assertThat(capacityHoldRepository.sumActiveHoldsForSession(session1.getId(), Instant.now())).isZero();

        // Repeated release command (with distinct opRef, representing late/repeated call)
        ReleaseBookingHoldsCommand releaseCmd2 = new ReleaseBookingHoldsCommand(
                bookingRef,
                List.of(holdRef),
                "op-rel-cmd-02",
                "corr-rel-03"
        );
        CapacityReleaseOutcome releaseOutcome2 = operationsFacade.releaseBookingHolds(releaseCmd2);
        assertThat(releaseOutcome2.status()).isEqualTo(OutcomeStatus.ACCEPTED);

        // Capacity remains 0 (does not decrement below 0 or double release)
        assertThat(capacityHoldRepository.sumActiveHoldsForSession(session1.getId(), Instant.now())).isZero();
        CapacityHold finalHold = capacityHoldRepository.findByReference(holdRef.toString()).orElseThrow();
        assertThat(finalHold.getState()).isEqualTo(CapacityHoldState.RELEASED);
    }

    // =========================================================================
    // 7. Capacity-Reduction Protection
    // =========================================================================

    @Test
    @DisplayName("T028-7: Capacity reduction below active holds + confirmed seats is strictly rejected")
    void capacityReductionProtection_rejectsReductionBelowCommittedSeats() {
        // Session capacity is 10
        // Create 3 active holds + 2 confirmed holds = 5 committed seats
        CapacityHold activeHold = CapacityHold.builder()
                .reference(UUID.randomUUID().toString())
                .session(session1)
                .bookingReference("BKG-RED-01")
                .requestedTicketCount(3)
                .state(CapacityHoldState.ACTIVE)
                .expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
                .build();
        capacityHoldRepository.save(activeHold);

        CapacityHold confirmedHold = CapacityHold.builder()
                .reference(UUID.randomUUID().toString())
                .session(session1)
                .bookingReference("BKG-RED-02")
                .requestedTicketCount(2)
                .state(CapacityHoldState.CONFIRMED)
                .expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
                .build();
        capacityHoldRepository.save(confirmedHold);

        // Committed seats = 3 + 2 = 5
        // Reducing capacity to 4 (< 5) must be rejected
        assertThatThrownBy(() -> capacityHoldService.validateCapacityReduction(session1.getId(), 4))
                .isInstanceOf(WorkshopCapacityConflictException.class)
                .hasMessageContaining("Cannot reduce capacity to 4")
                .hasMessageContaining("committed seats");

        // Reducing to exactly 5 (= 5) is allowed
        capacityHoldService.validateCapacityReduction(session1.getId(), 5);

        // Reducing to 7 (> 5) is allowed
        capacityHoldService.validateCapacityReduction(session1.getId(), 7);
    }

    // =========================================================================
    // 8. Strict No Oversell Boundary
    // =========================================================================

    @Test
    @DisplayName("T028-8: Sequential reserve rejects exceeding exact remaining capacity")
    void sequentialReserve_exactBoundaryCheck() {
        // Session capacity is 10
        // Reserve 6 seats -> remaining 4
        ReserveBookingCapacityCommand cmd1 = new ReserveBookingCapacityCommand(
                "BKG-OVER-01",
                List.of(UUID.fromString(session1.getReference())),
                6,
                "op-over-01",
                "corr-over-01"
        );
        CapacityHoldOutcome res1 = operationsFacade.reserveBookingCapacity(cmd1);
        assertThat(res1.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        assertThat(capacityHoldService.getRemainingCapacity(session1.getId())).isEqualTo(4);

        // Reserve 4 seats -> remaining 0
        ReserveBookingCapacityCommand cmd2 = new ReserveBookingCapacityCommand(
                "BKG-OVER-02",
                List.of(UUID.fromString(session1.getReference())),
                4,
                "op-over-02",
                "corr-over-02"
        );
        CapacityHoldOutcome res2 = operationsFacade.reserveBookingCapacity(cmd2);
        assertThat(res2.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        assertThat(capacityHoldService.getRemainingCapacity(session1.getId())).isZero();

        // Reserve 1 seat when remaining is 0 -> REJECTED with INSUFFICIENT_CAPACITY
        ReserveBookingCapacityCommand cmd3 = new ReserveBookingCapacityCommand(
                "BKG-OVER-03",
                List.of(UUID.fromString(session1.getReference())),
                1,
                "op-over-03",
                "corr-over-03"
        );
        CapacityHoldOutcome res3 = operationsFacade.reserveBookingCapacity(cmd3);
        assertThat(res3.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(res3.reason()).isEqualTo(RejectionReason.INSUFFICIENT_CAPACITY);
    }
}
