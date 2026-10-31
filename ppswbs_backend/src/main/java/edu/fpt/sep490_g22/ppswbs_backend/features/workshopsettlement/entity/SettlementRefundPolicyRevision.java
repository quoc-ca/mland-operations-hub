package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "settlement_refund_policy_revisions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementRefundPolicyRevision {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_reference", nullable = false, unique = true, length = 128)
    private String policyReference;

    @Column(name = "effective_at", nullable = false)
    private Instant effectiveAt;

    @Column(name = "whole_booking_refund_percent", nullable = false)
    private Integer wholeBookingRefundPercent;

    @Column(name = "created_by_account_id", nullable = false)
    private Long createdByAccountId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
