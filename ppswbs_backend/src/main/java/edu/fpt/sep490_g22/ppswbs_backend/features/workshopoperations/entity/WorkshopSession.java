package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
    name = "workshop_sessions",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_session_location_date_slot", columnNames = {"location_id", "workshop_date", "slot_id"}),
        @UniqueConstraint(name = "uq_session_reference", columnNames = {"reference"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkshopSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", nullable = false, unique = true, length = 36)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private WorkshopLocation location;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slot_id", nullable = false)
    private WorkshopSlot slot;

    @Column(name = "workshop_date", nullable = false)
    private LocalDate workshopDate;

    @Column(name = "scheduled_start_at", nullable = false)
    private Instant scheduledStartAt;

    @Column(name = "scheduled_end_at", nullable = false)
    private Instant scheduledEndAt;

    @Column(name = "check_in_opens_at", nullable = false)
    private Instant checkInOpensAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_in_opening_source", nullable = false, length = 32)
    @Builder.Default
    private CheckInOpeningSource checkInOpeningSource = CheckInOpeningSource.DEFAULT;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 32)
    @Builder.Default
    private SessionState state = SessionState.DRAFT;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(name = "cancellation_handoff_reference", length = 128)
    private String cancellationHandoffReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "attendance_finalized_at")
    private Instant attendanceFinalizedAt;

    @PrePersist
    protected void onCreate() {
        if (reference == null) {
            reference = UUID.randomUUID().toString();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
