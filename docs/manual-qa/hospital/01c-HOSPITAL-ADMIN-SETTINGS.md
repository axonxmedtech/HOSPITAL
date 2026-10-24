# 01c — HOSPITAL ADMIN: SETTINGS (every card, with downstream effect)

**Baseline:** `aa143a7` · **Cases:** `TC-HAS-001` … `TC-HAS-044` · Tenant HOSPITAL_A · admin login

**Settings landing** (Administration ▸ Settings) is a card grid; each opens a `settingsView`:

| Card title (verbatim) | View            | Component                            | Backend                                                                            |
| --------------------- | --------------- | ------------------------------------ | ---------------------------------------------------------------------------------- |
| Operations Settings   | `operations`    | inline                               | `PUT /hospital/settings/operations` (+ `/nurse-login`, `/ot-incharge`, `/barcode`) |
| Vitals                | `vitals`        | `VitalsSettingsCard`                 | `/hospital/vitals`                                                                 |
| Ventilator Parameters | `ventilator`    | `VentilatorSettingsCard`             | `/hospital/icu/ventilator-parameters`                                              |
| Severity Scores       | `scores`        | `ScoreSettingsCard`                  | `/hospital/icu/score-types`                                                        |
| ICU Alert Thresholds  | `alerts`        | `AlertThresholdsCard`                | `/hospital/icu/alert-thresholds`                                                   |
| Print & Payment       | `print-payment` | `PrintPaymentSettingsCard`           | `PUT /hospital/settings/print-payment`                                             |
| Nursing Records       | `nursing`       | `FilesAndAccessCard` (nursing group) | `/hospital/form-access`                                                            |
| IPD Forms             | `ipd-forms`     | `FilesAndAccessCard`                 | `/hospital/form-access`                                                            |
| OT / Surgery Forms    | `ot-forms`      | `FilesAndAccessCard` (OT group)      | `/hospital/form-access`                                                            |
| OT Permissions        | `permissions`   | `OtPermissionsCard`                  | `/hospital/ot/permissions`                                                         |
| OT Policies           | `policies`      | `OtPoliciesCard`                     | `/hospital/ot/policies`                                                            |

Plus: **Fees** (Finance ▸ Fees, `01b TC-HAC-019`), **OT Theatres** (`OtRoomsCard`, Staff ▸ OT Theatres, `TC-HA-026`), **Plans** (`PlansTab` — **Super Admin only**, not in tenant Settings), **Hospital Inventory / Medicine Inventory** (`01 TC-HA-034/035`).

**Every setting case runs the same 9-step protocol:** ① record current ② change ③ Save ④ F5 ⑤ still changed ⑥ logout/login (where the value is in the JWT/user object) ⑦ go to the affected workflow ⑧ confirm behaviour changed ⑨ restore. Steps below state only the deltas.

**Permission rule:** every card's PUT with a non-admin token must be **403** — except the Operations/Print/Barcode/Nurse-login/OT-incharge endpoints, which are the Tranche-1 drift (`TC-API-006`). Cases `TC-HAS-040`…`044` cover this.

---

## A. OPERATIONS SETTINGS (`HospitalSettingDTO`)

### TC-HAS-001 — Reception Mode: HAS_RECEPTIONIST ↔ SOLO

`HOSPITAL_A · HOSPITAL_ADMIN → DOCTOR · Settings · Critical · Operations`
**Delta:** **Toggle Reception Mode** to `SOLO`.
**Downstream (⑦⑧):** doctor dashboard gains patient-registration / OPD-creation controls (doctor acts as own reception); reception dashboard behaviour unchanged for existing receptionists — record exactly which controls appear on `DoctorDashboard` under SOLO. Restore `HAS_RECEPTIONIST`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAS-002 — Billing Handler: RECEPTIONIST / DOCTOR / BOTH

`HOSPITAL_A · HOSPITAL_ADMIN → DOC, REC · Settings · Critical · Operations`
**Delta:** set `DOCTOR`; then `BOTH`; then back to `RECEPTIONIST`.
**Downstream:** who sees **Mark Paid** / payment controls on the bill after consultation — with `DOCTOR` reception's control is hidden and the doctor's shown; `BOTH` shows both; also `OpdPaymentFields` in the OPD form. Verify via `PUT /hospital/billing/{id}/status` with the "wrong" role token → record status (UI vs API).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAS-003 — Bill Payment Timing: LAST ↔ FIRST

`HOSPITAL_A · HOSPITAL_ADMIN → REC · Settings · Critical · Print & Payment`
**Delta:** `Before OPD` (`FIRST`).
**Downstream:** reception's Add OPD form now **requires** payment method (`CASH`, …) and reference; the OPD is created with the bill already PAID; under `After OPD` (`LAST`) no payment fields and the bill is PENDING after consultation. Restore.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAS-004 — In-Clinic toggle

`HOSPITAL_A · HOSPITAL_ADMIN → DOCTOR · Settings · High · Operations`
**Delta:** In-Clinic **OFF**.
**Downstream:** **In-Clinic Presets** tab hides live (WS); doctor's consultation loses the in-clinic medicines/procedures section; `printInClinic` irrelevant. Restore ON.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAS-005 — Barcode Workflow toggle

`HOSPITAL_A · HOSPITAL_ADMIN → PHARMACIST · Settings · Medium · Operations`
**Delta:** **Toggle Barcode Workflow** OFF.
**Downstream:** pharmacist Billing Counter / Inventory barcode scan field hidden; ON restores it. (`PUT /hospital/settings/barcode`.)
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAS-006 — Separate Nurse Login toggle

`HOSPITAL_A · HOSPITAL_ADMIN → NURSE, NI · Settings · Critical · Operations`
**Delta:** ON → OFF → ON.
**Downstream:** OFF: staff nurses cannot log in (existing nurse sessions — record whether revoked immediately); incharge's clinical forms show **Performed By Nurse** picker; patient auto-assign rules change (ON + exactly one ward nurse → auto-assign). ON: nurse login works, picker hidden. See `TC-VIS-015`, `TC-HN-001`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAS-007 — OT Incharge toggle

`HOSPITAL_A · HOSPITAL_ADMIN → OTI · Settings · High · Operations`
**Delta:** OFF.
**Downstream:** OT Incharge tab / creation hidden; existing `ot.hospa@qa.test` login — record (revoked or not); scheduling falls to reception. Restore ON.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAS-008 — Operations settings persistence & broadcast

`HOSPITAL_A · HOSPITAL_ADMIN · Settings · High · Operations`
**Steps:** change two toggles; Save once; F5; logout/login; second admin tab open beforehand.
**Expected:** `Settings updated`; both persist; the other tab receives `SETTINGS_UPDATED` and re-renders without reload.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## B. PRINT & PAYMENT

### TC-HAS-009 — Print toggles: Case Paper / Bill / Prescription / In-Clinic Medicines

`HOSPITAL_A · HOSPITAL_ADMIN → DOC, REC · Settings · High · Print & Payment`
**Delta:** turn **Prescription** OFF.
**Downstream:** after a consultation the print-prescription control is hidden for doctor and reception; the PDF endpoint still answers (record) — this is a UI print policy, not authorization. Repeat for Case Paper, Bill, In-Clinic Medicines. Restore all ON.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## C. VITALS (`VitalsSettingsCard`)

### TC-HAS-010 — Toggle a built-in vital off → OPD form and case paper

`HOSPITAL_A · HOSPITAL_ADMIN → REC, DOC · Settings · High · Vitals`
**Delta:** turn **SPO2** off (`Vitals updated`).
**Downstream:** reception Add OPD form no longer shows SpO2; doctor's ConsultationModal vitals strip omits it; case-paper PDF VITAL SIGNS table omits it; **server drops** a submitted `spo2` value (`OpdService` uses `enabledBuiltInKeys`) — send it anyway via API and confirm it is not stored. Built-ins **cannot be deleted** (no delete control). Restore.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAS-011 — Add custom vital → capture → PDF → delete keeps history

`HOSPITAL_A · HOSPITAL_ADMIN → REC · Settings · High · Vitals`
**Delta:** add `QA Pain Score` (type `TEXT`) → `Vital added`; blank name → `Enter a vital name`.
**Downstream:** appears in OPD form; value saved into `opd.custom_vitals`; shows on case paper; **no validation** on custom values (enter `abc`); delete → `Vital deleted`; historical OPD still shows the value.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAS-012 — Built-in validation intact

`HOSPITAL_A · RECEPTIONIST · OPD · Medium · Add OPD`
**Steps:** BP `120-80`, BP `80/120`, temp `-1`, pulse `abc`, weight `-5`, SpO2 `-3`.
**Expected:** each rejected with the specific message (e.g. `Blood pressure must be in format Systolic/Diastolic, e.g., 120/80`, `Systolic blood pressure must be greater than diastolic blood pressure`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## D. FILES & ACCESS (Nursing Records · IPD Forms · OT / Surgery Forms)

### TC-HAS-013 — Nursing Records: VITALS → Doctor only

`HOSPITAL_A · HOSPITAL_ADMIN → NURSE, DOC · Settings · Critical · Nursing Records`
**Delta:** VITALS access `Doctor` (`Form access updated`).
**Downstream:** nurse's Vitals tab shows records, **no entry form**; doctor's IpdDetails Vitals shows the form; nurse token `POST /hospital/nurse/vitals` → **403** (`assertCanEdit`). Restore `Both`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAS-014 — Nursing Records: NOTES off → hidden everywhere

`HOSPITAL_A · HOSPITAL_ADMIN → all · Settings · High · Nursing Records`
**Delta:** NOTES (Re-Assessment Sheet) **Off**.
**Downstream:** Notes sub-tab absent for nurse and doctor; `GET /hospital/form-access/effective` returns `HIDDEN` for NOTES; existing notes are not deleted (restore → they reappear).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAS-015 — Each of the 20 form keys can be set Off / Doctor / Nurse / Both

`HOSPITAL_A · HOSPITAL_ADMIN · Settings · High · Files & Access`
**Steps:** for VITALS, NOTES, INITIAL_ASSESSMENT, VULNERABILITY_ASSESSMENT, SUGAR_CHART + the 15 OT keys: cycle the selector; Save; F5.
**Expected:** all persist; NURSE_INCHARGE behaves as Nurse; admin always editable; a form with **no row** defaults to enabled + Both (delete an override via API if exposed and confirm the default returns).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAS-016 — OT / Surgery Forms access → consent form editing

`HOSPITAL_A · HOSPITAL_ADMIN → NURSE · Settings · High · OT / Surgery Forms`
**Delta:** `WHO_CHECKLIST` → Doctor only.
**Downstream:** nurse's Consent Forms tab shows WHO checklist read-only/printable; doctor can fill; nurse token `POST /hospital/surgery-forms` for that type → 403.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAS-017 — IPD Forms view

`HOSPITAL_A · HOSPITAL_ADMIN · Settings · Medium · IPD Forms`
**Steps:** open; list every key shown; compare with Nursing Records and OT / Surgery Forms views.
**Expected:** the three views partition the same 20 keys with no key missing or duplicated; record the exact grouping.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## E. ICU CARDS (ICU module)

### TC-HAS-018 — Ventilator Parameters: add SETTING / OBSERVATION / MODE

`HOSPITAL_A · HOSPITAL_ADMIN → NURSE · Settings · High · Ventilator Parameters`
**Delta:** **Add parameter** `QA PEEP` (SETTING, unit cmH2O); blank → `Enter a parameter name` / `Enter a display name`.
**Downstream:** nurse's Ventilator panel on an ICU patient shows `QA PEEP` under `Ventilator Settings`; an OBSERVATION lands under `Ventilator Observations / Measurements`; disabling a parameter hides it for new entries, keeps history. Restore.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAS-019 — Severity Scores: enable/disable a score type, `Total only`

`HOSPITAL_A · HOSPITAL_ADMIN → NURSE · Settings · High · Severity Scores`
**Delta:** disable one score type; set another to `Total only`.
**Downstream:** nurse's Severity Scores panel omits the disabled type; the total-only type captures a single number instead of components; ICU dashboard shows the score. Restore.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAS-020 — ICU Alert Thresholds → dashboard alert

`HOSPITAL_A · HOSPITAL_ADMIN → NURSE · Settings · Critical · ICU Alert Thresholds`
**Delta:** set SpO2 low threshold to 95.
**Downstream:** nurse records SpO2 92 on the ICU patient → ICU Dashboard / stay card flags the alert; at 96 no flag. Non-numeric → `Could not save the threshold`. Restore.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAS-021 — ICU cards hidden without ICU module

`HOSPITAL_M · HOSPITAL_ADMIN · Settings · Medium · Settings`
**Steps:** Hospital M Settings grid.
**Expected:** Ventilator Parameters, Severity Scores, ICU Alert Thresholds cards absent; API 403 (`TC-MOD-017`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## F. OT CARDS (OT module)

### TC-HAS-022 — OT Permissions grid → runtime denial

`HOSPITAL_A · HOSPITAL_ADMIN → DOCTOR · Settings · Critical · OT Permissions`
**Delta:** columns Hospital Admin / Doctor / Receptionist / Nurse / Nurse Incharge / OT Incharge × the 16 permissions; untick `OT_CREATE` for Doctor; **Save**.
**Downstream:** = `TC-PERM-011`. **Reset to defaults** → `OT permissions reset to defaults`; record the default grid verbatim (it is the product's baseline).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAS-023 — OT Permissions: revoke OT_VIEW from OT Incharge

`HOSPITAL_A · HOSPITAL_ADMIN → OTI · Settings · High · OT Permissions`
**Delta:** untick `OT_VIEW` for OT Incharge.
**Downstream:** `ot.hospa@qa.test` OT Board empty/denied; `GET /hospital/surgeries` → 403. Restore.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAS-024 — OT Policies presets: Small / Medium / Large / Corporate-NABH

`HOSPITAL_A · HOSPITAL_ADMIN → all OT roles · Settings · Critical · OT Policies`
**Delta:** apply **Small hospital** → `OT policies saved`; then **Corporate / NABH**.
**Downstream:** the surgery lifecycle gates change: `APPROVAL_MODE` (approve step required?), `WHO_CHECKLIST_MODE`, `PRE_OP_CHECKLIST`, `ANAESTHESIA_CLEARANCE`, `FINANCIAL_CLEARANCE`, `RECOVERY_TRACKING`, `CANCELLATION_REASON`, `TEAM_CAPTURE` — each per scope `ANY/ELECTIVE/EMERGENCY`. Under Corporate/NABH attempt **Start** without pre-op checklist and without anaesthesia clearance → refused; under Small → allowed (or `ADVISORY`). Record the exact matrix observed. Restore.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAS-025 — OT Policies per-key edit and scope

`HOSPITAL_A · HOSPITAL_ADMIN · Settings · High · OT Policies`
**Delta:** set `CANCELLATION_REASON` required for `ELECTIVE` only.
**Downstream:** cancelling an elective surgery demands a reason (`CancellationReasons`); an emergency one does not.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAS-026 — OT Theatres (`OtRoomsCard`) CRUD + validation

`HOSPITAL_A · HOSPITAL_ADMIN · Settings · High · OT Theatres`
**Steps:** add (`Theatre added`), blank name (`Theatre name is required`), edit (`Theatre updated`), remove (`Theatre removed`), status `AVAILABLE`.
**Downstream:** = `TC-HA-026`; a theatre with a scheduled surgery — record whether removal is refused.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAS-027 — OT cards hidden without OT module

`HOSPITAL_M · HOSPITAL_ADMIN · Settings · Medium · Settings`
**Expected:** OT Permissions, OT Policies, OT / Surgery Forms cards absent; APIs 403.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## G. FEES · SUBSCRIPTION · PROFILE

### TC-HAS-028 — Fees & custom fees (see `TC-HAC-019`)

`HOSPITAL_A · HOSPITAL_ADMIN · Fees · Critical · Finance ▸ Fees`
**Steps:** execute `TC-HAC-019`; plus: negative fee, blank name for custom fee, deactivate custom fee (`HospitalFeeController` active toggle).
**Expected:** validation; deactivated custom fee disappears from bill-item pickers; historical bills keep it.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAS-029 — Subscription view

`HOSPITAL_A · HOSPITAL_ADMIN · Subscription · Medium · Settings / header`
**Steps:** open subscription info (`GET /hospital/subscription`); Super Admin changes plan; refresh.
**Expected:** plan name, billing period, modules listed; updates after reassignment; read-only.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAS-030 — Hospital profile / logo (ProfileModal)

`HOSPITAL_A · HOSPITAL_ADMIN · Profile · Medium · Navbar ▸ Profile`
**Steps:** update hospital display name/logo (Cloudinary upload if configured); F5; print a case paper.
**Expected:** header and PDF header reflect the change; without Cloudinary env the upload shows a readable error, not a crash.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## H. SETTINGS UI

### TC-HAS-031 — Settings grid renders one card per enabled area

`HOSPITAL_A · HOSPITAL_ADMIN · Settings · Medium · Settings`
**Expected:** exactly the 11 cards in the table above for a full plan; each has title + description; back link returns to the grid.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAS-032 — Save button states / double-click / unsaved navigation

`HOSPITAL_A · HOSPITAL_ADMIN · Settings · Medium · any card`
**Steps:** change a value; double-click Save (`Saving…` disables); change, then click another card without saving; return.
**Expected:** one PUT; unsaved change is discarded on navigation (record whether a prompt exists — likely not; expected).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAS-033 — Settings load-error states

`HOSPITAL_A · HOSPITAL_ADMIN · Settings · Low · ICU cards`
**Steps:** stop backend; open each ICU card.
**Expected:** `Could not load alert thresholds` / `Could not load severity scores` / `Could not load ventilator parameters`; no crash; retry after restart.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAS-034 — Settings on F5 return to grid (global rule)

`HOSPITAL_A · HOSPITAL_ADMIN · Settings · Low · Settings`
**Expected:** F5 inside a card → Overview (tab) — expected; not a bug.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAS-035 — Two admins editing the same setting

`HOSPITAL_A · HOSPITAL_ADMIN ×2 · Settings · Medium · Operations`
**Steps:** create a second admin if the UI allows (else use two tabs of the same admin); tab 1 sets Billing Handler `DOCTOR`, tab 2 (stale) sets `BOTH`.
**Expected:** last write wins; the losing tab is refreshed by `SETTINGS_UPDATED`; no 500.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAS-036 — Vitals card: duplicate custom name / long name

`HOSPITAL_A · HOSPITAL_ADMIN · Settings · Low · Vitals`
**Steps:** add `QA Pain Score` twice; add a 100-char name.
**Expected:** duplicate refused or de-duplicated (record); long name truncates in the OPD form without breaking layout.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAS-037 — Settings changes are audited

`HOSPITAL_A · HOSPITAL_ADMIN · Audit · Medium · Audit Logs`
**Steps:** after A–F, open Audit Logs.
**Expected:** entries for settings/fees/form-access/OT permission/OT policy changes with actor and timestamp (record which cards are and are not audited — informational).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAS-038 — Settings are per-tenant

`HOSPITAL_A + B · HOSPITAL_ADMIN · Settings · Critical · all`
**Steps:** = `TC-ISO-037` for fees, plus: A sets Bill Payment `FIRST`, B reads its own.
**Expected:** B unchanged (`LAST`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAS-039 — Restore checklist executed

`HOSPITAL_A · HOSPITAL_ADMIN · Settings · High · all`
**Steps:** after this document, re-read every card and confirm it equals the value recorded in step ①.
**Expected:** all restored; otherwise later tranches run against a mutated configuration.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## I. PERMISSIONS ON SETTINGS ENDPOINTS

### TC-HAS-040 — Form access, vitals, OT permissions/policies/rooms, ICU cards: non-admin PUT → 403

`HOSPITAL_A · REC, DOC, NURSE, PHA · Settings · Critical · API`
**Steps:** PUT `/hospital/form-access`, `/hospital/vitals` (toggle), `/hospital/ot/permissions`, `/hospital/ot/policies`, `/hospital/ot/rooms`, `/hospital/icu/alert-thresholds`, `/hospital/icu/score-types`, `/hospital/icu/ventilator-parameters` with each non-admin token.
**Expected:** 403 for all. (`VitalSettings` PUT admits `ADM DOC REC NUR NI` per matrix — record; `IcuVentilatorParameter` PUT likewise — record.)
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAS-041 — ⚠️ Operations / Print / Barcode / Nurse-login / OT-incharge: non-admin PUT

`HOSPITAL_A · REC, NURSE, PHA · Settings · Critical · API`
**Steps:** = `TC-API-006`. Record here.
**Expected (intent):** 403. **Code:** no `@PreAuthorize` → likely 200 = **Critical** drift.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAS-042 — Non-admin cannot see the Settings tab

`HOSPITAL_A · REC, DOC, NURSE, PHA, NI, OTI · Settings · High · sidebar`
**Expected:** no Settings tab on any non-admin dashboard.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAS-043 — Staff read `effective` form access only

`HOSPITAL_A · NURSE · Settings · High · API`
**Steps:** nurse token `GET /hospital/form-access` (admin list) vs `GET /hospital/form-access/effective`.
**Expected:** 403 then 200 with the verdict map (`HIDDEN`/`READ_ONLY`/`EDITABLE`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAS-044 — Settings endpoints across tenants

`HOSPITAL_B · HOSPITAL_ADMIN · Settings · Critical · API`
**Steps:** B admin PUTs `/hospital/form-access` with A's form ids / `/hospital/ot/rooms/{A_id}`.
**Expected:** 403/404; A unchanged.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
