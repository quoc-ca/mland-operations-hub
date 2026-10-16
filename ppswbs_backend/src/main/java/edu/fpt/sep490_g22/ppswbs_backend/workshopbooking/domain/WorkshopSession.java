package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A specific dated session slot for a workshop package.
 * Tracks participant capacity; availability is based on participant counts, not booking-row count.
 */
@Entity
@Table(name = "workshop_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkshopSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "package_id", nullable = false)
    private Long packageId;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "location", length = 512)
    private String location;

    /** Total participant capacity for this session. */
    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    /** Sum of participant_count across PENDING_PAYMENT and CONFIRMED bookings. */
    @Column(name = "reserved_participants", nullable = false)
    private Integer reservedParticipants;

    /** Session status: OPEN, FULL, CANCELLED. */
    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.reservedParticipants == null) {
            this.reservedParticipants = 0;
        }
        if (this.status == null) {
            this.status = "OPEN";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public int getAvailableCapacity() {
        return this.capacity - this.reservedParticipants;
    }

    public boolean isOpen() {
        return "OPEN".equals(this.status);
    }
}
