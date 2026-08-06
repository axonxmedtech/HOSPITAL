package com.hms.service.import_;

import com.hms.dto.import_.ParsedSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkbookParserTest {

    private final WorkbookParser parser = new WorkbookParser();

    private byte[] xlsx(String sheetName, String[][] rows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet(sheetName);
            for (int r = 0; r < rows.length; r++) {
                var row = sheet.createRow(r);
                for (int c = 0; c < rows[r].length; c++) {
                    if (rows[r][c] != null) row.createCell(c).setCellValue(rows[r][c]);
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void readsHeadersAndRowsFromXlsx() throws Exception {
        byte[] data = xlsx("Patients", new String[][]{
                {"Name", "Mob No"},
                {"Ramesh Patel", "9876543210"},
                {"Sita Rao", ""}
        });
        ParsedSheet sheet = parser.parseXlsx(new ByteArrayInputStream(data), "Patients");
        assertThat(sheet.headers()).containsExactly("Name", "Mob No");
        assertThat(sheet.rows()).hasSize(2);
        assertThat(sheet.rows().get(0)).containsEntry("Name", "Ramesh Patel");
        assertThat(sheet.rows().get(1)).containsEntry("Mob No", "");
    }

    @Test
    void blankValuesAreEmptyStringsNotMissingKeys() throws Exception {
        byte[] data = xlsx("Patients", new String[][]{
                {"Name", "Gender"},
                {"Sita Rao", null}
        });
        ParsedSheet sheet = parser.parseXlsx(new ByteArrayInputStream(data), "Patients");
        assertThat(sheet.rows().get(0)).containsKey("Gender");
        assertThat(sheet.rows().get(0).get("Gender")).isEmpty();
    }

    @Test
    void skipsFullyBlankRows() throws Exception {
        byte[] data = xlsx("Patients", new String[][]{
                {"Name"},
                {""},
                {"Sita Rao"}
        });
        ParsedSheet sheet = parser.parseXlsx(new ByteArrayInputStream(data), "Patients");
        assertThat(sheet.rows()).hasSize(1);
    }

    @Test
    void readsCsvIncludingByteOrderMark() {
        String csv = "﻿Name,Mob No\nRamesh Patel,9876543210\n";
        ParsedSheet sheet = parser.parseCsv(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        assertThat(sheet.headers()).containsExactly("Name", "Mob No");
        assertThat(sheet.rows().get(0)).containsEntry("Name", "Ramesh Patel");
    }

    @Test
    void rejectsAFileWithNoHeaderRow() {
        assertThatThrownBy(() -> parser.parseCsv(new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("header");
    }

    @Test
    void listsSheetNames() throws Exception {
        byte[] data = xlsx("Patients", new String[][]{{"Name"}, {"A"}});
        List<String> names = parser.sheetNames(new ByteArrayInputStream(data));
        assertThat(names).containsExactly("Patients");
    }
}
