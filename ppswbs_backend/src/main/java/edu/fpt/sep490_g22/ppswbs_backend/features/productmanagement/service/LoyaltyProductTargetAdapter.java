package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.PromotionTargetPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetDimension;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetRef;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.PublicCatalogueQueryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.Product;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * PRODUCT-scope provider of FE-06's {@link PromotionTargetPort} (T091, interfaces "Target port", G-02).
 *
 * <p>For every requested PRODUCT or VARIANT target (key = the decimal product or variant ID) it returns one
 * immutable fact: whether it exists, belongs to its parent, is soft-deleted, is publicly visible and is on
 * sale. Loyalty decides from these facts; it rejects only a missing, foreign or soft-deleted target and keeps
 * unpublished, hidden or stopped targets valid (spec clarification 2026-10-26).
 *
 * <ul>
 *   <li>{@code belongsToParent}: a variant belongs when its product is one of the PRODUCT targets of the same
 *       request, or when the request has no PRODUCT target (dimensions combine with AND, so a variant of
 *       another product could never match). A product has no parent and always belongs.</li>
 *   <li>{@code publicVisible} uses the public catalogue eligibility (published, not deleted, active category,
 *       usable image, applied price for every size). {@code purchasable} additionally needs a sale-enabled
 *       size; FE-08 availability is not used (T091: no T019), so quantity and holds do not count here.</li>
 *   <li>A malformed key is reported as not existing. A database failure is {@code DEPENDENCY_UNAVAILABLE};
 *       nothing is allowed by default. No lock is taken and no remote call is made.</li>
 * </ul>
 */
@Component
public class LoyaltyProductTargetAdapter implements PromotionTargetPort {

    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final PublicCatalogueQueryRepository publicQueries;

    public LoyaltyProductTargetAdapter(ProductRepository products, ProductVariantRepository variants,
                                       PublicCatalogueQueryRepository publicQueries) {
        this.products = products;
        this.variants = variants;
        this.publicQueries = publicQueries;
    }

    @Override
    public boolean supports(TargetScope scope) {
        return scope == TargetScope.PRODUCT;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TargetFact> validateAndGuard(TargetScope scope, List<TargetRef> targets) {
        if (!supports(scope)) {
            throw LoyaltyException.invalidField("scope", "This provider serves product targets only.");
        }
        if (targets == null) {
            throw LoyaltyException.invalidField("targets", "Targets are required.");
        }
        for (TargetRef target : targets) {
            if (target.dimension() != TargetDimension.PRODUCT && target.dimension() != TargetDimension.VARIANT) {
                throw LoyaltyException.invalidField("targets", "Product programmes target products or variants only.");
            }
        }
        try {
            return facts(targets);
        } catch (DataAccessException e) {
            throw new LoyaltyException(LoyaltyErrorCode.DEPENDENCY_UNAVAILABLE,
                    "Product information is temporarily unavailable.");
        }
    }

    private List<TargetFact> facts(List<TargetRef> targets) {
        Set<Long> productTargets = ids(targets, TargetDimension.PRODUCT);
        Map<Long, ProductVariant> variantById = variants.findAllById(ids(targets, TargetDimension.VARIANT)).stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        Set<Long> productIds = new HashSet<>(productTargets);
        variantById.values().forEach(variant -> productIds.add(variant.getProductId()));
        Map<Long, Product> productById = products.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Set<Long> visible = new HashSet<>(publicQueries.findEligibleIds(productById.keySet()));
        Map<Long, List<ProductVariant>> sizesByProduct = variants
                .findByProductIdInOrderByProductIdAscRingSizeAsc(productById.keySet()).stream()
                .collect(Collectors.groupingBy(ProductVariant::getProductId));

        List<TargetFact> facts = new ArrayList<>(targets.size());
        for (TargetRef target : targets) {
            Optional<Long> id = parse(target.targetKey());
            if (target.dimension() == TargetDimension.PRODUCT) {
                Product product = id.map(productById::get).orElse(null);
                if (product == null) {
                    facts.add(missing(target));
                    continue;
                }
                boolean publicVisible = visible.contains(product.getId());
                boolean anySizeOnSale = sizesByProduct.getOrDefault(product.getId(), List.of()).stream()
                        .anyMatch(ProductVariant::isSaleEnabled);
                facts.add(new TargetFact(target.dimension(), target.targetKey(), true, true, product.isDeleted(),
                        publicVisible, publicVisible && anySizeOnSale));
            } else {
                ProductVariant variant = id.map(variantById::get).orElse(null);
                Product product = variant == null ? null : productById.get(variant.getProductId());
                if (product == null) {
                    facts.add(missing(target));
                    continue;
                }
                boolean belongs = productTargets.isEmpty() || productTargets.contains(product.getId());
                boolean publicVisible = visible.contains(product.getId());
                facts.add(new TargetFact(target.dimension(), target.targetKey(), true, belongs, product.isDeleted(),
                        publicVisible, publicVisible && variant.isSaleEnabled()));
            }
        }
        return List.copyOf(facts);
    }

    private static TargetFact missing(TargetRef target) {
        return new TargetFact(target.dimension(), target.targetKey(), false, false, false, false, false);
    }

    private static Set<Long> ids(List<TargetRef> targets, TargetDimension dimension) {
        return targets.stream()
                .filter(target -> target.dimension() == dimension)
                .map(target -> parse(target.targetKey()))
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());
    }

    /** Same rule as the API IDs: a positive decimal BIGINT without sign, spaces or leading zero. */
    private static Optional<Long> parse(String key) {
        if (key == null || !key.matches("[1-9][0-9]{0,18}")) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(key));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
