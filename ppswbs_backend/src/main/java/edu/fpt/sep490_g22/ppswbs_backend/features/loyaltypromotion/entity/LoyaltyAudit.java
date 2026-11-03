package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Append-only audit trail and command receipt in one table. Written in the same transaction as the change;
 * it never holds a full request, token, PII or secret. Command fields are set only for commands that carry an
 * idempotency key: unique on (actor key, action, command id) when the command id is present. The same digest
 * replays the committed minimal result, a different digest is a conflict. Retained at least five years; never
 * purged while referenced or while the transaction is unfinished.
 */
@Entity
@Table(name = "loyalty_audit")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoyaltyAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Account reference of the actor, or the owning module for a system outcome. */
    @Column(name = "actor_ref", nullable = false, updatable = false, length = 128)
    private String actorRef;

    @Column(name = "action", nullable = false, updatable = false, length = 64)
    private String action;

    @Column(name = "object_ref", nullable = false, updatable = false, length = 128)
    private String objectRef;

    /** Safe before/after fields or revision references only. */
    @Column(name = "detail", updatable = false, length = 2000)
    private String detail;

    @Column(name = "correlation_id", updatable = false, length = 64)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "actor_key", updatable = false, length = 128)
    private String actorKey;

    @Column(name = "command_id", updatable = false, length = 36)
    private String commandId;

    @Column(name = "canonical_digest", updatable = false, length = 64)
    private String canonicalDigest;

    @Column(name = "minimal_result", updatable = false, length = 2000)
    private String minimalResult;

    public static LoyaltyAudit event(String actorRef, String action, String objectRef, String detail,
                                     String correlationId, Instant occurredAt) {
        LoyaltyAudit audit = new LoyaltyAudit();
        audit.actorRef = actorRef;
        audit.action = action;
        audit.objectRef = objectRef;
        audit.detail = detail;
        audit.correlationId = correlationId;
        audit.occurredAt = occurredAt;
        return audit;
    }

    /** Audit row that is also the receipt of an idempotent command. */
    public static LoyaltyAudit command(String actorRef, String action, String objectRef, String detail,
                                       String correlationId, Instant occurredAt, String actorKey, String commandId,
                                       String canonicalDigest, String minimalResult) {
        if (actorKey == null || commandId == null || canonicalDigest == null) {
            throw new IllegalArgumentException("A command receipt needs actor key, command id and digest.");
        }
        LoyaltyAudit audit = event(actorRef, action, objectRef, detail, correlationId, occurredAt);
        audit.actorKey = actorKey;
        audit.commandId = commandId;
        audit.canonicalDigest = canonicalDigest;
        audit.minimalResult = minimalResult;
        return audit;
    }

    public boolean isCommand() {
        return commandId != null;
    }
}
