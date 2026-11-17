package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** One single-use link that collects the recipient account of a refund (003.3 FR-040). */
@Entity
@Table(name = "settlement_refund_account_links")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementRefundAccountLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, updatable = false)
    private Long requestId;

    /** Only the SHA-256 hash is stored; the raw value lives in the delivered link alone. */
    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 16)
    private RefundAccountLinkStatus lifecycleStatus;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (lifecycleStatus == null) lifecycleStatus = RefundAccountLinkStatus.ACTIVE;
    }

    public boolean isUsable(Instant now) {
        return lifecycleStatus == RefundAccountLinkStatus.ACTIVE && expiresAt.isAfter(now);
    }
}
