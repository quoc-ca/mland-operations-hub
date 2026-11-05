package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.PriceProposal;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Price terms a decision is bound to (data-model {@code terms_digest}): the variant, its size, the currency
 * and the review context. Quantity is not a price term. Applying a decision recomputes the digest so a
 * decision can never be applied to different terms.
 */
public final class ProposalTerms {

    private ProposalTerms() {
    }

    public static String digest(ProductVariant variant, Long reviewId) {
        Map<String, Object> terms = new LinkedHashMap<>();
        terms.put("variantId", variant.getId());
        terms.put("size", variant.getRingSize());
        terms.put("currency", PriceProposal.CURRENCY);
        terms.put("reviewId", reviewId);
        return CatalogueCommandExecutor.digest(terms);
    }
}
