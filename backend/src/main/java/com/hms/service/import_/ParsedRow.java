package com.hms.service.import_;

import java.util.List;

/**
 * One data row, aligned to its {@link SheetHeader}: {@code values.get(i)} belongs to
 * {@code header.display().get(i)}, and a blank cell is the empty string, never null and never
 * absent — "the hospital left gender empty" must stay distinguishable from "this file has no
 * gender column", which is a header-level fact.
 *
 * <p>Values are raw strings exactly as the parser read them (trimmed of surrounding whitespace):
 * no phone normalisation, no date interpretation, no formula-guard stripping. That is deliberate;
 * those are the importer's decisions and they are tested there, not here.
 *
 * @param rowNum   1-based row as the administrator sees it in Excel or a text editor: the header
 *                 is row 1. For .xlsx it is the physical sheet row (blank rows keep their
 *                 number); for CSV it is the record number, so a quoted multi-line field still
 *                 counts as one row.
 * @param problems parse problems on this row (see {@link RowProblem}); empty for a clean row.
 */
public record ParsedRow(int rowNum, List<String> values, List<RowProblem> problems) {

    public ParsedRow {
        values = List.copyOf(values);
        problems = List.copyOf(problems);
    }

    public boolean hasProblems() {
        return !problems.isEmpty();
    }

    /** Value under a header (any spelling that normalises to it); empty string when the column is absent. */
    public String get(SheetHeader header, String column) {
        int i = header.indexOf(column);
        return i < 0 || i >= values.size() ? "" : values.get(i);
    }
}
