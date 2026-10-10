package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Participant capacity hold for a PENDING_PAYMENT booking.
 * <p>
 * A hold reserves participant_count slots for a session until:
 * - The booking is confirmed (hold becomes committed capacity).
 * - The hold expires after 15 minutes.
 * - The customer explicitly releases the hold (CANCELLED).
 * <p>
 * Each hold is released at most once; release_generation prevents double-release.
 */
@Entity
@Table(name = "capacity_holds",
        uniqueConstraints = @UniqueConstraint(name = "uk_hold_booking", columnNames = "booking_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CapacityHold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false, unique = true)
    private Long bookingId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "participant_count", nullable = false)
    private Integer participantCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 15-minute expiry deadline from creation. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * Monotonically increasing generation; when released, this is set to a non-zero sentinel.
     * Prevents a hold from being released twice (idempotent guard).
     */
    @Column(name = "release_generation", nullable = false)
    private Integer releaseGeneration;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "release_reason", length = 64)
    private String releaseReason;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        if (this.releaseGeneration == null) {
            this.releaseGeneration = 0;
        }
    }

    public boolean isActive() {
        return this.releaseGeneration == 0 && Instant.now().isBefore(this.expiresAt);
    }

    public boolean isExpired() {
        return this.releaseGeneration == 0 && !Instant.now().isBefore(this.expiresAt);
    }

    public boolean isReleased() {
        return this.releaseGeneration > 0;
    }
}
