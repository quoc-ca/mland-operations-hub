package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A workshop package offered to customers.
 * Owned by the workshopbooking module; configuration changes are deferred.
 */
@Entity
@Table(name = "workshop_packages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkshopPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", length = 4000)
    private String description;

    /** Price in smallest currency unit (e.g. VND dong, no decimal). */
    @Column(name = "price", nullable = false)
    private Long price;

    /** ISO 4217 currency code, e.g. VND. */
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /** Deposit percentage baseline (e.g. 50 = 50%). */
    @Column(name = "deposit_percent", nullable = false)
    private Integer depositPercent;

    /** Minimum number of participants. */
    @Column(name = "min_participants", nullable = false)
    private Integer minParticipants;

    /** Maximum number of participants. */
    @Column(name = "max_participants", nullable = false)
    private Integer maxParticipants;

    /** Additional supported design/material notes. */
    @Column(name = "supported_options", length = 2000)
    private String supportedOptions;

    /** Only PUBLISHED packages are visible/bookable by customers. */
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
        if (this.status == null) {
            this.status = "DRAFT";
        }
        if (this.depositPercent == null) {
            this.depositPercent = 50;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public boolean isPublished() {
        return "PUBLISHED".equals(this.status);
    }
}
