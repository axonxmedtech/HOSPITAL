package com.hms.service.import_;

import com.hms.dto.import_.ParsedSheet;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Reads .xlsx and .csv into a ParsedSheet. Values are returned as trimmed strings; interpreting
 * them (dates, numbers) is each EntityImporter's job, because only it knows what a column means.
 *
 * <p><b>.xlsx is read with POI's streaming event API</b> ({@code XSSFReader} + SAX), not the
 * DOM-based {@code XSSFWorkbook}. A workbook is a zip of XML that expands many times over on disk,
 * so the DOM reader holds the whole expanded document in heap — a 200 MB upload can cost several
 * gigabytes and take the JVM down with it, which on a single-VPS deployment means the whole
 * hospital system, not just the import. The event reader keeps one row in memory at a time.
 *
 * <p>The upload is spooled to a temp file first because the package reader needs random access
 * into the zip; feeding it an InputStream buffers the entire thing in memory and undoes the point.
 * That file is patient data, so it is created with owner-only permissions where the platform
 * supports it and deleted in a finally block.
 *
 * <p>What bounds memory now is {@link #MAX_ROWS}, not the file size: parsed rows are accumulated
 * into a list, so a very wide sheet with many rows is still the thing to watch.
 */
@Component
public class WorkbookParser {

    /** Guards against zip bombs: an .xlsx is a zip of XML and is attacker-controlled input. */
    static {
        ZipSecureFile.setMinInflateRatio(0.001);
    }

    private static final int MAX_ROWS = 100_000;

    public List<String> sheetNames(InputStream in) {
        java.io.File spooled = null;
        try {
            spooled = spool(in);
            try (org.apache.poi.openxml4j.opc.OPCPackage pkg =
                         org.apache.poi.openxml4j.opc.OPCPackage.open(spooled)) {
                org.apache.poi.xssf.eventusermodel.XSSFReader reader =
                        new org.apache.poi.xssf.eventusermodel.XSSFReader(pkg);
                List<String> names = new ArrayList<>();
                org.apache.poi.xssf.eventusermodel.XSSFReader.SheetIterator it =
                        (org.apache.poi.xssf.eventusermodel.XSSFReader.SheetIterator) reader.getSheetsData();
                while (it.hasNext()) {
                    try (InputStream ignored = it.next()) {
                        names.add(it.getSheetName());
                    }
                }
                return names;
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof IllegalArgumentException iae) throw iae;
            throw new IllegalArgumentException("Could not read the workbook: " + e.getMessage());
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not read the workbook: " + e.getMessage());
        } finally {
            delete(spooled);
        }
    }

    public ParsedSheet parseXlsx(InputStream in, String sheetName) {
        java.io.File spooled = null;
        try {
            spooled = spool(in);
            try (org.apache.poi.openxml4j.opc.OPCPackage pkg =
                         org.apache.poi.openxml4j.opc.OPCPackage.open(spooled)) {

                org.apache.poi.xssf.eventusermodel.XSSFReader reader =
                        new org.apache.poi.xssf.eventusermodel.XSSFReader(pkg);
                org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable strings =
                        new org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable(pkg);
                org.apache.poi.xssf.model.StylesTable styles = reader.getStylesTable();

                org.apache.poi.xssf.eventusermodel.XSSFReader.SheetIterator it =
                        (org.apache.poi.xssf.eventusermodel.XSSFReader.SheetIterator) reader.getSheetsData();

                while (it.hasNext()) {
                    try (InputStream sheetStream = it.next()) {
                        String currentName = it.getSheetName();
                        if (sheetName != null && !sheetName.equals(currentName)) {
                            continue;
                        }
                        RowCollector collector = new RowCollector();
                        org.xml.sax.XMLReader xml = org.apache.poi.util.XMLHelper.newXMLReader();
                        xml.setContentHandler(new org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler(
                                styles, strings, collector, new DataFormatter(), false));
                        xml.parse(new org.xml.sax.InputSource(sheetStream));

                        if (collector.headers.isEmpty()
                                || collector.headers.stream().allMatch(String::isEmpty)) {
                            throw new IllegalArgumentException("The sheet has no header row.");
                        }
                        return new ParsedSheet(currentName, collector.headers, collector.rows);
                    }
                }
                throw new IllegalArgumentException(sheetName == null
                        ? "The workbook has no sheets."
                        : "Sheet not found: " + sheetName);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            // POI raises unchecked NotOfficeXmlFileException / POIXMLException for a renamed .xls
            // or a file that trips the zip-bomb guard. Uncaught those surface as a 500; the admin
            // needs to be told their file is unreadable, not shown a server error.
            throw new IllegalArgumentException("Could not read the workbook: " + e.getMessage());
        } finally {
            delete(spooled);
        }
    }

    /**
     * Accumulates one sheet, row by row, as the event reader walks it.
     *
     * <p>The handler only reports cells that exist in the XML — a blank cell is simply absent. The
     * whole feature rests on "blank stays blank", so gaps are filled back in against the header row
     * rather than left as missing keys; otherwise downstream code could not tell "the hospital left
     * gender empty" from "this file has no gender column".
     */
    private final class RowCollector
            implements org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler {

        private final List<String> headers = new ArrayList<>();
        private final List<Map<String, String>> rows = new ArrayList<>();
        private Map<Integer, String> current = new LinkedHashMap<>();

        @Override
        public void startRow(int rowNum) {
            this.current = new LinkedHashMap<>();
        }

        @Override
        public void endRow(int rowNum) {
            if (rowNum == 0) {
                int width = current.keySet().stream().max(Integer::compareTo).orElse(-1);
                for (int c = 0; c <= width; c++) {
                    headers.add(current.getOrDefault(c, "").trim());
                }
                return;
            }
            if (headers.isEmpty()) {
                return; // data before a header row is not something we can map
            }
            if (rows.size() >= MAX_ROWS) {
                // Silently dropping the remainder would make every reported count agree with
                // itself while the file was only partly imported - the admin would have no way to
                // learn rows were lost. Refuse instead.
                throw new IllegalArgumentException(
                        "This sheet has more than " + MAX_ROWS + " rows. Importing it would "
                        + "silently drop the rest, so it is refused. Split it and import the "
                        + "parts separately.");
            }

            Map<String, String> values = new LinkedHashMap<>();
            boolean anyValue = false;
            for (int c = 0; c < headers.size(); c++) {
                String header = headers.get(c);
                if (header.isEmpty()) continue;
                String v = unescapeFormulaGuard(current.getOrDefault(c, "").trim());
                values.put(header, v);
                if (!v.isEmpty()) anyValue = true;
            }
            if (anyValue) rows.add(values);
        }

        @Override
        public void cell(String cellReference, String formattedValue,
                         org.apache.poi.xssf.usermodel.XSSFComment comment) {
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

    /**
     * Writes the upload to a temp file so the package reader can seek within the zip.
     *
     * <p>Handing POI an InputStream makes it buffer the entire workbook in memory, which is exactly
     * the cost this class exists to avoid. The spooled file holds patient data, so permissions are
     * narrowed to the owner where the filesystem supports it.
     */
    private java.io.File spool(InputStream in) throws IOException {
        java.io.File file = java.io.File.createTempFile("hms-import-", ".xlsx");
        try {
            file.setReadable(false, false);
            file.setReadable(true, true);
            file.setWritable(false, false);
            file.setWritable(true, true);
        } catch (SecurityException ignored) {
            // Best effort. A platform that refuses this still gets the file deleted below.
        }
        try (java.io.OutputStream out = new java.io.FileOutputStream(file)) {
            in.transferTo(out);
        }
        return file;
    }

    /** The spooled copy is patient data; it must not outlive the parse. */
    private void delete(java.io.File file) {
        if (file != null && file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }

    public ParsedSheet parseCsv(InputStream in) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                throw new IllegalArgumentException("The file has no header row.");
            }
            if (headerLine.startsWith("﻿")) {
                headerLine = headerLine.substring(1);
            }
            List<String> headers = splitCsv(headerLine);

            List<Map<String, String>> rows = new ArrayList<>();
            String line;
            boolean truncated = false;
            while ((line = reader.readLine()) != null) {
                if (rows.size() >= MAX_ROWS) {
                    truncated = true;
                    break;
                }
                if (line.isBlank()) continue;

                // A quoted field may contain newlines — multi-line addresses are common in legacy
                // exports. Reading line-by-line split those across two records: the real patient
                // lost its trailing columns and the continuation became a junk patient. Keep
                // pulling lines until the quotes balance.
                while (hasUnbalancedQuotes(line)) {
                    String continuation = reader.readLine();
                    if (continuation == null) break;
                    line = line + "\n" + continuation;
                }

                List<String> cells = splitCsv(line);
                Map<String, String> values = new LinkedHashMap<>();
                boolean anyValue = false;
                for (int c = 0; c < headers.size(); c++) {
                    String header = headers.get(c);
                    if (header.isEmpty()) continue;
                    String v = c < cells.size() ? unescapeFormulaGuard(cells.get(c)) : "";
                    values.put(header, v);
                    if (!v.isEmpty()) anyValue = true;
                }
                if (anyValue) rows.add(values);
            }
            if (truncated) {
                throw new IllegalArgumentException(
                        "This file has more than " + MAX_ROWS + " rows. Importing it would silently "
                        + "drop the rest, so it is refused. Split it and import the parts separately.");
            }
            return new ParsedSheet("csv", headers, rows);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the file: " + e.getMessage());
        }
    }

    /**
     * Removes the apostrophe our own error CSV adds in front of a value starting =, +, - or @.
     *
     * <p>That prefix stops the value executing as a formula when the file is opened in Excel, but
     * the error CSV is explicitly meant to be corrected and re-uploaded — so without this the guard
     * corrupted the very path it exists to support: a phone exported as +919812345678 came back as
     * '+919812345678 and was written into the patient verbatim.
     *
     * <p>Only stripped when the apostrophe is immediately followed by one of those four characters,
     * which is exactly the escape we emit, so a genuine leading apostrophe in a name survives.
     */
    private String unescapeFormulaGuard(String value) {
        if (value != null && value.length() > 1 && value.startsWith("'")
                && "=+-@".indexOf(value.charAt(1)) >= 0) {
            return value.substring(1);
        }
        return value;
    }

    /**
     * True when a line ends mid-quoted-field, meaning the record continues on the next line.
     * Doubled quotes inside a quoted field are escapes, and toggling on each quote handles them
     * correctly: a pair flips the state twice and leaves it unchanged.
     */
    private boolean hasUnbalancedQuotes(String line) {
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '"') inQuotes = !inQuotes;
        }
        return inQuotes;
    }

    /** Minimal RFC4180 splitter: handles quoted fields and doubled quotes. */
    private List<String> splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(ch);
                }
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == ',') {
                out.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString().trim());
        return out;
    }
}
