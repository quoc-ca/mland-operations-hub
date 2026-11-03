package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.PriceProposalRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductCategoryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductImageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductReviewRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.ManagementProductDtos.ManagementProductPage;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.PriceProposal;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.Product;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductCategory;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** T026: management search keyword, 20/page, separate price/review fields and authoritative held quantity. */
class ManagementProductQueryServiceTests {

    private ProductRepository products;
    private ProductVariantRepository variants;
    private ProductCategoryRepository categories;
    private ProductImageRepository images;
    private ProductReviewRepository reviews;
    private PriceProposalRepository proposals;
    private ObjectProvider<VariantHoldReader> holdReader;
    private ManagementProductQueryService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        products = mock(ProductRepository.class);
        variants = mock(ProductVariantRepository.class);
        categories = mock(ProductCategoryRepository.class);
        images = mock(ProductImageRepository.class);
        reviews = mock(ProductReviewRepository.class);
        proposals = mock(PriceProposalRepository.class);
        holdReader = mock(ObjectProvider.class);
        service = new ManagementProductQueryService(products, variants, categories, images, reviews, proposals,
                holdReader);
    }

    @Test
    void keywordMatchesProductNumberWithOrWithoutPrefix() {
        assertThat(ManagementProductQueryService.Keyword.parse(" SP000123 ").productNumber()).isEqualTo(123L);
        assertThat(ManagementProductQueryService.Keyword.parse("123").productNumber()).isEqualTo(123L);
        assertThat(ManagementProductQueryService.Keyword.parse("nhẫn").productNumber()).isNull();
    }

    @Test
    void keywordRecognisesExactSkuCaseInsensitively() {
        ManagementProductQueryService.Keyword keyword = ManagementProductQueryService.Keyword.parse("sp000123-s05");
        assertThat(keyword.sku()).isEqualTo("SP000123-S05");
        assertThat(ManagementProductQueryService.Keyword.parse("SP000123-S30").sku()).isNull();
    }

    @Test
    void keywordTreatsLikeWildcardsLiterally() {
        assertThat(ManagementProductQueryService.Keyword.parse("50%_off!").namePattern()).isEqualTo("%50!%!_off!!%");
    }

    @Test
    void blankKeywordIsNoFilterAndLongKeywordIsRejected() {
        assertThat(ManagementProductQueryService.Keyword.parse("   ").anyKeyword()).isTrue();
        assertThatThrownBy(() -> ManagementProductQueryService.Keyword.parse("a".repeat(201)))
                .isInstanceOf(CatalogueException.class);
    }

    @Test
    void listUsesTwentyPerPageAndKeepsSearchOrder() {
        Product newer = product(2L, false);
        Product older = product(1L, false);
        stubSearch(List.of(2L, 1L), 21);
        when(products.findAllById(List.of(2L, 1L))).thenReturn(List.of(older, newer));
        when(categories.findAllById(any())).thenReturn(List.of(category(5L)));
        when(variants.findByProductIdInOrderByProductIdAscRingSizeAsc(List.of(2L, 1L))).thenReturn(List.of());

        ManagementProductPage page = service.list(null, null, null, 1);

        verify(products).searchManagementIds(isNull(), isNull(), eq(true), isNull(), isNull(), isNull(),
                eq(PageRequest.of(1, 20)));
        assertThat(page.pageSize()).isEqualTo(20);
        assertThat(page.totalElements()).isEqualTo("21");
        assertThat(page.items()).extracting(item -> item.id()).containsExactly("2", "1");
    }

    @Test
    void neverPublishedProductHasProvenZeroHoldAndSeparatePriceFields() throws Exception {
        Product draft = product(123L, false);
        ProductVariant variant = variant(10L, 123L, 12, 7);
        PriceProposal proposal = PriceProposal.draft(10L, null, 1, BigInteger.valueOf(150_000), 0, "d", null);
        setField(proposal, "id", 99L);
        stubSearch(List.of(123L), 1);
        when(products.findAllById(List.of(123L))).thenReturn(List.of(draft));
        when(categories.findAllById(any())).thenReturn(List.of(category(5L)));
        when(variants.findByProductIdInOrderByProductIdAscRingSizeAsc(List.of(123L))).thenReturn(List.of(variant));
        when(proposals.findByVariantIdInOrderByVariantIdAscRevisionAscIdAsc(List.of(10L))).thenReturn(List.of(proposal));

        ManagementProductPage page = service.list("SP000123-S12", null, null, null);

        var dto = page.items().get(0).variants().get(0);
        assertThat(dto.sku()).isEqualTo("SP000123-S12");
        assertThat(dto.validHeldQuantity()).isEqualTo("0");
        assertThat(dto.availableQuantity()).isEqualTo("7");
        assertThat(dto.appliedPrice()).isNull();
        assertThat(dto.proposals().get(0).proposedPrice()).isEqualTo("150000");
        assertThat(dto.proposals().get(0).applied()).isFalse();

        ObjectMapper json = Jackson2ObjectMapperBuilder.json().build();
        String body = json.writeValueAsString(page.items().get(0));
        assertThat(body).contains("\"firstPublishedAt\":null").contains("\"deletedAt\":null")
                .doesNotContain("appliedPrice").doesNotContain("approvedPrice");
    }

    @Test
    void everPublishedProductWithoutHoldSourceIsUnavailableNeverZero() {
        Product published = product(7L, true);
        stubSearch(List.of(7L), 1);
        when(products.findAllById(List.of(7L))).thenReturn(List.of(published));
        when(categories.findAllById(any())).thenReturn(List.of(category(5L)));
        when(variants.findByProductIdInOrderByProductIdAscRingSizeAsc(List.of(7L)))
                .thenReturn(List.of(variant(70L, 7L, 5, 10)));
        when(holdReader.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> service.list(null, null, null, null))
                .isInstanceOfSatisfying(CatalogueException.class, error -> assertThat(error.code())
                        .isEqualTo(CatalogueErrorCode.AVAILABILITY_DEPENDENCY_UNAVAILABLE));
    }

    @Test
    void holdSourceGivesAvailableQuantity() {
        Product published = product(7L, true);
        stubSearch(List.of(7L), 1);
        when(products.findAllById(List.of(7L))).thenReturn(List.of(published));
        when(categories.findAllById(any())).thenReturn(List.of(category(5L)));
        when(variants.findByProductIdInOrderByProductIdAscRingSizeAsc(List.of(7L)))
                .thenReturn(List.of(variant(70L, 7L, 12, 10)));
        VariantHoldReader reader = ids -> Map.of(70L, BigInteger.TWO);
        when(holdReader.getIfAvailable()).thenReturn(reader);

        var dto = service.list(null, null, null, null).items().get(0).variants().get(0);

        assertThat(dto.remainingUnsold()).isEqualTo("10");
        assertThat(dto.validHeldQuantity()).isEqualTo("2");
        assertThat(dto.availableQuantity()).isEqualTo("8");
    }

    @Test
    void deletedOrUnknownProductIsNotFound() {
        when(products.findByIdAndDeletedAtIsNull(5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get("5"))
                .isInstanceOfSatisfying(CatalogueException.class,
                        error -> assertThat(error.code()).isEqualTo(CatalogueErrorCode.PRODUCT_NOT_FOUND));
    }

    @Test
    void negativePageAndMalformedCategoryAreRejected() {
        assertThatThrownBy(() -> service.list(null, null, null, -1)).isInstanceOf(CatalogueException.class);
        assertThatThrownBy(() -> service.list(null, "01", null, 0)).isInstanceOf(CatalogueException.class);
    }

    private void stubSearch(List<Long> ids, long total) {
        when(products.searchManagementIds(any(), any(), anyBoolean(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(ids, PageRequest.of(0, 20), total));
    }

    private static Product product(long id, boolean everPublished) {
        Product product = Product.draft("Nhẫn " + id, "Mô tả", 5L);
        setField(product, "id", id);
        if (everPublished) {
            product.publish(Instant.parse("2026-10-01T00:00:00Z"));
        }
        return product;
    }

    private static ProductCategory category(long id) {
        ProductCategory category = ProductCategory.create("Nhẫn bạc", null);
        setField(category, "id", id);
        return category;
    }

    private static ProductVariant variant(long id, long productId, int size, long quantity) {
        ProductVariant variant = ProductVariant.create(productId, size, BigInteger.valueOf(quantity));
        setField(variant, "id", id);
        return variant;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
