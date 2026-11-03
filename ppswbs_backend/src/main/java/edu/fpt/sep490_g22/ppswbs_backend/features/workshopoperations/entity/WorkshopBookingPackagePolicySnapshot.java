package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "workshop_booking_package_policy_snapshots", uniqueConstraints =
        @UniqueConstraint(name = "uq_workshop_booking_policy_booking", columnNames = "booking_reference"))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkshopBookingPackagePolicySnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_reference", nullable = false, updatable = false, length = 64)
    private String bookingReference;

    @Column(name = "package_reference", nullable = false, updatable = false, length = 64)
    private String packageReference;

    @Column(name = "required_session_count", nullable = false, updatable = false)
    private Integer requiredSessionCount;

    @Column(name = "min_participants", nullable = false, updatable = false)
    private Integer minParticipants;

    @Column(name = "max_participants", nullable = false, updatable = false)
    private Integer maxParticipants;

    @Column(name = "same_location_required", nullable = false, updatable = false)
    private boolean sameLocationRequired;

    @Column(name = "minimum_same_day_slot_gap", nullable = false, updatable = false)
    private Integer minimumSameDaySlotGap;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
