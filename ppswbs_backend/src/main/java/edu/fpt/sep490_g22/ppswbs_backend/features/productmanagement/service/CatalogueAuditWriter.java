package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.common.CorrelationIdFilter;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.CatalogueAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.CatalogueAuditEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Writes catalogue business audit (FR-027).
 *
 * <ul>
 *   <li>{@link #recordSuccess} must run inside the mutation's transaction; if it fails the mutation
 *       rolls back too, so a change is never committed without its audit.</li>
 *   <li>{@link #recordRejection} runs in its own transaction, after the rejected mutation has rolled back.</li>
 * </ul>
 * Callers pass only safe values: no token, secret, full request payload or file content.
 */
@Service
@RequiredArgsConstructor
public class CatalogueAuditWriter {

    private final CatalogueAuditRepository repository;

    public record Entry(String actorId, String action, String resourceType, String resourceId,
                        Long resourceVersion, Long resourceRevision, List<String> changedFields,
                        Map<String, Object> safeChanges) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CatalogueAuditEvent recordSuccess(Entry entry) {
        return repository.saveAndFlush(toEvent(entry, CatalogueAuditEvent.Outcome.SUCCESS));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CatalogueAuditEvent recordRejection(Entry entry) {
        return repository.saveAndFlush(toEvent(entry, CatalogueAuditEvent.Outcome.REJECTED));
    }

    private static CatalogueAuditEvent toEvent(Entry entry, CatalogueAuditEvent.Outcome outcome) {
        return new CatalogueAuditEvent(entry.actorId(), entry.action(), entry.resourceType(), entry.resourceId(),
                entry.resourceVersion(), entry.resourceRevision(), entry.changedFields(), entry.safeChanges(),
                outcome, currentCorrelationId(), CatalogueValues.toStoredInstant(Instant.now()));
    }

    private static String currentCorrelationId() {
        String id = MDC.get(CorrelationIdFilter.MDC_KEY);
        return id != null ? id : UUID.randomUUID().toString();
    }
}
