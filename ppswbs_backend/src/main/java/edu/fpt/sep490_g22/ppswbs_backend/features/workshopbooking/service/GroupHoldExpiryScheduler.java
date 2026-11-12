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
 * Releases booking groups whose 12-minute hold ran out with no confirmation (003.1 FR-032). The whole group
 * goes at once, never a part of it. Each group is released in its own transaction, so one stuck group cannot
 * keep the others held.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroupHoldExpiryScheduler {

    private static final int BATCH = 50;

    private final BookingGroupRepository groups;
    private final GroupConfirmationService confirmation;

    @Scheduled(fixedDelayString = "${workshop.booking.group-expiry-sweep:PT30S}")
    public void releaseExpiredGroups() {
        for (BookingGroup group : groups.findExpiredPending(Instant.now(), PageRequest.of(0, BATCH))) {
            String correlation = "GROUP-EXPIRY-" + UUID.randomUUID();
            try {
                confirmation.releaseExpiredGroup(group.getGroupCode(), "EXPIRE-" + group.getGroupCode(),
                        correlation);
            } catch (RuntimeException ex) {
                // A group that cannot be released now is retried on the next sweep while its seats stay held.
                log.warn("Could not release expired booking group {}: {}", group.getGroupCode(), ex.getMessage());
            }
        }
    }
}
