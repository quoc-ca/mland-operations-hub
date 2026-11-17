package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import edu.fpt.sep490_g22.ppswbs_backend.common.CorrelationIdFilter;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.LoyaltyAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyAudit;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Writes the audit trail in the same transaction as the change it describes (data-model {@code loyalty_audit}).
 * A failed write rolls the change back with it. Details are safe fields or revision references built by loyalty
 * code only: never a full request, token, member personal data, secret or raw source proof. Rows are retained at
 * least five years and are never purged in V1 (FR-041).
 */
@Service
public class LoyaltyAuditService {

    static final int DETAIL_MAX = 2000;

    private final LoyaltyAuditRepository audits;
    private final Clock clock;

    public LoyaltyAuditService(LoyaltyAuditRepository audits, ObjectProvider<Clock> clock) {
        this.audits = audits;
        this.clock = clock.getIfAvailable(Clock::systemUTC);
    }

    /** One audit event of a change; joins the caller's transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public LoyaltyAudit record(String actorRef, String action, String objectRef, String safeDetail) {
        requireSafeLength(safeDetail);
        return audits.save(LoyaltyAudit.event(actorRef, action, objectRef, safeDetail, correlationId(), now()));
    }

    /** Audit row that is also the receipt of an idempotent command; joins the caller's transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public LoyaltyAudit recordCommand(String actorRef, String action, String objectRef, String safeDetail,
                                      String actorKey, String commandId, String canonicalDigest, String minimalResult) {
        requireSafeLength(safeDetail);
        requireSafeLength(minimalResult);
        return audits.saveAndFlush(LoyaltyAudit.command(actorRef, action, objectRef, safeDetail, correlationId(), now(),
                actorKey, commandId, canonicalDigest, minimalResult));
    }

    Instant now() {
        return LoyaltyValues.truncateToMicros(clock.instant());
    }

    private static String correlationId() {
        String id = MDC.get(CorrelationIdFilter.MDC_KEY);
        return id == null || id.length() > 64 ? null : id;
    }

    private static void requireSafeLength(String value) {
        if (value != null && value.length() > DETAIL_MAX) {
            throw new IllegalArgumentException("Audit detail is longer than " + DETAIL_MAX + " characters");
        }
    }
}
