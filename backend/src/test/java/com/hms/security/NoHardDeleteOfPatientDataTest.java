package com.hms.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Patient, clinical and billing records are never really deleted — they are cancelled, archived,
 * stopped, undone or marked inactive, and stay readable in the Patient File and history lists.
 *
 * <p>This test reads the source and fails the build if a new code path calls a delete on one of
 * those repositories, declares a delete method on one, or issues a raw {@code DELETE FROM} on
 * one of their tables. The few existing, deliberate exceptions are listed below with why each is
 * safe; adding to the list should be a conscious, reviewed decision.
 */
class NoHardDeleteOfPatientDataTest {

    private static final Path MAIN = Paths.get("src/main/java/com/hms");

    /** Repositories whose rows are patient history, clinical record or money. */
    private static final Set<String> PROTECTED_REPOSITORIES = Set.of(
            "PatientRepository", "OpdRepository", "AppointmentRepository",
            "IpdAdmissionRepository", "IpdBedHistoryRepository", "DischargeSummaryRepository",
            "MedicalRecordRepository", "PrescriptionRepository",
            "VitalsRecordRepository", "NursingNoteRepository", "SugarChartEntryRepository",
            "MedicationAdministrationRepository", "InitialAssessmentRepository",
            "VulnerabilityAssessmentRepository",
            "IcuStayRepository", "SurgeryRepository", "SurgeryFormRepository",
            "SurgeryStateTransitionRepository", "PatientDocumentRepository",
            "BillingRepository", "BillingPaymentRepository", "BillingItemRepository",
            "BillingMedicineRepository",
            "PharmacySaleRepository", "PharmacySaleItemRepository", "PharmacySaleReturnRepository",
            "PurchasePaymentRepository", "SupplierReturnNoteRepository",
            "PatientAccessLogRepository");

    /** The same records by table name, for raw SQL. */
    private static final Set<String> PROTECTED_TABLES = Set.of(
            "patients", "opd", "appointments", "ipd_admission", "ipd_bed_history", "discharge_summary",
            "medical_records", "prescriptions", "vitals_records", "nursing_notes", "sugar_chart_entries",
            "medication_administrations", "initial_assessments", "vulnerability_assessments",
            "icu_stay", "surgeries", "surgery_forms", "surgery_state_transitions", "patient_documents",
            "billing", "billing_payments", "billing_items", "billing_medicines",
            "pharmacy_sales", "pharmacy_sale_items", "pharmacy_sale_returns",
            "purchase_payments", "supplier_return_notes", "supplier_return_note_items", "patient_access_logs");

    /**
     * Deliberate exceptions, as "File.java:variableName.method". Each removes a draft line of an
     * open bill, never a bill, a payment or a clinical record.
     */
    private static final Set<String> ALLOWED_CALLS = Set.of(
            // Submitting a consultation rebuilds that visit's fee lines from the consultation
            // (the source of truth for what the visit costs); the bill and its payments stay.
            "DoctorService.java:billingItemRepository.deleteAll",
            // Undoing a mistaken dose takes its charge line off the bill. Guarded: reason required,
            // admission must be active, assertChargeRemovable; the dose record itself is kept
            // (marked undone) and the undo is audited.
            "MedicationStockService.java:billingItemRepository.delete",
            "MedicationStockService.java:billingMedicineRepository.delete",
            // Editing the fee lines of an open bill replaces them. Guarded: under the bill's row
            // lock, refused on a paid / closed / cancelled bill, and never below what was collected.
            "BillingService.java:billingItemRepository.deleteAll");

    /**
     * Files allowed to issue raw DELETE FROM on these tables: the platform's permanent facility
     * delete, which is locked behind "blocked first + type the exact name + reason" and audited
     * (PlatformHospitalService.deleteHospitalPermanently).
     */
    private static final Set<String> ALLOWED_RAW_SQL_FILES = Set.of("PlatformHospitalService.java");

    private static final Pattern DELETE_CALL =
            Pattern.compile("\\b([a-z][A-Za-z0-9]*Repository)\\s*\\.\\s*(delete[A-Za-z]*)\\s*\\(");
    private static final Pattern RAW_DELETE =
            Pattern.compile("DELETE\\s+FROM\\s+`?([a-z_]+)`?", Pattern.CASE_INSENSITIVE);
    private static final Pattern DELETE_DECLARATION =
            Pattern.compile("^\\s*(?:void|long|int|Long|Integer|java\\.util\\.List<[^>]+>|List<[^>]+>)\\s+(delete[A-Za-z]*|remove[A-Za-z]*)\\s*\\(",
                    Pattern.MULTILINE);

    private static List<Path> sources() throws IOException {
        try (Stream<Path> s = Files.walk(MAIN)) {
            return s.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private static boolean isProtectedVariable(String variable) {
        String type = Character.toUpperCase(variable.charAt(0)) + variable.substring(1);
        return PROTECTED_REPOSITORIES.contains(type);
    }

    @Test
    void noCodePathDeletesPatientClinicalOrBillingRows() throws IOException {
        List<String> offences = new ArrayList<>();
        for (Path file : sources()) {
            String name = file.getFileName().toString();
            String src = Files.readString(file);
            Matcher m = DELETE_CALL.matcher(src);
            while (m.find()) {
                String variable = m.group(1);
                if (!isProtectedVariable(variable)) continue;
                String key = name + ":" + variable + "." + m.group(2);
                if (!ALLOWED_CALLS.contains(key)) offences.add(key);
            }
        }
        assertThat(offences)
                .as("Patient / clinical / billing rows must be cancelled, archived or marked inactive — never deleted")
                .isEmpty();
    }

    @Test
    void protectedRepositoriesDeclareNoDeleteMethods() throws IOException {
        List<String> offences = new ArrayList<>();
        for (Path file : sources()) {
            String name = file.getFileName().toString();
            if (!PROTECTED_REPOSITORIES.contains(name.replace(".java", ""))) continue;
            String src = Files.readString(file);
            Matcher m = DELETE_DECLARATION.matcher(src);
            while (m.find()) offences.add(name + ":" + m.group(1));
            if (RAW_DELETE.matcher(src).find()) offences.add(name + ": @Query DELETE");
        }
        assertThat(offences).as("a delete method on a patient-data repository").isEmpty();
    }

    @Test
    void noRawSqlDeletesPatientClinicalOrBillingTables() throws IOException {
        List<String> offences = new ArrayList<>();
        for (Path file : sources()) {
            String name = file.getFileName().toString();
            if (ALLOWED_RAW_SQL_FILES.contains(name)) continue;
            Matcher m = RAW_DELETE.matcher(Files.readString(file));
            while (m.find()) {
                String table = m.group(1).toLowerCase(Locale.ROOT);
                if (PROTECTED_TABLES.contains(table)) offences.add(name + ": DELETE FROM " + table);
            }
        }
        assertThat(offences).as("raw SQL deleting patient data").isEmpty();
    }

    @Test
    void thePermanentFacilityDeleteStaysLocked() throws IOException {
        String controller = Files.readString(MAIN.resolve("controller/platform/PlatformHospitalController.java"));
        assertThat(controller).contains("deleteHospitalPermanently(");
        assertThat(controller).doesNotContain("hospitalService.deleteHospital(");
        String service = Files.readString(MAIN.resolve("service/platform/PlatformHospitalService.java"));
        assertThat(service).as("the raw purge must not be public").doesNotContain("public void deleteHospital(");
    }
}
