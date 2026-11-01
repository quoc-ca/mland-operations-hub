package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.DiscountTerms;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyValues;
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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;

/**
 * Immutable terms of a programme at one point in time (unique per programme and revision number). Editing a
 * programme inserts a new revision; accepted transactions keep the terms they were accepted under. Exactly
 * one of percent or fixed amount is set. An automatic discount always allows points.
 */
@Entity
@Table(name = "promotion_revision")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PromotionRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "program_id", nullable = false, updatable = false)
    private Long programId;

    @Column(name = "revision_no", nullable = false, updatable = false)
    private long revisionNo;

    @Column(name = "name", nullable = false, updatable = false, length = 200)
    private String name;

    @Column(name = "start_at", nullable = false, updatable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false, updatable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_kind", nullable = false, updatable = false, length = 10)
    private DiscountKind discountKind;

    @Column(name = "percent", updatable = false, precision = 9, scale = 6)
    private BigDecimal percent;

    @Column(name = "fixed_amount", updatable = false, precision = 19, scale = 0)
    private BigInteger fixedAmount;

    @Column(name = "max_discount_amount", nullable = false, updatable = false, precision = 19, scale = 0)
    private BigInteger maxDiscountAmount;

    @Column(name = "minimum_eligible_amount", nullable = false, updatable = false, precision = 19, scale = 0)
    private BigInteger minimumEligibleAmount;

    @Column(name = "allow_points", nullable = false, updatable = false)
    private boolean allowPoints;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    public static PromotionRevision create(long programId, long revisionNo, ProgramKind kind, String name,
                                           Instant startAt, Instant endAt, DiscountTerms terms,
                                           boolean requestedAllowPoints, Instant createdAt, long createdBy) {
        if (revisionNo <= 0) {
            throw new IllegalArgumentException("Revision number must be positive.");
        }
        LoyaltyValues.requireInterval("startAt", startAt, endAt);
        PromotionRevision revision = new PromotionRevision();
        revision.programId = programId;
        revision.revisionNo = revisionNo;
        revision.name = name;
        revision.startAt = LoyaltyValues.truncateToMicros(startAt);
        revision.endAt = LoyaltyValues.truncateToMicros(endAt);
        revision.discountKind = terms.percent() != null ? DiscountKind.PERCENT : DiscountKind.FIXED;
        revision.percent = terms.percent();
        revision.fixedAmount = terms.fixedAmount();
        revision.maxDiscountAmount = terms.maxDiscount();
        revision.minimumEligibleAmount = terms.minimumEligible();
        revision.allowPoints = kind == ProgramKind.AUTO_DISCOUNT || requestedAllowPoints;
        revision.createdAt = createdAt;
        revision.createdBy = createdBy;
        return revision;
    }

    public DiscountTerms terms() {
        return new DiscountTerms(percent, fixedAmount, maxDiscountAmount, minimumEligibleAmount);
    }

    public boolean isActiveAt(Instant at) {
        return LoyaltyValues.isActive(startAt, endAt, at);
    }
}
