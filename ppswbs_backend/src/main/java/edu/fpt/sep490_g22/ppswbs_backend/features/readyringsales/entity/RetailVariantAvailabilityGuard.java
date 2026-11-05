package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Stable FE-08 lock row for one canonical FE-02 variant. */
@Entity
@Table(name = "retail_variant_availability_guards")
@Getter
@NoArgsConstructor
public class RetailVariantAvailabilityGuard {

    @Id
    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void touch() {
        updatedAt = Instant.now();
    }
}
