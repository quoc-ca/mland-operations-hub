package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Notification intent for post-payment confirmation emails.
 * Failure to deliver does not roll back booking confirmation.
 * Retry is capped at 3 attempts with backoff.
 * No raw email content, raw token or provider payload is stored here.
 */
@Entity
@Table(name = "notification_intents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationIntent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    /** CONFIRMATION, RESEND */
    @Column(name = "intent_type", nullable = false, length = 64)
    private String intentType;

    /** Contact email snapshot from booking (not logged). */
    @Column(name = "recipient_email_snapshot", nullable = false, length = 255)
    private String recipientEmailSnapshot;

    /** PENDING, SENT, FAILED, EXHAUSTED */
    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "last_attempted_at")
    private Instant lastAttemptedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = "PENDING";
        }
        if (this.attemptCount == null) {
            this.attemptCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
