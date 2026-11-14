package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitPartRef;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.ComponentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.EarnState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
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

/**
 * One settlement part of a retail checkout (Session 2026-11-12, D-011): its share of the accepted benefit, fixed at
 * acceptance, and its own points and earn state. Always changed after the parent {@link AcceptedBenefit} row is locked,
 * so the parent lock serialises every part of a checkout. A part is paid once or released once, never both; the same
 * outcome key replays as a no-op and another key on a decided part is a conflict. The voucher use stays on the parent.
 */
@Entity
@Table(name = "loyalty_accepted_benefit_part")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcceptedBenefitPart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "accepted_benefit_id", nullable = false, updatable = false)
    private Long acceptedBenefitId;

    @Column(name = "part_seq", nullable = false, updatable = false)
    private int partSeq;

    @Column(name = "part_ref", nullable = false, updatable = false, length = 64)
    private String partRef;

    @Column(name = "gross_amount", nullable = false, updatable = false, precision = 19, scale = 0)
    private BigInteger grossAmount;

    @Column(name = "program_discount", nullable = false, updatable = false, precision = 19, scale = 0)
    private BigInteger programDiscount;

    @Column(name = "points", nullable = false, updatable = false, precision = 19, scale = 0)
    private BigInteger points;

    @Column(name = "point_discount", nullable = false, updatable = false, precision = 19, scale = 0)
    private BigInteger pointDiscount;

    @Column(name = "earn_base", nullable = false, updatable = false, precision = 19, scale = 0)
    private BigInteger earnBase;

    @Column(name = "earn_points", nullable = false, updatable = false, precision = 19, scale = 0)
    private BigInteger earnPoints;

    @Enumerated(EnumType.STRING)
    @Column(name = "points_state", nullable = false, length = 10)
    private ComponentState pointsState;

    @Enumerated(EnumType.STRING)
    @Column(name = "earn_state", nullable = false, length = 12)
    private EarnState earnState;

    /** How a workshop ticket ended (Session 2026-11-12, D-012); null while open and for a retail branch order. */
    @Column(name = "closure", length = 20)
    private String closure;

    @Column(name = "earned_points", precision = 19, scale = 0)
    private BigInteger earnedPoints;

    @Column(name = "settlement_outcome_key", length = 128)
    private String settlementOutcomeKey;

    @Column(name = "release_outcome_key", length = 128)
    private String releaseOutcomeKey;

    @Column(name = "earn_outcome_key", length = 128)
    private String earnOutcomeKey;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "terminal_at")
    private Instant terminalAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** The share computed at acceptance (see {@code BenefitPartAllocator}). */
    public record Share(String partRef, BigInteger grossAmount, BigInteger programDiscount, BigInteger points,
                        BigInteger pointDiscount, BigInteger earnBase, BigInteger earnPoints) {
    }

    public static AcceptedBenefitPart accept(long acceptedBenefitId, int partSeq, Share share) {
        if (share.grossAmount().subtract(share.programDiscount()).subtract(share.pointDiscount())
                .compareTo(share.earnBase()) != 0 || share.earnBase().signum() < 0) {
            throw new IllegalStateException("A part share must add up and must not be negative");
        }
        AcceptedBenefitPart part = new AcceptedBenefitPart();
        part.acceptedBenefitId = acceptedBenefitId;
        part.partSeq = partSeq;
        part.partRef = share.partRef();
        part.grossAmount = share.grossAmount();
        part.programDiscount = share.programDiscount();
        part.points = share.points();
        part.pointDiscount = share.pointDiscount();
        part.earnBase = share.earnBase();
        part.earnPoints = share.earnPoints();
        part.pointsState = share.points().signum() > 0 ? ComponentState.HELD : ComponentState.NONE;
        part.earnState = EarnState.PENDING;
        return part;
    }

    public static final String COMPLETED = "COMPLETED";
    public static final String VALID_CANCELLATION = "VALID_CANCELLATION";
    public static final String MLAND_CANCELLATION = "MLAND_CANCELLATION";
    public static final String NO_SHOW = "NO_SHOW";

    /** What a ticket cancellation did to the ticket's points. */
    public enum TicketPoints { NONE, RELEASED, RESTORED }

    /** True once a paid, cancelled, expired, completed or no-show outcome was applied to this part. */
    public boolean hasOutcome() {
        return settlementOutcomeKey != null || releaseOutcomeKey != null || closure != null;
    }

    /** True when the ticket ended by a valid or a Mland cancellation (the group's voucher may come back). */
    public boolean isCancelledForVoucher() {
        return VALID_CANCELLATION.equals(closure) || MLAND_CANCELLATION.equals(closure);
    }

    /**
     * Workshop ticket completed and fully paid (003.3 FR-030): earns the ticket's share once. {@code groupSettled} is
     * true when the group deposit (or 0đ confirmation) already settled the held benefits.
     */
    public boolean grantTicketEarn(String outcomeKey, boolean groupSettled) {
        if (earnState == EarnState.GRANTED) {
            if (!outcomeKey.equals(earnOutcomeKey)) {
                throw conflict("This ticket already earned with a different outcome.");
            }
            return false;
        }
        if (closure != null || earnState == EarnState.INELIGIBLE) {
            throw conflict("A cancelled or no-show ticket does not earn points.");
        }
        if (!groupSettled) {
            throw conflict("Points are earned only after the booking's benefits are settled.");
        }
        closure = COMPLETED;
        earnState = EarnState.GRANTED;
        earnedPoints = earnPoints;
        earnOutcomeKey = outcomeKey;
        terminalAt = settledAt;
        return true;
    }

    /**
     * Valid or Mland cancellation of a workshop ticket (003.3 FR-042, FR-043): held points are released, consumed
     * points are restored in the original amount, and the ticket's earn closes. A completed ticket cannot be cancelled.
     */
    public TicketPoints cancelTicket(String outcomeKey, String how, Instant at) {
        if (outcomeKey.equals(releaseOutcomeKey)) {
            return TicketPoints.NONE;
        }
        if (closure != null) {
            throw conflict("This ticket already has a different outcome.");
        }
        closure = how;
        releaseOutcomeKey = outcomeKey;
        terminalAt = at;
        earnState = EarnState.INELIGIBLE;
        earnOutcomeKey = outcomeKey;
        if (pointsState == ComponentState.HELD) {
            pointsState = ComponentState.RELEASED;
            return TicketPoints.RELEASED;
        }
        if (pointsState == ComponentState.CONSUMED) {
            pointsState = ComponentState.RESTORED;
            return TicketPoints.RESTORED;
        }
        return TicketPoints.NONE;
    }

    /** Final no-show or late cancellation of a workshop ticket: nothing given back, the ticket earns nothing. */
    public boolean noShowTicket(String outcomeKey, Instant at) {
        if (outcomeKey.equals(releaseOutcomeKey)) {
            return false;
        }
        if (closure != null) {
            throw conflict("This ticket already has a different outcome.");
        }
        closure = NO_SHOW;
        releaseOutcomeKey = outcomeKey;
        terminalAt = at;
        earnState = EarnState.INELIGIBLE;
        earnOutcomeKey = outcomeKey;
        return true;
    }

    /** The whole booking was validly cancelled at once: each ticket records the same result for its share. */
    public void mirrorRestore(Instant at) {
        terminalAt = at;
        if (pointsState == ComponentState.HELD) {
            pointsState = ComponentState.RELEASED;
        } else if (pointsState == ComponentState.CONSUMED) {
            pointsState = ComponentState.RESTORED;
        }
        if (earnState == EarnState.PENDING) {
            earnState = EarnState.INELIGIBLE;
        }
    }

    public boolean isPaid() {
        return settlementOutcomeKey != null;
    }

    public boolean isReleased() {
        return releaseOutcomeKey != null;
    }

    /** Paid: HELD points become CONSUMED. Returns whether points changed. A released part cannot be paid. */
    public boolean settle(String outcomeKey, Instant at) {
        if (outcomeKey.equals(settlementOutcomeKey)) {
            return false;
        }
        if (settlementOutcomeKey != null || releaseOutcomeKey != null) {
            throw conflict("This branch order already has a different outcome.");
        }
        settlementOutcomeKey = outcomeKey;
        settledAt = at;
        if (pointsState == ComponentState.HELD) {
            pointsState = ComponentState.CONSUMED;
            return true;
        }
        return false;
    }

    /** Cancelled or expired unpaid: HELD points become RELEASED and the pending earn closes. A paid part cannot be. */
    public boolean release(String outcomeKey, Instant at) {
        if (outcomeKey.equals(releaseOutcomeKey)) {
            return false;
        }
        if (settlementOutcomeKey != null || releaseOutcomeKey != null) {
            throw conflict("This branch order already has a different outcome.");
        }
        releaseOutcomeKey = outcomeKey;
        terminalAt = at;
        if (earnState == EarnState.PENDING) {
            earnState = EarnState.INELIGIBLE;
            earnOutcomeKey = outcomeKey;
        }
        if (pointsState == ComponentState.HELD) {
            pointsState = ComponentState.RELEASED;
            return true;
        }
        return false;
    }

    /** Earn of a paid part, once. Returns whether the grant is new. */
    public boolean grantEarn(String outcomeKey) {
        if (earnState == EarnState.GRANTED) {
            if (!outcomeKey.equals(earnOutcomeKey)) {
                throw conflict("This branch order already earned with a different outcome.");
            }
            return false;
        }
        if (earnState == EarnState.INELIGIBLE || !isPaid()) {
            throw conflict("Points are earned only for a paid branch order.");
        }
        earnState = EarnState.GRANTED;
        earnedPoints = earnPoints;
        earnOutcomeKey = outcomeKey;
        terminalAt = terminalAt == null ? settledAt : terminalAt;
        return true;
    }

    /** Closes a pending earn of a part that will never be paid (release already does this). */
    public boolean finishIneligibleEarn(String outcomeKey, Instant at) {
        if (earnState == EarnState.GRANTED) {
            throw conflict("Earned points are never withdrawn.");
        }
        if (earnState == EarnState.INELIGIBLE) {
            return false;
        }
        if (isPaid()) {
            throw conflict("A paid branch order earns its points instead.");
        }
        earnState = EarnState.INELIGIBLE;
        earnOutcomeKey = outcomeKey;
        terminalAt = terminalAt == null ? at : terminalAt;
        return true;
    }

    /** The whole checkout was paid at once (whole-transaction outcome on a checkout with parts). */
    public void mirrorSettle(Instant at) {
        settledAt = at;
        if (pointsState == ComponentState.HELD) {
            pointsState = ComponentState.CONSUMED;
        }
    }

    /** The whole checkout was released at once. */
    public void mirrorRelease(Instant at) {
        terminalAt = at;
        if (pointsState == ComponentState.HELD) {
            pointsState = ComponentState.RELEASED;
        }
        if (earnState == EarnState.PENDING) {
            earnState = EarnState.INELIGIBLE;
        }
    }

    /** The whole checkout earned at once: each part records its own share. */
    public void mirrorGrant() {
        if (earnState == EarnState.PENDING) {
            earnState = EarnState.GRANTED;
            earnedPoints = earnPoints;
        }
    }

    /** The whole checkout's earn closed as ineligible. */
    public void mirrorIneligible() {
        if (earnState == EarnState.PENDING) {
            earnState = EarnState.INELIGIBLE;
        }
    }

    public BenefitPartRef ref() {
        return new BenefitPartRef(partRef, grossAmount, programDiscount, points, pointDiscount, earnBase, earnPoints,
                pointsState, earnState, closure);
    }

    private static LoyaltyException conflict(String message) {
        return new LoyaltyException(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT, message);
    }
}
