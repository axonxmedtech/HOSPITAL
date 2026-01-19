package com.hms.service;

import com.hms.entity.*;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class PdfService {

        public ByteArrayInputStream generatePrescriptionPdf(
                        Hospital hospital,
                        Doctor doctor,
                        Patient patient,
                        MedicalRecord medicalRecord,
                        List<Prescription> prescriptions) {

                Document document = new Document(PageSize.A4);
                ByteArrayOutputStream out = new ByteArrayOutputStream();

                try {
                        PdfWriter.getInstance(document, out);
                        document.open();

                        // Fonts
                        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Font.BOLD,
                                        java.awt.Color.BLACK);
                        Font subHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Font.BOLD,
                                        java.awt.Color.DARK_GRAY);
                        Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL,
                                        java.awt.Color.BLACK);
                        Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL,
                                        java.awt.Color.GRAY);

                        // --- Header: Hospital Info ---
                        Paragraph hospitalName = new Paragraph(hospital.getName(), headerFont);
                        hospitalName.setAlignment(Element.ALIGN_CENTER);
                        document.add(hospitalName);

                        Paragraph hospitalAddress = new Paragraph(
                                        "Clinic Address: " + (hospital.getAddress() != null ? hospital.getAddress()
                                                        : "N/A"),
                                        smallFont);
                        hospitalAddress.setAlignment(Element.ALIGN_CENTER);
                        document.add(hospitalAddress);

                        Paragraph hospitalContact = new Paragraph(
                                        "Contact: " + (hospital.getPhone() != null ? hospital.getPhone() : "N/A"),
                                        smallFont);
                        hospitalContact.setAlignment(Element.ALIGN_CENTER);
                        document.add(hospitalContact);

                        document.add(new Paragraph("\n"));
                        document.add(new Paragraph("PRESCRIPTION", subHeaderFont)); // Make this
                                                                                    // centered?
                        document.add(new Paragraph(
                                        "----------------------------------------------------------------------------------------------------------------"));

                        // --- Doctor & Patient Info Table ---
                        PdfPTable infoTable = new PdfPTable(2);
                        infoTable.setWidthPercentage(100);
                        infoTable.setWidths(new int[] { 1, 1 });

                        // Doctor Details (Left)
                        PdfPCell doctorCell = new PdfPCell();
                        doctorCell.setBorder(Rectangle.NO_BORDER);
                        doctorCell.addElement(new Paragraph("Dr. " + doctor.getName(), subHeaderFont));
                        doctorCell.addElement(new Paragraph(doctor.getSpecialization(), normalFont));
                        doctorCell.addElement(new Paragraph("Phone: " + doctor.getPhone(), normalFont));
                        infoTable.addCell(doctorCell);

                        // Patient Details (Right)
                        PdfPCell patientCell = new PdfPCell();
                        patientCell.setBorder(Rectangle.NO_BORDER);
                        patientCell.addElement(new Paragraph("Patient: " + patient.getName(), normalFont));
                        patientCell.addElement(
                                        new Paragraph("Age/Gender: " + patient.getAge() + " / " + patient.getGender(),
                                                        normalFont));
                        patientCell.addElement(new Paragraph(
                                        "Date: " + medicalRecord.getCreatedAt()
                                                        .format(DateTimeFormatter.ofPattern("dd-MM-yyyy")),
                                        normalFont));
                        patientCell.addElement(new Paragraph("ID: " + patient.getCustomId(), normalFont));
                        infoTable.addCell(patientCell);

                        document.add(infoTable);
                        document.add(new Paragraph(
                                        "----------------------------------------------------------------------------------------------------------------"));
                        document.add(new Paragraph("\n"));

                        // --- Vitals / Diagnosis ---
                        if (medicalRecord.getSymptoms() != null && !medicalRecord.getSymptoms().isEmpty()) {
                                document.add(new Paragraph("Symptoms: " + medicalRecord.getSymptoms(), normalFont));
                        }
                        if (medicalRecord.getDiagnosis() != null && !medicalRecord.getDiagnosis().isEmpty()) {
                                document.add(new Paragraph("Diagnosis: " + medicalRecord.getDiagnosis(), normalFont));
                        }
                        if (medicalRecord.getTreatmentNotes() != null && !medicalRecord.getTreatmentNotes().isEmpty()) {
                                document.add(new Paragraph("Notes: " + medicalRecord.getTreatmentNotes(), normalFont));
                        }
                        document.add(new Paragraph("\n"));

                        // --- Rx Table ---
                        if (prescriptions != null && !prescriptions.isEmpty()) {
                                PdfPTable table = new PdfPTable(5);
                                table.setWidthPercentage(100);
                                table.setWidths(new int[] { 3, 1, 1, 1, 3 });
                                table.setHeaderRows(1);

                                // Headers
                                table.addCell(new PdfPCell(new Paragraph("Medicine", subHeaderFont)));
                                table.addCell(new PdfPCell(new Paragraph("Dosage", subHeaderFont)));
                                table.addCell(new PdfPCell(new Paragraph("Freq", subHeaderFont)));
                                table.addCell(new PdfPCell(new Paragraph("Duration", subHeaderFont)));
                                table.addCell(new PdfPCell(new Paragraph("Instruction", subHeaderFont)));

                                // Rows
                                for (Prescription p : prescriptions) {
                                        table.addCell(new Paragraph(p.getMedicineName(), normalFont));
                                        table.addCell(new Paragraph(p.getDosage(), normalFont));
                                        table.addCell(new Paragraph(p.getFrequency(), normalFont));
                                        table.addCell(new Paragraph(p.getDuration(), normalFont));
                                        table.addCell(new Paragraph(p.getInstructions(), normalFont));
                                }

                                document.add(table);
                        } else {
                                document.add(new Paragraph("No medicines prescribed.", normalFont));
                        }

                        document.add(new Paragraph("\n"));

                        // --- Follow Up ---
                        if (medicalRecord.getFollowUpDate() != null) {
                                document.add(new Paragraph(
                                                "Follow-up Date: "
                                                                + medicalRecord.getFollowUpDate()
                                                                                .format(DateTimeFormatter.ofPattern(
                                                                                                "dd-MM-yyyy")),
                                                subHeaderFont));
                        }

                        // --- Footer ---
                        document.add(new Paragraph("\n\n\n\n"));
                        Paragraph signature = new Paragraph("(Doctor's Signature)", smallFont);
                        signature.setAlignment(Element.ALIGN_RIGHT);
                        document.add(signature);

                        document.close();

                } catch (DocumentException e) {
                        throw new RuntimeException("Error generating PDF", e);
                }

                return new ByteArrayInputStream(out.toByteArray());
        }

        public ByteArrayInputStream generateBillingReceiptPdf(Hospital hospital, Patient patient, Billing billing) {
                Document document = new Document(PageSize.A5.rotate()); // A5 Landscape for receipts
                ByteArrayOutputStream out = new ByteArrayOutputStream();

                try {
                        PdfWriter.getInstance(document, out);
                        document.open();

                        // Fonts
                        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Font.BOLD,
                                        java.awt.Color.BLACK);
                        Font subHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Font.BOLD,
                                        java.awt.Color.DARK_GRAY);
                        Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL,
                                        java.awt.Color.BLACK);
                        Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL,
                                        java.awt.Color.GRAY);

                        // --- Header: Hospital Info ---
                        Paragraph hospitalName = new Paragraph(hospital.getName(), headerFont);
                        hospitalName.setAlignment(Element.ALIGN_CENTER);
                        document.add(hospitalName);

                        Paragraph hospitalAddress = new Paragraph(
                                        "Clinic Address: " + (hospital.getAddress() != null ? hospital.getAddress()
                                                        : "N/A"),
                                        smallFont);
                        hospitalAddress.setAlignment(Element.ALIGN_CENTER);
                        document.add(hospitalAddress);

                        document.add(new Paragraph("\n"));
                        Paragraph title = new Paragraph("PAYMENT RECEIPT", subHeaderFont);
                        title.setAlignment(Element.ALIGN_CENTER);
                        document.add(title);
                        document.add(new Paragraph(
                                        "------------------------------------------------------------------------------------------------",
                                        normalFont));

                        // --- Receipt Details ---
                        PdfPTable table = new PdfPTable(2);
                        table.setWidthPercentage(100);

                        PdfPCell cell = new PdfPCell();
                        cell.setBorder(Rectangle.NO_BORDER);
                        cell.addElement(new Paragraph("Receipt No: " + billing.getCustomId(), normalFont));
                        cell.addElement(new Paragraph(
                                        "Date: " + billing.getCreatedAt()
                                                        .format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")),
                                        normalFont));
                        table.addCell(cell);

                        cell = new PdfPCell();
                        cell.setBorder(Rectangle.NO_BORDER);
                        cell.addElement(new Paragraph("Patient: " + patient.getName(), normalFont));
                        cell.addElement(new Paragraph("Patient ID: " + patient.getCustomId(), normalFont));
                        table.addCell(cell);

                        document.add(table);
                        document.add(new Paragraph(
                                        "------------------------------------------------------------------------------------------------",
                                        normalFont));
                        document.add(new Paragraph("\n"));

                        // --- Amount ---
                        PdfPTable amountTable = new PdfPTable(2);
                        amountTable.setWidthPercentage(100);
                        amountTable.setWidths(new int[] { 3, 1 });

                        amountTable.addCell(new Paragraph("Description", subHeaderFont));
                        amountTable.addCell(new Paragraph("Amount (INR)", subHeaderFont));

                        amountTable.addCell(new Paragraph(billing.getDescription(), normalFont));
                        amountTable.addCell(new Paragraph(billing.getAmount().toString(), normalFont));

                        document.add(amountTable);

                        document.add(new Paragraph("\n"));
                        Paragraph total = new Paragraph("Total Paid: INR " + billing.getAmount().toString(),
                                        subHeaderFont);
                        total.setAlignment(Element.ALIGN_RIGHT);
                        document.add(total);

                        Paragraph status = new Paragraph("Status: " + billing.getPaymentStatus(), smallFont);
                        status.setAlignment(Element.ALIGN_RIGHT);
                        document.add(status);

                        // --- Footer ---
                        document.add(new Paragraph("\n\n"));
                        Paragraph signature = new Paragraph("(Authorized Signature)", smallFont);
                        signature.setAlignment(Element.ALIGN_RIGHT);
                        document.add(signature);

                        document.close();

                } catch (DocumentException e) {
                        throw new RuntimeException("Error generating PDF", e);
                }

                return new ByteArrayInputStream(out.toByteArray());
        }
}
