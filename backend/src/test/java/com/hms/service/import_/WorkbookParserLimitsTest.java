package com.hms.service.import_;

import static com.hms.service.import_.ImportTestFiles.bigCsv;
import static com.hms.service.import_.ImportTestFiles.bigXlsx;
import static com.hms.service.import_.ImportTestFiles.bytes;
import static com.hms.service.import_.ImportTestFiles.columns;
import static com.hms.service.import_.ImportTestFiles.csv;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Resource limits. The production constants are asserted by value; the row boundary is proven
 * for real on CSV (100,000 rows parse in well under a second) and on XLSX through the injectable
 * limits, which exist only so this test need not write a 100,000-row workbook.
 */
class WorkbookParserLimitsTest {

    private final WorkbookParser parser = new WorkbookParser();

    private static ImportParseException parseError(Runnable r) {
        try {
            r.run();
        } catch (ImportParseException e) {
            return e;
        }
        throw new AssertionError("expected an ImportParseException");
    }

    @Test
    void productionLimitsAreTheApprovedOnes() {
        assertThat(ParserLimits.MAX_ROWS).isEqualTo(100_000);
        assertThat(ParserLimits.MAX_COLUMNS).isEqualTo(100);
        assertThat(ParserLimits.MAX_CELL_CHARS).isEqualTo(2_000);
        assertThat(new WorkbookParser().limits()).isEqualTo(ParserLimits.DEFAULT);
        assertThat(ParserLimits.DEFAULT).isEqualTo(new ParserLimits(100_000, 100, 2_000));
    }

    @Test
    void exactlyOneHundredColumnsAreAccepted() throws Exception {
        List<String> header = columns("Col", 100);
        ImportTestFiles.Collecting sink = new ImportTestFiles.Collecting();
        try (SpooledUpload upload = csv(bigCsv(header, columns("v", 100), 1))) {
            parser.parse(upload, ImportFormat.CSV, null, sink);
        }
        assertThat(sink.header.columnCount()).isEqualTo(100);
        assertThat(sink.rows.get(0).values()).hasSize(100);
    }

    @Test
    void oneHundredAndOneColumnsAreRefused() throws Exception {
        try (SpooledUpload upload = csv(bigCsv(columns("Col", 101), columns("v", 101), 1))) {
            ImportParseException e = parseError(
                    () -> parser.parse(upload, ImportFormat.CSV, null, new ImportTestFiles.Collecting()));
            assertThat(e.getCode()).isEqualTo(ParseErrorCode.TOO_MANY_COLUMNS);
        }
        try (SpooledUpload upload = bytes(bigXlsx(columns("Col", 101), columns("v", 101), 1))) {
            ImportParseException e = parseError(
                    () -> parser.parse(upload, ImportFormat.XLSX, null, new ImportTestFiles.Collecting()));
            assertThat(e.getCode()).isEqualTo(ParseErrorCode.TOO_MANY_COLUMNS);
        }
    }

    @Test
    void exactlyOneHundredThousandCsvRowsAreAccepted() throws Exception {
        int[] count = {0};
        RowSink counting = new RowSink() {
            @Override
            public void header(SheetHeader header) {}

            @Override
            public boolean row(ParsedRow row) {
                count[0]++;
                return true;
            }
        };
        try (SpooledUpload upload = csv(bigCsv(List.of("Name", "Phone"), List.of("Test", "9000000001"), 100_000))) {
            parser.parse(upload, ImportFormat.CSV, null, counting);
        }
        assertThat(count[0]).isEqualTo(100_000);
    }

    @Test
    void oneHundredThousandAndOneCsvRowsAreRefusedDeterministically() throws Exception {
        try (SpooledUpload upload = csv(bigCsv(List.of("Name", "Phone"), List.of("Test", "9000000001"), 100_001))) {
            ImportParseException e = parseError(
                    () -> parser.parse(upload, ImportFormat.CSV, null, new ImportTestFiles.Collecting()));
            assertThat(e.getCode()).isEqualTo(ParseErrorCode.TOO_MANY_ROWS);
            assertThat(e.getRowNum()).isEqualTo(100_002); // header is row 1; the 100,001st data row is record 100,002
        }
    }

    @Test
    void theXlsxRowBoundaryHoldsUnderInjectedLimitsWithoutTouchingTheProductionConstant() throws Exception {
        WorkbookParser small = new WorkbookParser(new ParserLimits(5, 100, 2_000));
        byte[] five = bigXlsx(List.of("Name"), List.of("Test"), 5);
        byte[] six = bigXlsx(List.of("Name"), List.of("Test"), 6);

        ImportTestFiles.Collecting ok = new ImportTestFiles.Collecting();
        try (SpooledUpload upload = bytes(five)) {
            small.parse(upload, ImportFormat.XLSX, null, ok);
        }
        assertThat(ok.rows).hasSize(5);

        try (SpooledUpload upload = bytes(six)) {
            ImportParseException e = parseError(
                    () -> small.parse(upload, ImportFormat.XLSX, null, new ImportTestFiles.Collecting()));
            assertThat(e.getCode()).isEqualTo(ParseErrorCode.TOO_MANY_ROWS);
            assertThat(e.getRowNum()).isEqualTo(7);
        }
        assertThat(ParserLimits.DEFAULT.maxRows()).isEqualTo(100_000);
    }

    @Test
    void blankRowsDoNotCountTowardsTheRowLimit() throws Exception {
        WorkbookParser small = new WorkbookParser(new ParserLimits(2, 100, 2_000));
        ImportTestFiles.Collecting sink = new ImportTestFiles.Collecting();
        try (SpooledUpload upload = csv("Name\nA\n\n,\nB\n")) {
            small.parse(upload, ImportFormat.CSV, null, sink);
        }
        assertThat(sink.rows).hasSize(2);
    }

    @Test
    void aCellOverTwoThousandCharactersIsDroppedAndTheRowFlaggedNeverTruncated() throws Exception {
        String exactly = "x".repeat(2_000);
        String over = "y".repeat(2_001);
        ImportTestFiles.Collecting sink = new ImportTestFiles.Collecting();
        try (SpooledUpload upload = csv("Name,Note,Phone\nOK," + exactly + ",9000000001\nBad," + over + ",9000000002\n")) {
            parser.parse(upload, ImportFormat.CSV, null, sink);
        }

        assertThat(sink.rows).hasSize(2);
        assertThat(sink.value(0, "Note")).hasSize(2_000);
        assertThat(sink.rows.get(0).hasProblems()).isFalse();

        ParsedRow bad = sink.rows.get(1);
        assertThat(bad.hasProblems()).isTrue();
        assertThat(bad.problems()).extracting(RowProblem::code).containsExactly(RowProblem.Code.CELL_TOO_LONG);
        assertThat(bad.problems().get(0).column()).isEqualTo("Note");
        assertThat(bad.get(sink.header, "Note")).isEmpty(); // dropped, not cut to 2,000
        assertThat(bad.get(sink.header, "Phone")).isEqualTo("9000000002"); // the rest of the row is intact
    }

    @Test
    void theSameCellRuleAppliesToXlsx() throws Exception {
        ImportTestFiles.Collecting sink = new ImportTestFiles.Collecting();
        try (SpooledUpload upload = bytes(ImportTestFiles.xlsx(List.of(List.of("Name", "Note"), List.of("Bad", "z".repeat(2_001)))))) {
            parser.parse(upload, ImportFormat.XLSX, null, sink);
        }
        assertThat(sink.rows.get(0).problems()).extracting(RowProblem::code).containsExactly(RowProblem.Code.CELL_TOO_LONG);
        assertThat(sink.value(0, "Note")).isEmpty();
    }

    @Test
    void aHeaderOverTheCellLimitIsAFileLevelRefusal() throws Exception {
        try (SpooledUpload upload = csv("Name," + "h".repeat(2_001) + "\nA,b\n")) {
            ImportParseException e = parseError(
                    () -> parser.parse(upload, ImportFormat.CSV, null, new ImportTestFiles.Collecting()));
            assertThat(e.getCode()).isEqualTo(ParseErrorCode.HEADER_TOO_LONG);
        }
    }
}
