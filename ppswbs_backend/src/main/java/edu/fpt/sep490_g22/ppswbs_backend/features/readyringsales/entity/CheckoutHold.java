package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "retail_checkout_holds")
@Getter
@NoArgsConstructor
public class CheckoutHold {
    @Id @Column(length = 36) private String id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "grouped_checkout_id", nullable = false, unique = true) private GroupedCheckout groupedCheckout;
    @Column(nullable = false, length = 32) private String status;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    public CheckoutHold(GroupedCheckout groupedCheckout, Instant expiresAt) { this.groupedCheckout = groupedCheckout; this.status = "LOCAL_HELD"; this.expiresAt = expiresAt; }
    public void expireLocalDemo() { this.status = "LOCAL_EXPIRED"; }
    @PrePersist void assignId() { if (id == null) id = UUID.randomUUID().toString(); }
}
