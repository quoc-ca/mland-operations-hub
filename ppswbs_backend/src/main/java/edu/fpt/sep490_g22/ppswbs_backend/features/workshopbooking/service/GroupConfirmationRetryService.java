package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.GroupConfirmationRetryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroup;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.GroupConfirmationRetry;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.GroupPaymentAttempt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Durable wait of a group whose verified payment arrived inside the hold but whose confirmation failed on a
 * dependency (003.1 T145, FR-038, FR-039, FR-040; decided 2026-11-15). The group stays PENDING_PAYMENT and the attempt
 * SUCCESS; the {@code WAITING} row is the wait. It is retried every minute, about 10 times, with no alert of its own
 * (the late-payment path alerts the Managers). Nothing is released while it waits. At {@code holdExpiresAt} the
 * late-payment review is created first (arrival = when Mland received the evidence), then the group is released.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroupConfirmationRetryService {

    static final Duration INTERVAL = Duration.ofMinutes(1);
    static final int MAX_RETRIES = 10;
    private static final int BATCH = 50;

    private final GroupConfirmationRetryRepository retries;
    private final BookingGroupRepository groups;
    private final LatePaymentReviewService lateReviews;

    /** Keeps the verified evidence as a WAITING row (once per evidence); the caller commits it with the receipt. */
    @Transactional(propagation = Propagation.MANDATORY)
    public GroupConfirmationRetry hold(BookingGroup group, GroupPaymentAttempt attempt, String evidenceReference,
                                       String providerTransactionReference, String failureCode,
                                       String correlationReference) {
        Optional<GroupConfirmationRetry> existing = retries.findByVerifiedEvidenceReference(evidenceReference);
        if (existing.isPresent()) {
            return existing.get();
        }
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        GroupConfirmationRetry row = retries.save(GroupConfirmationRetry.builder().group(group).attempt(attempt)
                .verifiedEvidenceReference(evidenceReference).merchantReference(attempt.getMerchantReference())
                .providerTransactionReference(providerTransactionReference).amountVnd(attempt.getAmountVnd())
                .receivedAt(now).retryStatus(GroupConfirmationRetry.Status.WAITING).retriesMade(0)
                .nextRetryAt(now.plus(INTERVAL)).lastFailureCode(failureCode)
                .correlationReference(correlationReference).build());
        log.warn("Confirmation of group {} is waiting for a dependency ({}); evidence kept for retry",
                group.getGroupCode(), failureCode);
        return row;
    }

    @Transactional(readOnly = true)
    public List<Long> dueIds(Instant now) {
        return retries.findDueIds(GroupConfirmationRetry.Status.WAITING, now, PageRequest.of(0, BATCH));
    }

    /** A confirmation succeeded (retry, replayed IPN or QueryDR): the waiting row, if any, is done. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void markConfirmed(Long groupId) {
        retries.findFirstByGroupIdAndRetryStatus(groupId, GroupConfirmationRetry.Status.WAITING).ifPresent(row -> {
            row.setRetryStatus(GroupConfirmationRetry.Status.CONFIRMED);
            row.setNextRetryAt(null);
            retries.save(row);
        });
    }

    /**
     * Settles a row that needs no new attempt: the group is already confirmed, or its hold has run out and the
     * evidence goes to the late-payment review. Returns true when the row is final, false when a retry is due.
     */
    @Transactional
    public boolean resolveIfFinal(Long retryId, String correlationReference) {
        GroupConfirmationRetry row = retries.findByIdForUpdate(retryId).orElse(null);
        if (row == null || row.getRetryStatus() != GroupConfirmationRetry.Status.WAITING) {
            return true;
        }
        BookingGroup group = groups.findByGroupCodeForUpdate(row.getGroup().getGroupCode()).orElseThrow();
        if (group.getGroupStatus() == BookingStatus.CONFIRMED) {
            row.setRetryStatus(GroupConfirmationRetry.Status.CONFIRMED);
            row.setNextRetryAt(null);
            retries.save(row);
            return true;
        }
        if (group.getHoldExpiresAt() == null || !group.getHoldExpiresAt().isAfter(Instant.now())) {
            handOver(row, group, correlationReference);
            return true;
        }
        return false;
    }

    /** Hands the WAITING row of an expired group to the late-payment review, before the group is released. */
    @Transactional
    public int handOverWaiting(String groupCode, String correlationReference) {
        BookingGroup group = groups.findByGroupCodeForUpdate(groupCode).orElse(null);
        if (group == null || group.getGroupStatus() != BookingStatus.PENDING_PAYMENT
                || group.getHoldExpiresAt() == null || group.getHoldExpiresAt().isAfter(Instant.now())) {
            return 0;
        }
        GroupConfirmationRetry row = retries
                .findFirstByGroupIdAndRetryStatus(group.getId(), GroupConfirmationRetry.Status.WAITING).orElse(null);
        if (row == null) {
            return 0;
        }
        handOver(row, group, correlationReference);
        return 1;
    }

    private void handOver(GroupConfirmationRetry row, BookingGroup group, String correlationReference) {
        var review = lateReviews.receive(group.getGroupCode(), row.getMerchantReference(), row.getAmountVnd(),
                row.getReceivedAt(), correlationReference);
        row.setRetryStatus(GroupConfirmationRetry.Status.HANDED_TO_LATE_REVIEW);
        row.setLatePaymentReviewReference(review.getReviewReference());
        row.setNextRetryAt(null);
        retries.save(row);
        log.info("Group {} confirmation never succeeded inside its hold; evidence handed to the late-payment review",
                group.getGroupCode());
    }

    /** What one retry needs, read inside a transaction so the scheduler never touches a lazy association. */
    public record RetryTarget(Long id, String groupCode, long amountVnd, String evidenceReference) { }

    /** The target when another retry is allowed (about 10 inside the hold). */
    @Transactional(readOnly = true)
    public Optional<RetryTarget> retryable(Long retryId) {
        return retries.findById(retryId)
                .filter(r -> r.getRetryStatus() == GroupConfirmationRetry.Status.WAITING
                        && r.getRetriesMade() < MAX_RETRIES)
                .map(r -> new RetryTarget(r.getId(), r.getGroup().getGroupCode(), r.getAmountVnd(),
                        r.getVerifiedEvidenceReference()));
    }

    /** One retry finished. {@code confirmed} closes the row; otherwise the failure is counted and rescheduled. */
    @Transactional
    public void recordRetry(Long retryId, boolean confirmed, String failureCode) {
        GroupConfirmationRetry row = retries.findByIdForUpdate(retryId).orElse(null);
        if (row == null || row.getRetryStatus() != GroupConfirmationRetry.Status.WAITING) {
            return;
        }
        row.setRetriesMade(row.getRetriesMade() + 1);
        if (confirmed) {
            row.setRetryStatus(GroupConfirmationRetry.Status.CONFIRMED);
            row.setNextRetryAt(null);
        } else {
            row.setLastFailureCode(failureCode);
            row.setNextRetryAt(Instant.now().plus(INTERVAL));
        }
        retries.save(row);
    }
}
