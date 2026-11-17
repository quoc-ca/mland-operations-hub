package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.importing;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductCategory;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueValues;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.importing.ProductImportParser.CellKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.importing.ProductImportParser.RawCell;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.importing.ProductImportParser.RawRow;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates parsed import rows with the same rules as manual creation (T097, FR-040/041): text limits
 * from {@link CatalogueValues}, sizes 5–29, positive whole VND prices, nonnegative whole quantities and
 * active categories matched by trimmed, case-insensitive name. Rows are grouped by the case-sensitive
 * group key into products of 1–25 distinct sizes with identical name, description and category.
 *
 * <p>Every error is collected (row, column, value, code, message); nothing is truncated except the echoed
 * value (200 characters). The preview never assigns a product number or SKU.
 */
public final class ProductImportValidator {

    public static final int GROUP_KEY_MAX = 100;
    private static final int ECHO_MAX = 200;
    /** A real number cell must be an integer of at most 15 significant digits (Excel precision). */
    private static final int MAX_SIGNIFICANT_DIGITS = 15;
    private static final Pattern POSITIVE_TEXT = Pattern.compile("[1-9][0-9]{0,18}");
    private static final Pattern NONNEGATIVE_TEXT = Pattern.compile("0|[1-9][0-9]{0,18}");
    /** A Size text is read as a list when it has a list separator or a range between two numbers. */
    private static final Pattern SIZE_LIST_HINT = Pattern.compile("[,;]|[0-9]\\s*[-–]\\s*[0-9]");
    private static final String SIZE_PART = "[1-9][0-9]{0,2}(?:\\s*[-–]\\s*[1-9][0-9]{0,2})?";
    private static final Pattern SIZE_LIST = Pattern.compile(SIZE_PART + "(?:\\s*[,;]\\s*" + SIZE_PART + ")*");

    public record ImportError(int row, String column, String value, String code, String message) {
    }

    /** {@code branchQuantities}: quantity at each chosen branch, in the order of the file columns (US10); null without branches. */
    public record PreviewVariant(int row, int size, BigInteger proposedPrice, BigInteger quantity,
                                 List<BigInteger> branchQuantities) {
    }

    public record PreviewProduct(String groupKey, String name, String description, long categoryId,
                                 String categoryName, List<PreviewVariant> variants) {
    }

    public record Result(int rowCount, List<PreviewProduct> products, List<ImportError> errors) {
        public boolean valid() {
            return errors.isEmpty();
        }

        public int variantCount() {
            return products.stream().mapToInt(product -> product.variants().size()).sum();
        }
    }

    private ProductImportValidator() {
    }

    public static Result validate(List<RawRow> rows, Collection<ProductCategory> activeCategories) {
        return validate(rows, activeCategories, List.of());
    }

    /**
     * {@code branchHeaders} (US10, Q-B2): the header of each branch quantity column, in file order, starting at the
     * column of {@link ImportColumn#QUANTITY}; empty = the single quantity column of the original template.
     */
    public static Result validate(List<RawRow> rows, Collection<ProductCategory> activeCategories,
                                  List<String> branchHeaders) {
        Map<ByteBuffer, ProductCategory> categoriesByKey = new HashMap<>();
        for (ProductCategory category : activeCategories) {
            if (category.isActive()) {
                categoriesByKey.put(ByteBuffer.wrap(CatalogueValues.categoryNameKey(category.getName())), category);
            }
        }

        List<ImportError> errors = new ArrayList<>();
        Map<String, Group> groups = new LinkedHashMap<>();
        for (RawRow row : rows) {
            if (row.isBlank()) {
                errors.add(error(row.rowNumber(), ImportColumn.GROUP_KEY, null, "EMPTY_ROW_IN_DATA",
                        "Empty rows are not allowed between data rows."));
                continue;
            }
            int before = errors.size();
            String groupKey = text(row, ImportColumn.GROUP_KEY, GROUP_KEY_MAX, errors);
            String name = text(row, ImportColumn.NAME, CatalogueValues.NAME_MAX, errors);
            String description = text(row, ImportColumn.DESCRIPTION, CatalogueValues.PRODUCT_DESCRIPTION_MAX, errors);
            String categoryName = text(row, ImportColumn.CATEGORY, CatalogueValues.NAME_MAX, errors);
            ProductCategory category = null;
            if (categoryName != null) {
                category = categoriesByKey.get(ByteBuffer.wrap(CatalogueValues.categoryNameKey(categoryName)));
                if (category == null) {
                    errors.add(error(row.rowNumber(), ImportColumn.CATEGORY, categoryName,
                            "CATEGORY_NOT_ACTIVE_OR_UNKNOWN", "The category does not exist or is not active."));
                }
            }
            List<Integer> sizes = sizes(row, errors);
            BigInteger price = number(row, ImportColumn.PROPOSED_PRICE, true, errors);
            BigInteger quantity;
            List<BigInteger> branchQuantities = null;
            if (branchHeaders.isEmpty()) {
                quantity = number(row, ImportColumn.QUANTITY, false, errors);
            } else {
                branchQuantities = new ArrayList<>();
                quantity = BigInteger.ZERO;
                for (int i = 0; i < branchHeaders.size(); i++) {
                    BigInteger value = number(row.rowNumber(), row.cells().get(ImportColumn.QUANTITY.ordinal() + i),
                            branchHeaders.get(i), false, true, errors);
                    branchQuantities.add(value);
                    quantity = value == null || quantity == null ? null : quantity.add(value);
                }
            }
            if (errors.size() > before || groupKey == null) {
                continue;
            }
            ProductCategory rowCategory = category;
            Group group = groups.computeIfAbsent(groupKey, key -> new Group(key, name, description, rowCategory));
            group.add(row.rowNumber(), name, description, rowCategory, sizes, price, quantity,
                    branchQuantities == null ? null : List.copyOf(branchQuantities), errors);
        }

        List<PreviewProduct> products = errors.isEmpty()
                ? groups.values().stream().map(Group::toPreview).toList()
                : List.of();
        return new Result(rows.size(), products, List.copyOf(errors));
    }

    private static final class Group {
        private final String key;
        private final String name;
        private final String description;
        private final ProductCategory category;
        private final List<PreviewVariant> variants = new ArrayList<>();
        private final Set<Integer> sizes = new HashSet<>();

        Group(String key, String name, String description, ProductCategory category) {
            this.key = key;
            this.name = name;
            this.description = description;
            this.category = category;
        }

        /** One row may carry several sizes (a list or range); each becomes a variant with the row's price and quantity. */
        void add(int row, String rowName, String rowDescription, ProductCategory rowCategory, List<Integer> rowSizes,
                 BigInteger price, BigInteger quantity, List<BigInteger> branchQuantities, List<ImportError> errors) {
            if (!name.equals(rowName)) {
                errors.add(error(row, ImportColumn.NAME, rowName, "GROUP_FIELD_MISMATCH",
                        "Rows with the same group key must have the same product name."));
                return;
            }
            if (!description.equals(rowDescription)) {
                errors.add(error(row, ImportColumn.DESCRIPTION, rowDescription, "GROUP_FIELD_MISMATCH",
                        "Rows with the same group key must have the same description."));
                return;
            }
            if (!Objects.equals(category.getId(), rowCategory.getId())) {
                errors.add(error(row, ImportColumn.CATEGORY, rowCategory.getName(), "GROUP_FIELD_MISMATCH",
                        "Rows with the same group key must have the same category."));
                return;
            }
            for (int size : rowSizes) {
                if (!sizes.add(size)) {
                    errors.add(error(row, ImportColumn.SIZE, Integer.toString(size), "DUPLICATE_SIZE_IN_GROUP",
                            "This size already appears in the same group."));
                    return;
                }
                if (sizes.size() > CatalogueValues.MAX_SIZES_PER_PRODUCT) {
                    errors.add(error(row, ImportColumn.GROUP_KEY, key, "TOO_MANY_SIZES",
                            "A product can have at most 25 sizes."));
                    return;
                }
                variants.add(new PreviewVariant(row, size, price, quantity, branchQuantities));
            }
        }

        PreviewProduct toPreview() {
            return new PreviewProduct(key, name, description, category.getId(), category.getName(), List.copyOf(variants));
        }
    }

    /**
     * The Size cell (FR-040, 2026-11-03): one whole size 5–29 as before, or a text list of sizes and ranges such as
     * {@code 12-20}, {@code 12,14,16} or {@code 5-9; 12}. A text without a list separator or a range between two
     * numbers keeps the number rules ({@code 5.0}, {@code +5}, {@code 05} stay INVALID_NUMBER). Sizes keep the order
     * written; a size repeated in one cell is DUPLICATE_SIZE_IN_GROUP.
     */
    private static List<Integer> sizes(RawRow row, List<ImportError> errors) {
        RawCell cell = row.cell(ImportColumn.SIZE);
        boolean list = cell.kind() == CellKind.TEXT && !cell.formula() && !cell.merged() && !cell.hyperlink()
                && SIZE_LIST_HINT.matcher(cell.text()).find();
        if (!list) {
            BigInteger size = number(row, ImportColumn.SIZE, false, errors);
            if (size == null) {
                return null;
            }
            if (!sizeInRange(size)) {
                errors.add(error(row.rowNumber(), ImportColumn.SIZE, size.toString(), "OUT_OF_RANGE",
                        "Size must be a whole number from 5 to 29."));
                return null;
            }
            return List.of(size.intValueExact());
        }
        String text = cell.text().strip();
        if (!SIZE_LIST.matcher(text).matches()) {
            errors.add(error(row.rowNumber(), ImportColumn.SIZE, text, "INVALID_SIZE_LIST",
                    "Write one size, a range like 12-20 or a list like 12,14,16."));
            return null;
        }
        List<Integer> sizes = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (String part : text.split("[,;]")) {
            String[] bounds = part.strip().split("\\s*[-–]\\s*");
            BigInteger low = new BigInteger(bounds[0]);
            BigInteger high = new BigInteger(bounds[bounds.length - 1]);
            if (!sizeInRange(low) || !sizeInRange(high)) {
                errors.add(error(row.rowNumber(), ImportColumn.SIZE, text, "OUT_OF_RANGE",
                        "Size must be a whole number from 5 to 29."));
                return null;
            }
            if (low.compareTo(high) > 0) {
                errors.add(error(row.rowNumber(), ImportColumn.SIZE, text, "INVALID_SIZE_LIST",
                        "A size range must go from the smaller to the larger size, like 12-20."));
                return null;
            }
            for (int size = low.intValueExact(); size <= high.intValueExact(); size++) {
                if (!seen.add(size)) {
                    errors.add(error(row.rowNumber(), ImportColumn.SIZE, Integer.toString(size), "DUPLICATE_SIZE_IN_GROUP",
                            "This size already appears in the same group."));
                    return null;
                }
                sizes.add(size);
            }
        }
        return List.copyOf(sizes);
    }

    private static boolean sizeInRange(BigInteger size) {
        return size.compareTo(BigInteger.valueOf(CatalogueValues.MIN_SIZE)) >= 0
                && size.compareTo(BigInteger.valueOf(CatalogueValues.MAX_SIZE)) <= 0;
    }

    /** Trimmed required text measured in code points; numbers are accepted as their integer text. */
    private static String text(RawRow row, ImportColumn column, int max, List<ImportError> errors) {
        RawCell cell = row.cell(column);
        if (unsupported(row.rowNumber(), column, cell, errors)) {
            return null;
        }
        String value = switch (cell.kind()) {
            case TEXT, BOOLEAN -> cell.text();
            case NUMBER -> integerText(cell.text());
            default -> null;
        };
        String trimmed = value == null ? "" : value.strip();
        if (trimmed.isEmpty()) {
            errors.add(error(row.rowNumber(), column, null, "REQUIRED", "This field is required."));
            return null;
        }
        if (trimmed.codePointCount(0, trimmed.length()) > max) {
            errors.add(error(row.rowNumber(), column, trimmed, "TOO_LONG",
                    "Must be at most " + max + " characters."));
            return null;
        }
        return trimmed;
    }

    /**
     * Whole number: a real number cell must be an integer of at most 15 significant digits; a text cell
     * must be plain digits (no sign, separator, decimal point, exponent or leading zero).
     */
    private static BigInteger number(RawRow row, ImportColumn column, boolean positive, List<ImportError> errors) {
        return number(row.rowNumber(), row.cell(column), column.header(), positive, false, errors);
    }

    /** {@code blankIsZero}: a blank branch quantity cell means 0 (Q-B2); other number cells are required. */
    private static BigInteger number(int rowNumber, RawCell cell, String column, boolean positive, boolean blankIsZero,
                                     List<ImportError> errors) {
        if (unsupported(rowNumber, column, cell, errors)) {
            return null;
        }
        if (cell.isBlank()) {
            if (blankIsZero) {
                return BigInteger.ZERO;
            }
            errors.add(error(rowNumber, column, null, "REQUIRED", "This field is required."));
            return null;
        }
        BigInteger value = null;
        if (cell.kind() == CellKind.NUMBER) {
            try {
                BigDecimal decimal = new BigDecimal(cell.text()).stripTrailingZeros();
                if (decimal.scale() <= 0 && decimal.precision() <= MAX_SIGNIFICANT_DIGITS) {
                    value = decimal.toBigIntegerExact();
                }
            } catch (ArithmeticException | NumberFormatException ignored) {
                value = null;
            }
        } else if (cell.kind() == CellKind.TEXT) {
            String text = cell.text().strip();
            if ((positive ? POSITIVE_TEXT : NONNEGATIVE_TEXT).matcher(text).matches()) {
                value = new BigInteger(text);
            } else if (NONNEGATIVE_TEXT.matcher(text).matches()) {
                errors.add(error(rowNumber, column, text, "OUT_OF_RANGE", "Must be greater than 0."));
                return null;
            }
        }
        if (value == null) {
            errors.add(error(rowNumber, column, cell.text(), "INVALID_NUMBER",
                    "Must be a whole number without separators, decimals or exponent."));
            return null;
        }
        if (value.signum() < 0 || (positive && value.signum() == 0)) {
            errors.add(error(rowNumber, column, value.toString(), "OUT_OF_RANGE",
                    positive ? "Must be greater than 0." : "Must not be negative."));
            return null;
        }
        if (value.toString().length() > 19) {
            errors.add(error(rowNumber, column, value.toString(), "OUT_OF_RANGE", "The number is too large."));
            return null;
        }
        return value;
    }

    /**
     * Formulas, merged cells, hyperlinks and Excel error values are not data. The contract has no code of its
     * own for the last three, so all four use FORMULA_NOT_SUPPORTED with a specific message (to be confirmed).
     */
    private static boolean unsupported(int row, ImportColumn column, RawCell cell, List<ImportError> errors) {
        return unsupported(row, column.header(), cell, errors);
    }

    private static boolean unsupported(int row, String column, RawCell cell, List<ImportError> errors) {
        String message = null;
        if (cell.formula()) {
            message = "Formulas are not supported; enter the value itself.";
        } else if (cell.merged()) {
            message = "Merged cells are not supported.";
        } else if (cell.hyperlink()) {
            message = "Hyperlinks are not supported.";
        } else if (cell.kind() == CellKind.ERROR) {
            message = "Excel error values are not supported.";
        }
        if (message == null) {
            return false;
        }
        errors.add(error(row, column, null, "FORMULA_NOT_SUPPORTED", message));
        return true;
    }

    private static String integerText(String raw) {
        try {
            BigDecimal decimal = new BigDecimal(raw).stripTrailingZeros();
            return decimal.scale() <= 0 ? decimal.toBigIntegerExact().toString() : raw;
        } catch (ArithmeticException | NumberFormatException e) {
            return raw;
        }
    }

    private static ImportError error(int row, ImportColumn column, String value, String code, String message) {
        return error(row, column.header(), value, code, message);
    }

    private static ImportError error(int row, String column, String value, String code, String message) {
        String echoed = value == null ? null
                : value.codePointCount(0, value.length()) <= ECHO_MAX ? value
                : value.substring(0, value.offsetByCodePoints(0, ECHO_MAX));
        return new ImportError(row, column, echoed, code, message);
    }
}
