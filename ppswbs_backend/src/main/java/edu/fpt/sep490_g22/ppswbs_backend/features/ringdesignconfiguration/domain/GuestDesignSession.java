package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "guest_design_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuestDesignSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_reference", nullable = false, unique = true, length = 128)
    private String sessionReference;

    @Column(name = "session_digest", nullable = false, length = 128)
    private String sessionDigest;

    @Column(name = "design_reference", length = 64)
    private String designReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private GuestSessionStatus status = GuestSessionStatus.ACTIVE;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (issuedAt == null) issuedAt = now;
        if (lastActivityAt == null) lastActivityAt = now;
        if (expiresAt == null) expiresAt = now.plusSeconds(20 * 60); // 20 minutes inactivity expiry
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public boolean isExpired(Instant now) {
        return this.status != GuestSessionStatus.ACTIVE || now.isAfter(this.expiresAt);
    }

    public void touch(Instant now) {
        this.lastActivityAt = now;
        this.expiresAt = now.plusSeconds(20 * 60);
    }
}

