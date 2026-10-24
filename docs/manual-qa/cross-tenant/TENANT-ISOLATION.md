# TENANT ISOLATION

**Baseline:** `origin/staging` @ `aa143a7` · **Cases:** 58 (`TC-ISO-001` … `TC-ISO-058`)
**Every case in this document is Critical or High.** This is the most important section in the pack.

---

## 1. The rule being tested

> **Tenant A must never read, modify, delete, link to, or learn of the existence of Tenant B's data — through any screen, any API, any dropdown, any report, any PDF, or any identifier.**

HMS enforces this by taking `hospitalId` **from the JWT**, never from the request
(`SecurityContextHelper.getCurrentHospitalId()`), and filtering every query by it. Your job is to
prove that holds everywhere, including where a developer might have forgotten.

### The one legitimate exception

The **platform medicine catalogue** (`/platform/medicines`) is deliberately global — every tenant
searches the same list. A medicine visible in two tenants is **not** a leak. Nothing else is shared.

---

## 2. How to run every case in this document

**Two browser tabs, two identities** (`sessionStorage` makes this safe — see `TC-AUTH-013`):

```
Tab 1 : HOSPITAL_A   — create the record, capture its identifiers
Tab 2 : HOSPITAL_B   — attempt to reach it
```

**Two tokens** for the API half (see [`../05-API-TEST-TRACK.md`](../05-API-TEST-TRACK.md) §2):

```bash
TOKEN_A="..."   # HOSPITAL_A
TOKEN_B="..."   # HOSPITAL_B
```

### The four verdicts

| Result                       | Verdict                                                                        |
| ---------------------------- | ------------------------------------------------------------------------------ |
| **403 Forbidden**            | ✅ PASS                                                                        |
| **404 Not Found**            | ✅ PASS — the record must look _absent_, not _forbidden_; either is acceptable |
| **200 with an empty list**   | ✅ PASS for collection endpoints                                               |
| **200 with Tenant A's data** | ❌ **CRITICAL FAIL — stop, capture evidence, raise immediately**               |

A **500** is a separate bug: it usually means the tenant check threw instead of filtering.

### Leak check — do this on every response body

Search the response for the record's **name, phone number, date of birth, address, diagnosis,
amount, and `customId`**. Even an error message that echoes a patient's name is a leak.

---

### TC-ISO-001 — Prepare the isolation fixture

| Tenant                  | Role         | Module | Priority     |
| ----------------------- | ------------ | ------ | ------------ |
| HOSPITAL_A + HOSPITAL_B | RECEPTIONIST | Setup  | **Critical** |

**Steps**

1. Complete `02-TEST-DATA-SETUP.md` for both hospitals.
2. In **HOSPITAL_A**, record for each record below: numeric `id`, `publicId`, and display label.
3. In **HOSPITAL_B**, create the same _shape_ of record so you can tell "empty" from "leaked".

| Record              | HOSPITAL_A id / publicId | HOSPITAL_B id / publicId |
| ------------------- | ------------------------ | ------------------------ |
| Patient P1          | ____ / ____              | ____ / ____              |
| Appointment         | ____ / ____              | ____ / ____              |
| OPD case            | ____ / ____              | ____ / ____              |
| IPD admission       | ____ / ____              | ____ / ____              |
| Ward                | ____ / ____              | ____ / ____              |
| Bed                 | ____ / ____              | ____ / ____              |
| Bill                | ____ / ____              | ____ / ____              |
| Pharmacy sale       | ____ / ____              | ____ / ____              |
| Medicine batch      | ____ / ____              | ____ / ____              |
| Staff user (doctor) | ____ / ____              | ____ / ____              |
| Clinical document   | ____ / ____              | ____ / ____              |
| Surgery             | ____ / ____              | ____ / ____              |
| ICU stay            | ____ / ____              | ____ / ____              |

**Expected** — the table is fully populated. Without it the rest of this document cannot be run.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-002 — Confirm the two tokens are genuinely different tenants

| Tenant | Role         | Module | Priority     | Endpoint   | Method |
| ------ | ------------ | ------ | ------------ | ---------- | ------ |
| A + B  | RECEPTIONIST | Setup  | **Critical** | `/auth/me` | GET    |

**Steps** `GET /auth/me` with `TOKEN_A`, then with `TOKEN_B`. Compare the `hospitalId` values.

**Expected** — two different `hospitalId` values. If they match, you captured the same token twice and every subsequent case is invalid.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

## 3. PATIENTS — the reference pattern

> Cases `TC-ISO-003` … `TC-ISO-012` establish the pattern. Every later domain repeats it.

### TC-ISO-003 — Patient list is scoped

| Tenant     | Role         | Module   | Priority     | Endpoint             | Method | Auth      | Expected status   |
| ---------- | ------------ | -------- | ------------ | -------------------- | ------ | --------- | ----------------- |
| HOSPITAL_B | RECEPTIONIST | Patients | **Critical** | `/hospital/patients` | GET    | `TOKEN_B` | **200**, A absent |

**Steps** 1. Tab 2 (B): open Patients, list every row. 2. `GET /hospital/patients` with `TOKEN_B`. 3. Search the response for `Rahul Patil`, `9900011111`, and P1's `customId`.

**Expected** — only HOSPITAL_B patients. **Zero** occurrences of A's names, phones or customIds.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-004 — Patient search cannot reach the other tenant

| Tenant     | Role         | Module   | Priority     | Endpoint                     | Method | Auth      | Expected status |
| ---------- | ------------ | -------- | ------------ | ---------------------------- | ------ | --------- | --------------- |
| HOSPITAL_B | RECEPTIONIST | Patients | **Critical** | `/hospital/patients?search=` | GET    | `TOKEN_B` | **200**, empty  |

**Steps** In Tab 2 search for: 1. `Rahul` (A's patient name) 2. `9900011111` (A's phone) 3. `PAT<P1 id>` 4. A's exact full name.

**Expected** — every search returns **no results** and a normal empty state. Search is the most common leak path: `searchActiveByNameOrPhone` must filter by `hospitalId`.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-005 — ⭐ Fetch A's patient by **numeric id**

| Tenant     | Role         | Module   | Priority     | Endpoint                    | Method | Auth      | Expected status |
| ---------- | ------------ | -------- | ------------ | --------------------------- | ------ | --------- | --------------- |
| HOSPITAL_B | RECEPTIONIST | Patients | **Critical** | `/hospital/patients/{A_id}` | GET    | `TOKEN_B` | **403 or 404**  |

```bash
curl -i -X GET "http://localhost:8080/hospital/patients/{P1_NUMERIC_ID}" \
  -H "Authorization: Bearer $TOKEN_B"
```

**Steps** 1. Run it. 2. Run it again with ids ±1 and ±2 around P1's id (id enumeration). 3. Inspect every body.

**Expected** — **403/404** every time; no name, phone, DOB or address in any response.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-006 — ⭐ Fetch A's patient by **publicId**

| Tenant     | Role         | Module   | Priority     | Endpoint                          | Method | Auth      | Expected status |
| ---------- | ------------ | -------- | ------------ | --------------------------------- | ------ | --------- | --------------- |
| HOSPITAL_B | RECEPTIONIST | Patients | **Critical** | `/hospital/patients/{A_publicId}` | GET    | `TOKEN_B` | **403 or 404**  |

**Why this is separate from `TC-ISO-005`:** `publicId` is globally unique across all tenants, so a
lookup that forgets the tenant filter will succeed. This is the highest-risk identifier in HMS.

**Steps** 1. GET by P1's publicId with `TOKEN_B`. 2. Repeat for the timeline endpoint: `/hospital/patients/{A_publicId}/timeline`. 3. Repeat for `/consultation-details` and `/latest-prescription`.

**Expected** — **403/404** on all four, with no clinical content.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-007 — Update A's patient from B

| Tenant     | Role         | Module   | Priority     | Endpoint                    | Method | Auth      | Expected status |
| ---------- | ------------ | -------- | ------------ | --------------------------- | ------ | --------- | --------------- |
| HOSPITAL_B | RECEPTIONIST | Patients | **Critical** | `/hospital/patients/{A_id}` | PUT    | `TOKEN_B` | **403 or 404**  |

**Steps** 1. PUT a changed name to A's patient id using `TOKEN_B`. 2. Switch to Tab 1 and reload A's patient list.

**Expected** — refused, **and A's patient is unchanged**. A silent 200 that does nothing is still a bug; a 200 that _changes_ the record is **Critical**.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-008 — Delete A's patient from B

| Tenant     | Role         | Module   | Priority     | Endpoint                                   | Method | Auth      | Expected status |
| ---------- | ------------ | -------- | ------------ | ------------------------------------------ | ------ | --------- | --------------- |
| HOSPITAL_B | RECEPTIONIST | Patients | **Critical** | `/hospital/patients/{A_publicId}?reason=x` | DELETE | `TOKEN_B` | **403 or 404**  |

**Steps** 1. DELETE using A's publicId with `TOKEN_B`. 2. Tab 1: confirm P1 is still active and visible.

**Expected** — refused; P1 remains active. Remember deletion is **soft** — also check `is_active` in the database if you have access.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-009 — Change patient status across tenants

| Tenant     | Role         | Module   | Priority | Endpoint                                 | Method | Auth      | Expected status |
| ---------- | ------------ | -------- | -------- | ---------------------------------------- | ------ | --------- | --------------- |
| HOSPITAL_B | RECEPTIONIST | Patients | **High** | `/hospital/patients/{A_publicId}/status` | PUT    | `TOKEN_B` | **403 or 404**  |

**Steps** PUT a status change, then `POST /hospital/patients/{A_publicId}/start-consultation`.

**Expected** — both refused; A's patient status unchanged in Tab 1.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-010 — ⭐ Same phone in two tenants stays separate

| Tenant | Role         | Module   | Priority     |
| ------ | ------------ | -------- | ------------ |
| A + B  | RECEPTIONIST | Patients | **Critical** |

**Context:** P1 (HOSPITAL_A) and P6 (HOSPITAL_B) deliberately share phone `9900011111`.

**Steps**

1. Tab 2 (B): register a **new** patient with phone `9900011111`.
2. Read the duplicate-phone dialog carefully **if one appears**.
3. Tab 1 (A): register another patient with the same phone.

**Expected**

- Step 2: the conflict dialog either does **not** appear, or lists **only HOSPITAL_B** patients (P6). It must **never** list Rahul Patil from HOSPITAL_A.
- ⚠️ **If A's patient name appears in B's conflict dialog, that is a Critical cross-tenant disclosure** — the 409 conflict body would be leaking a patient's name and age across tenants.
- Step 3: A's dialog lists only A's patients.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-011 — A's patient does not appear in B's dropdowns

| Tenant     | Role         | Module   | Priority     |
| ---------- | ------------ | -------- | ------------ |
| HOSPITAL_B | RECEPTIONIST | Patients | **Critical** |

**Steps** In Tab 2, open every screen with a patient picker — Add Appointment, Add OPD, IPD Admit, Billing, Pharmacy Billing Counter, Patient Documents — and type `Rahul`, then `9900011111`.

**Expected** — no HOSPITAL_A patient is offered in **any** autocomplete. Dropdowns often use a different endpoint from the main list and are a classic missed filter.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-012 — B cannot link A's patient into a B workflow

| Tenant     | Role         | Module         | Priority     | Endpoint                                  | Method | Auth      | Expected status |
| ---------- | ------------ | -------------- | ------------ | ----------------------------------------- | ------ | --------- | --------------- |
| HOSPITAL_B | RECEPTIONIST | Patients / OPD | **Critical** | `/hospital/opd`, `/hospital/appointments` | POST   | `TOKEN_B` | **403 or 404**  |

**Steps** 1. Capture a valid `POST /hospital/opd` body from B. 2. Replace `patientId` with **A's** patient id. Send with `TOKEN_B`. 3. Repeat for `POST /hospital/appointments`. 4. Repeat for `POST /hospital/ipd`.

**Expected** — all refused; **no record is created in either tenant**. Check both tenants' OPD lists afterwards. A created record that spans two tenants is the worst possible outcome here — **Critical**, and it must be deleted from the database before testing continues.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

## 4. APPOINTMENTS

### TC-ISO-013 — Appointment list scoped

| Tenant     | Role         | Module       | Priority     | Endpoint                 | Method | Auth      | Expected status   |
| ---------- | ------------ | ------------ | ------------ | ------------------------ | ------ | --------- | ----------------- |
| HOSPITAL_B | RECEPTIONIST | Appointments | **Critical** | `/hospital/appointments` | GET    | `TOKEN_B` | **200**, A absent |

**Steps** List in the UI and via API; search A's patient name; check today's-appointments and the calendar view.
**Expected** — none of A's appointments appear anywhere, including the dashboard "today" widget.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-014 — Appointment by id / publicId

| Tenant     | Role         | Module       | Priority     | Endpoint                        | Method | Auth      | Expected status |
| ---------- | ------------ | ------------ | ------------ | ------------------------------- | ------ | --------- | --------------- |
| HOSPITAL_B | RECEPTIONIST | Appointments | **Critical** | `/hospital/appointments/{A_id}` | GET    | `TOKEN_B` | **403/404**     |

**Steps** GET by numeric id, then by publicId; also try the consultation-details endpoint for A's appointment id.
**Expected** — 403/404, no patient name, no doctor name, no time slot disclosed.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-015 — Update / cancel / delete A's appointment from B

| Tenant     | Role         | Module       | Priority     | Endpoint                        | Method      | Auth      | Expected status |
| ---------- | ------------ | ------------ | ------------ | ------------------------------- | ----------- | --------- | --------------- |
| HOSPITAL_B | RECEPTIONIST | Appointments | **Critical** | `/hospital/appointments/{A_id}` | PUT, DELETE | `TOKEN_B` | **403/404**     |

**Steps** PUT a reschedule; DELETE; then verify in Tab 1 that A's appointment is unchanged and still active.
**Expected** — refused and unchanged.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

## 5. OPD

### TC-ISO-016 — OPD list scoped

| Tenant     | Role         | Module | Priority     | Endpoint        | Method | Auth      | Expected status   |
| ---------- | ------------ | ------ | ------------ | --------------- | ------ | --------- | ----------------- |
| HOSPITAL_B | RECEPTIONIST | OPD    | **Critical** | `/hospital/opd` | GET    | `TOKEN_B` | **200**, A absent |

**Note for testers:** the `opd` table has **no `hospital_id` column** — tenancy is enforced by
joining through the patient. That makes OPD a higher-risk area than most; test it carefully.

**Steps** List OPD in B; filter by today; search A's patient; check the OPD queue widget.
**Expected** — no A cases anywhere.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-017 — OPD case by id

| Tenant     | Role   | Module | Priority     | Endpoint                                                            | Method | Auth      | Expected status |
| ---------- | ------ | ------ | ------------ | ------------------------------------------------------------------- | ------ | --------- | --------------- |
| HOSPITAL_B | DOCTOR | OPD    | **Critical** | `/hospital/opd/{A_id}`, `/hospital/doctors/consultation/opd/{A_id}` | GET    | `TOKEN_B` | **403/404**     |

**Steps** GET A's OPD id on both endpoints with B's doctor token.
**Expected** — 403/404; no vitals, no problem text, no diagnosis.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-018 — OPD PDFs across tenants

| Tenant     | Role   | Module          | Priority     | Endpoint                                                                                       | Method | Auth      | Expected status |
| ---------- | ------ | --------------- | ------------ | ---------------------------------------------------------------------------------------------- | ------ | --------- | --------------- |
| HOSPITAL_B | DOCTOR | OPD / Documents | **Critical** | `/hospital/patients/opd/{A_id}/medicines/pdf`, `/hospital/doctors/prescription/opd/{A_id}/pdf` | GET    | `TOKEN_B` | **403/404**     |

**Steps** Request both PDFs for A's OPD id with `TOKEN_B`. If a file downloads, **open it**.
**Expected** — refused. **If a PDF downloads containing A's patient name or prescription, that is Critical** — PDF endpoints are data endpoints (cross-reference `TC-PERM-006`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

## 6. IPD & ADMISSIONS

### TC-ISO-019 — IPD list scoped

| Tenant     | Role         | Module | Priority     | Endpoint        | Method | Auth      | Expected status   |
| ---------- | ------------ | ------ | ------------ | --------------- | ------ | --------- | ----------------- |
| HOSPITAL_B | RECEPTIONIST | IPD    | **Critical** | `/hospital/ipd` | GET    | `TOKEN_B` | **200**, A absent |

**Steps** List current admissions and requested admissions in B; search A's patient.
**Expected** — no A admissions.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-020 — ⭐ `/ipd/:id` — the only deep-linkable record screen

| Tenant     | Role   | Module | Priority     | Screen                  | Auth      | Expected  |
| ---------- | ------ | ------ | ------------ | ----------------------- | --------- | --------- |
| HOSPITAL_B | DOCTOR | IPD    | **Critical** | `/ipd/{A_admission_id}` | B session | no A data |

**Why this case is special:** this is the **only** place in HMS where a tester can perform a true
UI-level ID-tampering test from the browser address bar.

**Steps**

1. Tab 1 (A): open an IPD admission and copy the URL, e.g. `http://localhost:5173/ipd/17`.
2. Tab 2 (B): log in as `doc1.hospb@qa.test`, paste that exact URL, press Enter.
3. Watch DevTools → Network for the API calls the page makes.
4. Try ids around it: `/ipd/16`, `/ipd/18`.

**Expected**

- No HOSPITAL_A patient name, diagnosis, ward, bed, vitals or notes render — not even briefly before an error.
- The API calls return **403/404**.
- The user sees a clean "not found"/"not permitted" state, not a broken page or a React crash.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-021 — Admission / discharge / transfer actions across tenants

| Tenant     | Role         | Module | Priority     | Endpoint                 | Method    | Auth      | Expected status |
| ---------- | ------------ | ------ | ------------ | ------------------------ | --------- | --------- | --------------- |
| HOSPITAL_B | RECEPTIONIST | IPD    | **Critical** | `/hospital/ipd/{A_id}/*` | POST, PUT | `TOKEN_B` | **403/404**     |

**Steps** Against A's admission id with `TOKEN_B`: attempt discharge, transfer, and any status change the UI offers in B. Then verify in Tab 1 that A's admission is untouched and its bed is still occupied.
**Expected** — all refused; A's admission and bed state unchanged. A cross-tenant discharge would free a bed in another hospital — **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-022 — Nursing records attached to A's admission

| Tenant     | Role  | Module  | Priority     | Endpoint                                                                                 | Method    | Auth      | Expected status |
| ---------- | ----- | ------- | ------------ | ---------------------------------------------------------------------------------------- | --------- | --------- | --------------- |
| HOSPITAL_B | NURSE | Nursing | **Critical** | `/hospital/nurse/vitals`, `/notes`, `/sugar-chart`, `/medication`, `/initial-assessment` | GET, POST | B's nurse | **403/404**     |

**Steps** With B's nurse token, GET each nursing endpoint using **A's** `ipdAdmissionId`, then POST a record against it.
**Expected** — every call refused; **no nursing record is written against A's admission**. Verify in Tab 1.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

## 7. WARDS & BEDS

### TC-ISO-023 — Ward list scoped

| Tenant     | Role           | Module | Priority     | Endpoint          | Method | Auth      | Expected status   |
| ---------- | -------------- | ------ | ------------ | ----------------- | ------ | --------- | ----------------- |
| HOSPITAL_B | HOSPITAL_ADMIN | Wards  | **Critical** | `/hospital/wards` | GET    | `TOKEN_B` | **200**, A absent |

**Steps** List wards in B's UI and API. A's `General Ward A`, `ICU Ward` and `OT-1` must not appear.
**Expected** — only B's wards.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-024 — Bed list and bed status across tenants

| Tenant     | Role           | Module | Priority     | Endpoint                                  | Method   | Auth      | Expected status          |
| ---------- | -------------- | ------ | ------------ | ----------------------------------------- | -------- | --------- | ------------------------ |
| HOSPITAL_B | HOSPITAL_ADMIN | Beds   | **Critical** | `/hospital/beds`, `/hospital/beds/{A_id}` | GET, PUT | `TOKEN_B` | **200 scoped / 403/404** |

**Steps** 1. List beds in B. 2. GET A's bed by id. 3. Attempt to change A's bed status (e.g. mark cleaned).
**Expected** — A's beds absent from the list; id lookup refused; **A's bed status unchanged** (verify in Tab 1). Changing another hospital's bed state would corrupt their occupancy — **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-025 — Ward incharge assignment across tenants

| Tenant     | Role           | Module          | Priority | Endpoint                         | Method | Auth      | Expected status |
| ---------- | -------------- | --------------- | -------- | -------------------------------- | ------ | --------- | --------------- |
| HOSPITAL_B | HOSPITAL_ADMIN | Wards / Nursing | **High** | `/hospital/nurses/ward-incharge` | POST   | `TOKEN_B` | **403/404**     |

**Steps** Attempt to set **B's** nurse as incharge of **A's** ward id, and A's nurse as incharge of B's ward.
**Expected** — both refused. Cross-tenant staff-to-ward linking must be impossible.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-026 — Bed dropdowns during admission

| Tenant     | Role         | Module     | Priority     |
| ---------- | ------------ | ---------- | ------------ |
| HOSPITAL_B | RECEPTIONIST | IPD / Beds | **Critical** |

**Steps** In B, start an IPD admission and open the ward dropdown, then the bed dropdown.
**Expected** — only B's wards and beds are offered. A's `General Ward A` / `GA-01` must not be selectable.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

## 8. ICU

### TC-ISO-027 — ICU dashboard and bed board scoped

| Tenant     | Role           | Module | Priority     | Endpoint        | Method | Auth      | Expected status   |
| ---------- | -------------- | ------ | ------------ | --------------- | ------ | --------- | ----------------- |
| HOSPITAL_B | HOSPITAL_ADMIN | ICU    | **Critical** | `/hospital/icu` | GET    | `TOKEN_B` | **200**, A absent |

**Steps** Open ICU Dashboard and ICU Bed Board in B; compare against A's.
**Expected** — no A patients or A ICU beds.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-028 — ICU stay by id

| Tenant     | Role   | Module | Priority     | Endpoint                    | Method   | Auth      | Expected status |
| ---------- | ------ | ------ | ------------ | --------------------------- | -------- | --------- | --------------- |
| HOSPITAL_B | DOCTOR | ICU    | **Critical** | `/hospital/icu/{A_stay_id}` | GET, PUT | `TOKEN_B` | **403/404**     |

**Steps** GET and PUT A's ICU stay id with `TOKEN_B`.
**Expected** — refused; no severity score, ventilator setting or infusion data disclosed.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-029 — ICU clinical sub-records across tenants

| Tenant     | Role  | Module | Priority     | Endpoint                                                              | Method    | Auth      | Expected status |
| ---------- | ----- | ------ | ------------ | --------------------------------------------------------------------- | --------- | --------- | --------------- |
| HOSPITAL_B | NURSE | ICU    | **Critical** | `/hospital/nurse/infusions`, `/io`, `/ventilator`, `/severity-scores` | GET, POST | B's nurse | **403/404**     |

**Steps** Using A's ICU stay / admission id, GET then POST on each of the four endpoints.
**Expected** — all refused; nothing written against A's stay.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

## 9. OPERATION THEATRE

### TC-ISO-030 — Surgery list and board scoped

| Tenant     | Role        | Module | Priority     | Endpoint              | Method | Auth         | Expected status   |
| ---------- | ----------- | ------ | ------------ | --------------------- | ------ | ------------ | ----------------- |
| HOSPITAL_B | OT_INCHARGE | OT     | **Critical** | `/hospital/surgeries` | GET    | B's OT token | **200**, A absent |

**Steps** Open the OT Board and Requests in B; call the API.
**Expected** — no A surgeries, no A patient names on the board.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-031 — Surgery by id and lifecycle actions

| Tenant     | Role        | Module | Priority     | Endpoint                                                        | Method    | Auth         | Expected status |
| ---------- | ----------- | ------ | ------------ | --------------------------------------------------------------- | --------- | ------------ | --------------- |
| HOSPITAL_B | OT_INCHARGE | OT     | **Critical** | `/hospital/surgeries/{A_id}`, `/hospital/ot/surgeries/{A_id}/*` | GET, POST | B's OT token | **403/404**     |

**Steps** GET A's surgery; then attempt schedule, start and complete against A's surgery id.
**Expected** — refused; A's surgery status unchanged in Tab 1. A cross-tenant "Complete surgery" would corrupt a clinical record — **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-032 — Surgery / NABH forms across tenants

| Tenant     | Role  | Module | Priority     | Endpoint                  | Method    | Auth      | Expected status |
| ---------- | ----- | ------ | ------------ | ------------------------- | --------- | --------- | --------------- |
| HOSPITAL_B | NURSE | OT     | **Critical** | `/hospital/surgery-forms` | GET, POST | B's nurse | **403/404**     |

**Steps** 1. In A, fill and save one NABH consent form. 2. From B, GET that form by A's surgery id. 3. POST a form against A's surgery id. 4. If any form renders or prints, check the **UHID field** — it must show the _patient's_ id, never the hospital's.
**Expected** — refused; no consent content disclosed.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-033 — OT rooms, recovery bays and team across tenants

| Tenant     | Role           | Module | Priority | Endpoint                                            | Method   | Auth      | Expected status          |
| ---------- | -------------- | ------ | -------- | --------------------------------------------------- | -------- | --------- | ------------------------ |
| HOSPITAL_B | HOSPITAL_ADMIN | OT     | **High** | `/hospital/ot/rooms`, `/recovery-bays`, `/recovery` | GET, PUT | `TOKEN_B` | **200 scoped / 403/404** |

**Steps** List each in B; then GET/PUT A's room id and bay id.
**Expected** — lists contain only B's resources; id lookups refused.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

## 10. BILLING & PAYMENTS

### TC-ISO-034 — Bill list scoped

| Tenant     | Role         | Module  | Priority     | Endpoint            | Method | Auth      | Expected status   |
| ---------- | ------------ | ------- | ------------ | ------------------- | ------ | --------- | ----------------- |
| HOSPITAL_B | RECEPTIONIST | Billing | **Critical** | `/hospital/billing` | GET    | `TOKEN_B` | **200**, A absent |

**Steps** List bills in B; search by A's patient name and by A's bill number; check dashboard revenue figures.
**Expected** — no A bills; **B's revenue total does not include A's amounts** (a very common aggregation leak).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-035 — Bill by id and receipt download

| Tenant     | Role         | Module  | Priority     | Endpoint                                | Method | Auth      | Expected status |
| ---------- | ------------ | ------- | ------------ | --------------------------------------- | ------ | --------- | --------------- |
| HOSPITAL_B | RECEPTIONIST | Billing | **Critical** | `/hospital/billing/{A_id}`, receipt PDF | GET    | `TOKEN_B` | **403/404**     |

**Steps** GET A's bill by id; then request its receipt PDF. If a file downloads, open it.
**Expected** — refused; **no PDF containing A's hospital name, patient name or amounts**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-036 — Record a payment against A's bill from B

| Tenant     | Role         | Module             | Priority     | Endpoint                           | Method    | Auth      | Expected status |
| ---------- | ------------ | ------------------ | ------------ | ---------------------------------- | --------- | --------- | --------------- |
| HOSPITAL_B | RECEPTIONIST | Billing / Payments | **Critical** | `/hospital/billing/{A_id}/payment` | POST, PUT | `TOKEN_B` | **403/404**     |

**Steps** Attempt to add a payment to A's bill. Then check A's bill balance in Tab 1.
**Expected** — refused; **A's bill balance unchanged**. A cross-tenant payment is a financial-integrity failure.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-037 — Fees and services configuration scoped

| Tenant     | Role           | Module | Priority | Endpoint                                                    | Method   | Auth      | Expected status |
| ---------- | -------------- | ------ | -------- | ----------------------------------------------------------- | -------- | --------- | --------------- |
| HOSPITAL_B | HOSPITAL_ADMIN | Fees   | **High** | `/hospital/settings/fees`, `/hospital/settings/fees/custom` | GET, PUT | `TOKEN_B` | **200 scoped**  |

**Steps** 1. Set a distinctive consultation fee in A (e.g. 777). 2. Read B's fees. 3. Change B's fees and re-check A's.
**Expected** — each tenant has independent fees; changing one never affects the other.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

## 11. PHARMACY SALES

### TC-ISO-038 — Sales list scoped

| Tenant     | Role       | Module   | Priority     | Endpoint          | Method | Auth             | Expected status   |
| ---------- | ---------- | -------- | ------------ | ----------------- | ------ | ---------------- | ----------------- |
| PHARMACY_B | PHARMACIST | Pharmacy | **Critical** | `/pharmacy/sales` | GET    | PHARMACY_B token | **200**, A absent |

**Steps** Run between **PHARMACY_A** and **PHARMACY_B**. List sales and billing history in B; search A's invoice number.
**Expected** — only B's sales; B's totals exclude A's.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-039 — Sale by id and invoice print

| Tenant     | Role       | Module   | Priority     | Endpoint                 | Method | Auth             | Expected status |
| ---------- | ---------- | -------- | ------------ | ------------------------ | ------ | ---------------- | --------------- |
| PHARMACY_B | PHARMACIST | Pharmacy | **Critical** | `/pharmacy/sales/{A_id}` | GET    | PHARMACY_B token | **403/404**     |

**Steps** GET A's sale by id; attempt to print/download its invoice.
**Expected** — refused; no customer name, medicine list or amount disclosed.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-040 — Return / refund against another tenant's sale

| Tenant     | Role       | Module   | Priority     | Endpoint                        | Method | Auth             | Expected status |
| ---------- | ---------- | -------- | ------------ | ------------------------------- | ------ | ---------------- | --------------- |
| PHARMACY_B | PHARMACIST | Pharmacy | **Critical** | `/pharmacy/sales/{A_id}/return` | POST   | PHARMACY_B token | **403/404**     |

**Steps** Attempt a return against A's sale id. Then check A's sale and A's stock levels.
**Expected** — refused; **A's stock is not incremented**. A cross-tenant refund would move money and stock in another business.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

## 12. PHARMACY INVENTORY & BATCHES

### TC-ISO-041 — Inventory list scoped

| Tenant     | Role       | Module    | Priority     | Endpoint              | Method | Auth             | Expected status   |
| ---------- | ---------- | --------- | ------------ | --------------------- | ------ | ---------------- | ----------------- |
| PHARMACY_B | PHARMACIST | Inventory | **Critical** | `/pharmacy/inventory` | GET    | PHARMACY_B token | **200**, A absent |

**Steps** Compare stock quantities between A and B for the same medicine. Set A's `QA Paracetamol 500` to 100 and B's to 7.
**Expected** — B shows 7, A shows 100. Stock is per tenant even though the **medicine catalogue** is global (that part is expected — see §1).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-042 — Batch by id across tenants

| Tenant     | Role       | Module    | Priority     | Endpoint                           | Method   | Auth             | Expected status |
| ---------- | ---------- | --------- | ------------ | ---------------------------------- | -------- | ---------------- | --------------- |
| PHARMACY_B | PHARMACIST | Inventory | **Critical** | `/pharmacy/inventory/{A_batch_id}` | GET, PUT | PHARMACY_B token | **403/404**     |

**Steps** GET A's batch id; then PUT a quantity change to it.
**Expected** — refused; **A's batch quantity unchanged**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-043 — Sell from another tenant's batch

| Tenant     | Role       | Module   | Priority     | Endpoint          | Method | Auth             | Expected status |
| ---------- | ---------- | -------- | ------------ | ----------------- | ------ | ---------------- | --------------- |
| PHARMACY_B | PHARMACIST | Pharmacy | **Critical** | `/pharmacy/sales` | POST   | PHARMACY_B token | **403/404**     |

**Steps** Capture a valid sale payload in B, replace the batch id with **A's** batch id, send.
**Expected** — refused. **Check A's stock afterwards** — if it decremented, that is a **Critical** cross-tenant inventory mutation.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-044 — Suppliers, manufacturers, categories, purchases

| Tenant     | Role       | Module           | Priority | Endpoint                                                             | Method   | Auth             | Expected status          |
| ---------- | ---------- | ---------------- | -------- | -------------------------------------------------------------------- | -------- | ---------------- | ------------------------ |
| PHARMACY_B | PHARMACIST | Pharmacy masters | **High** | `/pharmacy/suppliers`, `/manufacturers`, `/categories`, `/purchases` | GET, PUT | PHARMACY_B token | **200 scoped / 403/404** |

**Steps** List each in B (A's `QA Supplier One` must be absent), then GET/PUT A's supplier id, manufacturer id, category id and purchase id.
**Expected** — lists scoped; all id lookups refused.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

## 13. USERS & STAFF

### TC-ISO-045 — Staff lists scoped

| Tenant     | Role           | Module          | Priority     | Endpoint                                                                          | Method | Auth      | Expected status   |
| ---------- | -------------- | --------------- | ------------ | --------------------------------------------------------------------------------- | ------ | --------- | ----------------- |
| HOSPITAL_B | HOSPITAL_ADMIN | User management | **Critical** | `/hospital/doctors`, `/nurses`, `/receptionists`, `/pharmacists`, `/ot-incharges` | GET    | `TOKEN_B` | **200**, A absent |

**Steps** List each staff type in B. Search for `Dr Meera Kulkarni` and `doc1.hospa@qa.test`.
**Expected** — none of A's staff appear; **no email addresses from A are disclosed**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-046 — Staff by id, edit and delete across tenants

| Tenant     | Role           | Module          | Priority     | Endpoint                   | Method           | Auth      | Expected status |
| ---------- | -------------- | --------------- | ------------ | -------------------------- | ---------------- | --------- | --------------- |
| HOSPITAL_B | HOSPITAL_ADMIN | User management | **Critical** | `/hospital/doctors/{A_id}` | GET, PUT, DELETE | `TOKEN_B` | **403/404**     |

**Steps** GET, PUT (rename) and DELETE A's doctor id with `TOKEN_B`. Then check A's doctor list.
**Expected** — refused; A's doctor unchanged and still active.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-047 — ⭐ Reset another tenant's staff password

| Tenant     | Role           | Module          | Priority     | Endpoint                                  | Method | Auth      | Expected status |
| ---------- | -------------- | --------------- | ------------ | ----------------------------------------- | ------ | --------- | --------------- |
| HOSPITAL_B | HOSPITAL_ADMIN | User management | **Critical** | `/hospital/doctors/{A_id}/reset-password` | POST   | `TOKEN_B` | **403/404**     |

**Steps** 1. POST a password reset for **A's** doctor using `TOKEN_B`. 2. Attempt to log in as `doc1.hospa@qa.test` with the password B tried to set. 3. Confirm the original password still works.
**Expected** — refused; A's doctor's password is unchanged. Account takeover across tenants is the single worst outcome in this document.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-048 — Doctor dropdowns scoped

| Tenant     | Role         | Module             | Priority     |
| ---------- | ------------ | ------------------ | ------------ |
| HOSPITAL_B | RECEPTIONIST | Appointments / OPD | **Critical** |

**Steps** In B, open the doctor picker on Add Appointment, Add OPD, IPD Admit and Surgery Schedule.
**Expected** — only B's doctors are offered. A's `Dr Meera Kulkarni` must not appear in any picker.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

## 14. CLINICAL DOCUMENTS

### TC-ISO-049 — Document list scoped to patient and tenant

| Tenant     | Role         | Module    | Priority     | Endpoint                            | Method | Auth      | Expected status |
| ---------- | ------------ | --------- | ------------ | ----------------------------------- | ------ | --------- | --------------- |
| HOSPITAL_B | RECEPTIONIST | Documents | **Critical** | `/hospital/patients/{id}/documents` | GET    | `TOKEN_B` | **403/404**     |

**Steps** GET documents using **A's** patient id/publicId with `TOKEN_B`.
**Expected** — refused; **no filenames disclosed** (a filename can itself carry a patient's name).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-050 — ⭐ Download another tenant's clinical document

| Tenant     | Role   | Module    | Priority     | Endpoint                    | Method | Auth      | Expected status |
| ---------- | ------ | --------- | ------------ | --------------------------- | ------ | --------- | --------------- |
| HOSPITAL_B | DOCTOR | Documents | **Critical** | document download / preview | GET    | `TOKEN_B` | **403/404**     |

**Steps** 1. Tab 1 (A): open P1's uploaded document, capture the download request (Copy as cURL). 2. Replay with `TOKEN_B`. 3. Try both the numeric id and the publicId form. 4. If bytes come back, open the file.
**Expected** — refused; **no file content**. Clinical documents are the most sensitive artefact in HMS; a leak here is the highest-severity finding possible.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-051 — Upload a document against another tenant's patient

| Tenant     | Role         | Module    | Priority     | Endpoint        | Method | Auth      | Expected status |
| ---------- | ------------ | --------- | ------------ | --------------- | ------ | --------- | --------------- |
| HOSPITAL_B | RECEPTIONIST | Documents | **Critical** | document upload | POST   | `TOKEN_B` | **403/404**     |

**Steps** Upload a synthetic file targeting **A's** patient id with `TOKEN_B`. Then check A's document list.
**Expected** — refused; nothing appears under A's patient.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-052 — Archive another tenant's document

| Tenant     | Role   | Module    | Priority     | Endpoint                | Method     | Auth      | Expected status |
| ---------- | ------ | --------- | ------------ | ----------------------- | ---------- | --------- | --------------- |
| HOSPITAL_B | DOCTOR | Documents | **Critical** | document archive/delete | DELETE/PUT | `TOKEN_B` | **403/404**     |

**Steps** Attempt to archive A's document; then confirm it is still visible and downloadable in A.
**Expected** — refused; A's document intact.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

## 15. REPORTS & STATISTICS

### TC-ISO-053 — Dashboard statistics exclude the other tenant

| Tenant     | Role           | Module  | Priority     | Endpoint                                 | Method | Auth      | Expected status     |
| ---------- | -------------- | ------- | ------------ | ---------------------------------------- | ------ | --------- | ------------------- |
| HOSPITAL_B | HOSPITAL_ADMIN | Reports | **Critical** | `/hospital/dashboard`, `/hospital/stats` | GET    | `TOKEN_B` | **200**, A excluded |

**Steps**

1. Count HOSPITAL_A's patients, OPD cases, admissions and revenue by hand.
2. Do the same for B.
3. Open B's Overview and compare every tile against B's hand count.

**Expected** — every figure matches **B only**. Aggregates are the easiest place to forget a tenant filter and the hardest to notice: a total that silently includes another hospital is a **Critical** leak even though no name is displayed.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-054 — Report PDFs are scoped

| Tenant     | Role           | Module  | Priority     | Endpoint                        | Method | Auth      | Expected status   |
| ---------- | -------------- | ------- | ------------ | ------------------------------- | ------ | --------- | ----------------- |
| HOSPITAL_B | HOSPITAL_ADMIN | Reports | **Critical** | `/hospital/patients/report/pdf` | GET    | `TOKEN_B` | **200**, A absent |

**Steps** Download B's patient report PDF and **open it**. Read every row.
**Expected** — contains only B's patients, and the header shows B's hospital name.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-055 — Pharmacy reports scoped

| Tenant     | Role       | Module  | Priority | Endpoint            | Method | Auth             | Expected status   |
| ---------- | ---------- | ------- | -------- | ------------------- | ------ | ---------------- | ----------------- |
| PHARMACY_B | PHARMACIST | Reports | **High** | `/pharmacy/reports` | GET    | PHARMACY_B token | **200**, A absent |

**Steps** Run every report B offers (sales, stock, expiry, purchase) and compare totals against B's own data.
**Expected** — all figures are B's alone.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

## 16. AUDIT LOGS

### TC-ISO-056 — Tenant audit log is scoped

| Tenant     | Role           | Module | Priority     | Endpoint               | Method | Auth      | Expected status   |
| ---------- | -------------- | ------ | ------------ | ---------------------- | ------ | --------- | ----------------- |
| HOSPITAL_B | HOSPITAL_ADMIN | Audit  | **Critical** | `/hospital/audit-logs` | GET    | `TOKEN_B` | **200**, A absent |

**Steps** 1. In A, perform three audited actions (create patient, delete patient with a reason, acknowledge a duplicate phone). 2. In B, open Audit Logs and search for A's patient name, A's user emails, and the reason text.
**Expected** — no A entries. The audit trail must not become a back door into another tenant's activity.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-057 — Audit log does not leak secrets or full phone numbers

| Tenant     | Role           | Module          | Priority |
| ---------- | -------------- | --------------- | -------- |
| HOSPITAL_A | HOSPITAL_ADMIN | Audit / Privacy | **High** |

**Steps** 1. In A, register the child P2 acknowledging the shared phone. 2. Open Audit Logs and find `PATIENT_DUPLICATE_PHONE_ACKNOWLEDGED`.
**Expected**

- The entry exists, names the acting user and the existing patient's `customId`.
- **The phone number is masked** (e.g. `99******11`) — the full number must not appear.
- No password, hash or token appears anywhere in the log.
  **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-ISO-058 — Clinic ↔ clinic and cross-tenant-**type** isolation

| Tenant                           | Role           | Module      | Priority     | Endpoint                | Method | Auth         | Expected status |
| -------------------------------- | -------------- | ----------- | ------------ | ----------------------- | ------ | ------------ | --------------- |
| CLINIC_A / CLINIC_B / HOSPITAL_A | HOSPITAL_ADMIN | All domains | **Critical** | `/clinic/patients/{id}` | GET    | other tenant | **403/404**     |

**Why:** every case above used two hospitals. This repeats the core checks across **clinics**, and
across **different tenant types** — where the URL namespace differs and a filter might be missed.

**Steps**

1. CLINIC_B token → `GET /clinic/patients/{CLINIC_A_patient_id}` and by publicId.
2. CLINIC_B token → `GET /clinic/patients?search=Sanjay` (P8/P9 share a name and phone).
3. **HOSPITAL_A** token → `GET /hospital/patients/{CLINIC_A_patient_id}` (cross **type**).
4. **CLINIC_A** token → `GET /clinic/patients/{HOSPITAL_A_patient_id}`.
5. **PHARMACY_A** token → `GET /pharmacy/patients/{HOSPITAL_A_patient_id}`.

**Expected** — **403/404** on all five; B's search returns only B's Sanjay. Crossing tenant _types_
must be no more permissive than crossing tenants of the same type.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______
