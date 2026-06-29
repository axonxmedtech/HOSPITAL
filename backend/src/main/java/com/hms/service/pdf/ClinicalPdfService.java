package com.hms.service.pdf;

import com.hms.entity.*;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ClinicalPdfService {

    @Autowired
    private com.hms.repository.OpdRepository opdRepository;

    @Autowired
    private com.hms.repository.DoctorRepository doctorRepository;

    @Autowired
    private PdfLayoutHelper helper;

    public ByteArrayInputStream generatePrescriptionPdf(
            Hospital hospital,
            Doctor doctor,
            Patient patient,
            MedicalRecord medicalRecord,
            List<Prescription> prescriptions) {

        Document document = new Document(PageSize.A4, 36, 36, 36, 180);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            helper.addPageBorder(writer);
            document.open();

            // Resolve case number from associated OPD
            String customNo = "-";
            if (medicalRecord != null && medicalRecord.getOpdId() != null) {
                try {
                    com.hms.entity.Opd opd = opdRepository.findById(medicalRecord.getOpdId()).orElse(null);
                    if (opd != null) {
                        customNo = opd.getCaseId();
                    }
                } catch (Exception ignored) {
                }
            }

            // 1. Premium Patient Header
            String diagnosis = (medicalRecord != null && medicalRecord.getDiagnosis() != null)
                    ? medicalRecord.getDiagnosis()
                    : "-";
            helper.addPremiumPatientHeader(
                    document,
                    hospital,
                    doctor,
                    patient,
                    customNo,
                    (medicalRecord != null && medicalRecord.getCreatedAt() != null) ? medicalRecord.getCreatedAt()
                            : java.time.LocalDateTime.now(),
                    diagnosis,
                    "PRESCRIPTION");

            // 2. Main Medication Table
            PdfPTable rxTable = new PdfPTable(5);
            rxTable.setWidthPercentage(100);
            rxTable.setWidths(new float[] { 3f, 1f, 1f, 1.5f, 2.5f });
            rxTable.setSpacingBefore(10f);

            helper.addTableHeaderCell(rxTable, "Medicine");
            helper.addTableHeaderCell(rxTable, "Dosage");
            helper.addTableHeaderCell(rxTable, "Freq");
            helper.addTableHeaderCell(rxTable, "Duration");
            helper.addTableHeaderCell(rxTable, "Instruction");

            if (prescriptions != null && !prescriptions.isEmpty()) {
                for (Prescription p : prescriptions) {
                    helper.addTableCell(rxTable, p.getMedicineName(), false);
                    helper.addTableCell(rxTable, p.getDosage(), false);
                    helper.addTableCell(rxTable, p.getFrequency(), false);
                    helper.addTableCell(rxTable, p.getDuration(), false);
                    helper.addTableCell(rxTable, p.getInstructions(), false);
                }
            } else {
                PdfPCell cell = new PdfPCell(new Phrase("No medications prescribed.", PdfLayoutHelper.NORMAL_FONT));
                cell.setColspan(5);
                cell.setPadding(8f);
                rxTable.addCell(cell);
            }
            document.add(rxTable);

            // 3. Follow Up Section
            if (medicalRecord != null && medicalRecord.getFollowUpDate() != null) {
                document.add(new Paragraph("\n"));
                Paragraph flw = new Paragraph("Follow Up Date: ",
                        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.BOLD, PdfLayoutHelper.NAVY_BLUE));
                flw.add(new Chunk(medicalRecord.getFollowUpDate().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")),
                        PdfLayoutHelper.NORMAL_FONT));
                document.add(flw);
            }

            // 4. Fixed Signature Footer
            helper.addPremiumFooter(writer, hospital, patient, customNo, "Prescription Authorized Signature");

            document.close();

        } catch (DocumentException e) {
            throw new RuntimeException("Error generating PDF", e);
        }

        return new ByteArrayInputStream(out.toByteArray());
    }

    public ByteArrayInputStream generateIpdPrescriptionPdf(
            Hospital hospital,
            Patient patient,
            IpdAdmission ipd,
            List<Prescription> prescriptions) {

        Document document = new Document(PageSize.A4, 36, 36, 36, 180);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            helper.addPageBorder(writer);
            document.open();

            // Resolve Doctor associated with this IPD
            Doctor doctor = null;
            if (ipd != null && ipd.getDoctorId() != null) {
                doctor = doctorRepository.findById(ipd.getDoctorId()).orElse(null);
            }

            String ipdNo = (ipd != null) ? ipd.getIpdNumber() : "-";
            java.time.LocalDateTime admissionDateTime = (ipd != null && ipd.getAdmissionDatetime() != null)
                    ? ipd.getAdmissionDatetime()
                    : java.time.LocalDateTime.now();
            String primaryDiag = (ipd != null && ipd.getPrimaryDiagnosis() != null) ? ipd.getPrimaryDiagnosis() : "-";

            // 1. Premium Patient Header
            helper.addPremiumPatientHeader(
                    document,
                    hospital,
                    doctor,
                    patient,
                    ipdNo,
                    admissionDateTime,
                    primaryDiag,
                    "IPD PRESCRIPTION");

            // 2. Main Medication Table
            PdfPTable rxTable = new PdfPTable(5);
            rxTable.setWidthPercentage(100);
            rxTable.setWidths(new float[] { 3f, 1f, 1f, 1.5f, 2.5f });
            rxTable.setSpacingBefore(10f);

            helper.addTableHeaderCell(rxTable, "Medicine");
            helper.addTableHeaderCell(rxTable, "Dosage");
            helper.addTableHeaderCell(rxTable, "Freq");
            helper.addTableHeaderCell(rxTable, "Duration");
            helper.addTableHeaderCell(rxTable, "Instruction");

            if (prescriptions != null && !prescriptions.isEmpty()) {
                for (Prescription p : prescriptions) {
                    helper.addTableCell(rxTable, p.getMedicineName(), false);
                    helper.addTableCell(rxTable, p.getDosage(), false);
                    helper.addTableCell(rxTable, p.getFrequency(), false);
                    helper.addTableCell(rxTable, p.getDuration(), false);
                    helper.addTableCell(rxTable, p.getInstructions(), false);
                }
            } else {
                PdfPCell cell = new PdfPCell(new Phrase("No medications prescribed.", PdfLayoutHelper.NORMAL_FONT));
                cell.setColspan(5);
                cell.setPadding(8f);
                rxTable.addCell(cell);
            }
            document.add(rxTable);

            // 3. Fixed Footer Style
            helper.addPremiumFooter(writer, hospital, patient, ipdNo, "Prescription Authorized Signature");

            document.close();

        } catch (DocumentException e) {
            throw new RuntimeException("Error generating IPD Prescription PDF", e);
        }

        return new ByteArrayInputStream(out.toByteArray());
    }

    public ByteArrayInputStream generateCasePaperPdf(
            Hospital hospital,
            Doctor doctor,
            Patient patient,
            Opd opd,
            MedicalRecord medicalRecord) {

        Document document = new Document(PageSize.A4, 36, 36, 36, 180);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            helper.addPageBorder(writer);
            document.open();

            // Resolve Doctor associated with this OPD if null
            if (doctor == null && opd != null && opd.getDoctor() != null) {
                doctor = opd.getDoctor();
            }

            String diagnosis = "-";
            if (medicalRecord != null && medicalRecord.getDiagnosis() != null
                    && !medicalRecord.getDiagnosis().isEmpty()) {
                diagnosis = medicalRecord.getDiagnosis();
            }

            // 1. Standard Premium Header
            helper.addPremiumPatientHeader(
                    document,
                    hospital,
                    doctor,
                    patient,
                    (opd != null) ? opd.getCaseId() : "-",
                    (opd != null) ? opd.getCreatedAt() : java.time.LocalDateTime.now(),
                    diagnosis,
                    "OPD CASE PAPER / CONSULTATION RECORD");

            // 2. Vitals Signs Section
            if (opd != null) {
                Paragraph vitalsTitle = new Paragraph("VITAL SIGNS",
                        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.BOLD, PdfLayoutHelper.NAVY_BLUE));
                vitalsTitle.setSpacingBefore(10f);
                vitalsTitle.setSpacingAfter(5f);
                document.add(vitalsTitle);

                PdfPTable vitalsTable = new PdfPTable(5);
                vitalsTable.setWidthPercentage(100);
                vitalsTable.setSpacingAfter(15f);

                helper.addTableHeaderCell(vitalsTable, "BP (mmHg)");
                helper.addTableHeaderCell(vitalsTable, "Temp (°F)");
                helper.addTableHeaderCell(vitalsTable, "Pulse (bpm)");
                helper.addTableHeaderCell(vitalsTable, "Weight (kg)");
                helper.addTableHeaderCell(vitalsTable, "SpO2 (%)");

                helper.addTableCell(vitalsTable,
                        (opd.getBp() != null && !opd.getBp().trim().isEmpty()) ? opd.getBp() : "--", false);
                helper.addTableCell(vitalsTable,
                        (opd.getTemperature() != null) ? String.valueOf(opd.getTemperature()) : "--", false);
                helper.addTableCell(vitalsTable, (opd.getPulse() != null) ? String.valueOf(opd.getPulse()) : "--",
                        false);
                helper.addTableCell(vitalsTable, (opd.getWeight() != null) ? String.valueOf(opd.getWeight()) : "--",
                        false);
                helper.addTableCell(vitalsTable, (opd.getSpo2() != null) ? String.valueOf(opd.getSpo2()) : "--", false);

                document.add(vitalsTable);
            }

            // 3. Clinical Consultation Info
            boolean hasClinicalInfo = false;
            PdfPTable clinicalTable = new PdfPTable(1);
            clinicalTable.setWidthPercentage(100);

            if (opd != null && opd.getProblem() != null && !opd.getProblem().trim().isEmpty()) {
                hasClinicalInfo = true;
                PdfPCell cell = new PdfPCell();
                cell.setBorder(Rectangle.NO_BORDER);
                Paragraph problemTitle = new Paragraph("CHIEF COMPLAINT / REASON FOR VISIT:",
                        PdfLayoutHelper.SMALL_BOLD_FONT);
                problemTitle.setSpacingBefore(5f);
                Paragraph problemVal = new Paragraph(opd.getProblem(), PdfLayoutHelper.NORMAL_FONT);
                problemVal.setSpacingAfter(10f);
                cell.addElement(problemTitle);
                cell.addElement(problemVal);
                clinicalTable.addCell(cell);
            }

            if (medicalRecord != null) {
                if (medicalRecord.getSymptoms() != null && !medicalRecord.getSymptoms().trim().isEmpty()) {
                    hasClinicalInfo = true;
                    PdfPCell cell = new PdfPCell();
                    cell.setBorder(Rectangle.NO_BORDER);
                    Paragraph symTitle = new Paragraph("SYMPTOMS / CHIEF COMPLAINTS:", PdfLayoutHelper.SMALL_BOLD_FONT);
                    symTitle.setSpacingBefore(5f);
                    Paragraph symVal = new Paragraph(medicalRecord.getSymptoms(), PdfLayoutHelper.NORMAL_FONT);
                    symVal.setSpacingAfter(10f);
                    cell.addElement(symTitle);
                    cell.addElement(symVal);
                    clinicalTable.addCell(cell);
                }

                if (medicalRecord.getDiagnosis() != null && !medicalRecord.getDiagnosis().trim().isEmpty()) {
                    hasClinicalInfo = true;
                    PdfPCell cell = new PdfPCell();
                    cell.setBorder(Rectangle.NO_BORDER);
                    Paragraph diagTitle = new Paragraph("DIAGNOSIS / CLINICAL IMPRESSION:",
                            PdfLayoutHelper.SMALL_BOLD_FONT);
                    diagTitle.setSpacingBefore(5f);
                    Paragraph diagVal = new Paragraph(medicalRecord.getDiagnosis(),
                            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.BOLD, PdfLayoutHelper.NAVY_BLUE));
                    diagVal.setSpacingAfter(10f);
                    cell.addElement(diagTitle);
                    cell.addElement(diagVal);
                    clinicalTable.addCell(cell);
                }

                if (medicalRecord.getTreatmentNotes() != null && !medicalRecord.getTreatmentNotes().trim().isEmpty()) {
                    hasClinicalInfo = true;
                    PdfPCell cell = new PdfPCell();
                    cell.setBorder(Rectangle.NO_BORDER);
                    Paragraph notesTitle = new Paragraph("TREATMENT & CLINICAL NOTES:",
                            PdfLayoutHelper.SMALL_BOLD_FONT);
                    notesTitle.setSpacingBefore(5f);
                    Paragraph notesVal = new Paragraph(medicalRecord.getTreatmentNotes(), PdfLayoutHelper.NORMAL_FONT);
                    notesVal.setSpacingAfter(10f);
                    cell.addElement(notesTitle);
                    cell.addElement(notesVal);
                    clinicalTable.addCell(cell);
                }
            }

            if (hasClinicalInfo) {
                Paragraph clinHeading = new Paragraph("CLINICAL EVALUATION",
                        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.BOLD, PdfLayoutHelper.NAVY_BLUE));
                clinHeading.setSpacingBefore(10f);
                clinHeading.setSpacingAfter(5f);
                document.add(clinHeading);
                document.add(clinicalTable);
            }

            // 4. Fixed Bottom Signature Footer
            helper.addPremiumFooter(writer, hospital, patient, (opd != null) ? opd.getCaseId() : "-",
                    "Doctor Authorized Signature");

            document.close();

        } catch (DocumentException e) {
            throw new RuntimeException("Error generating Case Paper PDF", e);
        }

        return new ByteArrayInputStream(out.toByteArray());
    }

    public ByteArrayInputStream generateDischargeSummaryPdf(
            Hospital hospital,
            Patient patient,
            IpdAdmission ipd,
            DischargeSummary summary,
            Doctor doctor) {

        Document document = new Document(PageSize.A4, 36, 36, 36, 180);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            helper.addPageBorder(writer);
            document.open();

            String diagnosis = (summary != null && summary.getFinalDiagnosis() != null) ? summary.getFinalDiagnosis()
                    : "-";

            // 1. Premium Letterhead Patient Header
            helper.addPremiumPatientHeader(
                    document,
                    hospital,
                    doctor,
                    patient,
                    (ipd != null) ? ipd.getIpdNumber() : "-",
                    (ipd != null && ipd.getAdmissionDatetime() != null) ? ipd.getAdmissionDatetime()
                            : java.time.LocalDateTime.now(),
                    diagnosis,
                    "PATIENT DISCHARGE SUMMARY");

            // 2. Admission & Discharge Timelines Table
            Paragraph timelineTitle = new Paragraph("ADMISSION & DISCHARGE TIMELINE",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.BOLD, PdfLayoutHelper.NAVY_BLUE));
            timelineTitle.setSpacingBefore(10f);
            timelineTitle.setSpacingAfter(5f);
            document.add(timelineTitle);

            PdfPTable timelineTable = new PdfPTable(2);
            timelineTable.setWidthPercentage(100);
            timelineTable.setSpacingAfter(15f);

            helper.addTableHeaderCell(timelineTable, "Admission Date & Time");
            helper.addTableHeaderCell(timelineTable, "Discharge Date & Time");

            String admDate = (ipd != null && ipd.getAdmissionDatetime() != null)
                    ? ipd.getAdmissionDatetime().format(DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm a"))
                    : "--";
            String disDate = (ipd != null && ipd.getDischargeDatetime() != null)
                    ? ipd.getDischargeDatetime().format(DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm a"))
                    : "--";

            helper.addTableCell(timelineTable, admDate, false);
            helper.addTableCell(timelineTable, disDate, false);

            document.add(timelineTable);

            // 3. Clinical Summary Details
            if (summary != null) {
                // Final Diagnosis
                Paragraph fdTitle = new Paragraph("FINAL DIAGNOSIS:", PdfLayoutHelper.SMALL_BOLD_FONT);
                fdTitle.setSpacingBefore(8f);
                document.add(fdTitle);
                document.add(new Paragraph(summary.getFinalDiagnosis() != null ? summary.getFinalDiagnosis() : "-",
                        PdfLayoutHelper.NORMAL_FONT));

                // Treatment Given
                Paragraph tgTitle = new Paragraph("TREATMENT GIVEN:", PdfLayoutHelper.SMALL_BOLD_FONT);
                tgTitle.setSpacingBefore(8f);
                document.add(tgTitle);
                document.add(new Paragraph(summary.getTreatmentGiven() != null ? summary.getTreatmentGiven() : "-",
                        PdfLayoutHelper.NORMAL_FONT));

                // Discharge Notes
                Paragraph dnTitle = new Paragraph("DISCHARGE INSTRUCTIONS & CLINICAL NOTES:",
                        PdfLayoutHelper.SMALL_BOLD_FONT);
                dnTitle.setSpacingBefore(8f);
                document.add(dnTitle);
                document.add(new Paragraph(summary.getDischargeNotes() != null ? summary.getDischargeNotes() : "-",
                        PdfLayoutHelper.NORMAL_FONT));

                // Follow Up Date
                if (summary.getFollowUpDate() != null) {
                    Paragraph fuPara = new Paragraph("\nFOLLOW UP DATE: ",
                            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.BOLD, PdfLayoutHelper.NAVY_BLUE));
                    fuPara.add(new Chunk(summary.getFollowUpDate().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")),
                            PdfLayoutHelper.NORMAL_FONT));
                    document.add(fuPara);
                }
            }

            // 4. Fixed Signature Footer
            String docName = (doctor != null && doctor.getName() != null) ? doctor.getName() : "Attending Consultant";
            helper.addFixedFooter(writer, patient, (ipd != null) ? ipd.getIpdNumber() : "-", "Dr. " + docName);

            document.close();
        } catch (Exception e) {
            throw new RuntimeException("Error generating Discharge Summary PDF", e);
        }

        return new ByteArrayInputStream(out.toByteArray());
    }
}
