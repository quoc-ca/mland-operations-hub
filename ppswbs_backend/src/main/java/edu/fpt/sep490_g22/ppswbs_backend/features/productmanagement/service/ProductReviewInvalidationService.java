package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.PriceProposalRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductReviewRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.PriceProposal;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.Product;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductReview;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ReviewState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Before the first publication, any change to reviewable content (name, description, category, images)
 * or to a proposed price withdraws the submitted/decided revision and starts a new DRAFT revision
 * (T032, FR-011/013). Decisions and applications already recorded stay as history; they simply no longer
 * count for publication. Quantity, holds and sale status never invalidate a review. After the first
 * publication this service does nothing: content is edited directly and prices follow their own proposals.
 *
 * <p>Runs inside the caller's transaction after the caller locked the product and its sizes and already
 * applied the change to the product (so {@code product.contentVersion} is current).
 */
@Service
@RequiredArgsConstructor
public class ProductReviewInvalidationService {

    private final ProductReviewRepository reviews;
    private final PriceProposalRepository proposals;

    /**
     * @param priceChanges new proposed amounts by variant ID; empty when only content changed
     * @return the current DRAFT review, or {@code null} when the product was already published once
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ProductReview invalidateBeforeFirstPublication(Product product, Collection<ProductVariant> variants,
                                                          Map<Long, BigInteger> priceChanges) {
        Objects.requireNonNull(priceChanges, "priceChanges");
        if (product.hasEverBeenPublished()) {
            return null;
        }
        ProductReview current = reviews.findFirstByProductIdOrderByRevisionDesc(product.getId())
                .orElseThrow(() -> new CatalogueException(CatalogueErrorCode.REVIEW_NOT_CURRENT));
        Map<Long, PriceProposal> currentByVariant = latestProposals(current.getId());

        boolean stillDraft = current.getState() == ReviewState.DRAFT && current.isEffective();
        ProductReview target;
        if (stillDraft) {
            current.followContentVersion(product.getContentVersion());
            target = current;
        } else {
            current.makeIneffective();
            target = reviews.saveAndFlush(ProductReview.draft(product.getId(), current.getRevision() + 1,
                    product.getContentVersion()));
        }

        for (ProductVariant variant : variants) {
            PriceProposal old = currentByVariant.get(variant.getId());
            BigInteger newAmount = priceChanges.get(variant.getId());
            boolean priceChanged = newAmount != null && (old == null || !newAmount.equals(old.getProposedAmount()));
            if (stillDraft && !priceChanged) {
                continue;
            }
            BigInteger amount = newAmount != null ? newAmount : old == null ? null : old.getProposedAmount();
            if (amount == null) {
                continue;
            }
            if (old != null) {
                old.makeIneffective();
            }
            if (variant.getPendingProposalId() != null) {
                variant.clearPendingProposal();
            }
            proposals.saveAndFlush(PriceProposal.draft(variant.getId(), target.getId(),
                    old == null ? 1 : old.getRevision() + 1, amount, variant.getAppliedSequence(),
                    ProposalTerms.digest(variant, target.getId()), old == null ? null : old.getId()));
        }
        return target;
    }

    /** Latest effective revision of each size's proposal in the given review. */
    private Map<Long, PriceProposal> latestProposals(Long reviewId) {
        List<PriceProposal> all = proposals.findByReviewIdOrderByIdAsc(reviewId);
        return all.stream()
                .filter(PriceProposal::isEffective)
                .collect(Collectors.toMap(PriceProposal::getVariantId, Function.identity(),
                        (a, b) -> Comparator.comparingLong(PriceProposal::getRevision).compare(a, b) >= 0 ? a : b));
    }
}
