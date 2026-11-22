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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ClinicalPdfService {

    private static final Logger logger = LoggerFactory.getLogger(ClinicalPdfService.class);

    @Autowired
    private com.hms.repository.OpdRepository opdRepository;

    @Autowired
    private com.hms.repository.DoctorRepository doctorRepository;

    @Autowired
    private PdfLayoutHelper helper;

    @Autowired
    private com.hms.service.hospital.VitalSettingsService vitalSettingsService;

    @Autowired(required = false)
    private com.hms.repository.ConsultationNotePresetRepository presetRepository;

    @Autowired(required = false)
    private com.hms.repository.ConsultationStatementRepository statementRepository;

    @Autowired(required = false)
    private com.hms.repository.HospitalSettingRepository hospitalSettingRepository;

    private static final com.fasterxml.jackson.databind.ObjectMapper VITALS_JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * The vitals to print for this OPD: the hospital's enabled vitals, in order,
     * as [header, value] pairs. Built-ins read their typed Opd column; custom
     * vitals are read from the opd.custom_vitals JSON. Values default to "--".
     */
    private java.util.List<String[]> resolveVitalsForPrint(Hospital hospital, com.hms.entity.Opd opd) {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        if (hospital == null || hospital.getId() == null) return out;

        java.util.Map<String, String> customs = java.util.Collections.emptyMap();
        if (opd.getCustomVitals() != null && !opd.getCustomVitals().isBlank()) {
            try {
                customs = VITALS_JSON.readValue(opd.getCustomVitals(),
                        new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, String>>() {});
            } catch (Exception e) {
                logger.debug("Could not parse custom vitals for OPD {}", opd.getId(), e);
            }
        }

        java.util.List<java.util.Map<String, Object>> enabled;
        try {
            enabled = vitalSettingsService.enabledVitalsFor(hospital.getId());
        } catch (Exception e) {
            logger.warn("Could not load vitals config; printing none", e);
            return out;
        }

        for (java.util.Map<String, Object> v : enabled) {
            String key = (String) v.get("key");
            String label = (String) v.get("label");
            String unit = (String) v.get("unit");
            boolean isCustom = Boolean.TRUE.equals(v.get("isCustom"));

            String value;
            if (isCustom) {
                value = customs.get(key);
            } else {
                value = switch (key) {
                    case "BP" -> opd.getBp();
                    case "TEMPERATURE" -> opd.getTemperature() == null ? null : String.valueOf(opd.getTemperature());
                    case "PULSE" -> opd.getPulse() == null ? null : String.valueOf(opd.getPulse());
                    case "HEIGHT" -> opd.getHeight() == null ? null : String.valueOf(opd.getHeight());
                    case "WEIGHT" -> opd.getWeight() == null ? null : String.valueOf(opd.getWeight());
                    case "SPO2" -> opd.getSpo2() == null ? null : String.valueOf(opd.getSpo2());
                    default -> null;
                };
            }
            String header = (unit == null || unit.isBlank()) ? label : label + " (" + unit + ")";
            out.add(new String[] { header, (value == null || value.isBlank()) ? "--" : value });
        }
        return out;
    }

    public ByteArrayInputStream generatePrescriptionPdf(
            Hospital hospital,
            Doctor doctor,
            Patient patient,
            MedicalRecord medicalRecord,
            List<Prescription> prescriptions) {
        String lang = (medicalRecord != null && medicalRecord.getConsultationLanguage() != null)
                ? medicalRecord.getConsultationLanguage() : "en";
        return generatePrescriptionPdf(hospital, doctor, patient, medicalRecord, prescriptions, lang);
    }

    public ByteArrayInputStream generatePrescriptionPdf(
            Hospital hospital,
            Doctor doctor,
            Patient patient,
            MedicalRecord medicalRecord,
            List<Prescription> prescriptions,
            String lang) {

        Document document = new Document(PageSize.A4, 36, 36, 36, 180);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            helper.addPageBorder(writer);
            document.open();

            // Resolve language: if lang is default/en and medicalRecord has explicit consultation language, use it
            String effectiveLang = lang;
            if ((effectiveLang == null || effectiveLang.isBlank() || "en".equalsIgnoreCase(effectiveLang))
                    && medicalRecord != null && medicalRecord.getConsultationLanguage() != null && !medicalRecord.getConsultationLanguage().isBlank()) {
                effectiveLang = medicalRecord.getConsultationLanguage();
            }
            if ((effectiveLang == null || effectiveLang.isBlank() || "en".equalsIgnoreCase(effectiveLang))
                    && hospital != null && hospital.getId() != null && hospitalSettingRepository != null) {
                try {
                    effectiveLang = hospitalSettingRepository.findByHospital_Id(hospital.getId())
                            .map(HospitalSetting::getDefaultConsultationLanguage)
                            .orElse(effectiveLang);
                } catch (Exception ignored) {
                }
            }

            final String printMode = PatientInstructionFormatter.normalizeMode(effectiveLang);

            // Resolve case number from associated OPD
            String customNo = "-";
            if (medicalRecord != null && medicalRecord.getOpdId() != null) {
                try {
                    // Scoped by the record's own hospital rather than the security context: this
                    // is a rendering service with no principal of its own, and the record being
                    // printed is the authority on which tenant the document belongs to.
                    com.hms.entity.Opd opd = opdRepository
                            .findByIdAndHospitalIdWithPatientAndDoctor(
                                    medicalRecord.getOpdId(), medicalRecord.getHospitalId())
                            .orElse(null);
                    if (opd != null) {
                        customNo = opd.getCaseId();
                    }
                } catch (Exception e) {
                    logger.debug("Could not resolve OPD case number for prescription PDF", e);
                }
            }

            // 1. Premium Patient Header
            String diagnosis = (medicalRecord != null && medicalRecord.getDiagnosis() != null) ? medicalRecord.getDiagnosis() : "-";
            helper.addPremiumPatientHeader(
                    document,
                    hospital,
                    doctor,
                    patient,
                    customNo,
                    (medicalRecord != null && medicalRecord.getCreatedAt() != null) ? medicalRecord.getCreatedAt() : java.time.LocalDateTime.now(),
                    diagnosis,
                    "PRESCRIPTION"
            );

            // 2. Main Medication Table
            PdfPTable rxTable = new PdfPTable(5);
            rxTable.setWidthPercentage(100);
            rxTable.setWidths(new float[]{2.6f, 1.4f, 1.3f, 1.4f, 2.3f});
            rxTable.setSpacingBefore(10f);

            addRxHeader(rxTable, printMode);

            if (prescriptions != null && !prescriptions.isEmpty()) {
                for (Prescription p : prescriptions) {
                    helper.addTableCell(rxTable, withStrength(p), false);
                    helper.addTableCell(rxTable, PrintLabels.dose(p.getDosage(), printMode), false, PdfLayoutHelper.UNICODE_NORMAL_FONT);
                    helper.addTableCell(rxTable, PrintLabels.frequency(p.getFrequency(), printMode), false, PdfLayoutHelper.UNICODE_NORMAL_FONT);
                    helper.addTableCell(rxTable, PrintLabels.duration(p.getDuration(), printMode), false, PdfLayoutHelper.UNICODE_NORMAL_FONT);

                    String foodTimingLabel = PatientInstructionFormatter.getFoodTimingLabel(p.getFoodTiming(), effectiveLang);
                    String rawInstruction = (p.getInstructions() != null && !p.getInstructions().trim().isEmpty())
                            ? p.getInstructions().trim()
                            : "";
                    String instructionText = translateInstruction(rawInstruction, PatientInstructionFormatter.normalizeMode(effectiveLang));
                    String cellContent;
                    if (foodTimingLabel != null && !foodTimingLabel.isBlank()) {
                        if (!instructionText.isEmpty()) {
                            cellContent = foodTimingLabel + " · " + instructionText;
                        } else {
                            cellContent = foodTimingLabel;
                        }
                    } else {
                        cellContent = instructionText;
                    }
                    helper.addTableCell(rxTable, cellContent, false, PdfLayoutHelper.UNICODE_NORMAL_FONT);
                }
            } else {
                PdfPCell cell = new PdfPCell(new Phrase(PrintLabels.label("noMedicines", printMode), PdfLayoutHelper.UNICODE_NORMAL_FONT));
                cell.setColspan(5);
                cell.setPadding(8f);
                rxTable.addCell(cell);
            }
            document.add(rxTable);

            // 3. Doctor Advice Section (if present)
            if (medicalRecord != null && medicalRecord.getTreatmentNotes() != null && !medicalRecord.getTreatmentNotes().trim().isEmpty()) {
                document.add(new Paragraph("\n"));
                String adviceTitle = PrintLabels.label("advice", printMode) + ":";
                Paragraph advHead = new Paragraph(adviceTitle, PdfLayoutHelper.UNICODE_NAVY_BOLD_FONT);
                advHead.setSpacingBefore(6f);
                advHead.setSpacingAfter(3f);
                document.add(advHead);
                String formattedAdvice = formatAdviceNotes(
                        medicalRecord.getTreatmentNotes().trim(), effectiveLang, hospital != null ? hospital.getId() : null);
                Paragraph advBody = new Paragraph(formattedAdvice, PdfLayoutHelper.UNICODE_NORMAL_FONT);
                document.add(advBody);
            }

            // 4. Follow Up Section
            if (medicalRecord != null && medicalRecord.getFollowUpDate() != null) {
                document.add(new Paragraph("\n"));
                Paragraph flw = new Paragraph(PrintLabels.label("followUp", printMode) + ": ", PdfLayoutHelper.UNICODE_NAVY_BOLD_FONT);
                flw.add(new Chunk(medicalRecord.getFollowUpDate().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")), PdfLayoutHelper.NORMAL_FONT));
                document.add(flw);
            }

            // 5. Fixed Signature Footer
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
            final String printMode = PatientInstructionFormatter.normalizeMode(hospitalDefaultLanguage(hospital));

            // 1. Premium Patient Header
            helper.addPremiumPatientHeader(
                    document,
                    hospital,
                    doctor,
                    patient,
                    ipdNo,
                    admissionDateTime,
                    primaryDiag,
                    "IPD PRESCRIPTION"
            );

            // 2. Main Medication Table
            PdfPTable rxTable = new PdfPTable(5);
            rxTable.setWidthPercentage(100);
            rxTable.setWidths(new float[]{2.6f, 1.4f, 1.3f, 1.4f, 2.3f});
            rxTable.setSpacingBefore(10f);

            addRxHeader(rxTable, printMode);

            if (prescriptions != null && !prescriptions.isEmpty()) {
                for (Prescription p : prescriptions) {
                    helper.addTableCell(rxTable, withStrength(p), false);
                    helper.addTableCell(rxTable, PrintLabels.dose(p.getDosage(), printMode), false, PdfLayoutHelper.UNICODE_NORMAL_FONT);
                    helper.addTableCell(rxTable, PrintLabels.frequency(p.getFrequency(), printMode), false, PdfLayoutHelper.UNICODE_NORMAL_FONT);
                    helper.addTableCell(rxTable, PrintLabels.duration(p.getDuration(), printMode), false, PdfLayoutHelper.UNICODE_NORMAL_FONT);
                    helper.addTableCell(rxTable, p.getInstructions(), false);
                }
            } else {
                PdfPCell cell = new PdfPCell(new Phrase(PrintLabels.label("noMedicines", printMode), PdfLayoutHelper.UNICODE_NORMAL_FONT));
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

    private static final java.time.format.DateTimeFormatter DS_DATE_TIME =
            java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", java.util.Locale.ENGLISH);
    private static final java.time.format.DateTimeFormatter DS_DATE =
            java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale.ENGLISH);
    private static final java.time.format.DateTimeFormatter DS_TIME =
            java.time.format.DateTimeFormatter.ofPattern("hh:mm a", java.util.Locale.ENGLISH);

    /**
     * The discharge summary handed to the patient: admission details, diagnosis, course in
     * hospital, surgeries, investigations, condition at discharge, medicines to take home,
     * advice and follow-up, with the treating doctor's signature. Text sections use the
     * Unicode font so summaries written in Marathi or Hindi print correctly.
     */
    public ByteArrayInputStream generateDischargeSummaryPdf(Hospital hospital, Patient patient, DischargeSummaryPrint p) {
        Document document = new Document(PageSize.A4, 36, 36, 36, 140);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            helper.addPageBorder(writer);
            document.open();

            IpdAdmission ipd = p.ipd;
            com.hms.entity.DischargeSummary ds = p.summary;
            helper.addPremiumPatientHeader(document, hospital, p.doctor, patient, ipd.getIpdNumber(),
                    ipd.getAdmissionDatetime(), ds.getFinalDiagnosis(), "DISCHARGE SUMMARY");

            PdfPTable facts = new PdfPTable(4);
            facts.setWidthPercentage(100);
            facts.setWidths(new float[]{1.3f, 2f, 1.3f, 2f});
            facts.setSpacingBefore(8f);
            dsFact(facts, "Admitted on", ipd.getAdmissionDatetime() == null ? "-" : ipd.getAdmissionDatetime().format(DS_DATE_TIME));
            dsFact(facts, "Discharged on", ipd.getDischargeDatetime() == null ? "Planned (not yet discharged)"
                    : ipd.getDischargeDatetime().format(DS_DATE_TIME));
            dsFact(facts, "Ward / Bed", dash(p.ward) + " / " + dash(p.bed));
            dsFact(facts, "Consultant", p.doctor == null ? "-" : PdfLayoutHelper.doctorTitle(p.doctor.getName()));
            dsFact(facts, "Admission type", dash(ipd.getAdmissionType()));
            dsFact(facts, "Discharge type", dischargeTypeText(ipd.getDischargeType())
                    + (ipd.getDischargeNote() == null ? "" : " — " + ipd.getDischargeNote()));
            dsFact(facts, "Condition", ds.getConditionAtDischarge() == null ? "-" : titleCase(ds.getConditionAtDischarge()));
            dsFact(facts, "Provisional diagnosis", dash(ipd.getPrimaryDiagnosis()));
            document.add(facts);

            dsSection(document, "Final diagnosis", ds.getFinalDiagnosis());
            dsSection(document, "Course in hospital / treatment given", ds.getTreatmentGiven());
            dsSection(document, "Procedures", proceduresText(ds.getProceduresDone(), p.surgeries));
            dsSection(document, "Key investigations", ds.getInvestigations());

            if (p.medicines != null && !p.medicines.isEmpty()) {
                dsHeading(document, "Medicines to take at home");
                PdfPTable meds = new PdfPTable(5);
                meds.setWidthPercentage(100);
                meds.setWidths(new float[]{2.6f, 1.2f, 1.4f, 1f, 2.4f});
                for (String h : new String[]{"Medicine", "Dose", "Frequency", "Days", "Instructions"}) {
                    helper.addTableHeaderCell(meds, h);
                }
                for (com.hms.dto.PlanDischargeRequest.DischargeMedication m : p.medicines) {
                    helper.addTableCell(meds, m.getName(), false, PdfLayoutHelper.UNICODE_NORMAL_FONT);
                    helper.addTableCell(meds, dash(m.getDose()), false, PdfLayoutHelper.UNICODE_NORMAL_FONT);
                    helper.addTableCell(meds, dash(m.getFrequency()), false, PdfLayoutHelper.UNICODE_NORMAL_FONT);
                    helper.addTableCell(meds, m.getDurationDays() == null ? "-" : String.valueOf(m.getDurationDays()), false);
                    helper.addTableCell(meds, dash(m.getInstructions()), false, PdfLayoutHelper.UNICODE_NORMAL_FONT);
                }
                document.add(meds);
            }

            dsSection(document, "Advice on discharge", ds.getAdvice());
            dsSection(document, "Other notes", ds.getDischargeNotes());
            if (ds.getFollowUpDate() != null) {
                java.time.LocalTime t = p.followUpTime != null ? p.followUpTime : ds.getFollowUpTime();
                dsSection(document, "Follow-up", ds.getFollowUpDate().format(DS_DATE)
                        + (t == null ? "" : " at " + t.format(DS_TIME))
                        + (p.doctor == null ? "" : " with " + PdfLayoutHelper.doctorTitle(p.doctor.getName()))
                        + ". Come earlier if symptoms return or get worse.");
            }

            helper.addPremiumFooter(writer, hospital, patient, ipd.getIpdNumber(), "Treating Doctor's Signature");
            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Error generating the discharge summary PDF", e);
        }
        return new ByteArrayInputStream(out.toByteArray());
    }

    /** The kitchen's diet sheet: every patient in a bed with their current diet, by ward and bed. */
    public ByteArrayInputStream generateDietSheetPdf(Hospital hospital, List<java.util.Map<String, Object>> rows) {
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            helper.addPageBorder(writer);
            document.open();
            helper.addStyledHeader(document, hospital, "DIET SHEET");
            Paragraph when = new Paragraph("Printed " + java.time.LocalDateTime.now(java.time.ZoneId.systemDefault()).format(DS_DATE_TIME)
                    + " · " + rows.size() + " patient" + (rows.size() == 1 ? "" : "s"), PdfLayoutHelper.LABEL_FONT);
            when.setSpacingAfter(8f);
            document.add(when);
            PdfPTable t = new PdfPTable(5);
            t.setWidthPercentage(100);
            t.setWidths(new float[]{1.6f, 1f, 2.2f, 1.6f, 3f});
            for (String h : new String[]{"Ward", "Bed", "Patient", "Diet", "Instructions"}) {
                helper.addTableHeaderCell(t, h);
            }
            for (java.util.Map<String, Object> r : rows) {
                helper.addTableCell(t, String.valueOf(r.get("ward")), false, PdfLayoutHelper.UNICODE_NORMAL_FONT);
                helper.addTableCell(t, String.valueOf(r.get("bed")), false, PdfLayoutHelper.UNICODE_NORMAL_FONT);
                helper.addTableCell(t, r.get("patientName") + " (" + r.get("ipdNumber") + ")", false, PdfLayoutHelper.UNICODE_NORMAL_FONT);
                Object diet = r.get("dietType");
                helper.addTableCell(t, diet == null ? "Not set" : dietLabel(String.valueOf(diet)), false, PdfLayoutHelper.UNICODE_BOLD_FONT);
                helper.addTableCell(t, dash(r.get("instructions") == null ? null : String.valueOf(r.get("instructions"))), false,
                        PdfLayoutHelper.UNICODE_NORMAL_FONT);
            }
            if (rows.isEmpty()) {
                PdfPCell none = new PdfPCell(new Phrase("No patients in a bed.", PdfLayoutHelper.UNICODE_NORMAL_FONT));
                none.setColspan(5);
                none.setPadding(8f);
                t.addCell(none);
            }
            document.add(t);
            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Error generating the diet sheet PDF", e);
        }
        return new ByteArrayInputStream(out.toByteArray());
    }

    private static String dietLabel(String type) {
        return switch (type) {
            case "NBM" -> "Nil by mouth (NBM)";
            case "CLEAR_LIQUID" -> "Clear liquid";
            case "LOW_SALT" -> "Low salt";
            case "HIGH_PROTEIN" -> "High protein";
            default -> titleCase(type);
        };
    }

    private void dsFact(PdfPTable table, String label, String value) {
        PdfPCell l = new PdfPCell(new Phrase(label, PdfLayoutHelper.LABEL_FONT));
        l.setBorder(Rectangle.NO_BORDER);
        l.setPadding(3f);
        table.addCell(l);
        PdfPCell v = new PdfPCell(new Phrase(value, PdfLayoutHelper.UNICODE_BOLD_FONT));
        v.setBorder(Rectangle.NO_BORDER);
        v.setPadding(3f);
        table.addCell(v);
    }

    private void dsHeading(Document document, String title) throws DocumentException {
        Paragraph h = new Paragraph(title.toUpperCase(java.util.Locale.ROOT), PdfLayoutHelper.UNICODE_NAVY_BOLD_FONT);
        h.setSpacingBefore(10f);
        h.setSpacingAfter(3f);
        document.add(h);
    }

    /**
     * A titled block of text; skipped entirely when there is nothing to say. Heading and text are
     * one paragraph kept together, so a heading is never left alone at the foot of a page.
     */
    private void dsSection(Document document, String title, String text) throws DocumentException {
        if (text == null || text.isBlank()) return;
        Paragraph block = new Paragraph();
        block.setLeading(14f);
        block.setSpacingBefore(10f);
        block.setKeepTogether(true);
        block.add(new Chunk(title.toUpperCase(java.util.Locale.ROOT), PdfLayoutHelper.UNICODE_NAVY_BOLD_FONT));
        block.add(Chunk.NEWLINE);
        block.add(new Phrase(text.trim(), PdfLayoutHelper.UNICODE_NORMAL_FONT));
        document.add(block);
    }

    private static String proceduresText(String written, List<com.hms.entity.Surgery> surgeries) {
        StringBuilder b = new StringBuilder();
        if (surgeries != null) {
            for (com.hms.entity.Surgery s : surgeries) {
                b.append("• ").append(s.getProcedureName());
                if (s.getCompletedAt() != null) b.append(" (").append(s.getCompletedAt().format(DS_DATE)).append(")");
                if (s.getSurgeonName() != null && !s.getSurgeonName().isBlank()) b.append(" — ").append(s.getSurgeonName());
                b.append('\n');
            }
        }
        if (written != null && !written.isBlank()) b.append(written.trim());
        return b.toString().trim();
    }

    private static String dischargeTypeText(String type) {
        if (type == null) return "Normal";
        return switch (type) {
            case "LAMA" -> "LAMA (left against medical advice)";
            case "REFERRED" -> "Referred";
            case "EXPIRED" -> "Expired";
            case "ABSCONDED" -> "Absconded";
            default -> "Normal";
        };
    }

    private static String titleCase(String s) {
        return s.isEmpty() ? s : s.charAt(0) + s.substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    private static String dash(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    public ByteArrayInputStream generateCasePaperPdf(
            Hospital hospital,
            Doctor doctor,
            Patient patient,
            Opd opd,
            MedicalRecord medicalRecord) {
        return generateCasePaperPdf(hospital, doctor, patient, opd, medicalRecord, java.util.List.of());
    }

    public ByteArrayInputStream generateCasePaperPdf(
            Hospital hospital,
            Doctor doctor,
            Patient patient,
            Opd opd,
            MedicalRecord medicalRecord,
            java.util.List<com.hms.entity.LabOrder> labOrders) {
        String lang = (medicalRecord != null && medicalRecord.getConsultationLanguage() != null)
                ? medicalRecord.getConsultationLanguage() : "en";
        return generateCasePaperPdf(hospital, doctor, patient, opd, medicalRecord, labOrders, lang);
    }

    public ByteArrayInputStream generateCasePaperPdf(
            Hospital hospital,
            Doctor doctor,
            Patient patient,
            Opd opd,
            MedicalRecord medicalRecord,
            java.util.List<com.hms.entity.LabOrder> labOrders,
            String lang) {

        Document document = new Document(PageSize.A4, 36, 36, 36, 180);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Resolve language: if lang is default/en and medicalRecord has explicit consultation language, use it
        String effectiveLang = lang;
        if ((effectiveLang == null || effectiveLang.isBlank() || "en".equalsIgnoreCase(effectiveLang))
                && medicalRecord != null && medicalRecord.getConsultationLanguage() != null && !medicalRecord.getConsultationLanguage().isBlank()) {
            effectiveLang = medicalRecord.getConsultationLanguage();
        }
        if ((effectiveLang == null || effectiveLang.isBlank() || "en".equalsIgnoreCase(effectiveLang))
                && hospital != null && hospital.getId() != null && hospitalSettingRepository != null) {
            try {
                effectiveLang = hospitalSettingRepository.findByHospital_Id(hospital.getId())
                        .map(HospitalSetting::getDefaultConsultationLanguage)
                        .orElse(effectiveLang);
            } catch (Exception ignored) {
            }
        }

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            helper.addPageBorder(writer);
            document.open();

            // Resolve Doctor associated with this OPD if null
            if (doctor == null && opd != null && opd.getDoctor() != null) {
                doctor = opd.getDoctor();
            }

            String diagnosis = "-";
            if (medicalRecord != null && medicalRecord.getDiagnosis() != null && !medicalRecord.getDiagnosis().isEmpty()) {
                diagnosis = medicalRecord.getDiagnosis();
            }

            final String printMode = PatientInstructionFormatter.normalizeMode(effectiveLang);

            // 1. Standard Premium Header
            helper.addPremiumPatientHeader(
                    document,
                    hospital,
                    doctor,
                    patient,
                    (opd != null) ? opd.getCaseId() : "-",
                    (opd != null) ? opd.getCreatedAt() : java.time.LocalDateTime.now(),
                    diagnosis,
                    "OPD CASE PAPER / CONSULTATION RECORD"
            );

            // 2. Vitals Signs Section — only the vitals this hospital has switched on,
            //    including any it defined itself.
            if (opd != null) {
                java.util.List<String[]> vitals = resolveVitalsForPrint(hospital, opd); // [header, value]
                if (!vitals.isEmpty()) {
                    Paragraph vitalsTitle = new Paragraph(PrintLabels.label("vitals", printMode), PdfLayoutHelper.UNICODE_NAVY_BOLD_FONT);
                    vitalsTitle.setSpacingBefore(10f);
                    vitalsTitle.setSpacingAfter(5f);
                    document.add(vitalsTitle);

                    // Chunk into tables of at most 5 columns so any number of vitals lays out cleanly.
                    final int MAX_COLS = 5;
                    for (int start = 0; start < vitals.size(); start += MAX_COLS) {
                        java.util.List<String[]> chunk = vitals.subList(start, Math.min(start + MAX_COLS, vitals.size()));
                        PdfPTable vitalsTable = new PdfPTable(chunk.size());
                        vitalsTable.setWidthPercentage(100);
                        vitalsTable.setSpacingAfter(start + MAX_COLS >= vitals.size() ? 15f : 4f);
                        for (String[] v : chunk) helper.addTableHeaderCell(vitalsTable, v[0]);
                        for (String[] v : chunk) helper.addTableCell(vitalsTable, v[1], false);
                        document.add(vitalsTable);
                    }
                }
            }

            // 3. Clinical Consultation Info
            boolean hasClinicalInfo = false;
            PdfPTable clinicalTable = new PdfPTable(1);
            clinicalTable.setWidthPercentage(100);

            if (opd != null && opd.getProblem() != null && !opd.getProblem().trim().isEmpty()) {
                hasClinicalInfo = true;
                PdfPCell cell = new PdfPCell();
                cell.setBorder(Rectangle.NO_BORDER);
                Paragraph problemTitle = new Paragraph(PrintLabels.label("complaint", printMode) + ":", PdfLayoutHelper.UNICODE_BOLD_FONT);
                problemTitle.setSpacingBefore(5f);
                Paragraph problemVal = new Paragraph(opd.getProblem(), PdfLayoutHelper.UNICODE_NORMAL_FONT);
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
                    Paragraph symTitle = new Paragraph(PrintLabels.label("symptoms", printMode) + ":", PdfLayoutHelper.UNICODE_BOLD_FONT);
                    symTitle.setSpacingBefore(5f);
                    Paragraph symVal = new Paragraph(medicalRecord.getSymptoms(), PdfLayoutHelper.UNICODE_NORMAL_FONT);
                    symVal.setSpacingAfter(10f);
                    cell.addElement(symTitle);
                    cell.addElement(symVal);
                    clinicalTable.addCell(cell);
                }

                if (medicalRecord.getDiagnosis() != null && !medicalRecord.getDiagnosis().trim().isEmpty()) {
                    hasClinicalInfo = true;
                    PdfPCell cell = new PdfPCell();
                    cell.setBorder(Rectangle.NO_BORDER);
                    Paragraph diagTitle = new Paragraph(PrintLabels.label("diagnosis", printMode) + ":", PdfLayoutHelper.UNICODE_BOLD_FONT);
                    diagTitle.setSpacingBefore(5f);
                    Paragraph diagVal = new Paragraph(medicalRecord.getDiagnosis(), PdfLayoutHelper.UNICODE_NORMAL_FONT);
                    diagVal.setSpacingAfter(10f);
                    cell.addElement(diagTitle);
                    cell.addElement(diagVal);
                    clinicalTable.addCell(cell);
                }

                if (medicalRecord.getTreatmentNotes() != null && !medicalRecord.getTreatmentNotes().trim().isEmpty()) {
                    hasClinicalInfo = true;
                    PdfPCell cell = new PdfPCell();
                    cell.setBorder(Rectangle.NO_BORDER);
                    String notesHeader = PrintLabels.label("treatment", printMode) + ":";
                    Paragraph notesTitle = new Paragraph(notesHeader, PdfLayoutHelper.UNICODE_BOLD_FONT);
                    notesTitle.setSpacingBefore(5f);
                    String formattedNotes = formatAdviceNotes(
                            medicalRecord.getTreatmentNotes(), effectiveLang, hospital != null ? hospital.getId() : null);
                    Paragraph notesVal = new Paragraph(formattedNotes, PdfLayoutHelper.UNICODE_NORMAL_FONT);
                    notesVal.setSpacingAfter(10f);
                    cell.addElement(notesTitle);
                    cell.addElement(notesVal);
                    clinicalTable.addCell(cell);
                }
            }

            if (hasClinicalInfo) {
                Paragraph clinHeading = new Paragraph(PrintLabels.label("evaluation", printMode), PdfLayoutHelper.UNICODE_NAVY_BOLD_FONT);
                clinHeading.setSpacingBefore(10f);
                clinHeading.setSpacingAfter(5f);
                document.add(clinHeading);
                document.add(clinicalTable);
            }

            // 4. Lab Tests Advised (printed when the doctor ordered any at this consultation)
            if (labOrders != null && !labOrders.isEmpty()) {
                Paragraph labHead = new Paragraph(PrintLabels.label("labTests", printMode) + ":", PdfLayoutHelper.UNICODE_BOLD_FONT);
                labHead.setSpacingBefore(10f);
                document.add(labHead);
                String tests = labOrders.stream()
                        .map(com.hms.entity.LabOrder::getTestName)
                        .filter(java.util.Objects::nonNull)
                        .collect(java.util.stream.Collectors.joining(", "));
                document.add(new Paragraph(tests, PdfLayoutHelper.NORMAL_FONT));
            }

            // 5. Follow-up date (printed when the doctor scheduled one)
            if (medicalRecord != null && medicalRecord.getFollowUpDate() != null) {
                Paragraph flwHead = new Paragraph(PrintLabels.label("followUp", printMode).toUpperCase() + ":", PdfLayoutHelper.UNICODE_BOLD_FONT);
                flwHead.setSpacingBefore(10f);
                document.add(flwHead);
                document.add(new Paragraph(
                        medicalRecord.getFollowUpDate().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")),
                        PdfLayoutHelper.NORMAL_FONT));
            }

            // 6. Fixed Bottom Signature Footer
            helper.addPremiumFooter(writer, hospital, patient, (opd != null) ? opd.getCaseId() : "-", "Doctor Authorized Signature");

            document.close();

        } catch (DocumentException e) {
            throw new RuntimeException("Error generating Case Paper PDF", e);
        }

        return new ByteArrayInputStream(out.toByteArray());
    }

    /**
     * Formats clinical treatment notes bilingually when EN_MR or EN_HI is active.
     * Looks up phrases in hospital note presets first, then in platform master statements.
     */
    public String formatAdviceNotes(String rawNotes, String lang, Long hospitalId) {
        if (rawNotes == null || rawNotes.isBlank()) {
            return rawNotes;
        }
        String mode = PatientInstructionFormatter.normalizeMode(lang);
        if (PatientInstructionFormatter.MODE_EN.equals(mode)) {
            return rawNotes;
        }

        List<ConsultationNotePreset> presets = java.util.Collections.emptyList();
        if (hospitalId != null && presetRepository != null) {
            try {
                presets = presetRepository.findByHospitalIdAndFieldTypeAndIsActiveTrue(
                        hospitalId, ConsultationNotePreset.FIELD_TYPE_TREATMENT_NOTES);
            } catch (Exception ignored) {
            }
        }

        String[] lines = rawNotes.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                if (i > 0) sb.append("\n");
                continue;
            }

            // If line already contains bilingual separator with non-ASCII Devanagari characters, keep as-is
            if (trimmed.contains(" / ") && trimmed.chars().anyMatch(c -> c > 127)) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(trimmed);
                continue;
            }

            String translation = null;
            // 1. Match from hospital's consultation note presets
            for (ConsultationNotePreset p : presets) {
                if (p.getText() != null && p.getText().trim().equalsIgnoreCase(trimmed)) {
                    if (PatientInstructionFormatter.MODE_EN_MR.equals(mode) && p.getMarathiText() != null && !p.getMarathiText().isBlank()) {
                        translation = p.getMarathiText().trim();
                        break;
                    } else if (PatientInstructionFormatter.MODE_EN_HI.equals(mode) && p.getHindiText() != null && !p.getHindiText().isBlank()) {
                        translation = p.getHindiText().trim();
                        break;
                    }
                }
            }

            // 2. Match from platform master consultation statements
            if (translation == null && statementRepository != null) {
                try {
                    var stmtOpt = statementRepository.findMatchingActiveStatements(trimmed).stream().findFirst();
                    if (stmtOpt.isPresent()) {
                        var stmt = stmtOpt.get();
                        if (PatientInstructionFormatter.MODE_EN_MR.equals(mode) && stmt.getMarathiText() != null && !stmt.getMarathiText().isBlank()) {
                            translation = stmt.getMarathiText().trim();
                        } else if (PatientInstructionFormatter.MODE_EN_HI.equals(mode) && stmt.getHindiText() != null && !stmt.getHindiText().isBlank()) {
                            translation = stmt.getHindiText().trim();
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            String formattedLine = PatientInstructionFormatter.formatBilingual(trimmed, translation, mode);
            if (sb.length() > 0) sb.append("\n");
            sb.append(formattedLine);
        }

        return sb.toString();
    }

    private String translateInstruction(String text, String mode) {
        if (text == null || text.isBlank() || statementRepository == null) return text;
        if (PatientInstructionFormatter.MODE_EN.equals(mode)) return text;
        if (text.contains(" / ") && text.chars().anyMatch(c -> c > 127)) return text;

        try {
            var stmtOpt = statementRepository.findMatchingActiveStatements(text.trim()).stream().findFirst();
            if (stmtOpt.isPresent()) {
                var stmt = stmtOpt.get();
                String translation = PatientInstructionFormatter.MODE_EN_MR.equals(mode)
                        ? stmt.getMarathiText() : stmt.getHindiText();
                return PatientInstructionFormatter.formatBilingual(text.trim(), translation, mode);
            }
        } catch (Exception ignored) {
        }
        return text;
    }

    /** The medicine table's column headings, bilingual when the print is. */
    private void addRxHeader(PdfPTable rxTable, String printMode) {
        boolean bilingual = !PatientInstructionFormatter.MODE_EN.equals(printMode);
        com.lowagie.text.Font font = bilingual ? PdfLayoutHelper.UNICODE_WHITE_BOLD_FONT : null;
        for (String key : new String[] {"medicine", "dosage", "frequency", "duration", "instruction"}) {
            if (font == null) helper.addTableHeaderCell(rxTable, PrintLabels.label(key, printMode));
            else helper.addTableHeaderCell(rxTable, PrintLabels.label(key, printMode), font);
        }
    }

    /** The hospital's chosen print language (Settings), for documents without a consultation of their own. */
    private String hospitalDefaultLanguage(Hospital hospital) {
        if (hospital == null || hospital.getId() == null || hospitalSettingRepository == null) return null;
        try {
            return hospitalSettingRepository.findByHospital_Id(hospital.getId())
                    .map(HospitalSetting::getDefaultConsultationLanguage).orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** "Paracetamol 500 mg": the strength is printed with the name when it was recorded. */
    private static String withStrength(Prescription p) {
        String name = p.getMedicineName() == null ? "" : p.getMedicineName();
        String strength = p.getStrength();
        if (strength == null || strength.isBlank() || name.toLowerCase().contains(strength.trim().toLowerCase())) {
            return name;
        }
        return name + " " + strength.trim();
    }
}
