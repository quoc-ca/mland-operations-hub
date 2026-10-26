package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workshop_branches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkshopBranch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", nullable = false, unique = true, length = 36)
    private String reference;

    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "address_snapshot", nullable = false, length = 1000)
    private String addressSnapshot;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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
