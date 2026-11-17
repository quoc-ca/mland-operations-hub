package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One held quantity of a canonical variant at one branch under an accepted grouped checkout (FR-053, T124).
 * {@link #UNATTRIBUTED_LOCATION} marks a legacy size-level hold that is not tied to a branch; it is counted at
 * every branch of its size so a branch never looks more available than it is.
 */
@Entity
@Table(name = "retail_variant_holds")
@Getter
@NoArgsConstructor
public class RetailVariantHold {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grouped_checkout_id", nullable = false)
    private GroupedCheckout groupedCheckout;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RetailVariantHoldStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Location id of a legacy size-level hold (no branch). */
    public static final long UNATTRIBUTED_LOCATION = 0L;

    public RetailVariantHold(GroupedCheckout groupedCheckout, Long variantId, Long locationId, int quantity, Instant expiresAt) {
        this.groupedCheckout = groupedCheckout;
        this.variantId = variantId;
        this.locationId = locationId;
        this.quantity = quantity;
        this.status = RetailVariantHoldStatus.HELD;
        this.expiresAt = expiresAt;
    }

    /** Legacy size-level hold kept for callers that still lock by size only. */
    public RetailVariantHold(GroupedCheckout groupedCheckout, Long variantId, int quantity, Instant expiresAt) {
        this(groupedCheckout, variantId, UNATTRIBUTED_LOCATION, quantity, expiresAt);
    }

    public boolean isBranchAttributed() { return locationId != null && locationId != UNATTRIBUTED_LOCATION; }

    @PrePersist
    void assignId() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }

    public void settle() { status = RetailVariantHoldStatus.SETTLED; }
    public void release() { status = RetailVariantHoldStatus.RELEASED; }
    public void expire() { status = RetailVariantHoldStatus.EXPIRED; }
}
