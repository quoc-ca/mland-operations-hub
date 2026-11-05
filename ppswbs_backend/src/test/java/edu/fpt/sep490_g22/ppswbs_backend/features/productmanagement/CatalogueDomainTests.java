package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.Product;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.SkuGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** T020: SKU/size/amount/text boundaries and invariants of products and their fixed sizes (FR-004–007, SC-003). */
class CatalogueDomainTests {

    private static final Instant NOW = Instant.parse("2026-10-26T04:00:00Z");

    @ParameterizedTest
    @CsvSource({
            "123, 5, SP000123-S05",
            "123, 12, SP000123-S12",
            "1, 29, SP000001-S29",
            "999999, 7, SP999999-S07",
            "1234567, 7, SP1234567-S07",
            "9223372036854775807, 29, SP9223372036854775807-S29"
    })
    void skuPadsToSixDigitsAndKeepsLongerNumbersWhole(long productId, int size, String expected) {
        assertThat(SkuGenerator.sku(productId, size)).isEqualTo(expected);
        assertThat(expected.length()).isLessThanOrEqualTo(SkuGenerator.MAX_LENGTH);
    }

    @Test
    void largestSkuIsExactlyTwentyFiveAsciiCharacters() {
        String sku = SkuGenerator.sku(Long.MAX_VALUE, 29);
        assertThat(sku).hasSize(25).matches("[ -~]+");
    }

    @Test
    void skuDigitsDoNotDependOnTheDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"));
            assertThat(SkuGenerator.sku(123, 5)).isEqualTo("SP000123-S05");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void allTwentyFiveSizesGiveDistinctSkus() {
        Set<String> skus = IntStream.rangeClosed(5, 29)
                .mapToObj(size -> ProductVariant.create(123, size, BigInteger.ZERO).getSku())
                .collect(Collectors.toSet());
        assertThat(skus).hasSize(25);
    }

    @Test
    void sizeOutsideFiveToTwentyNineIsRejected() {
        assertThatThrownBy(() -> ProductVariant.create(1, 4, BigInteger.ONE)).isInstanceOf(CatalogueException.class);
        assertThatThrownBy(() -> ProductVariant.create(1, 30, BigInteger.ONE)).isInstanceOf(CatalogueException.class);
    }

    @Test
    void newVariantIsSaleEnabledWithoutAppliedPrice() {
        ProductVariant variant = ProductVariant.create(123, 12, BigInteger.TEN);
        assertThat(variant.isSaleEnabled()).isTrue();
        assertThat(variant.getAppliedPrice()).isNull();
        assertThat(variant.hasAppliedPrice()).isFalse();
        assertThat(variant.getAppliedSequence()).isZero();
        assertThat(variant.getPendingProposalId()).isNull();
    }

    @Test
    void negativeQuantityIsRejected() {
        assertThatThrownBy(() -> ProductVariant.create(1, 5, BigInteger.valueOf(-1)))
                .isInstanceOf(CatalogueException.class);
    }

    @Test
    void quantityCannotGoBelowValidHolds() {
        ProductVariant variant = ProductVariant.create(1, 12, BigInteger.TEN);
        assertThatThrownBy(() -> variant.updateRemainingUnsold(BigInteger.ONE, BigInteger.TWO))
                .isInstanceOfSatisfying(CatalogueException.class,
                        error -> assertThat(error.code()).isEqualTo(CatalogueErrorCode.QUANTITY_BELOW_HELD));
        variant.updateRemainingUnsold(BigInteger.TWO, BigInteger.TWO);
        assertThat(variant.getRemainingUnsold()).isEqualTo(BigInteger.TWO);
    }

    @Test
    void appliedPriceRejectsAnOlderDecision() {
        ProductVariant variant = ProductVariant.create(1, 12, BigInteger.TEN);
        variant.applyApprovedPrice(7L, BigInteger.valueOf(100_000), 0);
        assertThat(variant.getAppliedSequence()).isEqualTo(1);
        assertThatThrownBy(() -> variant.applyApprovedPrice(6L, BigInteger.valueOf(90_000), 0))
                .isInstanceOfSatisfying(CatalogueException.class,
                        error -> assertThat(error.code()).isEqualTo(CatalogueErrorCode.PRICE_APPLICATION_STALE));
        assertThat(variant.getAppliedPrice()).isEqualTo(BigInteger.valueOf(100_000));
    }

    @Test
    void sizeAndSkuHaveNoMutator() {
        Set<String> publicMethods = Arrays.stream(ProductVariant.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .collect(Collectors.toSet());
        assertThat(publicMethods).noneMatch(name -> name.startsWith("set") && (name.contains("Size") || name.contains("Sku")));
        assertThat(publicMethods).doesNotContain("setRingSize", "setSku", "setProductId");
    }

    @Test
    void productDraftIsUnpublishedAndTrimmed() {
        Product product = Product.draft("  Nhẫn bạc  ", " Mô tả ", 3L);
        assertThat(product.getName()).isEqualTo("Nhẫn bạc");
        assertThat(product.getDescription()).isEqualTo("Mô tả");
        assertThat(product.isPublished()).isFalse();
        assertThat(product.hasEverBeenPublished()).isFalse();
        assertThat(product.getContentVersion()).isZero();
    }

    @Test
    void productTextBoundariesUseCodePoints() {
        assertThat(Product.draft("😀".repeat(200), "d", 1L).getName()).hasSize(400);
        assertThatThrownBy(() -> Product.draft("😀".repeat(201), "d", 1L)).isInstanceOf(CatalogueException.class);
        assertThatThrownBy(() -> Product.draft("n", "", 1L)).isInstanceOf(CatalogueException.class);
        assertThatThrownBy(() -> Product.draft("n", "x".repeat(5001), 1L)).isInstanceOf(CatalogueException.class);
    }

    @Test
    void contentVersionChangesOnlyWhenContentChanges() {
        Product product = Product.draft("Nhẫn", "Mô tả", 1L);
        assertThat(product.updateContent(" Nhẫn ", "Mô tả", 1L)).isFalse();
        assertThat(product.getContentVersion()).isZero();
        assertThat(product.updateContent("Nhẫn", "Mô tả", 2L)).isTrue();
        assertThat(product.getContentVersion()).isEqualTo(1);
    }

    @Test
    void firstPublicationTimeSurvivesHidingAndRepublishing() {
        Product product = Product.draft("Nhẫn", "Mô tả", 1L);
        assertThat(product.publish(NOW)).isTrue();
        assertThat(product.publish(NOW.plusSeconds(1))).isFalse();
        assertThat(product.unpublish()).isTrue();
        product.publish(NOW.plusSeconds(60));
        assertThat(product.getFirstPublishedAt()).isEqualTo(NOW);
    }

    @Test
    void softDeleteBlocksCommandsAndIsNeverUndone() {
        Product product = Product.draft("Nhẫn", "Mô tả", 1L);
        product.publish(NOW);
        assertThat(product.softDelete("manager-1", NOW)).isTrue();
        assertThat(product.isPublished()).isFalse();
        assertThat(product.softDelete("manager-1", NOW.plusSeconds(5))).isFalse();
        assertThat(product.getDeletedAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> product.publish(NOW))
                .isInstanceOfSatisfying(CatalogueException.class,
                        error -> assertThat(error.code()).isEqualTo(CatalogueErrorCode.PRODUCT_DELETED));
        assertThatThrownBy(() -> product.updateContent("x", "y", 1L)).isInstanceOf(CatalogueException.class);
    }
}
