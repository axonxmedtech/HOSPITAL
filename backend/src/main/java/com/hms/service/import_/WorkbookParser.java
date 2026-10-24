package com.hms.service.import_;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * Reads an uploaded .csv or .xlsx and pushes it, one row at a time, to a {@link RowSink}.
 * Nothing about the rows is retained here: a 100,000-row workbook costs the parser one row of
 * heap at a time, and what the caller keeps is the caller's decision.
 *
 * <p><b>Ownership.</b> The upload arrives as a {@link SpooledUpload}, which the caller owns and
 * closes; this class opens streams on it and closes those streams, and creates no files of its
 * own. Every POI package, SAX reader and CSV parser is closed in try-with-resources whether the
 * parse finishes, stops early or fails.
 *
 * <p><b>What a value is.</b> A cell reaches the sink as a trimmed raw string. Text is verbatim
 * (a phone typed as {@code +91 98765-43210} stays exactly that; a leading {@code =} stays too —
 * formula-injection escaping is an export concern). A numeric .xlsx cell is rendered with its
 * own number format under {@link Locale#ROOT}; a date-formatted numeric cell is rendered as ISO
 * {@code yyyy-MM-dd} (or {@code yyyy-MM-ddTHH:mm:ss} when it carries a time), never in the
 * locale-dependent display format, so "3/4/1977" can never be read two ways downstream. A
 * formula cell yields the value Excel last cached for it; formulas are never evaluated — no
 * {@code FormulaEvaluator} is ever constructed on untrusted input — and a formula with no cached
 * value is an empty cell.
 *
 * <p><b>Security posture for .xlsx.</b> The package is opened read-only through POI with POI's
 * default zip-bomb protection (inflate ratio, max entry size) untouched, read with the streaming
 * event API ({@link XSSFReader} + SAX) so sheet XML is never materialised, and the shared-strings
 * table is the read-only variant. Bytes that are not an OOXML package are refused as
 * {@link ParseErrorCode#NOT_AN_XLSX} whatever their extension said. Nothing from the file —
 * headers, cells, sheet names — is ever logged.
 *
 * <p><b>Limits</b> come from {@link ParserLimits}: more than {@code maxColumns} headers or
 * {@code maxRows} data rows refuses the file; a cell over {@code maxCellChars} is not truncated but
 * dropped and the row delivered flagged (see {@link RowProblem}).
 */
@Component
public class WorkbookParser {

    /** Sheet name reported for CSV, which has none. */
    public static final String CSV_SHEET_NAME = "csv";

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final ParserLimits limits;

    public WorkbookParser() {
        this(ParserLimits.DEFAULT);
    }

    /** Package-visible so tests can prove the boundaries with small limits; production wiring uses {@link ParserLimits#DEFAULT}. */
    WorkbookParser(ParserLimits limits) {
        this.limits = limits;
    }

    public ParserLimits limits() {
        return limits;
    }

    // ── public API ───────────────────────────────────────────────────────────

    /**
     * Parses the upload and streams it to {@code sink}.
     *
     * @param sheetName for XLSX, the sheet to read; {@code null} means the first sheet. Ignored for CSV.
     * @throws ImportParseException for any file-level refusal (see {@link ParseErrorCode})
     */
    public void parse(SpooledUpload upload, ImportFormat format, String sheetName, RowSink sink) {
        if (upload.isEmpty()) {
            throw new ImportParseException(ParseErrorCode.EMPTY_FILE, "The file is empty.");
        }
        switch (format) {
            case CSV -> parseCsv(upload, sink);
            case XLSX -> parseXlsx(upload, sheetName, sink);
        }
    }

    /** The sheet names of an .xlsx, in workbook order. Refuses non-XLSX bytes the same way {@link #parse} does. */
    public List<String> sheetNames(SpooledUpload upload) {
        if (upload.isEmpty()) {
            throw new ImportParseException(ParseErrorCode.EMPTY_FILE, "The file is empty.");
        }
        try (OPCPackage pkg = openPackage(upload)) {
            XSSFReader reader = new XSSFReader(pkg);
            XSSFReader.SheetIterator it = (XSSFReader.SheetIterator) reader.getSheetsData();
            List<String> names = new ArrayList<>();
            while (it.hasNext()) {
                try (InputStream ignored = it.next()) {
                    names.add(it.getSheetName());
                }
            }
            return names;
        } catch (ImportParseException e) {
            throw e;
        } catch (Exception e) {
            throw unreadable(e);
        }
    }

    // ── CSV ──────────────────────────────────────────────────────────────────

    private static final CSVFormat CSV = CSVFormat.RFC4180
            .builder()
            .setIgnoreEmptyLines(true)
            .setAllowMissingColumnNames(true)
            .setTrim(false)
            .build();

    private void parseCsv(SpooledUpload upload, RowSink sink) {
        RowAssembler assembler = new RowAssembler(CSV_SHEET_NAME, sink);
        try (InputStream raw = upload.open();
                Reader reader = new InputStreamReader(skipBom(raw), StandardCharsets.UTF_8);
                CSVParser parser = CSVParser.parse(reader, CSV)) {
            for (CSVRecord record : parser) {
                int rowNum = (int) record.getRecordNumber();
                List<String> cells = record.toList();
                if (!assembler.headerSeen()) {
                    if (isBlank(cells)) continue; // a leading blank record is not a header
                    assembler.headerRowNum = rowNum;
                    assembler.header(cells);
                    continue;
                }
                if (!assembler.row(rowNum, cells)) return;
            }
            assembler.finish();
        } catch (UncheckedIOException | IOException | IllegalStateException e) {
            // commons-csv reports unbalanced or misplaced quotes as an IOException (wrapped
            // unchecked by the iterator) or an IllegalStateException. The message names a line,
            // never content, but it is library wording; ours names the row we were on.
            throw new ImportParseException(
                    ParseErrorCode.MALFORMED_CSV,
                    "The CSV file has malformed quoting near row " + (assembler.lastRowNum + 1)
                            + ". Every quoted field must be closed, and a quote inside a quoted field must be doubled.",
                    assembler.lastRowNum + 1,
                    null,
                    e);
        }
    }

    /** Strips a UTF-8 byte-order mark if present; Excel writes one and it must not become part of the first header. */
    private static InputStream skipBom(InputStream in) throws IOException {
        PushbackInputStream pushback = new PushbackInputStream(in, UTF8_BOM.length);
        byte[] head = new byte[UTF8_BOM.length];
        int read = pushback.read(head, 0, head.length);
        if (read == UTF8_BOM.length && head[0] == UTF8_BOM[0] && head[1] == UTF8_BOM[1] && head[2] == UTF8_BOM[2]) {
            return pushback;
        }
        if (read > 0) pushback.unread(head, 0, read);
        return pushback;
    }

    // ── XLSX ─────────────────────────────────────────────────────────────────

    private void parseXlsx(SpooledUpload upload, String sheetName, RowSink sink) {
        try (OPCPackage pkg = openPackage(upload)) {
            XSSFReader reader = new XSSFReader(pkg);
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(pkg);
            StylesTable styles = reader.getStylesTable();
            XSSFReader.SheetIterator it = (XSSFReader.SheetIterator) reader.getSheetsData();

            while (it.hasNext()) {
                try (InputStream sheet = it.next()) {
                    String current = it.getSheetName();
                    if (sheetName != null && !sheetName.equals(current)) continue;

                    RowAssembler assembler = new RowAssembler(current, sink);
                    XMLReader xml = XMLHelper.newXMLReader();
                    xml.setContentHandler(new XSSFSheetXMLHandler(
                            styles, strings, new SheetCollector(assembler), new IsoDateFormatter(), false));
                    try {
                        xml.parse(new InputSource(sheet));
                    } catch (StopParsing stop) {
                        return; // the sink asked to stop; resources close on the way out
                    }
                    assembler.finish();
                    return;
                }
            }
            throw new ImportParseException(
                    ParseErrorCode.SHEET_NOT_FOUND,
                    sheetName == null ? "The workbook has no sheets." : "The workbook has no sheet named \"" + sheetName + "\".");
        } catch (ImportParseException e) {
            throw e;
        } catch (SAXException e) {
            if (e.getCause() instanceof ImportParseException ipe) throw ipe;
            if (e.getCause() instanceof StopParsing) return;
            throw unreadable(e);
        } catch (Exception e) {
            throw unreadable(e);
        }
    }

    /**
     * Opens the spooled file as an OOXML package, read-only. POI's own guards apply unchanged:
     * {@code ZipSecureFile} inflate-ratio and entry-size limits are never touched here.
     */
    private static OPCPackage openPackage(SpooledUpload upload) {
        try {
            return OPCPackage.open(upload.path().toFile(), PackageAccess.READ);
        } catch (Exception e) {
            // NotOfficeXmlFileException, OLE2NotOfficeXmlFileException, InvalidFormatException,
            // zip failures: whatever the extension claimed, these bytes are not an .xlsx.
            throw new ImportParseException(
                    ParseErrorCode.NOT_AN_XLSX,
                    "The file is not a readable Excel (.xlsx) workbook. Save it as .xlsx or .csv and upload again.",
                    null,
                    null,
                    e);
        }
    }

    private static ImportParseException unreadable(Exception e) {
        // The cause is kept for server-side diagnosis; the message carries no file content.
        return new ImportParseException(
                ParseErrorCode.UNREADABLE, "The file could not be read. It may be damaged or not a supported format.", null, null, e);
    }

    /** Thrown from inside the SAX callbacks when the sink returns false; never leaves this class. */
    private static final class StopParsing extends RuntimeException {
        StopParsing() {
            super(null, null, false, false);
        }
    }

    /**
     * Renders numeric cells deterministically. Dates become ISO-8601 instead of the cell's
     * display format, which is what makes "03/04/1977" unambiguous by the time the importer sees
     * it; every other number keeps its own format under {@link Locale#ROOT} (so a phone stored
     * as the number 9876543210 renders as {@code 9876543210}, not {@code 9.88E9}).
     */
    private static final class IsoDateFormatter extends DataFormatter {
        IsoDateFormatter() {
            super(Locale.ROOT);
        }

        @Override
        public String formatRawCellContents(double value, int formatIndex, String formatString, boolean use1904Windowing) {
            if (DateUtil.isADateFormat(formatIndex, formatString) && DateUtil.isValidExcelDate(value)) {
                LocalDateTime dt = DateUtil.getLocalDateTime(value, use1904Windowing);
                if (dt == null) return "";
                return dt.toLocalTime().equals(java.time.LocalTime.MIDNIGHT)
                        ? dt.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
                        : dt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
            return super.formatRawCellContents(value, formatIndex, formatString, use1904Windowing);
        }
    }

    /**
     * Collects one physical row at a time from the SAX stream and hands it to the assembler.
     * Only cells present in the XML are reported, so gaps are filled back in against the header
     * width by the assembler: a blank cell is an empty string, never a missing column.
     */
    private static final class SheetCollector implements XSSFSheetXMLHandler.SheetContentsHandler {
        private final RowAssembler assembler;
        private final Map<Integer, String> current = new HashMap<>();
        private int currentRow;

        SheetCollector(RowAssembler assembler) {
            this.assembler = assembler;
        }

        @Override
        public void startRow(int rowNum) {
            currentRow = rowNum + 1; // POI is 0-based; the administrator's Excel is 1-based
            current.clear();
        }

        @Override
        public void endRow(int rowNum) {
            int width = current.keySet().stream().max(Integer::compareTo).map(i -> i + 1).orElse(0);
            List<String> cells = new ArrayList<>(width);
            for (int c = 0; c < width; c++) cells.add(current.getOrDefault(c, ""));
            if (!assembler.headerSeen()) {
                if (isBlank(cells)) return; // leading blank rows are not a header
                assembler.headerRowNum = currentRow;
                assembler.header(cells);
                return;
            }
            if (!assembler.row(currentRow, cells)) throw new StopParsing();
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            if (cellReference == null) return;
            current.put(columnIndexOf(cellReference), formattedValue == null ? "" : formattedValue);
        }

        @Override
        public void headerFooter(String text, boolean isHeader, String tagName) {
            // Page headers and footers are presentation, not data.
        }
    }

    /** "AB12" to 27. Column letters are base-26 with no zero. */
    static int columnIndexOf(String cellReference) {
        int index = 0;
        for (int i = 0; i < cellReference.length(); i++) {
            char ch = Character.toUpperCase(cellReference.charAt(i));
            if (ch < 'A' || ch > 'Z') break;
            index = index * 26 + (ch - 'A' + 1);
        }
        return index - 1;
    }

    private static boolean isBlank(List<String> cells) {
        for (String c : cells) {
            if (c != null && !c.isBlank()) return false;
        }
        return true;
    }

    // ── shared header/row assembly ───────────────────────────────────────────

    /**
     * Format-independent half of the parser: validates the header, aligns every row to it,
     * applies the limits and delivers to the sink. Both readers feed it raw cell lists.
     */
    private final class RowAssembler {
        private final String sheetName;
        private final RowSink sink;
        private SheetHeader header;
        int headerRowNum;
        int lastRowNum;
        private int delivered;

        RowAssembler(String sheetName, RowSink sink) {
            this.sheetName = sheetName;
            this.sink = sink;
        }

        boolean headerSeen() {
            return header != null;
        }

        void header(List<String> rawCells) {
            List<String> cells = new ArrayList<>(rawCells.size());
            for (String c : rawCells) cells.add(c == null ? "" : c.strip());
            // Trailing blank header cells are Excel's habit (a formatted but empty column), not a
            // blank header name; a blank BETWEEN named headers is.
            while (!cells.isEmpty() && cells.get(cells.size() - 1).isEmpty()) cells.remove(cells.size() - 1);
            if (cells.isEmpty()) {
                throw new ImportParseException(ParseErrorCode.NO_HEADER_ROW, "The file has no header row.");
            }
            if (cells.size() > limits.maxColumns()) {
                throw new ImportParseException(
                        ParseErrorCode.TOO_MANY_COLUMNS,
                        "The file has " + cells.size() + " columns; at most " + limits.maxColumns() + " are supported.");
            }
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < cells.size(); i++) {
                String display = cells.get(i);
                String column = columnLetter(i);
                if (display.isEmpty()) {
                    throw new ImportParseException(
                            ParseErrorCode.BLANK_HEADER,
                            "Column " + column + " has a blank header. Every column needs a name.",
                            headerRowNum,
                            column);
                }
                if (display.length() > limits.maxCellChars()) {
                    throw new ImportParseException(
                            ParseErrorCode.HEADER_TOO_LONG,
                            "Column " + column + " has a header longer than " + limits.maxCellChars() + " characters.",
                            headerRowNum,
                            column);
                }
                if (!seen.add(SheetHeader.normalize(display))) {
                    throw new ImportParseException(
                            ParseErrorCode.DUPLICATE_HEADER,
                            "Column " + column + " (\"" + display + "\") repeats an earlier header. Rename or remove one of them.",
                            headerRowNum,
                            display);
                }
            }
            header = new SheetHeader(sheetName, cells);
            sink.header(header);
        }

        /** @return false when the sink asked to stop */
        boolean row(int rowNum, List<String> rawCells) {
            lastRowNum = rowNum;
            int width = header.columnCount();
            List<String> values = new ArrayList<>(width);
            List<RowProblem> problems = new ArrayList<>();
            boolean any = false;
            for (int i = 0; i < width; i++) {
                String v = i < rawCells.size() && rawCells.get(i) != null ? rawCells.get(i).strip() : "";
                if (v.length() > limits.maxCellChars()) {
                    problems.add(new RowProblem(RowProblem.Code.CELL_TOO_LONG, header.display().get(i), i));
                    v = "";
                }
                if (!v.isEmpty()) any = true;
                values.add(v);
            }
            for (int i = width; i < rawCells.size(); i++) {
                String v = rawCells.get(i);
                if (v != null && !v.isBlank()) {
                    problems.add(new RowProblem(RowProblem.Code.UNEXPECTED_CELL, columnLetter(i), i));
                    any = true;
                }
            }
            if (!any && problems.isEmpty()) return true; // fully blank row: not data, not counted

            if (delivered >= limits.maxRows()) {
                throw new ImportParseException(
                        ParseErrorCode.TOO_MANY_ROWS,
                        "The file has more than " + limits.maxRows() + " data rows. Split it and import the parts separately.",
                        rowNum,
                        null);
            }
            delivered++;
            return sink.row(new ParsedRow(rowNum, values, problems));
        }

        void finish() {
            if (header == null) {
                throw new ImportParseException(ParseErrorCode.NO_HEADER_ROW, "The file has no header row.");
            }
        }
    }

    /** 0 → "A", 26 → "AA". The administrator's Excel column letter, for messages. */
    static String columnLetter(int index) {
        StringBuilder sb = new StringBuilder();
        int i = index;
        do {
            sb.insert(0, (char) ('A' + (i % 26)));
            i = i / 26 - 1;
        } while (i >= 0);
        return sb.toString();
    }
}
