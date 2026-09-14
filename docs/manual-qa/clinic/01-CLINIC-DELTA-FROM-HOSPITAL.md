# 01 — CLINIC: AUTHORITATIVE DELTA FROM HOSPITAL

**Baseline:** `origin/staging` @ `aa143a7` · **Cases:** `TC-CD-001` … `TC-CD-040`

Clinic **deliberately shares the Hospital frontend**. There is no `/clinic/*` route: a clinic user
logs in at `/login/clinic` and lands on `/hospital/admin`, `/hospital/receptionist` or
`/hospital/doctor`. Only the **API namespace** (`/clinic/**`) and the **module set** differ.

This document is the authority on what is the same, what differs, and what must not work. Where a
row says `SAME_AS_HOSPITAL`, run the referenced Hospital case **as a clinic user** — do not
re-specify it.

**Classification key:** `SAME_AS_HOSPITAL` · `CLINIC_VARIANT` · `NOT_SUPPORTED_IN_CLINIC` · `IMPLEMENTATION_DRIFT` · `NEEDS_PRODUCT_CONFIRMATION`

---

## 1. What the code actually says

**Sellable to CLINIC** (`EntitlementRegistry:116-117`): `OPD` · `APPOINTMENTS` · `BILLING` · `PHARMACY` · `MEDICAL_INVENTORY` · `REPORTS`.
**Not sellable:** `IPD`, `OT`, `NURSING`, `ICU`, `HOSPITAL_INVENTORY` (and therefore the implied `WARDS`, `BEDS`, `CLINICAL_RECORDS`).

**Roles admitted to `/clinic/**`** (`SecurityConfig:104-105`): `HOSPITAL_ADMIN`, `DOCTOR`, `RECEPTIONIST`, `PHARMACIST`.
**Excluded:** `NURSE`, `NURSE_INCHARGE`, `OT_INCHARGE`, `SUPER_ADMIN`.

**23 controllers are aliased onto `/clinic/…` (148 endpoints).** Of these, only **five** carry a module gate:

| Controller                                                                                                                                                                           | `/clinic` endpoints | Module gate                                                        | Tenant gate |
| ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------- | ------------------------------------------------------------------ | ----------- |
| Appointment                                                                                                                                                                          | 11                  | `APPOINTMENTS` ✅                                                  | —           |
| HospitalFee                                                                                                                                                                          | 4                   | `BILLING` ✅                                                       | —           |
| Medicine                                                                                                                                                                             | 9                   | `MEDICAL_INVENTORY` ✅                                             | —           |
| HospitalStats                                                                                                                                                                        | 4                   | `REPORTS` ✅                                                       | —           |
| HospitalInventory                                                                                                                                                                    | 7                   | `HOSPITAL_INVENTORY` ✅ (clinic can't buy it → effectively closed) | —           |
| **IpdAdmission**                                                                                                                                                                     | **13**              | **none** ❌                                                        | **none** ❌ |
| **Ward**                                                                                                                                                                             | **7**               | **none** ❌                                                        | **none** ❌ |
| **Bed**                                                                                                                                                                              | **5**               | **none** ❌                                                        | **none** ❌ |
| Patient · Opd · Billing · Doctor · Receptionist · Pharmacist · Pharmacy · FollowUp · FormAccess · VitalSettings · Presets · Audit · Ticket · Faq · HospitalService · PatientDocument | 88                  | none                                                               | none        |

**No `/clinic` endpoint carries `@TenantType`.** The hospital-only surfaces (OT, ICU, nursing,
`/hospital/dashboard`) are _not_ aliased to `/clinic` at all, which is why they are genuinely
closed — see §4.

---

## 2. Login, landing, shell

### TC-CD-001 — Clinic login lands on the hospital URL namespace

`CLINIC_A · HOSPITAL_ADMIN · Auth · High · /login/clinic` — `CLINIC_VARIANT`
**Steps:** log in as `admin.clina@qa.test` at `/login/clinic`; record the URL and the header tenant name; log out; repeat for `rec.clina@qa.test` and `doc.clina@qa.test`.
**Expected:** `/hospital/admin`, `/hospital/receptionist`, `/hospital/doctor`. Header shows **QA Clinic A**. **The "hospital" URL is correct by design — do not raise a bug** (`TC-AUTH-003`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CD-002 — Session expiry returns to `/login/clinic`

`CLINIC_A · any · Auth · Medium · /login/clinic` — `CLINIC_VARIANT`
**Steps:** = `TC-AUTH-025` for the clinic portal.
**Expected:** redirect to **`/login/clinic`**, not `/login/hospital`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CD-003 — API calls use the `/clinic` namespace

`CLINIC_A · RECEPTIONIST · API · High · DevTools` — `CLINIC_VARIANT`
**Steps:** with DevTools open, click Patients, Appointments, OPD, Billing; read each Request URL.
**Expected:** every call is `/clinic/...`, never `/hospital/...`. Record any `/hospital/...` call a clinic session makes — that would mean the tenant namespace is not applied consistently.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CD-004 — Clinic sidebar: exactly the supported tabs

`CLINIC_A · HOSPITAL_ADMIN · UI · Critical · sidebar` — `CLINIC_VARIANT`
**Steps:** log in on the full clinic plan; list every sidebar group and tab.
**Expected — present:** Overview · Patients · Appointments · OPD · Follow-ups · Pharmacy · Pharmacists · Medicine Inventory · Billing · Fees · Doctors · Receptionists · Reports & Analytics · Audit Logs · Settings · Support · the five preset tabs.
**Expected — absent:** **IPD, Wards & Beds, ICU Dashboard, ICU Bed Board, Operation Theatre, OT Incharge, OT Theatres, OT Analytics, Nurses, Nurse Assignments, Nurse Tasks, Time Slots, Calendar, Hospital Inventory, Pathology.**
Empty sidebar groups (Rooms, Critical Care, Nursing) must not render.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## 3. Feature-by-feature delta

| #   | Feature                                                            | Classification                                                                            | Run                                              |
| --- | ------------------------------------------------------------------ | ----------------------------------------------------------------------------------------- | ------------------------------------------------ |
| 1   | Patient registration, edit, search, soft delete                    | `SAME_AS_HOSPITAL`                                                                        | `TC-HR-001`…`004`, `013`…`019` as clinic         |
| 2   | **Duplicate-phone chooser**                                        | `SAME_AS_HOSPITAL`                                                                        | `TC-CD-020`…`024` (re-specified — highest value) |
| 3   | Appointments incl. new-patient booking                             | `SAME_AS_HOSPITAL`                                                                        | `TC-HR-021`…`027` as clinic                      |
| 4   | OPD create, vitals, queue, case paper                              | `SAME_AS_HOSPITAL`                                                                        | `TC-HR-029`…`036` as clinic                      |
| 5   | Consultation, symptoms, diagnosis, prescription                    | `SAME_AS_HOSPITAL`                                                                        | `TC-HD-004`…`018` as clinic                      |
| 6   | **Lab ordering** (8 tests → `LabOrder`)                            | `SAME_AS_HOSPITAL` (`PARTIAL` feature)                                                    | `TC-CD-027`                                      |
| 7   | Follow-ups                                                         | `SAME_AS_HOSPITAL`                                                                        | `TC-HAC-008`/`009` as clinic                     |
| 8   | Billing, payments, receipts                                        | `SAME_AS_HOSPITAL`                                                                        | `TC-HB-001`…`009`, `015`…`022` as clinic         |
| 9   | Fees + custom fees                                                 | `SAME_AS_HOSPITAL`                                                                        | `TC-HAC-019` as clinic                           |
| 10  | Doctors / Receptionists / Pharmacists CRUD                         | `SAME_AS_HOSPITAL`                                                                        | `TC-HA-004`…`015` as clinic                      |
| 11  | Medicine Inventory                                                 | `SAME_AS_HOSPITAL`                                                                        | `TC-HA-034`, `TC-HA-036` as clinic               |
| 12  | Pharmacy dashboard (13 tabs)                                       | `SAME_AS_HOSPITAL`                                                                        | `05-CLINIC-PHARMACY-INTEGRATION.md`              |
| 13  | Presets (quick notes, symptom, diagnosis, prescription, in-clinic) | `SAME_AS_HOSPITAL`                                                                        | `TC-HA-028`…`031` as clinic                      |
| 14  | Patient documents                                                  | `SAME_AS_HOSPITAL` (`/clinic` aliased)                                                    | `TC-HD-021` as clinic                            |
| 15  | Reports & Analytics                                                | `SAME_AS_HOSPITAL`, `REPORTS`-gated                                                       | `TC-HAC-012` as clinic                           |
| 16  | Audit Logs                                                         | `SAME_AS_HOSPITAL`                                                                        | `TC-HA-033` as clinic                            |
| 17  | Support + FAQs                                                     | `CLINIC_VARIANT` — clinic-type FAQs                                                       | `TC-CD-032`                                      |
| 18  | Settings ▸ Operations, Print & Payment, Vitals                     | `SAME_AS_HOSPITAL`                                                                        | `TC-CD-029`/`030`                                |
| 19  | Settings ▸ Files & Access                                          | `CLINIC_VARIANT` — **not module-gated**, but the IPD-only forms have nothing to attach to | `TC-CD-031`                                      |
| 20  | Settings ▸ ICU cards, OT cards                                     | `NOT_SUPPORTED_IN_CLINIC`                                                                 | `TC-CD-034`                                      |
| 21  | `isSingleDoctor` mode                                              | `SAME_AS_HOSPITAL`                                                                        | `TC-MODE-001`…`009` (uses `QA Clinic Solo`)      |
| 22  | **IPD / admissions**                                               | **`NOT_SUPPORTED` + `IMPLEMENTATION_DRIFT`**                                              | `TC-CD-010`…`015`                                |
| 23  | **Wards / Beds**                                                   | **`NOT_SUPPORTED` + `IMPLEMENTATION_DRIFT`**                                              | `TC-CD-016`/`017`                                |
| 24  | ICU                                                                | `NOT_SUPPORTED_IN_CLINIC` (not aliased)                                                   | `TC-CD-018`                                      |
| 25  | OT + 15 NABH forms                                                 | `NOT_SUPPORTED_IN_CLINIC` (not aliased)                                                   | `TC-CD-018`                                      |
| 26  | Nursing (all 14 controllers)                                       | `NOT_SUPPORTED_IN_CLINIC` (not aliased)                                                   | `TC-CD-018`                                      |
| 27  | NURSE / NURSE_INCHARGE / OT_INCHARGE roles                         | `NOT_SUPPORTED_IN_CLINIC`                                                                 | `TC-CD-007`/`008`                                |
| 28  | Hospital Inventory                                                 | `NOT_SUPPORTED_IN_CLINIC` (gated)                                                         | `TC-CD-019`                                      |
| 29  | `/hospital/dashboard` Overview analytics                           | `NOT_SUPPORTED_IN_CLINIC` (`@TenantType(HOSPITAL)`)                                       | `TC-CD-009`                                      |
| 30  | Pathology                                                          | `PLACEHOLDER` everywhere                                                                  | `TC-MOD-030`                                     |

---

## 4. Roles

### TC-CD-005 — Clinic staff creation offers only the four supported roles

`CLINIC_A · HOSPITAL_ADMIN · Staff · High · Staff tabs` — `CLINIC_VARIANT`
**Steps:** open every staff tab; list what can be created.
**Expected:** Doctors, Receptionists, Pharmacists only. **No Nurses, Nurse Assignments, Nurse Tasks, OT Incharge tabs** (`NURSING`/`OT` not sellable).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CD-006 — Clinic roles land correctly and are tenant-scoped

`CLINIC_A · ADM/REC/DOC/PHA · Auth · High · login` — `SAME_AS_HOSPITAL`
**Steps:** create and log in as each of the four; record landing URLs; call `GET /auth/me`.
**Expected:** admin→`/hospital/admin`, receptionist→`/hospital/receptionist`, doctor→`/hospital/doctor`, pharmacist→`/hospital/pharmacy`; each `hospitalType: CLINIC` with CLINIC_A's `hospitalId`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CD-007 — Nursing/OT roles cannot exist in a clinic

`CLINIC_A · HOSPITAL_ADMIN · Staff · High · API` — `NOT_SUPPORTED_IN_CLINIC`
**Steps:** `POST /clinic/nurses`, `/clinic/ot-incharges` with the clinic admin token; also `POST /hospital/nurses` with it.
**Expected:** `/clinic/nurses` and `/clinic/ot-incharges` **404** (never aliased). `/hospital/nurses` → **403** from `@RequireModule("NURSING")`. Record both.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CD-008 — A hospital NURSE token is refused on `/clinic/**`

`HOSPITAL_A → CLINIC · NURSE/NI/OTI · Authorization · High · API` — `NOT_SUPPORTED_IN_CLINIC`
**Steps:** = `TC-API-008` / `TC-PERM-013` with all three tokens against `/clinic/patients`, `/clinic/opd`, `/clinic/billing`.
**Expected:** **403** on all nine (`SecurityConfig:104-105` omits them).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CD-009 — Hospital-only dashboard endpoint refuses a clinic

`CLINIC_A · HOSPITAL_ADMIN · Reports · High · API` — `NOT_SUPPORTED_IN_CLINIC`
**Steps:** = `TC-PERM-018`: `GET /hospital/dashboard` with the clinic admin token.
**Expected:** **403** (`@TenantType(HOSPITAL)`). This is the reference for a _correctly_ gated tenant-type boundary — compare against §5.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## 5. ⚠️ THE `/clinic/ipd` DRIFT — detailed runtime cases

> **Product policy: A CLINIC MUST NOT OPERATE IPD.**
> **Code position:** `IpdAdmissionController` is aliased to `/clinic/ipd` with **13 endpoints,
> none carrying `@TenantType` or `@RequireModule`.** Authorization falls back to the
> `SecurityConfig` URL rule, which admits clinic admins, doctors and receptionists.
> Each case below therefore has an **expected (policy)** and a **record (actual)**. A 200 is a
> **FAIL / IMPLEMENTATION_DRIFT**, not a pass.

### TC-CD-010 — Clinic IPD: UI absence

`CLINIC_A · ADM/REC/DOC · IPD · Critical · sidebar` — `NOT_SUPPORTED_IN_CLINIC`
**Steps:** as each of the three roles, look for an IPD tab, an "Admit to IPD" control on an OPD case, and an IPD-requests badge.
**Expected:** absent everywhere. If the doctor's consultation still offers **Admit to IPD**, record it as **HIGH** drift — it would let a clinic create an admission request.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CD-011 — Clinic IPD: list endpoints

`CLINIC_A · HOSPITAL_ADMIN · IPD · Critical · API` — `IMPLEMENTATION_DRIFT`

| Endpoint                                                                                                                                                                         | Method | Expected (policy) | Actual status | Body contained data? |
| -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------ | ----------------- | ------------- | -------------------- |
| `/clinic/ipd`                                                                                                                                                                    | GET    | 403               | ____          | ____                 |
| `/clinic/ipd/admissions`                                                                                                                                                         | GET    | 403               | ____          | ____                 |
| `/clinic/ipd/my`                                                                                                                                                                 | GET    | 403               | ____          | ____                 |
| **Steps:** run each with the clinic admin token, then the clinic receptionist and doctor tokens.                                                                                 |
| **Expected:** 403. **If 200 with an empty list** → drift, **HIGH**. **If 200 with data** → also check _whose_ data it is; another tenant's would be **Critical** (`TC-ISO-019`). |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____                                                                       |

### TC-CD-012 — Clinic IPD: lookup by id

`CLINIC_A · HOSPITAL_ADMIN · IPD · Critical · API` — `IMPLEMENTATION_DRIFT`
**Steps:** `GET /clinic/ipd/{id}` using (a) a nonexistent id, (b) **HOSPITAL_A's** admission id from the Tranche-2 data.
**Expected:** 403 for both. **A 200 returning HOSPITAL_A's admission is a Critical cross-tenant leak** — stop and raise immediately.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CD-013 — ⭐ Clinic IPD: create an admission

`CLINIC_A · RECEPTIONIST · IPD · Critical · API` — `IMPLEMENTATION_DRIFT`
**Steps:**

1. Capture a valid `POST /hospital/ipd/admit` body from HOSPITAL_A (Tranche 2).
2. Replace `opdId`/`wardId`/`bedId` with **clinic** values. A clinic has no wards or beds, so first run `TC-CD-016` to see whether a clinic ward can even be created.
3. `POST /clinic/ipd/admit` with the clinic token.
4. If it returns 2xx, immediately `GET /clinic/ipd` and query the database: `SELECT COUNT(*) FROM ipd_admission WHERE hospital_id = <CLINIC_A id>;`
   **Expected (policy):** **403**, no row written.
   **If an admission is created:** **FAIL / IMPLEMENTATION_DRIFT, severity HIGH** (a clinic operating an unsold module) — and **Critical** if it consumed a bed belonging to another tenant. Record the row id and report it; **do not attempt to clean it up yourself**.
   **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CD-014 — Clinic IPD: update / clinical sub-actions

`CLINIC_A · DOCTOR · IPD · Critical · API` — `IMPLEMENTATION_DRIFT`

| Endpoint                                                                                                          | Method | Expected | Actual |
| ----------------------------------------------------------------------------------------------------------------- | ------ | -------- | ------ |
| `/clinic/ipd/{id}/prescriptions`                                                                                  | POST   | 403      | ____   |
| `/clinic/ipd/prescriptions/{id}/stop`                                                                             | PUT    | 403      | ____   |
| `/clinic/ipd/{id}/administer`                                                                                     | POST   | 403      | ____   |
| `/clinic/ipd/{id}/administer-hospital-items`                                                                      | POST   | 403      | ____   |
| `/clinic/ipd/{id}/change-bed`                                                                                     | PUT    | 403      | ____   |
| `/clinic/ipd/{id}/followup`                                                                                       | POST   | 403      | ____   |
| **Steps:** run each against a HOSPITAL_A admission id **and** a clinic-created one (if `TC-CD-013` produced one). |
| **Expected:** 403 throughout; **nothing written to any tenant**.                                                  |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____        |

### TC-CD-015 — Clinic IPD: discharge endpoints

`CLINIC_A · RECEPTIONIST · IPD · Critical · API` — `IMPLEMENTATION_DRIFT`
**Steps:** `POST /clinic/ipd/{id}/plan-discharge` and `/confirm-discharge` against a HOSPITAL_A admission id.
**Expected:** **403**. Then verify in HOSPITAL_A that the admission is **still ADMITTED** and its bed still Occupied. A clinic discharging a hospital's inpatient would be the most severe outcome in this document — **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CD-016 — ⚠️ Clinic wards

`CLINIC_A · HOSPITAL_ADMIN · Wards · Critical · API` — `IMPLEMENTATION_DRIFT`

| Endpoint                                                                                                                                                   | Method       | Expected | Actual |
| ---------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------ | -------- | ------ |
| `/clinic/wards`                                                                                                                                            | GET          | 403      | ____   |
| `/clinic/wards`                                                                                                                                            | POST         | 403      | ____   |
| `/clinic/wards/{id}`                                                                                                                                       | PUT / DELETE | 403      | ____   |
| **Steps:** as above; if a POST succeeds, record the created ward and check whether it appears anywhere in the clinic UI (it should not — there is no tab). |
| **Expected (policy):** 403. `WardController` has no module or tenant gate → 200 likely → **HIGH** drift.                                                   |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____                                                 |

### TC-CD-017 — ⚠️ Clinic beds

`CLINIC_A · HOSPITAL_ADMIN · Beds · Critical · API` — `IMPLEMENTATION_DRIFT`
**Steps:** `GET /clinic/beds`; `POST /clinic/beds`; attempt a bed-status change on a **HOSPITAL_A** bed id.
**Expected:** 403 throughout; **HOSPITAL_A's bed state unchanged** (verify in HOSPITAL_A). `Bed` endpoints are `ADM`+`NI` — the clinic admin passes the role check, so only tenant scoping stands between this and a cross-tenant bed mutation.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CD-018 — Never-aliased hospital-only namespaces

`CLINIC_A · HOSPITAL_ADMIN · ICU/OT/Nursing · High · API` — `NOT_SUPPORTED_IN_CLINIC`
**Steps:** with the clinic token call: `/clinic/icu`, `/clinic/surgeries`, `/clinic/ot/rooms`, `/clinic/nurses`, `/clinic/nurse-assignments`, `/clinic/nurse/vitals`, `/clinic/time-slots`, `/clinic/calendar`, `/clinic/notifications`. Then call the `/hospital/...` forms of the same.
**Expected:** the `/clinic/...` forms return **404** (no such mapping); the `/hospital/...` forms return **403** (module and/or tenant gate). Both are correct denials — record which each gives, because 404-vs-403 tells you _why_ it is closed.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CD-019 — Hospital Inventory is gated for clinic

`CLINIC_A · HOSPITAL_ADMIN · Inventory · High · API` — `NOT_SUPPORTED_IN_CLINIC`
**Steps:** `GET` and `POST /clinic/hospital-inventory` with the clinic token.
**Expected:** **403** — `HospitalInventoryController` carries `@RequireModule("HOSPITAL_INVENTORY")` and a clinic plan cannot contain it. A working gate; contrast with `TC-CD-011`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## 6. Patient identity in a clinic (re-specified — highest value)

### TC-CD-020 — Clinic patient registration and `PAT` numbering

`CLINIC_A · RECEPTIONIST · Patients · Critical · Patients` — `SAME_AS_HOSPITAL`
**Steps:** register `Clinic One`, `9900090001`, DOB 1990-01-01; record `id`/`publicId`/`customId`.
**Expected:** `customId == "PAT"+id`; the number series is **global**, not per tenant — a clinic patient may be `PAT57` while a hospital patient is `PAT56`. That is expected, not a leak.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CD-021 — ⭐ Clinic parent → child on the same phone → chooser → Use This Patient

`CLINIC_A · RECEPTIONIST · Patients · Critical · Add Patient` — `SAME_AS_HOSPITAL`
**Steps:** 1. Register father `Nitin Rane`, `9900090010`, DOB 1984-02-20. 2. Register child `Ira Rane`, same phone, DOB 2019-07-05. 3. Read the dialog. 4. **Use This Patient** on the father.
**Expected:** **Mobile number already registered** lists only `Nitin Rane` with `Age`, `Patient ID PAT…` — no phone/DOB/address/email; **no child row is created**; exactly one refused POST (409).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CD-022 — ⭐ Clinic: Register Different Patient → child identity + bound acknowledgement

`CLINIC_A · RECEPTIONIST · Patients · Critical · Add Patient` — `SAME_AS_HOSPITAL`
**Steps:** repeat, then click **Register Different Patient**; inspect the second POST and the response.
**Expected:** `?acknowledgeDuplicatePhone=true`; the child is created with its own `PAT` id; `duplicatePhoneAckFor = 9900090010`, `duplicatePhoneAckBy = rec.clina@qa.test`; **the father is unchanged**; Audit Logs show `PATIENT_DUPLICATE_PHONE_ACKNOWLEDGED` with the phone **masked**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CD-023 — Clinic: third family member; chooser lists all; pick the exact child

`CLINIC_A · RECEPTIONIST · Patients · Critical · Add Patient` — `SAME_AS_HOSPITAL`
**Steps:** register the mother on the same number; the chooser lists **both** father and child; click **Use This Patient on Ira**; then repeat and **Register Different Patient**.
**Expected:** rows in registration order, one button each; selecting Ira hands back `id_child`; three active patients on one number.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CD-024 — ⭐ Same phone in CLINIC_A and HOSPITAL_A — no cross-tenant conflict or leak

`CLINIC_A + HOSPITAL_A + CLINIC_B · RECEPTIONIST · Patients · Critical · Add Patient` — `SAME_AS_HOSPITAL`
**Steps:**

1. HOSPITAL_A already has the Kale family on `9900080001` (Tranche 2). In **CLINIC_A** register a patient on **`9900080001`**.
2. In **CLINIC_B** register a patient on `9900090010` (CLINIC_A's Rane number).
3. In **HOSPITAL_A** register a patient on `9900090010`.
   **Expected:** in every case **no chooser appears** (or it lists only that tenant's own patients). **No `Kale` or `Rane` name from another tenant may appear in any chooser** — that would be a **Critical** cross-tenant disclosure via the 409 body (`TC-ISO-010`).
   **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CD-025 — Clinic phone-edit collision and ack lapse

`CLINIC_A · RECEPTIONIST · Patients · High · Edit Patient` — `SAME_AS_HOSPITAL`
**Steps:** = `TC-VIS-007` / `TC-VIS-008` in the clinic.
**Expected:** chooser on collision; ack recorded for the new number; moving to a free number clears the ack; an ack for X never exempts Y.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CD-026 — Clinic inactive patient does not reserve a number

`CLINIC_A · RECEPTIONIST · Patients · High · Add Patient` — `SAME_AS_HOSPITAL`
**Steps:** = `TC-VIS-004` in the clinic.
**Expected:** no chooser; created with no acknowledgement.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## 7. Clinical and configuration deltas

### TC-CD-027 — Lab ordering works in a clinic (PARTIAL feature)

`CLINIC_A · DOCTOR · OPD · High · ConsultationModal` — `SAME_AS_HOSPITAL` (`PARTIAL`)
**Steps:** = `TC-HD-008` as a clinic doctor: tick lab required, select `CBC` and `RBS`, complete, print the case paper; then look for any results screen.
**Expected:** the orders are created and printed on the case paper; **no results/management UI exists** — do not raise that as a bug.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CD-028 — Case paper and prescription PDFs in a clinic

`CLINIC_A · DOCTOR · Documents · High · PDFs` — `CLINIC_VARIANT`
**Steps:** print the case paper and prescription for a clinic patient.
**Expected:** header shows the **clinic's** name and logo; patient line shows the patient's `PAT` id; VITAL SIGNS table built from the clinic's enabled vitals. Never a hospital's name.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CD-029 — Clinic Settings grid contents

`CLINIC_A · HOSPITAL_ADMIN · Settings · High · Settings` — `CLINIC_VARIANT`
**Steps:** open Settings; list every card.
**Expected — present:** Operations Settings, Print & Payment, Vitals, and the Files & Access views. **Absent:** Ventilator Parameters, Severity Scores, ICU Alert Thresholds, OT Permissions, OT Policies (all ICU/OT-gated). Fees lives under Finance as in hospital.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CD-030 — Clinic Operations Settings: which toggles are meaningful

`CLINIC_A · HOSPITAL_ADMIN · Settings · High · Operations` — `CLINIC_VARIANT`
**Steps:** open Operations Settings and record every visible control; change Reception Mode, Billing Handler, Bill Payment Timing, In-Clinic, Barcode.
**Expected:** all five behave as in hospital (`TC-HAS-001`…`005`). **Separate Nurse Login** and **OT Incharge** toggles are meaningless for a clinic — record whether they are hidden (expected) or shown; if shown and toggled, confirm nothing breaks and no nurse/OT surface appears. Mark `NEEDS_PRODUCT_CONFIRMATION` if they are visible.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CD-031 — Clinic Files & Access: which of the 20 form keys are usable

`CLINIC_A · HOSPITAL_ADMIN · Settings · Medium · Files & Access` — `CLINIC_VARIANT` / `NEEDS_PRODUCT_CONFIRMATION`
**Steps:** open each Files & Access view; list the form keys offered; set one nursing key and one OT key to Doctor-only and save.
**Expected:** Files & Access is **not module-gated**, so all 20 keys may appear — but the 5 nursing keys are admission-scoped and the 15 OT keys need a surgery, neither of which a clinic can create. Record exactly which keys are listed. `NEEDS_PRODUCT_CONFIRMATION`: should a clinic be offered OT/IPD form access at all?
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CD-032 — Clinic support tickets and FAQs are clinic-typed

`CLINIC_A · HOSPITAL_ADMIN · Support · Medium · Support` — `CLINIC_VARIANT`
**Steps:** raise a ticket; Super Admin opens **Clinic ▸ Tickets**; create a **Clinic** FAQ and a **Hospital** FAQ; check both tenants.
**Expected:** the clinic ticket appears under Clinic ▸ Tickets, **not** Hospital ▸ Tickets; clinic users see clinic FAQs only (`TC-SA-053`, `TC-SA-057`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CD-033 — Clinic Overview card set

`CLINIC_A · HOSPITAL_ADMIN · Overview · High · Overview` — `CLINIC_VARIANT`
**Steps:** read every Overview card on the full clinic plan.
**Expected:** Core + OPD + Billing + Pharmacy cards. **No IPD card, no Beds card** (no IPD module). Note that `/hospital/dashboard` is hospital-only (`TC-CD-009`), so record which endpoint the clinic Overview actually calls and whether any card silently fails.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CD-034 — ICU/OT settings endpoints refuse a clinic

`CLINIC_A · HOSPITAL_ADMIN · Settings · High · API` — `NOT_SUPPORTED_IN_CLINIC`
**Steps:** `GET`/`PUT` `/hospital/icu/alert-thresholds`, `/hospital/icu/score-types`, `/hospital/icu/ventilator-parameters`, `/hospital/ot/permissions`, `/hospital/ot/policies`, `/hospital/ot/rooms` with the clinic admin token.
**Expected:** **403** on all twelve calls (`@RequireModule` + `@TenantType`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## 8. Module gating and drift inside the clinic's own modules

### TC-CD-035 — Clinic plan catalogue offers only the six sellable modules

`PLATFORM · SUPER_ADMIN · Plans · Critical · Plans` — `CLINIC_VARIANT`
**Steps:** = `TC-SA-009`/`TC-MOD-001`: open Create Plan under Clinic and write down every checkbox; then `TC-MOD-002` — POST a clinic plan containing `IPD`, `OT`, `NURSING`, `ICU`, `HOSPITAL_INVENTORY`.
**Expected:** only OPD, APPOINTMENTS, BILLING, PHARMACY, MEDICAL_INVENTORY, REPORTS offered; each illegal POST → **400**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CD-036 — APPOINTMENTS / BILLING / MEDICAL_INVENTORY / REPORTS gates work in clinic

`CLINIC_B · HOSPITAL_ADMIN · Entitlement · High · sidebar/API` — `SAME_AS_HOSPITAL`
**Pre:** create plan `QA-CLINIC-MIN` with **OPD only**; assign it to CLINIC_B.
**Steps:** log in; list tabs; then call `/clinic/appointments`, `/clinic/settings/fees/custom`, `/clinic/medicines`, `/clinic/stats`.
**Expected:** those four tabs hidden and all four endpoints **403** — these are the gates that _do_ work.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CD-037 — ⚠️ OPD and PHARMACY have no backend gate in clinic either

`CLINIC_B · HOSPITAL_ADMIN · Entitlement · High · API` — `IMPLEMENTATION_DRIFT`
**Steps:** with CLINIC_B on a plan **without** OPD and **without** PHARMACY (e.g. BILLING only): confirm the Patients/OPD/Pharmacy tabs are hidden, then call `GET`/`POST /clinic/opd`, `GET /clinic/patients`, `GET /clinic/pharmacy`.
**Expected (policy):** 403. **Code:** no `@RequireModule("OPD")` or `("PHARMACY")` anywhere → 200 likely → drift (`TC-MOD-010`/`012`). Record whether a POST actually **creates** an OPD case in a tenant that did not buy OPD.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CD-038 — Live module revocation in a clinic

`PLATFORM → CLINIC_A · SUPER_ADMIN → ADM · Entitlement · Critical · UI/API` — `SAME_AS_HOSPITAL`
**Steps:** = `TC-SA-041` / `TC-MOD-020` with CLINIC_A and the **APPOINTMENTS** module (a gate that works): capture an appointments cURL, revoke, replay, refresh, restore.
**Expected:** tab disappears on refresh; the still-valid token gets **403**; appointments data survives and returns on restore.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CD-039 — Clinic ↔ clinic and clinic ↔ hospital isolation

`CLINIC_A + CLINIC_B + HOSPITAL_A · all · Isolation · Critical · API` — `SAME_AS_HOSPITAL`
**Steps:** = `TC-ISO-058` in full, plus: CLINIC_B fetching CLINIC_A's patient/OPD/bill/document by numeric id and publicId; CLINIC_A fetching HOSPITAL_A's; HOSPITAL_A fetching CLINIC_A's; and attempts to **modify** each.
**Expected:** 403/404 throughout; no name, phone, DOB, amount or filename in any body; the target tenant verified unchanged afterwards.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CD-040 — Clinic `isSingleDoctor` mode

`CLINIC_SOLO · HOSPITAL_ADMIN · Special modes · High · Navbar` — `SAME_AS_HOSPITAL`
**Steps:** = `TC-MODE-001`…`009` against `QA Clinic Solo` (`admin.clinsolo@qa.test`).
**Expected:** lands `/hospital/doctor`; switcher works; auto-created doctor exists; the admin can run a full consultation; **no third role is granted** — in particular `/clinic/nurses` and nurse endpoints stay closed.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
