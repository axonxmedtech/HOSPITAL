# Form Spec — Anaesthesia Record / Anaesthesia Information Management System (AIMS)

| | |
|---|---|
| **Status** | Draft |
| **Source** | pasted form analysis — *VH/NABH/OT/~05/2026* (OCR gap; sits between OT/04 Operation Record and OT/08 PACU) (2026-07-01) |
| **Existing code?** | **`anaesthesia_record` is new; it links to and reuses existing OT/clinical rails.** ASA/plan from [`pre_anaesthesia_assessment`](./15-pre-anaesthesia-assessment.md) (Form 15); drugs deduct [`Medicine`](../../backend/src/main/java/com/hms/entity/Medicine.java) stock (like [Form 18](./18-operation-record.md) implants); fluids → Fluid Management (Form 10); EBL shared with the Operation Record (Form 18); early-warning extends [`CdssEvaluationService.calculateEws`](../../backend/src/main/java/com/hms/service/hospital/CdssEvaluationService.java); trigger keys off `OtBooking` status. |

> **Read first — reuse, and note two feeder gaps.**
> **(1) ASA + anaesthesia plan are NOT re-entered** — they come from `pre_anaesthesia_assessment`
> (`asa_class`, `planned_anaesthesia`, airway plan; Form 15). The record links `pac_id`.
> **(2) Anaesthetic drugs deduct `Medicine` stock** (BR-3) — same pattern as Form 18 implants deducting
> `InventoryItem`. Don't build a parallel drug store.
> **(3) Fluids reuse Fluid Management** (`fluid_intake`/`fluid_output`, Form 10); **EBL is the same value**
> the Operation Record records (Form 18) — capture once, share.
> **(4) Intraop vitals are the high-frequency, device-fed sibling of `VitalSigns`** — separate
> `anaesthesia_vitals` (adds ETCO₂/ventilation, recorded every few minutes) but conceptually part of the
> **Vitals & Monitoring Engine**; early-warning wraps the existing `calculateEws`, not a new scorer.
> **New feeder gaps:** **Biomedical Device Integration** (AIMS auto-import from monitors/ventilators/pumps —
> BR-2) and **OT-status granularity** (an "Anaesthesia Started" sub-state; today `OtBooking` jumps
> `SCHEDULED → IN_PROGRESS` only at time-out, but anaesthesia starts *before* incision).

---

## 1. Form Overview
- **Department:** Anaesthesia (primary); OT, ICU, PACU, MRD (secondary)
- **Module:** **Operation Theatre → Anaesthesia Module → Live Anaesthesia Record** (real-time monitoring screen, not a PDF)
- **Filled By:** Anaesthesiologist (complete record); OT Nurse assists
- **Reviewed By:** Surgeon (major events), ICU/Recovery (handover)
- **Archived By:** MRD
- **Lifecycle:** starts at anaesthesia induction; completes before PACU; permanent after archival
- **NABH clause:** COP/PSQ — intraoperative anaesthesia monitoring record.

## 2. Purpose
- **Hospital use:** everything that happened to the patient *physiologically* during surgery (the anaesthesiologist's counterpart to the surgeon's Operation Record).
- **NABH requirement:** continuous documented anaesthesia monitoring.
- **Legal:** the first document reviewed on any intraoperative complication; protects patient/anaesthetist/surgeon/hospital.
- **Clinical:** drug + vital + event timelines drive intraop decisions and post-op handover.
- **Business rationale:** a real-time **AIMS** — device-fed, auto-plotted, low documentation burden, high accuracy.

## 3. Trigger
`WHO sign-in → **anaesthesia started** (record auto-starts, BR-1) → monitoring → time-out/incision → surgery → anaesthesia end → PACU`. Auto-starts on the "Anaesthesia Started" OT sub-state (gap §Read-first-4).

## 4. User Roles
| Actor | Capacity | HMS role |
|---|---|---|
| Anaesthesiologist | full record, finalize + sign | `DOCTOR` (anaesthetist flag) |
| OT Nurse | assisted entries | `NURSE` (OT) |
| Surgeon | read major events | `DOCTOR` |
| ICU Doctor | read (post-op) | `DOCTOR` (ICU) |
| Recovery Nurse | handover details | `NURSE` (PACU) |
| MRD | archive | `MRD_OFFICER` (gap) |

No new role gaps (anaesthesiologist flag already logged, Form 15).

## 5. Sections → storage
| § | Section | Capture | Storage / source |
|---|---|---|---|
| A | Patient info + ASA | UHID/name/age/gender/weight/height/**ASA grade**/surgeon/procedure/OT no | **auto** `Patient`/`OtBooking` + **`pre_anaesthesia_assessment`** (ASA/plan) |
| B | Anaesthesia info | type, induction method, airway device, ventilation mode, position, monitoring devices | `anaesthesia_record` |
| C | Drugs administered | propofol/fentanyl/rocuronium/sevoflurane… dose/route/time/anaesthetist | new **`anaesthesia_drugs`** → **deduct `Medicine` stock** (BR-3) |
| D | Vital monitoring | BP/pulse/SpO₂/ECG/temp/RR/**ETCO₂** every few min | new **`anaesthesia_vitals`** (high-frequency, device-fed sibling of `VitalSigns`) |
| E | Fluid management | IV/blood/plasma/urine/**EBL** | **reuse Fluid Mgmt** (`fluid_intake`/`fluid_output`, Form 10); EBL shared w/ Form 18 |
| F | Airway events | intubation/extubation/difficult airway/re-intubation/O₂ support | new **`anaesthesia_events`** (type=AIRWAY) |
| G | Intraoperative events | bradycardia/hypotension/cardiac arrest/bronchospasm/arrhythmia — timestamped | `anaesthesia_events` (type=CRITICAL) → notify (BR-4) |
| H | Recovery status | conscious, pain score, **Aldrete score**, stable, shift ICU/ward | new **`recovery_scores`** → feeds PACU (next form) |

## 6. Database Design
**`anaesthesia_record`** (new): `id, public_id, hospital_id, patient_id, admission_id, ot_booking_id (= surgery_request), pac_id (→ pre_anaesthesia_assessment), anaesthesiologist_id, anaesthesia_type, asa_grade, induction_time, completion_time, airway_type, ventilation_mode, status (ACTIVE/COMPLETED), signed_by, signed_at, created_at`.
**Related (new):** `anaesthesia_drugs` (+`medicine_id`, dose, route, time), `anaesthesia_vitals` (high-frequency + ETCO₂), `anaesthesia_events` (AIRWAY/CRITICAL, timestamped), `anaesthesia_fluids` (or reuse `fluid_intake`/`fluid_output`), `recovery_scores` (Aldrete).
- FK `ot_booking_id → ot_bookings`, `pac_id → pre_anaesthesia_assessment`, `patient_id`/`admission_id`. `hospital_id` on every row; index `(hospital_id, ot_booking_id)`, `(hospital_id, anaesthesia_record_id, recorded_at)` for the vitals trend.

## 7. Business Rules
- **BR-1** Record **auto-starts** when OT status → "Anaesthesia Started" (needs OT sub-state, §Read-first-4).
- **BR-2** Vitals recorded at **configurable intervals or auto-imported** from monitoring equipment (Biomedical Device Integration gap).
- **BR-3** All anaesthetic drugs **linked to `Medicine` inventory** (deduct stock).
- **BR-4** Critical intraop events **auto-notify OT team + ICU** (if transfer anticipated) — reuse `CdssAlertLog` + `WebSocketConfig`.
- **BR-5** **Recovery cannot begin until the anaesthesia record is completed** (gates PACU, next form).
- **BR-6** Every query filters `hospital_id`; every `{id}` validates ownership.

## 8. APIs
Under `/hospital/anaesthesia`; every `{id}` validates `hospital_id`.
| Verb | Path | Roles | Purpose |
|---|---|---|---|
| POST | `/hospital/anaesthesia/start` | ANAESTHETIST | start record (BR-1) |
| POST | `/hospital/anaesthesia/{id}/drug` | ANAESTHETIST | log drug (+ deduct stock) |
| POST | `/hospital/anaesthesia/{id}/vitals` | ANAESTHETIST, OT NURSE, device | record vitals (interval/auto) |
| POST | `/hospital/anaesthesia/{id}/event` | ANAESTHETIST | log airway/critical event |
| POST | `/hospital/anaesthesia/{id}/end` | ANAESTHETIST | complete + sign (gates recovery) |
| GET | `/hospital/anaesthesia/{admissionId}` | OT/ICU team | view record + timelines |

## 9. Permissions
| Role | Create | Edit | Finalize | View |
|---|---|---|---|---|
| Anaesthesiologist | Yes | Draft | Yes | Yes |
| OT Nurse | Assisted | Limited | No | Yes |
| Surgeon | No | No | No | Yes |
| ICU Doctor | No | No | No | Relevant |
| MRD | No | No | No | Archived |

Matches §8 `@PreAuthorize`.

## 10. Notifications
Anaesthesia started · drug administered · **critical event detected** · anaesthesia completed · ready for PACU · ICU transfer required. Reuse `WebSocketConfig` + `CdssAlertLog`.

## 11. Print Rules
Via [Signature & Document service](./shared/signature-and-document-service.md). `templates/anaesthesia-record.html`: patient details, anaesthesia type, **drug timeline**, **vital-sign timeline (plotted)**, airway management, intraop events, fluid balance, recovery status, anaesthesiologist signature, QR. Copy: file (MRD).

## 12. AI & Smart Enhancements — AIMS
- **Real-time early warning** — falling BP / dropping SpO₂ / rising ETCO₂ / tachycardia → immediate alert. **Extend `calculateEws`** (already scores BP/pulse/temp/RR/SpO₂) with ETCO₂ + trend velocity; reuse `CdssAlertLog`.
- **Automatic drug timeline** — chronological (08:05 propofol → 08:07 rocuronium → 08:10 intubation → 08:12 surgery). From `anaesthesia_drugs` + `anaesthesia_events`.
- **OT device integration** — import from multiparameter monitor / ventilator / syringe & infusion pumps / anaesthesia workstation (Biomedical Device Integration gap).
- **Recovery readiness** — before PACU, verify airway secure + stable vitals + pain controlled + acceptable Aldrete → "Ready for Recovery" (same readiness-gate pattern as OT; gates BR-5).

## 13. Validation
`asa_grade` from PAC; drug qty ≤ `Medicine` stock; vitals within physiologic bounds (Form 09 rules) + ETCO₂ range; events timestamped; `induction_time ≤ completion_time`; recovery-readiness required before completion; server-side only.

## 14. Audit Logs
Via `AuditLogService` (`entity_type="ANAESTHESIA_RECORD"`): started · drug logged (+ stock deduct) · vitals recorded (source: manual/device) · critical event + alert · completed/signed · recovery-readiness pass — user, role, timestamp, IP.

---

## Module & workflow placement
- **Owning module:** Operation Theatre → Anaesthesia Module (AIMS — the physiological execution record).
- **Creates:** `anaesthesia_record` + `anaesthesia_drugs`/`vitals`/`events`/`recovery_scores`. **Deducts:** `Medicine` stock (drugs). **Reuses:** `pre_anaesthesia_assessment` (ASA/plan), Fluid Mgmt (Form 10), `calculateEws` (early warning). **Shares:** EBL with Operation Record (Form 18). **Gates:** PACU/recovery (BR-5). **Prints:** anaesthesia chart. **Archives:** MRD.
- **Feeds into:** Operation Record (Form 18 anaesthesia link) · PACU (next form) · ICU · MRD · OT analytics. **Fed by:** WHO checklist (Form 17) · PAC (Form 15) · Pharmacy · biomedical devices.
- **New this form implies (add to README):** **AIMS / Anaesthesia Record** (`anaesthesia_record` + related) · **`anaesthesia_vitals`** = high-frequency device-fed channel of the Vitals & Monitoring Engine (early-warning extends `calculateEws` with ETCO₂/trend) · **Biomedical Device Integration** module (auto-import from OT monitors — genuinely new) · **OT-status granularity** ("Anaesthesia Started" sub-state; `OtBooking` currently jumps SCHEDULED→IN_PROGRESS only at time-out) · **Aldrete/recovery scoring** (feeds PACU).
