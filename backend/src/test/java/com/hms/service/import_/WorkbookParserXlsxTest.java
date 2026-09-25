package com.hms.service.import_;

import static com.hms.service.import_.ImportTestFiles.bytes;
import static com.hms.service.import_.ImportTestFiles.cell;
import static com.hms.service.import_.ImportTestFiles.xlsx;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * XLSX half of the parser. The interesting contracts: bytes that are not an OOXML package are
 * refused whatever the caller called them; formulas are never evaluated (the cached value is what
 * comes out, and a formula with no cached value is empty); dates leave as ISO-8601 regardless of
 * the cell's display format; numbers keep their own format so a numeric phone is not 9.88E9.
 */
class WorkbookParserXlsxTest {

    private final WorkbookParser parser = new WorkbookParser();

    private ImportTestFiles.Collecting parse(byte[] workbook) throws Exception {
        return parse(workbook, null);
    }

    private ImportTestFiles.Collecting parse(byte[] workbook, String sheet) throws Exception {
        ImportTestFiles.Collecting sink = new ImportTestFiles.Collecting();
        try (SpooledUpload upload = bytes(workbook)) {
            parser.parse(upload, ImportFormat.XLSX, sheet, sink);
        }
        return sink;
    }

    @Test
    void readsAPlainWorkbook() throws Exception {
        var sink = parse(xlsx(List.of(
                List.of("Name", "Phone", "Gender"),
                List.of("Test Person", "9000000001", "F"),
                Arrays.asList("Other Person", "9000000002", null))));

        assertThat(sink.header.sheetName()).isEqualTo("Sheet1");
        assertThat(sink.header.display()).containsExactly("Name", "Phone", "Gender");
        assertThat(sink.rows).hasSize(2);
        assertThat(sink.rows.get(0).rowNum()).isEqualTo(2);
        assertThat(sink.value(0, "name")).isEqualTo("Test Person");
        assertThat(sink.value(1, "Gender")).isEmpty(); // absent cell → empty string, not a missing column
        assertThat(sink.rows.get(1).values()).hasSize(3);
    }

    @Test
    void arbitraryBytesPretendingToBeXlsxAreRefusedAsNotAnXlsx() throws Exception {
        for (byte[] notXlsx : List.of(
                "Name,Phone\nTest,9000000001\n".getBytes(StandardCharsets.UTF_8),
                new byte[] {0x00, 0x01, 0x02, 0x03, 0x7f, (byte) 0xff},
                "<?xml version=\"1.0\"?><workbook/>".getBytes(StandardCharsets.UTF_8),
                // The OLE2 magic of a legacy .xls: also not an .xlsx.
                new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1, 0, 0})) {
            assertThatThrownBy(() -> parse(notXlsx))
                    .isInstanceOf(ImportParseException.class)
                    .satisfies(e -> assertThat(((ImportParseException) e).getCode()).isEqualTo(ParseErrorCode.NOT_AN_XLSX));
        }
    }

    @Test
    void aZipThatIsNotAWorkbookIsRefusedToo() throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(out)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("hello.txt"));
            zip.write("not a workbook".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        assertThatThrownBy(() -> parse(out.toByteArray()))
                .isInstanceOf(ImportParseException.class)
                .satisfies(e -> assertThat(((ImportParseException) e).getCode())
                        .isIn(ParseErrorCode.NOT_AN_XLSX, ParseErrorCode.UNREADABLE));
    }

    @Test
    void formulasAreNeverEvaluatedTheCachedValueIsWhatComesOut() throws Exception {
        byte[] wb = xlsx("Sheet1", w -> {
            Sheet s = w.createSheet("Sheet1");
            cell(s, 0, 0).setCellValue("Name");
            cell(s, 0, 1).setCellValue("Computed");
            cell(s, 0, 2).setCellValue("Uncached");
            cell(s, 1, 0).setCellValue("Test Person");
            // Formula says 1+3, but the cached value Excel last wrote is 2. Evaluating would give 4.
            XSSFCell computed = (XSSFCell) cell(s, 1, 1);
            computed.setCellFormula("1+3");
            computed.getCTCell().setV("2");
            // A formula with no cached value at all.
            ((XSSFCell) cell(s, 1, 2)).setCellFormula("HYPERLINK(\"http://evil.test\",\"x\")");
        });

        var sink = parse(wb);

        assertThat(sink.value(0, "Computed")).isEqualTo("2");
        assertThat(sink.value(0, "Uncached")).isEmpty();
    }

    @Test
    void dateCellsLeaveAsIsoDatesWhateverTheDisplayFormatWas() throws Exception {
        byte[] wb = xlsx("Sheet1", w -> {
            Sheet s = w.createSheet("Sheet1");
            cell(s, 0, 0).setCellValue("Name");
            cell(s, 0, 1).setCellValue("DOB");
            cell(s, 0, 2).setCellValue("Seen");
            cell(s, 0, 3).setCellValue("DOB text");
            cell(s, 1, 0).setCellValue("Test Person");

            CellStyle dmy = w.createCellStyle(); // the ambiguous one: 03/04/1977
            dmy.setDataFormat(w.createDataFormat().getFormat("dd/mm/yyyy"));
            cell(s, 1, 1).setCellValue(LocalDate.of(1977, 4, 3));
            cell(s, 1, 1).setCellStyle(dmy);

            CellStyle withTime = w.createCellStyle();
            withTime.setDataFormat(w.createDataFormat().getFormat("m/d/yy h:mm"));
            cell(s, 1, 2).setCellValue(LocalDateTime.of(2020, 2, 29, 10, 30));
            cell(s, 1, 2).setCellStyle(withTime);

            cell(s, 1, 3).setCellValue("03/04/1977"); // text stays text; interpretation is the importer's job
        });

        var sink = parse(wb);

        assertThat(sink.value(0, "DOB")).isEqualTo("1977-04-03");
        assertThat(sink.value(0, "Seen")).isEqualTo("2020-02-29T10:30:00");
        assertThat(sink.value(0, "DOB text")).isEqualTo("03/04/1977");
    }

    @Test
    void numericCellsKeepTheirOwnFormatSoANumericPhoneIsNotScientific() throws Exception {
        byte[] wb = xlsx("Sheet1", w -> {
            Sheet s = w.createSheet("Sheet1");
            cell(s, 0, 0).setCellValue("Phone");
            cell(s, 0, 1).setCellValue("Age");
            cell(s, 0, 2).setCellValue("Text phone");
            cell(s, 1, 0).setCellValue(9876543210d);
            cell(s, 1, 1).setCellValue(42d);
            cell(s, 1, 2).setCellValue("+91 98765-43210");
        });

        var sink = parse(wb);

        assertThat(sink.value(0, "Phone")).isEqualTo("9876543210");
        assertThat(sink.value(0, "Age")).isEqualTo("42");
        assertThat(sink.value(0, "Text phone")).isEqualTo("+91 98765-43210");
    }

    @Test
    void unicodeValuesSurviveIntact() throws Exception {
        var sink = parse(xlsx(List.of(List.of("Name", "City"), List.of("रोगी परीक्षण", "José Ñandú – Zürich"))));

        assertThat(sink.value(0, "Name")).isEqualTo("रोगी परीक्षण");
        assertThat(sink.value(0, "City")).isEqualTo("José Ñandú – Zürich");
    }

    @Test
    void headerRulesApplyToWorkbooksToo() throws Exception {
        assertThatThrownBy(() -> parse(xlsx(List.of(List.of("Name", "Phone", " phone")))))
                .isInstanceOf(ImportParseException.class)
                .satisfies(e -> assertThat(((ImportParseException) e).getCode()).isEqualTo(ParseErrorCode.DUPLICATE_HEADER));
        assertThatThrownBy(() -> parse(xlsx(List.of(Arrays.asList("Name", null, "Phone")))))
                .isInstanceOf(ImportParseException.class)
                .satisfies(e -> assertThat(((ImportParseException) e).getCode()).isEqualTo(ParseErrorCode.BLANK_HEADER));
        assertThatThrownBy(() -> parse(xlsx(List.of(List.of()))))
                .isInstanceOf(ImportParseException.class)
                .satisfies(e -> assertThat(((ImportParseException) e).getCode()).isEqualTo(ParseErrorCode.NO_HEADER_ROW));
    }

    @Test
    void aHeaderOnlySheetDeliversTheHeaderAndNoRows() throws Exception {
        var sink = parse(xlsx(List.of(List.of("Name", "Phone"))));

        assertThat(sink.header.display()).containsExactly("Name", "Phone");
        assertThat(sink.rows).isEmpty();
    }

    @Test
    void leadingBlankRowsAreSkippedAndTheHeaderKeepsItsPhysicalRowNumber() throws Exception {
        byte[] wb = xlsx("Sheet1", w -> {
            Sheet s = w.createSheet("Sheet1");
            cell(s, 2, 0).setCellValue("Name"); // header on physical row 3
            cell(s, 3, 0).setCellValue("Test Person");
            cell(s, 5, 0).setCellValue("Later Person"); // row 5 is blank
        });

        var sink = parse(wb);

        assertThat(sink.rows).extracting(ParsedRow::rowNum).containsExactly(4, 6);
    }

    @Test
    void selectsTheNamedSheetAndListsSheetNames() throws Exception {
        byte[] wb = xlsx("x", w -> {
            Sheet a = w.createSheet("Summary");
            cell(a, 0, 0).setCellValue("Title");
            Sheet b = w.createSheet("Patients");
            cell(b, 0, 0).setCellValue("Name");
            cell(b, 1, 0).setCellValue("Test Person");
        });

        try (SpooledUpload upload = bytes(wb)) {
            assertThat(parser.sheetNames(upload)).containsExactly("Summary", "Patients");
        }
        var sink = parse(wb, "Patients");
        assertThat(sink.header.sheetName()).isEqualTo("Patients");
        assertThat(sink.rows).hasSize(1);

        assertThatThrownBy(() -> parse(wb, "Nope"))
                .isInstanceOf(ImportParseException.class)
                .satisfies(e -> assertThat(((ImportParseException) e).getCode()).isEqualTo(ParseErrorCode.SHEET_NOT_FOUND));
    }

    @Test
    void theSinkCanStopEarlyMidSheet() throws Exception {
        ImportTestFiles.Collecting sink = new ImportTestFiles.Collecting();
        sink.stopAfter = 1;
        try (SpooledUpload upload = bytes(xlsx(List.of(List.of("Name"), List.of("A"), List.of("B"), List.of("C"))))) {
            parser.parse(upload, ImportFormat.XLSX, null, sink);
        }
        assertThat(sink.rows).hasSize(1);
    }

    @Test
    void poiZipBombProtectionIsLeftAtItsDefaults() {
        // The old implementation lowered this to 0.001 JVM-wide. POI's default is 0.01; the
        // parser must not have touched it (a static initialiser would have run by now).
        assertThat(new WorkbookParser()).isNotNull();
        assertThat(org.apache.poi.openxml4j.util.ZipSecureFile.getMinInflateRatio()).isEqualTo(0.01d);
        assertThat(org.apache.poi.openxml4j.util.ZipSecureFile.getMaxEntrySize()).isEqualTo(0xFFFFFFFFL);
    }

    @Test
    void workbookBuilderSanityCheck() throws Exception {
        // Guards the fixture itself: a full-model workbook round-trips through POI's own reader.
        byte[] wb = xlsx(List.of(List.of("Name"), List.of("x")));
        try (XSSFWorkbook read = new XSSFWorkbook(new java.io.ByteArrayInputStream(wb))) {
            assertThat(read.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).isEqualTo("x");
        }
    }
}
