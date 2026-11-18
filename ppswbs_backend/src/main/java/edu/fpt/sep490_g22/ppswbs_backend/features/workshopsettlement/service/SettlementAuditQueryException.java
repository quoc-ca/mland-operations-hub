package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

/**
 * A malformed audit-review query value (bad limit, cursor, time range or number). Answered 400
 * {@code VALIDATION_ERROR} with the offending field, like the existing Settlement bean-validation answer; no new
 * error code exists for it (003.2 plan, Phase 19).
 */
public class SettlementAuditQueryException extends RuntimeException {
    private final String field;

    public SettlementAuditQueryException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
