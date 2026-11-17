package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingIdempotencyRecordRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingIdempotencyLifecycleStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingIdempotencyRecord;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingIdempotencyClaim;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingIdempotencyConflictException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingIdempotencyService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopOperationsFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class BookingIdempotencyIT {

    @Autowired
    private BookingIdempotencyService idempotencyService;

    @Autowired
    private BookingIdempotencyRecordRepository repository;

    @MockBean
    private AccountFacade accountFacade;

    @MockBean
    private WorkshopOperationsFacade operationsFacade;

    @Test
    @DisplayName("T012-1: Sequential replay with same key and payload returns cached outcome")
    void verifySequentialReplayReturnsCachedOutcome() {
        String key = "IDEM-" + UUID.randomUUID();
        String action = "CREATE_BOOKING";
        String payload = "{\"packageId\":1,\"participantCount\":2}";
        String correlationRef = "CORR-01";

        BookingIdempotencyClaim initialClaim = idempotencyService.acquireClaim(key, action, payload, correlationRef);
        assertThat(initialClaim.cachedOutcome()).isFalse();
        assertThat(initialClaim.recordId()).isNotNull();

        // Complete the claim
        idempotencyService.completeClaim(initialClaim.recordId(), initialClaim.claimVersion(), 201, "{\"bookingCode\":\"BK-100\"}");

        // Replay with identical key and payload
        BookingIdempotencyClaim replayClaim = idempotencyService.acquireClaim(key, action, payload, "CORR-02");
        assertThat(replayClaim.cachedOutcome()).isTrue();
        assertThat(replayClaim.statusCode()).isEqualTo(201);
        assertThat(replayClaim.responsePayload()).contains("BK-100");
    }

    @Test
    @DisplayName("T012-2: Reused key with different payload throws BookingIdempotencyConflictException")
    void verifyReusedKeyWithMismatchedPayloadThrowsConflict() {
        String key = "IDEM-" + UUID.randomUUID();
        String action = "CREATE_BOOKING";
        String payload1 = "{\"packageId\":1,\"participantCount\":2}";
        String payload2 = "{\"packageId\":1,\"participantCount\":4}";

        BookingIdempotencyClaim initialClaim = idempotencyService.acquireClaim(key, action, payload1, "CORR-01");
        idempotencyService.completeClaim(initialClaim.recordId(), initialClaim.claimVersion(), 201, "{\"bookingCode\":\"BK-200\"}");

        assertThatThrownBy(() -> idempotencyService.acquireClaim(key, action, payload2, "CORR-02"))
                .isInstanceOf(BookingIdempotencyConflictException.class)
                .hasMessageContaining("different request parameters");
    }

    @Test
    @DisplayName("T012-3: Concurrent duplicate command execution allows only 1 live claim, loser rejected without side effect")
    void verifyConcurrentDuplicateRejection() throws Exception {
        String key = "IDEM-" + UUID.randomUUID();
        String action = "START_PAYMENT";
        String payload = "{\"bookingCode\":\"BK-300\"}";

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threadCount);

        AtomicInteger liveClaims = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final String corr = "CORR-CONC-" + i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    BookingIdempotencyClaim claim = idempotencyService.acquireClaim(key, action, payload, corr);
                    if (!claim.cachedOutcome()) {
                        liveClaims.incrementAndGet();
                    }
                } catch (BookingIdempotencyConflictException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    // unexpected error
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        assertThat(doneGate.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(liveClaims.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("T012-4: Expired lease is reclaimed with incremented claimVersion")
    void verifyExpiredLeaseReclaim() {
        String key = "IDEM-" + UUID.randomUUID();
        String action = "RELEASE_HOLD";
        String payload = "{\"bookingCode\":\"BK-400\"}";

        BookingIdempotencyClaim initialClaim = idempotencyService.acquireClaim(key, action, payload, "CORR-01");
        assertThat(initialClaim.claimVersion()).isEqualTo(1L);

        // Manually expire the lease in the past
        BookingIdempotencyRecord record = repository.findById(initialClaim.recordId()).orElseThrow();
        record.setLeaseExpiresAt(Instant.now().minus(10, ChronoUnit.MINUTES));
        repository.saveAndFlush(record);

        // Second caller should reclaim expired lease
        BookingIdempotencyClaim reclaimedClaim = idempotencyService.acquireClaim(key, action, payload, "CORR-02");
        assertThat(reclaimedClaim.cachedOutcome()).isFalse();
        assertThat(reclaimedClaim.claimVersion()).isEqualTo(2L);
    }

    @Test
    @DisplayName("T012-5: Fencing check prevents completion if claimVersion is outdated")
    void verifyFencingPreventsStaleCompletion() {
        String key = "IDEM-" + UUID.randomUUID();
        String action = "CONFIRMATION_RESEND";
        String payload = "{\"bookingCode\":\"BK-500\"}";

        BookingIdempotencyClaim claim = idempotencyService.acquireClaim(key, action, payload, "CORR-01");

        // Attempting to complete with outdated version throws IllegalStateException
        assertThatThrownBy(() -> idempotencyService.completeClaim(claim.recordId(), 999L, 200, "{\"status\":\"OK\"}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Fencing check failed");
    }
}
