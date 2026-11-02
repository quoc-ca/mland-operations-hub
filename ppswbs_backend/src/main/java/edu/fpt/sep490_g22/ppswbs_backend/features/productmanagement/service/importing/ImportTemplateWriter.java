package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.importing;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductCategory;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Builds the {@code .xlsx} import template per request (T096, US9.1): the data sheet "Sản phẩm" with the
 * seven headers only, an instructions sheet with an example, and the active category names. The example
 * stays on the instructions sheet so it can never be imported by mistake. Nothing is stored.
 */
public final class ImportTemplateWriter {

    public static final String DATA_SHEET = "Sản phẩm";
    public static final String GUIDE_SHEET = "Hướng dẫn";
    public static final String CATEGORY_SHEET = "Danh mục";

    private ImportTemplateWriter() {
    }

    public static byte[] write(List<ProductCategory> activeCategories) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet data = workbook.createSheet(DATA_SHEET);
            Row header = data.createRow(0);
            for (ImportColumn column : ImportColumn.ORDER) {
                header.createCell(column.ordinal()).setCellValue(column.header());
                data.setColumnWidth(column.ordinal(), 22 * 256);
            }
            data.createFreezePane(0, 1);

            Sheet guide = workbook.createSheet(GUIDE_SHEET);
            String[] lines = {
                    "Mỗi dòng là một size của một sản phẩm. Các dòng cùng Mã nhóm tạo thành một sản phẩm.",
                    "Các dòng cùng Mã nhóm phải có cùng Tên sản phẩm, Mô tả và Danh mục; tối đa 25 size khác nhau.",
                    "Tên sản phẩm: 1–200 ký tự. Mô tả: 1–5.000 ký tự. Danh mục: đúng tên một danh mục đang hoạt động.",
                    "Size: số nguyên từ 5 đến 29. Giá đề xuất: số nguyên VND lớn hơn 0. Số lượng: số nguyên từ 0.",
                    "Không dùng công thức, ô gộp, hyperlink, dấu phân cách hàng nghìn hay số thập phân.",
                    "Không thêm, xóa hay đổi thứ tự cột. Tối đa 500 dòng dữ liệu; file tối đa 5.000.000 byte.",
                    "File không chứa ảnh: sau khi import, thêm ảnh cho từng sản phẩm trước khi gửi duyệt.",
                    "",
                    "Ví dụ (không nhập ở sheet này):"
            };
            for (int i = 0; i < lines.length; i++) {
                guide.createRow(i).createCell(0).setCellValue(lines[i]);
            }
            String[][] example = {
                    {"NHAN-01", "Nhẫn bạc trơn", "Nhẫn bạc 925 mặt trơn", "Nhẫn bạc", "12", "350000", "5"},
                    {"NHAN-01", "Nhẫn bạc trơn", "Nhẫn bạc 925 mặt trơn", "Nhẫn bạc", "14", "350000", "3"}
            };
            Row exampleHeader = guide.createRow(lines.length);
            for (ImportColumn column : ImportColumn.ORDER) {
                exampleHeader.createCell(column.ordinal()).setCellValue(column.header());
            }
            for (int i = 0; i < example.length; i++) {
                Row row = guide.createRow(lines.length + 1 + i);
                for (int column = 0; column < example[i].length; column++) {
                    row.createCell(column).setCellValue(example[i][column]);
                }
            }

            Sheet categories = workbook.createSheet(CATEGORY_SHEET);
            categories.createRow(0).createCell(0).setCellValue("Danh mục đang hoạt động");
            int rowIndex = 1;
            for (ProductCategory category : activeCategories) {
                if (category.isActive()) {
                    categories.createRow(rowIndex++).createCell(0).setCellValue(category.getName());
                }
            }
            workbook.setActiveSheet(0);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot build the import template", e);
        }
    }
}
