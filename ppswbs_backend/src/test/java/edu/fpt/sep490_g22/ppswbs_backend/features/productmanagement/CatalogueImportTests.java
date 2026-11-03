package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductCategory;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.importing.ImportColumn;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.importing.ImportErrorReportWriter;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.importing.ImportTemplateWriter;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.importing.ProductImportParser;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.importing.ProductImportValidator;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.importing.ProductImportValidator.ImportError;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFHyperlink;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** T093: import parser/validator boundaries, file checks, template and error report (FR-040/041/043, SC-008). */
class CatalogueImportTests {

    private static final String[] HEADER = ImportColumn.ORDER.stream().map(ImportColumn::header).toArray(String[]::new);
    private static final List<ProductCategory> CATEGORIES = categories();

    // ---------- valid files ----------

    @Test
    void validFileGroupsRowsIntoDraftProductsWithoutIds() {
        var result = validate(xlsx(HEADER,
                row("A", "Nhẫn bạc trơn", "Mô tả", "Nhẫn bạc", 5, 150000, 3),
                row("A", "Nhẫn bạc trơn", "Mô tả", "Nhẫn bạc", 12, 150000, 0),
                row("B", "Nhẫn cưới", "Mô tả 2", "  nhẫn cưới ", 29, "200000", "2")));

        assertThat(result.errors()).isEmpty();
        assertThat(result.products()).hasSize(2);
        assertThat(result.variantCount()).isEqualTo(3);
        assertThat(result.products().get(0).variants()).extracting(v -> v.size()).containsExactly(5, 12);
        assertThat(result.products().get(1).categoryId()).isEqualTo(2L);
    }

    @Test
    void trailingEmptyRowsAreIgnoredButAnEmptyRowBetweenDataIsAnError() {
        assertThat(validate(xlsx(HEADER, row("A", "N", "D", "Nhẫn bạc", 5, 1, 1), row(), row())).errors()).isEmpty();

        var result = validate(xlsx(HEADER, row("A", "N", "D", "Nhẫn bạc", 5, 1, 1), row(),
                row("B", "N", "D", "Nhẫn bạc", 6, 1, 1)));
        assertThat(codes(result.errors())).containsExactly("EMPTY_ROW_IN_DATA");
        assertThat(result.errors().get(0).row()).isEqualTo(3);
    }

    // ---------- file-level rejections ----------

    @Test
    void headerMustBeExactlyTheSevenColumnsInOrder() {
        String[] missing = Arrays.copyOf(HEADER, 6);
        String[] reordered = HEADER.clone();
        reordered[0] = HEADER[1];
        reordered[1] = HEADER[0];
        String[] extra = Arrays.copyOf(HEADER, 8);
        extra[7] = "Ghi chú";
        String[] wrong = HEADER.clone();
        wrong[5] = "Giá";
        for (String[] header : List.of(missing, reordered, extra, wrong)) {
            assertFileCode(xlsx(header, row("A", "N", "D", "Nhẫn bạc", 5, 1, 1)), CatalogueErrorCode.IMPORT_FILE_INVALID);
        }
    }

    @Test
    void valueInAnExtraColumnRejectsTheFile() {
        assertFileCode(xlsx(HEADER, row("A", "N", "D", "Nhẫn bạc", 5, 1, 1, "lạ")), CatalogueErrorCode.IMPORT_FILE_INVALID);
    }

    @Test
    void emptyFirstSheetIsInvalid() {
        assertFileCode(workbook(workbook -> workbook.createSheet("Sản phẩm")), CatalogueErrorCode.IMPORT_FILE_INVALID);
    }

    @Test
    void fiveHundredRowsPassAndFiveHundredOneAreRejected() {
        assertThat(validate(xlsx(HEADER, rows(500))).rowCount()).isEqualTo(500);
        assertFileCode(xlsx(HEADER, rows(501)), CatalogueErrorCode.IMPORT_ROW_LIMIT_EXCEEDED);
    }

    @Test
    void extensionSignatureSizeMacroAndZipBombAreCheckedFirst() throws Exception {
        byte[] valid = xlsx(HEADER, row("A", "N", "D", "Nhẫn bạc", 5, 1, 1));
        assertThatCode(() -> ProductImportParser.parse("san-pham.xls", valid), CatalogueErrorCode.IMPORT_FILE_TYPE_UNSUPPORTED);
        assertThatCode(() -> ProductImportParser.parse("a.xlsx", "not a zip".getBytes()), CatalogueErrorCode.IMPORT_FILE_INVALID);
        assertThatCode(() -> ProductImportParser.parse("a.xlsx", new byte[5_000_001]), CatalogueErrorCode.IMPORT_FILE_TOO_LARGE);
        byte[] withMacro = withExtraEntry(valid, "xl/vbaProject.bin", new byte[16]);
        assertThatCode(() -> ProductImportParser.parse("a.xlsx", withMacro), CatalogueErrorCode.IMPORT_FILE_INVALID);
        byte[] withBomb = withExtraEntry(valid, "xl/media/padding.xml", new byte[20 * 1024 * 1024]);
        assertThatCode(() -> ProductImportParser.parse("a.xlsx", withBomb), CatalogueErrorCode.IMPORT_FILE_INVALID);
    }

    // ---------- cell content ----------

    @Test
    void formulaMergedCellHyperlinkAndErrorValueAreReported() {
        byte[] file = workbook(workbook -> {
            Sheet sheet = data(workbook, HEADER);
            fill(sheet.createRow(1), row("A", "N", "D", "Nhẫn bạc", 5, 1, 1));
            sheet.getRow(1).getCell(6).setCellFormula("1+1");
            fill(sheet.createRow(2), row("B", "N", "D", "Nhẫn bạc", 6, 1, 1));
            sheet.addMergedRegion(new CellRangeAddress(2, 2, 1, 2));
            fill(sheet.createRow(3), row("C", "N", "D", "Nhẫn bạc", 7, 1, 1));
            XSSFHyperlink link = workbook.getCreationHelper().createHyperlink(HyperlinkType.URL);
            link.setAddress("https://example.com");
            sheet.getRow(3).getCell(1).setHyperlink(link);
            fill(sheet.createRow(4), row("D", "N", "D", "Nhẫn bạc", 8, 1, 1));
            sheet.getRow(4).getCell(5).setCellErrorValue(org.apache.poi.ss.usermodel.FormulaError.NA.getCode());
        });

        var errors = validate(file).errors();
        assertThat(errors).extracting(ImportError::code).containsOnly("FORMULA_NOT_SUPPORTED");
        assertThat(errors).extracting(ImportError::row).contains(2, 3, 4, 5);
        assertThat(errors).filteredOn(error -> error.row() == 2).singleElement()
                .satisfies(error -> assertThat(error.value()).isNull());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.000", "12,5", "5.0", "1e3", "+5", "05", " 1 000"})
    void numbersWrittenAsFormattedTextAreInvalid(String text) {
        var errors = validate(xlsx(HEADER, row("A", "N", "D", "Nhẫn bạc", text, 1, 1))).errors();
        assertThat(codes(errors)).containsExactly("INVALID_NUMBER");
        assertThat(errors.get(0).column()).isEqualTo("Size");
    }

    @Test
    void numericCellMustBeAWholeNumber() {
        assertThat(codes(validate(xlsx(HEADER, row("A", "N", "D", "Nhẫn bạc", 12.5, 1, 1))).errors()))
                .containsExactly("INVALID_NUMBER");
        assertThat(validate(xlsx(HEADER, row("A", "N", "D", "Nhẫn bạc", 12.0, 1, 1))).errors()).isEmpty();
    }

    @Test
    void sizeBoundariesAndPriceAndQuantityDomains() {
        assertThat(validate(xlsx(HEADER, row("A", "N", "D", "Nhẫn bạc", 5, 1, 0),
                row("A", "N", "D", "Nhẫn bạc", 29, 1, 0))).errors()).isEmpty();
        var errors = validate(xlsx(HEADER,
                row("A", "N", "D", "Nhẫn bạc", 4, 1, 1),
                row("B", "N", "D", "Nhẫn bạc", 30, 1, 1),
                row("C", "N", "D", "Nhẫn bạc", 5, 0, 1),
                row("D", "N", "D", "Nhẫn bạc", 5, 1, -1))).errors();
        assertThat(codes(errors)).containsExactly("OUT_OF_RANGE", "OUT_OF_RANGE", "OUT_OF_RANGE", "OUT_OF_RANGE");
        assertThat(errors).extracting(ImportError::column)
                .containsExactly("Size", "Size", "Giá đề xuất (VND)", "Số lượng");
    }

    @Test
    void textLimitsAreMeasuredInCodePoints() {
        assertThat(validate(xlsx(HEADER, row("A", "n".repeat(200), "d".repeat(5000), "Nhẫn bạc", 5, 1, 1))).errors())
                .isEmpty();
        var errors = validate(xlsx(HEADER,
                row("A", "n".repeat(201), "d", "Nhẫn bạc", 5, 1, 1),
                row("B", "n", "d".repeat(5001), "Nhẫn bạc", 5, 1, 1),
                row("C", "  ", "d", "Nhẫn bạc", 5, 1, 1))).errors();
        assertThat(codes(errors)).containsExactly("TOO_LONG", "TOO_LONG", "REQUIRED");
    }

    @Test
    void categoryMustBeActiveAndIsMatchedTrimmedCaseInsensitively() {
        assertThat(validate(xlsx(HEADER, row("A", "N", "D", " NHẪN BẠC ", 5, 1, 1))).errors()).isEmpty();
        var errors = validate(xlsx(HEADER,
                row("A", "N", "D", "Nhẫn vàng", 5, 1, 1),
                row("B", "N", "D", "Ngừng bán", 5, 1, 1),
                row("C", "N", "D", "Nhan bac", 5, 1, 1))).errors();
        assertThat(codes(errors)).containsOnly("CATEGORY_NOT_ACTIVE_OR_UNKNOWN").hasSize(3);
    }

    @Test
    void groupRulesDuplicateSizeMismatchAndTwentyFiveSizes() {
        var errors = validate(xlsx(HEADER,
                row("A", "N", "D", "Nhẫn bạc", 5, 1, 1),
                row("A", "N", "D", "Nhẫn bạc", 5, 1, 1),
                row("A", "Khác", "D", "Nhẫn bạc", 6, 1, 1),
                row("A", "N", "D", "Nhẫn cưới", 7, 1, 1))).errors();
        assertThat(codes(errors)).containsExactly("DUPLICATE_SIZE_IN_GROUP", "GROUP_FIELD_MISMATCH", "GROUP_FIELD_MISMATCH");

        List<Object[]> twentyFive = new ArrayList<>();
        for (int size = 5; size <= 29; size++) {
            twentyFive.add(row("A", "N", "D", "Nhẫn bạc", size, 1, 1));
        }
        assertThat(validate(xlsx(HEADER, twentyFive.toArray(Object[][]::new))).products().get(0).variants()).hasSize(25);
    }

    @Test
    void groupKeyIsCaseSensitiveAndLimitedToOneHundredCharacters() {
        var result = validate(xlsx(HEADER, row("a", "N", "D", "Nhẫn bạc", 5, 1, 1), row("A", "N", "D", "Nhẫn bạc", 5, 1, 1)));
        assertThat(result.products()).hasSize(2);
        assertThat(codes(validate(xlsx(HEADER, row("k".repeat(101), "N", "D", "Nhẫn bạc", 5, 1, 1))).errors()))
                .containsExactly("TOO_LONG");
    }

    @Test
    void everyErrorIsReportedWithoutTruncation() {
        Object[][] bad = new Object[500][];
        for (int i = 0; i < 500; i++) {
            bad[i] = row("", "", "", "Không có", "x", "x", "x");
        }
        var errors = validate(xlsx(HEADER, bad)).errors();
        assertThat(errors).hasSize(500 * 7);
    }

    // ---------- template and error report ----------

    @Test
    void errorReportNeutralizesFormulaLikeValues() throws Exception {
        List<ImportError> errors = List.of(
                new ImportError(2, "Tên sản phẩm", "=HYPERLINK(\"x\")", "TOO_LONG", "m"),
                new ImportError(3, "Mô tả", "+1", "TOO_LONG", "m"),
                new ImportError(4, "Mô tả", "-1", "TOO_LONG", "m"),
                new ImportError(5, "Mô tả", "@SUM(A1)", "TOO_LONG", "m"),
                new ImportError(6, "Mô tả", "an toàn", "TOO_LONG", "m"));
        try (XSSFWorkbook report = new XSSFWorkbook(new ByteArrayInputStream(ImportErrorReportWriter.write(errors)))) {
            Sheet sheet = report.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(5);
            List<String> values = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                Cell cell = sheet.getRow(i).getCell(2);
                assertThat(cell.getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
                values.add(cell.getStringCellValue());
            }
            assertThat(values).containsExactly("'=HYPERLINK(\"x\")", "'+1", "'-1", "'@SUM(A1)", "an toàn");
        }
    }

    @Test
    void templateHasOnlyHeadersOnTheDataSheetAndListsActiveCategories() throws Exception {
        byte[] template = ImportTemplateWriter.write(CATEGORIES);
        assertThat(ProductImportParser.parse("mau.xlsx", template)).isEmpty();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template))) {
            assertThat(workbook.getSheetName(0)).isEqualTo("Sản phẩm");
            Sheet categories = workbook.getSheet("Danh mục");
            List<String> names = new ArrayList<>();
            for (int i = 1; i <= categories.getLastRowNum(); i++) {
                names.add(categories.getRow(i).getCell(0).getStringCellValue());
            }
            assertThat(names).containsExactly("Nhẫn bạc", "Nhẫn cưới");
        }
    }

    // ---------- helpers ----------

    private static ProductImportValidator.Result validate(byte[] file) {
        return ProductImportValidator.validate(ProductImportParser.parse("san-pham.xlsx", file), CATEGORIES);
    }

    private static List<String> codes(List<ImportError> errors) {
        return errors.stream().map(ImportError::code).toList();
    }

    private static Object[] row(Object... values) {
        return values;
    }

    private static Object[][] rows(int count) {
        Object[][] rows = new Object[count][];
        for (int i = 0; i < count; i++) {
            rows[i] = row("G" + i, "N", "D", "Nhẫn bạc", 5, 1, 1);
        }
        return rows;
    }

    private static byte[] xlsx(String[] header, Object[]... rows) {
        return workbook(workbook -> {
            Sheet sheet = data(workbook, header);
            for (int i = 0; i < rows.length; i++) {
                fill(sheet.createRow(i + 1), rows[i]);
            }
        });
    }

    private static Sheet data(XSSFWorkbook workbook, String[] header) {
        Sheet sheet = workbook.createSheet("Sản phẩm");
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < header.length; i++) {
            headerRow.createCell(i).setCellValue(header[i]);
        }
        return sheet;
    }

    private static void fill(Row row, Object[] values) {
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            if (value instanceof Number number) {
                row.createCell(i).setCellValue(number.doubleValue());
            } else if (value != null) {
                row.createCell(i).setCellValue(value.toString());
            }
        }
    }

    private static byte[] workbook(Consumer<XSSFWorkbook> builder) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            builder.accept(workbook);
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Copies the package and adds one extra entry (macro project or a highly compressible bomb). */
    private static byte[] withExtraEntry(byte[] xlsx, String name, byte[] content) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(xlsx)); ZipOutputStream zip = new ZipOutputStream(out)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                zip.putNextEntry(new ZipEntry(entry.getName()));
                zip.write(in.readAllBytes());
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry(name));
            zip.write(content);
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    private static void assertFileCode(byte[] file, CatalogueErrorCode code) {
        assertThatCode(() -> ProductImportParser.parse("san-pham.xlsx", file), code);
    }

    private static void assertThatCode(Runnable action, CatalogueErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(CatalogueException.class, error -> assertThat(error.code()).isEqualTo(code));
    }

    private static List<ProductCategory> categories() {
        ProductCategory silver = ProductCategory.create("Nhẫn bạc", null);
        ProductCategory wedding = ProductCategory.create("Nhẫn cưới", null);
        ProductCategory inactive = ProductCategory.create("Ngừng bán", null);
        inactive.setActive(false);
        setId(silver, 1L);
        setId(wedding, 2L);
        setId(inactive, 3L);
        return List.of(silver, wedding, inactive);
    }

    private static void setId(Object entity, long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
