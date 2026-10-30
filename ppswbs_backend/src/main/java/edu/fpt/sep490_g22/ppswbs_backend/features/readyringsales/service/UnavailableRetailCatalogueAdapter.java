package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCataloguePort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!local")
public class UnavailableRetailCatalogueAdapter implements RetailCataloguePort {

    @Override
    public boolean isEligibleForCart(Long variantId, Long locationId) {
        throw new RetailIntegrationUnavailableException("Catalogue integration is not configured");
    }
}
