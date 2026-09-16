package com.hms.service.import_;

import static com.hms.service.import_.ImportTestFiles.csv;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CSV half of the parser. Every value below is synthetic. The contract under test: RFC 4180
 * quoting is honoured, malformed quoting is refused rather than guessed, the BOM is not a header
 * character, and values reach the sink raw — the parser has no opinion about phones or dates.
 */
class WorkbookParserCsvTest {

    private final WorkbookParser parser = new WorkbookParser();

    private ImportTestFiles.Collecting parse(String text) throws Exception {
        ImportTestFiles.Collecting sink = new ImportTestFiles.Collecting();
        try (SpooledUpload upload = csv(text)) {
            parser.parse(upload, ImportFormat.CSV, null, sink);
        }
        return sink;
    }

    @Test
    void readsAPlainCsvWithHeaderAndRows() throws Exception {
        var sink = parse("Name,Phone,DOB\nTest Person,9000000001,1990-01-01\nOther Person,9000000002,\n");

        assertThat(sink.header.sheetName()).isEqualTo(WorkbookParser.CSV_SHEET_NAME);
        assertThat(sink.header.display()).containsExactly("Name", "Phone", "DOB");
        assertThat(sink.rows).hasSize(2);
        assertThat(sink.rows.get(0).rowNum()).isEqualTo(2);
        assertThat(sink.value(0, "name")).isEqualTo("Test Person");
        assertThat(sink.value(1, "DOB")).isEmpty(); // blank stays blank, never null, never absent
        assertThat(sink.rows.get(1).values()).hasSize(3);
    }

    @Test
    void stripsAUtf8ByteOrderMarkSoTheFirstHeaderIsClean() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(ImportTestFiles.BOM);
        bytes.write("Name,Phone\nTest Person,9000000001\n".getBytes(StandardCharsets.UTF_8));
        ImportTestFiles.Collecting sink = new ImportTestFiles.Collecting();
        try (SpooledUpload upload = SpooledUpload.spool(new ByteArrayInputStream(bytes.toByteArray()))) {
            parser.parse(upload, ImportFormat.CSV, null, sink);
        }

        assertThat(sink.header.display().get(0)).isEqualTo("Name");
        assertThat(sink.header.indexOf("name")).isZero();
    }

    @Test
    void keepsACommaInsideAQuotedField() throws Exception {
        var sink = parse("Name,Address\n\"Person, Test\",\"12, Some Road, Town\"\n");

        assertThat(sink.value(0, "Name")).isEqualTo("Person, Test");
        assertThat(sink.value(0, "Address")).isEqualTo("12, Some Road, Town");
    }

    @Test
    void keepsAQuotedMultiLineFieldAsOneRowAndCountsItAsOneRecord() throws Exception {
        var sink = parse("Name,Address,Phone\nTest Person,\"Line one\nLine two\",9000000001\nNext,Addr,9000000002\n");

        assertThat(sink.rows).hasSize(2);
        assertThat(sink.value(0, "Address")).isEqualTo("Line one\nLine two");
        assertThat(sink.value(0, "Phone")).isEqualTo("9000000001");
        assertThat(sink.rows.get(1).rowNum()).isEqualTo(3); // record number, not physical line
    }

    @Test
    void acceptsCrlfLineEndingsAndDoubledQuotes() throws Exception {
        var sink = parse("Name,Note\r\n\"Test \"\"Nick\"\" Person\",plain\r\n");

        assertThat(sink.value(0, "Name")).isEqualTo("Test \"Nick\" Person");
    }

    @Test
    void refusesMalformedQuotingInsteadOfGuessing() {
        // Text after a closing quote, and an unterminated quote: neither has one honest reading.
        assertThatThrownBy(() -> parse("Name,Phone\n\"Test\"Person,9000000001\n"))
                .isInstanceOf(ImportParseException.class)
                .satisfies(e -> assertThat(((ImportParseException) e).getCode()).isEqualTo(ParseErrorCode.MALFORMED_CSV));
        assertThatThrownBy(() -> parse("Name,Phone\n\"Test Person,9000000001\n"))
                .isInstanceOf(ImportParseException.class)
                .satisfies(e -> assertThat(((ImportParseException) e).getCode()).isEqualTo(ParseErrorCode.MALFORMED_CSV));
    }

    @Test
    void refusesAnEmptyFile() {
        assertThatThrownBy(() -> parse(""))
                .isInstanceOf(ImportParseException.class)
                .satisfies(e -> assertThat(((ImportParseException) e).getCode()).isEqualTo(ParseErrorCode.EMPTY_FILE));
    }

    @Test
    void aWhitespaceOnlyFileHasNoHeaderRow() {
        assertThatThrownBy(() -> parse("\n\n  \n"))
                .isInstanceOf(ImportParseException.class)
                .satisfies(e -> assertThat(((ImportParseException) e).getCode()).isEqualTo(ParseErrorCode.NO_HEADER_ROW));
    }

    @Test
    void aHeaderOnlyFileDeliversTheHeaderAndNoRows() throws Exception {
        var sink = parse("Name,Phone\n");

        assertThat(sink.header.display()).containsExactly("Name", "Phone");
        assertThat(sink.rows).isEmpty();
    }

    @Test
    void refusesABlankHeaderBetweenNamedOnesButTrimsTrailingBlankHeaders() throws Exception {
        assertThatThrownBy(() -> parse("Name,,Phone\nTest,x,9000000001\n"))
                .isInstanceOf(ImportParseException.class)
                .satisfies(e -> {
                    ImportParseException ipe = (ImportParseException) e;
                    assertThat(ipe.getCode()).isEqualTo(ParseErrorCode.BLANK_HEADER);
                    assertThat(ipe.getColumn()).isEqualTo("B");
                    assertThat(ipe.getRowNum()).isEqualTo(1);
                });

        var sink = parse("Name,Phone,,\nTest,9000000001,,\n");
        assertThat(sink.header.columnCount()).isEqualTo(2);
    }

    @Test
    void refusesAnExactDuplicateHeader() {
        assertThatThrownBy(() -> parse("Name,Phone,Phone\n"))
                .isInstanceOf(ImportParseException.class)
                .satisfies(e -> {
                    ImportParseException ipe = (ImportParseException) e;
                    assertThat(ipe.getCode()).isEqualTo(ParseErrorCode.DUPLICATE_HEADER);
                    assertThat(ipe.getColumn()).isEqualTo("Phone");
                });
    }

    @Test
    void refusesAHeaderThatDuplicatesAnotherAfterNormalisation() {
        // "Phone" and " phone " name one column; the second must not silently overwrite the first.
        assertThatThrownBy(() -> parse("Phone,Name, phone \n"))
                .isInstanceOf(ImportParseException.class)
                .satisfies(e -> assertThat(((ImportParseException) e).getCode()).isEqualTo(ParseErrorCode.DUPLICATE_HEADER));
        assertThatThrownBy(() -> parse("Name,PHONE,Phone\n"))
                .isInstanceOf(ImportParseException.class)
                .satisfies(e -> assertThat(((ImportParseException) e).getCode()).isEqualTo(ParseErrorCode.DUPLICATE_HEADER));
    }

    @Test
    void preservesTheDisplayHeaderWhileMatchingOnTheNormalisedOne() throws Exception {
        var sink = parse("  Patient   Name ,Mobile\nTest,9000000001\n");

        assertThat(sink.header.display().get(0)).isEqualTo("Patient   Name");
        assertThat(sink.header.normalized().get(0)).isEqualTo("patient name");
        assertThat(sink.header.indexOf("PATIENT NAME")).isZero();
    }

    @Test
    void unicodeValuesSurviveIntact() throws Exception {
        var sink = parse("Name,City\nरोगी परीक्षण,José Ñandú – Zürich\n");

        assertThat(sink.value(0, "Name")).isEqualTo("रोगी परीक्षण");
        assertThat(sink.value(0, "City")).isEqualTo("José Ñandú – Zürich");
    }

    @Test
    void doesNotTouchPhoneFormattingOrLeadingFormulaCharacters() throws Exception {
        var sink = parse("Name,Phone,Note\nTest,\"+91 98765-43210\",=SUM(A1)\nTest2,09876543210,+x\nTest3,98765 43210,-y\nTest4,'9876543210,@z\n");

        assertThat(sink.value(0, "Phone")).isEqualTo("+91 98765-43210");
        assertThat(sink.value(1, "Phone")).isEqualTo("09876543210");
        assertThat(sink.value(2, "Phone")).isEqualTo("98765 43210");
        assertThat(sink.value(3, "Phone")).isEqualTo("'9876543210");
        assertThat(sink.value(0, "Note")).isEqualTo("=SUM(A1)");
        assertThat(sink.value(1, "Note")).isEqualTo("+x");
        assertThat(sink.value(2, "Note")).isEqualTo("-y");
        assertThat(sink.value(3, "Note")).isEqualTo("@z");
    }

    @Test
    void skipsFullyBlankRowsWithoutCountingThem() throws Exception {
        var sink = parse("Name,Phone\n\n,\nTest,9000000001\n   ,  \n");

        assertThat(sink.rows).hasSize(1);
        assertThat(sink.rows.get(0).rowNum()).isEqualTo(3); // physical record 3; the blank one keeps its number
    }

    @Test
    void aValueBeyondTheHeaderWidthFlagsTheRowInsteadOfVanishing() throws Exception {
        var sink = parse("Name,Phone\nTest,9000000001,stray\n");

        assertThat(sink.rows).hasSize(1);
        assertThat(sink.rows.get(0).problems())
                .extracting(RowProblem::code, RowProblem::column)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(RowProblem.Code.UNEXPECTED_CELL, "C"));
    }

    @Test
    void theSinkCanStopEarly() throws Exception {
        ImportTestFiles.Collecting sink = new ImportTestFiles.Collecting();
        sink.stopAfter = 2;
        try (SpooledUpload upload = csv("Name\nA\nB\nC\nD\n")) {
            parser.parse(upload, ImportFormat.CSV, null, sink);
        }
        assertThat(sink.rows).extracting(r -> r.values().get(0)).containsExactly("A", "B");
    }

    @Test
    void parseExceptionsNeverCarryCellContent() {
        assertThatThrownBy(() -> parse("Name,Phone\n\"SECRET-CELL-VALUE,9000000001\n"))
                .hasMessageNotContaining("SECRET-CELL-VALUE");
    }

    @Test
    void unknownFormatIsRejectedUpFrontNotByTheReader() throws Exception {
        try (SpooledUpload upload = csv("Name\nA\n")) {
            assertThat(upload.size()).isGreaterThan(0);
            assertThat(List.of(ImportFormat.values())).containsExactly(ImportFormat.CSV, ImportFormat.XLSX);
        }
    }
}
