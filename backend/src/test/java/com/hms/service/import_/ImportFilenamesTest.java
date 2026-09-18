package com.hms.service.import_;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ImportFilenamesTest {

    @Test
    void pathTraversalAndSeparatorsAreReducedToABasename() {
        assertThat(ImportFilenames.sanitize("../../patients.xlsx")).isEqualTo("patients.xlsx");
        assertThat(ImportFilenames.sanitize("C:\\Users\\x\\legacy.csv")).isEqualTo("legacy.csv");
        assertThat(ImportFilenames.sanitize("/etc/passwd")).isEqualTo("passwd");
        assertThat(ImportFilenames.sanitize("..")).isEqualTo(ImportFilenames.FALLBACK);
        assertThat(ImportFilenames.sanitize("dir/")).isEqualTo(ImportFilenames.FALLBACK);
    }

    @Test
    void controlCharactersAreRemovedLengthIsBoundedAndBlankFallsBack() {
        assertThat(ImportFilenames.sanitize("bad\u0000name\r\n.csv")).isEqualTo("badname.csv");
        assertThat(ImportFilenames.sanitize("x".repeat(500) + ".csv")).hasSize(ImportFilenames.MAX_LENGTH);
        assertThat(ImportFilenames.sanitize(null)).isEqualTo(ImportFilenames.FALLBACK);
        assertThat(ImportFilenames.sanitize("   ")).isEqualTo(ImportFilenames.FALLBACK);
    }

    @Test
    void onlyCsvAndXlsxAreFormats() {
        assertThat(ImportFilenames.formatOf("a.csv")).isEqualTo(ImportFormat.CSV);
        assertThat(ImportFilenames.formatOf("A.XLSX")).isEqualTo(ImportFormat.XLSX);
        assertThat(ImportFilenames.formatOf("a.xls")).isNull();
        assertThat(ImportFilenames.formatOf("a.txt")).isNull();
        assertThat(ImportFilenames.formatOf("import")).isNull();
    }
}
