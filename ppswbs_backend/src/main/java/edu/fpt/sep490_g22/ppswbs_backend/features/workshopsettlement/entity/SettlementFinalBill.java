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
    @Column(name="post_promotion_amount_vnd", nullable=false) private Long postPromotionAmountVnd;
    @Column(name="accepted_deposit_amount_vnd", nullable=false) private Long acceptedDepositAmountVnd;
    @Column(name="remaining_amount_vnd", nullable=false) private Long remainingAmountVnd;
    @Column(name="currency_code", nullable=false, length=3) private String currencyCode;
    @Column(name="lifecycle_status", nullable=false, length=24) private String lifecycleStatus;
    @Column(name="frozen_at", nullable=false, updatable=false) private Instant frozenAt;
    @Column(name="created_at", nullable=false, updatable=false) private Instant createdAt;
    @PrePersist void onCreate() { if (createdAt == null) createdAt=Instant.now(); if (frozenAt == null) frozenAt=createdAt; }
}
