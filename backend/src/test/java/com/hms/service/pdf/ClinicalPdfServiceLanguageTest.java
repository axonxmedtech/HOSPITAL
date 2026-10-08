package com.hms.service.pdf;

import com.hms.entity.*;
import com.hms.repository.OpdRepository;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ClinicalPdfServiceLanguageTest {

    @Mock
    private OpdRepository opdRepository;

    @Mock
    private com.hms.repository.ConsultationNotePresetRepository presetRepository;

    @Mock
    private com.hms.repository.ConsultationStatementRepository statementRepository;

    @Mock
    private com.hms.repository.HospitalSettingRepository hospitalSettingRepository;

    @Spy
    private PdfLayoutHelper pdfLayoutHelper = new PdfLayoutHelper();

    @InjectMocks
    private ClinicalPdfService clinicalPdfService;

    private Hospital hospital;
    private Doctor doctor;
    private Patient patient;
    private MedicalRecord medicalRecord;

    @BeforeEach
    void setUp() {
        hospital = new Hospital();
        hospital.setId(1L);
        hospital.setName("City General Hospital");

        doctor = new Doctor();
        doctor.setId(10L);
        doctor.setName("Dr. Arvind Sharma");
        doctor.setSpecialization("General Physician");

        patient = new Patient();
        patient.setId(100L);
        patient.setName("Rajesh Patel");
        patient.setGender("Male");

        medicalRecord = new MedicalRecord();
        medicalRecord.setId(500L);
        medicalRecord.setHospitalId(1L);
        medicalRecord.setDiagnosis("Hypertension");
        medicalRecord.setTreatmentNotes("Drink more water.\nAvoid oily food.");
        medicalRecord.setCreatedAt(LocalDateTime.of(2026, 9, 11, 10, 30));
    }

    private String extractText(ByteArrayInputStream pdfStream) throws Exception {
        PdfReader reader = new PdfReader(pdfStream.readAllBytes());
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= reader.getNumberOfPages(); i++) {
            sb.append(extractor.getTextFromPage(i)).append("\n");
        }
        reader.close();
        return sb.toString();
    }

    @Test
    void marathiBilingualPrescriptionRendersEnglishAndMarathi() throws Exception {
        Prescription p1 = new Prescription();
        p1.setMedicineName("Amoxicillin 500mg");
        p1.setDosage("1 tab");
        p1.setFrequency("1-0-1");
        p1.setDuration("5 Days");
        p1.setFoodTiming("AFTER_FOOD");
        p1.setInstructions("with warm water");

        Prescription p2 = new Prescription();
        p2.setMedicineName("Pantoprazole 40mg");
        p2.setDosage("1 cap");
        p2.setFrequency("1-0-0");
        p2.setDuration("7 Days");
        p2.setFoodTiming("BEFORE_FOOD");

        ByteArrayInputStream pdf = clinicalPdfService.generatePrescriptionPdf(
                hospital, doctor, patient, medicalRecord, List.of(p1, p2), "EN_MR");

        String text = extractText(pdf);

        // Assert bilingual food-timing strings
        assertThat(text).contains("After Food / जेवणानंतर");
        assertThat(text).contains("with");
        assertThat(text).contains("warm water");
        assertThat(text).contains("Before Food / जेवणापूर्वी");

        // Assert doctor advice section in prescription
        assertThat(text).contains("DOCTOR ADVICE / सल्ला:");
        assertThat(text).contains("Drink more water.");
        assertThat(text).contains("Avoid oily food.");

        // Assert other fields remain unchanged in Latin English
        assertThat(text).contains("Amoxicillin 500mg");
        assertThat(text).contains("Pantoprazole 40mg");
        assertThat(text).contains("1-0-1");
        assertThat(text).contains("5 Days");
        assertThat(text).contains("RAJESH PATEL");
    }

    @Test
    void hindiBilingualPrescriptionRendersEnglishAndHindi() throws Exception {
        Prescription p1 = new Prescription();
        p1.setMedicineName("Paracetamol 650mg");
        p1.setDosage("1 tab");
        p1.setFrequency("1-1-1");
        p1.setDuration("3 Days");
        p1.setFoodTiming("AFTER_FOOD");

        Prescription p2 = new Prescription();
        p2.setMedicineName("Omeprazole 20mg");
        p2.setDosage("1 cap");
        p2.setFrequency("1-0-0");
        p2.setDuration("5 Days");
        p2.setFoodTiming("BEFORE_FOOD");
        p2.setInstructions("empty stomach");

        ByteArrayInputStream pdf = clinicalPdfService.generatePrescriptionPdf(
                hospital, doctor, patient, medicalRecord, List.of(p1, p2), "EN_HI");

        String text = extractText(pdf);

        assertThat(text).contains("After Food / भोजन के बाद");
        assertThat(text).contains("Before Food / भोजन से पहले");
        assertThat(text).contains("empty stomach");
        assertThat(text).contains("Paracetamol 650mg");

        // Assert doctor advice section
        assertThat(text).contains("DOCTOR ADVICE / सलाह:");
        assertThat(text).contains("Drink more water.");
    }

    @Test
    void englishPrescriptionRendersBeforeAndAfterFood() throws Exception {
        Prescription p1 = new Prescription();
        p1.setMedicineName("Metformin 500mg");
        p1.setDosage("1 tab");
        p1.setFrequency("1-0-1");
        p1.setDuration("30 Days");
        p1.setFoodTiming("AFTER_FOOD");
        p1.setInstructions("after meal");

        ByteArrayInputStream pdf = clinicalPdfService.generatePrescriptionPdf(
                hospital, doctor, patient, medicalRecord, List.of(p1), "EN");

        String text = extractText(pdf);

        assertThat(text).contains("After Food · after meal");
        assertThat(text).contains("Metformin 500mg");
        assertThat(text).contains("DOCTOR ADVICE:");
    }

    @Test
    void historicalMedicalRecordLanguageIsPreservedOnReprintWithoutParam() throws Exception {
        medicalRecord.setConsultationLanguage("EN_MR");

        Prescription p1 = new Prescription();
        p1.setMedicineName("Cetirizine 10mg");
        p1.setDosage("1 tab");
        p1.setFrequency("0-0-1");
        p1.setDuration("5 Days");
        p1.setFoodTiming("BEFORE_FOOD");

        // Calling overload without lang parameter (historical re-print)
        ByteArrayInputStream pdf = clinicalPdfService.generatePrescriptionPdf(
                hospital, doctor, patient, medicalRecord, List.of(p1));

        String text = extractText(pdf);

        // Should resolve to EN_MR from medicalRecord
        assertThat(text).contains("Before Food / जेवणापूर्वी");
    }

    @Test
    void casePaperRendersBilingualHeaderAndDevanagariNotes() throws Exception {
        medicalRecord.setSymptoms("Headache and fever");
        medicalRecord.setTreatmentNotes("विश्रांती आणि भरपूर पाणी पिण्याचा सल्ला");

        Opd opd = new Opd();
        opd.setCaseId("OPD-101");
        opd.setProblem("Fever since 2 days");

        ByteArrayInputStream pdf = clinicalPdfService.generateCasePaperPdf(
                hospital, doctor, patient, opd, medicalRecord, List.of(), "EN_MR");

        String text = extractText(pdf);

        assertThat(text).contains("TREATMENT & CLINICAL NOTES / सल्ला:");
        assertThat(text).contains("विश्रांती आणि भरपूर पाणी पिण्याचा सल्ला");
        assertThat(text).contains("Headache and fever");
    }

    @Test
    void prescriptionRendersBilingualQuickNotesFromPresets() throws Exception {
        medicalRecord.setTreatmentNotes("Drink more water.\nAvoid oily food.");

        ConsultationNotePreset p1 = new ConsultationNotePreset();
        p1.setText("Drink more water.");
        p1.setMarathiText("जास्त पाणी प्या.");
        p1.setHindiText("ज्यादा पानी पिएं.");

        ConsultationNotePreset p2 = new ConsultationNotePreset();
        p2.setText("Avoid oily food.");
        p2.setMarathiText("तेलकट अन्न टाळा.");
        p2.setHindiText("तैलीय भोजन से बचें.");

        org.mockito.Mockito.when(presetRepository.findByHospitalIdAndFieldTypeAndIsActiveTrue(
                1L, ConsultationNotePreset.FIELD_TYPE_TREATMENT_NOTES))
                .thenReturn(List.of(p1, p2));

        ByteArrayInputStream pdf = clinicalPdfService.generatePrescriptionPdf(
                hospital, doctor, patient, medicalRecord, List.of(), "EN_MR");

        String text = extractText(pdf);

        assertThat(text).contains("DOCTOR ADVICE / सल्ला:");
        assertThat(text).contains("Drink more water. / जास्त पाणी प्या.");
        assertThat(text).contains("Avoid oily food. / तेलकट अन्न टाळा.");
    }
}
