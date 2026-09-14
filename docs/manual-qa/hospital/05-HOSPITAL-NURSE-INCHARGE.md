# 05 — HOSPITAL NURSE INCHARGE

**Baseline:** `aa143a7` · **Cases:** `TC-HNI-001` … `TC-HNI-032` · Tenant HOSPITAL_A · login `ni.hospa@qa.test` → `/hospital/nurse-incharge`

**Tabs:** Dashboard · My Nurses · My Ward Patients · Unassigned Patients · Schedule · Attendance · Beds · ICU Beds · Coverage · Calendar

**Verified strings:** Attendance `PRESENT/Present`, `ABSENT/Absent`, `LATE/Late`, `LEAVE/Leave`, `HOLIDAY/Holiday`, `Half Day`, `Attendance saved`. Schedule: **Fill Schedule** (`Filling…`), `Shift assigned`, `Shift cleared`, `Please fill in all required fields`. Beds: `Available`, `Occupied`, `Cleaning Required`, `Under Maintenance`, **Mark Bed Cleaned**, **Put Bed Under Maintenance**, **Return Bed to Available**, `Bed status updated`. Coverage: `Temporary assignment created/removed`, `Substitution created/removed`, `Select a nurse and a ward`, `Select both nurses`, `Primary and replacement must differ`, `Active`/`Ended`.

**Scope rule:** an incharge is limited to **their own wards** (`Ward.incharge_nurse_id`, `NurseInchargeGuard`). Admin bypasses.

---

## A. ACCESS & DASHBOARD

### TC-HNI-001 — Incharge login and dashboard

`HOSPITAL_A · NURSE_INCHARGE · Dashboard · Critical · Dashboard`
**Steps:** log in; read `InchargeOverview` counts (my nurses, ward patients, unassigned, beds by state, today's coverage).
**Expected:** lands `/hospital/nurse-incharge`; counts match the other tabs; NURSING-gated.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HNI-002 — ⚠️ Notification bell present but backend refuses

`HOSPITAL_A · NURSE_INCHARGE · Notifications · Medium · header`
**Steps:** look for the bell; open DevTools → Network; click it.
**Expected:** bell **is rendered** (`NurseInchargeDashboard.jsx:147`) but `GET /hospital/notifications` and `/unread-count` return **403** (`hasRole('NURSE')`). Record whether the failure is silent or shown. **Known `UI_WITHOUT_BACKEND`** — raise as **Medium** citing `TC-PERM-007`; do not raise as new.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## B. MY NURSES

### TC-HNI-003 — Admin creates nurse → incharge sees her

`HOSPITAL_A · HOSPITAL_ADMIN → NURSE_INCHARGE · Nursing · Critical · My Nurses`
**Steps:** 1. Admin creates `Staff Nurse Reena Das` and assigns her ward = `General Ward A` (this incharge's ward). 2. Incharge opens **My Nurses**.
**Expected:** Reena listed with ward and shift info; a nurse of **another** incharge's ward is absent; error path `Failed to load nurses`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HNI-004 — Incharge cannot create/delete nurses

`HOSPITAL_A · NURSE_INCHARGE · Nursing · High · API`
**Steps:** `POST`, `PUT`, `DELETE /hospital/nurses` with the incharge token; look for an Add control.
**Expected:** **403** (`NurseController` = `HOSPITAL_ADMIN` only); no Add button.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HNI-005 — Two incharges, disjoint wards

`HOSPITAL_A · NURSE_INCHARGE ×2 · Nursing · Critical · My Nurses / My Ward Patients`
**Steps:** = `TC-PERM-027`. Create `Sister Two` with `Ward Z`; admit a patient there.
**Expected:** each incharge sees only their ward's nurses, patients and beds; cross-ward API by id → **403/404**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## C. PATIENT ASSIGNMENT

### TC-HNI-006 — Unassigned Patients → assign → nurse sees patient

`HOSPITAL_A · NURSE_INCHARGE → NURSE · Nursing · Critical · Unassigned Patients`
**Steps:** 1. Reception admits P1 into `General Ward A`. 2. Incharge → **Unassigned Patients** → P1 present. 3. Assign to Reena. 4. Reena refreshes **My Patients**. 5. Incharge → My Ward Patients.
**Expected:** P1 moves out of Unassigned; Reena sees P1 (`TC-VIS-013`); assignment audited.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HNI-007 — Reassign and unassign

`HOSPITAL_A · NURSE_INCHARGE · Nursing · High · My Ward Patients`
**Steps:** reassign P1 from Reena to a second ward nurse; then unassign entirely.
**Expected:** old nurse loses visibility, new nurse gains it; records written by the previous nurse remain attributed to her; unassigned patient returns to the Unassigned list.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HNI-008 — Auto-assign when Separate Nurse Login ON and exactly one ward nurse

`HOSPITAL_A · RECEPTIONIST → NURSE_INCHARGE · Nursing · High · Unassigned Patients`
**Steps:** 1. Setting ON; ward has exactly **one** staff nurse. 2. Reception admits a patient. 3. Check Unassigned.
**Expected:** patient is **auto-assigned** to that nurse (per `PatientAssignmentService`). With two or more ward nurses it stays Unassigned for the incharge to decide. With the setting **OFF** the incharge handles it. Record each of the three variants.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HNI-009 — Incharge records care on a nurse's behalf (setting OFF)

`HOSPITAL_B · NURSE_INCHARGE · Nursing · High · My Ward Patients`
**Steps:** = `TC-VIS-015` in HOSPITAL_B (setting OFF): record vitals; observe the **Performed By Nurse** picker; save; view as doctor.
**Expected:** picker present and required; record shows performer = chosen nurse, recorder = incharge. In HOSPITAL_A (ON) the picker is absent.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HNI-010 — Incharge writes every clinical form for a ward patient

`HOSPITAL_A · NURSE_INCHARGE · Nursing · Critical · My Ward Patients`
**Steps:** run the `04` per-form protocol (`TC-HN-008`…`018`) as the incharge on a ward patient.
**Expected:** same behaviour as the nurse; Files & Access treats `NURSE_INCHARGE` as **NURSE**; writes to a **non-ward** patient → 403/404.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## D. SCHEDULE & ATTENDANCE

### TC-HNI-011 — Fill Schedule from templates

`HOSPITAL_A · NURSE_INCHARGE · Schedule · High · Schedule`
**Steps:** 1. Admin created `Morning 07:00–15:00` (`TC-HA-023`). 2. Incharge → Schedule → pick ward/week → **Fill Schedule** (`Filling…`). 3. Assign a nurse to a day (`Shift assigned`); clear one (`Shift cleared`). 4. Submit with a missing field.
**Expected:** grid populates; assign/clear persist after F5; `Please fill in all required fields`; errors `Failed to load shift templates` / `Failed to load wards` / `Failed to update shift` handled.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HNI-012 — Template edit rewrites future only

`HOSPITAL_A · HOSPITAL_ADMIN → NURSE_INCHARGE · Schedule · High · Schedule`
**Steps:** = `TC-HN-026`.
**Expected:** today's snapshot unchanged; future days updated.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HNI-013 — Attendance marking

`HOSPITAL_A · NURSE_INCHARGE · Attendance · High · Attendance`
**Steps:** 1. Attendance → select date. 2. Mark Reena `PRESENT`, another `LEAVE`, another `Half Day`. 3. Save (`Attendance saved`). 4. F5. 5. Re-mark the same nurse/date differently. 6. Use **Add a ward nurse…** to include a nurse.
**Expected:** one row per nurse/date (upsert — the second marking overwrites, not duplicates); shift window snapshotted; nurse sees it read-only (`TC-HN-027`); errors `Failed to load/save attendance`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## E. BEDS (ward-scoped)

### TC-HNI-014 — ⭐ Bed lifecycle: occupied → cleaning → available

`HOSPITAL_A · NURSE_INCHARGE · Beds · Critical · Beds`
**Steps:** 1. P1 admitted to `GA-01` → state `Occupied`. 2. Reception confirms discharge → state `Cleaning Required`. 3. Incharge → Beds → **Mark Bed Cleaned** → `Confirm` → `Bed status updated`. 4. Reception opens the admit bed picker.
**Expected:** `Available` after cleaning and **only then** offered for a new admission; each transition written through `BedStatusService` and visible in bed history (`Failed to load bed history` on error).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HNI-015 — Maintenance in and out

`HOSPITAL_A · NURSE_INCHARGE · Beds · High · Beds`
**Steps:** **Put Bed Under Maintenance** on `GA-03`; check reception's picker and the admin Overview beds card; **Return Bed to Available**.
**Expected:** `Under Maintenance` excluded from the picker and from **usable capacity** on Overview (`usableCapacity = allBeds − maintenance`); returning restores both.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HNI-016 — Invalid bed transitions

`HOSPITAL_A · NURSE_INCHARGE · Beds · High · Beds/API`
**Steps:** mark an **Occupied** bed cleaned; put an occupied bed under maintenance; mark an already-available bed cleaned.
**Expected:** refused with a clear message; occupancy never silently freed while a patient is admitted.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HNI-017 — Bed scope is ward-limited

`HOSPITAL_A · NURSE_INCHARGE · Beds · Critical · API`
**Steps:** incharge #1 changes a bed in incharge #2's `Ward Z` (UI absent; try API by bed id).
**Expected:** **403/404**; bed unchanged.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HNI-018 — Bed status audit trail

`HOSPITAL_A · HOSPITAL_ADMIN · Beds · High · Audit`
**Steps:** after `TC-HNI-014`/`015`, open bed history and the hospital Audit Logs.
**Expected:** every transition recorded with actor, from-state, to-state, timestamp (`bed_status_audits`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## F. ICU BEDS · COVERAGE · CALENDAR

### TC-HNI-019 — ICU Beds (incharge)

`HOSPITAL_A · NURSE_INCHARGE · ICU · High · ICU Beds`
**Steps:** open; compare with the admin ICU Bed Board; open an ICU ward patient.
**Expected:** ward-scoped; ICU-gated; states consistent with the Beds tab.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HNI-020 — Coverage: temporary assignment

`HOSPITAL_A · NURSE_INCHARGE · Coverage · High · Coverage`
**Steps:** 1. Coverage → temporary assignment: pick a nurse + ward → create (`Temporary assignment created`); `Adding…`. 2. Omit one → `Select a nurse and a ward`. 3. Remove (`Temporary assignment removed`). 4. Check the covering nurse's visibility during the window.
**Expected:** `Active` while in window, `Ended` after; the nurse temporarily sees that ward's patients; removal revokes it.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HNI-021 — Coverage: substitution

`HOSPITAL_A · NURSE_INCHARGE · Coverage · High · Coverage`
**Steps:** 1. Create a substitution (primary → replacement) → `Substitution created`. 2. Same nurse for both → `Primary and replacement must differ`. 3. Omit one → `Select both nurses`. 4. Remove → `Substitution removed`. 5. Check that the replacement inherits the primary's patients for the window.
**Expected:** as stated; `Failed to create substitution` / `Failed to remove` handled; `Failed to load coverage` on load error.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HNI-022 — Coverage respects ward scope and tenant

`HOSPITAL_A + B · NURSE_INCHARGE · Coverage · Critical · Coverage/API`
**Steps:** try to substitute a nurse from another incharge's ward and from HOSPITAL_B (API by id).
**Expected:** 403/404; no cross-ward or cross-tenant coverage.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HNI-023 — Calendar (incharge)

`HOSPITAL_A · NURSE_INCHARGE · Calendar · Medium · Calendar`
**Steps:** open; navigate months; a day with shifts; an empty day.
**Expected:** shows this incharge's wards' schedules; empty state; NURSING-gated.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## G. PERMISSIONS · ISOLATION · UI

### TC-HNI-024 — Incharge cannot reach non-nursing areas

`HOSPITAL_A · NURSE_INCHARGE · Authorization · Critical · UI/API`
**Steps:** `GET /hospital/patients`, `/hospital/billing`, `/hospital/opd`, `/hospital/audit-logs`, `/hospital/settings/fees`.
**Expected:** 403 (settings = known drift, record).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HNI-025 — Incharge is hospital-only

`HOSPITAL_A · NURSE_INCHARGE · Authorization · High · API`
**Steps:** `/clinic/**`, `/pharmacy/**`.
**Expected:** **403**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HNI-026 — Incharge tenant isolation

`HOSPITAL_A + B · NURSE_INCHARGE · all · Critical · API`
**Steps:** = `TC-ISO-022`, `024`, `025`, `029` with incharge tokens.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HNI-027 — Ward without an incharge blocks admission

`HOSPITAL_A · HOSPITAL_ADMIN → RECEPTIONIST · Wards · Critical · IPD`
**Steps:** create `Ward NoIncharge` with a bed, leave incharge empty; reception attempts to admit there.
**Expected:** ward not offered / admission refused with a clear reason.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HNI-028 — Removing an incharge from a ward

`HOSPITAL_A · HOSPITAL_ADMIN · Wards · High · Wards & Beds`
**Steps:** clear `General Ward A`'s incharge while patients are admitted.
**Expected:** record behaviour — refused, or allowed leaving patients ward-orphaned. `NEEDS_PRODUCT_CONFIRMATION`; check the incharge immediately loses visibility.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HNI-029 — Promotion/demotion effects on scope

`HOSPITAL_A · HOSPITAL_ADMIN · Nursing · High · Nurses`
**Steps:** promote Reena to incharge (`TC-HA-018`); assign her a ward; demote her.
**Expected:** after promotion she has the incharge dashboard and her ward; after demotion she returns to `/hospital/nurse` and the ward has no incharge (or retains the old one) — record.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HNI-030 — Double-submit and refresh

`HOSPITAL_A · NURSE_INCHARGE · UI · High · Attendance/Coverage/Beds`
**Steps:** double-click Save attendance, create substitution, Mark Bed Cleaned.
**Expected:** one record / one transition each.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HNI-031 — Incharge UI states

`HOSPITAL_A · NURSE_INCHARGE · UI · Medium · all tabs`
**Steps:** empty ward, loading, backend down, long nurse/patient names.
**Expected:** EmptyState per tab; the documented `Failed to load …` messages; no crash.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HNI-032 — Re-login persistence

`HOSPITAL_A · NURSE_INCHARGE · Persistence · High · all`
**Steps:** logout/login; re-check assignments, schedule, attendance, coverage, bed states.
**Expected:** all persist.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
