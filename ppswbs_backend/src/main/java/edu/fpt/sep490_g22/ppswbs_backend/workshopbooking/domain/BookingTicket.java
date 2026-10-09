package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * QR Check-in ticket issued after booking confirmation.
 * - Issued only once per confirmed booking; resend reuses the same active ticket.
 * - QR value is a signed opaque reference only — no PII, payment data, or raw token.
 * - Valid until the associated workshop session ends; can be explicitly revoked.
 */
@Entity
@Table(name = "booking_tickets",
        uniqueConstraints = @UniqueConstraint(name = "uk_ticket_booking", columnNames = "booking_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false, unique = true)
    private Long bookingId;

    /** Signed opaque QR token/reference. Must not contain PII, payment data, or Magic Link. */
    @Column(name = "qr_value", nullable = false, length = 512)
    private String qrValue;

    /** Ticket validity end — equals the associated workshop session end time. */
    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;

    /** Whether ticket has been explicitly revoked. */
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
        return !revoked && Instant.now().isBefore(validUntil);
    }
}
