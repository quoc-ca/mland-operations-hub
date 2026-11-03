package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.PolicyTerms;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;

/**
 * Immutable points policy revision (data-model {@code loyalty_policy}). A change inserts a new row and
 * moves the registry pointer; accepted transactions keep the revision they were accepted under.
 */
@Entity
@Table(name = "loyalty_policy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoyaltyPolicy {

    public static final String ROUNDING_MODE = "FLOOR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "revision_no", nullable = false, updatable = false)
    private long revisionNo;

    @Column(name = "earn_unit_amount", nullable = false, updatable = false, precision = 19, scale = 0)
    private BigInteger earnUnitAmount;

    @Column(name = "redeem_point_value", nullable = false, updatable = false, precision = 19, scale = 0)
    private BigInteger redeemPointValue;

    @Column(name = "min_redeem_points", nullable = false, updatable = false, precision = 19, scale = 0)
    private BigInteger minRedeemPoints;

    @Column(name = "max_redeem_percent", nullable = false, updatable = false, precision = 9, scale = 6)
    private BigDecimal maxRedeemPercent;

    @Column(name = "rounding_mode", nullable = false, updatable = false, length = 10)
    private String roundingMode = ROUNDING_MODE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Account id of the Manager, or {@code null} for the seeded default. */
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    public static LoyaltyPolicy create(long revisionNo, PolicyTerms terms, Instant createdAt, Long createdBy) {
        if (revisionNo <= 0) {
            throw new IllegalArgumentException("Revision number must be positive.");
        }
        LoyaltyPolicy policy = new LoyaltyPolicy();
        policy.revisionNo = revisionNo;
        policy.earnUnitAmount = terms.earnUnitAmount();
        policy.redeemPointValue = terms.redeemPointValue();
        policy.minRedeemPoints = terms.minRedeemPoints();
        policy.maxRedeemPercent = terms.maxRedeemPercent();
        policy.createdAt = createdAt;
        policy.createdBy = createdBy;
        return policy;
    }

    public PolicyTerms terms() {
        return new PolicyTerms(earnUnitAmount, redeemPointValue, minRedeemPoints, maxRedeemPercent);
    }
}
