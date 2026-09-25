package com.hms.service.import_;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Builds synthetic .csv / .xlsx uploads for the parser tests. No real patient data anywhere. */
final class ImportTestFiles {

    private ImportTestFiles() {}

    static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    static SpooledUpload csv(String text) throws IOException {
        return SpooledUpload.spool(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }

    static SpooledUpload csv(Path dir, String text) throws IOException {
        return SpooledUpload.spool(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), dir);
    }

    static SpooledUpload bytes(byte[] bytes) throws IOException {
        return SpooledUpload.spool(new ByteArrayInputStream(bytes));
    }

    static SpooledUpload bytes(Path dir, byte[] bytes) throws IOException {
        return SpooledUpload.spool(new ByteArrayInputStream(bytes), dir);
    }

    /** A one-sheet workbook of string cells: first row is the header. */
    static byte[] xlsx(List<List<String>> rows) throws IOException {
        return xlsx("Sheet1", wb -> {
            Sheet sheet = wb.createSheet("Sheet1");
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r);
                List<String> cells = rows.get(r);
                for (int c = 0; c < cells.size(); c++) {
                    if (cells.get(c) != null) row.createCell(c).setCellValue(cells.get(c));
                }
            }
        });
    }

    /** Full-model workbook, for tests that need styles, formulas or dates. */
    static byte[] xlsx(String ignoredSheetName, Consumer<XSSFWorkbook> build) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            build.accept(wb);
            wb.write(out);
            return out.toByteArray();
        }
    }

    /** Streaming-written workbook with a header and {@code dataRows} identical data rows; for volume tests. */
    static byte[] bigXlsx(List<String> header, List<String> dataRow, int dataRows) throws IOException {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(100);
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Data");
            Row h = sheet.createRow(0);
            for (int c = 0; c < header.size(); c++) h.createCell(c).setCellValue(header.get(c));
            for (int r = 1; r <= dataRows; r++) {
                Row row = sheet.createRow(r);
                for (int c = 0; c < dataRow.size(); c++) row.createCell(c).setCellValue(dataRow.get(c));
            }
            wb.write(out);
            wb.dispose();
            return out.toByteArray();
        }
    }

    static String bigCsv(List<String> header, List<String> dataRow, int dataRows) {
        StringBuilder sb = new StringBuilder(dataRows * 32);
        sb.append(String.join(",", header)).append('\n');
        String line = String.join(",", dataRow) + "\n";
        for (int i = 0; i < dataRows; i++) sb.append(line);
        return sb.toString();
    }

    static List<String> columns(String prefix, int n) {
        List<String> out = new ArrayList<>(n);
        for (int i = 1; i <= n; i++) out.add(prefix + i);
        return out;
    }

    static Cell cell(Sheet sheet, int r, int c) {
        Row row = sheet.getRow(r) == null ? sheet.createRow(r) : sheet.getRow(r);
        return row.getCell(c) == null ? row.createCell(c) : row.getCell(c);
    }

    /** Everything a parse delivered, for assertions. Production sinks never buffer like this. */
    static final class Collecting implements RowSink {
        SheetHeader header;
        final List<ParsedRow> rows = new ArrayList<>();
        int stopAfter = Integer.MAX_VALUE;

        @Override
        public void header(SheetHeader header) {
            this.header = header;
        }

        @Override
        public boolean row(ParsedRow row) {
            rows.add(row);
            return rows.size() < stopAfter;
        }

        String value(int rowIndex, String column) {
            return rows.get(rowIndex).get(header, column);
        }
    }
}
