package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * A voucher or automatic discount. Kind, scope and code never change after creation; the code key is unique
 * across all history and is never reused. Quota counters (voucher only) live on this row and change only
 * under the programme lock: {@code held + consumed <= totalLimit}. Expiry is derived from the current
 * revision window; only ENABLED/PAUSED is stored.
 */
@Entity
@Table(name = "promotion_program")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PromotionProgram {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false, length = 20)
    private ProgramKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, updatable = false, length = 20)
    private TargetScope scope;

    /** Trimmed code as the Manager typed it; {@code null} for an automatic discount. */
    @Column(name = "code_original", updatable = false, length = 128)
    private String codeOriginal;

    /** Upper-cased comparison key, binary-collated unique column; {@code null} for an automatic discount. */
    @Column(name = "code_key", updatable = false, length = 128)
    private String codeKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ProgramStatus status;

    @Column(name = "current_revision_id")
    private Long currentRevisionId;

    @Column(name = "total_limit")
    private Long totalLimit;

    @Column(name = "member_limit")
    private Long memberLimit;

    @Column(name = "held_count")
    private Long heldCount;

    @Column(name = "consumed_count")
    private Long consumedCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public static PromotionProgram voucher(TargetScope scope, String codeOriginal, String codeKey, long totalLimit,
                                           long memberLimit, Instant createdAt, long createdBy) {
        requirePositiveLimits(totalLimit, memberLimit);
        PromotionProgram program = base(ProgramKind.VOUCHER, scope, createdAt, createdBy);
        program.codeOriginal = codeOriginal;
        program.codeKey = codeKey;
        program.totalLimit = totalLimit;
        program.memberLimit = memberLimit;
        program.heldCount = 0L;
        program.consumedCount = 0L;
        return program;
    }

    /** Automatic discounts are product-only, have no code and no quota. */
    public static PromotionProgram autoDiscount(Instant createdAt, long createdBy) {
        return base(ProgramKind.AUTO_DISCOUNT, TargetScope.PRODUCT, createdAt, createdBy);
    }

    private static PromotionProgram base(ProgramKind kind, TargetScope scope, Instant createdAt, long createdBy) {
        PromotionProgram program = new PromotionProgram();
        program.kind = kind;
        program.scope = scope;
        program.status = ProgramStatus.ENABLED;
        program.createdAt = createdAt;
        program.createdBy = createdBy;
        return program;
    }

    public boolean isVoucher() {
        return kind == ProgramKind.VOUCHER;
    }

    public void pointToRevision(long revisionId) {
        this.currentRevisionId = revisionId;
    }

    /** ENABLED to PAUSED. Existing holds and accepted snapshots are untouched. */
    public void pause() {
        requireState(ProgramStatus.ENABLED);
        status = ProgramStatus.PAUSED;
    }

    /** PAUSED to ENABLED on the same programme and code. Quotas, history and window are not changed. */
    public void resume() {
        requireState(ProgramStatus.PAUSED);
        status = ProgramStatus.ENABLED;
    }

    /**
     * Changes the limits for new transactions. The total can never drop below {@code held + consumed};
     * the per-member limit is checked against every member count by the caller under the programme lock.
     */
    public void changeLimits(long newTotalLimit, long newMemberLimit) {
        requireVoucher();
        requirePositiveLimits(newTotalLimit, newMemberLimit);
        if (newTotalLimit < heldCount + consumedCount) {
            throw new LoyaltyException(LoyaltyErrorCode.QUOTA_LIMIT_CONFLICT,
                    "The limit cannot be lower than the amount already used or held.");
        }
        totalLimit = newTotalLimit;
        memberLimit = newMemberLimit;
    }

    public long availableTotal() {
        requireVoucher();
        return totalLimit - heldCount - consumedCount;
    }

    /** One slot moves to held. */
    public void hold() {
        requireVoucher();
        if (availableTotal() < 1) {
            throw new LoyaltyException(LoyaltyErrorCode.QUOTA_LIMIT_CONFLICT, "No voucher uses are left.");
        }
        heldCount++;
    }

    /** HELD to CONSUMED. */
    public void consume() {
        requireVoucher();
        requireHeld();
        heldCount--;
        consumedCount++;
    }

    /** HELD back to available. Never counted as used. */
    public void release() {
        requireVoucher();
        requireHeld();
        heldCount--;
    }

    /** CONSUMED back to available after a valid cancellation; does not reactivate an expired or paused programme. */
    public void restore() {
        requireVoucher();
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

    private void requireVoucher() {
        if (!isVoucher()) {
            throw new LoyaltyException(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT, "Automatic discounts have no quota.");
        }
    }

    private void requireState(ProgramStatus expected) {
        if (status != expected) {
            throw new LoyaltyException(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT,
                    "The programme is not in a state that allows this action.");
        }
    }

    private static void requirePositiveLimits(long total, long perMember) {
        if (total < 1 || perMember < 1) {
            throw LoyaltyException.invalidField("limits", "Limits must be positive whole numbers.");
        }
    }
}
