# 02 — CLINIC ADMIN

**Baseline:** `aa143a7` · **Cases:** `TC-CA-001` … `TC-CA-026` · Tenant CLINIC_A · `admin.clina@qa.test` at `/login/clinic` → `/hospital/admin`

Read [`01-CLINIC-DELTA-FROM-HOSPITAL.md`](01-CLINIC-DELTA-FROM-HOSPITAL.md) first. This document
covers **only** what a clinic admin does differently or additionally; shared behaviour is
referenced, not re-specified.

---

## A. SETUP

### TC-CA-001 — Clinic admin first login and shell

`CLINIC_A · HOSPITAL_ADMIN · Auth · Critical · /hospital/admin`
**Steps:** log in; record the header tenant name, sidebar groups and tabs; open `GET /auth/me`.
**Expected:** header **QA Clinic A**; `hospitalType: CLINIC`; tab set exactly per `TC-CD-004`; no Rooms/Critical Care/Nursing groups.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CA-002 — Create doctor → login → picker → API namespace

`CLINIC_A · HOSPITAL_ADMIN → DOCTOR · Doctors · Critical · Staff ▸ Doctors`
**Steps:** = `TC-HA-004` as clinic, with `Dr Sanjay Bhosale` / `doc.clina@qa.test`. Additionally: with the doctor's token call `GET /clinic/doctors` **and** `GET /hospital/doctors`.
**Expected:** doctor created, lands `/hospital/doctor`, appears in the Appointment and OPD pickers. **Both** namespace calls are admitted by `SecurityConfig` — record whether `/hospital/doctors` returns the **clinic's** doctors (correct: `hospitalId` comes from the JWT) or anything else (**Critical** if it returns another tenant's).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CA-003 — Create receptionist and pharmacist

`CLINIC_A · HOSPITAL_ADMIN · Staff · Critical · Staff`
**Steps:** = `TC-HA-011` and `TC-HA-014` as clinic (`rec.clina@qa.test`, `pharm.clina@qa.test`).
**Expected:** both land on their dashboards; the pharmacist gets the 13-tab pharmacy dashboard (PHARMACY module on).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CA-004 — Staff validation, duplicates, reset, deactivate

`CLINIC_A · HOSPITAL_ADMIN · Staff · High · Staff`
**Steps:** = `TC-HA-005`, `007`, `008`, `012`, `013`, `040`, `041` as clinic. Include a duplicate email already used in **HOSPITAL_A**.
**Expected:** as hospital; the cross-tenant duplicate-email message must not name the other tenant.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CA-005 — No nursing / OT staff tabs or endpoints

`CLINIC_A · HOSPITAL_ADMIN · Staff · High · sidebar/API`
**Steps:** = `TC-CD-005`, `TC-CD-007`.
**Expected:** absent in UI; `/clinic/nurses` 404; `/hospital/nurses` 403.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## B. CONFIGURATION → DOWNSTREAM

### TC-CA-006 — Fees → consultation bill

`CLINIC_A · HOSPITAL_ADMIN → REC/DOC · Fees · Critical · Finance ▸ Fees`
**Steps:** = `TC-HAC-019` as clinic: set consultation 555, follow-up 111, custom fee `Dressing` 50; run an OPD→consultation→bill; restore.
**Expected:** bill line 555; custom fee selectable; `BILLING`-gated (`/clinic/settings/fees/custom`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CA-007 — Vitals settings → clinic OPD form and case paper

`CLINIC_A · HOSPITAL_ADMIN → REC · Settings · High · Settings ▸ Vitals`
**Steps:** = `TC-HAS-010`/`011` as clinic (`/clinic/vitals`): disable SpO2; add custom vital `QA Clinic Pain`; check the OPD form, the doctor's vitals strip and the case paper; restore.
**Expected:** identical behaviour to hospital; server drops a disabled vital submitted via API.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CA-008 — Operations settings in a clinic

`CLINIC_A · HOSPITAL_ADMIN · Settings · Critical · Settings ▸ Operations`
**Steps:** = `TC-CD-030`, then exercise Reception Mode `SOLO`, Billing Handler `DOCTOR`, Bill Payment `FIRST`, In-Clinic OFF, Barcode OFF — each with its downstream check (`TC-HAS-001`…`005`). Restore all.
**Expected:** all five behave as hospital; record whether the nurse-login and OT-incharge toggles are visible at all.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CA-009 — Print & Payment toggles

`CLINIC_A · HOSPITAL_ADMIN → DOC · Settings · High · Print & Payment`
**Steps:** = `TC-HAS-009` as clinic.
**Expected:** Case Paper / Bill / Prescription / In-Clinic print controls hide and show; endpoints still answer (policy, not authorization).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CA-010 — Files & Access in a clinic

`CLINIC_A · HOSPITAL_ADMIN · Settings · Medium · Files & Access`
**Steps:** = `TC-CD-031`.
**Expected:** record which form keys are listed; `NEEDS_PRODUCT_CONFIRMATION` on offering IPD/OT keys to a clinic.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CA-011 — Settings grid has no ICU/OT cards

`CLINIC_A · HOSPITAL_ADMIN · Settings · High · Settings`
**Steps:** = `TC-CD-029` and `TC-CD-034`.
**Expected:** cards absent; all twelve ICU/OT settings calls **403**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CA-012 — Subscription and profile

`CLINIC_A · HOSPITAL_ADMIN · Settings · Medium · Settings/Navbar`
**Steps:** = `TC-HAS-029`/`030` as clinic (`GET /clinic/subscription`).
**Expected:** shows the clinic plan and its six-module maximum; logo/name change reflects in the header and on PDFs (`TC-CD-028`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## C. PRESETS · INVENTORY · PHARMACY (admin side)

### TC-CA-013 — All five preset tabs

`CLINIC_A · HOSPITAL_ADMIN → DOCTOR · Presets · Medium · Presets`
**Steps:** = `TC-HA-028`…`031` as clinic, including the In-Clinic Presets live-toggle behaviour.
**Expected:** presets apply in the clinic doctor's consultation; the In-Clinic tab hides/shows live over WebSocket.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CA-014 — Medicine Inventory (MEDICAL_INVENTORY)

`CLINIC_A · HOSPITAL_ADMIN · Inventory · High · Inventory`
**Steps:** = `TC-HA-034`/`036` as clinic via `/clinic/medicines`; then verify the module gate by removing MEDICAL_INVENTORY from the plan (`TC-CD-036`).
**Expected:** stock CRUD works; with the module removed the tab hides **and** `/clinic/medicines` returns **403** (a working gate).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CA-015 — Hospital Inventory is closed

`CLINIC_A · HOSPITAL_ADMIN · Inventory · High · sidebar/API`
**Steps:** = `TC-CD-019`.
**Expected:** no tab; `/clinic/hospital-inventory` **403**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CA-016 — Admin Pharmacy tab and Pharmacists tab

`CLINIC_A · HOSPITAL_ADMIN · Pharmacy · Medium · Pharmacy`
**Steps:** open the Pharmacy analytics tab and the Pharmacists tab; compare with the clinic pharmacist's Dashboard after one sale.
**Expected:** totals agree; the admin can open `/hospital/pharmacy` (route admits `HOSPITAL_ADMIN`) and sees the **clinic's** stock.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## D. CLINICAL OVERSIGHT · REPORTS · AUDIT

### TC-CA-017 — Admin clinical tabs

`CLINIC_A · HOSPITAL_ADMIN · multi · High · Patients/Appointments/OPD/Follow-ups`
**Steps:** = `TC-HA-027`, `TC-HAC-001`…`009` as clinic (Add Patient, New OPD Case, appointments, follow-ups).
**Expected:** all function; the duplicate-phone chooser behaves identically (`TC-CD-021`…`023`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CA-018 — Clinic Overview

`CLINIC_A · HOSPITAL_ADMIN · Overview · High · Overview`
**Steps:** = `TC-CD-033`; hand-count patients, today's OPD, collection and pharmacy sales and compare.
**Expected:** figures reconcile; no IPD/Beds cards; no silently failing card.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CA-019 — Reports & Analytics (REPORTS-gated)

`CLINIC_A · HOSPITAL_ADMIN · Reports · High · Reports`
**Steps:** = `TC-HAC-012` as clinic (`/clinic/stats`); then remove REPORTS from the plan and re-check.
**Expected:** totals reconcile with the clinic's own records; with the module removed the tab hides and `/clinic/stats` → **403**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CA-020 — Reports contain no other tenant's data

`CLINIC_A + CLINIC_B + HOSPITAL_A · HOSPITAL_ADMIN · Reports · Critical · Reports`
**Steps:** = `TC-ISO-053`/`054` across all three tenants; download the clinic patients-report PDF and read every row.
**Expected:** clinic figures and PDF contain CLINIC_A only.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CA-021 — Audit Logs

`CLINIC_A · HOSPITAL_ADMIN · Audit · High · Audit Logs`
**Steps:** = `TC-HA-033` as clinic (`/clinic/audit-logs`): perform 8 audited actions incl. a duplicate-phone acknowledgement; filter; search; check masking; then call with the receptionist token.
**Expected:** all actions listed with actor and timestamp; phone **masked**; no secrets; non-admin **403**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CA-022 — Support and FAQs

`CLINIC_A · HOSPITAL_ADMIN · Support · Medium · Support`
**Steps:** = `TC-CD-032`.
**Expected:** clinic-typed ticket and FAQ routing.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## E. NEGATIVE · ISOLATION · UI

### TC-CA-023 — Clinic admin cannot operate hospital-only domains

`CLINIC_A · HOSPITAL_ADMIN · Authorization · Critical · API`
**Steps:** = `TC-CD-010`…`019` executed in full from the admin session, plus `TC-CD-009`.
**Expected:** IPD/Wards/Beds results **recorded** (drift expected); ICU/OT/Nursing 403 or 404; `/hospital/dashboard` 403.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CA-024 — Clinic admin cannot reach the platform

`CLINIC_A · HOSPITAL_ADMIN · Authorization · Critical · API`
**Steps:** = `TC-API-004`: `/platform/hospitals`, `/platform/plans`, `/platform/users`, `/platform/audit-logs`; and `POST /platform/hospitals`.
**Expected:** **403** on all; no tenant list disclosed.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CA-025 — Clinic admin isolation sweep

`CLINIC_A + CLINIC_B + HOSPITAL_A · HOSPITAL_ADMIN · Isolation · Critical · API`
**Steps:** = `TC-CD-039`, focusing on staff endpoints: list, fetch by id, edit, delete and **reset password** for another tenant's doctor (`TC-ISO-045`…`047`).
**Expected:** 403/404; the target tenant's staff unchanged; the other tenant's admin can still log in with the original password.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CA-026 — Clinic admin UI states, persistence, double-submit

`CLINIC_A · HOSPITAL_ADMIN · UI · Medium · all tabs`
**Steps:** = `TC-HA-041`…`046` as clinic: double-submit each staff form; cancel/back/refresh mid-form; long names; empty/loading/error states; responsive at 375/768px; logout/login and re-check everything created.
**Expected:** one row per form; EmptyState on fresh tabs; readable errors; F5 returns to Overview (expected); all data persists.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
