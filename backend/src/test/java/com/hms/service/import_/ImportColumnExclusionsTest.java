package com.hms.service.import_;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ImportColumnExclusionsTest {
    private final SheetHeader header = new SheetHeader("Patients", List.of("Name", "Extra", "Phone", "Other"));
    private final Map<String, String> mapping = Map.of("Name", "name", "Phone", "phone");

    @Test
    void absentExclusionsPreserveTheOriginalObjects() {
        ParsedRow row = new ParsedRow(4, List.of("Person", "metadata", "9000000001", "kept"), List.of());
        assertThat(ImportColumnExclusions.NONE.header(header)).isSameAs(header);
        assertThat(ImportColumnExclusions.NONE.row(row)).isSameAs(row);
    }

    @Test
    void projectionRemovesOnlySelectedColumnsAndPreservesAlignmentAndRowNumbers() {
        var projection = ImportColumnExclusions.validate(header, mapping, List.of(" extra ", "OTHER"));
        ParsedRow row = new ParsedRow(9, List.of("Person", "excluded", "9000000001", "also excluded"), List.of());
        assertThat(projection.header(header).display()).containsExactly("Name", "Phone");
        assertThat(projection.header(header).sheetName()).isEqualTo("Patients");
        assertThat(projection.row(row).values()).containsExactly("Person", "9000000001");
        assertThat(projection.row(row).rowNum()).isEqualTo(9);
        assertThat(projection.row(row).get(projection.header(header), "Phone")).isEqualTo("9000000001");
    }

    @Test
    void exclusionsCannotHideParserProblems() {
        var problem = new RowProblem(RowProblem.Code.CELL_TOO_LONG, "Extra", 1);
        var row = new ParsedRow(2, List.of("Person", "", "9000000001"), List.of(problem));
        assertThat(ImportColumnExclusions.validate(header, mapping, List.of("Extra")).row(row).problems()).containsExactly(problem);
    }

    @Test
    void mappedColumnConflictsUseTheSameNormalizationAsSourceHeaders() {
        assertThatThrownBy(() -> ImportColumnExclusions.validate(header, Map.of(" name ", "name"), List.of("NAME")))
                .isInstanceOf(InvalidImportMappingException.class);
    }
}
