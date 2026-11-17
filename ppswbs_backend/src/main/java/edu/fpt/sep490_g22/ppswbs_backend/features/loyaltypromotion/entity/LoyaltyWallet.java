package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyValues;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

/**
 * One points wallet per Member, shared by both services; no tiers, no per-service split and no expiry.
 * Invariant {@code 0 <= held <= balance}. Available points are {@code balance - held}. The Member is a
 * logical reference (no association to the identity entity). Rows are created under the identity guard.
 */
@Entity
@Table(name = "loyalty_wallet")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoyaltyWallet {

    @Id
    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "balance_points", nullable = false, precision = 19, scale = 0)
    private BigInteger balancePoints = BigInteger.ZERO;

    @Column(name = "held_points", nullable = false, precision = 19, scale = 0)
    private BigInteger heldPoints = BigInteger.ZERO;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public static LoyaltyWallet open(long memberId) {
        LoyaltyWallet wallet = new LoyaltyWallet();
        wallet.memberId = memberId;
        return wallet;
    }

    public BigInteger availablePoints() {
        return balancePoints.subtract(heldPoints);
    }

    /** HOLD: held += P. Rejected when the available points are not enough. */
    public void hold(BigInteger points) {
        requirePositive(points);
        if (availablePoints().compareTo(points) < 0) {
            throw new LoyaltyException(LoyaltyErrorCode.POINTS_LIMIT_CHANGED, "Not enough available points.");
        }
        heldPoints = LoyaltyValues.requireAmount("heldPoints", heldPoints.add(points));
    }

    /** CONSUME: balance -= P and held -= P. */
    public void consume(BigInteger points) {
        requirePositive(points);
        requireHeld(points);
        balancePoints = balancePoints.subtract(points);
        heldPoints = heldPoints.subtract(points);
    }

    /** RELEASE: held -= P, balance unchanged and no extra credit. */
    public void release(BigInteger points) {
        requirePositive(points);
        requireHeld(points);
        heldPoints = heldPoints.subtract(points);
    }

    /** RESTORE: balance += P (the exact originally debited amount, whatever the current conversion rate). */
    public void restore(BigInteger points) {
        requirePositive(points);
        balancePoints = LoyaltyValues.requireAmount("balancePoints", balancePoints.add(points));
    }

    /** EARN: balance += earned points. */
    public void earn(BigInteger points) {
        requirePositive(points);
        balancePoints = LoyaltyValues.requireAmount("balancePoints", balancePoints.add(points));
    }

    private void requireHeld(BigInteger points) {
        if (heldPoints.compareTo(points) < 0) {
            throw new LoyaltyException(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT, "These points are not held.");
        }
    }

    private static void requirePositive(BigInteger points) {
        if (points == null || points.signum() <= 0) {
            throw LoyaltyException.invalidField("points", "Points must be greater than zero.");
        }
    }
}
