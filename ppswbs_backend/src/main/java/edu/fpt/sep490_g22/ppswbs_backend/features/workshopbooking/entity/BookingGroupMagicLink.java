package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** One Magic Link per confirmed booking group, so the buyer reaches every ticket of the group (FR-042). */
@Entity
@Table(name = "booking_group_magic_links")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingGroupMagicLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, updatable = false)
    private BookingGroup group;

    /** Only the SHA-256 hash is stored; the raw value lives in the delivered link alone. */
    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 16)
    private MagicLinkLifecycleStatus lifecycleStatus;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (lifecycleStatus == null) lifecycleStatus = MagicLinkLifecycleStatus.ACTIVE;
    }
}
