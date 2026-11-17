package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCataloguePort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Development-only FE-02 substitute. Real catalogue sizes use their catalogue facts; unknown sizes keep the demo
 * values. Variant ID 999999 is reserved for an unavailable test case.
 */
@Service
@Profile("local")
@RequiredArgsConstructor
public class LocalRetailCatalogueAdapter implements RetailCataloguePort {
    private final LocalCatalogueFacts catalogue;

    @Override
    public CartProductFact readCartProduct(Long variantId, Long locationId) {
        if (!validKey(variantId, locationId)) {
            return new CartProductFact("Demo ring", "DEMO-" + variantId, null, null, "VND", "0", false);
        }
        return catalogue.read(variantId, locationId)
            .map(fact -> new CartProductFact(fact.productName(), fact.sku(), fact.size(),
                fact.appliedPrice() == null ? null : fact.appliedPrice().toString(), fact.currency(),
                String.valueOf(fact.availableQuantity()), fact.saleEligible()))
            .orElseGet(() -> new CartProductFact("Demo ring", "DEMO-" + variantId, null,
                LocalCatalogueFacts.demoPrice(variantId).toString(), "VND", "99", true));
    }

    @Override
    public boolean isEligibleForCart(Long variantId, Long locationId) {
        if (!validKey(variantId, locationId)) return false;
        return catalogue.read(variantId, locationId).map(fact -> fact.saleEligible()).orElse(true);
    }

    private boolean validKey(Long variantId, Long locationId) {
        return variantId != null && variantId > 0 && locationId != null && locationId > 0 && variantId != 999999L;
    }
}
