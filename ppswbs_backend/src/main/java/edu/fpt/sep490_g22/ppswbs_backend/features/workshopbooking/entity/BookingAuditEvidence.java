package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "booking_audit_evidence")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingAuditEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_code", nullable = false, length = 32)
    private String bookingCode;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "actor_type", nullable = false, length = 32)
    private String actorType;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "outcome", nullable = false, length = 32)
    private String outcome;

    @Column(name = "correlation_reference", nullable = false, length = 64)
    private String correlationReference;

    @Column(name = "manager_reason", length = 1000)
    private String managerReason;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @PrePersist
    protected void onCreate() {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}
