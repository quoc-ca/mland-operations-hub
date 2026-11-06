package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Exact value rules of FE-02 (data-model "Ownership and representation", interfaces "API conventions").
 *
 * <ul>
 *   <li>Product/variant IDs are positive signed BIGINT, exchanged as JSON decimal strings.</li>
 *   <li>Prices and quantities are DECIMAL(19,0) handled as {@link BigInteger}, never floating point;
 *       technical range 0–9999999999999999999 and a price must be greater than 0. No business cap.</li>
 *   <li>Versions are nonnegative signed BIGINT; timestamps are UTC with microsecond precision.</li>
 *   <li>Text is measured in Unicode code points after trimming leading/trailing whitespace.</li>
 * </ul>
 */
public final class CatalogueValues {

    public static final int MIN_SIZE = 5;
    public static final int MAX_SIZE = 29;
    public static final int MAX_SIZES_PER_PRODUCT = 25;

    public static final int NAME_MAX = 200;
    public static final int PRODUCT_DESCRIPTION_MAX = 5_000;
    public static final int CATEGORY_DESCRIPTION_MAX = 5_000;
    public static final int REJECTION_REASON_MAX = 2_000;
    public static final int ALT_TEXT_MAX = 200;
    public static final int SEARCH_QUERY_MAX = 200;

    /** 1–19 digits, no sign, fraction, exponent or leading zero. */
    private static final Pattern POSITIVE_DECIMAL = Pattern.compile("[1-9][0-9]{0,18}");
    /** {@code 0} or 1–19 digits without leading zero. */
    private static final Pattern NONNEGATIVE_DECIMAL = Pattern.compile("0|[1-9][0-9]{0,18}");

    private static final BigInteger MAX_ID = BigInteger.valueOf(Long.MAX_VALUE);

    private CatalogueValues() {
    }

    public static long parseId(String field, String raw) {
        if (raw == null || !POSITIVE_DECIMAL.matcher(raw).matches()) {
            throw CatalogueException.invalidField(field, "Must be a positive integer identifier.");
        }
        BigInteger value = new BigInteger(raw);
        if (value.compareTo(MAX_ID) > 0) {
            throw CatalogueException.invalidField(field, "Identifier is out of range.");
        }
        return value.longValueExact();
    }

    /** Positive whole VND amount (proposed, approved or applied price). */
    public static BigInteger parseAmount(String field, String raw) {
        if (raw == null || !POSITIVE_DECIMAL.matcher(raw).matches()) {
            throw CatalogueException.invalidField(field,
                    "Must be a positive whole VND amount without sign, separators, decimals or leading zero.");
        }
        return new BigInteger(raw);
    }

    /** Nonnegative whole quantity. */
    public static BigInteger parseQuantity(String field, String raw) {
        if (raw == null || !NONNEGATIVE_DECIMAL.matcher(raw).matches()) {
            throw CatalogueException.invalidField(field,
                    "Must be a nonnegative whole number without sign, separators, decimals or leading zero.");
        }
        return new BigInteger(raw);
    }

    public static String format(BigInteger value) {
        return value.toString();
    }

    public static String format(long id) {
        return Long.toString(id);
    }

    public static long requireVersion(String field, Long version) {
        if (version == null || version < 0) {
            throw CatalogueException.invalidField(field, "Must be a nonnegative version.");
        }
        return version;
    }

    public static int requireSize(String field, Integer size) {
        if (size == null || size < MIN_SIZE || size > MAX_SIZE) {
            throw CatalogueException.invalidField(field, "Size must be a whole number from 5 to 29.");
        }
        return size;
    }

    /** 1–25 distinct sizes, each 5–29. */
    public static List<Integer> requireSizes(String field, List<Integer> sizes) {
        if (sizes == null || sizes.isEmpty() || sizes.size() > MAX_SIZES_PER_PRODUCT) {
            throw CatalogueException.invalidField(field, "Choose from 1 to 25 sizes.");
        }
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < sizes.size(); i++) {
            int size = requireSize(field + "[" + i + "]", sizes.get(i));
            if (!seen.add(size)) {
                throw CatalogueException.invalidField(field + "[" + i + "]", "Each size may appear only once.");
            }
        }
        return List.copyOf(sizes);
    }

    public static String productName(String field, String raw) {
        return requiredText(field, raw, NAME_MAX);
    }

    public static String productDescription(String field, String raw) {
        return requiredText(field, raw, PRODUCT_DESCRIPTION_MAX);
    }

    public static String categoryName(String field, String raw) {
        return requiredText(field, raw, NAME_MAX);
    }

    /** Optional category description; blank after trimming becomes {@code null}. */
    public static String categoryDescription(String field, String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = trim(raw);
        if (trimmed.isEmpty()) {
            return null;
        }
        if (codePoints(trimmed) > CATEGORY_DESCRIPTION_MAX) {
            throw CatalogueException.invalidField(field, "Must be at most 5000 characters.");
        }
        return trimmed;
    }

    public static String rejectionReason(String field, String raw) {
        return requiredText(field, raw, REJECTION_REASON_MAX);
    }

    public static String altText(String field, String raw) {
        return requiredText(field, raw, ALT_TEXT_MAX);
    }

    /** Optional search keyword; blank after trimming means "no keyword". */
    public static String searchQuery(String field, String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = trim(raw);
        if (trimmed.isEmpty()) {
            return null;
        }
        if (codePoints(trimmed) > SEARCH_QUERY_MAX) {
            throw CatalogueException.invalidField(field, "Must be at most 200 characters.");
        }
        return trimmed;
    }

    /**
     * Uniqueness key of a category name: NFC, lower case with {@link Locale#ROOT}, UTF-8 bytes.
     * Accents are kept, so names that differ only by diacritics stay distinct.
     */
    public static byte[] categoryNameKey(String trimmedName) {
        String normalized = Normalizer.normalize(trimmedName, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        return normalized.getBytes(StandardCharsets.UTF_8);
    }

    /** UTC instant truncated to the microsecond precision stored in MySQL. */
    public static Instant toStoredInstant(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS);
    }

    static String trim(String raw) {
        return raw.strip();
    }

    static int codePoints(String text) {
        return text.codePointCount(0, text.length());
    }

    private static String requiredText(String field, String raw, int max) {
        String trimmed = raw == null ? "" : trim(raw);
        int length = codePoints(trimmed);
        if (length == 0) {
            throw CatalogueException.invalidField(field, "This field is required.");
        }
        if (length > max) {
            throw CatalogueException.invalidField(field, "Must be at most " + max + " characters.");
        }
        return trimmed;
    }
}
