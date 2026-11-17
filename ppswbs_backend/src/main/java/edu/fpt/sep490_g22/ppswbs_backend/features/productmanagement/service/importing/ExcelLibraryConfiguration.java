package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.importing;

import jakarta.annotation.PostConstruct;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.springframework.context.annotation.Configuration;

/**
 * Apache POI safety limits for the Excel import (T092, research D-10, FR-043).
 *
 * <p>An accepted file is at most 5,000,000 bytes and 500 data rows, so the limits below are far above
 * any valid template yet stop decompression bombs early:
 * <ul>
 *   <li>minimum inflate ratio 0.01: an entry may not expand more than 100 times its compressed size;</li>
 *   <li>maximum uncompressed entry size 64 MiB;</li>
 *   <li>maximum extracted text 10,485,760 characters.</li>
 * </ul>
 * XML is parsed through POI's {@code XMLHelper}, which disables DTDs and external entities (XXE) by
 * default; the parser tests (T093) cover it. These POI settings are JVM-wide; no other module uses POI.
 */
@Configuration(proxyBeanMethods = false)
public class ExcelLibraryConfiguration {

    public static final double MIN_INFLATE_RATIO = 0.01d;
    public static final long MAX_ENTRY_SIZE_BYTES = 64L * 1024 * 1024;
    public static final long MAX_TEXT_SIZE_CHARS = 10_485_760L;

    @PostConstruct
    void applyLimits() {
        apply();
    }

    /** Also used by tests that read workbooks without a Spring context. */
    public static void apply() {
        ZipSecureFile.setMinInflateRatio(MIN_INFLATE_RATIO);
        ZipSecureFile.setMaxEntrySize(MAX_ENTRY_SIZE_BYTES);
        ZipSecureFile.setMaxTextSize(MAX_TEXT_SIZE_CHARS);
    }
}
