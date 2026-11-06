package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementRefundPolicyRevisionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundPolicyRevision;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;

/** Owns prospective, global Mland-caused-cancellation refund policy revisions. */
@Service
@RequiredArgsConstructor
public class MlandCancellationPolicyService {
    private final SettlementRefundPolicyRevisionRepository revisions;
    private final WorkshopSettlementAuthorizationService authorization;
    private final SettlementIdempotencyService idempotency;

    @Transactional(readOnly = true)
    public List<SettlementRefundPolicyRevision> list(String firebaseUid) {
        authorization.requireManager(firebaseUid);
        return revisions.findAllByOrderByEffectiveAtAsc();
    }

    @Transactional(readOnly = true)
    public List<SettlementRefundPolicyRevision> list(CurrentAccountActor actor) {
        authorization.requireManager(actor);
        return revisions.findAllByOrderByEffectiveAtAsc();
    }

    @Transactional
    public SettlementRefundPolicyRevision create(String firebaseUid, Instant effectiveAt,
                                                 Integer wholeBookingRefundPercent) {
        var manager = authorization.requireManager(firebaseUid);
        return createRevision(manager.accountId(), effectiveAt, wholeBookingRefundPercent);
    }

    @Transactional
    public SettlementRefundPolicyRevision create(CurrentAccountActor actor, Instant effectiveAt,
                                                 Integer wholeBookingRefundPercent) {
        var manager = authorization.requireManager(actor);
        return createRevision(manager.accountId(), effectiveAt, wholeBookingRefundPercent);
    }

    private SettlementRefundPolicyRevision createRevision(Long managerAccountId, Instant effectiveAt,
                                                            Integer wholeBookingRefundPercent) {
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
                .createdByAccountId(managerAccountId)
                .build());
    }

    @Transactional
    public SettlementRefundPolicyRevision create(String firebaseUid, Instant effectiveAt,
                                                 Integer wholeBookingRefundPercent, String idempotencyKey) {
        String fingerprint = fingerprint(effectiveAt, wholeBookingRefundPercent);
        var result = idempotency.execute(idempotencyKey, fingerprint,
                () -> create(firebaseUid, effectiveAt, wholeBookingRefundPercent).getPolicyReference());
        return revisions.findByPolicyReference(result.outcomeReference())
                .orElseThrow(() -> new WorkshopSettlementConflictException("IDEMPOTENCY_OUTCOME_INCOMPLETE"));
    }

    @Transactional
    public SettlementRefundPolicyRevision create(CurrentAccountActor actor, Instant effectiveAt,
                                                 Integer wholeBookingRefundPercent, String idempotencyKey) {
        String fingerprint = fingerprint(effectiveAt, wholeBookingRefundPercent);
        var result = idempotency.execute(idempotencyKey, fingerprint,
                () -> create(actor, effectiveAt, wholeBookingRefundPercent).getPolicyReference());
        return revisions.findByPolicyReference(result.outcomeReference())
                .orElseThrow(() -> new WorkshopSettlementConflictException("IDEMPOTENCY_OUTCOME_INCOMPLETE"));
    }

    private static String fingerprint(Instant effectiveAt, Integer percent) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((String.valueOf(effectiveAt) + "|" + percent).getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder();
            for (byte item : digest) value.append(String.format("%02x", item));
            return value.toString();
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }
}
