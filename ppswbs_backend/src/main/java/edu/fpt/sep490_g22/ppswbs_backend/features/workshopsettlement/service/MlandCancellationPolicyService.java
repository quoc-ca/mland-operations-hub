package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementRefundPolicyRevisionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundPolicyRevision;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Owns prospective, global Mland-caused-cancellation refund policy revisions. */
@Service
@RequiredArgsConstructor
public class MlandCancellationPolicyService {
    private final SettlementRefundPolicyRevisionRepository revisions;
    private final WorkshopSettlementAuthorizationService authorization;

    @Transactional(readOnly = true)
    public List<SettlementRefundPolicyRevision> list(String firebaseUid) {
        authorization.requireManager(firebaseUid);
        return revisions.findAllByOrderByEffectiveAtAsc();
    }

    @Transactional
    public SettlementRefundPolicyRevision create(String firebaseUid, Instant effectiveAt,
                                                 Integer wholeBookingRefundPercent) {
        var manager = authorization.requireManager(firebaseUid);
        if (effectiveAt == null || !effectiveAt.isAfter(Instant.now())) {
            throw new WorkshopSettlementValidationException("effectiveAt must be in the future");
        }
        if (wholeBookingRefundPercent == null || wholeBookingRefundPercent < 0
                || wholeBookingRefundPercent > 100) {
            throw new WorkshopSettlementValidationException("wholeBookingRefundPercent must be between 0 and 100");
        }
        if (revisions.existsByEffectiveAt(effectiveAt)) {
            throw new WorkshopSettlementConflictException("POLICY_EFFECTIVE_TIME_ALREADY_EXISTS");
        }
        return revisions.saveAndFlush(SettlementRefundPolicyRevision.builder()
                .policyReference("POLICY-" + UUID.randomUUID())
                .effectiveAt(effectiveAt)
                .wholeBookingRefundPercent(wholeBookingRefundPercent)
                .createdByAccountId(manager.accountId())
                .build());
    }
}
