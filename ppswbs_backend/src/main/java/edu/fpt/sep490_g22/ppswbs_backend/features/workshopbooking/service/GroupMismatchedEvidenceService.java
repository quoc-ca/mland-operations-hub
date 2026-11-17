package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingAuditEvidenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.GroupPaymentAttemptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingAuditEvidence;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroup;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.RecordMismatchedGroupPaymentEvidenceCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 003.1 FR-010f / T144 (decided 2026-11-16, F14). Verified money for a group whose amount or currency does not match
 * is never a confirmation. It is recorded as the shared ingress receipt (written by paymentintegration), one audit
 * entry with the reason and one WARN log; no new table or column. The group stays PENDING_PAYMENT while its hold runs.
 * At {@code holdExpiresAt} (or at once when the evidence arrives after it) the evidence is handed to the
 * late-payment review, whose amount condition fails, so the Managers are alerted and only a 100% bank refund remains.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroupMismatchedEvidenceService {

    public static final String ACTION = "GROUP_PAYMENT_EVIDENCE_MISMATCH";
    private static final String ACTOR = "SYSTEM";

    private final GroupPaymentAttemptRepository attempts;
    private final BookingGroupRepository groups;
    private final BookingAuditEvidenceRepository audit;
    private final LatePaymentReviewService lateReviews;

    /** @return true when the merchant reference belongs to a group payment, so the caller stores the receipt. */
    @Transactional
    public boolean record(RecordMismatchedGroupPaymentEvidenceCommand command) {
        var attempt = attempts.findByMerchantReference(command.merchantReference()).orElse(null);
        if (attempt == null) {
            return false;
        }
        BookingGroup group = attempt.getGroup();
        String detail = "reason=" + command.reason() + ";merchant=" + command.merchantReference()
                + ";evidence=" + command.evidenceReference() + ";amount=" + command.receivedAmountVnd()
                + ";currency=" + command.receivedCurrencyCode();
        if (!audit.existsByBookingCodeAndActionAndActionReason(group.getGroupCode(), ACTION, detail)) {
            audit.save(BookingAuditEvidence.builder().bookingCode(group.getGroupCode()).locationId(0L)
                    .actorType(ACTOR).action(ACTION).outcome("RECORDED")
                    .correlationReference(truncate(command.correlationReference(), 64))
                    .actionReason(detail).occurredAt(Instant.now()).build());
            log.warn("Verified VNPay evidence for group {} does not match its payment attempt ({}); recorded, "
                            + "group not confirmed", group.getGroupCode(), command.reason());
        }
        if (isHandable(group) && group.getHoldExpiresAt() != null
                && !group.getHoldExpiresAt().isAfter(Instant.now())) {
            handOver(group.getGroupCode(), command.correlationReference());
        }
        return true;
    }

    /**
     * Hands every recorded mismatched evidence of an expired group to the late-payment review, creating the review
     * before the group is released (the review service releases the stale hold itself). Idempotent per evidence.
     */
    @Transactional
    public int handOver(String groupCode, String correlationReference) {
        BookingGroup group = groups.findByGroupCodeForUpdate(groupCode).orElse(null);
        if (group == null || !isHandable(group) || group.getHoldExpiresAt() == null
                || group.getHoldExpiresAt().isAfter(Instant.now())) {
            return 0;
        }
        int handed = 0;
        for (BookingAuditEvidence entry : audit.findByBookingCodeOrderByOccurredAtDesc(groupCode)) {
            if (!ACTION.equals(entry.getAction()) || entry.getActionReason() == null) continue;
            String evidence = field(entry.getActionReason(), "evidence");
            String amount = field(entry.getActionReason(), "amount");
            if (evidence == null || amount == null) continue;
            lateReviews.receive(groupCode, evidence, Long.parseLong(amount), correlationReference);
            handed++;
        }
        return handed;
    }

    private static boolean isHandable(BookingGroup group) {
        return group.getGroupStatus() == BookingStatus.PENDING_PAYMENT || group.getGroupStatus() == BookingStatus.EXPIRED;
    }

    private static String field(String detail, String key) {
        for (String part : detail.split(";")) {
            if (part.startsWith(key + "=")) return part.substring(key.length() + 1);
        }
        return null;
    }

    private static String truncate(String value, int max) {
        return value == null ? "MISMATCH" : value.length() <= max ? value : value.substring(0, max);
    }
}
