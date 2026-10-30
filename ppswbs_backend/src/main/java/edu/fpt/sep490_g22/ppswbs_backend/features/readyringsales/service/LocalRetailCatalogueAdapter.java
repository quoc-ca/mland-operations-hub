package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCataloguePort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Development-only FE-02 substitute. Variant ID 999999 is reserved for an unavailable test case. */
@Service
@Profile("local")
public class LocalRetailCatalogueAdapter implements RetailCataloguePort {

    @Override
    public boolean isEligibleForCart(Long variantId, Long locationId) {
        return variantId != null
            && variantId > 0
            && locationId != null
            && locationId > 0
            && variantId != 999999L;
    }
}
