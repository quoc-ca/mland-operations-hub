package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "booking_capacity_holds", uniqueConstraints = {
    @UniqueConstraint(name = "uq_bch_hold_reference", columnNames = {"hold_reference"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingCapacityHold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private WorkshopBooking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_commitment_id", nullable = false)
    private BookingSessionCommitment sessionCommitment;

    @Column(name = "hold_reference", nullable = false, length = 64)
    private String holdReference;

    @Column(name = "hold_status", nullable = false, length = 32)
    private String holdStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (holdStatus == null) {
            holdStatus = "RESERVED";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
