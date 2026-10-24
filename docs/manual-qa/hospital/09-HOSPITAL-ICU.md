# 09 — HOSPITAL ICU

**Baseline:** `aa143a7` · **Cases:** `TC-HICU-001` … `TC-HICU-022` · Tenant HOSPITAL_A · **ICU module** (`@RequireModule("ICU")` on 9 handlers) + `@TenantType(HOSPITAL)` on the dashboard/stay controllers

**Screens:** Admin/Reception/Doctor ▸ **ICU Dashboard**, **ICU Bed Board** · Nurse ▸ **ICU Beds** · Incharge ▸ **ICU Beds** · patient-level panels inside `NursePatientDetail`/`IpdDetails`: **Intake / Output**, **Ventilator**, **Severity Scores**, infusions.
**Settings:** Ventilator Parameters · Severity Scores · ICU Alert Thresholds (`01c` §E).
**Endpoints:** `/hospital/icu` (dashboard, stays) · `/hospital/icu/alert-thresholds` · `/hospital/icu/score-types` · `/hospital/icu/ventilator-parameters` · nurse-side `/hospital/nurse/io`, `/ventilator`, `/severity-scores`, `/infusions`.

> **ICU depends on IPD** — an ICU stay hangs off an inpatient admission. `TC-HICU-021` is the runtime test for the open question "can ICU function without IPD?". **Do not answer it from assumption.**

---

## A. SETUP → ICU ADMISSION

### TC-HICU-001 — ICU ward and beds

`HOSPITAL_A · HOSPITAL_ADMIN · ICU · High · Wards & Beds`
**Steps:** confirm `ICU Ward` with beds `ICU-01`, `ICU-02` exists and has an incharge; check both appear on the ICU Bed Board as available.
**Expected:** ICU beds are ordinary beds in an ICU ward; bed states are the same four (`available/occupied/cleaning/maintenance`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HICU-002 — Admit into ICU creates an ICU stay

`HOSPITAL_A · RECEPTIONIST · ICU · Critical · IPD admit`
**Steps:** 1. Doctor requests admission. 2. Reception admits P1 to `ICU Ward` / `ICU-01`. 3. Open ICU Dashboard and ICU Bed Board.
**Expected:** `ICU-01` occupied; P1 appears on the dashboard as an ICU stay with admission date; the stay links to the IPD admission (`IcuStayCard`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HICU-003 — Backfilled stays for existing occupants

`HOSPITAL_A · HOSPITAL_ADMIN · ICU · Medium · ICU Dashboard`
**Steps:** if a patient was already in an ICU ward before ICU was enabled, enable ICU and restart the backend.
**Expected:** `backfillIcuStaysForCurrentOccupants` creates the stay; the dashboard shows the existing occupant rather than an empty board.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## B. DASHBOARD & BED BOARD

### TC-HICU-004 — ICU Dashboard content and accuracy

`HOSPITAL_A · HOSPITAL_ADMIN · ICU · High · ICU Dashboard`
**Steps:** with 2 ICU patients, read every tile and card; compare with a hand count of ICU occupants, free beds and latest scores.
**Expected:** occupancy, patient list, latest severity score and alerts per patient; figures reconcile; `GET /hospital/icu` is the source; EmptyState when no ICU patients.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HICU-005 — ICU Bed Board

`HOSPITAL_A · RECEPTIONIST · ICU · High · ICU Bed Board`
**Steps:** open; verify each bed tile's state and occupant; discharge one patient; re-check.
**Expected:** tiles show `Available`/`Occupied`/`Cleaning Required`/`Under Maintenance`; after discharge the bed shows cleaning until the incharge marks it cleaned (`TC-HNI-014`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HICU-006 — Role visibility of ICU screens

`HOSPITAL_A · all · ICU · High · ICU`
**Steps:** open ICU Dashboard / Bed Board as admin, doctor, reception, nurse, incharge, pharmacist, OT incharge.
**Expected:** `ADM DOC REC NUR NI` can read (`IcuDashboard` GET); PHA and OTI have no ICU tab and `GET /hospital/icu` → 403.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## C. CLINICAL ICU RECORDING

### TC-HICU-007 — Intake / Output

`HOSPITAL_A · NURSE · ICU · High · Intake / Output`
**Steps:** = `TC-HN-014`: add intake and output entries; check running totals and the balance; add a correction.
**Expected:** entries timestamped with performer; totals correct; corrections are new rows, not silent edits (`IcuIoService` correction flow).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HICU-008 — Ventilator recording follows configured parameters

`HOSPITAL_A · NURSE · ICU · High · Ventilator`
**Steps:** = `TC-HN-015` after `TC-HAS-018`: record settings and observations; disable a parameter and re-open.
**Expected:** only enabled parameters offered, grouped as `Ventilator Settings` vs `Ventilator Observations / Measurements`; history preserved for disabled ones.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HICU-009 — Severity scores

`HOSPITAL_A · NURSE · ICU · High · Severity Scores`
**Steps:** = `TC-HN-016`: record a component-based score and a `Total only` score; view the latest score on the dashboard and stay card.
**Expected:** score computed/stored; latest value surfaces on `IcuStayCard`; disabled types absent.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HICU-010 — Infusions

`HOSPITAL_A · NURSE · ICU · High · Infusions`
**Steps:** = `TC-HN-017`: start, rate change, stop; view history.
**Expected:** rate changes recorded as a series; stopping ends the infusion; corrections supported.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HICU-011 — ⭐ Alert threshold breach surfaces

`HOSPITAL_A · HOSPITAL_ADMIN → NURSE · ICU · Critical · ICU Dashboard`
**Steps:** = `TC-HAS-020`: set SpO2 low threshold 95; nurse records SpO2 92; open the ICU Dashboard and the stay card; then record 97.
**Expected:** the breach is flagged on the dashboard/stay card while out of range and clears when back in range. A threshold that never fires makes the ICU dashboard useless — **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HICU-012 — Doctor updates an ICU stay

`HOSPITAL_A · DOCTOR · ICU · High · ICU`
**Steps:** `PUT /hospital/icu/{stayId}` (or the UI) to update stay details; then try the same as a nurse.
**Expected:** `ADM DOC` allowed; nurse **403** (stay PUT is `ADM DOC`) while nurse **clinical entries** remain allowed — confirm the split.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HICU-013 — ICU records obey Files & Access

`HOSPITAL_A · HOSPITAL_ADMIN → NURSE · ICU · High · Settings`
**Steps:** set `VENTILATOR` / `SEVERITY_SCORE` form access to Doctor only; nurse opens the panels; nurse POSTs.
**Expected:** read-only panel, entry hidden, API 403 (these keys exist in `FormRegistry`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## D. ICU STAY LIFECYCLE

### TC-HICU-014 — Transfer out of ICU to a general ward

`HOSPITAL_A · RECEPTIONIST/DOCTOR · ICU · Critical · /ipd/:id`
**Steps:** **change bed** from `ICU-01` to `GA-02`; re-check ICU Dashboard, ICU Bed Board and Wards & Beds.
**Expected:** ICU stay ends (or is marked transferred — record); `ICU-01` → cleaning; `GA-02` occupied; the patient leaves the ICU dashboard.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HICU-015 — Transfer into ICU from a general ward

`HOSPITAL_A · RECEPTIONIST · ICU · High · /ipd/:id`
**Steps:** move a general-ward patient to `ICU-02`.
**Expected:** an ICU stay is created; the patient appears on the ICU dashboard; general bed → cleaning.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HICU-016 — Discharge from ICU

`HOSPITAL_A · RECEPTIONIST · ICU · Critical · /ipd/:id`
**Steps:** discharge an ICU patient directly.
**Expected:** stay closed; ICU bed → cleaning; patient off the dashboard; final bill includes ICU-period charges if implemented (record).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HICU-017 — Invalid ICU operations

`HOSPITAL_A · NURSE/DOCTOR · ICU · High · API`
**Steps:** record I/O, ventilator or a score against a **discharged** stay; against a non-ICU admission; against a nonexistent stay id.
**Expected:** refused with 400/404; nothing written.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## E. GATING · ISOLATION · THE OPEN QUESTION

### TC-HICU-018 — ICU module off

`HOSPITAL_M · HOSPITAL_ADMIN · ICU · High · sidebar/API`
**Steps:** = `TC-MOD-017`: tabs and all four ICU endpoint groups.
**Expected:** ICU Dashboard / Bed Board / ICU settings cards hidden; `GET /hospital/icu`, `/alert-thresholds`, `/score-types`, `/ventilator-parameters` → **403**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HICU-019 — ICU is hospital-only

`CLINIC_A / PHARMACY_A · HOSPITAL_ADMIN · ICU · High · API`
**Steps:** = `TC-PERM-020`.
**Expected:** **403** from `@TenantType(HOSPITAL)` and the missing module.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HICU-020 — ICU tenant isolation

`HOSPITAL_A + B · DOCTOR/NURSE · ICU · Critical · API`
**Steps:** = `TC-ISO-027`, `028`, `029`.
**Expected:** B sees no A ICU stays; A's stay id → 403/404; no ICU clinical record can be written across tenants.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HICU-021 — ⭐ Runtime discovery: can ICU function without IPD?

`HOSPITAL_A-variant · HOSPITAL_ADMIN · ICU · High · Plans/ICU`
**Open question from Tranche 1 (item 6). Do not answer from assumption — this case produces the answer.**
**Steps:**

1. As Super Admin create plan `QA-ICU-NO-IPD` for HOSPITAL with **ICU ticked and IPD unticked**. Record whether the plan is accepted (`TC-MOD-006`).
2. If accepted, assign it to a test hospital and log in as its admin.
3. Record which tabs appear: is **ICU Dashboard** present? Is **IPD** / **Wards & Beds** absent?
4. Open ICU Dashboard and ICU Bed Board. Record exactly what renders (data, empty state, or error).
5. `GET /hospital/icu` — record the status.
6. Try to create a ward and a bed (`POST /hospital/wards`, `/hospital/beds`) — record.
7. Try to admit a patient (`POST /hospital/ipd/admit`) — record.
8. If an admission is impossible, confirm that no ICU stay can exist and therefore the ICU screens are permanently empty.
   **Expected:** **no pass/fail — this is a discovery case.** Fill in the observations and mark `NEEDS_PRODUCT_CONFIRMATION`. The likely finding is that ICU without IPD yields a permanently empty dashboard (because an ICU stay hangs off an admission), in which case the product decision is whether the plan combination should be rejected at creation.
   **Observed:** plan accepted? ____ · ICU tab shown? ____ · dashboard renders? ____ · `GET /hospital/icu` status ____ · ward/bed creation ____ · admission ____
   **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HICU-022 — ICU UI, persistence and re-login

`HOSPITAL_A · NURSE/ADMIN · ICU · Medium · all ICU screens`
**Steps:** empty ICU (no patients); slow network; backend down; long patient names; F5; logout/login; re-open every panel.
**Expected:** EmptyState on dashboard and board; readable errors (`Could not load …`); all recorded I/O, ventilator, scores and infusions persist exactly.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
