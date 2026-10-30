package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCataloguePort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Development-only FE-02 substitute. Variant ID 999999 is reserved for an unavailable test case. */
@Service
@Profile("local")
public class LocalRetailCatalogueAdapter implements RetailCataloguePort {

    @Override
    public CartProductFact readCartProduct(Long variantId) {
        boolean eligible = variantId != null && variantId > 0 && variantId != 999999L;
        String price = eligible ? Long.toString(500_000L + Math.floorMod(variantId, 500_000L)) : null;
        return new CartProductFact("Demo ring", "DEMO-" + variantId, null, price, "VND", eligible ? "99" : "0", eligible);
    }

    @Override
    public boolean isEligibleForCart(Long variantId, Long locationId) {
        return variantId != null
            && variantId > 0
            && locationId != null
            && locationId > 0
            && variantId != 999999L;
    }
}
