package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueValues;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** T006 value rules: exact decimal strings, size/text boundaries and category name key. */
class CatalogueValuesTests {

    @Test
    void idAcceptsPositiveSignedBigintRange() {
        assertThat(CatalogueValues.parseId("id", "1")).isEqualTo(1L);
        assertThat(CatalogueValues.parseId("id", "9223372036854775807")).isEqualTo(Long.MAX_VALUE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "+1", "01", "1.0", "1e3", " 1", "9223372036854775808", "abc", ""})
    void idRejectsAnythingElse(String raw) {
        assertInvalid(() -> CatalogueValues.parseId("productId", raw), "productId");
    }

    @Test
    void amountIsExactPositiveWholeVndUpToNineteenDigits() {
        assertThat(CatalogueValues.parseAmount("price", "1")).isEqualTo(BigInteger.ONE);
        assertThat(CatalogueValues.parseAmount("price", "9999999999999999999"))
                .isEqualTo(new BigInteger("9999999999999999999"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-100", "+100", "0100", "100.5", "1.000", "1,000", "1e3", "10000000000000000000"})
    void amountRejectsZeroNegativeFractionAndFormatting(String raw) {
        assertInvalid(() -> CatalogueValues.parseAmount("price", raw), "price");
    }

    @Test
    void amountRejectsNull() {
        assertInvalid(() -> CatalogueValues.parseAmount("price", null), "price");
    }

    @Test
    void quantityAllowsZero() {
        assertThat(CatalogueValues.parseQuantity("quantity", "0")).isEqualTo(BigInteger.ZERO);
        assertThat(CatalogueValues.parseQuantity("quantity", "12")).isEqualTo(BigInteger.valueOf(12));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "00", "01", "1.5", "+0"})
    void quantityRejectsNegativeAndFormatting(String raw) {
        assertInvalid(() -> CatalogueValues.parseQuantity("quantity", raw), "quantity");
    }

    @Test
    void sizeBoundaries() {
        assertThat(CatalogueValues.requireSize("size", 5)).isEqualTo(5);
        assertThat(CatalogueValues.requireSize("size", 29)).isEqualTo(29);
        assertInvalid(() -> CatalogueValues.requireSize("size", 4), "size");
        assertInvalid(() -> CatalogueValues.requireSize("size", 30), "size");
        assertInvalid(() -> CatalogueValues.requireSize("size", null), "size");
    }

    @Test
    void allTwentyFiveSizesAreAcceptedAndDuplicatesRejected() {
        List<Integer> all = IntStream.rangeClosed(5, 29).boxed().toList();
        assertThat(CatalogueValues.requireSizes("variants", all)).hasSize(25);
        assertInvalid(() -> CatalogueValues.requireSizes("variants", List.of(5, 12, 5)), "variants[2]");
        assertInvalid(() -> CatalogueValues.requireSizes("variants", List.of()), "variants");
    }

    @Test
    void productNameIsTrimmedAndMeasuredInCodePoints() {
        assertThat(CatalogueValues.productName("name", "  Nhẫn bạc  ")).isEqualTo("Nhẫn bạc");
        String emoji200 = "😀".repeat(200);
        assertThat(CatalogueValues.productName("name", emoji200)).isEqualTo(emoji200);
        assertInvalid(() -> CatalogueValues.productName("name", "😀".repeat(201)), "name");
        assertInvalid(() -> CatalogueValues.productName("name", "   "), "name");
        assertInvalid(() -> CatalogueValues.productName("name", null), "name");
    }

    @Test
    void productDescriptionBoundaries() {
        assertThat(CatalogueValues.productDescription("description", "a".repeat(5000))).hasSize(5000);
        assertInvalid(() -> CatalogueValues.productDescription("description", "a".repeat(5001)), "description");
        assertInvalid(() -> CatalogueValues.productDescription("description", ""), "description");
    }

    @Test
    void categoryDescriptionIsOptional() {
        assertThat(CatalogueValues.categoryDescription("description", null)).isNull();
        assertThat(CatalogueValues.categoryDescription("description", "   ")).isNull();
        assertThat(CatalogueValues.categoryDescription("description", "a".repeat(5000))).hasSize(5000);
        assertInvalid(() -> CatalogueValues.categoryDescription("description", "a".repeat(5001)), "description");
    }

    @Test
    void rejectionReasonIsRequiredUpToTwoThousand() {
        assertThat(CatalogueValues.rejectionReason("reason", "x".repeat(2000))).hasSize(2000);
        assertInvalid(() -> CatalogueValues.rejectionReason("reason", "x".repeat(2001)), "reason");
        assertInvalid(() -> CatalogueValues.rejectionReason("reason", " "), "reason");
    }

    @Test
    void searchQueryBlankMeansNoKeywordAndMaxIsTwoHundred() {
        assertThat(CatalogueValues.searchQuery("q", "  ")).isNull();
        assertThat(CatalogueValues.searchQuery("q", " " + "a".repeat(200) + " ")).hasSize(200);
        assertInvalid(() -> CatalogueValues.searchQuery("q", "a".repeat(201)), "q");
    }

    @Test
    void categoryKeyIgnoresCaseButKeepsVietnameseDiacritics() {
        byte[] upper = CatalogueValues.categoryNameKey("NHẪN CƯỚI");
        byte[] lower = CatalogueValues.categoryNameKey("nhẫn cưới");
        byte[] withoutAccent = CatalogueValues.categoryNameKey("nhan cuoi");
        assertThat(Arrays.equals(upper, lower)).isTrue();
        assertThat(Arrays.equals(lower, withoutAccent)).isFalse();
    }

    @Test
    void categoryKeyNormalizesToNfc() {
        String composed = "ẫ";           // ẫ precomposed
        String decomposed = "ẫ";   // a + circumflex + tilde
        assertThat(CatalogueValues.categoryNameKey(composed)).isEqualTo(CatalogueValues.categoryNameKey(decomposed));
    }

    @Test
    void storedInstantKeepsMicroseconds() {
        Instant instant = Instant.parse("2026-10-26T01:02:03.123456789Z");
        assertThat(CatalogueValues.toStoredInstant(instant)).isEqualTo(Instant.parse("2026-10-26T01:02:03.123456Z"));
    }

    private static void assertInvalid(Runnable action, String field) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(CatalogueException.class, error -> {
                    assertThat(error.code()).isEqualTo(CatalogueErrorCode.VALIDATION_ERROR);
                    assertThat(error.fieldErrors()).singleElement()
                            .satisfies(fieldError -> assertThat(fieldError.field()).isEqualTo(field));
                });
    }
}
