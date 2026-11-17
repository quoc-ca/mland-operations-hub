package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.LoyaltyScalarCodec;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyValues;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyValues.NormalizedCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Value rules of data-model section 1 and the wire rules of interfaces section 2. */
class LoyaltyValuesTest {

    private static void assertInvalid(Runnable action, String field) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(LoyaltyException.class, e -> {
                    assertThat(e.code()).isEqualTo(LoyaltyErrorCode.INVALID_INPUT);
                    assertThat(e.fieldIssues()).extracting(LoyaltyException.FieldIssue::field).contains(field);
                });
    }

    @Test
    void codeIsTrimmedAndComparedCaseInsensitively() {
        NormalizedCode code = LoyaltyValues.normalizeCode("code", "  tet10 ");
        assertThat(code.original()).isEqualTo("tet10");
        assertThat(code.key()).isEqualTo("TET10");
        assertThat(LoyaltyValues.normalizeCode("code", "TET10").key()).isEqualTo(code.key());
    }

    @Test
    void codeDoesNotFoldAccentsOrExtraCharacters() {
        assertThat(LoyaltyValues.normalizeCode("code", "Tết10").key()).isNotEqualTo("TET10");
    }

    @Test
    void codeLengthIsMeasuredInCodePointsAndBoundedTo128() {
        assertThat(LoyaltyValues.normalizeCode("code", "a".repeat(128)).key()).hasSize(128);
        assertInvalid(() -> LoyaltyValues.normalizeCode("code", "a".repeat(129)), "code");
        assertInvalid(() -> LoyaltyValues.normalizeCode("code", "   "), "code");
        assertInvalid(() -> LoyaltyValues.normalizeCode("code", null), "code");
    }

    @Test
    void codeRejectsControlCharacters() {
        assertInvalid(() -> LoyaltyValues.normalizeCode("code", "TET\n10"), "code");
        assertInvalid(() -> LoyaltyValues.normalizeCode("code", "TET\u0000"), "code");
    }

    @Test
    void nameIsTrimmedAndBoundedTo200CodePoints() {
        assertThat(LoyaltyValues.normalizeName("name", "  Giảm Tết  ")).isEqualTo("Giảm Tết");
        assertThat(LoyaltyValues.normalizeName("name", "😀".repeat(200))).isNotEmpty();
        assertInvalid(() -> LoyaltyValues.normalizeName("name", "😀".repeat(201)), "name");
        assertInvalid(() -> LoyaltyValues.normalizeName("name", ""), "name");
    }

    @Test
    void amountsStayInTheDecimal19Range() {
        assertThat(LoyaltyScalarCodec.parseAmount("amount", "0")).isEqualTo(BigInteger.ZERO);
        assertThat(LoyaltyScalarCodec.parseAmount("amount", "9999999999999999999"))
                .isEqualTo(new BigInteger("9999999999999999999"));
        for (String bad : new String[] {"-1", "+1", "01", "1.0", "1e3", "10000000000000000000", "", " 1", "abc"}) {
            assertInvalid(() -> LoyaltyScalarCodec.parseAmount("amount", bad), "amount");
        }
    }

    @Test
    void positiveAmountsRejectZero() {
        assertThat(LoyaltyScalarCodec.parsePositiveAmount("cap", "1")).isEqualTo(BigInteger.ONE);
        assertInvalid(() -> LoyaltyScalarCodec.parsePositiveAmount("cap", "0"), "cap");
    }

    @Test
    void idsAndVersionsFollowTheBigintRules() {
        assertThat(LoyaltyScalarCodec.parseId("id", "9223372036854775807")).isEqualTo(Long.MAX_VALUE);
        assertInvalid(() -> LoyaltyScalarCodec.parseId("id", "9223372036854775808"), "id");
        assertInvalid(() -> LoyaltyScalarCodec.parseId("id", "0"), "id");
        assertThat(LoyaltyScalarCodec.parseVersion("version", "0")).isZero();
        assertInvalid(() -> LoyaltyScalarCodec.parseVersion("version", "-1"), "version");
    }

    @Test
    void percentageHasAtMostSixDecimalsAndIsInHalfOpenRange() {
        assertThat(LoyaltyScalarCodec.parsePercent("percent", "10")).isEqualByComparingTo("10");
        assertThat(LoyaltyScalarCodec.parsePercent("percent", "0.000001")).isEqualByComparingTo("0.000001");
        assertThat(LoyaltyScalarCodec.parsePercent("percent", "100")).isEqualByComparingTo("100");
        assertThat(LoyaltyScalarCodec.parsePercent("percent", "100.000000")).isEqualByComparingTo("100");
        for (String bad : new String[] {"0", "0.0", "100.000001", "101", "0.0000001", "-5", "1e1", ".5", "5.", ""}) {
            assertInvalid(() -> LoyaltyScalarCodec.parsePercent("percent", bad), "percent");
        }
    }

    @Test
    void formattingIsPlainDecimalWithoutExponent() {
        assertThat(LoyaltyScalarCodec.format(new BigInteger("9999999999999999999"))).isEqualTo("9999999999999999999");
        assertThat(LoyaltyScalarCodec.format(new BigDecimal("12.500000"))).isEqualTo("12.5");
        assertThat(LoyaltyScalarCodec.format(new BigDecimal("100"))).isEqualTo("100");
        assertThat(LoyaltyScalarCodec.format(new BigDecimal("1E+2"))).isEqualTo("100");
    }

    @Test
    void intervalIsHalfOpenAndStartMustBeBeforeEnd() {
        Instant start = Instant.parse("2026-10-26T00:00:00Z");
        Instant end = Instant.parse("2026-10-27T00:00:00Z");
        LoyaltyValues.requireInterval("startAt", start, end);
        assertInvalid(() -> LoyaltyValues.requireInterval("startAt", end, start), "startAt");
        assertInvalid(() -> LoyaltyValues.requireInterval("startAt", start, start), "startAt");
        assertThat(LoyaltyValues.isActive(start, end, start)).isTrue();
        assertThat(LoyaltyValues.isActive(start, end, end)).isFalse();
        assertThat(LoyaltyValues.isActive(start, end, start.minusNanos(1_000))).isFalse();
    }

    @Test
    void timestampsKeepMicrosecondPrecision() {
        Instant raw = Instant.parse("2026-10-26T01:02:03.123456789Z");
        assertThat(LoyaltyValues.truncateToMicros(raw)).isEqualTo(Instant.parse("2026-10-26T01:02:03.123456Z"));
    }
}
