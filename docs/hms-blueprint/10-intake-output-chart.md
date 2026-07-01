# Form Spec — Intake & Output (I/O) Chart / Fluid Management Engine

| | |
|---|---|
| **Status** | Draft |
| **Source** | pasted form analysis — *VH/NABH/IPD/06/2026* (2026-07-01) |
| **Existing code?** | **new module.** No fluid/intake/output tables exist. Integrates with existing MAR ([`NurseTask`](../../backend/src/main/java/com/hms/entity/NurseTask.java) IV administration), Blood Bank (Form 01 gap), and extends [`CdssEvaluationService`](../../backend/src/main/java/com/hms/service/hospital/CdssEvaluationService.java) for AKI/overload screening. `FluidMaster` follows the existing master-table convention (`AllergyMaster`/`DiagnosisMaster`/…). |

> **Read first — two anti-duplication reconciliations.**
> **(1) IV fluids must auto-import from the MAR, not be re-typed.** IV fluid administration already
> flows through [`NurseTask`](../../backend/src/main/java/com/hms/entity/NurseTask.java)
> (`administered_quantity`, `route`) off an IV-type `DoctorOrder`. The I/O chart's IV-intake rows
> should be **derived from** those administrations, not a parallel manual entry — otherwise volumes
> double-count. **(2) Blood products import from Blood Bank** (Form 01) — no duplicate entry (the
> form states this explicitly). Until Blood Bank exists, blood-product intake is a manual row flagged
> for later linkage. **(3) AKI/overload screening extends CDSS.** Add `evaluateFluidBalance(...)` to
> the existing `CdssEvaluationService` (alongside `evaluatePrescription`/`evaluateLabResult`/
> `calculateEws`), reusing `CdssAlertLog` — don't build a parallel alert engine.

---

## 1. Form Overview
- **Department:** Nursing Station (primary); Doctor, ICU, Nephrology, Surgery, Dietician, MRD (secondary)
- **Module:** **Nursing Station → Fluid Balance → Intake & Output** (live monitoring)
- **Filled By:** Staff Nurse / ICU staff (continuous)
- **Reviewed By:** Doctor / Nephrologist / Surgeon
- **Archived By:** MRD
- **Lifecycle:** active during admission (esp. ICU/renal/post-op); permanent after archival
- **NABH clause:** COP — fluid balance monitoring for applicable patients.

## 2. Purpose
- **Hospital use:** measure fluid balance → drives IV/diuretic/dialysis decisions.
- **NABH requirement:** documented I/O monitoring where clinically indicated.
- **Legal:** fluid records are key evidence in renal/ICU/post-op litigation and audits.
- **Clinical:** a **decision-making** document — continue/stop fluids, diuretics, dialysis, AKI detection.
- **Business rationale:** auto-calculated balance eliminates arithmetic errors; active clinical service vs passive chart.

## 3. Trigger
`Admission → Doctor orders fluid monitoring → I/O chart activated → nurse records each intake/output → system calculates balance continuously → doctor reviews → treatment modified`. Activated by a `DoctorOrder` of type FLUID_MONITORING (or ICU/post-op protocol).

## 4. User Roles
| Actor | Capacity | HMS role |
|---|---|---|
| Staff Nurse / ICU | record intake+output, edit current shift | `NURSE` |
| Doctor / Nephrologist / Surgeon | review balance (read) | `DOCTOR` |
| Dietician | oral-intake entry, view relevant | `DIETICIAN` (gap, Form 06) |
| MRD | archive | `MRD_OFFICER` (gap) |
| Hospital Admin | read | `HOSPITAL_ADMIN` |

No new role gaps.

## 5. Intake (fields → storage)
| Category | Fields | Source |
|---|---|---|
| Oral (water/tea/juice/milk/ORS/liquid diet) | time, type, quantity(ml), recorded_by | manual (or Dietician) |
| IV fluids (NS/RL/DNS/dextrose) | fluid name, volume, start/end time, rate, nurse | **auto-import from `NurseTask` IV administration** (§Read-first-1) |
| Tube feeding (NG/PEG) | formula, volume, time | manual |
| Blood products | product, volume, time | **auto-import from Blood Bank** (Form 01); manual interim |

## 6. Output (fields → storage)
| Category | Fields |
|---|---|
| Urine | time, volume, catheter/natural, color, remarks |
| Stool | frequency, quantity, consistency |
| Vomiting | time, volume, color |
| Drain (chest/abdominal/surgical) | drain type, volume, color, remarks |
| Dialysis | volume (auto if integrated) |

## 7. Automatic Fluid Balance
`balance = Σ intake − Σ output`, computed continuously (no manual arithmetic). Aggregated per configurable window (per-shift, 24 h). Stored/cached in `daily_fluid_balance`.

## 8. Database Design
**`fluid_intake`** (new): `id, public_id, hospital_id, patient_id, admission_id, type, source_ref (NurseTask/BloodBank id when derived), description, volume_ml, recorded_time, recorded_by, created_at`.
**`fluid_output`** (new): `id, public_id, hospital_id, patient_id, admission_id, type, description, volume_ml, color, recorded_time, recorded_by, created_at`.
**`daily_fluid_balance`** (new, computed/cached): `id, hospital_id, patient_id, admission_id, date, total_intake, total_output, balance, updated_at`.
**`fluid_master`** (new; follows existing master convention): `id, hospital_id, category(ORAL/IV/TUBE/BLOOD/URINE/DRAIN…), name, default_unit`.
- FK: `patient_id → patients`, `admission_id → ipd_admissions`, `recorded_by → users`. Tenant + audit + soft-delete. Index `(hospital_id, admission_id, recorded_time)` for trends.

## 9. Business Rules
- **BR-1** Negative volumes not allowed (`volume_ml > 0`).
- **BR-2** Fluid type must come from `fluid_master` (no free-text type).
- **BR-3** Output beyond plausible limits requires confirmation (guardrail).
- **BR-4** `IF intake > doctor-prescribed limit THEN` notify doctor (fluid restriction).
- **BR-5** `IF urine output < configured threshold (e.g. <0.5 ml/kg/hr) THEN` alert (AKI risk).
- **BR-6** `IF drain output rises sharply THEN` notify surgeon immediately.
- **BR-7** IV/blood rows **derived** from MAR/Blood Bank are read-only in the chart (edit at source) — prevents double entry.
- **BR-8** Nurse edits current shift only; prior locked (in-charge override, audited).
- **BR-9** Every query filters `hospital_id`; every `{id}` validates ownership.

## 10. Dashboard
**Nurse:** current intake · current output · today's balance · fluid-restriction patients · strict-I/O patients · low-urine alerts · high-drain alerts. **Doctor:** 24 h balance · trend graph · hourly urine output · restriction compliance. All `WHERE hospital_id = current`.

## 11. Trend Graph
Intake vs output series over time (computed from row history) — doctor sees deterioration (rising positive balance / falling urine) instantly. Same trend infra as vitals (Form 09).

## 12. APIs
Under `/hospital/fluid`; every `{id}`/`{patient}` validates `hospital_id`.
| Verb | Path | Roles | Purpose |
|---|---|---|---|
| POST | `/hospital/fluid/intake` | NURSE, DIETICIAN(oral) | add intake |
| POST | `/hospital/fluid/output` | NURSE | add output |
| GET | `/hospital/fluid/balance/{patient}` | NURSE, DOCTOR, ADMIN | current balance |
| GET | `/hospital/fluid/trends` | NURSE, DOCTOR | trend series |
| GET | `/hospital/fluid/dashboard` | NURSE, DOCTOR | dashboard |
| PUT | `/hospital/fluid/{id}` | NURSE (current shift) | correct manual entry |

## 13. Permissions
| Role | Add | Edit | View |
|---|---|---|---|
| Nurse / ICU | Yes | Current shift (ICU: yes) | Yes |
| Doctor | No | No | Yes |
| Dietician *(gap)* | Oral intake only | No | Relevant patients |
| Hospital Admin | Read | No | Read |
| MRD | No | No | Archived |

Matches §12 `@PreAuthorize`.

## 14. Validation
`volume_ml > 0`; type ∈ `fluid_master`; recorded_time not future; urine/drain thresholds configurable per hospital; derived IV/blood rows not manually editable; balance recomputed server-side (never client).

## 15. Print Rules
Via [Signature & Document service](./shared/signature-and-document-service.md). `templates/io-chart.html`: patient info, intake table, output table, daily totals, fluid balance, nurse signature, doctor review, QR. Mirrors the familiar ward chart + auto totals. Copy: file (MRD; Form 02 `io_chart` item).

## 16. Audit Logs
Via `AuditLogService` (`entity_type="FLUID_INTAKE"`/`"FLUID_OUTPUT"`): entry added/edited (old→new volume) · threshold alert · restriction breach — user, role, timestamp, old→new, IP.

## 17. AI & Smart Enhancements — extend CDSS
- **Fluid-overload detection** — high intake, low output → alert. New `CdssEvaluationService.evaluateFluidBalance`.
- **AKI screening** — falling urine output + rising positive balance + renal diagnosis → *suggest* AKI review (recommendation, not diagnosis). Reuses `PatientAllergy`/diagnosis context + `CdssAlertLog`.
- **Smart nurse reminder** — urine-output-pending nudge (ties to `NurseTask` scheduling).
- **Doctor round summary** — 24 h intake/output/balance/lowest urine — extend the existing `/hospital/cdss/smart-summary` (Form 09) to include fluid metrics.

---

## Module & workflow placement
- **Owning module:** Nursing Station → Fluid Balance (Fluid Management Engine).
- **Creates:** `fluid_intake`, `fluid_output`, `daily_fluid_balance`, `fluid_master`. **Updates:** balance, alerts. **Views/derives:** IV from MAR (`NurseTask`), blood from Blood Bank. **Prints:** I/O chart. **Archives:** MRD (Form 02 `io_chart`).
- **Feeds into:** Doctor Dashboard · ICU · Nephrology/Dialysis · CDSS (AKI/overload) · Alerts · MRD · Reports · Lab (RFT correlation). **Fed by:** IPD Admission · Pharmacy IV orders · Blood Bank · Dietician oral intake.
- **New modules/roles this form implies:** `fluid_intake`/`fluid_output`/`daily_fluid_balance`/`fluid_master` tables · **`CdssEvaluationService.evaluateFluidBalance`** (extend existing CDSS) · MAR→I/O and Blood Bank→I/O auto-import links — add to README.
