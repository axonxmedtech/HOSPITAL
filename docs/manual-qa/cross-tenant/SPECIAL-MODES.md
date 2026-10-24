# SPECIAL MODES — `isSingleDoctor` and `SINGLE_PHARMACIST_ADMIN`

**Baseline:** `origin/staging` @ `aa143a7` · **Cases:** 18 (`TC-MODE-001` … `TC-MODE-018`)

Two configurations let **one `HOSPITAL_ADMIN` login act as two roles**. They are the only places
in HMS where a single session sees two dashboards, and they are easy to get subtly wrong.

| Mode                      | Set by                                                  | Second role granted | Landing                                                                 | Switcher                              |
| ------------------------- | ------------------------------------------------------- | ------------------- | ----------------------------------------------------------------------- | ------------------------------------- |
| **Single Doctor**         | tenant flag `isSingleDoctor` (Super Admin, at creation) | DOCTOR              | `/hospital/doctor` (or admin if `sessionStorage.activeDashboard=admin`) | Navbar → _Switch to Admin / Doctor_   |
| **Solo Pharmacist Admin** | plan tier `SINGLE_PHARMACIST_ADMIN`                     | PHARMACIST          | `/pharmacy/pharmacy` (or admin if preference set)                       | Navbar → _Switch to Admin / Pharmacy_ |

**How it is enforced (verified):**

- **Frontend:** `App.jsx ProtectedRoute` lets `HOSPITAL_ADMIN` pass the `DOCTOR` guard when `user.isSingleDoctor`, and the `PHARMACIST` guard when `user.modules` includes `SINGLE_PHARMACIST_ADMIN`. `Navbar.jsx:9-16` shows the switcher; the preference is stored in `sessionStorage.activeDashboard`.
- **Backend:** there is **no** role elevation. The admin's JWT still says `HOSPITAL_ADMIN`. Doctor endpoints work only because they list `HOSPITAL_ADMIN` explicitly (e.g. `DoctorController /consultation` = `DOCTOR, HOSPITAL_ADMIN`); pharmacy endpoints are `ADM PHA`. **Anything an admin cannot already call stays forbidden.**
- Single-doctor tenants get a **Doctor record auto-created** for the admin (`PlatformHospitalService:189`).

Test tenants: `QA Clinic Solo` (`admin.clinsolo@qa.test`) and `QA Pharmacy Solo` (`admin.pharmsolo@qa.test`).

---

## A. Single Doctor mode

### TC-MODE-001 — Landing and switcher

| Tenant                                                                                                                                                                                                                     | Role                            | Module            | Priority     |
| -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------- | ----------------- | ------------ |
| CLINIC_SOLO                                                                                                                                                                                                                | HOSPITAL_ADMIN + isSingleDoctor | Auth / Navigation | **Critical** |
| **Steps** 1. Log in at `/login/clinic` as `admin.clinsolo@qa.test`. 2. Record the landing URL. 3. Find the navbar switcher; click **Switch to Admin**. 4. Click **Switch to Doctor**. 5. Refresh on each.                  |
| **Expected** — lands on `/hospital/doctor`; switcher present; switching navigates between `/hospital/doctor` and `/hospital/admin`; **refresh keeps you on the last chosen dashboard** (`sessionStorage.activeDashboard`). |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                                                         |

### TC-MODE-002 — Auto-created doctor record

| Tenant                                                                                                                                                           | Role           | Module  | Priority |
| ---------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------- | ------- | -------- |
| CLINIC_SOLO                                                                                                                                                      | HOSPITAL_ADMIN | Doctors | **High** |
| **Steps** Switch to Admin → **Doctors**.                                                                                                                         |
| **Expected** — exactly one doctor exists, named after the admin, active, created automatically at tenant creation. Reception's doctor picker offers this doctor. |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                               |

### TC-MODE-003 — The admin can run a consultation end-to-end

| Tenant                                                                                                                                                                                                                    | Role                       | Module | Priority     |
| ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------- | ------ | ------------ |
| CLINIC_SOLO                                                                                                                                                                                                               | HOSPITAL_ADMIN (as doctor) | OPD    | **Critical** |
| **Steps** 1. As admin, register a patient and create an OPD case assigned to the auto-doctor. 2. Switch to Doctor. 3. Open the queue, start and complete the consultation with a prescription. 4. Print the prescription. |
| **Expected** — the full doctor workflow works; the prescription PDF names the admin as the doctor; the bill is generated. `POST /hospital/doctors/consultation` returned 200 with an `HOSPITAL_ADMIN` token.              |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                                                        |

### TC-MODE-004 — Dual role does not grant a third role

| Tenant                                                                                                                             | Role           | Module        | Priority     | Endpoint                                                 | Method | Auth       | Expected status |
| ---------------------------------------------------------------------------------------------------------------------------------- | -------------- | ------------- | ------------ | -------------------------------------------------------- | ------ | ---------- | --------------- |
| CLINIC_SOLO                                                                                                                        | HOSPITAL_ADMIN | Authorization | **Critical** | `/hospital/nurse` (workspace), `/hospital/notifications` | GET    | solo admin | **403**         |
| **Steps** With the solo admin's token call a **NURSE-only** endpoint and a nurse-incharge endpoint.                                |
| **Expected** — 403. The mode adds doctor access, nothing else.                                                                     |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______ |

### TC-MODE-005 — A normal admin cannot reach the doctor dashboard

| Tenant                                                                                                                             | Role                               | Module        | Priority |
| ---------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------- | ------------- | -------- |
| HOSPITAL_A                                                                                                                         | HOSPITAL_ADMIN (not single-doctor) | Authorization | **High** |
| **Steps** As `admin.hospa@qa.test` navigate to `/hospital/doctor`; look for a switcher.                                            |
| **Expected** — redirected back to `/hospital/admin`; **no switcher** in the navbar. The mode is per-tenant, not per-role.          |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______ |

### TC-MODE-006 — Adding a second doctor to a single-doctor tenant

| Tenant                                                                                                                                                                                                                 | Role           | Module  | Priority   |
| ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------- | ------- | ---------- |
| CLINIC_SOLO                                                                                                                                                                                                            | HOSPITAL_ADMIN | Doctors | **Medium** |
| **Steps** In Admin → Doctors, try to add a second doctor.                                                                                                                                                              |
| **Expected** — record what happens: is it blocked, allowed, or does the mode silently stop being "single"? Mark `NEEDS_PRODUCT_CONFIRMATION` — the flag name implies one doctor but nothing verified enforces a count. |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                                                     |

### TC-MODE-007 — Session revocation applies to both surfaces

| Tenant                                                                                                                             | Role           | Module | Priority |
| ---------------------------------------------------------------------------------------------------------------------------------- | -------------- | ------ | -------- |
| CLINIC_SOLO                                                                                                                        | HOSPITAL_ADMIN | Auth   | **High** |
| **Steps** 1. Log in and switch to Doctor. 2. Super Admin resets this admin's password. 3. Click any doctor tab.                    |
| **Expected** — 401 → login. One token, one revocation, both dashboards gone.                                                       |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______ |

### TC-MODE-008 — Audit attribution for the dual-role user

| Tenant                                                                                                                                                                                                           | Role           | Module | Priority   |
| ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------- | ------ | ---------- |
| CLINIC_SOLO                                                                                                                                                                                                      | HOSPITAL_ADMIN | Audit  | **Medium** |
| **Steps** After `TC-MODE-003`, open Audit Logs.                                                                                                                                                                  |
| **Expected** — the consultation and the setting changes are both attributed to `admin.clinsolo@qa.test`. Record whether the log distinguishes which "hat" was worn (it likely does not — note as informational). |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                                               |

### TC-MODE-009 — Flag change on an existing tenant

| Tenant                                                                                                                                                                                                             | Role        | Module            | Priority   |
| ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ----------- | ----------------- | ---------- |
| PLATFORM → CLINIC_A                                                                                                                                                                                                | SUPER_ADMIN | Tenant management | **Medium** |
| **Steps** If the tenant edit screen exposes _Single Doctor_, toggle it **on** for QA Clinic A; log in as its admin.                                                                                                |
| **Expected** — record whether the switcher appears and whether a doctor record is created retroactively. If the flag is **not editable** after creation, record that as the design (`NEEDS_PRODUCT_CONFIRMATION`). |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                                                 |

---

## B. Solo Pharmacist Admin mode

### TC-MODE-010 — Landing and switcher

| Tenant                                                                                                                                               | Role                                     | Module            | Priority     |
| ---------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------- | ----------------- | ------------ |
| PHARM_SOLO                                                                                                                                           | HOSPITAL_ADMIN + SINGLE_PHARMACIST_ADMIN | Auth / Navigation | **Critical** |
| **Steps** 1. Log in at `/login/pharmacy` as `admin.pharmsolo@qa.test`. 2. Record the landing URL. 3. Use the switcher both ways. 4. Refresh on each. |
| **Expected** — lands on **`/pharmacy/pharmacy`**; switching goes to `/pharmacy/admin` and back; refresh preserves the choice.                        |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                   |

### TC-MODE-011 — The admin can run the pharmacy counter end-to-end

| Tenant                                                                                                                                                            | Role                           | Module   | Priority     |
| ----------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------ | -------- | ------------ |
| PHARM_SOLO                                                                                                                                                        | HOSPITAL_ADMIN (as pharmacist) | Pharmacy | **Critical** |
| **Steps** In pharmacy mode: add a medicine batch, make a sale at the Billing Counter, print the invoice, process a return.                                        |
| **Expected** — the full pharmacist workflow works with the admin token (`PharmacySaleController` = `ADM PHA`). Stock decrements on sale and increments on return. |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                |

### TC-MODE-012 — A SINGLE_PHARMACY admin does **not** get the switcher

| Tenant                                                                                                                                                          | Role                                  | Module        | Priority |
| --------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------- | ------------- | -------- |
| PHARMACY_A                                                                                                                                                      | HOSPITAL_ADMIN (SINGLE_PHARMACY tier) | Authorization | **High** |
| **Steps** Log in as `admin.pharma@qa.test`; navigate to `/pharmacy/pharmacy`; look for a switcher.                                                              |
| **Expected** — lands on `/pharmacy/admin`; `/pharmacy/pharmacy` **redirects away**; no switcher. Only the `SINGLE_PHARMACIST_ADMIN` tier doubles as pharmacist. |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                              |

### TC-MODE-013 — Tier change requires re-login (JWT claim)

| Tenant                                                                                                                                                                                           | Role        | Module      | Priority |
| ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ----------- | ----------- | -------- |
| PLATFORM → PHARMACY_A                                                                                                                                                                            | SUPER_ADMIN | Entitlement | **High** |
| **Steps** Execute `TC-SA-042` and record here.                                                                                                                                                   |
| **Expected** — after assigning the SOLO tier, a **refresh alone does not** add the switcher (the claim is in the JWT); a re-login does. Expected, not a bug — but document it for support staff. |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                               |

### TC-MODE-014 — Dual role does not grant clinical access

| Tenant                                                                                                                                                                                              | Role           | Module        | Priority     | Endpoint                         | Method | Auth                  | Expected status  |
| --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------- | ------------- | ------------ | -------------------------------- | ------ | --------------------- | ---------------- |
| PHARM_SOLO                                                                                                                                                                                          | HOSPITAL_ADMIN | Authorization | **Critical** | `/hospital/opd`, `/hospital/ipd` | GET    | solo pharmacist admin | **403** expected |
| **Steps** With the solo admin's token call `/pharmacy/opd` and `/pharmacy/ipd` (the pharmacy aliases) and the `/hospital/...` forms.                                                                |
| **Expected (intent)** — 403: a pharmacy tenant has no OPD/IPD. **Code position:** these aliases are ungated (`TC-API-010`), so record the actual result and cross-reference `IMPLEMENTATION_DRIFT`. |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                                  |

### TC-MODE-015 — Adding a separate pharmacist to a solo tenant

| Tenant                                                                                                                                                                                                                                           | Role           | Module          | Priority   |
| ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------- | --------------- | ---------- |
| PHARM_SOLO                                                                                                                                                                                                                                       | HOSPITAL_ADMIN | User management | **Medium** |
| **Steps** In Admin → Pharmacists, add `pharm2.pharmsolo@qa.test`; log in as them.                                                                                                                                                                |
| **Expected** — record whether creation is allowed, and whether the new pharmacist lands on `/pharmacy/pharmacy` and sees the same stock. `NEEDS_PRODUCT_CONFIRMATION` on whether "single pharmacist admin" should permit additional pharmacists. |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                                                                               |

---

## C. Shared behaviours

### TC-MODE-016 — `activeDashboard` preference is tab-scoped and cleared on logout

| Tenant                                                                                                                                                                                                       | Role           | Module  | Priority   |
| ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------- | ------- | ---------- |
| CLINIC_SOLO / PHARM_SOLO                                                                                                                                                                                     | HOSPITAL_ADMIN | Session | **Medium** |
| **Steps** 1. Switch to Admin. 2. Open a **new tab** and log in again. 3. Log out in tab 1 and log back in.                                                                                                   |
| **Expected** — the new tab lands on the **default** (doctor/pharmacy) because `sessionStorage` is per tab; after logout+login in tab 1 the preference is cleared (or record if it persists — informational). |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                                           |

### TC-MODE-017 — Deep link `/ipd/:id` from the dual-role user

| Tenant                                                                                                                                                                                                | Role           | Module | Priority   |
| ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------- | ------ | ---------- |
| CLINIC_SOLO                                                                                                                                                                                           | HOSPITAL_ADMIN | IPD    | **Medium** |
| **Steps** Paste an `/ipd/{id}` URL while in doctor mode.                                                                                                                                              |
| **Expected** — `/ipd/:id` admits `HOSPITAL_ADMIN`, so the page opens. **But** IPD is not sold to clinics: the page should show an empty/denied state, not data. Record; cross-reference `TC-MOD-024`. |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                                    |

### TC-MODE-018 — Both modes on one tenant is impossible

| Tenant                                                                                                                                                                                                                                                                                                                                                                                         | Role        | Module            | Priority |
| ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------- | ----------------- | -------- |
| PLATFORM                                                                                                                                                                                                                                                                                                                                                                                       | SUPER_ADMIN | Tenant onboarding | **Low**  |
| **Steps** Attempt to create a **PHARMACY** tenant with `isSingleDoctor` **on**, and a **CLINIC** tenant on a `SINGLE_PHARMACIST_ADMIN` plan.                                                                                                                                                                                                                                                   |
| **Expected** — the pharmacy-tier plan is not offered to a clinic (`TC-SA-027`). Record whether `isSingleDoctor` can be set on a pharmacy tenant and what the landing becomes; `LandingRedirect` checks `isSingleDoctor` **before** the pharmacy check, so a mis-set flag would send a pharmacy admin to `/hospital/doctor`. Mark `NEEDS_PRODUCT_CONFIRMATION` if the combination is creatable. |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                                                                                                                                                                                                                             |
