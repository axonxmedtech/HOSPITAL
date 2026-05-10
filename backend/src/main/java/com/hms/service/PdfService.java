package com.hms.service;

import com.hms.entity.*;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class PdfService {

    // Standardized Fonts
    private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.TIMES_BOLD, 22, Font.BOLD, new Color(0, 51, 102));
    private static final Font SUBTITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLDOBLIQUE, 16, Font.ITALIC, Color.DARK_GRAY);
    private static final Font LABEL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL, Color.GRAY);
    private static final Font VALUE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.NORMAL, Color.BLACK);
    private static final Font TABLE_HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.BOLD, Color.WHITE);
    private static final Font NORMAL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL, Color.BLACK);
    private static final Font FOOTER_FONT = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL, Color.GRAY);
    private static final Font SMALL_BOLD_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.BOLD, Color.BLACK);

    public ByteArrayInputStream generatePrescriptionPdf(
            Hospital hospital,
            Doctor doctor,
            Patient patient,
            MedicalRecord medicalRecord,
            List<Prescription> prescriptions) {

        Document document = new Document(PageSize.A4, 36, 36, 48, 36);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
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
            addMetaRow(leftCol, "Prescription#", medicalRecord.getId().toString());
            addMetaRow(leftCol, "Date", medicalRecord.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")));
            addMetaRow(leftCol, "Patient ID", patient.getCustomId());
            addMetaRow(leftCol, "Age / Gender", patient.getAge() + " / " + patient.getGender());

            PdfPTable rightCol = new PdfPTable(2);
            rightCol.setWidths(new float[]{1.2f, 2f});
            addMetaRow(rightCol, "Doctor", "Dr. " + doctor.getName());
            addMetaRow(rightCol, "Spec.", doctor.getSpecialization());
            addMetaRow(rightCol, "Bill To:", patient.getName());
            addMetaRow(rightCol, "Address", patient.getAddress() != null ? patient.getAddress() : "-");

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
            addFooter(document, patient, medicalRecord.getId().toString(), "Prescription Authorized Signature");

            document.close();

        } catch (DocumentException e) {
            throw new RuntimeException("Error generating PDF", e);
        }

        return new ByteArrayInputStream(out.toByteArray());
    }

    public ByteArrayInputStream generateBillingReceiptPdf(Hospital hospital, Patient patient, Billing billing) {
        // Now using A4 for consistent styling
        Document document = new Document(PageSize.A4, 36, 36, 48, 36);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // 1. Standard Header
            addStyledHeader(document, hospital, "SERVICE CHARGES");

            // 2. Metadata Table
            PdfPTable metaTable = new PdfPTable(2);
            metaTable.setWidthPercentage(100);
            metaTable.setSpacingBefore(10f);
            metaTable.setSpacingAfter(15f);

            PdfPTable leftCol = new PdfPTable(2);
            leftCol.setWidths(new float[]{1.2f, 2f});
            addMetaRow(leftCol, "Invoice#", billing.getCustomId());
            addMetaRow(leftCol, "Date", billing.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")));
            addMetaRow(leftCol, "Customer ID", patient.getCustomId());
            addMetaRow(leftCol, "Type", billing.getBillingType());

            PdfPTable rightCol = new PdfPTable(2);
            rightCol.setWidths(new float[]{1.2f, 2f});
            addMetaRow(rightCol, "Bill To:", patient.getName());
            addMetaRow(rightCol, "Address", patient.getAddress() != null ? patient.getAddress() : "N/A");
            addMetaRow(rightCol, "Status", billing.getPaymentStatus());

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

            // 4. Main Service/Amount Table
            PdfPTable amtTable = new PdfPTable(4);
            amtTable.setWidthPercentage(100);
            amtTable.setWidths(new float[]{0.5f, 4f, 1.5f, 1.5f});

            addTableHeaderCell(amtTable, "Sr#");
            addTableHeaderCell(amtTable, "Description");
            addTableHeaderCell(amtTable, "Unit");
            addTableHeaderCell(amtTable, "Amount");

            // Body rows
            addTableCell(amtTable, "1", false);
            addTableCell(amtTable, billing.getDescription() != null ? billing.getDescription() : "Service Fee / Consultation", false);
            addTableCell(amtTable, "1", true);
            addTableCell(amtTable, "INR " + String.format("%.2f", billing.getAmount()), true);

            // Push total right aligned
            PdfPCell totalLabel = new PdfPCell(new Phrase("Total", SMALL_BOLD_FONT));
            totalLabel.setColspan(3);
            totalLabel.setBorder(Rectangle.NO_BORDER);
            totalLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalLabel.setPaddingTop(15f);
            amtTable.addCell(totalLabel);

            PdfPCell totalVal = new PdfPCell(new Phrase("INR " + String.format("%.2f", billing.getAmount()), SMALL_BOLD_FONT));
            totalVal.setBorder(Rectangle.NO_BORDER);
            totalVal.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalVal.setPaddingTop(15f);
            amtTable.addCell(totalVal);

            // Push final amount right aligned
            PdfPCell paidLabel = new PdfPCell(new Phrase("Paid Amount", SMALL_BOLD_FONT));
            paidLabel.setColspan(3);
            paidLabel.setBorder(Rectangle.NO_BORDER);
            paidLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
            amtTable.addCell(paidLabel);

            PdfPCell paidVal = new PdfPCell(new Phrase("INR " + String.format("%.2f", billing.getAmount()), SMALL_BOLD_FONT));
            paidVal.setBorder(Rectangle.NO_BORDER);
            paidVal.setHorizontalAlignment(Element.ALIGN_RIGHT);
            amtTable.addCell(paidVal);

            document.add(amtTable);

            // 5. Footers and signature
            addFooter(document, patient, billing.getCustomId(), "Authorized Signature");

            document.close();

        } catch (DocumentException e) {
            throw new RuntimeException("Error generating PDF", e);
        }

        return new ByteArrayInputStream(out.toByteArray());
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
        left.addElement(new Paragraph(hospital.getName().toUpperCase(), TITLE_FONT));
        String addr = hospital.getAddress() != null ? hospital.getAddress() : "Hospital Location Address Unavailable";
        left.addElement(new Paragraph(addr, NORMAL_FONT));
        headerTable.addCell(left);

        // Right side: Contacts
        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setVerticalAlignment(Element.ALIGN_BOTTOM);

        PdfPTable contactSubTable = new PdfPTable(2);
        contactSubTable.setWidths(new float[]{1f, 2.5f});
        addHeaderContactRow(contactSubTable, "Phone:", hospital.getPhone() != null ? hospital.getPhone() : "-");
        addHeaderContactRow(contactSubTable, "E-mail:", "support@" + hospital.getName().toLowerCase().replaceAll("\\s+", "") + ".com");
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
        addMetaRow(subRem, "Patient Name", patient.getName());
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
}
