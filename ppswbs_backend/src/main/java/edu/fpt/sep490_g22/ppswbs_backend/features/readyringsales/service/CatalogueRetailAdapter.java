package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.BranchCartSaleFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.BranchVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCataloguePort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Profile("!local")
@RequiredArgsConstructor
public class CatalogueRetailAdapter implements RetailCataloguePort {

    private final CatalogueSalesFacade catalogue;

    @Override
    public CartProductFact readCartProduct(Long variantId, Long locationId) {
        if (!validKey(variantId, locationId)) {
            return unavailable();
        }
        try {
            List<BranchCartSaleFact> facts = catalogue.readBranchSaleFacts(List.of(new BranchVariant(variantId, locationId)));
            if (facts == null || facts.size() != 1 || facts.getFirst() == null
                || facts.getFirst().variantId() != variantId || facts.getFirst().locationId() != locationId) {
                throw new RetailIntegrationUnavailableException("Catalogue returned unverifiable branch sale facts");
            }
            BranchCartSaleFact fact = facts.getFirst();
            return new CartProductFact(fact.productName(), fact.sku(), fact.size(),
                fact.appliedPrice() == null ? null : fact.appliedPrice().toString(), fact.currency(),
                fact.availableQuantity().toString(), fact.saleEligible());
        } catch (CatalogueSalesException exception) {
            if (exception.reason() == CatalogueSalesException.Reason.NOT_FOUND) {
                return unavailable();
            }
            throw new RetailIntegrationUnavailableException("Catalogue integration is unavailable");
        }
    }

    @Override
    public boolean isEligibleForCart(Long variantId, Long locationId) {
        if (!validKey(variantId, locationId)) {
            return false;
        }
        try {
            List<BranchCartSaleFact> facts = catalogue.readBranchSaleFacts(List.of(new BranchVariant(variantId, locationId)));
            if (facts == null || facts.size() != 1 || facts.getFirst() == null
                || facts.getFirst().variantId() != variantId || facts.getFirst().locationId() != locationId) {
                throw new RetailIntegrationUnavailableException("Catalogue returned unverifiable branch sale facts");
            }
            return facts.getFirst().saleEligible();
        } catch (CatalogueSalesException exception) {
            if (exception.reason() == CatalogueSalesException.Reason.NOT_FOUND) {
                return false;
            }
            throw new RetailIntegrationUnavailableException("Catalogue integration is unavailable");
        }
    }

    private boolean validKey(Long variantId, Long locationId) {
        return variantId != null && variantId > 0 && locationId != null && locationId > 0;
    }

    private CartProductFact unavailable() {
        return new CartProductFact(null, null, null, null, null, null, false);
    }
}
