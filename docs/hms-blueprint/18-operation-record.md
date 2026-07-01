# Form Spec — Operation Record / Intraoperative Record (OT Execution Engine)

| | |
|---|---|
| **Status** | Draft |
| **Source** | pasted form analysis — *VH/NABH/OT/04/2026* (2026-07-01) |
| **Existing code?** | **`operation_record` is new — but it fans out to existing subsystems, not new ones.** Timings auto-capture from [`OtChecklist`](../../backend/src/main/java/com/hms/entity/OtChecklist.java) phase timestamps; team/schedule from [`OtBooking`](../../backend/src/main/java/com/hms/entity/OtBooking.java); implants/consumables deduct from existing [`InventoryItem`](../../backend/src/main/java/com/hms/entity/InventoryItem.java)/[`HospitalInventory`](../../backend/src/main/java/com/hms/entity/HospitalInventory.java); specimens create [`LabOrder`](../../backend/src/main/java/com/hms/entity/LabOrder.java) (pathology); fluids → Fluid Management (Form 10); post-op → `DoctorOrder`/`NurseTask`. |

> **Read first — the Operation Record is a *hub*: capture once, fan out to systems that already exist.**
> The architect's point ("captured once and automatically shared with Inventory, Billing, Pathology, ICU,
> Pharmacy, MRD") maps onto existing rails — reconcile, don't rebuild:
> - **Implants/consumables (Section G, BR-3)** → deduct from the **existing** `InventoryItem`/`HospitalInventory`
>   (and `Medicine.stockQuantity`) — add implant **batch/manufacturer traceability**, don't build a parallel inventory.
> - **Specimens (Section I, BR-4)** → auto-create **`LabOrder`** (pathology order type) via the existing order chain; results return as `LabResult`.
> - **Blood loss / fluids (Section H)** → Blood Bank (Form 01 gap) + **Fluid Management** (`fluid_intake`, [Form 10](./10-intake-output-chart.md)).
> - **Post-op instructions (Section K)** → generate **`DoctorOrder`**/`NurseTask` (same smart-linking as [Form 13](./13-doctor-progress-notes.md)).
> - **Timings (Section C)** → derive from `OtChecklist` (`sign_in_at`≈entered, `time_out_at`≈incision, `sign_out_at`≈end) + `OtBooking` — not manual.
> - **Instrument count (BR-2)** → the **Form 17 sign-out count** (`ot_checklist_items`).
> **Genuinely new:** `operation_record` + related tables, implant traceability fields, and **Incident Reporting** (BR-6 — no incident entity exists).

---

## 1. Form Overview
- **Department:** OT (primary); Surgeon, Anaesthesia, OT Nurse, CSSD, Blood Bank, ICU, MRD (secondary)
- **Module:** **Operation Theatre → Intraoperative Documentation** (structured workflow, not free-text)
- **Filled By:** Primary Surgeon (findings/procedure); Scrub Nurse (implants/counts); Circulating Nurse (OT docs)
- **Reviewed By:** Assistant Surgeon, Anaesthesiologist (linked record)
- **Archived By:** MRD
- **Lifecycle:** created after WHO time-out (patient Inside OT); finalized on close; permanent after archival
- **NABH clause:** COP/PSQ — the operative record; core medico-legal surgical document.

## 2. Purpose
- **Hospital use:** official record of what surgery was performed, by whom, findings, implants, complications, blood loss, specimens, timings.
- **NABH requirement:** documented operative note per surgery.
- **Legal:** one of the most-scrutinized medico-legal documents; implant traceability supports recalls.
- **Clinical:** drives post-op care (ICU/orders) and the surgical timeline.
- **Business rationale:** the **central execution record** — capture once, share to Inventory/Billing/Pathology/ICU/Pharmacy/MRD.

## 3. Trigger
`WHO checklist (time-out) → anaesthesia → operation start → intraoperative documentation → operation complete → shift to PACU`. Created once the booking is `IN_PROGRESS` (Form 17 auto-sets this on time-out).

## 4. User Roles
| Actor | Capacity | HMS role |
|---|---|---|
| Primary Surgeon | operative findings/procedure, finalize + sign | `DOCTOR` |
| Assistant Surgeon | notes | `DOCTOR` |
| Anaesthesiologist | linked anaesthesia section | `DOCTOR` (anaesthetist flag) |
| Scrub Nurse | instruments/implants/counts | `NURSE` (scrub) |
| Circulating Nurse | OT documentation | `NURSE` |
| OT Technician | equipment | technician capacity |
| MRD | archive | `MRD_OFFICER` (gap) |

No new role gaps.

## 5. Sections → storage
| § | Section | Capture | Storage / source |
|---|---|---|---|
| A | Patient info | UHID/IPD/name/age/gender/ward/bed/surgeon/anaesthetist/OT no | **auto** `Patient`/`OtBooking`/`IpdBedHistory` |
| B | Procedure info | scheduled vs **actual** procedure, category, elective/emergency, specialty, OT room | `operation_record` + `OtBooking` |
| C | Operation timing | entered OT / anaesthesia start / surgery start / incision / closure / end / shifted | **auto** from `OtChecklist` timestamps; store on `operation_record` |
| D | Surgical team | primary/assistant surgeon, anaesthetist, scrub/circulating nurse, technician | new **`operation_team`** (`OtBooking` has only surgeon + anaesthetist name) |
| E | Operative findings | structured (inflamed appendix, GB stones…) + narrative | `operative_findings` |
| F | Procedure performed | steps, techniques, devices, intraop decisions | `actual_procedure` + notes |
| G | Implants & consumables | mesh/plate/screw/prosthesis + batch/manufacturer/qty/cost | new **`operation_implants`**/`operation_consumables` → **deduct `InventoryItem`** (BR-3) |
| H | Blood loss | EBL, blood transfused, fluids | `estimated_blood_loss` + Blood Bank + Fluid Mgmt (Form 10) |
| I | Specimens | type/container/label/destination lab | new **`operation_specimens`** → auto-create **`LabOrder`** (BR-4) |
| J | Complications | coded (bleeding/cardiac/equipment/organ injury/none) | new **`operation_complications`** → Incident Reporting (BR-6) |
| K | Post-op instructions | ICU/O₂/NPO/antibiotics/drain/pain/physio | `post_op_plan` → generate **`DoctorOrder`**/`NurseTask` |

## 6. Database Design
**`operation_record`** (new): `id, public_id, hospital_id, patient_id, admission_id, ot_booking_id (→ ot_bookings; = surgery_request/ot_schedule), surgeon_id, procedure_name, actual_procedure, operative_findings, estimated_blood_loss, complications_summary, post_op_plan, operation_start, operation_end, status (DRAFT/FINALIZED), signed_by, signed_at, created_at`.
**Related (new):** `operation_team`, `operation_implants` (+`inventory_item_id`, batch, manufacturer, qty, cost — traceability), `operation_consumables` (+`inventory_item_id`), `operation_specimens` (+`lab_order_id`), `operation_complications` (coded).
- FK `ot_booking_id → ot_bookings`, `patient_id → patients`, `admission_id → ipd_admissions`. `hospital_id` on every row; index `(hospital_id, ot_booking_id)`.

## 7. Business Rules
- **BR-1** Cannot create until **WHO checklist complete + patient Inside OT** (`OtChecklist` time-out done → booking `IN_PROGRESS`).
- **BR-2** Cannot **finalize/close** without: final procedure · surgeon signature · **instrument count confirmed** (Form 17 sign-out count) · post-op plan.
- **BR-3** Implants used **auto-deduct `InventoryItem` stock** (reuse existing inventory; low-stock alert via `minStockLevel`).
- **BR-4** Specimens collected **auto-generate `LabOrder`** (pathology).
- **BR-5** Blood transfusion **must match Blood Bank records** (Form 01 gap).
- **BR-6** Intraoperative complication → **auto-create Quality Incident review** if hospital policy configured (Incident Reporting — new).
- **BR-7** Every query filters `hospital_id`; every `{id}` validates ownership.

## 8. APIs
Under `/hospital/ot/operation-record`; every `{id}` validates `hospital_id`.
| Verb | Path | Roles | Purpose |
|---|---|---|---|
| POST | `/hospital/ot/operation-record` | SURGEON | create (BR-1 gate) |
| GET | `/hospital/ot/operation-record/{id}` | OT team | view |
| PUT | `/hospital/ot/operation-record/{id}` | SURGEON, OT NURSE(sections) | edit draft |
| POST | `/hospital/ot/operation-record/{id}/finalize` | SURGEON | finalize + sign (BR-2 gate) |
| GET | `/hospital/ot/operation-record/{id}/print` | all | PDF |

## 9. Permissions
| Role | Create | Edit | Finalize | View |
|---|---|---|---|---|
| Surgeon | Yes | Draft | Yes | Yes |
| Assistant Surgeon | Notes | No | No | Yes |
| OT Nurse | Nursing sections | Limited | No | Yes |
| Anaesthesiologist | Anaesthesia link | Own section | No | Yes |
| MRD | No | No | No | Archived |

Matches §8 `@PreAuthorize`.

## 10. Notifications
Surgery started · implant used · blood requested · specimen sent · surgery completed · ICU bed required · recovery ready. Reuse `WebSocketConfig` + OT `broadcast`.

## 11. Print Rules
Via [Signature & Document service](./shared/signature-and-document-service.md). `templates/operation-record.html`: patient, procedure, team, timings, findings, implants, blood loss, specimens, complications, post-op instructions, surgeon signature, QR, version. Copy: file (MRD; Form 02 checklist `operation_record` item).

## 12. AI & Smart Enhancements
- **Automatic operative-note draft** — assemble from procedure + implant records + OT timestamps + anaesthesia record + nursing docs; surgeon reviews/signs. Extends the smart-summary pattern (Forms 13/14).
- **Implant traceability** — every implant linked to patient + batch + manufacturer + surgery date (recall support) via `operation_implants`.
- **OT efficiency analytics** — avg duration, turnaround, delay reasons, blood/implant utilization, complication rates (from timings + records).
- **Surgical timeline** — entered→anaesthesia→incision→closure→completed→recovery, part of the unified clinical timeline (Forms 11/13).

## 13. Validation
`status ∈ {DRAFT,FINALIZED}`; finalize blocked unless BR-2 conditions met (server-enforced); implant qty ≤ stock; specimen requires destination lab; complications coded; timings monotonic (start ≤ incision ≤ closure ≤ end); server-side only.

## 14. Audit Logs
Via `AuditLogService` (`entity_type="OPERATION_RECORD"`): created · edited · implant used (+ inventory deduct) · specimen sent (+ lab order) · complication logged (+ incident) · finalized/signed — user, role, timestamp, old→new, IP.

---

## Module & workflow placement
- **Owning module:** Operation Theatre → Intraoperative Documentation (OT Execution Engine — the central surgical record).
- **Creates:** `operation_record` + `operation_team`/`implants`/`consumables`/`specimens`/`complications`. **Deducts:** `InventoryItem` stock (BR-3). **Generates:** `LabOrder` (specimens), `DoctorOrder`/`NurseTask` (post-op), Quality Incident (complications). **Reads:** `OtChecklist` (timings + counts), `OtBooking` (team/schedule), Blood Bank, Fluid Mgmt (Form 10). **Prints:** operative note. **Archives:** MRD.
- **Feeds into:** Inventory · Billing (implants/consumables cost) · Pathology (`LabOrder`) · ICU · Pharmacy (post-op) · PACU (next form) · MRD · OT analytics. **Fed by:** WHO checklist (Form 17) · scheduling · anaesthesia record (next form).
- **New this form implies (add to README):** **Operation Record / OT Execution Engine** (`operation_record` + related) · **implant/consumable traceability** extends existing `InventoryItem`/`HospitalInventory` (auto-deduct — **not** a new inventory) · **specimens → `LabOrder`** (pathology is a lab order type, not a new module) · **Incident Reporting module** (genuinely new — BR-6 complication → quality review) · surgical timeline joins the unified clinical timeline.
