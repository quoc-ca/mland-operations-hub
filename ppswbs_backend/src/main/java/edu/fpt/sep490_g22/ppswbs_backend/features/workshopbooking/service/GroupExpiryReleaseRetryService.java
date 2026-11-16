package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingAuditEvidenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingAuditEvidence;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Retry rule of a failed expiry release (003.1 FR-039, T133, decided 2026-11-15): after the first failure retry 3 times,
 * 1 minute apart (inside SC-004's 5 minutes); still failing, alert the Managers once, then retry every 5 minutes until
 * it succeeds. The state is the audit trail itself: every failure writes one {@code GROUP_EXPIRY_RELEASE_FAILED} entry,
 * so the count and the last failure survive a restart and no new table is needed. A retry is the idempotent
 * {@link GroupConfirmationService#releaseExpiredGroup}, which returns at once for a group that is no longer pending.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroupExpiryReleaseRetryService {

    public static final String AUDIT_FAILED = "GROUP_EXPIRY_RELEASE_FAILED";
    static final int RETRIES_BEFORE_ALERT = 3;
    static final Duration RETRY_INTERVAL = Duration.ofMinutes(1);
    static final Duration LATER_INTERVAL = Duration.ofMinutes(5);

    private final BookingAuditEvidenceRepository auditEvidence;
    private final ManagerAlertService managerAlerts;

    /** Is the next release attempt of this group due? A group that never failed is always due. */
    @Transactional(readOnly = true)
    public boolean isDue(String groupCode, Instant now) {
        long failures = auditEvidence.countByBookingCodeAndAction(groupCode, AUDIT_FAILED);
        if (failures == 0) {
            return true;
        }
        Duration wait = failures <= RETRIES_BEFORE_ALERT ? RETRY_INTERVAL : LATER_INTERVAL;
        return auditEvidence.findFirstByBookingCodeAndActionOrderByOccurredAtDesc(groupCode, AUDIT_FAILED)
                .map(last -> !now.isBefore(last.getOccurredAt().plus(wait)))
                .orElse(true);
    }

    /** Records one failure; the failure that ends the 3 retries alerts the Managers exactly once. */
    @Transactional
    public void recordFailure(BookingGroup group, String correlationReference, String reason) {
        auditEvidence.save(BookingAuditEvidence.builder()
                .bookingCode(group.getGroupCode())
                .locationId(0L)
                .actorType("SYSTEM")
                .action(AUDIT_FAILED)
                .outcome("FAILED")
                .correlationReference(correlationReference)
                .actionReason(reason)
                .occurredAt(Instant.now())
                .build());
        long failures = auditEvidence.countByBookingCodeAndAction(group.getGroupCode(), AUDIT_FAILED);
        if (failures == RETRIES_BEFORE_ALERT + 1L) {
            managerAlerts.alert(group, GroupNotificationService.KIND_EXPIRY_RELEASE_MANAGER_ALERT, group.getGroupCode(),
                    "MLAND – Không nhả được chỗ giữ của đơn " + group.getGroupCode(),
                    "Chỗ giữ, voucher hoặc điểm của đơn " + group.getGroupCode() + " đã hết hạn nhưng hệ thống chưa nhả "
                            + "được sau " + failures + " lần thử. Hệ thống sẽ thử lại mỗi 5 phút cho đến khi thành công; "
                            + "đơn vẫn nằm trong hàng chờ để theo dõi.",
                    correlationReference);
        }
    }
}
