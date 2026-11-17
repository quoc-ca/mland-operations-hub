package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "workshop_addons")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WorkshopAddOn {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", nullable = false, unique = true, length = 36)
    private String reference;

    @Column(name = "package_id", nullable = false)
    private Long packageId;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "description", nullable = false, length = 200)
    private String description;

    @Column(name = "price_vnd", nullable = false)
    private Long priceVnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    private WorkshopAddOnState state;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
}
