package com.hms.service;

import com.hms.entity.*;
import com.hms.entity.pharmacy.PharmacySale;
import com.hms.service.pdf.BillingPdfService;
import com.hms.service.pdf.ClinicalPdfService;
import com.hms.service.pdf.ReportPdfService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.List;

/**
 * @deprecated Use the focused services in com.hms.service.pdf:
 *   {@link BillingPdfService}, {@link ClinicalPdfService}, {@link ReportPdfService}.
 *   This facade exists only for backward compatibility with existing controllers.
 */
@Deprecated
@Service
public class PdfService {

    @Autowired
    private BillingPdfService billingPdfService;

    @Autowired
    private ClinicalPdfService clinicalPdfService;

    @Autowired
    private ReportPdfService reportPdfService;

    public ByteArrayInputStream generatePrescriptionPdf(
            Hospital hospital,
            Doctor doctor,
            Patient patient,
            MedicalRecord medicalRecord,
            List<Prescription> prescriptions) {
        return clinicalPdfService.generatePrescriptionPdf(hospital, doctor, patient, medicalRecord, prescriptions);
    }

    public ByteArrayInputStream generateBillingReceiptPdf(Hospital hospital, Patient patient, Billing billing) {
        return billingPdfService.generateBillingReceiptPdf(hospital, patient, billing);
    }

    public ByteArrayInputStream generatePharmacySaleReceiptPdf(Hospital hospital, Patient patient, PharmacySale sale) {
        return billingPdfService.generatePharmacySaleReceiptPdf(hospital, patient, sale);
    }

    public ByteArrayInputStream generateMedicinesListPdf(
            Hospital hospital,
            Doctor doctor,
            Patient patient,
            String title,
            String customNo,
            java.time.LocalDateTime createdAt,
            List<String[]> itemsList) {
        return reportPdfService.generateMedicinesListPdf(hospital, doctor, patient, title, customNo, createdAt, itemsList);
    }

    public ByteArrayInputStream generateIpdPrescriptionPdf(
            Hospital hospital,
            Patient patient,
            IpdAdmission ipd,
            List<Prescription> prescriptions) {
        return clinicalPdfService.generateIpdPrescriptionPdf(hospital, patient, ipd, prescriptions);
    }

    public ByteArrayInputStream generatePatientActivityPdf(
            Hospital hospital,
            java.time.LocalDate date,
            java.util.List<java.util.Map<String, Object>> activities) {
        return reportPdfService.generatePatientActivityPdf(hospital, date, activities);
    }

    public ByteArrayInputStream generateCasePaperPdf(
            Hospital hospital,
            Doctor doctor,
            Patient patient,
            Opd opd,
            MedicalRecord medicalRecord) {
        return clinicalPdfService.generateCasePaperPdf(hospital, doctor, patient, opd, medicalRecord);
    }

    public ByteArrayInputStream generatePatientsReportPdf(
            Hospital hospital,
            java.time.LocalDate date,
            java.util.List<Patient> patients) {
        return reportPdfService.generatePatientsReportPdf(hospital, date, patients);
    }

    public ByteArrayInputStream generateOpdReportPdf(
            Hospital hospital,
            java.time.LocalDate date,
            java.util.List<Opd> opds,
            String reportType) {
        return reportPdfService.generateOpdReportPdf(hospital, date, opds, reportType);
    }

    public ByteArrayInputStream generateDischargeSummaryPdf(
            Hospital hospital,
            Patient patient,
            IpdAdmission ipd,
            DischargeSummary summary,
            Doctor doctor) {
        return clinicalPdfService.generateDischargeSummaryPdf(hospital, patient, ipd, summary, doctor);
    }

    public ByteArrayInputStream generateConsentPdf(
            Hospital hospital,
            Patient patient,
            IpdAdmission ipd,
            PatientConsent consent,
            BloodConsentDetail bloodDetail,
            Doctor doctor) {
        return clinicalPdfService.generateConsentPdf(hospital, patient, ipd, consent, bloodDetail, doctor);
    }
}
