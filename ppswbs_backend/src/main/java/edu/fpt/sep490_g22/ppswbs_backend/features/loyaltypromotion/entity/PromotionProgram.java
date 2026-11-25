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
 *
 * <p>Session 2026-11-22 (FR-046): a voucher has a {@link VoucherDistribution}. For {@code CLAIM_REQUIRED} saving holds
 * one use ({@code claimedCount}: saved by a Member, not yet held or consumed), so
 * {@code held + consumed + claimed <= totalLimit}; holding the saved use moves it from claimed to held, releasing or
 * restoring it gives it back to the Member's wallet (claimed). {@code PUBLIC} vouchers keep {@code claimed = 0}.
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

    /** Voucher only (Session 2026-11-22); {@code null} for an automatic discount. Never changes. */
    @Enumerated(EnumType.STRING)
    @Column(name = "distribution", updatable = false, length = 16)
    private VoucherDistribution distribution;

    /** Saved uses of a {@code CLAIM_REQUIRED} voucher not yet held or consumed; 0 for PUBLIC, null for auto. */
    @Column(name = "claimed_count")
    private Long claimedCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    /**
     * Configuration changes only (edit, pause, resume). Quota counters do not move it, so a sale between loading and
     * saving does not make a Manager's edit stale. Exposed by the API as the programme {@code version}.
     */
    @Column(name = "config_version", nullable = false)
    private long configVersion;

    /** Row version for optimistic locking; also moves with the quota counters. Not exposed. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** A {@code PUBLIC} voucher, the shape before Session 2026-11-22. */
    public static PromotionProgram voucher(TargetScope scope, String codeOriginal, String codeKey, long totalLimit,
                                           long memberLimit, Instant createdAt, long createdBy) {
        return voucher(scope, codeOriginal, codeKey, totalLimit, memberLimit, VoucherDistribution.PUBLIC, createdAt,
                createdBy);
    }

    /** A {@code CLAIM_REQUIRED} voucher must allow exactly one use per Member (FR-046). */
    public static PromotionProgram voucher(TargetScope scope, String codeOriginal, String codeKey, long totalLimit,
                                           long memberLimit, VoucherDistribution distribution, Instant createdAt,
                                           long createdBy) {
        requirePositiveLimits(totalLimit, memberLimit);
        requireDistributionLimit(distribution, memberLimit);
        PromotionProgram program = base(ProgramKind.VOUCHER, scope, createdAt, createdBy);
        program.codeOriginal = codeOriginal;
        program.codeKey = codeKey;
        program.totalLimit = totalLimit;
        program.memberLimit = memberLimit;
        program.heldCount = 0L;
        program.consumedCount = 0L;
        program.distribution = distribution;
        program.claimedCount = 0L;
        return program;
    }

    /**
     * Automatic discounts have no code and no quota. Since Session 2026-11-14 (FR-044) the scope may also be
     * {@link TargetScope#WORKSHOP}: a workshop automatic discount is targeted by package only, so the discounted
     * price can be shown publicly before the customer picks a branch or a session.
     */
    public static PromotionProgram autoDiscount(TargetScope scope, Instant createdAt, long createdBy) {
        if (scope == null) {
            throw LoyaltyException.invalidField("scope", "A scope is required.");
        }
        return base(ProgramKind.AUTO_DISCOUNT, scope, createdAt, createdBy);
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

    /** {@code true} for a voucher only a Member who saved it can use (Session 2026-11-22). */
    public boolean isClaimRequired() {
        return distribution == VoucherDistribution.CLAIM_REQUIRED;
    }

    public void pointToRevision(long revisionId) {
        this.currentRevisionId = revisionId;
    }

    /** Records one configuration change (an edit); pause and resume record their own. */
    public void markConfigurationChanged() {
        configVersion++;
    }

    /** ENABLED to PAUSED. Existing holds and accepted snapshots are untouched. */
    public void pause() {
        requireState(ProgramStatus.ENABLED);
        status = ProgramStatus.PAUSED;
        configVersion++;
    }

    /** PAUSED to ENABLED on the same programme and code. Quotas, history and window are not changed. */
    public void resume() {
        requireState(ProgramStatus.PAUSED);
        status = ProgramStatus.ENABLED;
        configVersion++;
    }

    /**
     * Changes the limits for new transactions. The total can never drop below {@code held + consumed + claimed};
     * the per-member limit is checked against every member count by the caller under the programme lock.
     */
    public void changeLimits(long newTotalLimit, long newMemberLimit) {
        requireVoucher();
        requirePositiveLimits(newTotalLimit, newMemberLimit);
        requireDistributionLimit(distribution, newMemberLimit);
        if (newTotalLimit < heldCount + consumedCount + claimed()) {
            throw new LoyaltyException(LoyaltyErrorCode.QUOTA_LIMIT_CONFLICT,
                    "The limit cannot be lower than the amount already used or held.");
        }
        totalLimit = newTotalLimit;
        memberLimit = newMemberLimit;
    }

    public long availableTotal() {
        requireVoucher();
        return totalLimit - heldCount - consumedCount - claimed();
    }

    /**
     * Saving a {@code CLAIM_REQUIRED} voucher holds one use for the Member (FR-046). The caller checks under the
     * programme and member locks that this Member has not saved or used it yet, so it is called once per Member.
     */
    public void claim() {
        requireVoucher();
        if (!isClaimRequired()) {
            throw new LoyaltyException(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT, "Only a claim voucher is claimed.");
        }
        if (availableTotal() < 1) {
            throw new LoyaltyException(LoyaltyErrorCode.PROMOTION_INELIGIBLE, "No voucher uses are left.");
        }
        claimedCount++;
    }

    /**
     * One slot moves to held. Defensive guard: the locked quote already refuses a voucher with no uses left
     * ({@code VOUCHER_NO_USES_LEFT}), so this answers as the contract does for an ineligible voucher (T123).
     */
    public void hold() {
        requireVoucher();
        if (isClaimRequired()) {
            // The Member's saved use moves to held; the quote already refused a Member who did not save it.
            if (claimed() < 1) {
                throw new LoyaltyException(LoyaltyErrorCode.PROMOTION_INELIGIBLE, "The voucher is not saved.");
            }
            claimedCount--;
        } else if (availableTotal() < 1) {
            throw new LoyaltyException(LoyaltyErrorCode.PROMOTION_INELIGIBLE, "No voucher uses are left.");
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

    /** HELD back to available (a claim voucher: back to the Member's wallet). Never counted as used. */
    public void release() {
        requireVoucher();
        requireHeld();
        heldCount--;
        if (isClaimRequired()) {
            claimedCount++;
        }
    }

    /** CONSUMED back to available after a valid cancellation; does not reactivate an expired or paused programme. */
    public void restore() {
        requireVoucher();
        if (consumedCount < 1) {
            throw new LoyaltyException(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT, "No consumed use to restore.");
        }
        consumedCount--;
        if (isClaimRequired()) {
            // Back to the wallet of the Member who saved it (FR-046 (3)).
            claimedCount++;
        }
    }

    private long claimed() {
        return claimedCount == null ? 0L : claimedCount;
    }

    private static void requireDistributionLimit(VoucherDistribution distribution, long memberLimit) {
        if (distribution == null) {
            throw LoyaltyException.invalidField("distribution", "A distribution is required.");
        }
        if (distribution == VoucherDistribution.CLAIM_REQUIRED && memberLimit != 1) {
            throw LoyaltyException.invalidField("perMemberLimit", "A voucher that must be saved allows one use per member.");
        }
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
