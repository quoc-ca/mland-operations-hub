package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "settlement_final_bills")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SettlementFinalBill {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="bill_reference", nullable=false, unique=true, length=128) private String billReference;
    @Column(name="booking_reference", nullable=false, unique=true, length=128) private String bookingReference;
    /** The booking group a ticket folio belongs to; null for a single direct booking (003.3 FR-030). */
    @Column(name="group_reference", length=128) private String groupReference;
    @Column(name="post_promotion_amount_vnd", nullable=false) private Long postPromotionAmountVnd;
    @Column(name="accepted_deposit_amount_vnd", nullable=false) private Long acceptedDepositAmountVnd;
    @Column(name="remaining_amount_vnd", nullable=false) private Long remainingAmountVnd;
    @Column(name="currency_code", nullable=false, length=3) private String currencyCode;
    @Column(name="lifecycle_status", nullable=false, length=24) private String lifecycleStatus;
    @Column(name="frozen_at", nullable=false, updatable=false) private Instant frozenAt;
    /** Set once the Member Loyalty outcome for this folio is decided (granted, finished, or not applicable). */
    @Column(name="loyalty_processed_at") private Instant loyaltyProcessedAt;
    /** FR-035: the Manager-only exception that let the product leave before the money was proven. */
    @Column(name="handover_approved_by_account_id") private Long handoverApprovedByAccountId;
    @Column(name="handover_reason", length=1000) private String handoverReason;
    @Column(name="handover_approved_at") private Instant handoverApprovedAt;
    /** FR-035: a real debt, recorded and closed by a Manager only; no reminder, deadline or booking block. */
    @Column(name="debt_amount_vnd") private Long debtAmountVnd;
    @Column(name="debt_reason", length=1000) private String debtReason;
    @Column(name="debt_recorded_by_account_id") private Long debtRecordedByAccountId;
    @Column(name="debt_recorded_at") private Instant debtRecordedAt;
    @Column(name="debt_closed_by_account_id") private Long debtClosedByAccountId;
    @Column(name="debt_closed_at") private Instant debtClosedAt;
    @Column(name="created_at", nullable=false, updatable=false) private Instant createdAt;

    @PrePersist
    void onCreate() {
        requireKnownLifecycleStatus();
        if (createdAt == null) createdAt = Instant.now();
        if (frozenAt == null) frozenAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        requireKnownLifecycleStatus();
    }

    void requireKnownLifecycleStatus() {
        if (!SettlementFinalBillLifecycleStatus.isKnown(lifecycleStatus)) {
            throw new IllegalStateException("SETTLEMENT_FINAL_BILL_STATUS_UNSUPPORTED");
        }
    }
}
