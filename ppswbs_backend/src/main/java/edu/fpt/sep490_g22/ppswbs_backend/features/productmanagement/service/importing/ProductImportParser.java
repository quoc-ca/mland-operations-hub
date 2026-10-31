package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.importing;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.SharedStrings;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;

/**
 * Streaming reader of the import template (T096, FR-040/043). Checks the file before decompressing it,
 * reads only the first sheet with SAX and returns raw cells; it never evaluates formulas, follows links,
 * keeps the file or logs cell values.
 *
 * <ul>
 *   <li>File-level problems are HTTP errors: {@code IMPORT_FILE_TOO_LARGE} (over 5,000,000 bytes),
 *       {@code IMPORT_FILE_TYPE_UNSUPPORTED} (not {@code .xlsx}), {@code IMPORT_FILE_INVALID} (not an OOXML
 *       ZIP, macros, missing sheet, wrong header or extra columns) and {@code IMPORT_ROW_LIMIT_EXCEEDED}
 *       (more than 500 data rows).</li>
 *   <li>Cell-level problems (formula, merged cell, hyperlink, Excel error value, empty row in the data) are
 *       returned as flags for the validator to report per row and column.</li>
 * </ul>
 */
public final class ProductImportParser {

    public static final long MAX_FILE_BYTES = 5_000_000L;
    public static final int MAX_DATA_ROWS = 500;
    /** Stops reading absurdly long sheets early; anything past this is far over the row limit anyway. */
    private static final int HARD_ROW_SCAN_LIMIT = 10_000;

    public enum CellKind {
        EMPTY, TEXT, NUMBER, BOOLEAN, ERROR
    }

    /** One raw cell. {@code text} is the stored value (never a cached formula result). */
    public record RawCell(CellKind kind, String text, boolean formula, boolean merged, boolean hyperlink) {
        static final RawCell EMPTY = new RawCell(CellKind.EMPTY, null, false, false, false);

        public boolean isBlank() {
            return !formula && !merged && !hyperlink
                    && (kind == CellKind.EMPTY || (kind == CellKind.TEXT && (text == null || text.isBlank())));
        }
    }

    /** One data row; {@code rowNumber} is the Excel row number (header is row 1). */
    public record RawRow(int rowNumber, List<RawCell> cells) {
        public RawCell cell(ImportColumn column) {
            return cells.get(column.ordinal());
        }

        public boolean isBlank() {
            return cells.stream().allMatch(RawCell::isBlank);
        }
    }

    private ProductImportParser() {
    }

    public static List<RawRow> parse(String fileName, byte[] file) {
        if (file == null || file.length == 0) {
            throw new CatalogueException(CatalogueErrorCode.IMPORT_FILE_INVALID);
        }
        if (file.length > MAX_FILE_BYTES) {
            throw new CatalogueException(CatalogueErrorCode.IMPORT_FILE_TOO_LARGE);
        }
        if (fileName == null || !fileName.strip().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new CatalogueException(CatalogueErrorCode.IMPORT_FILE_TYPE_UNSUPPORTED);
        }
        checkPackageWithoutDecompressing(file);
        ExcelLibraryConfiguration.apply();
        try (OPCPackage pkg = OPCPackage.open(new ByteArrayInputStream(file))) {
            XSSFReader reader = new XSSFReader(pkg);
            SharedStrings strings = reader.getSharedStringsTable();
            Iterator<InputStream> sheets = reader.getSheetsData();
            if (!sheets.hasNext()) {
                throw new CatalogueException(CatalogueErrorCode.IMPORT_FILE_INVALID);
            }
            SheetHandler handler = new SheetHandler(strings);
            try (InputStream firstSheet = sheets.next()) {
                XMLReader xml = XMLHelper.newXMLReader();
                xml.setContentHandler(handler);
                xml.parse(new InputSource(firstSheet));
            } catch (StopParsing ignored) {
                throw new CatalogueException(CatalogueErrorCode.IMPORT_ROW_LIMIT_EXCEEDED);
            }
            return handler.toDataRows();
        } catch (CatalogueException e) {
            throw e;
        } catch (Exception e) {
            // Corrupt package, zip bomb or unreadable XML: reject the file without echoing any content.
            throw new CatalogueException(CatalogueErrorCode.IMPORT_FILE_INVALID);
        }
    }

    /** ZIP/OOXML signature, required parts and no macro project, read from the central directory only. */
    private static void checkPackageWithoutDecompressing(byte[] file) {
        if (file.length < 4 || file[0] != 'P' || file[1] != 'K' || file[2] != 3 || file[3] != 4) {
            throw new CatalogueException(CatalogueErrorCode.IMPORT_FILE_INVALID);
        }
        Set<String> names = new HashSet<>();
        try (ZipFile zip = ZipFile.builder().setSeekableByteChannel(new SeekableInMemoryByteChannel(file)).get()) {
            Enumeration<ZipArchiveEntry> entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                names.add(entries.nextElement().getName().toLowerCase(Locale.ROOT));
            }
        } catch (IOException e) {
            throw new CatalogueException(CatalogueErrorCode.IMPORT_FILE_INVALID);
        }
        boolean hasMacros = names.stream().anyMatch(name -> name.endsWith("vbaproject.bin"));
        if (!names.contains("[content_types].xml") || !names.contains("xl/workbook.xml") || hasMacros) {
            throw new CatalogueException(CatalogueErrorCode.IMPORT_FILE_INVALID);
        }
    }

    private static final class StopParsing extends RuntimeException {
        StopParsing() {
            super(null, null, false, false);
        }
    }

    /** SAX handler for one worksheet: values, formulas, merged ranges and hyperlinks. */
    private static final class SheetHandler extends DefaultHandler {
        private final SharedStrings strings;
        private final TreeMap<Integer, TreeMap<Integer, RawCell>> rows = new TreeMap<>();
        private final List<String> mergedRanges = new ArrayList<>();
        private final List<String> hyperlinkRefs = new ArrayList<>();

        private int currentRow;
        private int currentColumn;
        private String cellType;
        private boolean cellHasFormula;
        private boolean inValue;
        private boolean inInlineText;
        private final StringBuilder value = new StringBuilder();

        SheetHandler(SharedStrings strings) {
            this.strings = strings;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            switch (localName) {
                case "row" -> {
                    currentRow = Integer.parseInt(attributes.getValue("r"));
                    if (currentRow > HARD_ROW_SCAN_LIMIT) {
                        throw new StopParsing();
                    }
                }
                case "c" -> {
                    CellReference reference = new CellReference(attributes.getValue("r"));
                    currentColumn = reference.getCol();
                    cellType = attributes.getValue("t");
                    cellHasFormula = false;
                    value.setLength(0);
                }
                case "f" -> cellHasFormula = true;
                case "v" -> {
                    inValue = true;
                    value.setLength(0);
                }
                case "t" -> {
                    if ("inlineStr".equals(cellType)) {
                        inInlineText = true;
                    }
                }
                case "mergeCell" -> mergedRanges.add(attributes.getValue("ref"));
                case "hyperlink" -> hyperlinkRefs.add(attributes.getValue("ref"));
                default -> {
                }
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (inValue || inInlineText) {
                value.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            switch (localName) {
                case "v" -> inValue = false;
                case "t" -> inInlineText = false;
                case "c" -> rows.computeIfAbsent(currentRow, ignored -> new TreeMap<>())
                        .put(currentColumn, toCell());
                default -> {
                }
            }
        }

        private RawCell toCell() {
            String raw = value.toString();
            if (cellHasFormula) {
                return new RawCell(CellKind.TEXT, null, true, false, false);
            }
            if (cellType == null || "n".equals(cellType)) {
                return raw.isEmpty() ? RawCell.EMPTY : new RawCell(CellKind.NUMBER, raw, false, false, false);
            }
            return switch (cellType) {
                case "s" -> new RawCell(CellKind.TEXT, strings.getItemAt(Integer.parseInt(raw)).getString(),
                        false, false, false);
                case "inlineStr", "str" -> new RawCell(CellKind.TEXT, raw, false, false, false);
                case "b" -> new RawCell(CellKind.BOOLEAN, raw, false, false, false);
                case "e" -> new RawCell(CellKind.ERROR, null, false, false, false);
                default -> new RawCell(CellKind.TEXT, raw, false, false, false);
            };
        }

        List<RawRow> toDataRows() {
            Set<Long> merged = coordinates(mergedRanges);
            Set<Long> linked = coordinates(hyperlinkRefs);

            TreeMap<Integer, RawCell> header = rows.getOrDefault(1, new TreeMap<>());
            for (int column = 0; column < ImportColumn.ORDER.size(); column++) {
                RawCell cell = header.getOrDefault(column, RawCell.EMPTY);
                if (cell.kind() != CellKind.TEXT || cell.text() == null
                        || !cell.text().strip().equals(ImportColumn.ORDER.get(column).header())) {
                    throw new CatalogueException(CatalogueErrorCode.IMPORT_FILE_INVALID);
                }
            }
            for (TreeMap<Integer, RawCell> row : rows.values()) {
                for (var entry : row.entrySet()) {
                    if (entry.getKey() >= ImportColumn.ORDER.size() && !entry.getValue().isBlank()) {
                        throw new CatalogueException(CatalogueErrorCode.IMPORT_FILE_INVALID);
                    }
                }
            }

            int lastDataRow = 1;
            for (var entry : rows.entrySet()) {
                if (entry.getKey() > 1 && entry.getValue().values().stream().anyMatch(cell -> !cell.isBlank())) {
                    lastDataRow = entry.getKey();
                }
            }
            for (long coordinate : merged) {
                lastDataRow = Math.max(lastDataRow, (int) (coordinate >>> 32));
            }
            for (long coordinate : linked) {
                lastDataRow = Math.max(lastDataRow, (int) (coordinate >>> 32));
            }
            if (lastDataRow - 1 > MAX_DATA_ROWS) {
                throw new CatalogueException(CatalogueErrorCode.IMPORT_ROW_LIMIT_EXCEEDED);
            }

            List<RawRow> result = new ArrayList<>();
            for (int rowNumber = 2; rowNumber <= lastDataRow; rowNumber++) {
                TreeMap<Integer, RawCell> row = rows.getOrDefault(rowNumber, new TreeMap<>());
                List<RawCell> cells = new ArrayList<>(ImportColumn.ORDER.size());
                for (int column = 0; column < ImportColumn.ORDER.size(); column++) {
                    RawCell cell = row.getOrDefault(column, RawCell.EMPTY);
                    long key = ((long) rowNumber << 32) | column;
                    boolean isMerged = merged.contains(key);
                    boolean isLinked = linked.contains(key);
                    cells.add(isMerged || isLinked
                            ? new RawCell(cell.kind(), cell.text(), cell.formula(), isMerged, isLinked)
                            : cell);
                }
                result.add(new RawRow(rowNumber, Collections.unmodifiableList(cells)));
            }
            return result;
        }

        /** Expands "A2:C3"-style references to (row << 32 | column), 1-based rows. Bounded by the row limit. */
        private static Set<Long> coordinates(List<String> refs) {
            Set<Long> result = new HashSet<>();
            for (String ref : refs) {
                if (ref == null) {
                    continue;
                }
                String[] parts = ref.split(":");
                CellReference first = new CellReference(parts[0]);
                CellReference last = parts.length > 1 ? new CellReference(parts[1]) : first;
                int lastRow = Math.min(last.getRow() + 1, HARD_ROW_SCAN_LIMIT);
                int lastColumn = Math.min(last.getCol(), ImportColumn.ORDER.size());
                for (int row = first.getRow() + 1; row <= lastRow; row++) {
                    for (int column = first.getCol(); column <= lastColumn; column++) {
                        result.add(((long) row << 32) | column);
                    }
                }
            }
            return result;
        }
    }
}
