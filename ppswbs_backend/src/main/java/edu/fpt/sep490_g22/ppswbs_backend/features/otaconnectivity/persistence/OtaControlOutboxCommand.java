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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** OTA-owned control command; deliberately independent of Operations source versions. */
@Entity
@Table(name = "ota_control_outbox", uniqueConstraints = {
        @UniqueConstraint(name = "uq_ota_control_operation_reference", columnNames = "operation_reference"),
        @UniqueConstraint(name = "uq_ota_control_actor_idempotency", columnNames = {"actor_account_reference", "idempotency_key_hash"})
})
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class OtaControlOutboxCommand {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operation_reference", nullable = false, length = 36, updatable = false)
    private String operationReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mapping_id", nullable = false, updatable = false)
    private OtaChannelMapping mapping;

    @Column(name = "command_kind", nullable = false, length = 32, updatable = false)
    private String commandKind;

    @Column(name = "actor_account_reference", nullable = false, length = 128, updatable = false)
    private String actorAccountReference;

    @Column(name = "reason", nullable = false, length = 1000, updatable = false)
    private String reason;

    @Column(name = "idempotency_key_hash", nullable = false, length = 64, updatable = false)
    private String idempotencyKeyHash;

    @Column(name = "request_fingerprint", nullable = false, length = 64, updatable = false)
    private String requestFingerprint;

    @Column(name = "correlation_reference", nullable = false, length = 36, updatable = false)
    private String correlationReference;

    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Column(name = "provider_correlation_reference", length = 255)
    private String providerCorrelationReference;

    @Column(name = "failure_category", length = 64)
    private String failureCategory;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    private OtaControlOutboxCommand(String operationReference, OtaChannelMapping mapping, String commandKind,
                                    String actorAccountReference, String reason, String idempotencyKeyHash,
                                    String requestFingerprint, String correlationReference) {
        this.operationReference = required(operationReference, 36);
        this.mapping = mapping;
        this.commandKind = required(commandKind, 32);
        this.actorAccountReference = required(actorAccountReference, 128);
        this.reason = required(reason, 1000);
        this.idempotencyKeyHash = required(idempotencyKeyHash, 64);
        this.requestFingerprint = required(requestFingerprint, 64);
        this.correlationReference = required(correlationReference, 36);
        this.status = "QUEUED";
    }

    public static OtaControlOutboxCommand salesClose(String operationReference, OtaChannelMapping mapping,
                                                      String actorAccountReference, String reason,
                                                      String idempotencyKeyHash, String requestFingerprint,
                                                      String correlationReference) {
        if (mapping == null) throw new IllegalArgumentException("A mapping is required");
        return new OtaControlOutboxCommand(operationReference, mapping, "SALES_CLOSE", actorAccountReference,
                reason, idempotencyKeyHash, requestFingerprint, correlationReference);
    }

    public void recordDelivered(String providerCorrelationReference) {
        if (!"QUEUED".equals(status)) throw new IllegalStateException("Only a queued control command can be delivered");
        this.status = "DELIVERED";
        this.providerCorrelationReference = optional(providerCorrelationReference, 255);
        this.deliveredAt = Instant.now();
    }

    public void recordExhausted(String failureCategory, String providerCorrelationReference) {
        if (!"QUEUED".equals(status)) throw new IllegalStateException("Only a queued control command can be exhausted");
        this.status = "EXHAUSTED";
        this.failureCategory = optional(failureCategory, 64);
        this.providerCorrelationReference = optional(providerCorrelationReference, 255);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    private static String required(String value, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new IllegalArgumentException("Control command audit field is invalid");
        }
        return value.trim();
    }

    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        if (value.trim().length() > max) throw new IllegalArgumentException("Provider reference is invalid");
        return value.trim();
    }
}
