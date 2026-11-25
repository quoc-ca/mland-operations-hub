package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.PromotionSeedCataloguePort;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SellingLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SellingLocationPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 002's side of 006 {@link PromotionSeedCataloguePort} (006 T116): turns the names in the sample promotion programmes
 * into ids from 002's own catalogue and from the branch list 002 already reads through {@link SellingLocationPort}.
 * Read-only. Without a branch provider no branch is found, so a branch-limited sample programme is skipped.
 */
@Component
public class LoyaltySeedCatalogueAdapter implements PromotionSeedCataloguePort {

    private final ProductRepository products;
    private final ObjectProvider<SellingLocationPort> locations;

    public LoyaltySeedCatalogueAdapter(ProductRepository products, ObjectProvider<SellingLocationPort> locations) {
        this.products = products;
        this.locations = locations;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> productIdsInCategory(String categoryName) {
        return products.findLiveIdsByCategoryName(categoryName);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> productIdsNamed(String productName) {
        return products.findLiveIdsByName(productName);
    }

    @Override
    @Transactional(readOnly = true)
    public long publishedProductCount(String categoryName) {
        return products.countPublishedByCategoryName(categoryName);
    }

    @Override
    public List<Long> activeBranchIdsNamed(String displayName) {
        SellingLocationPort port = locations.getIfAvailable();
        if (port == null) {
            return List.of();
        }
        return port.activeSellingLocations().stream()
                .filter(location -> location.displayName().equals(displayName))
                .map(SellingLocation::id)
                .sorted()
                .toList();
    }
}
