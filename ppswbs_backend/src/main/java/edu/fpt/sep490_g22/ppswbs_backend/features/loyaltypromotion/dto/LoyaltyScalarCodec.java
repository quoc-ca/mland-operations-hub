package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyValues;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.regex.Pattern;

/**
 * Wire codec for scalars: IDs, versions, money, points and percentages travel as JSON decimal strings,
 * never as JavaScript numbers. Parsing is strict (no sign, exponent, leading zero or surrounding space).
 */
public final class LoyaltyScalarCodec {

    /** {@code 0} or 1-19 digits without a leading zero. */
    private static final Pattern NONNEGATIVE = Pattern.compile("0|[1-9][0-9]{0,18}");
    private static final Pattern POSITIVE = Pattern.compile("[1-9][0-9]{0,18}");
    /** Integer part 1-3 digits without a leading zero (or a single 0), optional fraction of 1-6 digits. */
    private static final Pattern PERCENT = Pattern.compile("(0|[1-9][0-9]{0,2})(\\.[0-9]{1,6})?");
    private static final BigInteger MAX_BIGINT = BigInteger.valueOf(Long.MAX_VALUE);

    private LoyaltyScalarCodec() {
    }

    /** Positive signed BIGINT identifier. */
    public static long parseId(String field, String raw) {
        if (raw == null || !POSITIVE.matcher(raw).matches()) {
            throw LoyaltyException.invalidField(field, "Must be a positive integer identifier.");
        }
        BigInteger value = new BigInteger(raw);
        if (value.compareTo(MAX_BIGINT) > 0) {
            throw LoyaltyException.invalidField(field, "Identifier is out of range.");
        }
        return value.longValueExact();
    }

    /** Non-negative signed BIGINT (versions, counters, quotas). */
    public static long parseVersion(String field, String raw) {
        if (raw == null || !NONNEGATIVE.matcher(raw).matches()) {
            throw LoyaltyException.invalidField(field, "Must be a non-negative integer.");
        }
        BigInteger value = new BigInteger(raw);
        if (value.compareTo(MAX_BIGINT) > 0) {
            throw LoyaltyException.invalidField(field, "Value is out of range.");
        }
        return value.longValueExact();
    }

    /** Whole VND or points amount in the DECIMAL(19,0) range, zero allowed. */
    public static BigInteger parseAmount(String field, String raw) {
        if (raw == null || !NONNEGATIVE.matcher(raw).matches()) {
            throw LoyaltyException.invalidField(field, "Must be a whole number without sign, fraction or exponent.");
        }
        return LoyaltyValues.requireAmount(field, new BigInteger(raw));
    }

    /** Whole amount greater than zero (fixed discounts, caps). */
    public static BigInteger parsePositiveAmount(String field, String raw) {
        return LoyaltyValues.requirePositiveAmount(field, parseAmount(field, raw));
    }

    /** Percentage greater than 0 and at most 100 with up to six decimals. */
    public static BigDecimal parsePercent(String field, String raw) {
        if (raw == null || !PERCENT.matcher(raw).matches()) {
            throw LoyaltyException.invalidField(field, "Must be a decimal percentage with up to 6 decimals.");
        }
        return LoyaltyValues.requirePercent(field, new BigDecimal(raw));
    }

    public static String format(BigInteger value) {
        return value.toString();
    }

    /** Plain decimal string without exponent or trailing zeros. */
    public static String format(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() < 0) {
            stripped = stripped.setScale(0);
        }
        return stripped.toPlainString();
    }
}
