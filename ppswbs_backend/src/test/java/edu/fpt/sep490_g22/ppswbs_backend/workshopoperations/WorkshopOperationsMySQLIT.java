package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.FacadeCommandIdempotencyCleanupService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.FacadeCommandIdempotencyManager;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.FacadeCommandIdempotencyManager.ClaimResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.FacadeCommandIdempotencyManager.ClaimStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.StaleClaimantException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Integration verification test targeting real MySQL database and Flyway migration V7.
 * Covers all mandatory scenarios for T066:
 * 1. Flyway V7 applied + ledger table structure & unique constraints
 * 2. Sequential equivalent duplicate (original outcome returned, single ledger record, single audit event)
 * 3. Concurrent duplicate with active lease returns IN_PROGRESS without side effects
 * 4. Semantic fingerprint mismatch returns safe rejection (INVALID_STATE)
 * 5. Expired-lease reclaim executes business effect and increments claimVersion
 * 6. Stale-claimant rollback via StaleClaimantException fencing
 * 7. Completed-record retention (>=24h) and cleanup (never deletes IN_PROGRESS)
 * 8. Non-disclosure of tokens, QR codes, or customer PII
 */
@SpringBootTest
@ActiveProfiles("test")
class WorkshopOperationsMySQLIT {

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private AccountFacade accountFacade;

    @Autowired
    private WorkshopOperationsFacade operationsFacade;

    @SpyBean
    private FacadeCommandIdempotencyManager idempotencyManager;

    @Autowired
    private FacadeCommandIdempotencyCleanupService cleanupService;

    @Autowired
    private FacadeCommandIdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private OperationsAuditEventRepository auditEventRepository;

    @Autowired
    private WorkshopBranchRepository branchRepository;

    @Autowired
    private WorkshopSlotRepository slotRepository;

    @Autowired
    private WorkshopSessionRepository sessionRepository;

    @Autowired
    private CapacityHoldRepository holdRepository;

    @Autowired
    private AttendanceTicketRepository ticketRepository;

    @Autowired
    private TicketSessionEntitlementRepository entitlementRepository;

    @Autowired
    private AttendanceActionRepository actionRepository;

    @Autowired
    private CancellationHandoffRepository handoffRepository;

    @Autowired
    private SessionStaffAssignmentRepository staffAssignmentRepository;

    @Autowired
    private SessionOperationalLeadRepository operationalLeadRepository;

    private WorkshopBranch testBranch;
    private WorkshopSlot testSlot;
    private WorkshopSession testSession;

    @BeforeEach
    void setUp() {
        assertThat(jdbcTemplate).as("Real MySQL JdbcTemplate must be available").isNotNull();

        if (idempotencyManager != null) {
            org.mockito.Mockito.reset(idempotencyManager);
        }

        idempotencyRecordRepository.deleteAll();
        actionRepository.deleteAll();
        entitlementRepository.deleteAll();
        ticketRepository.deleteAll();
        holdRepository.deleteAll();
        operationalLeadRepository.deleteAll();
        staffAssignmentRepository.deleteAll();
        handoffRepository.deleteAll();
        auditEventRepository.deleteAll();
        sessionRepository.deleteAll();
        slotRepository.deleteAll();
        branchRepository.deleteAll();

        testBranch = branchRepository.save(WorkshopBranch.builder()
                .reference(UUID.randomUUID().toString())
                .code("BR-MYSQL-01")
                .displayName("MySQL Verification Branch")
                .addressSnapshot("456 Nguyen Hue, District 1, HCMC")
                .active(true)
                .build());

        testSlot = slotRepository.save(WorkshopSlot.builder()
                .reference(UUID.randomUUID().toString())
                .branch(testBranch)
                .label("Afternoon Verification Slot")
                .startTime(LocalTime.of(14, 0))
                .endTime(LocalTime.of(17, 0))
                .defaultCapacity(30)
                .active(true)
                .build());

        testSession = sessionRepository.save(WorkshopSession.builder()
                .reference(UUID.randomUUID().toString())
                .branch(testBranch)
                .slot(testSlot)
                .workshopDate(LocalDate.now().plusDays(7))
                .scheduledStartAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .scheduledEndAt(Instant.now().plus(7, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS))
                .capacity(30)
                .state(SessionState.PUBLISHED)
                .checkInOpensAt(Instant.now().plus(6, ChronoUnit.DAYS))
                .checkInOpeningSource(CheckInOpeningSource.DEFAULT)
                .build());
    }

    @Test
    @DisplayName("T066-1: Verify Flyway V7 idempotency ledger migration applied on MySQL")
    void verifyV7MigrationAppliedOnMySQL() {
        Integer v7Count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '7' AND success = 1",
                Integer.class
        );
        assertThat(v7Count).as("Flyway migration V7 must be applied successfully").isNotNull().isGreaterThanOrEqualTo(1);

        Integer ledgerTableExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'facade_command_idempotency_records'",
                Integer.class
        );
        assertThat(ledgerTableExists).as("Table facade_command_idempotency_records must exist").isNotNull().isEqualTo(1);

        // Verify UNIQUE constraint on (facade_action, operation_reference)
        Integer uniqueIndexCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE() " +
                        "AND table_name = 'facade_command_idempotency_records' AND constraint_type = 'UNIQUE'",
                Integer.class
        );
        assertThat(uniqueIndexCount).as("Unique constraint on facade action and operation reference must exist").isNotNull().isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("T066-2: Sequential equivalent duplicate returns original outcome, single ledger record, single audit event")
    void shouldHandleSequentialDuplicate() {
        String opRef = "OP-SEQ-" + UUID.randomUUID();
        String corrRef = "CORR-SEQ-001";
        UUID sessionUuid = UUID.fromString(testSession.getReference());

        ReserveBookingCapacityCommand cmd = new ReserveBookingCapacityCommand(
                "BKG-SEQ-01",
                List.of(sessionUuid),
                2,
                opRef,
                corrRef
        );

        // First call -> creates holds, audit event, completed ledger
        CapacityHoldOutcome firstOutcome = operationsFacade.reserveBookingCapacity(cmd);
        assertThat(firstOutcome.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        assertThat(firstOutcome.holdReferences()).hasSize(1);
        assertThat(firstOutcome.expiresAt()).isNotNull();

        // Second call with identical opRef and semantic payload
        CapacityHoldOutcome secondOutcome = operationsFacade.reserveBookingCapacity(cmd);
        assertThat(secondOutcome.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        assertThat(secondOutcome.holdReferences()).isEqualTo(firstOutcome.holdReferences());
        assertThat(secondOutcome.expiresAt()).isEqualTo(firstOutcome.expiresAt());

        // Assert exactly 1 ledger record in COMPLETED state
        List<FacadeCommandIdempotencyRecord> records = idempotencyRecordRepository.findAll();
        assertThat(records).hasSize(1);
        FacadeCommandIdempotencyRecord ledger = records.getFirst();
        assertThat(ledger.getFacadeAction()).isEqualTo("FACADE_RESERVE_CAPACITY");
        assertThat(ledger.getOperationReference()).isEqualTo(opRef);
        assertThat(ledger.getCorrelationReference()).isEqualTo(corrRef);
        assertThat(ledger.getLifecycleStatus()).isEqualTo(IdempotencyLifecycleStatus.COMPLETED);
        assertThat(ledger.getOutcomeStatus()).isEqualTo(OutcomeStatus.ACCEPTED);

        // Assert exactly 1 business audit event, with correlation_id = corrRef (NOT opRef)
        List<OperationsAuditEvent> auditEvents = auditEventRepository.findAll();
        assertThat(auditEvents).hasSize(1);
        OperationsAuditEvent audit = auditEvents.getFirst();
        assertThat(audit.getCorrelationId()).isEqualTo(corrRef);
        assertThat(audit.getAction()).isEqualTo("FACADE_RESERVE_CAPACITY");
    }

    @Test
    @DisplayName("T066-3: Real concurrent duplicate with barrier returns IN_PROGRESS on second request, prevents duplicate side effects, and reuses outcome on completed retry")
    void shouldReturnInProgressOnConcurrentDuplicate() throws Exception {
        String opRef = "OP-CONC-" + UUID.randomUUID();
        String corrRef = "CORR-CONC-001";
        UUID sessionUuid = UUID.fromString(testSession.getReference());

        ReserveBookingCapacityCommand cmd = new ReserveBookingCapacityCommand(
                "BKG-CONC-01",
                List.of(sessionUuid),
                3,
                opRef,
                corrRef
        );

        CountDownLatch thread1ClaimAcquired = new CountDownLatch(1);
        CountDownLatch thread2Completed = new CountDownLatch(1);

        doAnswer(invocation -> {
            thread1ClaimAcquired.countDown();
            boolean awaited = thread2Completed.await(5, TimeUnit.SECONDS);
            assertThat(awaited).as("Thread 2 must complete while Thread 1 holds active in-progress claim").isTrue();
            return invocation.callRealMethod();
        }).when(idempotencyManager).completeClaim(any(), any(), any(), any(), any());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // Execution 1: Thread 1 starts command, acquires claim in MySQL, then pauses at barrier
            Future<CapacityHoldOutcome> future1 = executor.submit(() -> operationsFacade.reserveBookingCapacity(cmd));

            // Wait until Thread 1 has acquired claim in MySQL
            boolean thread1Ready = thread1ClaimAcquired.await(5, TimeUnit.SECONDS);
            assertThat(thread1Ready).as("Thread 1 must acquire claim before Thread 2 proceeds").isTrue();

            // Verify claim in MySQL is indeed IN_PROGRESS before Thread 2 runs
            FacadeCommandIdempotencyRecord inProgressRecord = idempotencyRecordRepository
                    .findByFacadeActionAndOperationReference("FACADE_RESERVE_CAPACITY", opRef)
                    .orElseThrow();
            assertThat(inProgressRecord.getLifecycleStatus()).isEqualTo(IdempotencyLifecycleStatus.IN_PROGRESS);

            // Execution 2: Thread 2 starts concurrent duplicate command while Thread 1's claim is live
            Future<CapacityHoldOutcome> future2 = executor.submit(() -> {
                try {
                    return operationsFacade.reserveBookingCapacity(cmd);
                } finally {
                    thread2Completed.countDown();
                }
            });

            CapacityHoldOutcome outcome2 = future2.get(5, TimeUnit.SECONDS);
            CapacityHoldOutcome outcome1 = future1.get(5, TimeUnit.SECONDS);

            // Invariant 1: Request 2 receives typed IN_PROGRESS without side effects
            assertThat(outcome2.status()).isEqualTo(OutcomeStatus.IN_PROGRESS);
            assertThat(outcome2.holdReferences()).isEmpty();

            // Invariant 2: Request 1 completes successfully
            assertThat(outcome1.status()).isEqualTo(OutcomeStatus.ACCEPTED);
            assertThat(outcome1.holdReferences()).hasSize(1);

            // Invariant 3: Exactly ONE business side effect (1 capacity hold) and ONE business audit event
            List<CapacityHold> holds = holdRepository.findAll();
            assertThat(holds).hasSize(1);
            assertThat(holds.getFirst().getBookingReference()).isEqualTo("BKG-CONC-01");

            List<OperationsAuditEvent> auditEvents = auditEventRepository.findAll();
            assertThat(auditEvents).hasSize(1);
            assertThat(auditEvents.getFirst().getCorrelationId()).isEqualTo(corrRef);

            // Invariant 4: After execution 1 completed, sequential retry with same reference returns original outcome
            CapacityHoldOutcome retryOutcome = operationsFacade.reserveBookingCapacity(cmd);
            assertThat(retryOutcome.status()).isEqualTo(OutcomeStatus.ACCEPTED);
            assertThat(retryOutcome.holdReferences()).isEqualTo(outcome1.holdReferences());
            assertThat(retryOutcome.expiresAt()).isEqualTo(outcome1.expiresAt());

            // Still exactly 1 hold and 1 audit event in MySQL
            assertThat(holdRepository.findAll()).hasSize(1);
            assertThat(auditEventRepository.findAll()).hasSize(1);

            // Ledger in MySQL is COMPLETED with claimVersion = 1
            FacadeCommandIdempotencyRecord completedRecord = idempotencyRecordRepository
                    .findByFacadeActionAndOperationReference("FACADE_RESERVE_CAPACITY", opRef)
                    .orElseThrow();
            assertThat(completedRecord.getLifecycleStatus()).isEqualTo(IdempotencyLifecycleStatus.COMPLETED);
            assertThat(completedRecord.getClaimVersion()).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("T066-3b: Real concurrent initial claim race when no ledger record exists: loser evaluates cleanly, receives IN_PROGRESS, single side effect, single audit event")
    void shouldHandleConcurrentInitialClaimRace() throws Exception {
        String opRef = "OP-INIT-RACE-" + UUID.randomUUID();
        String corrRef = "CORR-INIT-RACE-001";
        UUID sessionUuid = UUID.fromString(testSession.getReference());

        ReserveBookingCapacityCommand cmd = new ReserveBookingCapacityCommand(
                "BKG-INIT-RACE-01",
                List.of(sessionUuid),
                2,
                opRef,
                corrRef
        );

        // Precondition: prove no ledger record exists initially
        assertThat(idempotencyRecordRepository.findByFacadeActionAndOperationReference("FACADE_RESERVE_CAPACITY", opRef))
                .isEmpty();

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch loserFinished = new CountDownLatch(1);
        AtomicBoolean winnerPaused = new AtomicBoolean(false);

        // Winner pauses in completeClaim so that the claim remains IN_PROGRESS in MySQL while loser evaluates
        doAnswer(invocation -> {
            if (winnerPaused.compareAndSet(false, true)) {
                boolean awaited = loserFinished.await(5, TimeUnit.SECONDS);
                assertThat(awaited).as("Loser must evaluate and complete while winner holds active in-progress claim").isTrue();
            }
            return invocation.callRealMethod();
        }).when(idempotencyManager).completeClaim(any(), any(), any(), any(), any());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<CapacityHoldOutcome> task = () -> {
                boolean started = startGate.await(5, TimeUnit.SECONDS);
                assertThat(started).as("Start gate must open").isTrue();
                try {
                    CapacityHoldOutcome outcome = operationsFacade.reserveBookingCapacity(cmd);
                    if (outcome.status() == OutcomeStatus.IN_PROGRESS) {
                        loserFinished.countDown();
                    }
                    return outcome;
                } catch (Exception e) {
                    loserFinished.countDown();
                    throw e;
                }
            };

            Future<CapacityHoldOutcome> future1 = executor.submit(task);
            Future<CapacityHoldOutcome> future2 = executor.submit(task);

            // Open the gate so both threads race simultaneously on initial insert
            startGate.countDown();

            CapacityHoldOutcome outcome1 = future1.get(5, TimeUnit.SECONDS);
            CapacityHoldOutcome outcome2 = future2.get(5, TimeUnit.SECONDS);

            // Neither request failed with technical error; one is ACCEPTED and one is IN_PROGRESS
            List<OutcomeStatus> statuses = List.of(outcome1.status(), outcome2.status());
            assertThat(statuses).containsExactlyInAnyOrder(OutcomeStatus.ACCEPTED, OutcomeStatus.IN_PROGRESS);

            CapacityHoldOutcome acceptedOutcome = outcome1.status() == OutcomeStatus.ACCEPTED ? outcome1 : outcome2;
            CapacityHoldOutcome inProgressOutcome = outcome1.status() == OutcomeStatus.IN_PROGRESS ? outcome1 : outcome2;

            assertThat(acceptedOutcome.holdReferences()).hasSize(1);
            assertThat(inProgressOutcome.holdReferences()).isEmpty();

            // Invariant: Exactly ONE business side effect (1 capacity hold) and ONE business audit event
            List<CapacityHold> holds = holdRepository.findAll();
            assertThat(holds).hasSize(1);
            assertThat(holds.getFirst().getBookingReference()).isEqualTo("BKG-INIT-RACE-01");

            List<OperationsAuditEvent> auditEvents = auditEventRepository.findAll();
            assertThat(auditEvents).hasSize(1);
            assertThat(auditEvents.getFirst().getCorrelationId()).isEqualTo(corrRef);

            // Sequential retry returns original outcome
            CapacityHoldOutcome retryOutcome = operationsFacade.reserveBookingCapacity(cmd);
            assertThat(retryOutcome.status()).isEqualTo(OutcomeStatus.ACCEPTED);
            assertThat(retryOutcome.holdReferences()).isEqualTo(acceptedOutcome.holdReferences());
            assertThat(retryOutcome.expiresAt()).isEqualTo(acceptedOutcome.expiresAt());

            // Still exactly 1 hold and 1 audit event
            assertThat(holdRepository.findAll()).hasSize(1);
            assertThat(auditEventRepository.findAll()).hasSize(1);

            // Ledger in MySQL is COMPLETED with claimVersion = 1
            FacadeCommandIdempotencyRecord completedRecord = idempotencyRecordRepository
                    .findByFacadeActionAndOperationReference("FACADE_RESERVE_CAPACITY", opRef)
                    .orElseThrow();
            assertThat(completedRecord.getLifecycleStatus()).isEqualTo(IdempotencyLifecycleStatus.COMPLETED);
            assertThat(completedRecord.getClaimVersion()).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("T066-4: Semantic fingerprint mismatch rejects safely with INVALID_STATE")
    void shouldRejectSafeOnSemanticFingerprintMismatch() {
        String opRef = "OP-MISMATCH-" + UUID.randomUUID();
        UUID sessionUuid = UUID.fromString(testSession.getReference());

        ReserveBookingCapacityCommand initialCmd = new ReserveBookingCapacityCommand(
                "BKG-ORIGINAL-01",
                List.of(sessionUuid),
                2,
                opRef,
                "CORR-ORIG-001"
        );
        CapacityHoldOutcome initialOutcome = operationsFacade.reserveBookingCapacity(initialCmd);
        assertThat(initialOutcome.status()).isEqualTo(OutcomeStatus.ACCEPTED);

        // Mismatched command reusing the same opRef but different participantCount (different fingerprint)
        ReserveBookingCapacityCommand mismatchedCmd = new ReserveBookingCapacityCommand(
                "BKG-ORIGINAL-01",
                List.of(sessionUuid),
                5, // changed from 2 to 5
                opRef,
                "CORR-MISMATCH-002"
        );
        CapacityHoldOutcome mismatchOutcome = operationsFacade.reserveBookingCapacity(mismatchedCmd);
        assertThat(mismatchOutcome.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(mismatchOutcome.reason()).isEqualTo(RejectionReason.INVALID_STATE);

        // Verify no second audit event or additional hold created
        assertThat(auditEventRepository.findAll()).hasSize(1);
        assertThat(holdRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("T066-5: Expired-lease claim is reclaimed, increments claimVersion, and executes business effect")
    void shouldReclaimExpiredLeaseAndExecute() {
        String opRef = "OP-EXPIRED-" + UUID.randomUUID();
        String corrRef = "CORR-EXP-001";
        UUID sessionUuid = UUID.fromString(testSession.getReference());

        ReserveBookingCapacityCommand cmd = new ReserveBookingCapacityCommand(
                "BKG-RECLAIM-01",
                List.of(sessionUuid),
                2,
                opRef,
                corrRef
        );
        String fingerprint = cmd.bookingReference() + "|" + cmd.orderedRequiredSessionReferences() + "|" + cmd.participantCount();

        // Insert an abandoned IN_PROGRESS record with an expired lease (e.g., crashed claimant 10 minutes ago)
        FacadeCommandIdempotencyRecord abandoned = FacadeCommandIdempotencyRecord.builder()
                .facadeAction("FACADE_RESERVE_CAPACITY")
                .operationReference(opRef)
                .correlationReference(corrRef)
                .semanticFingerprint(fingerprint)
                .lifecycleStatus(IdempotencyLifecycleStatus.IN_PROGRESS)
                .claimVersion(1L)
                .leaseExpiresAt(Instant.now().minus(10, ChronoUnit.MINUTES))
                .claimedAt(Instant.now().minus(11, ChronoUnit.MINUTES))
                .build();
        idempotencyRecordRepository.save(abandoned);

        // Caller retries: acquireClaim must reclaim expired lease and increment claimVersion to 2
        CapacityHoldOutcome outcome = operationsFacade.reserveBookingCapacity(cmd);
        assertThat(outcome.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        assertThat(outcome.holdReferences()).hasSize(1);

        // Verify ledger record is now COMPLETED with claimVersion = 2
        FacadeCommandIdempotencyRecord updated = idempotencyRecordRepository.findByFacadeActionAndOperationReference(
                "FACADE_RESERVE_CAPACITY", opRef
        ).orElseThrow();
        assertThat(updated.getLifecycleStatus()).isEqualTo(IdempotencyLifecycleStatus.COMPLETED);
        assertThat(updated.getClaimVersion()).isEqualTo(2L);
        assertThat(updated.getOutcomeStatus()).isEqualTo(OutcomeStatus.ACCEPTED);

        // Verify business effect and audit event were executed
        assertThat(holdRepository.findAll()).hasSize(1);
        assertThat(auditEventRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("T066-6: Stale claimant cannot complete claim after reclaim; triggers rollback via StaleClaimantException")
    void shouldRollbackWhenStaleClaimantAttemptsToCommit() {
        String opRef = "OP-FENCE-" + UUID.randomUUID();
        String corrRef = "CORR-FENCE-001";
        String fingerprint = "FP-FENCE-TEST";

        // Claimant 1 acquires claim version 1
        ClaimResult claim1 = idempotencyManager.acquireClaim(
                "FACADE_RESERVE_CAPACITY", opRef, corrRef, fingerprint
        );
        assertThat(claim1.getStatus()).isEqualTo(ClaimStatus.ACQUIRED);
        assertThat(claim1.getClaimVersion()).isEqualTo(1L);

        // Simulate Claimant 2 reclaiming the lease by bumping claim_version to 2 directly in DB
        FacadeCommandIdempotencyRecord rec = idempotencyRecordRepository.findById(claim1.getRecordId()).orElseThrow();
        rec.setClaimVersion(2L);
        idempotencyRecordRepository.saveAndFlush(rec);

        // Verify DB now has claim_version = 2
        FacadeCommandIdempotencyRecord current = idempotencyRecordRepository.findById(claim1.getRecordId()).orElseThrow();
        assertThat(current.getClaimVersion()).isEqualTo(2L);

        // Claimant 1 (stale) now attempts to complete with its outdated version (1L)
        assertThatThrownBy(() ->
                idempotencyManager.completeClaim(
                        claim1.getRecordId(),
                        1L, // stale version!
                        OutcomeStatus.ACCEPTED,
                        null,
                        "{\"stale\": true}"
                )
        ).isInstanceOf(StaleClaimantException.class)
                .hasMessageContaining("Stale claimant");

        // Verify the record was NOT updated by Claimant 1
        FacadeCommandIdempotencyRecord untouched = idempotencyRecordRepository.findById(claim1.getRecordId()).orElseThrow();
        assertThat(untouched.getLifecycleStatus()).isEqualTo(IdempotencyLifecycleStatus.IN_PROGRESS);
        assertThat(untouched.getClaimVersion()).isEqualTo(2L);
    }

    @Test
    @DisplayName("T066-7: Retention and cleanup deletes completed records > 24h, retains < 24h, never deletes IN_PROGRESS")
    void shouldRetainCompletedRecordsAndCleanOnlyEligible() {
        // Record 1: Completed 25 hours ago -> ELIGIBLE FOR CLEANUP
        FacadeCommandIdempotencyRecord oldCompleted = FacadeCommandIdempotencyRecord.builder()
                .facadeAction("FACADE_ACTION_OLD")
                .operationReference("OP-OLD-" + UUID.randomUUID())
                .correlationReference("CORR-OLD")
                .semanticFingerprint("FP-OLD")
                .lifecycleStatus(IdempotencyLifecycleStatus.COMPLETED)
                .claimVersion(1L)
                .claimedAt(Instant.now().minus(26, ChronoUnit.HOURS))
                .completedAt(Instant.now().minus(25, ChronoUnit.HOURS))
                .leaseExpiresAt(Instant.now().minus(25, ChronoUnit.HOURS))
                .outcomeStatus(OutcomeStatus.ACCEPTED)
                .responsePayload("{}")
                .build();
        idempotencyRecordRepository.save(oldCompleted);

        // Record 2: Completed 2 hours ago -> RETAINED (retention >= 24h)
        FacadeCommandIdempotencyRecord recentCompleted = FacadeCommandIdempotencyRecord.builder()
                .facadeAction("FACADE_ACTION_RECENT")
                .operationReference("OP-RECENT-" + UUID.randomUUID())
                .correlationReference("CORR-RECENT")
                .semanticFingerprint("FP-RECENT")
                .lifecycleStatus(IdempotencyLifecycleStatus.COMPLETED)
                .claimVersion(1L)
                .claimedAt(Instant.now().minus(3, ChronoUnit.HOURS))
                .completedAt(Instant.now().minus(2, ChronoUnit.HOURS))
                .leaseExpiresAt(Instant.now().minus(2, ChronoUnit.HOURS))
                .outcomeStatus(OutcomeStatus.ACCEPTED)
                .responsePayload("{}")
                .build();
        idempotencyRecordRepository.save(recentCompleted);

        // Record 3: IN_PROGRESS created 26 hours ago (abandoned or long running) -> NEVER CLEANED
        FacadeCommandIdempotencyRecord oldInProgress = FacadeCommandIdempotencyRecord.builder()
                .facadeAction("FACADE_ACTION_IN_PROGRESS")
                .operationReference("OP-INPROG-" + UUID.randomUUID())
                .correlationReference("CORR-INPROG")
                .semanticFingerprint("FP-INPROG")
                .lifecycleStatus(IdempotencyLifecycleStatus.IN_PROGRESS)
                .claimVersion(1L)
                .claimedAt(Instant.now().minus(26, ChronoUnit.HOURS))
                .leaseExpiresAt(Instant.now().minus(25, ChronoUnit.HOURS))
                .build();
        idempotencyRecordRepository.save(oldInProgress);

        // Execute cleanup
        int deletedCount = cleanupService.cleanupExpiredCompletedRecords();
        assertThat(deletedCount).isEqualTo(1);

        // Verify remaining records
        List<FacadeCommandIdempotencyRecord> remaining = idempotencyRecordRepository.findAll();
        assertThat(remaining).hasSize(2);
        assertThat(remaining).extracting(FacadeCommandIdempotencyRecord::getOperationReference)
                .containsExactlyInAnyOrder(recentCompleted.getOperationReference(), oldInProgress.getOperationReference());
    }

    @Test
    @DisplayName("T066-8: Verify non-disclosure of tokens, QR payloads, and customer PII in ledger")
    void shouldVerifyNonDisclosure() {
        String opRef = "OP-ND-" + UUID.randomUUID();
        String corrRef = "CORR-ND-001";
        UUID sessionUuid = UUID.fromString(testSession.getReference());

        ReserveBookingCapacityCommand cmd = new ReserveBookingCapacityCommand(
                "BKG-ND-01",
                List.of(sessionUuid),
                1,
                opRef,
                corrRef
        );
        operationsFacade.reserveBookingCapacity(cmd);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM facade_command_idempotency_records WHERE operation_reference = ?",
                opRef
        );
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.getFirst();

        // Check columns do not contain sensitive tokens or PII
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getValue() instanceof String val) {
                assertThat(val).doesNotContain("Bearer ");
                assertThat(val).doesNotContain("firebase");
                assertThat(val).doesNotContain("vnp_");
                assertThat(val).doesNotContain("data:image");
                assertThat(val).doesNotContain("@example.com");
                assertThat(val).doesNotContain("0901234567");
            }
        }
    }
}
