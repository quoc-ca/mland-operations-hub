package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Relation between one Member and one voucher programme (unique per programme and member). It carries the
 * per-member quota counters and, when set, the time the Member saved the voucher. Saving is only a shortcut:
 * it holds no quota, grants no entitlement and is idempotent. Counter rows are created under the programme
 * lock; {@code held + consumed <= current member limit}.
 */
@Entity
@Table(name = "promotion_member_program")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberProgram {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "program_id", nullable = false, updatable = false)
    private Long programId;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "saved_at")
    private Instant savedAt;

    @Column(name = "held_count", nullable = false)
    private long heldCount;

    @Column(name = "consumed_count", nullable = false)
    private long consumedCount;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public static MemberProgram create(long programId, long memberId) {
        MemberProgram row = new MemberProgram();
        row.programId = programId;
        row.memberId = memberId;
        return row;
    }

    /** Idempotent: the first save time is kept, counters are never touched. */
    public void save(Instant at) {
        if (savedAt == null) {
            savedAt = at;
        }
    }

    public boolean isSaved() {
        return savedAt != null;
    }

    /** One slot moves to held for this Member if the current per-member limit allows it. */
    public void hold(long memberLimit) {
        if (heldCount + consumedCount >= memberLimit) {
            throw new LoyaltyException(LoyaltyErrorCode.QUOTA_LIMIT_CONFLICT,
                    "The per-member limit of this voucher is reached.");
        }
        heldCount++;
    }

    public void consume() {
        requireHeld();
        heldCount--;
        consumedCount++;
    }

    public void release() {
        requireHeld();
        heldCount--;
    }

    public void restore() {
        if (consumedCount < 1) {
            throw new LoyaltyException(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT, "No consumed use to restore.");
        }
        consumedCount--;
    }

    private void requireHeld() {
        if (heldCount < 1) {
            throw new LoyaltyException(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT, "No held use to change.");
        }
    }
}
