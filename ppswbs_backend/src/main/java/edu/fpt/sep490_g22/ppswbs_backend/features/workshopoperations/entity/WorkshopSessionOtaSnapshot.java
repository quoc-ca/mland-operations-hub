package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "workshop_session_ota_snapshots",
        uniqueConstraints = @UniqueConstraint(name = "uq_ota_snapshot_session_version",
                columnNames = {"session_reference", "source_version"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkshopSessionOtaSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_reference", nullable = false, length = 36)
    private String sessionReference;

    @Column(name = "location_reference", nullable = false, length = 64)
    private String locationReference;

    @Column(name = "scheduled_start_at", nullable = false)
    private Instant scheduledStart;

    @Column(name = "scheduled_end_at", nullable = false)
    private Instant scheduledEnd;

    @Column(name = "sellable_availability", nullable = false)
    private Integer sellableAvailability;

    @Column(name = "sale_state", nullable = false, length = 32)
    private String saleState;

    @Column(name = "source_version", nullable = false)
    private Long sourceVersion;

    @Column(name = "change_reference", nullable = false, length = 64)
    private String changeReference;

    @Column(name = "effective_at", nullable = false)
    private Instant effectiveAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
