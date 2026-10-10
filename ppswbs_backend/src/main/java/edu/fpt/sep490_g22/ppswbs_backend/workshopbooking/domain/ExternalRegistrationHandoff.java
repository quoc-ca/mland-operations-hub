package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Durable record for post-confirmation handoff to the External Registration System.
 * Processing this record is deferred to a future synchronization feature;
 * it must never block local booking confirmation.
 * Retry metadata is auditable and redacted of provider secrets.
 */
@Entity
@Table(name = "external_registration_handoffs",
        uniqueConstraints = @UniqueConstraint(name = "uk_handoff_booking_event", columnNames = {"booking_id", "event_identity"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExternalRegistrationHandoff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "booking_code", nullable = false, length = 64)
    private String bookingCode;

    /** Unique event identity for idempotency. */
    @Column(name = "event_identity", nullable = false, length = 128)
    private String eventIdentity;

    /** Safe payload reference (no provider secrets). */
    @Column(name = "payload_reference", length = 512)
    private String payloadReference;

    /** PENDING, PROCESSING, DONE, FAILED */
    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

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
