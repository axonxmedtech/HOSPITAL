package com.hms.service.import_;

import com.hms.entity.Patient;
import com.hms.repository.AppointmentRepository;
import com.hms.repository.PatientRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Exports one hospital's patient data as a single .xlsx workbook.
 *
 * <p>Always scoped to the caller's hospital, taken from the JWT. An export is a bulk read of every
 * patient a tenant holds, so there is deliberately no request parameter that could widen it to
 * another hospital's records.
 *
 * <p>Written with {@link SXSSFWorkbook}, POI's streaming writer, which keeps only a window of rows
 * in memory. The import side uses the DOM reader because it must random-access a sheet; writing has
 * no such need, and a hospital with 50,000 patients would otherwise assemble the whole workbook in
 * heap before sending a byte.
 *
 * <p><b>The file is unencrypted patient data.</b> Once downloaded it sits outside every protection
 * the server provides, so every export is written to the audit log with the row count and the admin
 * who requested it.
 */
@Service
public class HospitalExportService {

    private static final Logger log = LoggerFactory.getLogger(HospitalExportService.class);

    /** Rows SXSSF keeps in memory before flushing to a temp file. */
    private static final int ROW_WINDOW = 200;

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final SecurityContextHelper securityHelper;
    private final AuditLogService auditLogService;

    public HospitalExportService(PatientRepository patientRepository,
                                 AppointmentRepository appointmentRepository,
                                 SecurityContextHelper securityHelper,
                                 AuditLogService auditLogService) {
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.securityHelper = securityHelper;
        this.auditLogService = auditLogService;
    }

    public String suggestedFilename() {
        return "hospital-export-" + LocalDateTime.now().format(STAMP) + ".xlsx";
    }

    public byte[] exportAll() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new IllegalArgumentException("Could not determine which hospital to export.");
        }

        SXSSFWorkbook wb = new SXSSFWorkbook(ROW_WINDOW);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = headerStyle(wb);

            List<Patient> patients = patientRepository.findByHospitalIdAndIsActiveTrue(hospitalId);
            writePatients(wb, header, patients);
            writeSummary(wb, header, hospitalId, patients.size());

            wb.write(out);

            try {
                auditLogService.logAction("DATA_EXPORT",
                        "Exported " + patients.size() + " patient record(s) to Excel",
                        securityHelper.getCurrentUserEmail(), hospitalId,
                        "HOSPITAL", String.valueOf(hospitalId), null);
            } catch (Exception e) {
                log.warn("Export audit log failed: {}", e.getMessage());
            }

            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not build the export file: " + e.getMessage());
        } finally {
            try {
                // close() deletes the temp files SXSSF spills rows into. Without it they accumulate
                // on the server for the life of the process — and they hold patient data.
                wb.close();
            } catch (Exception e) {
                log.warn("Could not clean up export workbook: {}", e.getMessage());
            }
        }
    }

    private void writePatients(Workbook wb, CellStyle header, List<Patient> patients) {
        Sheet sheet = wb.createSheet("Patients");
        writeHeader(sheet, header,
                "Patient ID", "Old patient ID (MRN)", "Name", "Gender", "Phone", "Email",
                "Date of birth", "Address", "Medical history", "Status", "Source",
                "Edited by staff", "Registered on", "Imported information");

        int r = 1;
        for (Patient p : patients) {
            Row row = sheet.createRow(r++);
            int c = 0;
            put(row, c++, p.getCustomId());
            put(row, c++, p.getLegacyId());
            put(row, c++, p.getName());
            put(row, c++, p.getGender());
            put(row, c++, p.getPhone());
            put(row, c++, p.getEmail());
            put(row, c++, p.getDateOfBirth() == null ? null : p.getDateOfBirth().toString());
            put(row, c++, p.getAddress());
            put(row, c++, p.getMedicalHistory());
            put(row, c++, p.getStatus() == null ? null : p.getStatus().name());
            put(row, c++, p.getSource() == null ? null : p.getSource().name());
            put(row, c++, Boolean.TRUE.equals(p.getManuallyEdited()) ? "Yes" : "No");
            put(row, c++, p.getCreatedAt() == null ? null : p.getCreatedAt().toString());
            // Columns a previous import preserved that this schema does not model. Included so a
            // hospital taking their data elsewhere loses nothing we captured on their behalf.
            put(row, c, p.getCustomFields());
        }
    }

    /**
     * States plainly what the file does and does not contain.
     *
     * <p>Visit, admission, prescription and billing history are not exported yet. Each needs its own
     * column design and its own decisions about money and reversals, and shipping a half-considered
     * version of that would be worse than shipping none — but an admin must not mistake this file
     * for a complete backup, so the omission is written into the workbook itself rather than left
     * to be discovered.
     */
    private void writeSummary(Workbook wb, CellStyle header, Long hospitalId, int patientCount) {
        Sheet sheet = wb.createSheet("Summary");
        writeHeader(sheet, header, "Record type", "Count", "Included in this file");

        int r = 1;
        r = summaryRow(sheet, r, "Patients", String.valueOf(patientCount), "Yes — Patients sheet");

        long appointments;
        try {
            appointments = appointmentRepository.countByHospitalIdAndIsActiveTrue(hospitalId);
        } catch (Exception e) {
            log.warn("Export summary appointment count failed: {}", e.getMessage());
            appointments = -1;
        }
        r = summaryRow(sheet, r, "Appointments",
                appointments < 0 ? "unavailable" : String.valueOf(appointments), "No");
        r = summaryRow(sheet, r, "OPD visits", "—", "No");
        r = summaryRow(sheet, r, "IPD admissions", "—", "No");
        r = summaryRow(sheet, r, "Prescriptions", "—", "No");
        r = summaryRow(sheet, r, "Bills", "—", "No");

        Row note = sheet.createRow(r + 1);
        put(note, 0, "This file contains patient records only. It is NOT a complete backup — "
                + "visit, admission, prescription and billing history are not included.");
        Row warn = sheet.createRow(r + 2);
        put(warn, 0, "The file is unencrypted and contains patient information. Store it securely "
                + "and delete local copies when you are finished with them.");
    }

    private int summaryRow(Sheet sheet, int r, String label, String count, String included) {
        Row row = sheet.createRow(r);
        put(row, 0, label);
        put(row, 1, count);
        put(row, 2, included);
        return r + 1;
    }

    private void writeHeader(Sheet sheet, CellStyle style, String... labels) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < labels.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(labels[i]);
            cell.setCellStyle(style);
        }
    }

    private CellStyle headerStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font bold = wb.createFont();
        bold.setBold(true);
        style.setFont(bold);
        return style;
    }

    /**
     * Writes a cell, neutralising spreadsheet formula injection.
     *
     * <p>A name or address beginning =, +, - or @ executes as a formula when the file is opened.
     * The values came from users, so they get the same apostrophe prefix the import error report
     * uses — and the importer strips that prefix on read, so an exported file re-imports cleanly.
     */
    private void put(Row row, int column, String value) {
        Cell cell = row.createCell(column);
        if (value == null) {
            cell.setCellValue("");
            return;
        }
        String safe = value;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        cell.setCellValue(safe);
    }
}
