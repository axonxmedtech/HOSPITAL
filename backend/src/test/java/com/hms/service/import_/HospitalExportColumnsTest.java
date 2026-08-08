package com.hms.service.import_;

import com.hms.entity.Patient;
import com.hms.repository.AppointmentRepository;
import com.hms.repository.PatientRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Preserved columns come back out as columns.
 *
 * <p>An import keeps whatever the hospital's file carried that this schema does not model. Writing
 * that back as one cell of JSON is technically lossless and practically useless — nobody can sort,
 * filter or edit it, and a hospital taking their data elsewhere should get back something shaped
 * like what they handed over.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HospitalExportColumnsTest {

    @Mock PatientRepository patientRepository;
    @Mock AppointmentRepository appointmentRepository;
    @Mock SecurityContextHelper securityHelper;
    @Mock AuditLogService auditLogService;

    @InjectMocks HospitalExportService service;

    private Patient patient(String name, String customFieldsJson) {
        Patient p = new Patient();
        p.setName(name);
        p.setCustomFields(customFieldsJson);
        return p;
    }

    private Sheet exportedPatientsSheet(List<Patient> patients) throws Exception {
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        when(patientRepository.findByHospitalIdAndIsActiveTrue(7L)).thenReturn(patients);

        byte[] xlsx = service.exportAll();
        Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx));
        return wb.getSheet("Patients");
    }

    private List<String> headerOf(Sheet sheet) {
        List<String> headers = new ArrayList<>();
        Row row = sheet.getRow(0);
        for (int c = 0; c < row.getLastCellNum(); c++) {
            headers.add(row.getCell(c) == null ? "" : row.getCell(c).getStringCellValue());
        }
        return headers;
    }

    private String cell(Sheet sheet, int rowNum, String header) {
        int index = headerOf(sheet).indexOf(header);
        if (index < 0) return null;
        Row row = sheet.getRow(rowNum);
        return row.getCell(index) == null ? "" : row.getCell(index).getStringCellValue();
    }

    @Test
    void eachPreservedFieldGetsItsOwnColumn() throws Exception {
        Sheet sheet = exportedPatientsSheet(List.of(
                patient("Ramesh", "{\"Referred By\":\"Dr. Kulkarni\",\"Old Card No\":\"MRN-4471\"}")));

        assertThat(headerOf(sheet)).contains("Referred By", "Old Card No");
        assertThat(cell(sheet, 1, "Referred By")).isEqualTo("Dr. Kulkarni");
        assertThat(cell(sheet, 1, "Old Card No")).isEqualTo("MRN-4471");
    }

    /** The old single-cell dump must be gone, or the file has the data twice in two shapes. */
    @Test
    void thereIsNoLongerAJsonBlobColumn() throws Exception {
        Sheet sheet = exportedPatientsSheet(List.of(
                patient("Ramesh", "{\"Referred By\":\"Dr. Kulkarni\"}")));

        assertThat(headerOf(sheet)).doesNotContain("Imported information");
        assertThat(headerOf(sheet)).noneMatch(h -> h.contains("{"));
    }

    /**
     * Different patients carry different columns. The sheet must stay rectangular so it can be
     * edited and re-imported, so a patient without a column gets a blank rather than a gap.
     */
    @Test
    void patientsWithDifferentExtraFieldsShareOneRectangularSheet() throws Exception {
        Sheet sheet = exportedPatientsSheet(List.of(
                patient("Ramesh", "{\"Referred By\":\"Dr. Kulkarni\"}"),
                patient("Sita", "{\"Insurance TPA\":\"MediAssist\"}")));

        assertThat(headerOf(sheet)).contains("Referred By", "Insurance TPA");
        assertThat(cell(sheet, 1, "Insurance TPA")).isEmpty();
        assertThat(cell(sheet, 2, "Referred By")).isEmpty();
        assertThat(cell(sheet, 2, "Insurance TPA")).isEqualTo("MediAssist");
    }

    @Test
    void aPatientWithNoExtraFieldsExportsNormally() throws Exception {
        Sheet sheet = exportedPatientsSheet(List.of(patient("Ramesh", null)));

        assertThat(headerOf(sheet)).contains("Name");
        assertThat(cell(sheet, 1, "Name")).isEqualTo("Ramesh");
    }

    /** One corrupt record must not cost a hospital the other several thousand. */
    @Test
    void anUnreadablePreservedValueIsSkippedRatherThanFailingTheExport() throws Exception {
        Sheet sheet = exportedPatientsSheet(List.of(
                patient("Ramesh", "not valid json at all"),
                patient("Sita", "{\"Referred By\":\"Dr. K\"}")));

        assertThat(sheet).isNotNull();
        assertThat(cell(sheet, 1, "Name")).isEqualTo("Ramesh");
        assertThat(cell(sheet, 2, "Referred By")).isEqualTo("Dr. K");
    }
}
