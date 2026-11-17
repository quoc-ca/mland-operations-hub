package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.importing;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.importing.ProductImportValidator.ImportError;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Writes the downloadable error report (T096, FR-041/043): every error with row, column, value, code and
 * message, never truncated. Every cell is a plain string, and any text starting with {@code =}, {@code +},
 * {@code -} or {@code @} is prefixed with an apostrophe so a spreadsheet never treats it as a formula.
 */
public final class ImportErrorReportWriter {

    public static final String SHEET = "Lỗi";
    private static final String[] HEADERS = {"Dòng", "Cột", "Giá trị", "Mã lỗi", "Thông báo"};

    private ImportErrorReportWriter() {
    }

    public static byte[] write(List<ImportError> errors) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(SHEET);
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }
            int rowIndex = 1;
            for (ImportError error : errors) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(Integer.toString(error.row()));
                row.createCell(1).setCellValue(neutralize(error.column()));
                row.createCell(2).setCellValue(neutralize(error.value()));
                row.createCell(3).setCellValue(neutralize(error.code()));
                row.createCell(4).setCellValue(neutralize(error.message()));
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot build the import error report", e);
        }
    }

    /** Disables formula injection for values copied from the uploaded file. */
    public static String neutralize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        char first = value.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@' ? "'" + value : value;
    }
}
