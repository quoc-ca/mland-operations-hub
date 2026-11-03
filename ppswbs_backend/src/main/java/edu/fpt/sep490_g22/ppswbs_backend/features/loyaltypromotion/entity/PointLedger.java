package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.time.Instant;

/**
 * Append-only ledger. Unique per accepted benefit and entry kind, which is what stops a hold, consume,
 * release, restore or earn from being posted twice. Fixed deltas: HOLD (0, +P), CONSUME (-P, -P),
 * RELEASE (0, -P), RESTORE (+P, 0, linked to the original debit), EARN (+E, 0). A zero earn is recorded
 * on the accepted benefit only, never as a ledger row.
 */
@Entity
@Table(name = "loyalty_point_ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "accepted_benefit_id", nullable = false, updatable = false)
    private Long acceptedBenefitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_kind", nullable = false, updatable = false, length = 10)
    private LedgerEntryKind entryKind;

    @Column(name = "delta_balance", nullable = false, updatable = false, precision = 19, scale = 0)
    private BigInteger deltaBalance;

    @Column(name = "delta_held", nullable = false, updatable = false, precision = 19, scale = 0)
    private BigInteger deltaHeld;

    @Column(name = "balance_after", nullable = false, updatable = false, precision = 19, scale = 0)
    private BigInteger balanceAfter;

    @Column(name = "held_after", nullable = false, updatable = false, precision = 19, scale = 0)
    private BigInteger heldAfter;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    /** Only RESTORE: the CONSUME entry whose points are being returned. */
    @Column(name = "original_debit_id", updatable = false)
    private Long originalDebitId;

    /**
     * Builds the entry for a wallet that has already been updated; the caller passes the wallet balances after
     * the change. {@code points} must be greater than zero.
     */
    public static PointLedger entry(LedgerEntryKind kind, long memberId, long acceptedBenefitId, BigInteger points,
                                    BigInteger balanceAfter, BigInteger heldAfter, Instant occurredAt,
                                    Long originalDebitId) {
        if (points == null || points.signum() <= 0) {
            throw new IllegalArgumentException("A ledger entry needs a positive amount.");
        }
        if (kind == LedgerEntryKind.RESTORE && originalDebitId == null) {
            throw new IllegalArgumentException("A restore entry must reference the original debit.");
        }
        if (kind != LedgerEntryKind.RESTORE && originalDebitId != null) {
            throw new IllegalArgumentException("Only a restore entry references an original debit.");
        }
        PointLedger entry = new PointLedger();
        entry.entryKind = kind;
        entry.memberId = memberId;
        entry.acceptedBenefitId = acceptedBenefitId;
        entry.deltaBalance = switch (kind) {
            case HOLD, RELEASE -> BigInteger.ZERO;
            case CONSUME -> points.negate();
            case RESTORE, EARN -> points;
        };
        entry.deltaHeld = switch (kind) {
            case HOLD -> points;
            case CONSUME, RELEASE -> points.negate();
            case RESTORE, EARN -> BigInteger.ZERO;
        };
        entry.balanceAfter = balanceAfter;
        entry.heldAfter = heldAfter;
        entry.occurredAt = occurredAt;
        entry.originalDebitId = originalDebitId;
        return entry;
    }
}
