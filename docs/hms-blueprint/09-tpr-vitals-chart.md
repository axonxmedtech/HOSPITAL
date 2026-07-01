# Form Spec — TPR (Temperature/Pulse/Respiration/BP) Chart & Vitals Monitoring

| | |
|---|---|
| **Status** | Draft |
| **Source** | pasted form analysis — *VH/NABH/IPD/02/2026* (2026-07-01) |
| **Existing code?** | **substantially exists.** Storage = [`VitalSigns`](../../backend/src/main/java/com/hms/entity/VitalSigns.java) *(under-modeled — extend)*. **EWS + smart-summary + drug/lab CDSS already built** in [`CdssEvaluationService`](../../backend/src/main/java/com/hms/service/hospital/CdssEvaluationService.java) / [`CdssController`](../../backend/src/main/java/com/hms/controller/hospital/CdssController.java). Written today via [`NurseAssessmentService`](../../backend/src/main/java/com/hms/service/hospital/NurseAssessmentService.java). |

> **Read first — this is mostly reconciliation, not greenfield.**
> **(1) `VitalSigns` is the `patient_vitals` table — but under-modeled.** It stores `blood_pressure`
> as a **single VARCHAR(20) string**, and has **no** `bp_systolic`/`bp_diastolic`, `pain_score`,
> `weight`, `oxygen_support`, `remarks`, or method/rhythm/pattern/position. **Extend `VitalSigns`;
> don't create a new `patient_vitals` table.**
> **(2) The string-BP is already a proven problem.** [`CdssEvaluationService.calculateEws`](../../backend/src/main/java/com/hms/service/hospital/CdssEvaluationService.java)
> literally calls `parseSystolic(v.getBloodPressure())` — parsing the systolic out of `"120/80"`
> text to score it. Splitting BP into `bp_systolic`/`bp_diastolic` **INT** columns removes a live
> hack and is a hard prerequisite for validation, trend graphs, and EWS accuracy.
> **(3) EWS, Smart Summary, and drug/lab CDSS already exist — do not rebuild.** `CdssController`
> exposes `/hospital/cdss/ews/{id}`, `/smart-summary/{id}`, `/check-prescription`, `/acknowledge`.
> This is the **real EWS engine**. Reconcile: the [Clinical Risk Engine](./shared/clinical-risk-engine.md)'s
> "EWS scale" = this existing `CdssEvaluationService`, not a parallel build. The Form 07/08
> `[Future]` drug-interaction/5-rights flag is **partially built** (`evaluatePrescription`).

---

## 1. Form Overview
- **Department:** Nursing Station (primary); Doctor, ICU, Emergency, IPD, Quality, MRD (secondary)
- **Module:** **Nursing Station → Vitals Monitoring → TPR Chart** (interactive, continuous)
- **Filled By:** Staff Nurse / ICU staff (multiple times/day)
- **Reviewed By:** Doctor (trends at rounds), Ward In-charge (compliance)
- **Archived By:** MRD
- **Lifecycle:** continuous throughout admission; permanent after archival
- **NABH clause:** COP — monitoring of vital parameters + early recognition of deterioration.

## 2. Purpose
- **Hospital use:** chronological physiological record; early deterioration detection.
- **NABH requirement:** documented, scheduled vital monitoring per patient acuity.
- **Legal:** timestamped vitals + alerts are medico-legal evidence of monitoring.
- **Clinical:** trend graphs + EWS turn raw numbers into decision support.
- **Business rationale:** proactive monitoring module, not a recording sheet.

## 3. Trigger
`Admission → Vitals Recorded → TPR Chart Updated → Doctor Round → Treatment Changes → Repeat Monitoring → Discharge`. Repeats per the scheduled frequency (§8 BR-1) for the whole admission.

## 4. User Roles
| Actor | Capacity | HMS role |
|---|---|---|
| Staff Nurse | record, edit current shift | `NURSE` |
| ICU Staff | record, edit | `NURSE` (ICU) |
| Doctor | review trends (read) | `DOCTOR` |
| Ward In-charge | compliance | `NURSE` (in-charge) |
| Quality | audit | `QUALITY_OFFICER` (gap) |
| MRD | archive | `MRD_OFFICER` (gap) |
| Hospital Admin | read | `HOSPITAL_ADMIN` |

No new role gaps.

## 5. Parameters (fields → storage, extending `VitalSigns`)
| Parameter | Capture | DB column | Status |
|---|---|---|---|
| Temperature | value, unit (°C/°F), method (oral/axillary/tympanic/rectal) | `temperature` *(exists)* + **`temp_method`** (new) | store canonical °C |
| Pulse | rate, rhythm, quality | `pulse` *(exists)* + **`pulse_rhythm`** (new) | positive int |
| Respiratory rate | breaths/min, pattern (normal/laboured/shallow) | `respiratory_rate` *(exists)* + **`resp_pattern`** (new) | |
| Blood pressure | systolic, diastolic, position, arm | **`bp_systolic`,`bp_diastolic`** (new INT) + **`bp_position`** — replaces string `blood_pressure` | **split required (§Read-first-2)** |
| SpO₂ | %, oxygen support | `spo2` *(exists)* + **`oxygen_support`** (new: room-air/nasal/mask) | 0–100 |
| Pain score | 0–10 | **`pain_score`** (new) | mandatory for protocols |
| Weight | value | **`weight`** (new) | links Nutrition |
| Meta | recorded_by, recorded_at, remarks | `recorded_by`/`recorded_at` *(exists)* + **`remarks`** (new) | |

Each observation = a **separate row** (history preserved) — already the `VitalSigns` design.

## 6. Automatic Trend Graphs
Live graphs (temperature, pulse, BP, SpO₂, resp rate, weight) over the admission — computed from the row history via the trends API (§12). Requires split-BP ints (§Read-first-2). Doctors recognise trends instantly.

## 7. Database Design
**Extend `VitalSigns`** (tenant-owned, per-observation): add `bp_systolic`, `bp_diastolic`, `bp_position`, `temp_method`, `pulse_rhythm`, `resp_pattern`, `oxygen_support`, `pain_score`, `weight`, `remarks`. **Migrate** existing `blood_pressure` string → systolic/diastolic (backfill parse), then deprecate the string. FK `patient_id`(add)/`ipd_admission_id → ipd_admissions`, `recorded_by → users`. Index `(hospital_id, ipd_admission_id, recorded_at)` for trends. This is the **canonical vitals source** already fetched by Form 07 §J and Form 08 §D.

## 8. Business Rules
- **BR-1** Scheduled recordings by acuity: general ward 6–8 h · post-op as ordered · ICU frequent/continuous. Overdue → alert (§10).
- **BR-2** `IF temperature > threshold THEN` notify assigned doctor + ward nurse.
- **BR-3** `IF SpO₂ < configured limit THEN` critical alert.
- **BR-4** `IF BP critically high/low THEN` escalate immediately.
- **BR-5** `IF pain_score > threshold THEN` doctor review.
- **BR-6** Vitals feed **EWS** (`CdssEvaluationService.calculateEws`); score breach → notify doctor.
- **BR-7** Nurse edits only current shift; prior locked (in-charge override, audited).
- **BR-8** Every query filters `hospital_id`; every `{id}` validates ownership. *(Note: `VitalSigns` currently keys on `ipd_admission_id` — confirm service enforces hospital ownership per audit SEC rule.)*

## 9. Early Warning Score (EWS) — already built
`CdssEvaluationService.calculateEws(ipdAdmissionId)` scores pulse/BP/temp/RR/SpO₂(/consciousness) → NEWS. Exposed at `GET /hospital/cdss/ews/{ipdAdmissionId}`. **Reconcile with [Clinical Risk Engine](./shared/clinical-risk-engine.md):** this *is* the `EWS` scale — the engine's propagation (badge/notify) should wrap this existing calculator, not replace it. Improve accuracy once BP is split (removes `parseSystolic` string hack).

## 10. Alerts
High fever → doctor · low SpO₂ → emergency · rapid pulse → nurse · missed recording → ward in-charge · EWS breach → doctor. Reuse `CdssAlertLog` (already persisted by CDSS) + event/WebSocket infra.

## 11. Dashboard
**Nurse:** vitals due · overdue · high fever · low SpO₂ · high NEWS · abnormal BP · pain alerts. **Doctor:** trend graphs · latest vitals · critical changes since last round (via `/smart-summary`). All `WHERE hospital_id = current`.

## 12. APIs
Vitals under `/hospital/vitals`; EWS/summary reuse **existing** `/hospital/cdss/*`. Every `{id}`/`{patient}` validates `hospital_id`.
| Verb | Path | Roles | Purpose |
|---|---|---|---|
| POST | `/hospital/vitals` | NURSE | record observation |
| GET | `/hospital/patients/{id}/vitals` | NURSE, DOCTOR, ADMIN | history |
| GET | `/hospital/patients/{id}/vitals/trends` | NURSE, DOCTOR | trend series |
| PUT | `/hospital/vitals/{id}` | NURSE (current shift) | correct |
| GET | `/hospital/vitals/alerts` | NURSE, DOCTOR | active alerts |
| GET | `/hospital/cdss/ews/{admissionId}` | NURSE, DOCTOR | **exists** — EWS |
| GET | `/hospital/cdss/smart-summary/{admissionId}` | DOCTOR | **exists** — 24 h summary |

## 13. Permissions
| Role | Record | Edit | View |
|---|---|---|---|
| Nurse | Yes | Current shift | Yes |
| ICU Staff | Yes | Yes | Yes |
| Doctor | No | No | Yes |
| Hospital Admin | No | No | Read |
| MRD | No | No | Archived |

Matches §12 `@PreAuthorize`.

## 14. Validation
Temp 35–43 °C (convert °F on input); pulse 1–300 int; RR 1–80; **systolic 1–300, diastolic 1–200, systolic > diastolic**; SpO₂ 0–100; pain 0–10; weight > 0; recorded_at not future; oxygen_support ∈ enum. Server-side; reject impossible values.

## 15. Print Rules
Via [Signature & Document service](./shared/signature-and-document-service.md). `templates/tpr-chart.html`: patient details, chronological entries, **trend graphs**, nurse signature, date/time, QR, version. Layout resembles the familiar paper chart. Copy: file (MRD; Form 02 `tpr_chart` item).

## 16. Audit Logs
Via `AuditLogService` (`entity_type="VITAL_SIGNS"`): recorded · edited (old→new) · alert triggered · EWS breach — user, role, timestamp, old→new, IP. CDSS already logs alerts via `CdssAlertLog`.

## 17. AI & Smart Enhancements
- **Predictive deterioration** — rising-temperature / falling-SpO₂ trend detection → alert. `[Future]`.
- **Sepsis screening** — fever + high pulse + low BP + high RR → *suggest* sepsis screen (recommendation only). Fits CDSS rule set.
- **Automatic doctor summary** — 24 h highs/lows + NEWS — **already exists** (`/smart-summary`); surface it pre-round.

---

## Module & workflow placement
- **Owning module:** Nursing Station → Vitals Monitoring (canonical vitals source).
- **Creates:** `VitalSigns` rows. **Updates:** EWS score, alerts, trend cache. **Views:** consumed by Doctor rounds, Nursing progress (Form 08 §D), Initial assessment (Form 07 §J), ICU, Nutrition (weight). **Prints:** TPR chart. **Archives:** MRD (Form 02 `tpr_chart`).
- **Feeds into:** Doctor Dashboard · Clinical Assessment · MAR · ICU · EWS/CDSS · Alerts · Quality · MRD · Reports. **Fed by:** IPD Admission · Nursing.
- **New modules/roles this form implies:** **`VitalSigns` schema extension** (split BP + add pain/weight/oxygen_support/method/remarks) — foundational; **EWS/CDSS reconciliation** (existing `CdssEvaluationService` = the Clinical Risk Engine's EWS scale + smart-summary + drug/lab checks — not `[Future]`) — add to README.
