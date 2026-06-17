package com.hms.service.pdf;

import com.hms.entity.*;
import com.lowagie.text.*;
import com.lowagie.text.Font;
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
public class ReportPdfService {

    @Autowired
    private PdfLayoutHelper helper;

    public ByteArrayInputStream generateMedicinesListPdf(
            Hospital hospital,
            Doctor doctor,
            Patient patient,
            String title,
            String customNo,
            java.time.LocalDateTime createdAt,
            List<String[]> itemsList) {

        Document document = new Document(PageSize.A4, 36, 36, 36, 180);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            helper.addPageBorder(writer);
            document.open();

            // 1. Premium Patient Header
            helper.addPremiumPatientHeader(
                    document,
                    hospital,
                    doctor,
                    patient,
                    customNo,
                    createdAt != null ? createdAt : java.time.LocalDateTime.now(),
                    "-",
                    title
            );

            // 2. Main Medicines Table
            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{0.8f, 3.2f, 1.2f, 1.2f, 1.2f, 2.4f, 1.0f});
            table.setSpacingBefore(10f);

            helper.addTableHeaderCell(table, "S.No.");
            helper.addTableHeaderCell(table, "Medicine / Item Description");
            helper.addTableHeaderCell(table, "Dosage");
            helper.addTableHeaderCell(table, "Freq");
            helper.addTableHeaderCell(table, "Duration");
            helper.addTableHeaderCell(table, "Instruction");
            helper.addTableHeaderCell(table, "Qty");

            if (itemsList != null && !itemsList.isEmpty()) {
                int sr = 1;
                for (String[] row : itemsList) {
                    helper.addTableCell(table, String.valueOf(sr++), false);
                    helper.addTableCell(table, row.length > 0 ? row[0] : "", false);
                    helper.addTableCell(table, row.length > 5 ? row[1] : "", false);
                    helper.addTableCell(table, row.length > 5 ? row[2] : "", false);
                    helper.addTableCell(table, row.length > 5 ? row[3] : "", false);
                    helper.addTableCell(table, row.length > 5 ? row[4] : "", false);
                    helper.addTableCell(table, row.length > 5 ? row[5] : (row.length > 1 ? row[1] : ""), false);
                }
            } else {
                PdfPCell cell = new PdfPCell(new Phrase("No medicines or items recorded.", PdfLayoutHelper.NORMAL_FONT));
                cell.setColspan(7);
                cell.setPadding(8f);
                table.addCell(cell);
            }
            document.add(table);

            // 3. Fixed Footer Style
            helper.addPremiumFooter(writer, hospital, patient, customNo, "Authorized Signature");

            document.close();

        } catch (DocumentException e) {
            throw new RuntimeException("Error generating Medicines List PDF", e);
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
            helper.addPageBorder(writer);
            document.open();

            // 1. Standard Header
            String dateStr = date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"));
            String title = "PATIENT ACTIVITY REPORT - " + dateStr;
            helper.addStyledHeader(document, hospital, title);

            // 2. Main Activities Table
            // Columns: S.No., Patient ID, Patient Name, Type, Time, Doctor, Details
            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{0.6f, 1.2f, 2.0f, 1.3f, 1.1f, 2.0f, 1.8f});

            helper.addTableHeaderCell(table, "Sr#");
            helper.addTableHeaderCell(table, "Patient ID");
            helper.addTableHeaderCell(table, "Patient Name");
            helper.addTableHeaderCell(table, "Type");
            helper.addTableHeaderCell(table, "Time");
            helper.addTableHeaderCell(table, "Doctor Name");
            helper.addTableHeaderCell(table, "Details");

            if (activities != null && !activities.isEmpty()) {
                int sr = 1;
                for (java.util.Map<String, Object> act : activities) {
                    helper.addTableCell(table, String.valueOf(sr++), false);
                    helper.addTableCell(table, (String) act.get("patientId"), false);
                    helper.addTableCell(table, (String) act.get("patientName"), false);
                    helper.addTableCell(table, (String) act.get("activityType"), false);

                    // Format Time
                    String timeStr = "-";
                    Object timeObj = act.get("activityTime");
                    if (timeObj instanceof java.time.LocalDateTime) {
                        timeStr = ((java.time.LocalDateTime) timeObj).format(DateTimeFormatter.ofPattern("hh:mm a"));
                    } else if (timeObj != null) {
                        timeStr = timeObj.toString();
                    }
                    helper.addTableCell(table, timeStr, false);
                    helper.addTableCell(table, (String) act.get("doctorName"), false);
                    helper.addTableCell(table, (String) act.get("details"), false);
                }
            } else {
                PdfPCell cell = new PdfPCell(new Phrase("No patient activities found for this date.", PdfLayoutHelper.NORMAL_FONT));
                cell.setColspan(7);
                cell.setPadding(8f);
                table.addCell(cell);
            }
            document.add(table);

            // 3. Authorized Signature Block (fixed footer)
            helper.addFixedFooter(writer, null, "-", "Report Generated By Admin");

            document.close();

        } catch (DocumentException e) {
            throw new RuntimeException("Error generating Patient Activity PDF", e);
        }

        return new ByteArrayInputStream(out.toByteArray());
    }
}
