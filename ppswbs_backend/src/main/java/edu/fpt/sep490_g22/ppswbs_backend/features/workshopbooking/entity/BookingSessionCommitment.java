package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "booking_session_commitments", uniqueConstraints = {
    @UniqueConstraint(name = "uq_bsc_booking_ordinal", columnNames = {"booking_id", "ordinal"}),
    @UniqueConstraint(name = "uq_bsc_booking_session", columnNames = {"booking_id", "session_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingSessionCommitment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private WorkshopBooking booking;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "ordinal", nullable = false)
    private Integer ordinal;

    @Column(name = "seat_count", nullable = false)
    private Integer seatCount;

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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
