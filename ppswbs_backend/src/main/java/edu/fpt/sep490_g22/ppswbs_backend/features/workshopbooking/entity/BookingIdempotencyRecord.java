package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "booking_idempotency_records", uniqueConstraints = {
    @UniqueConstraint(name = "uq_bir_key_action", columnNames = {"idempotency_key", "command_action"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingIdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "command_action", nullable = false, length = 80)
    private String commandAction;

    @Column(name = "correlation_reference", nullable = false, length = 64)
    private String correlationReference;

    @Column(name = "semantic_fingerprint", nullable = false, length = 255)
    private String semanticFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 32)
    private BookingIdempotencyLifecycleStatus lifecycleStatus;

    @Column(name = "claim_version", nullable = false)
    private Long claimVersion;

    @Column(name = "lease_expires_at", nullable = false)
    private Instant leaseExpiresAt;

    @Column(name = "claimed_at", nullable = false)
    private Instant claimedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "response_payload", columnDefinition = "json")
    private String responsePayload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (claimedAt == null) {
            claimedAt = now;
        }
        if (claimVersion == null) {
            claimVersion = 1L;
        }
        if (lifecycleStatus == null) {
            lifecycleStatus = BookingIdempotencyLifecycleStatus.IN_PROGRESS;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
