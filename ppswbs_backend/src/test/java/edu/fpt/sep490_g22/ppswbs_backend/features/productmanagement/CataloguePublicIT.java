package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.CatalogueMediaRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.PublicCatalogueDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.CatalogueMediaObject;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.DeleteProductService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.PriceApplicationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.PriceProposalService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductCategoryService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductPublicationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.PublicCatalogueQueryService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.UpdateProductService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.AvailabilityGuardPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.TestOnlyAvailabilityGuard;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.VariantSaleStatusService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.MANAGER;
import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.STAFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T066 on MySQL: public eligibility, search, filters, sorts and "can buy" on the same size
 * (FR-020/031–036). The FE-08 hold source does not exist yet, so this class alone binds a TEST-ONLY
 * {@link AvailabilityGuardPort} stub ({@link TestOnlyAvailabilityGuard}: default zero holds, settable per size).
 * It is not part of the runtime.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
@Import({CatalogueFlowFixture.class, CataloguePublicIT.StubHolds.class})
class CataloguePublicIT {

    /** Test-only stand-in for the FE-08 hold source (T019/T003). */
    @TestConfiguration
    static class StubHolds {
        static final TestOnlyAvailabilityGuard SOURCE = new TestOnlyAvailabilityGuard();

        // @Primary: the real FE-08 source (RetailAvailabilityGuardService) is also a bean since develop b0e7796;
        // these tests need controllable held quantities, the real source is covered by CatalogueQuantityIT.
        @Bean
        @Primary
        AvailabilityGuardPort testOnlyAvailabilityGuard() {
            return SOURCE;
        }
    }

    @Autowired private CatalogueFlowFixture fixture;
    @Autowired private PublicCatalogueQueryService catalogue;
    @Autowired private UpdateProductService updates;
    @Autowired private ProductPublicationService publication;
    @Autowired private DeleteProductService deletion;
    @Autowired private VariantSaleStatusService saleStatus;
    @Autowired private ProductCategoryService categories;
    @Autowired private PriceProposalService proposals;
    @Autowired private PriceApplicationService applyService;
    @Autowired private ProductVariantRepository variants;
    @Autowired private CatalogueMediaRepository media;
    @Autowired private TransactionTemplate transaction;

    private String tag;

    @BeforeEach
    void newTag() {
        tag = "tag" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        StubHolds.SOURCE.reset();
    }

    @Test
    void onlyPublishedNotDeletedProductsAreListedAndDetailOfOthersIsNotFound() {
        long visible = published("Hiện");
        long hidden = published("Ẩn");
        publication.setPublication(STAFF, UUID.randomUUID(), Long.toString(hidden), fixture.productVersion(hidden), false);
        long deleted = published("Xóa");
        deletion.delete(STAFF, UUID.randomUUID(), Long.toString(deleted), fixture.productVersion(deleted));
        long draft = fixture.draftProduct(STAFF);
        rename(draft, "Nháp");

        assertThat(ids(list(null, null, null, null))).containsExactly(visible);
        PublicCatalogueDtos.PublicProduct detail = catalogue.get(Long.toString(visible));
        assertThat(detail.images()).hasSize(1).allSatisfy(image -> assertThat(image.url()).startsWith("/api/v1/product-media/"));
        assertThat(detail.priceRange().minimum()).isEqualTo("150000");
        assertThat(detail.priceRange().maximum()).isEqualTo("180000");
        assertThat(detail.canBuy()).isTrue();
        for (long other : List.of(hidden, deleted, draft)) {
            assertCode(() -> catalogue.get(Long.toString(other)), CatalogueErrorCode.PRODUCT_NOT_FOUND);
        }
    }

    @Test
    void priceAndCanBuyMustHoldForTheSameSize() {
        long productId = published("Một");
        // size 5: 150000, quantity 3; size 12: 180000, quantity 0 (sold out)
        assertThat(ids(list("170000", null, true, null))).isEmpty();
        assertThat(ids(list("170000", null, null, null))).containsExactly(productId);
        assertThat(ids(list(null, "160000", true, null))).containsExactly(productId);

        ProductVariant size5 = sizes(productId).get(0);
        saleStatus.setSaleEnabled(STAFF, UUID.randomUUID(), size5.getId().toString(), size5.getVersion(), false);

        assertThat(ids(list(null, "160000", true, null))).isEmpty();
        PublicCatalogueDtos.PublicProduct stillListed = list(null, null, null, null).items().get(0);
        assertThat(stillListed.canBuy()).isFalse();
        assertThat(stillListed.variants().get(0).saleEnabled()).isFalse();
        assertThat(stillListed.variants()).hasSize(2);
    }

    @Test
    void heldQuantityIsSubtractedFromWhatCanBeBought() {
        long productId = published("Giữ");
        StubHolds.SOURCE.setHeld(sizes(productId).get(0).getId(), 3);

        assertThat(catalogue.get(Long.toString(productId)).canBuy()).isFalse();
        assertThat(ids(list(null, null, true, null))).isEmpty();
        assertThat(ids(list(null, null, null, null))).containsExactly(productId);
    }

    @Test
    void sortsByNamePriceAndCreationWithTiesById() {
        long first = published("B");
        long second = published("A");
        lowerPriceOfSize5(second, "100000");

        assertThat(ids(list(null, null, null, "name-asc"))).containsExactly(second, first);
        assertThat(ids(list(null, null, null, "price-asc"))).containsExactly(second, first);
        assertThat(ids(list(null, null, null, "price-desc"))).containsExactly(first, second);
        assertThat(ids(list(null, null, null, "newest"))).containsExactly(second, first);
        // Within the range only size 12 (180000) of "second" matches, so it sorts above "first"'s 150000.
        assertThat(ids(list("150000", null, null, "price-asc"))).containsExactly(first, second);
    }

    @Test
    void anInactiveCategoryOrNoUsableImageRemovesTheProductUntilRestored() {
        long productId = published("Điều kiện");
        long categoryId = fixture.categoryOf(productId);
        categories.setActive(STAFF, UUID.randomUUID(), Long.toString(categoryId), fixture.category(categoryId).getVersion(),
                false);
        assertThat(ids(list(null, null, null, null))).isEmpty();
        assertCode(() -> catalogue.list(new PublicCatalogueQueryService.Filter(tag, Long.toString(categoryId), null, null,
                null, null, 0)), CatalogueErrorCode.VALIDATION_ERROR);
        categories.setActive(STAFF, UUID.randomUUID(), Long.toString(categoryId), fixture.category(categoryId).getVersion(),
                true);
        assertThat(ids(list(null, null, null, null))).containsExactly(productId);

        UUID image = fixture.images(productId).get(0).getMediaObjectId();
        transaction.executeWithoutResult(status -> media.findById(image).orElseThrow().markUnavailable(Instant.now()));
        assertThat(ids(list(null, null, null, null))).isEmpty();
        transaction.executeWithoutResult(status -> media.findById(image).orElseThrow()
                .markReady("asset-" + image, "2", "png", Instant.now()));
        assertThat(ids(list(null, null, null, null))).containsExactly(productId);
    }

    @Test
    void percentAndUnderscoreInTheKeywordAreLiteralCharacters() {
        long percent = published("Ưu đãi 50%");
        published("Ưu đãi 500");
        long underscore = published("Nhẫn a_b");
        published("Nhẫn axb");

        assertThat(ids(keyword("50% " + tag))).containsExactly(percent);
        assertThat(ids(keyword("a_b " + tag))).containsExactly(underscore);
        assertThat(ids(keyword("%"))).doesNotContain(underscore);
    }

    @Test
    void pagesHoldTwentyProductsWithoutOverlapAndTheTotalCountsAll() {
        for (int i = 21; i >= 1; i--) {
            published(String.format("Trang %02d", i));
        }

        PublicCatalogueDtos.PublicProductPage first = catalogue.list(new PublicCatalogueQueryService.Filter(tag, null, null,
                null, null, "name-asc", 0));
        PublicCatalogueDtos.PublicProductPage second = catalogue.list(new PublicCatalogueQueryService.Filter(tag, null, null,
                null, null, "name-asc", 1));
        PublicCatalogueDtos.PublicProductPage beyond = catalogue.list(new PublicCatalogueQueryService.Filter(tag, null, null,
                null, null, "name-asc", 2));

        assertThat(first.items()).hasSize(20);
        assertThat(second.items()).hasSize(1);
        assertThat(beyond.items()).isEmpty();
        assertThat(first.totalElements()).isEqualTo("21");
        assertThat(first.pageSize()).isEqualTo(20);
        assertThat(first.items().get(0).name()).startsWith("Trang 01");
        assertThat(second.items().get(0).name()).startsWith("Trang 21");
        assertThat(ids(first)).doesNotContainAnyElementsOf(ids(second));
    }

    @Test
    void priceRangeBoundsAreInclusive() {
        long productId = published("Biên");
        // size 5: 150000, size 12: 180000
        assertThat(ids(list("150000", "150000", null, null))).containsExactly(productId);
        assertThat(ids(list("180000", "180000", null, null))).containsExactly(productId);
        assertThat(ids(list("150001", "179999", null, null))).isEmpty();
        assertThat(ids(list(null, "149999", null, null))).isEmpty();
        assertThat(ids(list("180001", null, null, null))).isEmpty();
    }

    @Test
    void proposedAndApprovedButUnappliedPricesNeverReachThePublicCatalogue() {
        long productId = published("Đề xuất");
        ProductVariant size5 = sizes(productId).get(0);
        long proposalId = Long.parseLong(proposals.create(STAFF, UUID.randomUUID(), size5.getId().toString(),
                size5.getVersion(), "100000").resourceId());
        proposals.submit(STAFF, UUID.randomUUID(), Long.toString(proposalId), fixture.proposal(proposalId).getVersion());

        assertPublicPriceOfSize5(productId, "150000");
        assertThat(ids(list(null, "120000", null, null))).isEmpty();

        proposals.decide(MANAGER, UUID.randomUUID(), Long.toString(proposalId), new PriceProposalService.DecisionCommand(
                fixture.proposal(proposalId).getVersion(), PriceProposalService.Decision.APPROVE, "100000", null));

        assertPublicPriceOfSize5(productId, "150000");
        assertThat(ids(list(null, "120000", null, null))).isEmpty();

        applyService.apply(STAFF, UUID.randomUUID(), Long.toString(proposalId), fixture.proposal(proposalId).getVersion());

        assertPublicPriceOfSize5(productId, "100000");
        assertThat(ids(list(null, "120000", null, null))).containsExactly(productId);
    }

    @Test
    void aKeywordOfExactlyTwoHundredCodePointsIsAccepted() {
        // Length is counted in Unicode code points after trimming: 200 is the maximum, 201 is refused.
        String exact = "  " + "ẫ".repeat(200) + "  ";
        assertThat(catalogue.list(new PublicCatalogueQueryService.Filter(exact, null, null, null, null, null, 0)).items())
                .isEmpty();
        assertCode(() -> catalogue.list(new PublicCatalogueQueryService.Filter("ẫ".repeat(201), null, null, null, null,
                null, 0)), CatalogueErrorCode.VALIDATION_ERROR);
    }

    private void assertPublicPriceOfSize5(long productId, String price) {
        PublicCatalogueDtos.PublicProduct detail = catalogue.get(Long.toString(productId));
        assertThat(detail.variants().get(0).appliedPrice()).isEqualTo(price);
        assertThat(detail.priceRange().minimum()).isEqualTo(price);
    }

    @Test
    void invalidCriteriaAreRefused() {
        assertCode(() -> catalogue.list(new PublicCatalogueQueryService.Filter(null, null, "200", "100", null, null, 0)),
                CatalogueErrorCode.VALIDATION_ERROR);
        assertCode(() -> catalogue.list(new PublicCatalogueQueryService.Filter(null, null, "-1", null, null, null, 0)),
                CatalogueErrorCode.VALIDATION_ERROR);
        assertCode(() -> catalogue.list(new PublicCatalogueQueryService.Filter(null, null, null, null, null, "cheap", 0)),
                CatalogueErrorCode.INVALID_SORT);
        assertCode(() -> catalogue.list(new PublicCatalogueQueryService.Filter("x".repeat(201), null, null, null, null,
                null, 0)), CatalogueErrorCode.VALIDATION_ERROR);
        assertCode(() -> catalogue.list(new PublicCatalogueQueryService.Filter(null, "999999999999", null, null, null,
                null, 0)), CatalogueErrorCode.VALIDATION_ERROR);
    }

    private long published(String label) {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        rename(productId, label);
        return productId;
    }

    /** Puts the tag in the name so every query of a test sees only its own products. */
    private void rename(long productId, String label) {
        updates.update(STAFF, UUID.randomUUID(), Long.toString(productId), fixture.productVersion(productId),
                new UpdateProductService.UpdateCommand(label + " " + tag, "Mô tả", Long.toString(fixture.categoryOf(productId))));
    }

    private void lowerPriceOfSize5(long productId, String amount) {
        ProductVariant size5 = sizes(productId).get(0);
        long proposalId = Long.parseLong(proposals.create(STAFF, UUID.randomUUID(), size5.getId().toString(),
                size5.getVersion(), amount).resourceId());
        proposals.submit(STAFF, UUID.randomUUID(), Long.toString(proposalId), fixture.proposal(proposalId).getVersion());
        proposals.decide(MANAGER, UUID.randomUUID(), Long.toString(proposalId), new PriceProposalService.DecisionCommand(
                fixture.proposal(proposalId).getVersion(), PriceProposalService.Decision.APPROVE, amount, null));
        applyService.apply(STAFF, UUID.randomUUID(), Long.toString(proposalId), fixture.proposal(proposalId).getVersion());
    }

    private PublicCatalogueDtos.PublicProductPage list(String minimum, String maximum, Boolean canBuy, String sort) {
        return catalogue.list(new PublicCatalogueQueryService.Filter("  " + tag.toUpperCase() + " ", null, minimum,
                maximum, canBuy, sort, 0));
    }

    private PublicCatalogueDtos.PublicProductPage keyword(String q) {
        return catalogue.list(new PublicCatalogueQueryService.Filter(q, null, null, null, null, null, 0));
    }

    private static List<Long> ids(PublicCatalogueDtos.PublicProductPage page) {
        return page.items().stream().map(item -> Long.parseLong(item.id())).toList();
    }

    private List<ProductVariant> sizes(long productId) {
        return transaction.execute(status -> variants.findByProductIdOrderByRingSizeAsc(productId));
    }

    private static void assertCode(ThrowableAssert.ThrowingCallable action, CatalogueErrorCode code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(CatalogueException.class,
                error -> assertThat(error.code()).isEqualTo(code));
    }
}
