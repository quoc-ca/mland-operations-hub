package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueValues;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.SkuGenerator;
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
 * One size (5–29) of a product (data-model {@code catalogue_variants}, FR-005/006, FR-019–021).
 *
 * <ul>
 *   <li>Size and SKU are fixed at creation: there is no method to change, add or remove them.</li>
 *   <li>{@code remainingUnsold} is the system-wide quantity still for sale; it can never go below the
 *       quantity held by valid FE-08 holds (checked under the source guard).</li>
 *   <li>{@code appliedPrice} is null until an approved price is applied; a proposed price is never
 *       used in its place. {@code appliedSequence} protects against applying an older decision.</li>
 *   <li>{@code saleEnabled} starts true but does not make the size buyable before publication.</li>
 * </ul>
 */
@Entity
@Table(name = "catalogue_variants",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_catalogue_variants_size", columnNames = {"product_id", "ring_size"}),
                @UniqueConstraint(name = "uk_catalogue_variants_sku", columnNames = "sku")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "ring_size", nullable = false, columnDefinition = "smallint")
    private int ringSize;

    @Column(name = "sku", nullable = false, length = SkuGenerator.MAX_LENGTH)
    private String sku;

    @Column(name = "remaining_unsold", nullable = false, precision = 19, scale = 0)
    private BigInteger remainingUnsold;

    @Column(name = "sale_enabled", nullable = false)
    private boolean saleEnabled;

    @Column(name = "applied_price", precision = 19, scale = 0)
    private BigInteger appliedPrice;

    @Column(name = "applied_proposal_id")
    private Long appliedProposalId;

    @Column(name = "applied_sequence", nullable = false)
    private long appliedSequence;

    @Column(name = "pending_proposal_id")
    private Long pendingProposalId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** Created together with its product; the SKU is derived from the persisted product number. */
    public static ProductVariant create(long productId, int size, BigInteger initialQuantity) {
        ProductVariant variant = new ProductVariant();
        variant.productId = productId;
        variant.ringSize = CatalogueValues.requireSize("size", size);
        variant.sku = SkuGenerator.sku(productId, size);
        variant.remainingUnsold = requireQuantity(initialQuantity);
        variant.saleEnabled = true;
        variant.appliedSequence = 0;
        return variant;
    }

    /**
     * Manual quantity update. {@code validHeld} must come from the FE-08 source read under the same
     * guard that is held until commit; a hold is never cancelled to make the new value fit.
     */
    public void updateRemainingUnsold(BigInteger quantity, BigInteger validHeld) {
        BigInteger value = requireQuantity(quantity);
        if (validHeld == null || validHeld.signum() < 0) {
            throw new IllegalArgumentException("validHeld must be a known nonnegative quantity");
        }
        if (value.compareTo(validHeld) < 0) {
            throw new CatalogueException(CatalogueErrorCode.QUANTITY_BELOW_HELD);
        }
        this.remainingUnsold = value;
    }

    /**
     * Deducts an accepted FE-08 sale. {@code heldAfterSettlement} is the held quantity read under the same
     * source guard after FE-08 settled the hold of this sale; the remaining quantity can go neither below
     * zero nor below it. Returns the remaining quantity after the deduction.
     */
    public BigInteger recordAcceptedSale(BigInteger quantity, BigInteger heldAfterSettlement) {
        if (quantity == null || quantity.signum() <= 0) {
            throw CatalogueException.invalidField("quantity", "Must be a positive whole number.");
        }
        if (heldAfterSettlement == null || heldAfterSettlement.signum() < 0) {
            throw new IllegalArgumentException("heldAfterSettlement must be a known nonnegative quantity");
        }
        BigInteger after = remainingUnsold.subtract(quantity);
        if (after.signum() < 0 || after.compareTo(heldAfterSettlement) < 0) {
            throw new CatalogueException(CatalogueErrorCode.QUANTITY_BELOW_HELD);
        }
        this.remainingUnsold = after;
        return after;
    }

    /** Returns whether the sale status changed; size, SKU, quantity and price stay as they are. */
    public boolean setSaleEnabled(boolean enabled) {
        if (saleEnabled == enabled) {
            return false;
        }
        this.saleEnabled = enabled;
        return true;
    }

    public void pointPendingProposal(Long proposalId) {
        this.pendingProposalId = Objects.requireNonNull(proposalId, "proposalId");
    }

    public void clearPendingProposal() {
        this.pendingProposalId = null;
    }

    /**
     * Applies the exact approved amount of the given revision. A decision created against an older
     * applied sequence is stale and cannot overwrite a newer price.
     */
    public void applyApprovedPrice(Long proposalId, BigInteger approvedAmount, long baseAppliedSequence) {
        if (baseAppliedSequence != appliedSequence) {
            throw new CatalogueException(CatalogueErrorCode.PRICE_APPLICATION_STALE);
        }
        if (approvedAmount == null || approvedAmount.signum() <= 0) {
            throw new CatalogueException(CatalogueErrorCode.PRICE_NOT_APPROVED);
        }
        this.appliedPrice = approvedAmount;
        this.appliedProposalId = Objects.requireNonNull(proposalId, "proposalId");
        this.appliedSequence++;
    }

    public boolean hasAppliedPrice() {
        return appliedPrice != null;
    }

    private static BigInteger requireQuantity(BigInteger quantity) {
        if (quantity == null || quantity.signum() < 0 || quantity.toString().length() > 19) {
            throw CatalogueException.invalidField("quantity", "Must be a nonnegative whole number of at most 19 digits.");
        }
        return quantity;
    }
}
