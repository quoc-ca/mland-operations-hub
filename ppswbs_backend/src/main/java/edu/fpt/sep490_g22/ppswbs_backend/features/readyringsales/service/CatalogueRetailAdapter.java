package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CartSaleFact;
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
    public CartProductFact readCartProduct(Long variantId) {
        try {
            List<CartSaleFact> facts = catalogue.readSaleFacts(List.of(variantId));
            if (facts == null || facts.size() != 1 || facts.getFirst() == null
                || facts.getFirst().variantId() != variantId) {
                throw new RetailIntegrationUnavailableException("Catalogue returned unverifiable variant facts");
            }
            CartSaleFact fact = facts.getFirst();
            return new CartProductFact(fact.productName(), fact.sku(), fact.size(),
                fact.appliedPrice() == null ? null : fact.appliedPrice().toString(), fact.currency(),
                fact.availableQuantity().toString(), fact.saleEligible());
        } catch (CatalogueSalesException exception) {
            if (exception.reason() == CatalogueSalesException.Reason.NOT_FOUND) {
                return new CartProductFact(null, null, null, null, null, null, false);
            }
            throw new RetailIntegrationUnavailableException("Catalogue integration is unavailable");
        }
    }

    @Override
    public boolean isEligibleForCart(Long variantId, Long locationId) {
        if (variantId == null || variantId <= 0 || locationId == null || locationId <= 0) {
            return false;
        }
        try {
            List<CartSaleFact> facts = catalogue.readSaleFacts(List.of(variantId));
            if (facts == null || facts.size() != 1 || facts.getFirst() == null || facts.getFirst().variantId() != variantId) {
                throw new RetailIntegrationUnavailableException("Catalogue returned unverifiable variant facts");
            }
            return facts.getFirst().saleEligible();
        } catch (CatalogueSalesException exception) {
            if (exception.reason() == CatalogueSalesException.Reason.NOT_FOUND) {
                return false;
            }
            throw new RetailIntegrationUnavailableException("Catalogue integration is unavailable");
        }
    }
}
