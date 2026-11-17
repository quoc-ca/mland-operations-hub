package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/** Stable FE-08 lock row for one canonical FE-02 variant at one branch (FR-053, T124). */
@Entity
@Table(name = "retail_branch_availability_guards")
@IdClass(RetailBranchAvailabilityGuard.Key.class)
@Getter
@NoArgsConstructor
public class RetailBranchAvailabilityGuard {

    @Id
    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Id
    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void touch() {
        updatedAt = Instant.now();
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private Long variantId;
        private Long locationId;
    }
}
