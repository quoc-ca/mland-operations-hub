package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.PromotionTargetPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetDimension;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetRef;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.PublicCatalogueQueryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.DeleteProductService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.LoyaltyProductTargetAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductPublicationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.VariantSaleStatusService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.MANAGER;
import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.STAFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T091 on MySQL: facts returned to FE-06 for PRODUCT/VARIANT targets — valid, missing, foreign, deleted,
 * hidden and stopped targets — and a safe {@code DEPENDENCY_UNAVAILABLE} when the catalogue cannot be read.
 * Loyalty rejects only missing, foreign or deleted targets; that decision is FE-06's and tested there.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
@Import(CatalogueFlowFixture.class)
class LoyaltyProductTargetAdapterIT {

    @Autowired private CatalogueFlowFixture fixture;
    @Autowired private List<PromotionTargetPort> providers;
    @Autowired private LoyaltyProductTargetAdapter adapter;
    @Autowired private ProductPublicationService publication;
    @Autowired private DeleteProductService deletion;
    @Autowired private VariantSaleStatusService saleStatus;
    @Autowired private ProductVariantRepository variants;
    @Autowired private TransactionTemplate transaction;

    @Test
    void theCatalogueIsTheProviderForProductScopeOnly() {
        assertThat(providers).filteredOn(provider -> provider.supports(TargetScope.PRODUCT)).containsExactly(adapter);
        assertThat(adapter.supports(TargetScope.WORKSHOP)).isFalse();
        assertThatThrownBy(() -> adapter.validateAndGuard(TargetScope.WORKSHOP, List.of()))
                .isInstanceOfSatisfying(LoyaltyException.class,
                        error -> assertThat(error.code()).isEqualTo(LoyaltyErrorCode.INVALID_INPUT));
        assertThatThrownBy(() -> adapter.validateAndGuard(TargetScope.PRODUCT,
                List.of(new TargetRef(TargetDimension.BRANCH, "1"))))
                .isInstanceOfSatisfying(LoyaltyException.class,
                        error -> assertThat(error.code()).isEqualTo(LoyaltyErrorCode.INVALID_INPUT));
    }

    @Test
    void aPublishedProductAndItsSizesAreValidVisibleAndOnSale() {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        List<ProductVariant> sizes = sizes(productId);

        List<TargetFact> facts = adapter.validateAndGuard(TargetScope.PRODUCT, List.of(
                product(productId), variant(sizes.get(0).getId()), variant(sizes.get(1).getId())));

        assertThat(facts).hasSize(3).allSatisfy(fact -> {
            assertThat(fact.exists()).isTrue();
            assertThat(fact.belongsToParent()).isTrue();
            assertThat(fact.softDeleted()).isFalse();
            assertThat(fact.publicVisible()).isTrue();
        });
        assertThat(facts.get(0).purchasable()).isTrue();
        // Sale status, not stock: size 12 has quantity 0 but is still "on sale" (FE-08 availability is not used).
        assertThat(facts.get(2).purchasable()).isTrue();
        assertThat(facts).extracting(TargetFact::targetKey).containsExactly(Long.toString(productId),
                sizes.get(0).getId().toString(), sizes.get(1).getId().toString());
    }

    @Test
    void aVariantMustBelongToOneOfTheProductTargetsOfTheSameRequest() {
        long productId = fixture.draftProduct(STAFF);
        long otherProductId = fixture.draftProduct(STAFF);
        long variantId = sizes(productId).get(0).getId();

        TargetFact alone = adapter.validateAndGuard(TargetScope.PRODUCT, List.of(variant(variantId))).get(0);
        TargetFact withOwnProduct = adapter.validateAndGuard(TargetScope.PRODUCT,
                List.of(product(productId), variant(variantId))).get(1);
        TargetFact withOtherProduct = adapter.validateAndGuard(TargetScope.PRODUCT,
                List.of(product(otherProductId), variant(variantId))).get(1);

        assertThat(alone.belongsToParent()).isTrue();
        assertThat(withOwnProduct.belongsToParent()).isTrue();
        assertThat(withOtherProduct.exists()).isTrue();
        assertThat(withOtherProduct.belongsToParent()).isFalse();
    }

    @Test
    void missingOrMalformedTargetsDoNotExist() {
        List<TargetFact> facts = adapter.validateAndGuard(TargetScope.PRODUCT, List.of(
                product("999999999999999"), variant("999999999999999"), product("abc"), product("007"),
                variant("-1")));

        assertThat(facts).hasSize(5).allSatisfy(fact -> {
            assertThat(fact.exists()).isFalse();
            assertThat(fact.publicVisible()).isFalse();
            assertThat(fact.purchasable()).isFalse();
        });
    }

    @Test
    void deletedHiddenUnpublishedAndStoppedTargetsAreReportedAsTheyAre() {
        long deleted = fixture.publishedProduct(STAFF, MANAGER);
        deletion.delete(STAFF, UUID.randomUUID(), Long.toString(deleted), fixture.productVersion(deleted));
        long hidden = fixture.publishedProduct(STAFF, MANAGER);
        publication.setPublication(STAFF, UUID.randomUUID(), Long.toString(hidden), fixture.productVersion(hidden), false);
        long draft = fixture.draftProduct(STAFF);
        long stopped = fixture.publishedProduct(STAFF, MANAGER);
        List<ProductVariant> stoppedSizes = sizes(stopped);
        for (ProductVariant size : stoppedSizes) {
            saleStatus.setSaleEnabled(STAFF, UUID.randomUUID(), size.getId().toString(), size.getVersion(), false);
        }

        List<TargetFact> facts = adapter.validateAndGuard(TargetScope.PRODUCT, List.of(
                product(deleted), variant(sizes(deleted).get(0).getId()), product(hidden), product(draft),
                product(stopped), variant(stoppedSizes.get(0).getId())));

        TargetFact deletedProduct = facts.get(0);
        assertThat(deletedProduct.exists()).isTrue();
        assertThat(deletedProduct.softDeleted()).isTrue();
        assertThat(deletedProduct.publicVisible()).isFalse();
        assertThat(facts.get(1).softDeleted()).as("a variant of a deleted product is deleted too").isTrue();
        assertThat(facts.subList(2, 6)).allSatisfy(fact -> {
            assertThat(fact.exists()).isTrue();
            assertThat(fact.softDeleted()).isFalse();
            assertThat(fact.purchasable()).isFalse();
        });
        assertThat(facts.get(2).publicVisible()).as("hidden").isFalse();
        assertThat(facts.get(3).publicVisible()).as("never published").isFalse();
        assertThat(facts.get(4).publicVisible()).as("published, every size stopped").isTrue();
    }

    @Test
    void aCatalogueReadFailureIsASafeDependencyErrorNeverAnAllow() {
        ProductRepository products = mock(ProductRepository.class);
        ProductVariantRepository failingVariants = mock(ProductVariantRepository.class);
        when(failingVariants.findAllById(any())).thenThrow(new DataAccessResourceFailureException("connection refused"));
        LoyaltyProductTargetAdapter broken = new LoyaltyProductTargetAdapter(products, failingVariants,
                mock(PublicCatalogueQueryRepository.class));

        assertThatThrownBy(() -> broken.validateAndGuard(TargetScope.PRODUCT, List.of(product(1))))
                .isInstanceOfSatisfying(LoyaltyException.class, error -> {
                    assertThat(error.code()).isEqualTo(LoyaltyErrorCode.DEPENDENCY_UNAVAILABLE);
                    assertThat(error.getMessage()).doesNotContain("connection refused");
                });
    }

    private List<ProductVariant> sizes(long productId) {
        return transaction.execute(status -> variants.findByProductIdOrderByRingSizeAsc(productId));
    }

    private static TargetRef product(long id) {
        return product(Long.toString(id));
    }

    private static TargetRef product(String key) {
        return new TargetRef(TargetDimension.PRODUCT, key);
    }

    private static TargetRef variant(long id) {
        return variant(Long.toString(id));
    }

    private static TargetRef variant(String key) {
        return new TargetRef(TargetDimension.VARIANT, key);
    }
}
