package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * An undercharge Mland absorbs on a FINALIZED ticket folio (003.3 FR-057, T135). Documentation only: it never
 * changes the folio. Immutable except the single void.
 */
@Entity
@Table(name = "settlement_absorbed_undercharges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementAbsorbedUndercharge {

    public static final String ACTIVE = "ACTIVE";
    public static final String VOIDED = "VOIDED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "undercharge_reference", nullable = false, unique = true, length = 128, updatable = false)
    private String underchargeReference;

    @Column(name = "ticket_code", nullable = false, length = 128, updatable = false)
    private String ticketCode;

    @Column(name = "bill_reference", nullable = false, length = 128, updatable = false)
    private String billReference;

    /** Canonical workshop_location.id of the ticket (same meaning as V153). */
    @Column(name = "location_reference", nullable = false, updatable = false)
    private Long locationReference;

    @Column(name = "amount_vnd", nullable = false, updatable = false)
    private long amountVnd;

    @Column(name = "currency_code", nullable = false, length = 3, updatable = false)
    private String currencyCode;

    @Column(name = "reason", nullable = false, length = 1000, updatable = false)
    private String reason;

    @Column(name = "recorded_by_account_id", nullable = false, updatable = false)
    private Long recordedByAccountId;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "idempotency_key", nullable = false, length = 128, updatable = false)
    private String idempotencyKey;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "void_reason", length = 1000)
    private String voidReason;

    @Column(name = "voided_by_account_id")
    private Long voidedByAccountId;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "void_idempotency_key", length = 128)
    private String voidIdempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (recordedAt == null) recordedAt = now;
        if (currencyCode == null) currencyCode = "VND";
        if (status == null) status = ACTIVE;
    }

    public boolean isVoided() {
        return VOIDED.equals(status);
    }
}
