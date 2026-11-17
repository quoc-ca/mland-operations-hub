package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingAuditEvidenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.LatePaymentReviewRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingAuditEvidence;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroup;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.LatePaymentReview;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.BookingNotificationDeliveryFailed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A failed buyer confirmation email of a CONFIRMED group (003.1 FR-015a, FR-015b, FR-040a; T147 and T129, decided
 * 2026-11-16). Feature 007 reports a terminal failure with {@link BookingNotificationDeliveryFailed}; this service
 * changes no group, ticket, payment, hold, Magic-Link or QR state. It resends the confirmation 5 minutes, 30 minutes
 * and 2 hours after the failure, each time as a new immutable handoff with a new notification reference. When the
 * failure that ends the three resends arrives, the active Managers (only) are alerted to phone the buyer, with an
 * audit entry. If the group has a late-payment review, the failure also sets its {@code resultDeliveryState}: a failed
 * email never counts as "reached", and a result still undelivered after the FR-040a deadline is flagged for manual
 * contact. The state lives in the audit trail ({@code NOTIFICATION_DELIVERY_FAILED}, {@code NOTIFICATION_RESEND_QUEUED}),
 * so the count survives a restart; a duplicate event is ignored by notification reference.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroupNotificationFailureService {

    public static final String AUDIT_FAILED = "NOTIFICATION_DELIVERY_FAILED";
    public static final String AUDIT_RESEND_QUEUED = "NOTIFICATION_RESEND_QUEUED";
    public static final String AUDIT_EXHAUSTED = "NOTIFICATION_RESEND_EXHAUSTED";

    /** Delay after the 1st, 2nd and 3rd failure before the matching resend. */
    static final List<Duration> RESEND_DELAYS = List.of(Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(2));
    private static final Set<String> HANDLED_KINDS = Set.of(GroupNotificationService.KIND_GROUP_CONFIRMATION,
            GroupNotificationService.KIND_GROUP_CONFIRMATION_RESEND);
    private static final Duration LOOKBACK = Duration.ofHours(3);

    private final BookingGroupRepository groups;
    private final BookingAuditEvidenceRepository auditEvidence;
    private final LatePaymentReviewRepository reviews;
    private final GroupConfirmationService confirmation;
    private final GroupNotificationService notifications;
    private final ManagerAlertService managerAlerts;
    private final PlatformTransactionManager transactionManager;

    /** Consumes the 007 signal idempotently; any other notification kind is not this service's business. */
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDeliveryFailed(BookingNotificationDeliveryFailed event) {
        if (event == null || event.bookingReference() == null || event.notificationReference() == null
                || !HANDLED_KINDS.contains(event.notificationKind())) {
            return;
        }
        BookingGroup group = groups.findByGroupCodeForUpdate(event.bookingReference()).orElse(null);
        if (group == null || group.getGroupStatus() != BookingStatus.CONFIRMED) {
            return;
        }
        if (auditEvidence.existsByBookingCodeAndActionAndActionReason(group.getGroupCode(), AUDIT_FAILED,
                event.notificationReference())) {
            return;
        }
        Instant failedAt = (event.failedAt() == null ? Instant.now() : event.failedAt()).truncatedTo(ChronoUnit.MICROS);
        String correlation = event.correlationReference() == null ? "NOTIF-FAIL-" + UUID.randomUUID()
                : event.correlationReference();
        audit(group, AUDIT_FAILED, "FAILED", correlation, event.notificationReference(), failedAt);
        long failures = auditEvidence.countByBookingCodeAndAction(group.getGroupCode(), AUDIT_FAILED);
        Optional<LatePaymentReview> review = reviews.findFirstByGroupIdOrderByIdDesc(group.getId());

        if (failures > RESEND_DELAYS.size()) {
            audit(group, AUDIT_EXHAUSTED, "EXHAUSTED", correlation, event.notificationReference(), Instant.now());
            managerAlerts.alert(group, GroupNotificationService.KIND_CONFIRMATION_DELIVERY_MANAGER_ALERT,
                    event.notificationReference(),
                    "MLAND – Cần gọi điện cho khách của đơn " + group.getGroupCode(),
                    "Email xác nhận của đơn " + group.getGroupCode() + " không gửi được sau " + failures
                            + " lần (gồm 3 lần gửi lại). Vui lòng liên hệ khách qua điện thoại. Trạng thái đơn và vé "
                            + "không thay đổi.",
                    correlation);
            review.ifPresent(r -> setState(r, LatePaymentReview.DELIVERY_MANUAL_CONTACT));
            return;
        }
        review.ifPresent(r -> {
            boolean pastDeadline = r.getResultDueAt() != null && !failedAt.isBefore(r.getResultDueAt());
            setState(r, pastDeadline ? LatePaymentReview.DELIVERY_MANUAL_CONTACT : LatePaymentReview.DELIVERY_RESEND_SCHEDULED);
        });
    }

    /** Sends due resends and flags results still undelivered after their FR-040a deadline. */
    @Scheduled(fixedDelayString = "${workshop.booking.notification-resend-sweep:PT30S}")
    public void sweep() {
        Instant now = Instant.now();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        for (String groupCode : auditEvidence.findBookingCodesWithActionSince(AUDIT_FAILED, now.minus(LOOKBACK))) {
            try {
                tx.executeWithoutResult(status -> resendIfDue(groupCode, now));
            } catch (RuntimeException ex) {
                log.warn("Could not resend the confirmation of group {}: {}", groupCode, ex.getClass().getSimpleName());
            }
        }
        try {
            tx.executeWithoutResult(status -> flagOverdue(now));
        } catch (RuntimeException ex) {
            log.warn("Could not flag overdue late-payment results: {}", ex.getClass().getSimpleName());
        }
    }

    public void resendIfDue(String groupCode, Instant now) {
        BookingGroup group = groups.findByGroupCodeForUpdate(groupCode).orElse(null);
        if (group == null || group.getGroupStatus() != BookingStatus.CONFIRMED) {
            return;
        }
        long failures = auditEvidence.countByBookingCodeAndAction(groupCode, AUDIT_FAILED);
        if (failures == 0 || failures > RESEND_DELAYS.size()) {
            return;
        }
        BookingAuditEvidence lastFailure = auditEvidence
                .findFirstByBookingCodeAndActionOrderByOccurredAtDesc(groupCode, AUDIT_FAILED).orElse(null);
        if (lastFailure == null || now.isBefore(lastFailure.getOccurredAt().plus(RESEND_DELAYS.get((int) failures - 1)))) {
            return;
        }
        // One resend per failure: a resend already queued after the last failure is waiting for 007's answer.
        boolean alreadyQueued = auditEvidence.findFirstByBookingCodeAndActionOrderByOccurredAtDesc(groupCode,
                AUDIT_RESEND_QUEUED).map(q -> q.getOccurredAt().isAfter(lastFailure.getOccurredAt())).orElse(false);
        if (alreadyQueued) {
            return;
        }
        String correlation = "NOTIF-RESEND-" + UUID.randomUUID();
        String accessLink = notifications.findActiveAccessLink(group);
        if (accessLink == null) {
            // Nothing can be resent without the active booking link; the Manager must phone the buyer now.
            audit(group, AUDIT_EXHAUSTED, "NO_ACCESS_LINK", correlation, groupCode, now);
            managerAlerts.alert(group, GroupNotificationService.KIND_CONFIRMATION_DELIVERY_MANAGER_ALERT, groupCode,
                    "MLAND – Cần gọi điện cho khách của đơn " + groupCode,
                    "Email xác nhận của đơn " + groupCode + " không gửi được và không thể gửi lại. Vui lòng liên hệ "
                            + "khách qua điện thoại.", correlation);
            reviews.findFirstByGroupIdOrderByIdDesc(group.getId())
                    .ifPresent(r -> setState(r, LatePaymentReview.DELIVERY_MANUAL_CONTACT));
            return;
        }
        confirmation.resendConfirmationEmail(group, accessLink, correlation);
        audit(group, AUDIT_RESEND_QUEUED, "QUEUED", correlation, groupCode, Instant.now());
    }

    private void flagOverdue(Instant now) {
        for (LatePaymentReview review : reviews.findByResultDeliveryStateAndResultDueAtBefore(
                LatePaymentReview.DELIVERY_RESEND_SCHEDULED, now)) {
            setState(review, LatePaymentReview.DELIVERY_MANUAL_CONTACT);
        }
    }

    private void setState(LatePaymentReview review, String state) {
        if (!state.equals(review.getResultDeliveryState())) {
            review.setResultDeliveryState(state);
            reviews.save(review);
        }
    }

    private void audit(BookingGroup group, String action, String outcome, String correlation, String reason,
                       Instant occurredAt) {
        auditEvidence.save(BookingAuditEvidence.builder()
                .bookingCode(group.getGroupCode())
                .locationId(0L)
                .actorType("SYSTEM")
                .action(action)
                .outcome(outcome)
                .correlationReference(correlation.length() > 64 ? correlation.substring(0, 64) : correlation)
                .actionReason(reason)
                .occurredAt(occurredAt)
                .build());
    }
}
