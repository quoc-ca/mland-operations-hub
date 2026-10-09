package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Magic Link for post-payment read-only booking view.
 * - Token is stored as a hash only; raw token is never persisted.
 * - Valid until the associated workshop session ends.
 * - Revocable; revocation does not change payment evidence.
 * - Reuse prevention: only one active link per booking.
 */
@Entity
@Table(name = "booking_magic_links",
        uniqueConstraints = @UniqueConstraint(name = "uk_magic_link_booking", columnNames = "booking_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingMagicLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false, unique = true)
    private Long bookingId;

    /** SHA-256 hash of the raw token. Raw token is never stored. */
    @Column(name = "token_hash", nullable = false, length = 128)
    private String tokenHash;

    /** Expires when the associated workshop session ends. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Explicitly revoked flag. Revocation does not affect payment evidence. */
    @Column(name = "revoked", nullable = false)
    private boolean revoked;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        if (!this.revoked) {
            this.revoked = false;
        }
    }

    public boolean isValid() {
        return !revoked && Instant.now().isBefore(expiresAt);
    }
}
