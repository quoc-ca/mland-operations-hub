package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Releases booking groups whose 12-minute hold ran out with no confirmation (003.1 FR-032). The whole group goes at
 * once, never a part of it. Each group is released in its own transaction, so one stuck group cannot keep the others
 * held. The sweep runs every 30 seconds (SC-004 needs every expired group released within 5 minutes). A release that
 * fails is retried on the {@link GroupExpiryReleaseRetryService} schedule (FR-039, T133); every retry is idempotent.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroupHoldExpiryScheduler {

    private static final int BATCH = 50;

    private final BookingGroupRepository groups;
    private final GroupConfirmationService confirmation;
    private final GroupExpiryReleaseRetryService retries;
    private final GroupMismatchedEvidenceService mismatchedEvidence;
    private final GroupConfirmationRetryService confirmationRetries;

    @Scheduled(fixedDelayString = "${workshop.booking.group-expiry-sweep:PT30S}")
    public void releaseExpiredGroups() {
        Instant now = Instant.now();
        for (BookingGroup group : groups.findExpiredPending(now, PageRequest.of(0, BATCH))) {
            if (!retries.isDue(group.getGroupCode(), now)) {
                continue;
            }
            String correlation = "GROUP-EXPIRY-" + UUID.randomUUID();
            try {
                // T145: verified evidence still waiting for a confirmation retry is handed to the late-payment review first.
                confirmationRetries.handOverWaiting(group.getGroupCode(), correlation);
                // F14: evidence that did not match goes to the late-payment review first, then the group is released.
                mismatchedEvidence.handOver(group.getGroupCode(), correlation);
                confirmation.releaseExpiredGroup(group.getGroupCode(), "EXPIRE-" + group.getGroupCode(),
                        correlation);
            } catch (RuntimeException ex) {
                // The seats stay held; the group is retried on the FR-039 schedule and the Managers hear after 3 retries.
                log.warn("Could not release expired booking group {}: {}", group.getGroupCode(), ex.getMessage());
                try {
                    retries.recordFailure(group, correlation, ex.getClass().getSimpleName());
                } catch (RuntimeException recordFailure) {
                    log.warn("Could not record the failed release of {}: {}", group.getGroupCode(),
                            recordFailure.getClass().getSimpleName());
                }
            }
        }
    }
}
