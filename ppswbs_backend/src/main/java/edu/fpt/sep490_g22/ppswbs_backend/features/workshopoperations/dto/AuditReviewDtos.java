package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * OpenAPI {@code OperationsAuditRow}, {@code AuditActor} and {@code OperationsAuditPage} (003.2 FR-047 to FR-051).
 * Entities are never exposed; {@code metadata} holds whitelisted keys only, all values as strings.
 */
public final class AuditReviewDtos {

    private AuditReviewDtos() {
    }

    /** Account id plus display name; the name is null when the account is gone or has no name. */
    public record AuditActor(Long accountId, String displayName) { }

    public record OperationsAuditRow(Long id, Instant occurredAt, AuditActor actor, String action, String outcome,
                                     Long sessionId, String correlationId, Map<String, String> metadata) { }

    /** {@code nextCursor} is null on the last page. */
    public record OperationsAuditPage(List<OperationsAuditRow> items, String nextCursor) { }

    /** Validated, normalized filter of one read; {@code from} is already defaulted to the 7-day window. */
    public record OperationsAuditFilter(String action, String outcome, Long actorId, Long sessionId,
                                        Instant from, Instant to) { }
}
