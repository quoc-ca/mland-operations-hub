package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Quantity of one size at one branch (data-model "Branch stock", FR-047/049, Session 2026-11-08).
 *
 * <ul>
 *   <li>One row per size × branch, never deleted: stopping sale at a branch replaces removing it.</li>
 *   <li>{@code locationId} is a branch of the canonical list (003.2), validated through the selling location port;
 *       there is no foreign key across modules.</li>
 *   <li>The size's total ({@code catalogue_variants.remaining_unsold}) is kept equal to the sum of its rows.</li>
 * </ul>
 */
@Entity
@Table(name = "catalogue_variant_branch_stock",
        uniqueConstraints = @UniqueConstraint(name = "uk_catalogue_branch_stock", columnNames = {"variant_id", "location_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VariantBranchStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "remaining_unsold", nullable = false, precision = 19, scale = 0)
    private BigInteger remainingUnsold;

    @Column(name = "sale_enabled", nullable = false)
    private boolean saleEnabled;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public static VariantBranchStock create(long variantId, long locationId, BigInteger quantity) {
        VariantBranchStock stock = new VariantBranchStock();
        stock.variantId = variantId;
        stock.locationId = locationId;
        stock.remainingUnsold = requireQuantity(quantity);
        stock.saleEnabled = true;
        return stock;
    }

    public void setRemainingUnsold(BigInteger quantity) {
        this.remainingUnsold = requireQuantity(quantity);
    }

    /** Takes up to {@code wanted} from this branch for a sale whose branch is unknown; returns what was taken. */
    public BigInteger takeUpTo(BigInteger wanted) {
        BigInteger taken = remainingUnsold.min(wanted).max(BigInteger.ZERO);
        this.remainingUnsold = remainingUnsold.subtract(taken);
        return taken;
    }

    /** Returns whether the sale status at this branch changed. */
    public boolean setSaleEnabled(boolean enabled) {
        if (saleEnabled == enabled) {
            return false;
        }
        this.saleEnabled = enabled;
        return true;
    }

    private static BigInteger requireQuantity(BigInteger quantity) {
        if (quantity == null || quantity.signum() < 0 || quantity.toString().length() > 19) {
            throw CatalogueException.invalidField("quantity", "Must be a nonnegative whole number of at most 19 digits.");
        }
        return Objects.requireNonNull(quantity);
    }

    /** Guard used by services: the row must exist for the size and branch. */
    public static VariantBranchStock require(VariantBranchStock stock) {
        if (stock == null) {
            throw new CatalogueException(CatalogueErrorCode.BRANCH_STOCK_NOT_FOUND);
        }
        return stock;
    }
}
