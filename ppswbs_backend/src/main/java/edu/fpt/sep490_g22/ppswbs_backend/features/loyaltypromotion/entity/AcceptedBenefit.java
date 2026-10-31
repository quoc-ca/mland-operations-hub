package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.ComponentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.EarnState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
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

import java.math.BigInteger;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable snapshot of the benefits accepted together with one order or booking (unique per source kind and
 * source reference), plus the state of each component. A record exists for every accepted Member transaction,
 * even without voucher or points, so the policy at acceptance decides the later earn. Amounts and the policy and
 * programme revision never change. Component states, outcome keys and timestamps move forward only, guarded by
 * {@code version}, and replace a separate effect table: the same outcome key replays as a no-op, a different key
 * on an already decided component is a conflict, and a transition from RELEASED/RESTORED back to HELD/CONSUMED
 * does not exist.
 *
 * <p>Workshop amounts: {@code postPromotionAmount} is the fee after voucher and points. FE-03/003.1 derives the
 * deposit ({@code ceil(post / 2)}) and the remaining amount; loyalty never rounds a deposit itself.
 */
@Entity
@Table(name = "loyalty_accepted_benefit")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcceptedBenefit {

    /** Components whose state actually changed in one transition, so only those post ledger/quota effects. */
    public record Changes(boolean points, boolean voucher, boolean earn) {
        public boolean any() {
            return points || voucher || earn;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_kind", nullable = false, updatable = false, length = 10)
    private SourceKind sourceKind;

    @Column(name = "source_ref", nullable = false, updatable = false, length = 128)
    private String sourceRef;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "member_origin_eligible", nullable = false, updatable = false)
    private boolean memberOriginEligible;

    @Column(name = "accepted_at", nullable = false, updatable = false)
    private Instant acceptedAt;

    @Column(name = "original_deadline", nullable = false, updatable = false)
    private Instant originalDeadline;

    @Column(name = "source_revision", nullable = false, updatable = false)
    private long sourceRevision;

    @Column(name = "accepted_terms_digest", nullable = false, updatable = false, length = 128)
    private String acceptedTermsDigest;

    @Column(name = "policy_id", nullable = false, updatable = false)
    private Long policyId;

    @Column(name = "program_id", updatable = false)
    private Long programId;

    @Column(name = "revision_id", updatable = false)
    private Long revisionId;

    @Column(name = "gross_amount", nullable = false, updatable = false, precision = 19, scale = 0)
    private BigInteger grossAmount;

    @Column(name = "program_discount", nullable = false, updatable = false, precision = 19, scale = 0)
    private BigInteger programDiscount;

    @Column(name = "requested_points", nullable = false, updatable = false, precision = 19, scale = 0)
    private BigInteger requestedPoints;

    @Column(name = "point_discount", nullable = false, updatable = false, precision = 19, scale = 0)
    private BigInteger pointDiscount;

    @Column(name = "earn_base", nullable = false, updatable = false, precision = 19, scale = 0)
    private BigInteger earnBase;

    /** Workshop only: fee after voucher and points. Deposit and remaining belong to FE-03, not to loyalty. */
    @Column(name = "post_promotion_amount", updatable = false, precision = 19, scale = 0)
    private BigInteger postPromotionAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "points_state", nullable = false, length = 10)
    private ComponentState pointsState;

    @Enumerated(EnumType.STRING)
    @Column(name = "voucher_state", nullable = false, length = 10)
    private ComponentState voucherState;

    @Enumerated(EnumType.STRING)
    @Column(name = "earn_state", nullable = false, length = 12)
    private EarnState earnState;

    @Column(name = "earned_points", precision = 19, scale = 0)
    private BigInteger earnedPoints;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "settlement_outcome_key", length = 128)
    private String settlementOutcomeKey;

    @Column(name = "release_outcome_key", length = 128)
    private String releaseOutcomeKey;

    @Column(name = "restore_outcome_key", length = 128)
    private String restoreOutcomeKey;

    @Column(name = "earn_outcome_key", length = 128)
    private String earnOutcomeKey;

    @Column(name = "source_outcome_version", nullable = false)
    private long sourceOutcomeVersion;

    @Column(name = "voucher_consumed_at")
    private Instant voucherConsumedAt;

    @Column(name = "voucher_restored_at")
    private Instant voucherRestoredAt;

    @Column(name = "source_terminal_at")
    private Instant sourceTerminalAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** Amounts and revisions captured at acceptance; the caller already held quota and points. */
    public record Snapshot(SourceKind sourceKind, String sourceRef, long memberId, Instant acceptedAt,
                           Instant originalDeadline, long sourceRevision, String acceptedTermsDigest, long policyId,
                           Long programId, Long revisionId, BigInteger grossAmount, BigInteger programDiscount,
                           BigInteger requestedPoints, BigInteger pointDiscount, BigInteger earnBase,
                           BigInteger postPromotionAmount, boolean voucherHeld) {
    }

    /** Only transactions a Member made directly on the website qualify; guest history is never retrofitted. */
    public static AcceptedBenefit accept(Snapshot snapshot, boolean memberOrigin) {
        if (!memberOrigin) {
            throw new LoyaltyException(LoyaltyErrorCode.PROMOTION_INELIGIBLE,
                    "Only a Member's direct website transaction can use these benefits.");
        }
        boolean workshop = snapshot.sourceKind() == SourceKind.WORKSHOP;
        if (workshop != (snapshot.postPromotionAmount() != null)) {
            throw LoyaltyException.invalidField("postPromotionAmount",
                    "Only a workshop snapshot carries the post-promotion amount, and it must carry it.");
        }
        AcceptedBenefit benefit = new AcceptedBenefit();
        benefit.sourceKind = snapshot.sourceKind();
        benefit.sourceRef = snapshot.sourceRef();
        benefit.memberId = snapshot.memberId();
        benefit.memberOriginEligible = true;
        benefit.acceptedAt = snapshot.acceptedAt();
        benefit.originalDeadline = snapshot.originalDeadline();
        benefit.sourceRevision = snapshot.sourceRevision();
        benefit.acceptedTermsDigest = snapshot.acceptedTermsDigest();
        benefit.policyId = snapshot.policyId();
        benefit.programId = snapshot.programId();
        benefit.revisionId = snapshot.revisionId();
        benefit.grossAmount = snapshot.grossAmount();
        benefit.programDiscount = snapshot.programDiscount();
        benefit.requestedPoints = snapshot.requestedPoints();
        benefit.pointDiscount = snapshot.pointDiscount();
        benefit.earnBase = snapshot.earnBase();
        benefit.postPromotionAmount = snapshot.postPromotionAmount();
        benefit.pointsState = snapshot.requestedPoints().signum() > 0 ? ComponentState.HELD : ComponentState.NONE;
        benefit.voucherState = snapshot.voucherHeld() ? ComponentState.HELD : ComponentState.NONE;
        benefit.earnState = EarnState.PENDING;
        return benefit;
    }

    /** HELD to CONSUMED on a verified paid, deposit or zero outcome; records the settlement fact once. */
    public Changes settle(String outcomeKey, long outcomeVersion, Instant at) {
        acceptVersion(outcomeVersion);
        if (settlementOutcomeKey != null) {
            requireSameKey(settlementOutcomeKey, outcomeKey);
            return new Changes(false, false, false);
        }
        requireNoneOf("settle", pointsState, ComponentState.RELEASED, ComponentState.RESTORED);
        requireNoneOf("settle", voucherState, ComponentState.RELEASED, ComponentState.RESTORED);
        boolean points = pointsState == ComponentState.HELD;
        boolean voucher = voucherState == ComponentState.HELD;
        if (points) {
            pointsState = ComponentState.CONSUMED;
        }
        if (voucher) {
            voucherState = ComponentState.CONSUMED;
            voucherConsumedAt = at;
        }
        settledAt = at;
        settlementOutcomeKey = outcomeKey;
        return new Changes(points, voucher, false);
    }

    /** HELD to RELEASED on a confirmed unpaid expiry; consumed components are never released. */
    public Changes release(String outcomeKey, long outcomeVersion, Instant at) {
        acceptVersion(outcomeVersion);
        if (releaseOutcomeKey != null) {
            requireSameKey(releaseOutcomeKey, outcomeKey);
            return new Changes(false, false, false);
        }
        requireNoneOf("release", pointsState, ComponentState.CONSUMED);
        requireNoneOf("release", voucherState, ComponentState.CONSUMED);
        boolean points = pointsState == ComponentState.HELD;
        boolean voucher = voucherState == ComponentState.HELD;
        if (points) {
            pointsState = ComponentState.RELEASED;
        }
        if (voucher) {
            voucherState = ComponentState.RELEASED;
        }
        boolean earn = finishEarn(at);
        releaseOutcomeKey = outcomeKey;
        sourceTerminalAt = at;
        return new Changes(points, voucher, earn);
    }

    /**
     * Valid workshop cancellation before completion: HELD is released, CONSUMED is restored. Each component is
     * handled on its own, so a retry after a partial result only completes what is missing.
     */
    public Changes restore(String outcomeKey, long outcomeVersion, Instant at) {
        acceptVersion(outcomeVersion);
        if (restoreOutcomeKey != null) {
            requireSameKey(restoreOutcomeKey, outcomeKey);
        }
        if (earnState == EarnState.GRANTED) {
            throw new LoyaltyException(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT,
                    "A workshop that already earned points cannot be cancelled here.");
        }
        boolean points = false;
        boolean voucher = false;
        if (pointsState == ComponentState.HELD) {
            pointsState = ComponentState.RELEASED;
            points = true;
        } else if (pointsState == ComponentState.CONSUMED) {
            pointsState = ComponentState.RESTORED;
            points = true;
        }
        if (voucherState == ComponentState.HELD) {
            voucherState = ComponentState.RELEASED;
            voucher = true;
        } else if (voucherState == ComponentState.CONSUMED) {
            voucherState = ComponentState.RESTORED;
            voucherRestoredAt = at;
            voucher = true;
        }
        boolean earn = finishEarn(at);
        restoreOutcomeKey = outcomeKey;
        if (sourceTerminalAt == null) {
            sourceTerminalAt = at;
        }
        return new Changes(points, voucher, earn);
    }

    /** PENDING to GRANTED once every used component is CONSUMED (or not used). Zero points still record the result. */
    public boolean grantEarn(String outcomeKey, long outcomeVersion, BigInteger earned) {
        acceptVersion(outcomeVersion);
        if (earnState == EarnState.GRANTED) {
            requireSameKey(earnOutcomeKey, outcomeKey);
            return false;
        }
        if (earnState == EarnState.INELIGIBLE) {
            throw new LoyaltyException(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT, "This transaction is not eligible to earn.");
        }
        if (!isSettledForEarn(pointsState) || !isSettledForEarn(voucherState)) {
            throw new LoyaltyException(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT,
                    "Points cannot be earned while a benefit is still held or already given back.");
        }
        earnState = EarnState.GRANTED;
        earnedPoints = earned;
        earnOutcomeKey = outcomeKey;
        return true;
    }

    /** PENDING to INELIGIBLE on a terminal outcome that never qualifies; consumed components stay untouched. */
    public boolean finishIneligibleEarn(String outcomeKey, long outcomeVersion, Instant at) {
        acceptVersion(outcomeVersion);
        if (earnState == EarnState.GRANTED) {
            throw new LoyaltyException(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT, "Earned points are never withdrawn.");
        }
        if (earnState == EarnState.INELIGIBLE) {
            return false;
        }
        earnState = EarnState.INELIGIBLE;
        earnOutcomeKey = outcomeKey;
        if (sourceTerminalAt == null) {
            sourceTerminalAt = at;
        }
        return true;
    }

    /** A voucher use counts in the current usage report: consumed and not given back. */
    public boolean countsAsVoucherUse() {
        return voucherState == ComponentState.CONSUMED;
    }

    private boolean finishEarn(Instant at) {
        if (earnState == EarnState.PENDING) {
            earnState = EarnState.INELIGIBLE;
            return true;
        }
        return false;
    }

    private static boolean isSettledForEarn(ComponentState state) {
        return state == ComponentState.NONE || state == ComponentState.CONSUMED;
    }

    /** Outcomes are monotonic per source; a late older version is rejected, an equal or newer one is handled. */
    private void acceptVersion(long outcomeVersion) {
        if (outcomeVersion < sourceOutcomeVersion) {
            throw new LoyaltyException(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT, "A newer source outcome was already applied.");
        }
        sourceOutcomeVersion = outcomeVersion;
    }

    private static void requireSameKey(String recorded, String incoming) {
        if (!Objects.equals(recorded, incoming)) {
            throw new LoyaltyException(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT,
                    "A different outcome was already applied to this transaction.");
        }
    }

    private static void requireNoneOf(String action, ComponentState state, ComponentState... forbidden) {
        for (ComponentState bad : forbidden) {
            if (state == bad) {
                throw new LoyaltyException(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT,
                        "The benefit is not in a state that allows " + action + ".");
            }
        }
    }
}
