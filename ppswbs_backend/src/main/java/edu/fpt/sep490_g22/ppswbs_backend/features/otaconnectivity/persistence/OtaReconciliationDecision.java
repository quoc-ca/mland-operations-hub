package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Immutable audit evidence for one Manager/Admin sync or reconciliation action. */
@Entity
@Table(name = "ota_reconciliation_decisions", uniqueConstraints = {
        @UniqueConstraint(name = "uq_ota_recon_decision_reference", columnNames = "decision_reference"),
        @UniqueConstraint(name = "uq_ota_recon_operation_reference", columnNames = "operation_reference"),
        @UniqueConstraint(name = "uq_ota_recon_actor_idempotency", columnNames = {"actor_account_reference", "idempotency_key_hash"})
})
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class OtaReconciliationDecision {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "decision_reference", nullable = false, length = 36, updatable = false)
    private String decisionReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_item_id", updatable = false)
    private OtaReconciliationWorkItem workItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mapping_id", updatable = false)
    private OtaChannelMapping mapping;

    @Column(name = "provider_code", nullable = false, length = 32, updatable = false)
    private String providerCode;

    @Column(name = "location_reference", nullable = false, length = 128, updatable = false)
    private String locationReference;

    @Column(name = "actor_account_reference", nullable = false, length = 128, updatable = false)
    private String actorAccountReference;

    @Column(name = "actor_role", nullable = false, length = 16, updatable = false)
    private String actorRole;

    @Column(name = "action_code", nullable = false, length = 32, updatable = false)
    private String actionCode;

    @Column(name = "action_scope", nullable = false, length = 32, updatable = false)
    private String actionScope;

    @Column(name = "reason", nullable = false, length = 1000, updatable = false)
    private String reason;

    @Column(name = "outcome", nullable = false, length = 32, updatable = false)
    private String outcome;

    @Column(name = "operation_reference", nullable = false, length = 36, updatable = false)
    private String operationReference;

    @Column(name = "correlation_reference", nullable = false, length = 36, updatable = false)
    private String correlationReference;

    @Column(name = "idempotency_key_hash", nullable = false, length = 64, updatable = false)
    private String idempotencyKeyHash;

    @Column(name = "request_fingerprint", nullable = false, length = 64, updatable = false)
    private String requestFingerprint;

    @Column(name = "source_version", updatable = false)
    private Long sourceVersion;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    private OtaReconciliationDecision(OtaReconciliationWorkItem workItem, OtaChannelMapping mapping,
            String providerCode, String locationReference, String actorAccountReference, String actorRole,
            String actionCode, String actionScope, String reason, String outcome, String operationReference,
            String correlationReference, String idempotencyKeyHash, String requestFingerprint,
            Long sourceVersion) {
        if (workItem == null && mapping == null) {
            throw new IllegalArgumentException("A reconciliation work item or mapping is required");
        }
        this.decisionReference = UUID.randomUUID().toString();
        this.workItem = workItem;
        this.mapping = mapping;
        this.providerCode = required(providerCode, 32, "Provider is required");
        this.locationReference = required(locationReference, 128, "Canonical location is required");
        this.actorAccountReference = required(actorAccountReference, 128, "Actor is required");
        this.actorRole = required(actorRole, 16, "Actor role is required");
        this.actionCode = required(actionCode, 32, "Action is required");
        this.actionScope = required(actionScope, 32, "Action scope is required");
        this.reason = required(reason, 1000, "Reason is required");
        this.outcome = required(outcome, 32, "Outcome is required");
        this.operationReference = required(operationReference, 36, "Operation reference is required");
        this.correlationReference = required(correlationReference, 36, "Correlation reference is required");
        this.idempotencyKeyHash = required(idempotencyKeyHash, 64, "Idempotency key hash is required");
        this.requestFingerprint = required(requestFingerprint, 64, "Request fingerprint is required");
        if (sourceVersion != null && sourceVersion < 1) {
            throw new IllegalArgumentException("Source version must be positive");
        }
        this.sourceVersion = sourceVersion;
        this.recordedAt = Instant.now();
    }

    public static OtaReconciliationDecision record(OtaReconciliationWorkItem workItem, OtaChannelMapping mapping,
            String providerCode, String locationReference, String actorAccountReference, String actorRole,
            String actionCode, String actionScope, String reason, String outcome, String operationReference,
            String correlationReference, String idempotencyKeyHash, String requestFingerprint,
            Long sourceVersion) {
        return new OtaReconciliationDecision(workItem, mapping, providerCode, locationReference,
                actorAccountReference, actorRole, actionCode, actionScope, reason, outcome,
                operationReference, correlationReference, idempotencyKeyHash, requestFingerprint, sourceVersion);
    }

    @PrePersist
    void onCreate() {
        if (recordedAt == null) recordedAt = Instant.now();
    }

    private static String required(String value, int maxLength, String message) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
