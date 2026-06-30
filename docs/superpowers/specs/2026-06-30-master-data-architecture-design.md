# Master Data Architecture — Design Spec

**Date:** 2026-06-30
**Phase:** A of 5 (Master Data → CDSS → Queue → Communication → Security)

---

## Goal

Introduce controlled catalogs for Lab Tests, Radiology Tests, Allergies, Diagnoses (ICD-10), and Procedures so that clinical data is consistent, selectable (never free-typed), and ready to power CDSS in Phase B.

---

## What Exists Today (and the Problem)

| Field | Current | Problem |
|-------|---------|---------|
| `LabOrder.testName` | Free text | "cbc", "CBC", "C.B.C." all mean the same thing — breaks reporting |
| `RadiologyOrder.testName` | Free text | Same issue |
| `Prescription.medicineName` | Free text | Cannot check drug interactions or allergy conflicts in CDSS |
| Allergy info | `Patient.medicalHistory` text blob | No structured querying; CDSS cannot read it |
| Diagnosis | Not stored at all | No ICD codes; no disease-level reporting |

---

## Scope

### New Catalogs (6 entities)

| Entity | Table | Admin-managed | Pre-seeded |
|--------|-------|--------------|------------|
| `LabTestMaster` | `lab_test_master` | Yes | No |
| `RadiologyTestMaster` | `radiology_test_master` | Yes | No |
| `AllergyMaster` | `allergy_master` | Yes (add/deactivate) | Yes (~50 common) |
| `PatientAllergy` | `patient_allergies` | Doctor/Nurse (per patient) | No |
| `DiagnosisMaster` | `diagnosis_master` | Yes (add/deactivate) | Yes (~400 ICD-10 codes) |
| `ProcedureMaster` | `procedure_master` | Yes | No |

### Modified Existing Entities (nullable FK — backward-compatible)

| Entity | New Column | Purpose |
|--------|-----------|---------|
| `LabOrder` | `lab_test_master_id` | Optional link to catalog |
| `RadiologyOrder` | `radiology_test_master_id` | Optional link to catalog |
| `Prescription` | `medicine_master_id` | Doctor selects from pharmacy MedicineMaster |

---

## Entity Schemas

### LabTestMaster
```
id, hospital_id, test_code, test_name, department (BIOCHEMISTRY/HEMATOLOGY/MICROBIOLOGY/SEROLOGY/PATHOLOGY/OTHER),
sample_type (BLOOD/URINE/STOOL/SWAB/CSF/OTHER), normal_range_text, unit, turnaround_hours (int), price (decimal),
is_active (bool, default true), created_at, updated_at
```

### RadiologyTestMaster
```
id, hospital_id, test_code, test_name, modality (X_RAY/CT/MRI/USG/ECHO/ECG/OTHER),
preparation_instructions (text), estimated_duration_minutes (int), price (decimal),
is_active (bool, default true), created_at, updated_at
```

### AllergyMaster
```
id, hospital_id, allergy_name, category (DRUG/FOOD/ENVIRONMENTAL/OTHER),
is_custom (bool — false=seeded, true=admin-added), is_active (bool, default true), created_at
```

### PatientAllergy
```
id, hospital_id, patient_id, allergy_master_id, severity (MILD/MODERATE/SEVERE/UNKNOWN),
notes (text), recorded_by_user_id, recorded_at
```

### DiagnosisMaster
```
id, hospital_id, icd_code, icd_description, category (INFECTIOUS/CARDIOVASCULAR/RESPIRATORY/
ENDOCRINE/NEUROLOGICAL/MUSCULOSKELETAL/GASTROINTESTINAL/GENITOURINARY/OBSTETRIC/MENTAL/
INJURY/NEOPLASM/OTHER), is_custom (bool), is_active (bool, default true), created_at
```

### ProcedureMaster
```
id, hospital_id, procedure_code, procedure_name, department,
estimated_duration_minutes (int), price (decimal), is_active (bool, default true), created_at, updated_at
```

---

## API Design

### Base: `/hospital/master/**`

| Method | URL | Roles | Description |
|--------|-----|-------|-------------|
| GET | `/hospital/master/lab-tests/search?q=&page=0&size=20` | ALL | Autocomplete / list |
| POST | `/hospital/master/lab-tests` | HOSPITAL_ADMIN | Create |
| PUT | `/hospital/master/lab-tests/{id}` | HOSPITAL_ADMIN | Update |
| DELETE | `/hospital/master/lab-tests/{id}` | HOSPITAL_ADMIN | Deactivate (soft) |
| GET | `/hospital/master/radiology-tests/search?q=` | ALL | |
| POST/PUT/DELETE | `/hospital/master/radiology-tests/{id}` | HOSPITAL_ADMIN | |
| GET | `/hospital/master/allergies/search?q=` | ALL | |
| POST/PUT/DELETE | `/hospital/master/allergies/{id}` | HOSPITAL_ADMIN | |
| GET | `/hospital/master/diagnoses/search?q=` | ALL | |
| POST/PUT/DELETE | `/hospital/master/diagnoses/{id}` | HOSPITAL_ADMIN | |
| GET | `/hospital/master/procedures/search?q=` | ALL | |
| POST/PUT/DELETE | `/hospital/master/procedures/{id}` | HOSPITAL_ADMIN | |

### Patient Allergies: `/hospital/patients/{patientId}/allergies`

| Method | URL | Roles | Description |
|--------|-----|-------|-------------|
| GET | `/hospital/patients/{patientId}/allergies` | ALL | Get patient's allergies |
| POST | `/hospital/patients/{patientId}/allergies` | DOCTOR, NURSE, HOSPITAL_ADMIN | Add allergy |
| DELETE | `/hospital/patients/{patientId}/allergies/{allergyId}` | DOCTOR, NURSE, HOSPITAL_ADMIN | Remove allergy |

---

## Frontend Changes

### New: "Masters" tab in HospitalAdminDashboard
- Sub-tabs: Lab Tests | Radiology Tests | Allergies | Diagnoses | Procedures
- Each sub-tab: searchable DataTable + "Add New" modal + Edit/Deactivate action
- Only visible to HOSPITAL_ADMIN role

### Updated: Prescription form
- Medicine name field → SearchableSelect autocomplete backed by `/api/pharmacy/medicines/search?q=`
- On select: populates medicineName + sends medicineMasterId

### Updated: Lab order modal
- Test name field → SearchableSelect from `/hospital/master/lab-tests/search?q=`
- On select: auto-fills testName + labTestMasterId; sample type shown as hint

### Updated: Radiology order modal
- Scan type field → SearchableSelect from `/hospital/master/radiology-tests/search?q=`
- On select: auto-fills testName + radiologyTestMasterId; modality shown as hint

### New: Patient allergy section in IpdDetails sidebar
- "Allergies" card with red allergy chips (⚠ Penicillin · SEVERE)
- "Add Allergy" button → modal with AllergyMaster search + severity dropdown
- Visible to all roles; add/remove restricted to DOCTOR, NURSE, ADMIN

---

## Seed Data

**AllergyMaster (~50 entries):** Penicillin, Amoxicillin, Sulfa drugs, Aspirin, NSAIDs, Codeine, Latex, Peanuts, Tree nuts, Milk, Eggs, Wheat/Gluten, Shellfish, Soy, Bee venom, Dust mites, Pollen, Mold, Pet dander, Contrast dye, Iodine, Nickel, etc.

**DiagnosisMaster (~400 ICD-10 codes):** Common Indian hospital diagnoses across all categories — Hypertension (I10), Type 2 Diabetes (E11), Pneumonia (J18), Dengue (A90), Typhoid (A01), Malaria (B54), Tuberculosis (A15), UTI (N39), Appendicitis (K37), COPD (J44), Asthma (J45), Heart Failure (I50), MI (I21), Stroke (I63), Anaemia (D50-D64), Jaundice/Hepatitis (K72/B15-B19), etc.

---

## Design Decisions

1. **All catalogs are hospital-scoped** (`hospital_id` FK). Each hospital manages their own catalog independently — consistent with existing multi-tenant pattern.
2. **Soft deactivate, never delete** — `isActive = false`. Existing orders/prescriptions that reference a deactivated master still resolve correctly.
3. **Seeded entries (`isCustom = false`)** can be deactivated but not deleted (enforced in service layer).
4. **LabOrder/RadiologyOrder/Prescription FK is nullable** — existing records are unaffected; only new records set the FK.
5. **Prescription medicine link is recommended** — doctors use autocomplete to select from MedicineMaster; the free-text `medicineName` field is still persisted for display (copied from the master record on select).
