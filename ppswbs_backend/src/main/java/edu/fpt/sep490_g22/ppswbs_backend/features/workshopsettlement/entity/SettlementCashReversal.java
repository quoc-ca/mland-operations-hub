package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A Manager's negative cash entry that corrects a CASH collection of a closed day (003.3 FR-036, FR-053, T144).
 * Immutable: the closed day and the original collection are never edited.
 */
@Entity
@Table(name = "settlement_cash_reversals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementCashReversal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reversal_reference", nullable = false, unique = true, length = 128, updatable = false)
    private String reversalReference;

    @Column(name = "ticket_code", nullable = false, length = 128, updatable = false)
    private String ticketCode;

    @Column(name = "bill_reference", nullable = false, length = 128, updatable = false)
    private String billReference;

    /** The reversed collection; unique, so one collection is reversed once. */
    @Column(name = "collection_id", nullable = false, updatable = false)
    private Long collectionId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    /** Minus the collection's applied amount (always below zero). */
    @Column(name = "amount_vnd", nullable = false, updatable = false)
    private long amountVnd;

    /** The Vietnam working day this entry counts on (the day after a close once the count was submitted). */
    @Column(name = "cash_business_day", nullable = false, updatable = false)
    private LocalDate cashBusinessDay;

    @Column(name = "original_cash_business_day", nullable = false, updatable = false)
    private LocalDate originalCashBusinessDay;

    @Column(name = "reason", nullable = false, length = 1000, updatable = false)
    private String reason;

    @Column(name = "recorded_by_account_id", nullable = false, updatable = false)
    private Long recordedByAccountId;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "idempotency_key", nullable = false, length = 128, updatable = false)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (recordedAt == null) recordedAt = now;
    }
}
