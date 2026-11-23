package com.hms.service.pdf;

import com.hms.entity.*;
import com.hms.entity.pharmacy.MedicineBatch;
import com.hms.entity.pharmacy.PharmacySale;
import com.hms.entity.pharmacy.PharmacySaleItem;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

@Service
public class BillingPdfService {

    @Autowired
    private com.hms.repository.BillingItemRepository billingItemRepository;

    @Autowired
    private com.hms.repository.BillingMedicineRepository billingMedicineRepository;

    @Autowired
    private com.hms.repository.BillingPaymentRepository billingPaymentRepository;

    @Autowired
    private com.hms.repository.DoctorRepository doctorRepository;

    @Autowired
    private com.hms.repository.MedicalRecordRepository medicalRecordRepository;

    @Autowired
    private PdfLayoutHelper helper;

    public ByteArrayInputStream generateBillingReceiptPdf(Hospital hospital, Patient patient, Billing billing) {
        // Redesigned PDF to look exactly like a premium pre-printed receipt with a fixed bottom section
        Document document = new Document(PageSize.A4, 36, 36, 36, 180);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            helper.addPageBorder(writer);
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
                    helper.drawTextFallbackLogo(logoCell, hospital);
                }
            } else {
                helper.drawTextFallbackLogo(logoCell, hospital);
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
                Paragraph gmfText = new Paragraph(parentOrgText, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.BOLD, PdfLayoutHelper.NAVY_BLUE));
                gmfText.setAlignment(Element.ALIGN_CENTER);
                centerHeader.addElement(gmfText);
            }
            String rawHospName = (hospital != null && hospital.getName() != null) ? hospital.getName().toUpperCase() : "HOSPITAL";
            Paragraph hospText = new Paragraph(rawHospName, PdfLayoutHelper.RED_TITLE_FONT);
            hospText.setAlignment(Element.ALIGN_CENTER);
            String addressText = PdfLayoutHelper.hospitalAddress(hospital);
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

            // No doctor on the bill and none in the hospital: leave the block out. It used to fall
            // back to a real doctor's name and mobile number, printed on every tenant's receipts.
            if (doctor != null) {
                String docName = PdfLayoutHelper.doctorTitle(doctor.getName());
                String docSpec = doctor.getSpecialization() != null ? doctor.getSpecialization() : "";
                String docPhone = doctor.getPhone() != null ? doctor.getPhone() : "-";

                PdfPCell docLeft = new PdfPCell();
                docLeft.setBorder(Rectangle.NO_BORDER);
                Paragraph pName = new Paragraph(docName, PdfLayoutHelper.RED_DOCTOR_FONT);
                docLeft.addElement(pName);
                if (!docSpec.isBlank()) {
                    docLeft.addElement(new Paragraph("(" + docSpec + ")",
                            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Font.NORMAL, Color.DARK_GRAY)));
                }
                docTable.addCell(docLeft);

                PdfPCell docRight = new PdfPCell();
                docRight.setBorder(Rectangle.NO_BORDER);
                Paragraph pPhone = new Paragraph("Mob. : " + docPhone, PdfLayoutHelper.SMALL_BOLD_FONT);
                pPhone.setAlignment(Element.ALIGN_RIGHT);
                docRight.addElement(pPhone);
                docTable.addCell(docRight);

                document.add(docTable);
            }

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
            Paragraph noPara = new Paragraph("No. : ", PdfLayoutHelper.NORMAL_FONT);
            String rawCustomId = (billing != null && billing.getCustomId() != null) ? billing.getCustomId() : "-";
            noPara.add(new Chunk(rawCustomId, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Font.BOLD, PdfLayoutHelper.NAVY_BLUE)));
            noCell.addElement(noPara);
            patientTable.addCell(noCell);

            PdfPCell dateCell = new PdfPCell();
            dateCell.setBorder(Rectangle.NO_BORDER);
            String rawDate = (billing != null && billing.getCreatedAt() != null)
                    ? billing.getCreatedAt().format(DateTimeFormatter.ofPattern("dd / MM / yyyy"))
                    : "-";
            Paragraph datePara = new Paragraph("Date :  " + rawDate, PdfLayoutHelper.NORMAL_FONT);
            dateCell.addElement(datePara);
            patientTable.addCell(dateCell);

            // Patient Name (Row 2, colspan 2)
            PdfPCell nameCell = new PdfPCell();
            nameCell.setColspan(2);
            nameCell.setBorder(Rectangle.NO_BORDER);
            nameCell.setPaddingTop(4f);
            nameCell.setPaddingBottom(4f);
            Paragraph namePara = new Paragraph("Name of Patient :  ", PdfLayoutHelper.NORMAL_FONT);
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
            Paragraph addrPara = new Paragraph("Address :  ", PdfLayoutHelper.NORMAL_FONT);
            String patAddr = (patient != null && patient.getAddress() != null) ? patient.getAddress() : "-";
            addrPara.add(new Chunk(patAddr, PdfLayoutHelper.NORMAL_FONT));
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
            Paragraph diagPara = new Paragraph("Diagnosis :  ", PdfLayoutHelper.NORMAL_FONT);
            diagPara.add(new Chunk(diagnosis, PdfLayoutHelper.NORMAL_FONT));
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
            }

            // Discount / concession: Total stays the gross charges; net = total - discount.
            java.math.BigDecimal discountAmt = billing.getDiscountAmount() == null ? java.math.BigDecimal.ZERO
                    : billing.getDiscountAmount().max(java.math.BigDecimal.ZERO).min(totalAmt);
            if ((payments == null || payments.isEmpty()) && "PAID".equalsIgnoreCase(billing.getPaymentStatus())) {
                paidAmt = totalAmt.subtract(discountAmt); // legacy hand-marked PAID bill
            }
            java.math.BigDecimal refundedAmt = com.hms.service.hospital.BillMoney.refunded(payments);

            java.math.BigDecimal balance = totalAmt.subtract(discountAmt).subtract(paidAmt);

            // 4. Dynamic Charges Table
            java.util.List<Object[]> chargeRows = helper.buildDynamicChargeRows(items, medicines);

            // Fallback: if no items and no medicines, but bill has amount, show one row
            if (chargeRows.isEmpty() && totalAmt.compareTo(java.math.BigDecimal.ZERO) > 0) {
                String fallbackDesc = "OPD".equalsIgnoreCase(billing.getBillingType()) ? "Consultation Charges" : "Service Charges";
                chargeRows.add(new Object[]{fallbackDesc, totalAmt});
            }

            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{0.6f, 4.4f, 1.2f, 0.5f});

            // Table Headers
            helper.addTableHeaderCell(table, "S.No.");
            helper.addTableHeaderCell(table, "Fees For");
            helper.addTableHeaderCell(table, "Amount Rs.");
            helper.addTableHeaderCell(table, "Ps.");

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
                helper.addTableCell(table, String.valueOf(rowIdx), false);
                helper.addTableCell(table, desc, false);
                helper.addTableCell(table, rsStr, true);
                helper.addTableCell(table, psStr, true);
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

            PdfPCell totalLabelCell = new PdfPCell(new Phrase("Total", PdfLayoutHelper.SMALL_BOLD_FONT));
            totalLabelCell.setColspan(2);
            totalLabelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalLabelCell.setPadding(5f);
            fixedBottomTable.addCell(totalLabelCell);
            PdfPCell totalRsCell = new PdfPCell(new Phrase(totalRsStr, PdfLayoutHelper.SMALL_BOLD_FONT));
            totalRsCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalRsCell.setPadding(5f);
            fixedBottomTable.addCell(totalRsCell);
            PdfPCell totalPsCell = new PdfPCell(new Phrase(totalPsStr, PdfLayoutHelper.SMALL_BOLD_FONT));
            totalPsCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalPsCell.setPadding(5f);
            fixedBottomTable.addCell(totalPsCell);

            if (discountAmt.signum() > 0) {
                String label = billing.getDiscountReason() != null && !billing.getDiscountReason().isBlank()
                        ? "Discount (" + billing.getDiscountReason().trim() + ")" : "Discount";
                addMoneyRow(fixedBottomTable, label, discountAmt);
            }

            // Received Row
            long paidRupees = paidAmt.longValue();
            int paidPaise = paidAmt.subtract(java.math.BigDecimal.valueOf(paidRupees)).multiply(java.math.BigDecimal.valueOf(100)).intValue();
            String paidRsStr = String.valueOf(paidRupees);
            String paidPsStr = paidPaise == 0 ? "00" : String.format("%02d", paidPaise);

            PdfPCell receivedLabelCell = new PdfPCell(new Phrase("Received", PdfLayoutHelper.SMALL_BOLD_FONT));
            receivedLabelCell.setColspan(2);
            receivedLabelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            receivedLabelCell.setPadding(5f);
            fixedBottomTable.addCell(receivedLabelCell);
            PdfPCell paidRsCell = new PdfPCell(new Phrase(paidRsStr, PdfLayoutHelper.SMALL_BOLD_FONT));
            paidRsCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            paidRsCell.setPadding(5f);
            fixedBottomTable.addCell(paidRsCell);
            PdfPCell paidPsCell = new PdfPCell(new Phrase(paidPsStr, PdfLayoutHelper.SMALL_BOLD_FONT));
            paidPsCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            paidPsCell.setPadding(5f);
            fixedBottomTable.addCell(paidPsCell);

            // Balance Row
            long balRupees = balance.longValue();
            int balPaise = balance.subtract(java.math.BigDecimal.valueOf(balRupees)).multiply(java.math.BigDecimal.valueOf(100)).intValue();
            String balRsStr = String.valueOf(balRupees);
            String balPsStr = balPaise == 0 ? "00" : String.format("%02d", balPaise);

            PdfPCell balanceLabelCell = new PdfPCell(new Phrase("Balance", PdfLayoutHelper.SMALL_BOLD_FONT));
            balanceLabelCell.setColspan(2);
            balanceLabelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            balanceLabelCell.setPadding(5f);
            fixedBottomTable.addCell(balanceLabelCell);
            PdfPCell balRsCell = new PdfPCell(new Phrase(balRsStr, PdfLayoutHelper.SMALL_BOLD_FONT));
            balRsCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            balRsCell.setPadding(5f);
            fixedBottomTable.addCell(balRsCell);
            PdfPCell balPsCell = new PdfPCell(new Phrase(balPsStr, PdfLayoutHelper.SMALL_BOLD_FONT));
            balPsCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            balPsCell.setPadding(5f);
            fixedBottomTable.addCell(balPsCell);
            if (refundedAmt.signum() > 0) {
                // Received is already net of this; shown so the patient's copy explains the figure.
                addMoneyRow(fixedBottomTable, "Refunded", refundedAmt);
            }
            if ("CANCELLED".equalsIgnoreCase(billing.getPaymentStatus())) {
                addMoneyRow(fixedBottomTable, "BILL CANCELLED", java.math.BigDecimal.ZERO);
            }

            fixedBottomTable.writeSelectedRows(0, -1, 36, 175, writer.getDirectContent());

            // 6. Fixed Signature / Remittance Table at the bottom
            PdfPTable footerTable = new PdfPTable(2);
            footerTable.setTotalWidth(523f);
            footerTable.setWidths(new float[]{3.5f, 2.5f});

            PdfPCell footerLeft = new PdfPCell();
            footerLeft.setBorder(Rectangle.NO_BORDER);
            Paragraph recPara = new Paragraph("Received Rs. :  ", PdfLayoutHelper.NORMAL_FONT);
            if (paidAmt.compareTo(java.math.BigDecimal.ZERO) > 0) {
                recPara.add(new Chunk(helper.convertNumberToWords(paidRupees) + " Only", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.BOLD, Color.BLACK)));
            } else {
                recPara.add(new Chunk("_______________________________________", PdfLayoutHelper.NORMAL_FONT));
            }
            footerLeft.addElement(recPara);
            footerTable.addCell(footerLeft);

            PdfPCell footerRight = new PdfPCell();
            footerRight.setBorder(Rectangle.NO_BORDER);
            footerRight.setHorizontalAlignment(Element.ALIGN_RIGHT);

            Paragraph forHospital = new Paragraph("For " + hospital.getName(), PdfLayoutHelper.SMALL_BOLD_FONT);
            forHospital.setAlignment(Element.ALIGN_RIGHT);

            Paragraph sigLine = new Paragraph("\n\n\n___________________________\nAuthorized Signature", PdfLayoutHelper.FOOTER_FONT);
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

    private static final DateTimeFormatter ADVANCE_TIME =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", java.util.Locale.ENGLISH);

    /**
     * Receipt for one advance (deposit) on an IPD bill: the amount in figures and words, how it
     * was paid, the admission it is for and the total advance so far. Numbered ADV-&lt;id&gt;.
     */
    public ByteArrayInputStream generateAdvanceReceiptPdf(Hospital hospital, Patient patient, IpdAdmission ipd,
                                                          Billing bill, BillingPayment payment, BigDecimal totalSoFar) {
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Font label = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL, Color.GRAY);
        Font value = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.BOLD, Color.BLACK);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            helper.addPageBorder(writer);
            document.open();
            helper.addStyledHeader(document, hospital, "ADVANCE RECEIPT");

            PdfPTable t = new PdfPTable(4);
            t.setWidthPercentage(100);
            t.setWidths(new float[]{1.3f, 2.2f, 1.3f, 2.2f});
            t.setSpacingBefore(8f);
            advanceRow(t, label, value, "Receipt No.", "ADV-" + payment.getId());
            advanceRow(t, label, value, "Date", payment.getCreatedAt() == null ? "-" : payment.getCreatedAt().format(ADVANCE_TIME));
            advanceRow(t, label, value, "Patient", patient.getName());
            advanceRow(t, label, value, "IPD No.", ipd == null ? "-" : ipd.getIpdNumber());
            advanceRow(t, label, value, "Bill No.", bill.getCustomId() == null ? "-" : bill.getCustomId());
            advanceRow(t, label, value, "Paid by", payment.getMode()
                    + (payment.getReference() == null ? "" : " (" + payment.getReference() + ")"));
            document.add(t);

            long rupees = payment.getAmount().setScale(0, java.math.RoundingMode.HALF_UP).longValue();
            Paragraph amount = new Paragraph();
            amount.setSpacingBefore(12f);
            amount.add(new Chunk("Received with thanks Rs. " + payment.getAmount().stripTrailingZeros().toPlainString(),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, Font.BOLD, Color.BLACK)));
            document.add(amount);
            document.add(new Paragraph("Rupees " + (rupees == 0 ? "Zero" : helper.convertNumberToWords(rupees))
                    + " Only, as advance towards the inpatient bill.", FontFactory.getFont(FontFactory.HELVETICA, 10)));
            Paragraph total = new Paragraph("Total advance received for this admission: Rs. "
                    + totalSoFar.stripTrailingZeros().toPlainString()
                    + ". Any unused advance is returned at discharge.", label);
            total.setSpacingBefore(6f);
            document.add(total);

            Paragraph sign = new Paragraph("Received by: " + (payment.getRecordedBy() == null ? "-" : payment.getRecordedBy())
                    + "        Signature: ____________________", label);
            sign.setSpacingBefore(24f);
            document.add(sign);
            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Error generating the advance receipt PDF", e);
        }
        return new ByteArrayInputStream(out.toByteArray());
    }

    private static void advanceRow(PdfPTable t, Font label, Font value, String l, String v) {
        PdfPCell a = new PdfPCell(new Phrase(l, label));
        a.setBorder(Rectangle.NO_BORDER);
        a.setPadding(3f);
        t.addCell(a);
        PdfPCell b = new PdfPCell(new Phrase(v == null ? "-" : v, value));
        b.setBorder(Rectangle.NO_BORDER);
        b.setPadding(3f);
        t.addCell(b);
    }

    /**
     * Pharmacy tax invoice. Carries what a pharmacy bill must show in India: the shop's name,
     * address, GSTIN and drug licence number; a consecutive invoice number; per line the HSN code,
     * batch, expiry, MRP, rate, discount and GST rate; the taxable value with CGST and SGST; and
     * the amount in words. Prices include GST, so GST is shown as contained in the amount, not
     * added on top. Bills saved before prices included GST print the way they were billed.
     */
    public ByteArrayInputStream generatePharmacySaleReceiptPdf(Hospital hospital, Patient patient, PharmacySale sale) {
        Document document = new Document(PageSize.A4, 36, 36, 48, 120);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, Color.BLACK);
        Font smallBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Font.BOLD, Color.BLACK);
        Font headWhite = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, Font.BOLD, Color.WHITE);

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            helper.addPageBorder(writer);
            document.open();

            helper.addStyledHeader(document, hospital, "TAX INVOICE");
            document.add(new Paragraph(pharmacyRegistrationLine(hospital), smallBold));
            document.add(new Paragraph(" ", small));

            PdfPTable metaTable = new PdfPTable(2);
            metaTable.setWidthPercentage(100);
            metaTable.setSpacingAfter(10f);
            PdfPTable leftCol = new PdfPTable(2);
            leftCol.setWidths(new float[]{1.2f, 2f});
            helper.addMetaRow(leftCol, "Invoice No.", sale.getBillNumber());
            helper.addMetaRow(leftCol, "Date", sale.getCreatedAt() != null
                    ? sale.getCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")) : "-");
            helper.addMetaRow(leftCol, "Sale Type", sale.getSaleType() != null ? sale.getSaleType() : "WALK-IN");
            PdfPTable rightCol = new PdfPTable(2);
            rightCol.setWidths(new float[]{1.2f, 2f});
            helper.addMetaRow(rightCol, "Customer", customerName(patient, sale));
            helper.addMetaRow(rightCol, "Payment", sale.getPaymentMethod());
            helper.addMetaRow(rightCol, "Status", sale.getPaymentStatus());
            PdfPCell leftCell = new PdfPCell(leftCol);
            leftCell.setBorder(Rectangle.NO_BORDER);
            metaTable.addCell(leftCell);
            PdfPCell rightCell = new PdfPCell(rightCol);
            rightCell.setBorder(Rectangle.NO_BORDER);
            metaTable.addCell(rightCell);
            document.add(metaTable);

            boolean gstInclusive = isGstInclusive(sale);
            PdfPTable items = new PdfPTable(10);
            items.setWidthPercentage(100);
            items.setWidths(new float[]{0.4f, 2.8f, 0.8f, 1.2f, 0.8f, 0.6f, 0.9f, 0.9f, 0.7f, 1.1f});
            for (String h : new String[]{"#", "Medicine", "HSN", "Batch", "Exp", "Qty", "MRP", "Rate", "GST%", "Amount"}) {
                helper.addTableHeaderCell(items, h, headWhite);
            }
            int sr = 1;
            for (PharmacySaleItem item : sale.getItems()) {
                MedicineBatch b = item.getMedicineBatch();
                String name = b != null && b.getMedicine() != null ? b.getMedicine().getMedicineName() : "Medicine #" + item.getMedicineId();
                String hsn = b != null && b.getMedicine() != null && b.getMedicine().getHsnCode() != null ? b.getMedicine().getHsnCode() : "";
                helper.addTableCell(items, String.valueOf(sr++), false, small);
                helper.addTableCell(items, name, false, small);
                helper.addTableCell(items, hsn, false, small);
                helper.addTableCell(items, b != null ? b.getBatchNumber() : "-", false, small);
                helper.addTableCell(items, b != null && b.getExpiryDate() != null
                        ? b.getExpiryDate().format(DateTimeFormatter.ofPattern("MM/yy")) : "-", false, small);
                helper.addTableCell(items, plain(item.getQuantity()), true, small);
                helper.addTableCell(items, b != null && b.getMrp() != null ? money(b.getMrp()) : "-", true, small);
                helper.addTableCell(items, money(item.getUnitPrice()), true, small);
                helper.addTableCell(items, plain(item.getTaxPercentage()), true, small);
                helper.addTableCell(items, money(item.getTotalAmount()), true, small);
            }
            document.add(items);

            PdfPTable totals = new PdfPTable(2);
            totals.setWidthPercentage(45);
            totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totals.setSpacingBefore(8f);
            BigDecimal gst = nz(sale.getTaxAmount());
            BigDecimal net = nz(sale.getNetAmount());
            if (gstInclusive) {
                helper.addSummaryRow(totals, "Gross (incl. GST):", "INR " + money(sale.getSubtotal()));
                helper.addSummaryRow(totals, "Discount:", "- INR " + money(sale.getDiscountAmount()));
                helper.addSummaryRow(totals, "Net Payable:", "INR " + money(net));
                helper.addSummaryRow(totals, "Taxable Value:", "INR " + money(net.subtract(gst)));
                BigDecimal cgst = gst.divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
                helper.addSummaryRow(totals, "CGST (incl.):", "INR " + money(cgst));
                helper.addSummaryRow(totals, "SGST (incl.):", "INR " + money(gst.subtract(cgst)));
            } else {
                helper.addSummaryRow(totals, "Sub Total:", "INR " + money(sale.getSubtotal()));
                helper.addSummaryRow(totals, "GST (Tax):", "+ INR " + money(gst));
                helper.addSummaryRow(totals, "Discount:", "- INR " + money(sale.getDiscountAmount()));
                helper.addSummaryRow(totals, "Net Payable:", "INR " + money(net));
            }
            document.add(totals);

            long rupees = net.setScale(0, java.math.RoundingMode.DOWN).longValue();
            int paise = net.subtract(BigDecimal.valueOf(rupees)).movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).intValue();
            String words = "Rupees " + (rupees == 0 ? "Zero" : helper.convertNumberToWords(rupees))
                    + (paise > 0 ? " and " + helper.convertNumberToWords(paise) + " Paise" : "") + " Only";
            Paragraph inWords = new Paragraph("Amount in words: " + words, small);
            inWords.setSpacingBefore(6f);
            document.add(inWords);
            if (gstInclusive) {
                document.add(new Paragraph("Prices are inclusive of GST and do not exceed MRP.", small));
            }

            PdfPTable fTable = new PdfPTable(2);
            fTable.setTotalWidth(523f);
            fTable.setWidths(new float[]{3.5f, 2.5f});
            PdfPCell note = new PdfPCell(new Phrase("Medicines once sold are refunded only as per pharmacy policy.", small));
            note.setBorder(Rectangle.NO_BORDER);
            note.setVerticalAlignment(Element.ALIGN_BOTTOM);
            fTable.addCell(note);
            PdfPCell sigCell = new PdfPCell();
            sigCell.setBorder(Rectangle.NO_BORDER);
            sigCell.setVerticalAlignment(Element.ALIGN_BOTTOM);
            Paragraph sig = new Paragraph("\n\n______________________\nRegistered Pharmacist", PdfLayoutHelper.FOOTER_FONT);
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

    private static String customerName(Patient patient, PharmacySale sale) {
        if (patient != null && patient.getName() != null) {
            return patient.getName();
        }
        return sale.getPatientName() != null ? sale.getPatientName() : "Walk-in Customer";
    }

    /** "GSTIN: … | Drug Lic. No.: …" — states plainly when a detail has not been entered yet. */
    static String pharmacyRegistrationLine(Hospital hospital) {
        String gstin = hospital != null && hospital.getGstin() != null && !hospital.getGstin().isBlank()
                ? hospital.getGstin().trim() : "not entered";
        String dl = hospital != null && hospital.getDrugLicenseNumber() != null && !hospital.getDrugLicenseNumber().isBlank()
                ? hospital.getDrugLicenseNumber().trim() : "not entered";
        return "GSTIN: " + gstin + "    |    Drug Lic. No.: " + dl;
    }

    /** New bills: net = gross - discount (GST inside). Older bills added GST on top of the subtotal. */
    static boolean isGstInclusive(PharmacySale sale) {
        BigDecimal expectedNet = nz(sale.getSubtotal()).subtract(nz(sale.getDiscountAmount()));
        return expectedNet.compareTo(nz(sale.getNetAmount())) == 0;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String money(BigDecimal v) {
        return String.format("%.2f", nz(v));
    }

    private static String plain(BigDecimal v) {
        return nz(v).stripTrailingZeros().toPlainString();
    }

    /** One right-aligned "label | Rs | Ps" row in the totals block. */
    private static void addMoneyRow(PdfPTable table, String label, java.math.BigDecimal amount) {
        long rupees = amount.longValue();
        int paise = amount.subtract(java.math.BigDecimal.valueOf(rupees)).multiply(java.math.BigDecimal.valueOf(100)).intValue();
        PdfPCell l = new PdfPCell(new Phrase(label, PdfLayoutHelper.SMALL_BOLD_FONT));
        l.setColspan(2);
        l.setHorizontalAlignment(Element.ALIGN_RIGHT);
        l.setPadding(5f);
        table.addCell(l);
        PdfPCell rs = new PdfPCell(new Phrase(String.valueOf(rupees), PdfLayoutHelper.SMALL_BOLD_FONT));
        rs.setHorizontalAlignment(Element.ALIGN_RIGHT);
        rs.setPadding(5f);
        table.addCell(rs);
        PdfPCell ps = new PdfPCell(new Phrase(paise == 0 ? "00" : String.format("%02d", paise), PdfLayoutHelper.SMALL_BOLD_FONT));
        ps.setHorizontalAlignment(Element.ALIGN_RIGHT);
        ps.setPadding(5f);
        table.addCell(ps);
    }
}
