package com.hms.service.hospital;

import com.hms.entity.*;
import com.hms.exception.ResourceNotFoundException;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MasterDataService {

    @Autowired private LabTestMasterRepository labTestRepo;
    @Autowired private RadiologyTestMasterRepository radiologyTestRepo;
    @Autowired private AllergyMasterRepository allergyRepo;
    @Autowired private DiagnosisMasterRepository diagnosisRepo;
    @Autowired private ProcedureMasterRepository procedureRepo;
    @Autowired private SecurityContextHelper securityHelper;

    // ─── Lab Tests ────────────────────────────────────────────────────────────

    public List<LabTestMaster> searchLabTests(String q) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (q == null || q.isBlank()) return labTestRepo.findByHospitalIdAndIsActiveTrueOrderByTestNameAsc(hospitalId);
        return labTestRepo.searchByHospital(hospitalId, q.trim());
    }

    @Transactional
    public LabTestMaster createLabTest(LabTestMaster input) {
        input.setId(null);
        input.setHospitalId(securityHelper.getCurrentHospitalId());
        input.setIsActive(true);
        return labTestRepo.save(input);
    }

    @Transactional
    public LabTestMaster updateLabTest(Long id, LabTestMaster input) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        LabTestMaster existing = labTestRepo.findById(id)
            .filter(t -> t.getHospitalId().equals(hospitalId))
            .orElseThrow(() -> new ResourceNotFoundException("Lab test not found"));
        existing.setTestCode(input.getTestCode());
        existing.setTestName(input.getTestName());
        existing.setDepartment(input.getDepartment());
        existing.setSampleType(input.getSampleType());
        existing.setNormalRangeText(input.getNormalRangeText());
        existing.setUnit(input.getUnit());
        existing.setTurnaroundHours(input.getTurnaroundHours());
        existing.setPrice(input.getPrice());
        return labTestRepo.save(existing);
    }

    @Transactional
    public void deactivateLabTest(Long id) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        LabTestMaster existing = labTestRepo.findById(id)
            .filter(t -> t.getHospitalId().equals(hospitalId))
            .orElseThrow(() -> new ResourceNotFoundException("Lab test not found"));
        existing.setIsActive(false);
        labTestRepo.save(existing);
    }

    // ─── Radiology Tests ──────────────────────────────────────────────────────

    public List<RadiologyTestMaster> searchRadiologyTests(String q) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (q == null || q.isBlank()) return radiologyTestRepo.findByHospitalIdAndIsActiveTrueOrderByTestNameAsc(hospitalId);
        return radiologyTestRepo.searchByHospital(hospitalId, q.trim());
    }

    @Transactional
    public RadiologyTestMaster createRadiologyTest(RadiologyTestMaster input) {
        input.setId(null);
        input.setHospitalId(securityHelper.getCurrentHospitalId());
        input.setIsActive(true);
        return radiologyTestRepo.save(input);
    }

    @Transactional
    public RadiologyTestMaster updateRadiologyTest(Long id, RadiologyTestMaster input) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        RadiologyTestMaster existing = radiologyTestRepo.findById(id)
            .filter(t -> t.getHospitalId().equals(hospitalId))
            .orElseThrow(() -> new ResourceNotFoundException("Radiology test not found"));
        existing.setTestCode(input.getTestCode());
        existing.setTestName(input.getTestName());
        existing.setModality(input.getModality());
        existing.setPreparationInstructions(input.getPreparationInstructions());
        existing.setEstimatedDurationMinutes(input.getEstimatedDurationMinutes());
        existing.setPrice(input.getPrice());
        return radiologyTestRepo.save(existing);
    }

    @Transactional
    public void deactivateRadiologyTest(Long id) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        RadiologyTestMaster existing = radiologyTestRepo.findById(id)
            .filter(t -> t.getHospitalId().equals(hospitalId))
            .orElseThrow(() -> new ResourceNotFoundException("Radiology test not found"));
        existing.setIsActive(false);
        radiologyTestRepo.save(existing);
    }

    // ─── Allergies ────────────────────────────────────────────────────────────

    public List<AllergyMaster> searchAllergies(String q) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (q == null || q.isBlank()) return allergyRepo.findByHospitalIdAndIsActiveTrueOrderByAllergyNameAsc(hospitalId);
        return allergyRepo.searchByHospital(hospitalId, q.trim());
    }

    @Transactional
    public AllergyMaster createAllergy(AllergyMaster input) {
        input.setId(null);
        input.setHospitalId(securityHelper.getCurrentHospitalId());
        input.setIsCustom(true);
        input.setIsActive(true);
        return allergyRepo.save(input);
    }

    @Transactional
    public void deactivateAllergy(Long id) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        AllergyMaster existing = allergyRepo.findById(id)
            .filter(a -> a.getHospitalId().equals(hospitalId))
            .orElseThrow(() -> new ResourceNotFoundException("Allergy not found"));
        existing.setIsActive(false);
        allergyRepo.save(existing);
    }

    // ─── Diagnoses ────────────────────────────────────────────────────────────

    public List<DiagnosisMaster> searchDiagnoses(String q) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (q == null || q.isBlank()) return diagnosisRepo.findByHospitalIdAndIsActiveTrueOrderByIcdCodeAsc(hospitalId);
        return diagnosisRepo.searchByHospital(hospitalId, q.trim());
    }

    @Transactional
    public DiagnosisMaster createDiagnosis(DiagnosisMaster input) {
        input.setId(null);
        input.setHospitalId(securityHelper.getCurrentHospitalId());
        input.setIsCustom(true);
        input.setIsActive(true);
        return diagnosisRepo.save(input);
    }

    @Transactional
    public void deactivateDiagnosis(Long id) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        DiagnosisMaster existing = diagnosisRepo.findById(id)
            .filter(d -> d.getHospitalId().equals(hospitalId))
            .orElseThrow(() -> new ResourceNotFoundException("Diagnosis not found"));
        existing.setIsActive(false);
        diagnosisRepo.save(existing);
    }

    // ─── Procedures ───────────────────────────────────────────────────────────

    public List<ProcedureMaster> searchProcedures(String q) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (q == null || q.isBlank()) return procedureRepo.findByHospitalIdAndIsActiveTrueOrderByProcedureNameAsc(hospitalId);
        return procedureRepo.searchByHospital(hospitalId, q.trim());
    }

    @Transactional
    public ProcedureMaster createProcedure(ProcedureMaster input) {
        input.setId(null);
        input.setHospitalId(securityHelper.getCurrentHospitalId());
        input.setIsActive(true);
        return procedureRepo.save(input);
    }

    @Transactional
    public ProcedureMaster updateProcedure(Long id, ProcedureMaster input) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        ProcedureMaster existing = procedureRepo.findById(id)
            .filter(p -> p.getHospitalId().equals(hospitalId))
            .orElseThrow(() -> new ResourceNotFoundException("Procedure not found"));
        existing.setProcedureCode(input.getProcedureCode());
        existing.setProcedureName(input.getProcedureName());
        existing.setDepartment(input.getDepartment());
        existing.setEstimatedDurationMinutes(input.getEstimatedDurationMinutes());
        existing.setPrice(input.getPrice());
        return procedureRepo.save(existing);
    }

    @Transactional
    public void deactivateProcedure(Long id) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        ProcedureMaster existing = procedureRepo.findById(id)
            .filter(p -> p.getHospitalId().equals(hospitalId))
            .orElseThrow(() -> new ResourceNotFoundException("Procedure not found"));
        existing.setIsActive(false);
        procedureRepo.save(existing);
    }

    // ─── Seed defaults for a hospital ─────────────────────────────────────────

    @Transactional
    public void seedDefaultsForHospital(Long hospitalId) {
        if (!allergyRepo.existsByHospitalIdAndIsActiveTrue(hospitalId)) {
            String[][] allergies = {
                {"Penicillin","DRUG"},{"Amoxicillin","DRUG"},{"Ampicillin","DRUG"},
                {"Sulfa drugs","DRUG"},{"Aspirin","DRUG"},{"NSAIDs (Ibuprofen)","DRUG"},
                {"Codeine","DRUG"},{"Morphine","DRUG"},{"Tetracycline","DRUG"},
                {"Erythromycin","DRUG"},{"Ciprofloxacin","DRUG"},{"Metronidazole","DRUG"},
                {"Contrast dye (Iodine)","DRUG"},{"Insulin","DRUG"},
                {"Peanuts","FOOD"},{"Tree nuts","FOOD"},{"Milk / Dairy","FOOD"},
                {"Eggs","FOOD"},{"Wheat / Gluten","FOOD"},{"Shellfish","FOOD"},
                {"Soy","FOOD"},{"Sesame","FOOD"},{"Fish","FOOD"},
                {"Latex","ENVIRONMENTAL"},{"Dust mites","ENVIRONMENTAL"},
                {"Pollen","ENVIRONMENTAL"},{"Mold","ENVIRONMENTAL"},
                {"Pet dander (cats)","ENVIRONMENTAL"},{"Pet dander (dogs)","ENVIRONMENTAL"},
                {"Nickel","ENVIRONMENTAL"},{"Bee venom","OTHER"},{"Wasp venom","OTHER"}
            };
            for (String[] a : allergies) {
                AllergyMaster am = new AllergyMaster();
                am.setHospitalId(hospitalId); am.setAllergyName(a[0]);
                am.setCategory(a[1]); am.setIsCustom(false); am.setIsActive(true);
                allergyRepo.save(am);
            }
        }
        if (!diagnosisRepo.existsByHospitalIdAndIsActiveTrue(hospitalId)) {
            String[][] diagnoses = {
                {"I10","Essential Hypertension","CARDIOVASCULAR"},
                {"I11","Hypertensive Heart Disease","CARDIOVASCULAR"},
                {"I20","Angina Pectoris","CARDIOVASCULAR"},
                {"I21","Acute Myocardial Infarction","CARDIOVASCULAR"},
                {"I25","Chronic Ischaemic Heart Disease","CARDIOVASCULAR"},
                {"I48","Atrial Fibrillation","CARDIOVASCULAR"},
                {"I50","Heart Failure","CARDIOVASCULAR"},
                {"I63","Cerebral Infarction (Ischaemic Stroke)","CARDIOVASCULAR"},
                {"I64","Stroke NOS","CARDIOVASCULAR"},
                {"I70","Atherosclerosis","CARDIOVASCULAR"},
                {"J00","Acute Nasopharyngitis (Common Cold)","RESPIRATORY"},
                {"J02","Acute Pharyngitis","RESPIRATORY"},
                {"J03","Acute Tonsillitis","RESPIRATORY"},
                {"J06","Acute Upper Respiratory Infection","RESPIRATORY"},
                {"J18","Pneumonia","RESPIRATORY"},
                {"J20","Acute Bronchitis","RESPIRATORY"},
                {"J44","COPD","RESPIRATORY"},
                {"J45","Asthma","RESPIRATORY"},
                {"J46","Status Asthmaticus","RESPIRATORY"},
                {"E11","Type 2 Diabetes Mellitus","ENDOCRINE"},
                {"E10","Type 1 Diabetes Mellitus","ENDOCRINE"},
                {"E14","Unspecified Diabetes Mellitus","ENDOCRINE"},
                {"E03","Hypothyroidism","ENDOCRINE"},
                {"E05","Hyperthyroidism","ENDOCRINE"},
                {"E66","Obesity","ENDOCRINE"},
                {"A01","Typhoid Fever","INFECTIOUS"},
                {"A09","Diarrhoea and Gastroenteritis","INFECTIOUS"},
                {"A15","Respiratory Tuberculosis","INFECTIOUS"},
                {"A90","Dengue Fever","INFECTIOUS"},
                {"A91","Dengue Haemorrhagic Fever","INFECTIOUS"},
                {"B15","Acute Hepatitis A","INFECTIOUS"},
                {"B16","Acute Hepatitis B","INFECTIOUS"},
                {"B50","Plasmodium Falciparum Malaria","INFECTIOUS"},
                {"B54","Unspecified Malaria","INFECTIOUS"},
                {"B34","Viral Infection NOS","INFECTIOUS"},
                {"A49","Bacterial Infection NOS","INFECTIOUS"},
                {"K21","Gastro-oesophageal Reflux Disease","GASTROINTESTINAL"},
                {"K25","Gastric Ulcer","GASTROINTESTINAL"},
                {"K26","Duodenal Ulcer","GASTROINTESTINAL"},
                {"K29","Gastritis","GASTROINTESTINAL"},
                {"K37","Appendicitis","GASTROINTESTINAL"},
                {"K72","Hepatic Failure","GASTROINTESTINAL"},
                {"K80","Cholelithiasis (Gallstones)","GASTROINTESTINAL"},
                {"K85","Acute Pancreatitis","GASTROINTESTINAL"},
                {"K92","Gastrointestinal Haemorrhage","GASTROINTESTINAL"},
                {"N18","Chronic Kidney Disease","GENITOURINARY"},
                {"N20","Kidney Stone","GENITOURINARY"},
                {"N39","Urinary Tract Infection","GENITOURINARY"},
                {"N40","Benign Prostatic Hyperplasia","GENITOURINARY"},
                {"G40","Epilepsy","NEUROLOGICAL"},
                {"G43","Migraine","NEUROLOGICAL"},
                {"G45","Transient Ischaemic Attack","NEUROLOGICAL"},
                {"G20","Parkinson's Disease","NEUROLOGICAL"},
                {"G30","Alzheimer's Disease","NEUROLOGICAL"},
                {"M05","Rheumatoid Arthritis","MUSCULOSKELETAL"},
                {"M10","Gout","MUSCULOSKELETAL"},
                {"M15","Osteoarthritis","MUSCULOSKELETAL"},
                {"M54","Back Pain","MUSCULOSKELETAL"},
                {"O14","Gestational Hypertension","OBSTETRIC"},
                {"O24","Gestational Diabetes","OBSTETRIC"},
                {"O60","Preterm Labour","OBSTETRIC"},
                {"O80","Normal Delivery","OBSTETRIC"},
                {"F32","Depressive Episode","MENTAL"},
                {"F41","Anxiety Disorder","MENTAL"},
                {"F20","Schizophrenia","MENTAL"},
                {"S52","Fracture of Forearm","INJURY"},
                {"S72","Fracture of Femur","INJURY"},
                {"T14","Injury NOS","INJURY"},
                {"C34","Lung Cancer","NEOPLASM"},
                {"C50","Breast Cancer","NEOPLASM"},
                {"C18","Colon Cancer","NEOPLASM"},
                {"D50","Iron Deficiency Anaemia","OTHER"},
                {"D64","Other Anaemia","OTHER"},
                {"L30","Eczema / Dermatitis","OTHER"},
                {"H10","Conjunctivitis","OTHER"}
            };
            for (String[] d : diagnoses) {
                DiagnosisMaster dm = new DiagnosisMaster();
                dm.setHospitalId(hospitalId); dm.setIcdCode(d[0]);
                dm.setIcdDescription(d[1]); dm.setCategory(d[2]);
                dm.setIsCustom(false); dm.setIsActive(true);
                diagnosisRepo.save(dm);
            }
        }
    }
}
