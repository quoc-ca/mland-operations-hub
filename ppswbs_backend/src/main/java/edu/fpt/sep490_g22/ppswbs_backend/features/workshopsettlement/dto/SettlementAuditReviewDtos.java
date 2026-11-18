package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto;

import java.time.Instant;
import java.util.List;

/**
 * OpenAPI {@code SettlementAuditRow}, {@code AuditActor} and {@code SettlementAuditPage} (003.2 FR-052, served by
 * 003.3). {@code reasonText} is the full free text of the stored reason (up to 500 characters) and may contain
 * personal data typed by the actor: it is Manager-only and must never reach a log, so {@code toString} omits it.
 */
public final class SettlementAuditReviewDtos {

    private SettlementAuditReviewDtos() {
    }

    /** Account id plus display name; the name is null when the account is gone or has no name. */
    public record AuditActor(Long accountId, String displayName) { }

    public record SettlementAuditRow(Long id, Instant occurredAt, String actionCode, String aggregateType,
                                     String aggregateReference, AuditActor actor, String actorReference,
                                     String correlationReference, String evidenceReference, String reasonText) {
        @Override
        public String toString() {
            return "SettlementAuditRow[id=" + id + ", actionCode=" + actionCode + ", aggregateType=" + aggregateType
                    + ", reasonText=" + (reasonText == null ? "null" : "<omitted>") + "]";
        }
    }

    /** {@code nextCursor} is null on the last page. */
    public record SettlementAuditPage(List<SettlementAuditRow> items, String nextCursor) { }

    /** Validated, normalized filter of one read; {@code from} is already defaulted to the 7-day window. */
    public record SettlementAuditFilter(String actionCode, String actorReference, String aggregateType,
                                        String aggregateReference, Instant from, Instant to) { }
}
