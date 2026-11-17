package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Exact value rules of FE-06 (data-model section 1, interfaces "Wire rules").
 *
 * <ul>
 *   <li>Money and points are DECIMAL(19,0) handled as {@link BigInteger}, never floating point;
 *       technical range 0 to 9 999 999 999 999 999 999. No business cap is invented.</li>
 *   <li>Percentages are DECIMAL(9,6): greater than 0 and at most 100, at most six decimals, never rounded.</li>
 *   <li>Programme names are 1-200 and codes 1-128 Unicode code points after trimming, without control
 *       characters. A code key is the trimmed code upper-cased with {@link Locale#ROOT}; accents are not folded.</li>
 *   <li>Timestamps are UTC with microsecond precision; an effective window is {@code [startAt, endAt)}.</li>
 * </ul>
 */
public final class LoyaltyValues {

    public static final int NAME_MAX = 200;
    public static final int CODE_MAX = 128;
    public static final BigInteger MAX_AMOUNT = new BigInteger("9999999999999999999");
    public static final BigDecimal PERCENT_MAX = new BigDecimal("100");
    public static final int PERCENT_SCALE = 6;

    private LoyaltyValues() {
    }

    /** Trimmed original text plus the comparison key stored in the unique code column. */
    public record NormalizedCode(String original, String key) {
    }

    public static BigInteger requireAmount(String field, BigInteger value) {
        if (value == null || value.signum() < 0 || value.compareTo(MAX_AMOUNT) > 0) {
            throw LoyaltyException.invalidField(field, "Amount is outside the supported range.");
        }
        return value;
    }

    public static BigInteger requirePositiveAmount(String field, BigInteger value) {
        requireAmount(field, value);
        if (value.signum() == 0) {
            throw LoyaltyException.invalidField(field, "Amount must be greater than zero.");
        }
        return value;
    }

    /** Percentage in (0, 100] with at most six decimals; the value is returned unchanged. */
    public static BigDecimal requirePercent(String field, BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.compareTo(PERCENT_MAX) > 0
                || value.stripTrailingZeros().scale() > PERCENT_SCALE) {
            throw LoyaltyException.invalidField(field, "Percentage must be greater than 0, at most 100, with up to 6 decimals.");
        }
        return value;
    }

    public static String normalizeName(String field, String raw) {
        String trimmed = trimmed(field, raw);
        requireLength(field, trimmed, NAME_MAX);
        rejectControlCharacters(field, trimmed);
        return trimmed;
    }

    public static NormalizedCode normalizeCode(String field, String raw) {
        String trimmed = trimmed(field, raw);
        requireLength(field, trimmed, CODE_MAX);
        rejectControlCharacters(field, trimmed);
        return new NormalizedCode(trimmed, trimmed.toUpperCase(Locale.ROOT));
    }

    public static void requireInterval(String field, Instant startAt, Instant endAt) {
        if (startAt == null || endAt == null || !startAt.isBefore(endAt)) {
            throw LoyaltyException.invalidField(field, "Start must be before end.");
        }
    }

    /** Effective window {@code [startAt, endAt)}. */
    public static boolean isActive(Instant startAt, Instant endAt, Instant at) {
        return !at.isBefore(startAt) && at.isBefore(endAt);
    }

    public static Instant truncateToMicros(Instant value) {
        return value.truncatedTo(ChronoUnit.MICROS);
    }

    private static String trimmed(String field, String raw) {
        if (raw == null) {
            throw LoyaltyException.invalidField(field, "A value is required.");
        }
        String trimmed = raw.strip();
        if (trimmed.isEmpty()) {
            throw LoyaltyException.invalidField(field, "A value is required.");
        }
        return trimmed;
    }

    private static void requireLength(String field, String value, int max) {
        if (value.codePointCount(0, value.length()) > max) {
            throw LoyaltyException.invalidField(field, "Value is longer than " + max + " characters.");
        }
    }

    private static void rejectControlCharacters(String field, String value) {
        if (value.codePoints().anyMatch(Character::isISOControl)) {
            throw LoyaltyException.invalidField(field, "Control characters are not allowed.");
        }
    }
}
