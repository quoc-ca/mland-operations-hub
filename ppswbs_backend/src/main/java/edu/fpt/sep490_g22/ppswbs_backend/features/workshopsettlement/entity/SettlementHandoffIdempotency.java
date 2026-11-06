package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "settlement_handoff_idempotency")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementHandoffIdempotency {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operation_reference", nullable = false, unique = true, length = 128)
    private String operationReference;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 24)
    @Builder.Default
    private HandoffIdempotencyStatus lifecycleStatus = HandoffIdempotencyStatus.IN_PROGRESS;

    @Column(name = "cancellation_case_reference", length = 128)
    private String cancellationCaseReference;

    @Column(name = "rejection_reason", length = 255)
    private String rejectionReason;

    @Column(name = "correlation_reference", nullable = false, length = 128)
    private String correlationReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
}
