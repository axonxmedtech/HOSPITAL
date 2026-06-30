# Clinical Decision Support System (CDSS) — Design Spec

**Date:** 2026-06-30
**Phase:** B of 5 (Master Data → CDSS → Queue → Communication → Security)

---

## Goal

Intercept clinical actions (prescribing, lab result entry) with safety rule evaluations, surface structured alerts the clinician must acknowledge, and continuously monitor IPD patients using NEWS2 Early Warning Scores. All alerts are explained, logged, and auditable.

---

## What Exists Today (Foundation from Phase A)

| Asset | How CDSS Uses It |
|-------|-----------------|
| `patient_allergies` | Allergy-check source for prescription evaluation |
| `allergy_master.allergy_name` | Matched against medicine name for allergy detection |
| `prescription.medicine_master_id` + `medicine_name` | Duplicate/interaction check inputs |
| `lab_test_master.normal_range_text` | Parsed for critical value boundaries |
| IPD vitals (`bp_systolic`, `pulse_rate`, `temperature`, `spo2`, `respiratory_rate`) | NEWS2 EWS inputs |
| `MedicineMaster` (pharmacy) | Source for drug interaction lookup |

---

## Scope

### New Database Tables (2)

#### `drug_interaction_master`
Seeded with ~30 common high-risk drug pairs. Keyed by medicine name substring matching (no ATC codes required).

```
id, hospital_id,
drug_a_name varchar(200),   -- e.g. "Warfarin"
drug_b_name varchar(200),   -- e.g. "Aspirin"
severity varchar(20),       -- HIGH | MEDIUM
interaction_description text, -- "Risk of bleeding significantly increased..."
is_active bit(1),
created_at datetime(6)
```

#### `cdss_alert_log`
Audit trail of every alert shown to a clinician.

```
id, hospital_id,
alert_type varchar(50),     -- ALLERGY | DUPLICATE_MEDICINE | DRUG_INTERACTION | CRITICAL_LAB | EWS_HIGH | EWS_MEDIUM
patient_id bigint,
ipd_admission_id bigint DEFAULT NULL,
alert_message text,
severity varchar(20),
acknowledged_by_user_id bigint,
acknowledged_at datetime(6),
override_reason varchar(500) DEFAULT NULL,
created_at datetime(6)
```

---

## Alert Types

| Type | Trigger | Severity | Action |
|------|---------|---------|--------|
| `ALLERGY` | Prescribed medicine name matches `allergy_master.allergy_name` (case-insensitive substring) | HIGH | Doctor must confirm or cancel |
| `DUPLICATE_MEDICINE` | Same `medicine_name` already present in active IPD prescriptions | MEDIUM | Doctor must confirm or cancel |
| `DRUG_INTERACTION` | Prescribed medicine name matches a `drug_interaction_master` pair with any currently active prescription | HIGH/MEDIUM | Doctor must confirm or cancel |
| `CRITICAL_LAB` | Uploaded lab result numeric value is outside the critical range parsed from `lab_test_master.normal_range_text` | HIGH | Doctor + nurse notified; highlighted in red |
| `EWS_HIGH` | NEWS2 total score ≥ 5 | HIGH | Banner on IPD page; logged |
| `EWS_MEDIUM` | NEWS2 total score 3–4 | MEDIUM | Badge on vitals panel; logged |

---

## EWS — NEWS2 Scoring Table

| Parameter | Score 3 | Score 2 | Score 1 | Score 0 | Score 1 | Score 2 | Score 3 |
|-----------|---------|---------|---------|---------|---------|---------|---------|
| Respiratory Rate (/min) | ≤8 | — | 9–11 | 12–20 | — | 21–24 | ≥25 |
| SpO2 (%) | ≤91 | 92–93 | 94–95 | ≥96 | — | — | — |
| Systolic BP (mmHg) | ≤90 | 91–100 | 101–110 | 111–219 | — | — | ≥220 |
| Pulse (/min) | ≤40 | — | 41–50 | 51–90 | 91–110 | 111–130 | ≥131 |
| Temperature (°C) | ≤35.0 | — | 35.1–36.0 | 36.1–38.0 | 38.1–39.0 | ≥39.1 | — |
| Consciousness | — | — | — | Alert | — | — | CVPU |

Total ≥ 5 → HIGH (urgent doctor review). Total 3–4 → MEDIUM. Total 0–2 → normal.

Missing vitals score 0 (not penalised — graceful degradation).

---

## Normal Range Parsing

`lab_test_master.normal_range_text` is free text. The parser handles these formats:
- `"3.5-5.0"` → low=3.5, high=5.0
- `"< 10"` → high=10
- `"> 60"` → low=60
- `"70-110 mg/dL"` → low=70, high=110

If parsing fails for any format, the critical lab check is skipped for that test (graceful degradation — no false alerts).

---

## Drug Interaction Seed Data (~30 pairs)

Pre-seeded high-risk interactions:

| Drug A | Drug B | Severity | Risk |
|--------|--------|---------|------|
| Warfarin | Aspirin | HIGH | Bleeding risk |
| Warfarin | Ibuprofen | HIGH | Bleeding risk |
| Warfarin | Naproxen | HIGH | Bleeding risk |
| Methotrexate | Ibuprofen | HIGH | Methotrexate toxicity |
| Methotrexate | Aspirin | HIGH | Methotrexate toxicity |
| Lithium | Ibuprofen | HIGH | Lithium toxicity |
| Lithium | Naproxen | HIGH | Lithium toxicity |
| Digoxin | Amiodarone | HIGH | Digoxin toxicity |
| Clopidogrel | Omeprazole | MEDIUM | Reduced antiplatelet effect |
| Ciprofloxacin | Antacid | MEDIUM | Reduced absorption |
| Fluconazole | Warfarin | HIGH | Bleeding risk |
| Metformin | Contrast Dye | HIGH | Lactic acidosis risk |
| SSRIs | MAOIs | HIGH | Serotonin syndrome |
| Tramadol | SSRIs | HIGH | Serotonin syndrome |
| Phenytoin | Carbamazepine | MEDIUM | Altered drug levels |
| ACE Inhibitor | Potassium | MEDIUM | Hyperkalemia risk |
| Spironolactone | ACE Inhibitor | MEDIUM | Hyperkalemia risk |
| Sildenafil | Nitrate | HIGH | Severe hypotension |
| Haloperidol | Metoclopramide | MEDIUM | Extrapyramidal effects |
| Amiodarone | Simvastatin | HIGH | Myopathy risk |
| Clarithromycin | Simvastatin | HIGH | Myopathy risk |
| Erythromycin | Simvastatin | HIGH | Myopathy risk |
| Rifampicin | Warfarin | HIGH | Reduced anticoagulation |
| Rifampicin | Contraceptive Pill | HIGH | Contraceptive failure |
| Isoniazid | Phenytoin | MEDIUM | Phenytoin toxicity |
| Alcohol | Metronidazole | HIGH | Disulfiram-like reaction |
| Gentamicin | Furosemide | HIGH | Ototoxicity |
| Aminoglycoside | NSAIDs | HIGH | Nephrotoxicity |
| Theophylline | Ciprofloxacin | HIGH | Theophylline toxicity |
| Insulin | Beta Blocker | MEDIUM | Masked hypoglycemia |

---

## Backend Architecture

### New Files

| File | Purpose |
|------|---------|
| `entity/DrugInteractionMaster.java` | JPA entity |
| `entity/CdssAlertLog.java` | JPA entity |
| `repository/DrugInteractionMasterRepository.java` | Find by drug name |
| `repository/CdssAlertLogRepository.java` | Save + query logs |
| `dto/CdssAlertDTO.java` | Alert response: type, severity, message, suggestion |
| `dto/CdssCheckRequest.java` | Prescription check request |
| `dto/EwsResultDTO.java` | EWS score + breakdown + severity |
| `dto/SmartSummaryDTO.java` | Combined clinical summary |
| `service/hospital/CdssEvaluationService.java` | Core rules engine |
| `controller/hospital/CdssController.java` | REST endpoints |
| `test/.../CdssEvaluationServiceTest.java` | Unit tests |
| `setup/migrations/2026-06-30-cdss.sql` | DB migration |

### CdssEvaluationService — Methods

```java
// Called before saving a prescription
List<CdssAlertDTO> evaluatePrescription(Long hospitalId, Long patientId,
    String medicineName, Long medicineMasterId, Long ipdAdmissionId);
// Checks: allergy match, duplicate medicine, drug interactions

// Called after a lab result is uploaded
List<CdssAlertDTO> evaluateLabResult(Long hospitalId, Long labOrderId,
    Long labTestMasterId, Double numericValue);
// Checks: value vs parsed normal range from LabTestMaster

// Called after vitals are saved
EwsResultDTO calculateEws(Long hospitalId, Long ipdAdmissionId);
// Reads latest vitals, applies NEWS2 table, returns score + breakdown

// Log an acknowledgement (called when doctor clicks "Proceed")
void logAcknowledgement(Long hospitalId, String alertType, Long patientId,
    Long ipdAdmissionId, String alertMessage, String severity,
    String overrideReason);

// Smart summary for IPD page
SmartSummaryDTO getSmartSummary(Long hospitalId, Long ipdAdmissionId);
// Returns: allergies, active meds, pending lab/radiology orders, current EWS
```

### API Endpoints

| Method | URL | Roles | Description |
|--------|-----|-------|-------------|
| POST | `/hospital/cdss/check-prescription` | DOCTOR, HOSPITAL_ADMIN | Evaluate prescription before save |
| POST | `/hospital/cdss/acknowledge` | DOCTOR, HOSPITAL_ADMIN | Log alert acknowledgement |
| GET | `/hospital/cdss/ews/{ipdAdmissionId}` | ALL | Get current EWS score |
| GET | `/hospital/cdss/smart-summary/{ipdAdmissionId}` | ALL | Get combined clinical summary |

---

## Frontend Architecture

### New Files

| File | Purpose |
|------|---------|
| `frontend/src/components/CdssAlertModal.jsx` | Alert list modal with severity, explanation, Proceed/Cancel |
| `frontend/src/services/cdssService.js` | API calls for CDSS endpoints |

### Modified Files

| File | Change |
|------|--------|
| `frontend/src/pages/hospital/IpdDetails.jsx` | Add Smart Summary card at top of sidebar; EWS badge on vitals; call `checkPrescription` before saving |
| `frontend/src/components/lab/LabResultsPanel.jsx` | After lab result upload, call `evaluateLabResult` implicitly (backend triggers on save) |

### CdssAlertModal Behaviour

- Lists all alerts returned by `check-prescription`
- Each alert shows: coloured severity chip, type label, explanation text, suggestion
- HIGH alerts: red chip, "⚠ High Risk" badge
- MEDIUM alerts: orange chip, "⚠ Caution" badge
- "Proceed Anyway" button — enabled after all HIGH alerts are acknowledged (checkbox per alert)
- "Cancel Prescription" button — aborts the save
- On Proceed: calls `/hospital/cdss/acknowledge` for each alert, then continues with original save

### Smart Summary Card (IpdDetails sidebar top)

```
┌─────────────────────────────────────┐
│ Clinical Summary            EWS: 6 🔴│
│ ─────────────────────────────────── │
│ 🔴 Allergies: Penicillin (SEVERE)   │
│ 💊 Active Meds: Metformin, Warfarin │
│ 🧪 Pending: CBC, LFT               │
│ 📷 Pending: X-Ray Chest            │
└─────────────────────────────────────┘
```

### EWS Badge on Vitals Panel

After vitals are saved → call `GET /hospital/cdss/ews/{ipdId}` → show badge:
- Score 0–2: green "EWS: N"
- Score 3–4: orange "EWS: N ⚠"
- Score ≥5: red "EWS: N 🔴 Urgent"

---

## Alert Message Examples (Principle 17 — Explain Every Alert)

```
ALLERGY:
"This patient has a documented allergy to Penicillin (Severity: SEVERE).
 The prescribed medicine Amoxicillin belongs to the penicillin family.
 Review before proceeding."

DUPLICATE_MEDICINE:
"Metformin 500mg is already prescribed and active for this admission
 (prescribed 2 hours ago). This may result in duplicate dosing.
 Review existing prescription before adding another."

DRUG_INTERACTION:
"Warfarin and Aspirin together significantly increase the risk of bleeding.
 The patient is currently on Warfarin 5mg/day.
 Consider an alternative or adjust doses with specialist review."

CRITICAL_LAB:
"Serum Potassium result of 7.2 mmol/L is critically HIGH
 (normal range: 3.5–5.0 mmol/L). Immediate doctor review required.
 Risk of cardiac arrhythmia."
```

---

## Design Decisions

1. **Name-based matching for allergy + interaction checks** — We match `medicineName` (case-insensitive substring) against allergy name and drug interaction tables. No ATC codes needed. Approximate matching is intentional: better to show a false-positive alert the doctor dismisses than miss a real risk.

2. **Checks are advisory, not blocking** — The system never prevents saving. The doctor can always "Proceed Anyway" after acknowledging. This is consistent with clinical workflow: the HMS supports judgment, not replaces it.

3. **Allergy matching scope** — Checks `patient_allergies` for the IPD patient only (hospital-scoped). Does not check OPD-only patients (no allergy records there yet).

4. **Critical lab check is opt-in by design** — Only fires if `lab_test_master_id` is set on the lab order (i.e., order was placed using SearchableSelect from Phase A). Free-text orders skip the check.

5. **EWS recalculated on demand** — Not stored persistently. `calculateEws` reads the latest vitals record each time. No scheduled job needed.

6. **Drug interaction is bidirectional** — Checking "A+B" also covers "B+A" in the same query.

7. **Alert fatigue mitigation** — Only 3 prescription check types (allergy, duplicate, interaction) + critical lab + EWS. No alerts for every data entry. Each alert has a clear message and actionable suggestion.

---

## Seed Data Approach

Migration `2026-06-30-cdss.sql` seeds `drug_interaction_master` for `hospital_id = 0` (global/shared). The service checks `hospital_id IN (hospitalId, 0)` so hospitals get both global interactions and any custom ones they add. Alternatively, `seedCdssDefaults(hospitalId)` can be called per hospital (same idempotent pattern as Phase A).
