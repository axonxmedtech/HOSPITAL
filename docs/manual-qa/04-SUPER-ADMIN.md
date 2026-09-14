# 04 — SUPER ADMIN (Platform Dashboard)

**Baseline:** `origin/staging` @ `aa143a7` · **Cases:** 62 (`TC-SA-001` … `TC-SA-062`)

## Scope — what actually exists

The Platform Dashboard (`/platform/dashboard`, `pages/platform/PlatformDashboard.jsx`) has a
sidebar of **three tenant-type groups plus two global screens**:

```
Dashboard
Hospital ▸  Hospitals · Inventory Items · Plans · Tickets · FAQs
Clinic   ▸  Clinics   · Inventory Items · Plans · Tickets · FAQs
Pharmacy ▸  Pharmacies ·                 Plans · Tickets · FAQs
Medicines
Audit Logs
```

Note: **Pharmacy has no Inventory Items screen** — the group has four sub-items, not five.

**37 platform endpoints** exist, all `@PreAuthorize("hasRole('SUPER_ADMIN')")` except
`POST /platform/login`.

### ⛔ Not available to Super Admin — do not write cases for these

| Thing                                | Status                                                                                            |
| ------------------------------------ | ------------------------------------------------------------------------------------------------- |
| **Tenant impersonation / switching** | **Does not exist.** Out of scope per product decision.                                            |
| **Platform Users screen**            | `GET /platform/users` exists; **no UI calls it**. `BACKEND_WITHOUT_UI`.                           |
| **Editing an existing FAQ**          | `PUT /platform/faqs/{id}` exists; **no UI calls it**. Create + delete only. `BACKEND_WITHOUT_UI`. |
| **Reading tenant clinical data**     | By design the Super Admin cannot see patients, OPD, bills. Verified by `TC-API-003`.              |

---

## A. LOGIN & SHELL

### TC-SA-001 — Super Admin login

| Tenant   | Role        | Module         | Priority     | Screen            |
| -------- | ----------- | -------------- | ------------ | ----------------- |
| PLATFORM | SUPER_ADMIN | Authentication | **Critical** | `/platform/login` |

**Preconditions:** `setup/setup-super-admin.sql` has been run.
**Navigation:** `http://localhost:5173/platform/login`

**Steps**

1. Enter the seeded Super Admin email and password. Click Login.

**Expected**

- Redirect to `/platform/dashboard`.
- The sidebar shows Dashboard, Hospital, Clinic, Pharmacy, Medicines, Audit Logs.
- No tenant clinical menus (no Patients, OPD, Billing).

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-002 — Super Admin logout

| Tenant   | Role        | Module         | Priority |
| -------- | ----------- | -------------- | -------- |
| PLATFORM | SUPER_ADMIN | Authentication | **High** |

**Steps** 1. Log out. 2. Press Back. 3. Navigate directly to `/platform/dashboard`.

**Expected** — token cleared; Back does not restore the dashboard; direct URL redirects to `/platform/login`.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-003 — Sidebar groups expand and collapse

| Tenant   | Role        | Module          | Priority |
| -------- | ----------- | --------------- | -------- |
| PLATFORM | SUPER_ADMIN | Navigation / UI | **Low**  |

**Steps** 1. Click **Hospital** to expand. 2. Click a sub-item. 3. Collapse. 4. Repeat for Clinic and Pharmacy.

**Expected**

- Each group expands to its sub-items; the active sub-item is highlighted; the page title matches the selection.
- Pharmacy shows **four** sub-items (Pharmacies, Plans, Tickets, FAQs) — **no Inventory Items**. This is correct.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-004 — Dashboard statistics

| Tenant   | Role        | Module    | Priority   | Endpoint                        |
| -------- | ----------- | --------- | ---------- | ------------------------------- |
| PLATFORM | SUPER_ADMIN | Dashboard | **Medium** | `GET /platform/hospitals/stats` |

**Steps** 1. Open **Dashboard**. 2. Record every number shown. 3. Create one new tenant. 4. Return to Dashboard and refresh.

**Expected**

- Counts reflect the nine QA tenants; after step 3 the relevant count increases by exactly 1.
- Figures are tenant **counts and statuses only** — never patient counts or clinical figures.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-005 — Dashboard loading and error states

| Tenant   | Role        | Module         | Priority   |
| -------- | ----------- | -------------- | ---------- |
| PLATFORM | SUPER_ADMIN | Dashboard / UI | **Medium** |

**Steps** 1. Stop the backend. 2. Reload `/platform/dashboard`. 3. Restart the backend and retry.

**Expected** — a loading indicator, then a readable error message. No blank white screen, no raw error object, no React crash. Retry recovers without a manual page reload where a retry control exists.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

## B. PLANS (do these before tenant creation — a tenant needs a plan)

### TC-SA-006 — Plan list loads with server-driven capabilities

| Tenant   | Role        | Module | Priority | Endpoint                                                  |
| -------- | ----------- | ------ | -------- | --------------------------------------------------------- |
| PLATFORM | SUPER_ADMIN | Plans  | **High** | `GET /platform/plans`, `GET /platform/plans/capabilities` |

**Navigation:** Hospital ▸ **Plans**

**Steps** 1. Open the screen. 2. In DevTools → Network confirm **both** calls fire. 3. Click **Create Plan** and inspect the module checkbox list.

**Expected**

- The module list is populated from `/platform/plans/capabilities`, **not** hard-coded.
- If the capabilities call fails the UI shows _"Plan capabilities are unavailable"_ and the **Create** button is disabled — it must not silently offer an empty list.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-007 — Create a HOSPITAL plan with all modules

| Tenant   | Role        | Module | Priority     | Endpoint               |
| -------- | ----------- | ------ | ------------ | ---------------------- |
| PLATFORM | SUPER_ADMIN | Plans  | **Critical** | `POST /platform/plans` |

**Steps**

1. Hospital ▸ Plans → **Create Plan**.
2. Name `QA-HOSP-FULL`; set a price and billing period.
3. Tick **every** module offered.
4. Save.

**Expected**

- Plan appears in the list with all modules listed.
- The offered set is exactly: OPD, IPD, APPOINTMENTS, BILLING, PHARMACY, MEDICAL_INVENTORY, HOSPITAL_INVENTORY, REPORTS, OT, NURSING, ICU.
- Internal keys (`CORE`, `WARDS`, `BEDS`, `CLINICAL_RECORDS`, `PHARMACY_BRANCH`, `IN_CLINIC`) are **not** offered — they are granted automatically.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-008 — Create a minimal HOSPITAL plan (OPD only)

| Tenant   | Role        | Module | Priority |
| -------- | ----------- | ------ | -------- |
| PLATFORM | SUPER_ADMIN | Plans  | **High** |

**Steps** Create `QA-HOSP-MINIMAL` with **only OPD** ticked.

**Expected** — saved with a single module. This plan drives every module-gating negative test in `cross-tenant/PLAN-MODULE-RESTRICTIONS.md`.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-009 — ⭐ A CLINIC plan cannot offer hospital-only modules

| Tenant   | Role        | Module              | Priority     |
| -------- | ----------- | ------------------- | ------------ |
| PLATFORM | SUPER_ADMIN | Plans / Entitlement | **Critical** |

**Steps**

1. Clinic ▸ Plans → **Create Plan**.
2. Read the module checkbox list carefully and write down every option.

**Expected**

- Offered: OPD, PHARMACY, BILLING, APPOINTMENTS, MEDICAL_INVENTORY, REPORTS.
- **NOT offered: IPD, OT, NURSING, ICU, HOSPITAL_INVENTORY.**
- If **IPD** is offered on a clinic plan, raise a **Critical** bug — IPD is not supported for Clinic.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-010 — A PHARMACY plan offers only pharmacy tiers

| Tenant   | Role        | Module              | Priority |
| -------- | ----------- | ------------------- | -------- |
| PLATFORM | SUPER_ADMIN | Plans / Entitlement | **High** |

**Steps** Pharmacy ▸ Plans → Create Plan. Record the options.

**Expected**

- Offered: the PHARMACY base module plus the three tiers — **Single Pharmacist Admin**, **Single Pharmacy**, **Multi Pharmacy**.
- **NOT offered:** OPD, IPD, APPOINTMENTS, BILLING, OT, NURSING, ICU, REPORTS, inventory modules.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-011 — Create the three pharmacy tier plans

| Tenant   | Role        | Module | Priority |
| -------- | ----------- | ------ | -------- |
| PLATFORM | SUPER_ADMIN | Plans  | **High** |

**Steps** Create `QA-PHARM-SOLO` (SINGLE_PHARMACIST_ADMIN), `QA-PHARM-SINGLE` (SINGLE_PHARMACY), `QA-PHARM-MULTI` (MULTI_PHARMACY).

**Expected** — all three save. Each shows its tier. `QA-PHARM-MULTI` implies branch support (`PHARMACY_BRANCH`) without it being a tickable option.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-012 — Plan validation: required fields

| Tenant   | Role        | Module             | Priority   |
| -------- | ----------- | ------------------ | ---------- |
| PLATFORM | SUPER_ADMIN | Plans / Validation | **Medium** |

**Steps** 1. Create Plan → Save with everything blank. 2. Name only, no modules. 3. Negative price. 4. A 300-character name.

**Expected** — each rejected with a clear field-level message. No 500. No plan created (verify by reloading the list).

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-013 — Edit an existing plan

| Tenant   | Role        | Module | Priority | Endpoint                         |
| -------- | ----------- | ------ | -------- | -------------------------------- |
| PLATFORM | SUPER_ADMIN | Plans  | **High** | `PUT /platform/plans/{publicId}` |

**Steps** 1. Edit `QA-HOSP-MINIMAL`, add **BILLING**. 2. Save. 3. Reload the page.

**Expected** — change persists. **Now check downstream:** see `TC-SA-014`.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-014 — ⭐ Downstream: editing a plan changes what its tenants can do

| Tenant                | Role                         | Module              | Priority     |
| --------------------- | ---------------------------- | ------------------- | ------------ |
| PLATFORM → HOSPITAL_M | SUPER_ADMIN → HOSPITAL_ADMIN | Plans / Entitlement | **Critical** |

**Preconditions:** `QA Hospital M` is on `QA-HOSP-MINIMAL`.

**Steps**

1. **Tab 2:** log in as `admin.hospm@qa.test`. Record which sidebar tabs are visible. Leave logged in.
2. **Tab 1 (Super Admin):** edit `QA-HOSP-MINIMAL` to add **BILLING**. Save.
3. **Tab 2:** without logging out, refresh the page.
4. **Tab 2:** open Billing and confirm it works.
5. **Tab 1:** remove BILLING again. **Tab 2:** refresh.

**Expected**

- Step 1: no Billing tab.
- Step 3: **Billing tab now appears without re-login.** Module checks read the live hospital row, not the JWT.
- Step 5: the Billing tab disappears again, and any billing API call now returns **403**.
- **No data is destroyed** by removing the module — re-adding it restores access to the same records.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-015 — Delete a plan that no tenant uses

| Tenant   | Role        | Module | Priority   | Endpoint                            |
| -------- | ----------- | ------ | ---------- | ----------------------------------- |
| PLATFORM | SUPER_ADMIN | Plans  | **Medium** | `DELETE /platform/plans/{publicId}` |

**Steps** 1. Create a throwaway plan `QA-DELETE-ME`. 2. Delete it. 3. Confirm the dialog. 4. Reload.

**Expected** — removed from the list and does not return after reload.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-016 — ⭐ Deleting a plan that has active subscribers is refused

| Tenant   | Role        | Module | Priority     |
| -------- | ----------- | ------ | ------------ |
| PLATFORM | SUPER_ADMIN | Plans  | **Critical** |

**Steps** 1. Attempt to delete `QA-HOSP-FULL` (used by Hospital A and B). 2. Confirm. 3. Log in as `admin.hospa@qa.test`.

**Expected**

- Deletion is **refused** with a clear message naming the reason (active subscribers).
- Hospital A and B keep working, keep their modules, and lose no data.
- If the plan **is** deleted and tenants break, that is a **Critical** data-integrity bug.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-017 — Plan list is not visible to any tenant role (API)

| Tenant     | Role           | Module                | Priority | Endpoint          | Method | Auth           | Expected status |
| ---------- | -------------- | --------------------- | -------- | ----------------- | ------ | -------------- | --------------- |
| HOSPITAL_A | HOSPITAL_ADMIN | Plans / Authorization | **High** | `/platform/plans` | GET    | hospital admin | **403**         |

**Steps** `GET /platform/plans` with a tenant admin token (see `05` §6).

**Expected** — **403**; the pricing catalogue is not disclosed to tenants.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

## C. TENANT CREATION

### TC-SA-018 — Create a HOSPITAL tenant (happy path)

| Tenant   | Role        | Module            | Priority     | Endpoint                   |
| -------- | ----------- | ----------------- | ------------ | -------------------------- |
| PLATFORM | SUPER_ADMIN | Tenant onboarding | **Critical** | `POST /platform/hospitals` |

**Navigation:** Hospital ▸ Hospitals → **Create**

**Steps**

1. Hospital name `QA Hospital A`.
2. Admin name `Anita Deshpande`, admin email `admin.hospa@qa.test`, password `QaPass#2026` (confirm it).
3. Type **HOSPITAL**. Plan **QA-HOSP-FULL**. Billing period **MONTHLY**. Single doctor **off**.
4. Save.
5. Record the tenant's numeric **id** and **customId** from the list.

**Expected**

- Tenant appears in the Hospitals list, status **Active**, plan shown.
- The admin account is created and can immediately log in at `/login/hospital` (verify).
- **Information disclosure:** the created-tenant response must not echo the password.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-019 — Create a CLINIC tenant

| Tenant   | Role        | Module            | Priority     |
| -------- | ----------- | ----------------- | ------------ |
| PLATFORM | SUPER_ADMIN | Tenant onboarding | **Critical** |

**Steps** Clinic ▸ Clinics → Create `QA Clinic A`, admin `admin.clina@qa.test`, type **CLINIC**, plan **QA-CLINIC-FULL**.

**Expected**

- Appears under **Clinics**, not under Hospitals.
- Logging in at `/login/clinic` works and lands on `/hospital/admin` (see `TC-AUTH-003`).
- Sidebar shows **no** IPD, Wards & Beds, OT, Nursing or ICU tabs.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-020 — Create a PHARMACY tenant

| Tenant   | Role        | Module            | Priority     |
| -------- | ----------- | ----------------- | ------------ |
| PLATFORM | SUPER_ADMIN | Tenant onboarding | **Critical** |

**Steps** Pharmacy ▸ Pharmacies → Create `QA Pharmacy A`, admin `admin.pharma@qa.test`, type **PHARMACY**, plan **QA-PHARM-SINGLE**.

**Expected**

- Appears under **Pharmacies**.
- Admin logs in at `/login/pharmacy` and lands on **`/pharmacy/admin`**.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-021 — Create a tenant with `isSingleDoctor` enabled

| Tenant   | Role        | Module                            | Priority |
| -------- | ----------- | --------------------------------- | -------- |
| PLATFORM | SUPER_ADMIN | Tenant onboarding / Special modes | **High** |

**Steps** Create `QA Clinic Solo` (CLINIC, `QA-CLINIC-FULL`) with **Single Doctor = ON**, admin `admin.clinsolo@qa.test`.

**Expected**

- Created. On login the admin lands on **`/hospital/doctor`** (not `/hospital/admin`) and can switch between the doctor and admin dashboards.
- Full behaviour is covered by `cross-tenant/SPECIAL-MODES.md` (`TC-MODE-001`…).

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-022 — Tenant creation: required-field validation

| Tenant   | Role        | Module                         | Priority |
| -------- | ----------- | ------------------------------ | -------- |
| PLATFORM | SUPER_ADMIN | Tenant onboarding / Validation | **High** |

**Steps** — attempt to save with each of these, one at a time:

1. All fields blank.
2. Hospital name blank.
3. Admin email blank.
4. Admin name blank.
5. Password blank.
6. **No plan selected.**
7. No billing period.

**Expected** — each rejected with a specific message. **No tenant is created** (reload the list to confirm). Case 6 matters most: `planPublicId` is `@NotBlank` server-side — a tenant without a plan must be impossible.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-023 — Tenant creation: invalid input

| Tenant   | Role        | Module                         | Priority   |
| -------- | ----------- | ------------------------------ | ---------- |
| PLATFORM | SUPER_ADMIN | Tenant onboarding / Validation | **Medium** |

**Steps**

1. Admin email `not-an-email`.
2. Password shorter than the stated minimum.
3. Mismatched password confirmation.
4. Hospital name of 200 characters (limit is 150).
5. Hospital name containing an emoji 🏥.
6. Admin name containing digits or symbols.

**Expected** — each rejected with a readable message, never a 500 and never a raw validation object rendered into the page.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-024 — Duplicate admin email is rejected

| Tenant   | Role        | Module            | Priority |
| -------- | ----------- | ----------------- | -------- |
| PLATFORM | SUPER_ADMIN | Tenant onboarding | **High** |

**Steps** 1. Create a second tenant using `admin.hospa@qa.test` (already used by Hospital A). 2. Save.

**Expected** — rejected with a clear message. **QA Hospital A's admin is unaffected** — verify by logging in as `admin.hospa@qa.test` afterwards.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-025 — Cancel tenant creation

| Tenant   | Role        | Module                 | Priority   |
| -------- | ----------- | ---------------------- | ---------- |
| PLATFORM | SUPER_ADMIN | Tenant onboarding / UI | **Medium** |

**Steps** 1. Open Create, fill every field. 2. Click **Cancel** (or the ✕). 3. Reopen Create.

**Expected** — no tenant created; the form is **empty** on reopen (no stale values from the abandoned attempt).

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-026 — Double-click Create does not create two tenants

| Tenant   | Role        | Module            | Priority |
| -------- | ----------- | ----------------- | -------- |
| PLATFORM | SUPER_ADMIN | Tenant onboarding | **High** |

**Steps** 1. Fill the form for `QA Dup Test`. 2. Double-click **Save** rapidly. 3. Reload the list.

**Expected** — exactly **one** `QA Dup Test`. If two appear, raise a **High** bug and delete the extra.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-027 — Tenant type cannot be given a plan of another type

| Tenant   | Role        | Module                          | Priority |
| -------- | ----------- | ------------------------------- | -------- |
| PLATFORM | SUPER_ADMIN | Tenant onboarding / Entitlement | **High** |

**Steps** 1. Start creating a **CLINIC**. 2. Open the plan dropdown.

**Expected** — only CLINIC plans are offered. A hospital or pharmacy plan must not be selectable for a clinic. If it is, note it and attempt to save; record whether the server rejects it.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

## D. TENANT LIST, DETAIL & EDITING

### TC-SA-028 — Tenant list displays and is scoped to its type

| Tenant   | Role        | Module            | Priority | Endpoint                  |
| -------- | ----------- | ----------------- | -------- | ------------------------- |
| PLATFORM | SUPER_ADMIN | Tenant management | **High** | `GET /platform/hospitals` |

**Steps** 1. Open Hospital ▸ Hospitals. 2. Open Clinic ▸ Clinics. 3. Open Pharmacy ▸ Pharmacies.

**Expected**

- Hospitals lists only HOSPITAL tenants; Clinics only CLINIC; Pharmacies only PHARMACY.
- Each row shows name, type, plan, status and creation date.
- **A clinic never appears in the Hospitals list.**

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-029 — Tenant list search

| Tenant   | Role        | Module                 | Priority   |
| -------- | ----------- | ---------------------- | ---------- |
| PLATFORM | SUPER_ADMIN | Tenant management / UI | **Medium** |

**Steps** 1. Search `QA Hospital A` → exact hit. 2. Search `qa hospital` (lowercase, partial) → both A and B. 3. Search `zzzz` → empty state. 4. Clear the search.

**Expected** — case-insensitive partial match; a readable empty state (not a blank table); clearing restores the full list.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-030 — Tenant list sorting and pagination

| Tenant   | Role        | Module                 | Priority |
| -------- | ----------- | ---------------------- | -------- |
| PLATFORM | SUPER_ADMIN | Tenant management / UI | **Low**  |

**Steps** 1. Click each sortable column header (asc then desc). 2. If pagination exists, page forward and back. 3. Note whether a search survives a page change.

**Expected** — sorting is stable and correct; paging does not duplicate or skip rows; record the search-plus-paging behaviour as observed.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-031 — Open a tenant's detail view

| Tenant   | Role        | Module            | Priority | Endpoint                       |
| -------- | ----------- | ----------------- | -------- | ------------------------------ |
| PLATFORM | SUPER_ADMIN | Tenant management | **High** | `GET /platform/hospitals/{id}` |

**Steps** 1. Click `QA Hospital A`. 2. Record every field shown.

**Expected**

- Shows tenant name, type, status, plan, billing period, assigned date, admin contact, user counts.
- **Must not show patient names, clinical records, or bills.** The platform owner does not see tenant clinical data.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-032 — Edit tenant details

| Tenant   | Role        | Module            | Priority | Endpoint                               |
| -------- | ----------- | ----------------- | -------- | -------------------------------------- |
| PLATFORM | SUPER_ADMIN | Tenant management | **High** | `PUT /platform/hospitals/{id}/details` |

**Steps** 1. Edit `QA Hospital A`, change the name to `QA Hospital A Renamed`. 2. Save. 3. Reload. 4. Log in as `admin.hospa@qa.test` and check the header. 5. Rename it back.

**Expected** — change persists, and the **tenant's own header reflects the new name**. No data is lost by a rename.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-033 — Edit validation

| Tenant   | Role        | Module                         | Priority   |
| -------- | ----------- | ------------------------------ | ---------- |
| PLATFORM | SUPER_ADMIN | Tenant management / Validation | **Medium** |

**Steps** 1. Blank the tenant name and save. 2. A 200-character name. 3. An emoji in the name.

**Expected** — rejected with field-level messages; the existing record is unchanged (reload to confirm).

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-034 — ⭐ Deactivate a tenant

| Tenant                | Role        | Module           | Priority     | Endpoint                              |
| --------------------- | ----------- | ---------------- | ------------ | ------------------------------------- |
| PLATFORM → HOSPITAL_B | SUPER_ADMIN | Tenant lifecycle | **Critical** | `PUT /platform/hospitals/{id}/status` |

**Steps**

1. **Tab 2:** log in as `rec.hospb@qa.test`, open Patients, confirm P6 is visible. Leave it open.
2. **Tab 1:** set `QA Hospital B` to **Inactive**.
3. **Tab 2:** click another tab to force an API call.
4. **Tab 3:** attempt a fresh login as `admin.hospb@qa.test`.
5. **Tab 1:** confirm the status badge reads Inactive.

**Expected**

- Step 3: the live session is blocked (401/403 → login). Record exactly what the tester sees.
- Step 4: login **refused**.
- **All HOSPITAL_B data still exists** — proven by `TC-SA-035`.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-035 — ⭐ Reactivate a tenant — data must be intact

| Tenant                | Role        | Module           | Priority     |
| --------------------- | ----------- | ---------------- | ------------ |
| PLATFORM → HOSPITAL_B | SUPER_ADMIN | Tenant lifecycle | **Critical** |

**Preconditions:** `TC-SA-034` left Hospital B inactive.

**Steps** 1. Set `QA Hospital B` back to **Active**. 2. Log in as `rec.hospb@qa.test`. 3. Open Patients, Appointments, OPD.

**Expected**

- Login succeeds.
- **Every record created before deactivation is present and unchanged** — P6, the appointment, the OPD case.
- Deactivation is reversible and non-destructive. Any data loss here is **Critical**.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-036 — Reset a tenant admin's password

| Tenant                | Role        | Module            | Priority     | Endpoint                                       |
| --------------------- | ----------- | ----------------- | ------------ | ---------------------------------------------- |
| PLATFORM → HOSPITAL_B | SUPER_ADMIN | Tenant management | **Critical** | `POST /platform/hospitals/{id}/reset-password` |

**Steps**

1. **Tab 2:** log in as `admin.hospb@qa.test` and leave the session open.
2. **Tab 1:** open `QA Hospital B` → **Reset Password** → set `QaPass#2026New`.
3. **Tab 2:** click a tab to force a request.
4. Log in with the **old** password, then with the **new** one.

**Expected**

- Step 3: the live session is **revoked** (401 → login) — see `TC-AUTH-019`; `tokenVersion` is bumped.
- Old password fails; new password works.
- The new password is **not** displayed anywhere in the platform UI after the dialog closes, and never appears in a list.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-037 — Reset a non-admin tenant user's password

| Tenant                | Role        | Module            | Priority | Endpoint                                   |
| --------------------- | ----------- | ----------------- | -------- | ------------------------------------------ |
| PLATFORM → HOSPITAL_A | SUPER_ADMIN | Tenant management | **High** | `POST /platform/users/{id}/reset-password` |

**Steps** 1. From the tenant detail view, locate the user list. 2. Reset `rec.hospa@qa.test`'s password. 3. Verify old fails / new works.

**Expected** — as `TC-SA-036`. **If the platform UI offers no way to see or select individual tenant users**, record this case as **N/A** and note it: `GET /platform/users` exists in the backend with **no UI caller** (`BACKEND_WITHOUT_UI`).

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-038 — Delete a tenant

| Tenant   | Role        | Module           | Priority     | Endpoint                          |
| -------- | ----------- | ---------------- | ------------ | --------------------------------- |
| PLATFORM | SUPER_ADMIN | Tenant lifecycle | **Critical** | `DELETE /platform/hospitals/{id}` |

> ⚠️ Use a **throwaway** tenant. Never run this against Hospital A or B — later tranches depend on them.

**Steps**

1. Create `QA Delete Me` (HOSPITAL, QA-HOSP-MINIMAL, admin `admin.delete@qa.test`).
2. Log in as its admin once to confirm it works; create one patient inside it.
3. As Super Admin, **Delete** the tenant. Confirm the dialog.
4. Attempt to log in as `admin.delete@qa.test`.
5. Reload the Hospitals list.

**Expected**

- A confirmation step is required — deletion must never be one click.
- After deletion the tenant is gone from the list and its admin cannot log in.
- Record whether deletion is **hard** or **soft** (does the row survive in the database?) — this determines whether the admin email can be reused. Mark `NEEDS_PRODUCT_CONFIRMATION` if unclear.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

## E. PLAN ASSIGNMENT & MODULE CONFIGURATION (the highest-value platform behaviour)

### TC-SA-039 — Assign a different plan to an existing tenant

| Tenant                | Role        | Module              | Priority     | Endpoint                                 |
| --------------------- | ----------- | ------------------- | ------------ | ---------------------------------------- |
| PLATFORM → HOSPITAL_M | SUPER_ADMIN | Plans / Entitlement | **Critical** | `POST /platform/plans/{publicId}/assign` |

**Steps** 1. Open `QA Hospital M` (on `QA-HOSP-MINIMAL`). 2. Assign `QA-HOSP-FULL`. 3. Reload the detail view.

**Expected** — the plan and its module list update on the tenant record.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-040 — ⭐⭐ Downstream: upgrading a plan grants modules without re-login

| Tenant                | Role                         | Module      | Priority     |
| --------------------- | ---------------------------- | ----------- | ------------ |
| PLATFORM → HOSPITAL_M | SUPER_ADMIN → HOSPITAL_ADMIN | Entitlement | **Critical** |

**Steps**

1. **Tab 2:** log in as `admin.hospm@qa.test`. Write down every visible sidebar tab. Confirm there is **no** IPD, Wards & Beds, OT, Nursing, ICU, Billing.
2. **Tab 1:** assign `QA-HOSP-FULL` to QA Hospital M.
3. **Tab 2:** **without logging out**, refresh (F5).
4. Open IPD, Wards & Beds, OT, ICU in turn.
5. In DevTools → Network confirm each returns 200, not 403.

**Expected**

- Step 3: all the new tabs appear.
- Step 4: each screen loads.
- **No re-login was needed** — `ModuleAccessAspect` reads the live hospital row rather than the JWT claim.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-041 — ⭐⭐ Downstream: revoking a module denies UI **and** API

| Tenant                | Role                         | Module      | Priority     |
| --------------------- | ---------------------------- | ----------- | ------------ |
| PLATFORM → HOSPITAL_M | SUPER_ADMIN → HOSPITAL_ADMIN | Entitlement | **Critical** |

**This is the flagship module-revocation case. Execute it exactly.**

**Preconditions:** QA Hospital M is on `QA-HOSP-FULL`. Create one ward and one bed in it so there is data to preserve.

**Steps**

1. **Tab 2:** as `admin.hospm@qa.test`, open **Wards & Beds**. Confirm the ward and bed exist. In DevTools → Network, capture the exact GET request for the ward list (**Copy as cURL**), and note the token.
2. **Tab 1:** assign `QA-HOSP-MINIMAL` back to QA Hospital M (removing IPD and everything else).
3. **Tab 2:** **do not log out.** Refresh.
4. Look at the sidebar.
5. Replay the captured ward-list cURL with the **same still-valid token**.
6. **Tab 1:** re-assign `QA-HOSP-FULL`.
7. **Tab 2:** refresh and reopen Wards & Beds.

**Expected**

| Step | Expected                                                                                                                     |
| ---- | ---------------------------------------------------------------------------------------------------------------------------- |
| 4    | IPD, Wards & Beds, OT, Nursing, ICU, Billing tabs are **gone**                                                               |
| 5    | the API returns **403** — ⭐ _the still-valid token must not be enough_. A 200 here is a **Critical** entitlement-bypass bug |
| 7    | tabs return **and the original ward and bed are still there, unchanged**                                                     |

**Expected information disclosure at step 5:** none — no ward names, no bed data in the 403 body.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-042 — Downstream: changing a pharmacy tier changes the dual-role landing

| Tenant              | Role        | Module                      | Priority |
| ------------------- | ----------- | --------------------------- | -------- |
| PLATFORM → PHARMACY | SUPER_ADMIN | Entitlement / Special modes | **High** |

**Steps** 1. `QA Pharmacy A` is on `QA-PHARM-SINGLE`; log in as its admin and record the landing URL. 2. Assign `QA-PHARM-SOLO` (SINGLE_PHARMACIST_ADMIN). 3. Log out and back in.

**Expected**

- Before: lands on `/pharmacy/admin`; the admin cannot reach `/pharmacy/pharmacy`.
- After: lands on **`/pharmacy/pharmacy`** and can switch between pharmacist and admin dashboards.
- ⚠️ This change affects the **JWT `modules` claim**, so unlike `TC-SA-040` a **re-login is required**. Record whether a refresh alone is enough — if it is not, that is expected, not a bug.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-043 — Module revocation does not delete tenant data

| Tenant                | Role        | Module                       | Priority     |
| --------------------- | ----------- | ---------------------------- | ------------ |
| PLATFORM → HOSPITAL_M | SUPER_ADMIN | Entitlement / Data integrity | **Critical** |

**Steps** 1. In QA Hospital M (full plan) create a ward, a bed, and an OPD case. Record their ids. 2. Downgrade to OPD-only. 3. Query the database directly: `SELECT COUNT(*) FROM wards WHERE hospital_id = <M>;` and the same for `beds`. 4. Restore the full plan and check the UI.

**Expected** — row counts are **unchanged** at step 3. Revocation hides and denies; it never deletes.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

## F. MEDICINES (global catalogue)

### TC-SA-044 — Medicine list loads

| Tenant   | Role        | Module    | Priority   | Endpoint                  |
| -------- | ----------- | --------- | ---------- | ------------------------- |
| PLATFORM | SUPER_ADMIN | Medicines | **Medium** | `GET /platform/medicines` |

**Navigation:** sidebar → **Medicines** (top-level; shared by all three tenant types).

**Steps** Open the screen; confirm the list, search and paging render.

**Expected** — one global catalogue, not per tenant.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-045 — Create a medicine

| Tenant   | Role        | Module    | Priority | Endpoint                   |
| -------- | ----------- | --------- | -------- | -------------------------- |
| PLATFORM | SUPER_ADMIN | Medicines | **High** | `POST /platform/medicines` |

**Steps** Create `QA Platform Medicine 1` with its details. Save. Reload.

**Expected** — appears in the list and persists.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-046 — ⭐ Downstream: a platform medicine is searchable inside a tenant

| Tenant                | Role                     | Module    | Priority |
| --------------------- | ------------------------ | --------- | -------- |
| PLATFORM → HOSPITAL_A | SUPER_ADMIN → PHARMACIST | Medicines | **High** |

**Steps** 1. Create `QA Platform Medicine 1` as above. 2. Log in as `pharm.hospa@qa.test`. 3. In Inventory / medicine search, type `QA Platform Medicine`.

**Expected** — the medicine is findable in the tenant. This is the one deliberately **global** catalogue; it is **not** a tenant-isolation violation.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-047 — Edit and delete a medicine

| Tenant   | Role        | Module    | Priority   | Endpoint                                 |
| -------- | ----------- | --------- | ---------- | ---------------------------------------- |
| PLATFORM | SUPER_ADMIN | Medicines | **Medium** | `PUT`, `DELETE /platform/medicines/{id}` |

**Steps** 1. Edit `QA Platform Medicine 1`'s name. Save, reload. 2. Delete it. Confirm. Reload. 3. Check whether a tenant that already stocked it still has its inventory row.

**Expected** — edit persists; delete removes it from the catalogue; **existing tenant stock is not destroyed** (record the actual behaviour; mark `NEEDS_PRODUCT_CONFIRMATION` if stock disappears).

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-048 — Medicine CSV import

| Tenant   | Role        | Module    | Priority | Endpoint                              |
| -------- | ----------- | --------- | -------- | ------------------------------------- |
| PLATFORM | SUPER_ADMIN | Medicines | **High** | `POST /platform/medicines/import-csv` |

**Steps**

1. Prepare a **synthetic** CSV with 3 valid rows.
2. Import it. Verify all 3 appear.
3. Import a CSV with 1 valid and 1 malformed row.
4. Import a non-CSV file (e.g. a `.png` renamed to `.csv`).
5. Import an empty file.

**Expected**

- Step 2: 3 medicines created; a success summary is shown.
- Step 3: a clear report of which row failed and why. **Record whether valid rows are kept or the whole import is rolled back** — either is a legitimate design, but it must be stated to the user, not silent.
- Steps 4–5: rejected with a readable message, never a 500.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

## G. INVENTORY MASTER

### TC-SA-049 — Inventory Items list (Hospital and Clinic groups only)

| Tenant   | Role        | Module           | Priority   | Endpoint                         |
| -------- | ----------- | ---------------- | ---------- | -------------------------------- |
| PLATFORM | SUPER_ADMIN | Inventory master | **Medium** | `GET /platform/inventory-master` |

**Steps** 1. Hospital ▸ Inventory Items. 2. Clinic ▸ Inventory Items. 3. Confirm Pharmacy ▸ has **no** Inventory Items sub-item.

**Expected** — the screen exists under Hospital and Clinic; its absence under Pharmacy is correct, not a bug.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-050 — Create, edit and delete an inventory master item

| Tenant   | Role        | Module           | Priority   | Endpoint                                                |
| -------- | ----------- | ---------------- | ---------- | ------------------------------------------------------- |
| PLATFORM | SUPER_ADMIN | Inventory master | **Medium** | `POST`, `PUT`, `DELETE /platform/inventory-master/{id}` |

**Steps** 1. Create `QA Master Item 1`. 2. Edit its name. 3. Delete it. Each time reload to confirm persistence.

**Expected** — all three operate and persist; delete asks for confirmation.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-051 — Inventory master CSV import

| Tenant   | Role        | Module           | Priority   | Endpoint                                     |
| -------- | ----------- | ---------------- | ---------- | -------------------------------------------- |
| PLATFORM | SUPER_ADMIN | Inventory master | **Medium** | `POST /platform/inventory-master/import-csv` |

**Steps** As `TC-SA-048`, with a synthetic inventory CSV: valid file, partially invalid file, wrong file type, empty file.

**Expected** — same standards: clear per-row reporting, no 500, no silent partial success.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-052 — Downstream: a master item reaches the tenant's hospital inventory

| Tenant                | Role                         | Module           | Priority   |
| --------------------- | ---------------------------- | ---------------- | ---------- |
| PLATFORM → HOSPITAL_A | SUPER_ADMIN → HOSPITAL_ADMIN | Inventory master | **Medium** |

**Preconditions:** HOSPITAL_A's plan includes `HOSPITAL_INVENTORY`.

**Steps** 1. Create `QA Master Item 2`. 2. As `admin.hospa@qa.test` open **Settings → Hospital Inventory** (or the Hospital Inventory tab). 3. Search for it.

**Expected** — the platform-defined item is available to the tenant for stocking.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

## H. TICKETS

### TC-SA-053 — Ticket list per tenant type

| Tenant   | Role        | Module  | Priority   | Endpoint                |
| -------- | ----------- | ------- | ---------- | ----------------------- |
| PLATFORM | SUPER_ADMIN | Support | **Medium** | `GET /platform/tickets` |

**Preconditions:** raise one ticket from inside HOSPITAL_A (admin → Support) and one from CLINIC_A.

**Steps** 1. Hospital ▸ Tickets. 2. Clinic ▸ Tickets. 3. Pharmacy ▸ Tickets.

**Expected** — each list shows tickets from that tenant type only. The hospital ticket does not appear under Clinic.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-054 — Open a ticket and read its detail

| Tenant   | Role        | Module  | Priority   | Endpoint                     |
| -------- | ----------- | ------- | ---------- | ---------------------------- |
| PLATFORM | SUPER_ADMIN | Support | **Medium** | `GET /platform/tickets/{id}` |

**Steps** Open the HOSPITAL_A ticket; record subject, description, raising tenant, status and timestamps.

**Expected** — the raising tenant is identified; status is one of `OPEN`, `IN_PROGRESS`, `RESOLVED` with a colour badge.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-055 — ⭐ Resolve a ticket and check the tenant sees it

| Tenant                | Role                         | Module  | Priority | Endpoint                            |
| --------------------- | ---------------------------- | ------- | -------- | ----------------------------------- |
| PLATFORM → HOSPITAL_A | SUPER_ADMIN → HOSPITAL_ADMIN | Support | **High** | `PUT /platform/tickets/{id}/status` |

**Steps** 1. Set the ticket to **RESOLVED**. 2. Confirm the Resolve control disappears for an already-resolved ticket. 3. Log in as `admin.hospa@qa.test` → **Support**.

**Expected** — the tenant sees the ticket as resolved. A resolved ticket cannot be resolved twice.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-056 — Delete a ticket

| Tenant   | Role        | Module  | Priority | Endpoint                        |
| -------- | ----------- | ------- | -------- | ------------------------------- |
| PLATFORM | SUPER_ADMIN | Support | **Low**  | `DELETE /platform/tickets/{id}` |

**Steps** Delete a resolved ticket; confirm; reload; then check the tenant's Support screen.

**Expected** — removed from both the platform list and the tenant's view, with confirmation required.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

## I. FAQs

### TC-SA-057 — Create an FAQ and see it inside the tenant

| Tenant                | Role                            | Module | Priority   | Endpoint              |
| --------------------- | ------------------------------- | ------ | ---------- | --------------------- |
| PLATFORM → HOSPITAL_A | SUPER_ADMIN → any hospital role | FAQ    | **Medium** | `POST /platform/faqs` |

**Steps** 1. Hospital ▸ FAQs → Add. Question: _"How do I configure my billing settings?"_, with an answer. Save. 2. Log in as `admin.hospa@qa.test` → **Support** / FAQ area. 3. Log in as `admin.clina@qa.test` and look for the same FAQ.

**Expected**

- The FAQ appears for HOSPITAL tenants.
- Record whether it also appears for the **clinic** — FAQs are created per tenant-type group, so a Hospital FAQ should **not** show to a clinic. If it does, raise a Medium bug.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-058 — ⚠️ Editing an FAQ has no UI

| Tenant   | Role        | Module | Priority |
| -------- | ----------- | ------ | -------- |
| PLATFORM | SUPER_ADMIN | FAQ    | **Low**  |

**Steps** 1. Open Hospital ▸ FAQs. 2. Look for an Edit control on an existing FAQ.

**Expected**

- **There is no Edit control.** `PUT /platform/faqs/{id}` exists in the backend but **no frontend code calls it** — classified `BACKEND_WITHOUT_UI`.
- Record the observed UI. **This is a known gap, not a bug to raise** — reference `status/IMPLEMENTATION-STATUS.md`.
- Correcting an FAQ therefore means delete + recreate.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-059 — Delete an FAQ

| Tenant                | Role        | Module | Priority | Endpoint                     |
| --------------------- | ----------- | ------ | -------- | ---------------------------- |
| PLATFORM → HOSPITAL_A | SUPER_ADMIN | FAQ    | **Low**  | `DELETE /platform/faqs/{id}` |

**Steps** Delete the FAQ; confirm; reload; check the tenant's FAQ list.

**Expected** — gone from both.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

## J. AUDIT LOGS

### TC-SA-060 — Platform audit log records platform actions

| Tenant   | Role        | Module | Priority | Endpoint                   |
| -------- | ----------- | ------ | -------- | -------------------------- |
| PLATFORM | SUPER_ADMIN | Audit  | **High** | `GET /platform/audit-logs` |

**Steps**

1. Note the current time.
2. Perform three platform actions: create a tenant, deactivate it, reset its admin password.
3. Open **Audit Logs** and filter/sort to the most recent entries.

**Expected**

- All three actions appear, each with an action name, the acting user, a timestamp and the affected tenant.
- **Expected information disclosure:** the log must **not** contain the new password, a password hash, or a JWT.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-061 — Platform audit log does not expose tenant clinical detail

| Tenant   | Role        | Module          | Priority     |
| -------- | ----------- | --------------- | ------------ |
| PLATFORM | SUPER_ADMIN | Audit / Privacy | **Critical** |

**Steps** 1. As HOSPITAL_A reception, register a patient and complete an OPD consultation. 2. As Super Admin, open Audit Logs and search for the patient's name and phone number.

**Expected**

- The platform audit log shows **no patient name, phone, diagnosis or prescription**.
- Tenant-level clinical audit belongs to the tenant's own Audit Logs screen, not the platform's.
- Any patient identifier appearing here is a **Critical** privacy finding.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-SA-062 — Audit log filtering, paging and empty state

| Tenant   | Role        | Module     | Priority |
| -------- | ----------- | ---------- | -------- |
| PLATFORM | SUPER_ADMIN | Audit / UI | **Low**  |

**Steps** 1. Apply each available filter (date, action, tenant). 2. Filter to a range with no entries. 3. Page through a large result set.

**Expected** — filters apply correctly; the empty result shows a readable empty state; paging neither duplicates nor skips rows.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______
