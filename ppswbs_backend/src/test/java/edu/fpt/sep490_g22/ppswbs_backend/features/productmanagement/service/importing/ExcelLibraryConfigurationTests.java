package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.importing;

import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** T092: POI zip-bomb limits are applied and a valid small workbook still opens. */
class ExcelLibraryConfigurationTests {

    @BeforeAll
    static void applyLimits() {
        ExcelLibraryConfiguration.apply();
    }

    @Test
    void limitsAreApplied() {
        assertThat(ZipSecureFile.getMinInflateRatio()).isEqualTo(ExcelLibraryConfiguration.MIN_INFLATE_RATIO);
        assertThat(ZipSecureFile.getMaxEntrySize()).isEqualTo(ExcelLibraryConfiguration.MAX_ENTRY_SIZE_BYTES);
        assertThat(ZipSecureFile.getMaxTextSize()).isEqualTo(ExcelLibraryConfiguration.MAX_TEXT_SIZE_CHARS);
    }

    @Test
    void highlyCompressedEntryIsRejectedAsZipBomb(@TempDir Path dir) throws Exception {
        Path bomb = dir.resolve("bomb.zip");
        try (OutputStream out = Files.newOutputStream(bomb); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            byte[] zeros = new byte[1024 * 1024];
            for (int i = 0; i < 20; i++) {
                zip.write(zeros);
            }
            zip.closeEntry();
        }

        assertThatThrownBy(() -> {
            try (ZipSecureFile secure = new ZipSecureFile(bomb.toFile())) {
                try (InputStream in = secure.getInputStream(secure.getEntry("xl/worksheets/sheet1.xml"))) {
                    in.readAllBytes();
                }
            }
        }).hasMessageContaining("Zip bomb");
    }

    @Test
    void validSmallWorkbookStillOpens() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Sản phẩm").createRow(0).createCell(0).setCellValue("Mã nhóm");
            workbook.write(buffer);
        }
        try (XSSFWorkbook reopened = new XSSFWorkbook(new ByteArrayInputStream(buffer.toByteArray()))) {
            assertThat(reopened.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("Mã nhóm");
        }
    }
}
