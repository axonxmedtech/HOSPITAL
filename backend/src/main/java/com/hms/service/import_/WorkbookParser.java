package com.hms.service.import_;

import com.hms.dto.import_.ParsedSheet;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
 * <p>Note: .xlsx parsing uses {@link XSSFWorkbook}, POI's DOM-based reader, which loads the whole
 * workbook into heap rather than streaming it via POI's SAX/event API. That is fine for the
 * spreadsheet sizes legacy patient exports come in, but it is not true streaming, and a very
 * large upload (tens of MB+) will hold the entire parsed workbook in memory at once.
 */
@Component
public class WorkbookParser {

    /** Guards against zip bombs: an .xlsx is a zip of XML and is attacker-controlled input. */
    static {
        ZipSecureFile.setMinInflateRatio(0.001);
    }

    private static final int MAX_ROWS = 100_000;

    public List<String> sheetNames(InputStream in) {
        try (Workbook wb = new XSSFWorkbook(in)) {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                names.add(wb.getSheetName(i));
            }
            return names;
        } catch (IOException | RuntimeException e) {
            if (e instanceof IllegalArgumentException iae) throw iae;
            throw new IllegalArgumentException("Could not read the workbook: " + e.getMessage());
        }
    }

    public ParsedSheet parseXlsx(InputStream in, String sheetName) {
        try (Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = sheetName == null ? wb.getSheetAt(0) : wb.getSheet(sheetName);
            if (sheet == null) {
                throw new IllegalArgumentException("Sheet not found: " + sheetName);
            }
            DataFormatter fmt = new DataFormatter();
            Iterator<Row> it = sheet.iterator();
            if (!it.hasNext()) {
                throw new IllegalArgumentException("The sheet has no header row.");
            }

            List<String> headers = new ArrayList<>();
            Row headerRow = it.next();
            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                headers.add(fmt.formatCellValue(headerRow.getCell(c)).trim());
            }
            if (headers.stream().allMatch(String::isEmpty)) {
                throw new IllegalArgumentException("The sheet has no header row.");
            }

            List<Map<String, String>> rows = new ArrayList<>();
            while (it.hasNext()) {
                if (rows.size() >= MAX_ROWS) {
                    // Silently dropping the remainder would make every reported count agree with
                    // itself while the file was only partly imported - the admin would have no way
                    // to learn rows were lost. Refuse instead.
                    throw new IllegalArgumentException(
                            "This sheet has more than " + MAX_ROWS + " rows. Importing it would "
                            + "silently drop the rest, so it is refused. Split it and import the "
                            + "parts separately.");
                }
                Row row = it.next();
                Map<String, String> values = new LinkedHashMap<>();
                boolean anyValue = false;
                for (int c = 0; c < headers.size(); c++) {
                    String header = headers.get(c);
                    if (header.isEmpty()) continue;
                    String v = unescapeFormulaGuard(fmt.formatCellValue(row.getCell(c)).trim());
                    values.put(header, v);
                    if (!v.isEmpty()) anyValue = true;
                }
                if (anyValue) rows.add(values);
            }
            return new ParsedSheet(sheet.getSheetName(), headers, rows);
        } catch (IOException | RuntimeException e) {
            // POI raises unchecked NotOfficeXmlFileException / POIXMLException for a renamed .xls
            // or a file that trips the zip-bomb guard. Uncaught those surface as a 500; the admin
            // needs to be told their file is unreadable, not shown a server error.
            if (e instanceof IllegalArgumentException iae) throw iae;
            throw new IllegalArgumentException("Could not read the workbook: " + e.getMessage());
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
