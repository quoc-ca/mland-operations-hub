package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetDimension;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope;
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

import java.util.EnumSet;
import java.util.Set;

/**
 * One scope value of a programme (unique per revision, dimension and key). Different dimensions combine with
 * AND, values inside a dimension with OR. Product programmes have no branch; package, date, session and branch
 * belong to workshops. Products and variants stay referenced even if hidden or deleted later; they only stop
 * being eligible at quote time.
 */
@Entity
@Table(name = "promotion_revision_target")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PromotionTarget {

    private static final Set<TargetDimension> PRODUCT_DIMENSIONS =
            EnumSet.of(TargetDimension.PRODUCT, TargetDimension.VARIANT);
    private static final Set<TargetDimension> WORKSHOP_DIMENSIONS =
            EnumSet.of(TargetDimension.PACKAGE, TargetDimension.BRANCH, TargetDimension.WORKSHOP_DATE,
                    TargetDimension.SESSION);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "revision_id", nullable = false, updatable = false)
    private Long revisionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "dimension", nullable = false, updatable = false, length = 20)
    private TargetDimension dimension;

    @Column(name = "target_key", nullable = false, updatable = false, length = 64)
    private String targetKey;

    public static PromotionTarget create(long revisionId, TargetScope scope, TargetDimension dimension,
                                         String targetKey) {
        Set<TargetDimension> allowed = scope == TargetScope.PRODUCT ? PRODUCT_DIMENSIONS : WORKSHOP_DIMENSIONS;
        if (!allowed.contains(dimension)) {
            throw new IllegalArgumentException("Dimension " + dimension + " is not valid for scope " + scope);
        }
        PromotionTarget target = new PromotionTarget();
        target.revisionId = revisionId;
        target.dimension = dimension;
        target.targetKey = targetKey;
        return target;
    }
}
