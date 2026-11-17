package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetDimension;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetRef;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.PublicCatalogueQueryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SellingLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SellingLocationPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.LoyaltyProductTargetAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * T084 (FE-06 Session 2026-11-09): BRANCH facts for a product voucher come from {@link SellingLocationPort}. An
 * active branch is purchasable; any other well-formed ID is not; a missing or failing source is DEPENDENCY_UNAVAILABLE,
 * and the source is not read when no branch is asked for.
 */
class LoyaltyProductTargetAdapterBranchTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final ProductVariantRepository variants = mock(ProductVariantRepository.class);
    private final PublicCatalogueQueryRepository publicQueries = mock(PublicCatalogueQueryRepository.class);
    private final SellingLocationPort port = mock(SellingLocationPort.class);

    @SuppressWarnings("unchecked")
    private LoyaltyProductTargetAdapter adapter(SellingLocationPort source) {
        ObjectProvider<SellingLocationPort> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(source);
        return new LoyaltyProductTargetAdapter(products, variants, publicQueries, provider);
    }

    private static TargetRef branch(String key) {
        return new TargetRef(TargetDimension.BRANCH, key);
    }

    @Test
    void anActiveBranchIsPurchasableAndAnyOtherIsNot() {
        when(port.activeSellingLocations()).thenReturn(List.of(new SellingLocation(7, "Hoàn Kiếm", "Hà Nội")));

        List<TargetFact> facts = adapter(port).validateAndGuard(TargetScope.PRODUCT,
                List.of(branch("7"), branch("8"), branch("07")));

        assertThat(facts.get(0)).satisfies(fact -> {
            assertThat(fact.exists()).isTrue();
            assertThat(fact.purchasable()).isTrue();
            assertThat(fact.publicVisible()).isTrue();
        });
        assertThat(facts.get(1)).satisfies(fact -> {
            assertThat(fact.exists()).isTrue();
            assertThat(fact.purchasable()).isFalse();
        });
        assertThat(facts.get(2).exists()).isFalse();
    }

    @Test
    void aMissingOrFailingBranchSourceIsDependencyUnavailable() {
        assertThatThrownBy(() -> adapter(null).validateAndGuard(TargetScope.PRODUCT, List.of(branch("7"))))
                .isInstanceOfSatisfying(LoyaltyException.class,
                        e -> assertThat(e.code()).isEqualTo(LoyaltyErrorCode.DEPENDENCY_UNAVAILABLE));
        when(port.activeSellingLocations()).thenThrow(new IllegalStateException("down"));
        assertThatThrownBy(() -> adapter(port).validateAndGuard(TargetScope.PRODUCT, List.of(branch("7"))))
                .isInstanceOfSatisfying(LoyaltyException.class,
                        e -> assertThat(e.code()).isEqualTo(LoyaltyErrorCode.DEPENDENCY_UNAVAILABLE));
    }

    @Test
    void theBranchSourceIsNotReadWithoutABranchTarget() {
        adapter(port).validateAndGuard(TargetScope.PRODUCT, List.of(new TargetRef(TargetDimension.PRODUCT, "1")));
        verifyNoInteractions(port);
    }
}
