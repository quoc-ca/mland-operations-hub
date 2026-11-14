package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.BranchCartSaleFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.BranchVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.CatalogueRetailAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailIntegrationUnavailableException;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogueRetailAdapterTest {
    private final CatalogueSalesFacade catalogue = mock(CatalogueSalesFacade.class);
    private final CatalogueRetailAdapter adapter = new CatalogueRetailAdapter(catalogue);

    @Test
    void usesCatalogueEligibilityForTheRequestedVariant() {
        when(catalogue.readBranchSaleFacts(List.of(key(101L, 10L)))).thenReturn(List.of(fact(101L, 10L, true)));
        when(catalogue.readBranchSaleFacts(List.of(key(102L, 10L)))).thenReturn(List.of(fact(102L, 10L, false)));

        assertThat(adapter.isEligibleForCart(101L, 10L)).isTrue();
        assertThat(adapter.isEligibleForCart(102L, 10L)).isFalse();
    }

    @Test
    void missingVariantCannotBeAdded() {
        when(catalogue.readBranchSaleFacts(List.of(key(101L, 10L))))
            .thenThrow(new CatalogueSalesException(CatalogueSalesException.Reason.NOT_FOUND, "Missing"));

        assertThat(adapter.isEligibleForCart(101L, 10L)).isFalse();
        assertThat(adapter.readCartProduct(101L, 10L).saleEligible()).isFalse();
    }

    @Test
    void readsIndicativePriceAndAvailabilityFromCatalogue() {
        when(catalogue.readBranchSaleFacts(List.of(key(101L, 10L)))).thenReturn(List.of(fact(101L, 10L, true)));

        var result = adapter.readCartProduct(101L, 10L);

        assertThat(result.indicativePrice()).isEqualTo("10");
        assertThat(result.availableQuantity()).isEqualTo("1");
        assertThat(result.currency()).isEqualTo("VND");
        assertThat(result.saleEligible()).isTrue();
    }

    @Test
    void unavailableOrUnexpectedCatalogueFactsFailClosed() {
        when(catalogue.readBranchSaleFacts(List.of(key(101L, 10L))))
            .thenThrow(new CatalogueSalesException(CatalogueSalesException.Reason.DEPENDENCY_UNAVAILABLE, "Unavailable"));
        when(catalogue.readBranchSaleFacts(List.of(key(102L, 10L)))).thenReturn(List.of(fact(999L, 10L, true)));

        assertThatThrownBy(() -> adapter.isEligibleForCart(101L, 10L))
            .isInstanceOf(RetailIntegrationUnavailableException.class);
        assertThatThrownBy(() -> adapter.isEligibleForCart(102L, 10L))
            .isInstanceOf(RetailIntegrationUnavailableException.class);
    }

    private BranchVariant key(long variantId, long locationId) {
        return new BranchVariant(variantId, locationId);
    }

    private BranchCartSaleFact fact(long variantId, long locationId, boolean eligible) {
        return new BranchCartSaleFact(variantId, locationId, 1L, "Ring", "SKU", 10,
            BigInteger.TEN, "VND", eligible, BigInteger.ONE, true);
    }
}
