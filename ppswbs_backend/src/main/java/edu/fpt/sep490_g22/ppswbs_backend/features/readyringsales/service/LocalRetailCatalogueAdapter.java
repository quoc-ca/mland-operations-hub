package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCataloguePort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Development-only FE-02 substitute. Prefix a test variant with "unavailable-" to reject it. */
@Service
@Profile("local")
public class LocalRetailCatalogueAdapter implements RetailCataloguePort {

    @Override
    public boolean isEligibleForCart(String variantId, Long locationId) {
        return variantId != null
            && !variantId.isBlank()
            && locationId != null
            && locationId > 0
            && !variantId.startsWith("unavailable-");
    }
}
