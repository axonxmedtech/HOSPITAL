package com.hms.service;

import com.hms.entity.*;
import com.hms.entity.pharmacy.PharmacySale;
import com.hms.entity.pharmacy.PharmacySaleItem;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class PdfService {

    @Autowired
    private com.hms.repository.BillingItemRepository billingItemRepository;

    @Autowired
    private com.hms.repository.BillingMedicineRepository billingMedicineRepository;

    @Autowired
    private com.hms.repository.BillingPaymentRepository billingPaymentRepository;

    @Autowired
    private com.hms.repository.IpdAdmissionRepository ipdAdmissionRepository;

    @Autowired
    private com.hms.repository.WardRepository wardRepository;

    @Autowired
    private com.hms.repository.DoctorRepository doctorRepository;

    @Autowired
    private com.hms.repository.MedicalRecordRepository medicalRecordRepository;

    // Standardized Fonts
    private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.TIMES_BOLD, 22, Font.BOLD, new Color(0, 51, 102));
    private static final Font SUBTITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLDOBLIQUE, 16, Font.ITALIC, Color.DARK_GRAY);
    private static final Font LABEL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL, Color.GRAY);
    private static final Font VALUE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.NORMAL, Color.BLACK);
    private static final Font TABLE_HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.BOLD, Color.WHITE);
    private static final Font NORMAL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL, Color.BLACK);
    private static final Font FOOTER_FONT = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL, Color.GRAY);
    private static final Font SMALL_BOLD_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.BOLD, Color.BLACK);

    private static final Color PRIMARY_RED = new Color(180, 0, 0);
    private static final Color NAVY_BLUE = new Color(0, 51, 102);
    private static final Font RED_TITLE_FONT = FontFactory.getFont(FontFactory.TIMES_BOLD, 22, Font.BOLD, PRIMARY_RED);
    private static final Font RED_DOCTOR_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Font.BOLD, PRIMARY_RED);

    // Helper: build a dynamic list of charge rows from billing items + medicines
    private java.util.List<Object[]> buildDynamicChargeRows(
            java.util.List<com.hms.entity.BillingItem> items,
            java.util.List<com.hms.entity.BillingMedicine> medicines) {
        // Each entry is: { String description, BigDecimal amount }
        java.util.List<Object[]> rows = new java.util.ArrayList<>();

        if (items != null) {
            for (com.hms.entity.BillingItem it : items) {
                String desc = (it.getDescription() != null && !it.getDescription().trim().isEmpty())
                        ? it.getDescription().trim()
                        : "Service Charge";
                java.math.BigDecimal amt = (it.getAmount() != null) ? it.getAmount() : java.math.BigDecimal.ZERO;
                rows.add(new Object[]{desc, amt});
            }
        }

        // Sum all in-clinic medicines into one row
        if (medicines != null && !medicines.isEmpty()) {
            java.math.BigDecimal medTotal = java.math.BigDecimal.ZERO;
            for (com.hms.entity.BillingMedicine med : medicines) {
                if (med.getAmount() != null) medTotal = medTotal.add(med.getAmount());
            }
            if (medTotal.compareTo(java.math.BigDecimal.ZERO) > 0) {
                rows.add(new Object[]{"Medicines by Hospital", medTotal});
            }
        }

        return rows;
    }

    private String convertNumberToWords(long number) {
        if (number == 0) return "Zero";
        String[] units = { "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
                           "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen" };
        String[] tens = { "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety" };

        if (number < 20) return units[(int) number];
        if (number < 100) return tens[(int) (number / 10)] + ((number % 10 != 0) ? " " + units[(int) (number % 10)] : "");
        if (number < 1000) return units[(int) (number / 100)] + " Hundred" + ((number % 100 != 0) ? " and " + convertNumberToWords(number % 100) : "");
        if (number < 100000) return convertNumberToWords(number / 1000) + " Thousand" + ((number % 1000 != 0) ? " " + convertNumberToWords(number % 1000) : "");
        if (number < 10000000) return convertNumberToWords(number / 100000) + " Lakh" + ((number % 100000 != 0) ? " " + convertNumberToWords(number % 100000) : "");
        return convertNumberToWords(number / 10000000) + " Crore" + ((number % 10000000 != 0) ? " " + convertNumberToWords(number % 10000000) : "");
    }

    public ByteArrayInputStream generatePrescriptionPdf(
            Hospital hospital,
            Doctor doctor,
            Patient patient,
            MedicalRecord medicalRecord,
            List<Prescription> prescriptions) {

        Document document = new Document(PageSize.A4, 36, 36, 48, 180);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            addPageBorder(writer);
            document.open();

            // 1. Standard Header
            addStyledHeader(document, hospital, "PRESCRIPTION");

            // 2. Metadata Section
            PdfPTable metaTable = new PdfPTable(2);
            metaTable.setWidthPercentage(100);
            metaTable.setSpacingBefore(10f);
            metaTable.setSpacingAfter(15f);

            PdfPTable leftCol = new PdfPTable(2);
            leftCol.setWidths(new float[]{1.2f, 2f});
            
            String recordId = (medicalRecord != null && medicalRecord.getId() != null) ? medicalRecord.getId().toString() : "-";
            String recordDate = (medicalRecord != null && medicalRecord.getCreatedAt() != null) 
                    ? medicalRecord.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")) 
                    : "-";
            String patId = (patient != null && patient.getCustomId() != null) ? patient.getCustomId() : "-";
            String patAgeGender = (patient != null) ? patient.getAge() + " / " + patient.getGender() : "-";

            addMetaRow(leftCol, "Prescription#", recordId);
            addMetaRow(leftCol, "Date", recordDate);
            addMetaRow(leftCol, "Patient ID", patId);
            addMetaRow(leftCol, "Age / Gender", patAgeGender);

            PdfPTable rightCol = new PdfPTable(2);
            rightCol.setWidths(new float[]{1.2f, 2f});
            
            String docName = (doctor != null && doctor.getName() != null) ? doctor.getName() : "Unknown";
            String docSpec = (doctor != null && doctor.getSpecialization() != null) ? doctor.getSpecialization() : "-";
            String patName = (patient != null && patient.getName() != null) ? patient.getName() : "Unknown";
            String patAddress = (patient != null && patient.getAddress() != null) ? patient.getAddress() : "-";

            addMetaRow(rightCol, "Doctor", "Dr. " + docName);
            addMetaRow(rightCol, "Spec.", docSpec);
            addMetaRow(rightCol, "Bill To:", patName);
            addMetaRow(rightCol, "Address", patAddress);

            PdfPCell leftCell = new PdfPCell(leftCol);
            leftCell.setBorder(Rectangle.NO_BORDER);
            metaTable.addCell(leftCell);

            PdfPCell rightCell = new PdfPCell(rightCol);
            rightCell.setBorder(Rectangle.NO_BORDER);
            metaTable.addCell(rightCell);

            document.add(metaTable);

            // 3. Vitals / Clinical info
            Paragraph line = new Paragraph();
            line.add(new LineSeparator(1f, 100, Color.LIGHT_GRAY, Element.ALIGN_CENTER, -2));
            document.add(line);
            document.add(new Paragraph("\n"));

            if (medicalRecord.getSymptoms() != null && !medicalRecord.getSymptoms().isEmpty()) {
                Paragraph sym = new Paragraph("Symptoms: ", SMALL_BOLD_FONT);
                sym.add(new Chunk(medicalRecord.getSymptoms(), NORMAL_FONT));
                document.add(sym);
            }
            if (medicalRecord.getDiagnosis() != null && !medicalRecord.getDiagnosis().isEmpty()) {
                Paragraph dia = new Paragraph("Diagnosis: ", SMALL_BOLD_FONT);
                dia.add(new Chunk(medicalRecord.getDiagnosis(), NORMAL_FONT));
                document.add(dia);
            }
            if (medicalRecord.getTreatmentNotes() != null && !medicalRecord.getTreatmentNotes().isEmpty()) {
                Paragraph nts = new Paragraph("Clinical Notes: ", SMALL_BOLD_FONT);
                nts.add(new Chunk(medicalRecord.getTreatmentNotes(), NORMAL_FONT));
                document.add(nts);
            }
            document.add(new Paragraph("\n"));

            // 4. Main Medication Table
            PdfPTable rxTable = new PdfPTable(5);
            rxTable.setWidthPercentage(100);
            rxTable.setWidths(new float[]{3f, 1f, 1f, 1.5f, 2.5f});

            addTableHeaderCell(rxTable, "Medicine");
            addTableHeaderCell(rxTable, "Dosage");
            addTableHeaderCell(rxTable, "Freq");
            addTableHeaderCell(rxTable, "Duration");
            addTableHeaderCell(rxTable, "Instruction");

            if (prescriptions != null && !prescriptions.isEmpty()) {
                for (Prescription p : prescriptions) {
                    addTableCell(rxTable, p.getMedicineName(), false);
                    addTableCell(rxTable, p.getDosage(), false);
                    addTableCell(rxTable, p.getFrequency(), false);
                    addTableCell(rxTable, p.getDuration(), false);
                    addTableCell(rxTable, p.getInstructions(), false);
                }
            } else {
                PdfPCell cell = new PdfPCell(new Phrase("No medications prescribed.", NORMAL_FONT));
                cell.setColspan(5);
                cell.setPadding(8f);
                rxTable.addCell(cell);
            }
            document.add(rxTable);

            // 5. Follow Up Section
            if (medicalRecord.getFollowUpDate() != null) {
                document.add(new Paragraph("\n"));
                Paragraph flw = new Paragraph("Follow Up Date: ", SUBTITLE_FONT);
                flw.add(new Chunk(medicalRecord.getFollowUpDate().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")), NORMAL_FONT));
                document.add(flw);
            }

            // 6. Remittance / Footer style
            addFixedFooter(writer, patient, medicalRecord.getId().toString(), "Prescription Authorized Signature");

            document.close();

        } catch (DocumentException e) {
            throw new RuntimeException("Error generating PDF", e);
        }

        return new ByteArrayInputStream(out.toByteArray());
    }

    public ByteArrayInputStream generateBillingReceiptPdf(Hospital hospital, Patient patient, Billing billing) {
        // Redesigned PDF to look exactly like a premium pre-printed receipt with a fixed bottom section
        Document document = new Document(PageSize.A4, 36, 36, 36, 180);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            addPageBorder(writer);
            document.open();

            // Resolve Doctor associated with this bill
            Doctor doctor = null;
            if (billing.getDoctorId() != null) {
                doctor = doctorRepository.findById(billing.getDoctorId()).orElse(null);
            }
            if (doctor == null && hospital != null) {
                java.util.List<Doctor> doctors = doctorRepository.findByHospitalIdAndIsActiveTrueOrderByCreatedAtDesc(hospital.getId());
                if (doctors != null && !doctors.isEmpty()) {
                    doctor = doctors.get(0);
                }
            }

            // Resolve Diagnosis dynamically from related MedicalRecord
            String diagnosis = "-";
            if (billing.getOpdId() != null) {
                java.util.Optional<com.hms.entity.MedicalRecord> record = medicalRecordRepository.findByOpdId(billing.getOpdId());
                if (record.isPresent() && record.get().getDiagnosis() != null && !record.get().getDiagnosis().isEmpty()) {
                    diagnosis = record.get().getDiagnosis();
                }
            } else if (billing.getIpdAdmissionId() != null) {
                java.util.List<com.hms.entity.MedicalRecord> records = medicalRecordRepository.findByIpdAdmissionIdOrderByCreatedAtDesc(billing.getIpdAdmissionId());
                if (records != null && !records.isEmpty() && records.get(0).getDiagnosis() != null && !records.get(0).getDiagnosis().isEmpty()) {
                    diagnosis = records.get(0).getDiagnosis();
                }
            } else if (billing.getAppointmentId() != null) {
                java.util.Optional<com.hms.entity.MedicalRecord> record = medicalRecordRepository.findByAppointmentId(billing.getAppointmentId());
                if (record.isPresent() && record.get().getDiagnosis() != null && !record.get().getDiagnosis().isEmpty()) {
                    diagnosis = record.get().getDiagnosis();
                }
            }

            // 1. Premium Letterhead Header Table
            PdfPTable headerTable = new PdfPTable(3);
            headerTable.setWidthPercentage(100);
            headerTable.setWidths(new float[]{1.2f, 4f, 1f});

            // Logo Box
            PdfPCell logoCell = new PdfPCell();
            logoCell.setBorder(Rectangle.NO_BORDER);
            logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            
            if (hospital != null && hospital.getLogoUrl() != null && !hospital.getLogoUrl().trim().isEmpty()) {
                try {
                    com.lowagie.text.Image logoImg = com.lowagie.text.Image.getInstance(new java.net.URL(hospital.getLogoUrl().trim()));
                    logoImg.scaleToFit(60f, 60f);
                    logoImg.setAlignment(Element.ALIGN_CENTER);
                    logoCell.addElement(logoImg);
                } catch (Exception e) {
                    drawTextFallbackLogo(logoCell, hospital);
                }
            } else {
                drawTextFallbackLogo(logoCell, hospital);
            }
            headerTable.addCell(logoCell);

            // Hospital Details Center
            PdfPCell centerHeader = new PdfPCell();
            centerHeader.setBorder(Rectangle.NO_BORDER);
            centerHeader.setHorizontalAlignment(Element.ALIGN_CENTER);
            
            String parentOrgText = (hospital != null && hospital.getParentOrganization() != null && !hospital.getParentOrganization().trim().isEmpty())
                    ? hospital.getParentOrganization().trim()
                    : "";
            if (!parentOrgText.isEmpty()) {
                Paragraph gmfText = new Paragraph(parentOrgText, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.BOLD, NAVY_BLUE));
                gmfText.setAlignment(Element.ALIGN_CENTER);
                centerHeader.addElement(gmfText);
            }
            String rawHospName = (hospital != null && hospital.getName() != null) ? hospital.getName().toUpperCase() : "HOSPITAL";
            Paragraph hospText = new Paragraph(rawHospName, RED_TITLE_FONT);
            hospText.setAlignment(Element.ALIGN_CENTER);
            String addressText = (hospital != null && hospital.getAddress() != null) ? hospital.getAddress() : "\"Amrutwel\" Near water Tank, Shikrapur, Tal. Shirur, Dist. Pune.";
            Paragraph addrText = new Paragraph(addressText, FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, Color.DARK_GRAY));
            addrText.setAlignment(Element.ALIGN_CENTER);

            centerHeader.addElement(hospText);
            centerHeader.addElement(addrText);
            headerTable.addCell(centerHeader);

            // Right Caduceus Space (Empty / Symmetric padding)
            PdfPCell rightHeader = new PdfPCell();
            rightHeader.setBorder(Rectangle.NO_BORDER);
            headerTable.addCell(rightHeader);

            document.add(headerTable);

            // 2. Doctor credentials block
            PdfPTable docTable = new PdfPTable(2);
            docTable.setWidthPercentage(100);
            docTable.setWidths(new float[]{3.5f, 2f});
            docTable.setSpacingBefore(6f);

            String docName = (doctor != null && doctor.getName() != null) ? doctor.getName().toUpperCase() : "B. B. INGALE";
            String docSpec = (doctor != null && doctor.getSpecialization() != null) ? doctor.getSpecialization() : "M.B.B.S, Diploma in Industrial Health";
            String docPhone = (doctor != null && doctor.getPhone() != null) ? doctor.getPhone() : "9850914919";

            PdfPCell docLeft = new PdfPCell();
            docLeft.setBorder(Rectangle.NO_BORDER);
            Paragraph pName = new Paragraph("Dr. " + docName, RED_DOCTOR_FONT);
            Paragraph pSpec = new Paragraph("(" + docSpec + ")", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Font.NORMAL, Color.DARK_GRAY));
            docLeft.addElement(pName);
            docLeft.addElement(pSpec);
            docTable.addCell(docLeft);

            PdfPCell docRight = new PdfPCell();
            docRight.setBorder(Rectangle.NO_BORDER);
            Paragraph pPhone = new Paragraph("Mob. : " + docPhone, SMALL_BOLD_FONT);
            pPhone.setAlignment(Element.ALIGN_RIGHT);
            docRight.addElement(pPhone);
            docTable.addCell(docRight);

            document.add(docTable);

            // Separator Line
            Paragraph borderLine = new Paragraph();
            borderLine.add(new LineSeparator(1.5f, 100, Color.BLACK, Element.ALIGN_CENTER, -1));
            document.add(borderLine);
            document.add(new Paragraph("\n"));

            // 3. Patient Details Table (with under-lined input style)
            PdfPTable patientTable = new PdfPTable(2);
            patientTable.setWidthPercentage(100);
            patientTable.setWidths(new float[]{3f, 2f});
            patientTable.setSpacingAfter(10f);

            // No & Date
            PdfPCell noCell = new PdfPCell();
            noCell.setBorder(Rectangle.NO_BORDER);
            Paragraph noPara = new Paragraph("No. : ", NORMAL_FONT);
            String rawCustomId = (billing != null && billing.getCustomId() != null) ? billing.getCustomId() : "-";
            noPara.add(new Chunk(rawCustomId, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Font.BOLD, PRIMARY_RED)));
            noCell.addElement(noPara);
            patientTable.addCell(noCell);

            PdfPCell dateCell = new PdfPCell();
            dateCell.setBorder(Rectangle.NO_BORDER);
            String rawDate = (billing != null && billing.getCreatedAt() != null) 
                    ? billing.getCreatedAt().format(DateTimeFormatter.ofPattern("dd / MM / yyyy")) 
                    : "-";
            Paragraph datePara = new Paragraph("Date :  " + rawDate, NORMAL_FONT);
            dateCell.addElement(datePara);
            patientTable.addCell(dateCell);

            // Patient Name (Row 2, colspan 2)
            PdfPCell nameCell = new PdfPCell();
            nameCell.setColspan(2);
            nameCell.setBorder(Rectangle.NO_BORDER);
            nameCell.setPaddingTop(4f);
            nameCell.setPaddingBottom(4f);
            Paragraph namePara = new Paragraph("Name of Patient :  ", NORMAL_FONT);
            String patName = (patient != null && patient.getName() != null) ? patient.getName().toUpperCase() : "PATIENT";
            namePara.add(new Chunk(patName, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.BOLD, Color.BLACK)));
            nameCell.addElement(namePara);
            nameCell.setBorder(Rectangle.BOTTOM);
            nameCell.setBorderColor(Color.LIGHT_GRAY);
            nameCell.setBorderWidth(0.5f);
            patientTable.addCell(nameCell);

            // Address (Row 3, colspan 2)
            PdfPCell addrCell = new PdfPCell();
            addrCell.setColspan(2);
            addrCell.setBorder(Rectangle.NO_BORDER);
            addrCell.setPaddingTop(4f);
            addrCell.setPaddingBottom(4f);
            Paragraph addrPara = new Paragraph("Address :  ", NORMAL_FONT);
            String patAddr = (patient != null && patient.getAddress() != null) ? patient.getAddress() : "-";
            addrPara.add(new Chunk(patAddr, NORMAL_FONT));
            addrCell.addElement(addrPara);
            addrCell.setBorder(Rectangle.BOTTOM);
            addrCell.setBorderColor(Color.LIGHT_GRAY);
            addrCell.setBorderWidth(0.5f);
            patientTable.addCell(addrCell);

            // Diagnosis (Row 4, colspan 2)
            PdfPCell diagCell = new PdfPCell();
            diagCell.setColspan(2);
            diagCell.setBorder(Rectangle.NO_BORDER);
            diagCell.setPaddingTop(4f);
            diagCell.setPaddingBottom(4f);
            Paragraph diagPara = new Paragraph("Diagnosis :  ", NORMAL_FONT);
            diagPara.add(new Chunk(diagnosis, NORMAL_FONT));
            diagCell.addElement(diagPara);
            diagCell.setBorder(Rectangle.BOTTOM);
            diagCell.setBorderColor(Color.LIGHT_GRAY);
            diagCell.setBorderWidth(0.5f);
            patientTable.addCell(diagCell);

            document.add(patientTable);

            // Fetch details to support itemization
            java.util.List<com.hms.entity.BillingItem> items = billingItemRepository != null ? billingItemRepository.findByBillingId(billing.getId()) : null;
            java.util.List<com.hms.entity.BillingMedicine> medicines = billingMedicineRepository != null ? billingMedicineRepository.findByBillingId(billing.getId()) : null;
            java.util.List<com.hms.entity.BillingPayment> payments = billingPaymentRepository != null ? billingPaymentRepository.findByBillingId(billing.getId()) : null;

            // Recalculate totals
            java.math.BigDecimal totalAmt = java.math.BigDecimal.ZERO;
            if (items != null) {
                for (com.hms.entity.BillingItem it : items) {
                    if (it.getAmount() != null) totalAmt = totalAmt.add(it.getAmount());
                }
            }
            if (medicines != null) {
                for (com.hms.entity.BillingMedicine med : medicines) {
                    if (med.getAmount() != null) totalAmt = totalAmt.add(med.getAmount());
                }
            }
            if (totalAmt.compareTo(java.math.BigDecimal.ZERO) == 0 && (items == null || items.isEmpty()) && (medicines == null || medicines.isEmpty())) {
                totalAmt = billing.getAmount() != null ? billing.getAmount() : java.math.BigDecimal.ZERO;
            }

            java.math.BigDecimal paidAmt = java.math.BigDecimal.ZERO;
            if (payments != null && !payments.isEmpty()) {
                for (com.hms.entity.BillingPayment pay : payments) {
                    if (pay.getAmount() != null) paidAmt = paidAmt.add(pay.getAmount());
                }
            } else {
                if ("PAID".equalsIgnoreCase(billing.getPaymentStatus())) {
                    paidAmt = totalAmt;
                }
            }

            java.math.BigDecimal balance = totalAmt.subtract(paidAmt);

            // 4. Dynamic Charges Table
            java.util.List<Object[]> chargeRows = buildDynamicChargeRows(items, medicines);

            // Fallback: if no items and no medicines, but bill has amount, show one row
            if (chargeRows.isEmpty() && totalAmt.compareTo(java.math.BigDecimal.ZERO) > 0) {
                String fallbackDesc = "OPD".equalsIgnoreCase(billing.getBillingType()) ? "Consultation Charges" : "Service Charges";
                chargeRows.add(new Object[]{fallbackDesc, totalAmt});
            }

            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{0.6f, 4.4f, 1.2f, 0.5f});

            // Table Headers
            addTableHeaderCell(table, "S.No.");
            addTableHeaderCell(table, "Fees For");
            addTableHeaderCell(table, "Amount Rs.");
            addTableHeaderCell(table, "Ps.");

            // Render actual charge rows
            int rowIdx = 1;
            for (Object[] row : chargeRows) {
                String desc = (String) row[0];
                java.math.BigDecimal val = (java.math.BigDecimal) row[1];
                String rsStr = "";
                String psStr = "";
                if (val != null && val.compareTo(java.math.BigDecimal.ZERO) > 0) {
                    long rupees = val.longValue();
                    int paise = val.subtract(java.math.BigDecimal.valueOf(rupees)).multiply(java.math.BigDecimal.valueOf(100)).intValue();
                    rsStr = String.valueOf(rupees);
                    psStr = paise == 0 ? "00" : String.format("%02d", paise);
                }
                addTableCell(table, String.valueOf(rowIdx), false);
                addTableCell(table, desc, false);
                addTableCell(table, rsStr, true);
                addTableCell(table, psStr, true);
                rowIdx++;
            }

            document.add(table);

            // 5. Fixed Totals Table at the bottom
            PdfPTable fixedBottomTable = new PdfPTable(4);
            fixedBottomTable.setTotalWidth(523f);
            fixedBottomTable.setWidths(new float[]{0.6f, 4.4f, 1.2f, 0.5f});

            // Total Row
            long totalRupees = totalAmt.longValue();
            int totalPaise = totalAmt.subtract(java.math.BigDecimal.valueOf(totalRupees)).multiply(java.math.BigDecimal.valueOf(100)).intValue();
            String totalRsStr = String.valueOf(totalRupees);
            String totalPsStr = totalPaise == 0 ? "00" : String.format("%02d", totalPaise);

            PdfPCell totalLabelCell = new PdfPCell(new Phrase("Total", SMALL_BOLD_FONT));
            totalLabelCell.setColspan(2);
            totalLabelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalLabelCell.setPadding(5f);
            fixedBottomTable.addCell(totalLabelCell);
            PdfPCell totalRsCell = new PdfPCell(new Phrase(totalRsStr, SMALL_BOLD_FONT));
            totalRsCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalRsCell.setPadding(5f);
            fixedBottomTable.addCell(totalRsCell);
            PdfPCell totalPsCell = new PdfPCell(new Phrase(totalPsStr, SMALL_BOLD_FONT));
            totalPsCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalPsCell.setPadding(5f);
            fixedBottomTable.addCell(totalPsCell);

            // Received Row
            long paidRupees = paidAmt.longValue();
            int paidPaise = paidAmt.subtract(java.math.BigDecimal.valueOf(paidRupees)).multiply(java.math.BigDecimal.valueOf(100)).intValue();
            String paidRsStr = String.valueOf(paidRupees);
            String paidPsStr = paidPaise == 0 ? "00" : String.format("%02d", paidPaise);

            PdfPCell receivedLabelCell = new PdfPCell(new Phrase("Received", SMALL_BOLD_FONT));
            receivedLabelCell.setColspan(2);
            receivedLabelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            receivedLabelCell.setPadding(5f);
            fixedBottomTable.addCell(receivedLabelCell);
            PdfPCell paidRsCell = new PdfPCell(new Phrase(paidRsStr, SMALL_BOLD_FONT));
            paidRsCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            paidRsCell.setPadding(5f);
            fixedBottomTable.addCell(paidRsCell);
            PdfPCell paidPsCell = new PdfPCell(new Phrase(paidPsStr, SMALL_BOLD_FONT));
            paidPsCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            paidPsCell.setPadding(5f);
            fixedBottomTable.addCell(paidPsCell);

            // Balance Row
            long balRupees = balance.longValue();
            int balPaise = balance.subtract(java.math.BigDecimal.valueOf(balRupees)).multiply(java.math.BigDecimal.valueOf(100)).intValue();
            String balRsStr = String.valueOf(balRupees);
            String balPsStr = balPaise == 0 ? "00" : String.format("%02d", balPaise);

            PdfPCell balanceLabelCell = new PdfPCell(new Phrase("Balance", SMALL_BOLD_FONT));
            balanceLabelCell.setColspan(2);
            balanceLabelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            balanceLabelCell.setPadding(5f);
            fixedBottomTable.addCell(balanceLabelCell);
            PdfPCell balRsCell = new PdfPCell(new Phrase(balRsStr, SMALL_BOLD_FONT));
            balRsCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            balRsCell.setPadding(5f);
            fixedBottomTable.addCell(balRsCell);
            PdfPCell balPsCell = new PdfPCell(new Phrase(balPsStr, SMALL_BOLD_FONT));
            balPsCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            balPsCell.setPadding(5f);
            fixedBottomTable.addCell(balPsCell);

            fixedBottomTable.writeSelectedRows(0, -1, 36, 175, writer.getDirectContent());

            // 6. Fixed Signature / Remittance Table at the bottom
            PdfPTable footerTable = new PdfPTable(2);
            footerTable.setTotalWidth(523f);
            footerTable.setWidths(new float[]{3.5f, 2.5f});

            PdfPCell footerLeft = new PdfPCell();
            footerLeft.setBorder(Rectangle.NO_BORDER);
            Paragraph recPara = new Paragraph("Received Rs. :  ", NORMAL_FONT);
            if (paidAmt.compareTo(java.math.BigDecimal.ZERO) > 0) {
                recPara.add(new Chunk(convertNumberToWords(paidRupees) + " Only", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.BOLD, Color.BLACK)));
            } else {
                recPara.add(new Chunk("_______________________________________", NORMAL_FONT));
            }
            footerLeft.addElement(recPara);
            footerTable.addCell(footerLeft);

            PdfPCell footerRight = new PdfPCell();
            footerRight.setBorder(Rectangle.NO_BORDER);
            footerRight.setHorizontalAlignment(Element.ALIGN_RIGHT);
            
            Paragraph forHospital = new Paragraph("For " + hospital.getName(), SMALL_BOLD_FONT);
            forHospital.setAlignment(Element.ALIGN_RIGHT);
            
            Paragraph sigLine = new Paragraph("\n\n\n___________________________\nAuthorized Signature", FOOTER_FONT);
            sigLine.setAlignment(Element.ALIGN_RIGHT);
            
            footerRight.addElement(forHospital);
            footerRight.addElement(sigLine);
            footerTable.addCell(footerRight);

            footerTable.writeSelectedRows(0, -1, 36, 110, writer.getDirectContent());

            document.close();

        } catch (DocumentException e) {
            throw new RuntimeException("Error generating PDF", e);
        }

        return new ByteArrayInputStream(out.toByteArray());
    }

    public ByteArrayInputStream generatePharmacySaleReceiptPdf(Hospital hospital, Patient patient, PharmacySale sale) {
        Document document = new Document(PageSize.A4, 36, 36, 48, 180);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            addPageBorder(writer);
            document.open();

            // 1. Standard Header
            addStyledHeader(document, hospital, "PHARMACY INVOICE");

            // 2. Metadata Table
            PdfPTable metaTable = new PdfPTable(2);
            metaTable.setWidthPercentage(100);
            metaTable.setSpacingBefore(10f);
            metaTable.setSpacingAfter(15f);

            PdfPTable leftCol = new PdfPTable(2);
            leftCol.setWidths(new float[]{1.2f, 2f});
            addMetaRow(leftCol, "Invoice#", sale.getBillNumber());
            addMetaRow(leftCol, "Date", sale.getCreatedAt() != null ? sale.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")) : "-");
            addMetaRow(leftCol, "Patient ID", "-");
            addMetaRow(leftCol, "Sale Type", "WALK-IN");

            PdfPTable rightCol = new PdfPTable(2);
            rightCol.setWidths(new float[]{1.2f, 2f});
            addMetaRow(rightCol, "Customer Name", sale.getPatientName() != null ? sale.getPatientName() : "Walk-in Patient");
            addMetaRow(rightCol, "Payment Method", sale.getPaymentMethod());
            addMetaRow(rightCol, "Status", sale.getPaymentStatus());

            PdfPCell leftCell = new PdfPCell(leftCol);
            leftCell.setBorder(Rectangle.NO_BORDER);
            metaTable.addCell(leftCell);

            PdfPCell rightCell = new PdfPCell(rightCol);
            rightCell.setBorder(Rectangle.NO_BORDER);
            metaTable.addCell(rightCell);

            document.add(metaTable);

            // 3. Line separator
            Paragraph line = new Paragraph();
            line.add(new LineSeparator(1f, 100, Color.LIGHT_GRAY, Element.ALIGN_CENTER, -2));
            document.add(line);
            document.add(new Paragraph("\n"));

            // 4. Main Medicine Items Table
            PdfPTable itemsTable = new PdfPTable(7);
            itemsTable.setWidthPercentage(100);
            itemsTable.setWidths(new float[]{0.5f, 3.5f, 1.5f, 1.2f, 1.2f, 1.2f, 1.5f});

            addTableHeaderCell(itemsTable, "Sr#");
            addTableHeaderCell(itemsTable, "Medicine / Batch");
            addTableHeaderCell(itemsTable, "Expiry");
            addTableHeaderCell(itemsTable, "Qty");
            addTableHeaderCell(itemsTable, "Price");
            addTableHeaderCell(itemsTable, "GST %");
            addTableHeaderCell(itemsTable, "Total");

            int sr = 1;
            for (PharmacySaleItem item : sale.getItems()) {
                addTableCell(itemsTable, String.valueOf(sr++), false);
                
                // Medicine Name and Batch
                String medName = item.getMedicineBatch() != null && item.getMedicineBatch().getMedicine() != null 
                        ? item.getMedicineBatch().getMedicine().getMedicineName() 
                        : "Medicine #" + item.getMedicineId();
                String batchNum = item.getMedicineBatch() != null ? item.getMedicineBatch().getBatchNumber() : "N/A";
                addTableCell(itemsTable, medName + "\n(Batch: " + batchNum + ")", false);
                
                // Expiry Date
                String expDate = item.getMedicineBatch() != null && item.getMedicineBatch().getExpiryDate() != null
                        ? item.getMedicineBatch().getExpiryDate().toString()
                        : "-";
                addTableCell(itemsTable, expDate, false);
                
                // Qty, Unit Price, GST%, Total
                addTableCell(itemsTable, item.getQuantity() != null ? item.getQuantity().toString() : "0", true);
                addTableCell(itemsTable, item.getUnitPrice() != null ? String.format("%.2f", item.getUnitPrice()) : "0.00", true);
                addTableCell(itemsTable, item.getTaxPercentage() != null ? item.getTaxPercentage().toString() + "%" : "0%", true);
                addTableCell(itemsTable, item.getTotalAmount() != null ? "INR " + String.format("%.2f", item.getTotalAmount()) : "INR 0.00", true);
            }

            document.add(itemsTable);
            document.add(new Paragraph("\n"));

            // 5. Total Calculations (Right Aligned - Fixed Position)
            PdfPTable summaryTable = new PdfPTable(2);
            summaryTable.setTotalWidth(200f);
            summaryTable.setWidths(new float[]{1.5f, 1.5f});

            addSummaryRow(summaryTable, "Sub Total:", sale.getSubtotal() != null ? "INR " + String.format("%.2f", sale.getSubtotal()) : "INR 0.00");
            addSummaryRow(summaryTable, "Discount:", sale.getDiscountAmount() != null ? "- INR " + String.format("%.2f", sale.getDiscountAmount()) : "INR 0.00");
            addSummaryRow(summaryTable, "GST (Tax):", sale.getTaxAmount() != null ? "+ INR " + String.format("%.2f", sale.getTaxAmount()) : "INR 0.00");
            addSummaryRow(summaryTable, "Net Payable:", sale.getNetAmount() != null ? "INR " + String.format("%.2f", sale.getNetAmount()) : "INR 0.00");

            summaryTable.writeSelectedRows(0, -1, 359, 175, writer.getDirectContent());

            // 6. Signature section (Fixed Position)
            PdfPTable fTable = new PdfPTable(2);
            fTable.setTotalWidth(523f);
            fTable.setWidths(new float[]{3.5f, 2.5f});
            
            // Remittance section
            PdfPCell remCell = new PdfPCell();
            remCell.setBorder(Rectangle.NO_BORDER);
            remCell.addElement(new Paragraph("SUMMARY / REMITTANCE", SMALL_BOLD_FONT));
            
            PdfPTable subRem = new PdfPTable(2);
            subRem.setWidthPercentage(100);
            subRem.setSpacingBefore(5f);
            subRem.setWidths(new float[]{1.2f, 2f});
            addMetaRow(subRem, "Customer Name", sale.getPatientName() != null ? sale.getPatientName() : "Walk-in Patient");
            addMetaRow(subRem, "Invoice Ref#", sale.getBillNumber());
            remCell.addElement(subRem);
            fTable.addCell(remCell);

            // Sign line section
            PdfPCell sigCell = new PdfPCell();
            sigCell.setBorder(Rectangle.NO_BORDER);
            sigCell.setVerticalAlignment(Element.ALIGN_BOTTOM);
            Paragraph sig = new Paragraph("\n\n______________________\nPharmacist Signature", FOOTER_FONT);
            sig.setAlignment(Element.ALIGN_RIGHT);
            sigCell.addElement(sig);
            fTable.addCell(sigCell);

            fTable.writeSelectedRows(0, -1, 36, 110, writer.getDirectContent());

            document.close();

        } catch (DocumentException e) {
            throw new RuntimeException("Error generating Pharmacy Invoice PDF", e);
        }

        return new ByteArrayInputStream(out.toByteArray());
    }

    private void addSummaryRow(PdfPTable table, String label, String value) {
        PdfPCell lCell = new PdfPCell(new Phrase(label, SMALL_BOLD_FONT));
        lCell.setBorder(Rectangle.NO_BORDER);
        lCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        lCell.setPaddingBottom(5f);
        table.addCell(lCell);

        PdfPCell vCell = new PdfPCell(new Phrase(value, SMALL_BOLD_FONT));
        vCell.setBorder(Rectangle.NO_BORDER);
        vCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        vCell.setPaddingBottom(5f);
        table.addCell(vCell);
    }

    private void addFooterForWalkin(Document document, Patient patient, String refNum, String sigLabel) throws DocumentException {
        // Spacer
        for (int i = 0; i < 3; i++) document.add(new Paragraph("\n"));

        // Thick bottom gray bar mimicking remittance
        Paragraph footerBar = new Paragraph();
        footerBar.add(new LineSeparator(8f, 100, Color.DARK_GRAY, Element.ALIGN_CENTER, -2));
        document.add(footerBar);

        // Spacer
        for (int i = 0; i < 2; i++) document.add(new Paragraph("\n"));

        PdfPTable fTable = new PdfPTable(2);
        fTable.setWidthPercentage(100);
        fTable.setSpacingBefore(10f);

        // Remittance section
        PdfPCell remCell = new PdfPCell();
        remCell.setBorder(Rectangle.NO_BORDER);
        remCell.addElement(new Paragraph("SUMMARY / REMITTANCE", SMALL_BOLD_FONT));
        
        PdfPTable subRem = new PdfPTable(2);
        subRem.setWidthPercentage(100);
        subRem.setSpacingBefore(5f);
        addMetaRow(subRem, "Customer Name", patient != null ? patient.getName() : "Walk-in Patient");
        addMetaRow(subRem, "Invoice Ref#", refNum);
        remCell.addElement(subRem);
        fTable.addCell(remCell);

        // Sign line section
        PdfPCell sigCell = new PdfPCell();
        sigCell.setBorder(Rectangle.NO_BORDER);
        sigCell.setVerticalAlignment(Element.ALIGN_BOTTOM);
        Paragraph sig = new Paragraph("\n\n______________________\n" + sigLabel, FOOTER_FONT);
        sig.setAlignment(Element.ALIGN_RIGHT);
        sigCell.addElement(sig);
        fTable.addCell(sigCell);

        document.add(fTable);
    }

    /**
     * Shared logic: Generates the corporate header at top of doc
     */
    private void addStyledHeader(Document document, Hospital hospital, String title) throws DocumentException {
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{3f, 1.5f});

        // Left side: Name & Address
        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        String hospitalName = (hospital != null && hospital.getName() != null) ? hospital.getName().toUpperCase() : "HOSPITAL";
        left.addElement(new Paragraph(hospitalName, TITLE_FONT));
        String addr = (hospital != null && hospital.getAddress() != null) ? hospital.getAddress() : "Hospital Location Address Unavailable";
        left.addElement(new Paragraph(addr, NORMAL_FONT));
        headerTable.addCell(left);

        // Right side: Contacts
        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setVerticalAlignment(Element.ALIGN_BOTTOM);

        PdfPTable contactSubTable = new PdfPTable(2);
        contactSubTable.setWidths(new float[]{1f, 2.5f});
        String phoneVal = (hospital != null && hospital.getPhone() != null) ? hospital.getPhone() : "-";
        addHeaderContactRow(contactSubTable, "Phone:", phoneVal);
        String emailDomain = (hospital != null && hospital.getName() != null) ? hospital.getName().toLowerCase().replaceAll("\\s+", "") : "hospital";
        addHeaderContactRow(contactSubTable, "E-mail:", "support@" + emailDomain + ".com");
        right.addElement(contactSubTable);
        headerTable.addCell(right);

        document.add(headerTable);
        document.add(new Paragraph("\n"));

        // The Big Gray Italic Title
        Paragraph subtitlePara = new Paragraph(title, SUBTITLE_FONT);
        subtitlePara.setSpacingAfter(10f);
        document.add(subtitlePara);
    }

    private void addHeaderContactRow(PdfPTable table, String label, String value) {
        PdfPCell l = new PdfPCell(new Phrase(label, FOOTER_FONT));
        l.setBorder(Rectangle.NO_BORDER);
        l.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(l);

        PdfPCell v = new PdfPCell(new Phrase(value, NORMAL_FONT));
        v.setBorder(Rectangle.NO_BORDER);
        table.addCell(v);
    }

    /**
     * Helper to add a gray key and black value inside metadata grid
     */
    private void addMetaRow(PdfPTable table, String label, String value) {
        PdfPCell lCell = new PdfPCell(new Phrase(label, LABEL_FONT));
        lCell.setBorder(Rectangle.NO_BORDER);
        lCell.setPaddingBottom(5f);
        table.addCell(lCell);

        PdfPCell vCell = new PdfPCell(new Phrase(value != null ? value : "", VALUE_FONT));
        vCell.setBorder(Rectangle.NO_BORDER);
        vCell.setPaddingBottom(5f);
        table.addCell(vCell);
    }

    /**
     * Styles a black header cell for main data table
     */
    private void addTableHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, TABLE_HEADER_FONT));
        cell.setBackgroundColor(Color.BLACK);
        cell.setPaddingTop(6f);
        cell.setPaddingBottom(6f);
        cell.setPaddingLeft(4f);
        cell.setBorder(Rectangle.NO_BORDER);
        table.addCell(cell);
    }

    private void addTableCell(PdfPTable table, String text, boolean alignRight) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", NORMAL_FONT));
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(Color.LIGHT_GRAY);
        cell.setPaddingTop(6f);
        cell.setPaddingBottom(6f);
        cell.setPaddingLeft(4f);
        if (alignRight) {
            cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            cell.setPaddingRight(6f);
        }
        table.addCell(cell);
    }

    private void addFooter(Document document, Patient patient, String refNum, String sigLabel) throws DocumentException {
        // Spacer
        for (int i = 0; i < 5; i++) document.add(new Paragraph("\n"));

        // Thick bottom gray bar mimicking remittance
        Paragraph footerBar = new Paragraph();
        footerBar.add(new LineSeparator(8f, 100, Color.DARK_GRAY, Element.ALIGN_CENTER, -2));
        document.add(footerBar);

        // Spacer
        for (int i = 0; i < 2; i++) document.add(new Paragraph("\n"));

        PdfPTable fTable = new PdfPTable(2);
        fTable.setWidthPercentage(100);
        fTable.setSpacingBefore(10f);

        // Remittance section
        PdfPCell remCell = new PdfPCell();
        remCell.setBorder(Rectangle.NO_BORDER);
        remCell.addElement(new Paragraph("SUMMARY / REMITTANCE", SMALL_BOLD_FONT));
        
        PdfPTable subRem = new PdfPTable(2);
        subRem.setWidthPercentage(100);
        subRem.setSpacingBefore(5f);
        String patName = (patient != null && patient.getName() != null) ? patient.getName() : "Unknown";
        addMetaRow(subRem, "Patient Name", patName);
        addMetaRow(subRem, "Ref No#", refNum);
        remCell.addElement(subRem);
        fTable.addCell(remCell);

        // Sign line section
        PdfPCell sigCell = new PdfPCell();
        sigCell.setBorder(Rectangle.NO_BORDER);
        sigCell.setVerticalAlignment(Element.ALIGN_BOTTOM);
        Paragraph sig = new Paragraph("\n\n______________________\n" + sigLabel, FOOTER_FONT);
        sig.setAlignment(Element.ALIGN_RIGHT);
        sigCell.addElement(sig);
        fTable.addCell(sigCell);

        document.add(fTable);
    }

    private void drawTextFallbackLogo(PdfPCell logoCell, Hospital hospital) {
        String initials = "HOSP";
        if (hospital != null && hospital.getName() != null) {
            String name = hospital.getName().trim();
            String[] parts = name.split("\\s+");
            if (parts.length >= 2) {
                initials = (parts[0].substring(0, Math.min(parts[0].length(), 1)) + parts[1].substring(0, Math.min(parts[1].length(), 1))).toUpperCase();
            } else if (name.length() > 0) {
                initials = name.substring(0, Math.min(name.length(), 4)).toUpperCase();
            }
        }
        
        PdfPTable logoBorderTable = new PdfPTable(1);
        logoBorderTable.setWidthPercentage(100);
        
        PdfPCell borderCell = new PdfPCell(new Phrase(initials, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Font.BOLD, PRIMARY_RED)));
        borderCell.setBorderColor(PRIMARY_RED);
        borderCell.setBorderWidth(1.5f);
        borderCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        borderCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        borderCell.setPadding(6f);
        
        logoBorderTable.addCell(borderCell);
        logoCell.addElement(logoBorderTable);
    }

    private void addPageBorder(PdfWriter writer) {
        writer.setPageEvent(new com.lowagie.text.pdf.PdfPageEventHelper() {
            @Override
            public void onEndPage(PdfWriter writer, Document document) {
                com.lowagie.text.pdf.PdfContentByte cb = writer.getDirectContent();
                
                // Draw outer border
                cb.setColorStroke(Color.BLACK);
                cb.setLineWidth(1.0f);
                float margin = 20f;
                float x1 = margin;
                float y1 = margin;
                float x2 = document.getPageSize().getWidth() - margin;
                float y2 = document.getPageSize().getHeight() - margin;
                cb.rectangle(x1, y1, x2 - x1, y2 - y1);
                cb.stroke();

                // Draw inner border
                cb.setLineWidth(0.5f);
                float innerMargin = 23f;
                cb.rectangle(innerMargin, innerMargin, document.getPageSize().getWidth() - 2 * innerMargin, document.getPageSize().getHeight() - 2 * innerMargin);
                cb.stroke();
            }
        });
    }

    private void addFixedFooter(PdfWriter writer, Patient patient, String refNum, String sigLabel) throws DocumentException {
        PdfPTable fTable = new PdfPTable(2);
        fTable.setTotalWidth(523f);
        fTable.setWidths(new float[]{3.5f, 2.5f});

        // Remittance section
        PdfPCell remCell = new PdfPCell();
        remCell.setBorder(Rectangle.NO_BORDER);
        remCell.addElement(new Paragraph("SUMMARY / REMITTANCE", SMALL_BOLD_FONT));
        
        PdfPTable subRem = new PdfPTable(2);
        subRem.setWidthPercentage(100);
        subRem.setSpacingBefore(5f);
        subRem.setWidths(new float[]{1.2f, 2f});
        String patName = (patient != null && patient.getName() != null) ? patient.getName() : "Unknown";
        addMetaRow(subRem, "Patient Name", patName);
        addMetaRow(subRem, "Ref No#", refNum);
        remCell.addElement(subRem);
        fTable.addCell(remCell);

        // Sign line section
        PdfPCell sigCell = new PdfPCell();
        sigCell.setBorder(Rectangle.NO_BORDER);
        sigCell.setVerticalAlignment(Element.ALIGN_BOTTOM);
        Paragraph sig = new Paragraph("\n\n______________________\n" + sigLabel, FOOTER_FONT);
        sig.setAlignment(Element.ALIGN_RIGHT);
        sigCell.addElement(sig);
        fTable.addCell(sigCell);

        fTable.writeSelectedRows(0, -1, 36, 110, writer.getDirectContent());
    }

    public ByteArrayInputStream generateMedicinesListPdf(
            Hospital hospital,
            Patient patient,
            String title,
            List<String[]> itemsList) {

        Document document = new Document(PageSize.A4, 36, 36, 48, 180);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            addPageBorder(writer);
            document.open();

            // 1. Standard Header
            addStyledHeader(document, hospital, title);

            // 2. Metadata Section
            PdfPTable metaTable = new PdfPTable(2);
            metaTable.setWidthPercentage(100);
            metaTable.setSpacingBefore(10f);
            metaTable.setSpacingAfter(15f);

            PdfPTable leftCol = new PdfPTable(2);
            leftCol.setWidths(new float[]{1.2f, 2f});
            
            String patId = (patient != null && patient.getCustomId() != null) ? patient.getCustomId() : "-";
            String patAgeGender = (patient != null) ? patient.getAge() + " / " + patient.getGender() : "-";
            String dateStr = java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy"));

            addMetaRow(leftCol, "Date", dateStr);
            addMetaRow(leftCol, "Patient ID", patId);
            addMetaRow(leftCol, "Age / Gender", patAgeGender);

            PdfPTable rightCol = new PdfPTable(2);
            rightCol.setWidths(new float[]{1.2f, 2f});
            
            String patName = (patient != null && patient.getName() != null) ? patient.getName() : "Unknown";
            String patAddress = (patient != null && patient.getAddress() != null) ? patient.getAddress() : "-";

            addMetaRow(rightCol, "Patient Name", patName);
            addMetaRow(rightCol, "Address", patAddress);

            PdfPCell leftCell = new PdfPCell(leftCol);
            leftCell.setBorder(Rectangle.NO_BORDER);
            metaTable.addCell(leftCell);

            PdfPCell rightCell = new PdfPCell(rightCol);
            rightCell.setBorder(Rectangle.NO_BORDER);
            metaTable.addCell(rightCell);

            document.add(metaTable);

            // Separator Line
            Paragraph line = new Paragraph();
            line.add(new LineSeparator(1f, 100, Color.LIGHT_GRAY, Element.ALIGN_CENTER, -2));
            document.add(line);
            document.add(new Paragraph("\n"));

            // 3. Main Medicines Table
            PdfPTable table = new PdfPTable(3);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1f, 6f, 2f});

            addTableHeaderCell(table, "S.No.");
            addTableHeaderCell(table, "Medicine / Item Description");
            addTableHeaderCell(table, "Quantity");

            if (itemsList != null && !itemsList.isEmpty()) {
                int sr = 1;
                for (String[] row : itemsList) {
                    addTableCell(table, String.valueOf(sr++), false);
                    addTableCell(table, row[0], false);
                    addTableCell(table, row[1], false);
                }
            } else {
                PdfPCell cell = new PdfPCell(new Phrase("No medicines or items recorded.", NORMAL_FONT));
                cell.setColspan(3);
                cell.setPadding(8f);
                table.addCell(cell);
            }
            document.add(table);

            // 4. Authorized Signature Block (fixed footer)
            addFixedFooter(writer, patient, "-", "Authorized Signature");

            document.close();

        } catch (DocumentException e) {
            throw new RuntimeException("Error generating Medicines List PDF", e);
        }

        return new ByteArrayInputStream(out.toByteArray());
    }

    public ByteArrayInputStream generateIpdPrescriptionPdf(
            Hospital hospital,
            Patient patient,
            IpdAdmission ipd,
            List<Prescription> prescriptions) {

        Document document = new Document(PageSize.A4, 36, 36, 48, 180);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            addPageBorder(writer);
            document.open();

            // 1. Standard Header
            addStyledHeader(document, hospital, "IPD PRESCRIPTION");

            // 2. Metadata Section
            PdfPTable metaTable = new PdfPTable(2);
            metaTable.setWidthPercentage(100);
            metaTable.setSpacingBefore(10f);
            metaTable.setSpacingAfter(15f);

            PdfPTable leftCol = new PdfPTable(2);
            leftCol.setWidths(new float[]{1.2f, 2f});
            
            String ipdNo = (ipd != null) ? ipd.getIpdNumber() : "-";
            String adDate = (ipd != null && ipd.getAdmissionDatetime() != null) 
                    ? ipd.getAdmissionDatetime().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")) 
                    : "-";
            String patId = (patient != null && patient.getCustomId() != null) ? patient.getCustomId() : "-";
            String patAgeGender = (patient != null) ? patient.getAge() + " / " + patient.getGender() : "-";

            addMetaRow(leftCol, "IPD Case#", ipdNo);
            addMetaRow(leftCol, "Admission Date", adDate);
            addMetaRow(leftCol, "Patient ID", patId);
            addMetaRow(leftCol, "Age / Gender", patAgeGender);

            PdfPTable rightCol = new PdfPTable(2);
            rightCol.setWidths(new float[]{1.2f, 2f});
            
            String patName = (patient != null && patient.getName() != null) ? patient.getName() : "Unknown";
            String patAddress = (patient != null && patient.getAddress() != null) ? patient.getAddress() : "-";
            String primaryDiag = (ipd != null && ipd.getPrimaryDiagnosis() != null) ? ipd.getPrimaryDiagnosis() : "-";

            addMetaRow(rightCol, "Patient Name", patName);
            addMetaRow(rightCol, "Address", patAddress);
            addMetaRow(rightCol, "Primary Diag", primaryDiag);

            PdfPCell leftCell = new PdfPCell(leftCol);
            leftCell.setBorder(Rectangle.NO_BORDER);
            metaTable.addCell(leftCell);

            PdfPCell rightCell = new PdfPCell(rightCol);
            rightCell.setBorder(Rectangle.NO_BORDER);
            metaTable.addCell(rightCell);

            document.add(metaTable);

            // Separator Line
            Paragraph line = new Paragraph();
            line.add(new LineSeparator(1f, 100, Color.LIGHT_GRAY, Element.ALIGN_CENTER, -2));
            document.add(line);
            document.add(new Paragraph("\n"));

            // 3. Main Medication Table
            PdfPTable rxTable = new PdfPTable(5);
            rxTable.setWidthPercentage(100);
            rxTable.setWidths(new float[]{3f, 1f, 1f, 1.5f, 2.5f});

            addTableHeaderCell(rxTable, "Medicine");
            addTableHeaderCell(rxTable, "Dosage");
            addTableHeaderCell(rxTable, "Freq");
            addTableHeaderCell(rxTable, "Duration");
            addTableHeaderCell(rxTable, "Instruction");

            if (prescriptions != null && !prescriptions.isEmpty()) {
                for (Prescription p : prescriptions) {
                    addTableCell(rxTable, p.getMedicineName(), false);
                    addTableCell(rxTable, p.getDosage(), false);
                    addTableCell(rxTable, p.getFrequency(), false);
                    addTableCell(rxTable, p.getDuration(), false);
                    addTableCell(rxTable, p.getInstructions(), false);
                }
            } else {
                PdfPCell cell = new PdfPCell(new Phrase("No medications prescribed.", NORMAL_FONT));
                cell.setColspan(5);
                cell.setPadding(8f);
                rxTable.addCell(cell);
            }
            document.add(rxTable);

            // 4. Fixed Footer Style
            addFixedFooter(writer, patient, ipdNo, "Prescription Authorized Signature");

            document.close();

        } catch (DocumentException e) {
            throw new RuntimeException("Error generating IPD Prescription PDF", e);
        }

        return new ByteArrayInputStream(out.toByteArray());
    }

    public ByteArrayInputStream generatePatientActivityPdf(
            Hospital hospital,
            java.time.LocalDate date,
            java.util.List<java.util.Map<String, Object>> activities) {

        Document document = new Document(PageSize.A4, 36, 36, 48, 120);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            addPageBorder(writer);
            document.open();

            // 1. Standard Header
            String dateStr = date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"));
            String title = "PATIENT ACTIVITY REPORT - " + dateStr;
            addStyledHeader(document, hospital, title);

            // 2. Main Activities Table
            // Columns: S.No., Patient ID, Patient Name, Type, Time, Doctor, Details
            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{0.6f, 1.2f, 2.0f, 1.3f, 1.1f, 2.0f, 1.8f});

            addTableHeaderCell(table, "Sr#");
            addTableHeaderCell(table, "Patient ID");
            addTableHeaderCell(table, "Patient Name");
            addTableHeaderCell(table, "Type");
            addTableHeaderCell(table, "Time");
            addTableHeaderCell(table, "Doctor Name");
            addTableHeaderCell(table, "Details");

            if (activities != null && !activities.isEmpty()) {
                int sr = 1;
                for (java.util.Map<String, Object> act : activities) {
                    addTableCell(table, String.valueOf(sr++), false);
                    addTableCell(table, (String) act.get("patientId"), false);
                    addTableCell(table, (String) act.get("patientName"), false);
                    addTableCell(table, (String) act.get("activityType"), false);
                    
                    // Format Time
                    String timeStr = "-";
                    Object timeObj = act.get("activityTime");
                    if (timeObj instanceof java.time.LocalDateTime) {
                        timeStr = ((java.time.LocalDateTime) timeObj).format(DateTimeFormatter.ofPattern("hh:mm a"));
                    } else if (timeObj != null) {
                        timeStr = timeObj.toString();
                    }
                    addTableCell(table, timeStr, false);
                    addTableCell(table, (String) act.get("doctorName"), false);
                    addTableCell(table, (String) act.get("details"), false);
                }
            } else {
                PdfPCell cell = new PdfPCell(new Phrase("No patient activities found for this date.", NORMAL_FONT));
                cell.setColspan(7);
                cell.setPadding(8f);
                table.addCell(cell);
            }
            document.add(table);

            // 3. Authorized Signature Block (fixed footer)
            addFixedFooter(writer, null, "-", "Report Generated By Admin");

            document.close();

        } catch (DocumentException e) {
            throw new RuntimeException("Error generating Patient Activity PDF", e);
        }

        return new ByteArrayInputStream(out.toByteArray());
    }
}
