# 01 — PHARMACY TENANT ADMIN

**Baseline:** `aa143a7` · **Cases:** `TC-PA-001` … `TC-PA-024` · Tenant PHARMACY_A · `admin.pharma@qa.test` at `/login/pharmacy` → `/pharmacy/admin`

A standalone PHARMACY tenant is **not** "a hospital with pharmacy enabled". Its plan carries one
**tier** and nothing else; it has no OPD, IPD, wards, beds, ICU, OT or nursing. The admin
dashboard is the same `HospitalAdminDashboard` component rebuilt for pharmacy
(`HospitalAdminDashboard.jsx:2251`), with a tab list that depends on the tier (`03-PHARMACY-TIERS.md`).

---

## A. ACCESS & SHELL

### TC-PA-001 — Pharmacy admin login and landing

`PHARMACY_A · HOSPITAL_ADMIN · Auth · Critical · /login/pharmacy`
**Steps:** log in; record the URL, header tenant name and tab set; `GET /auth/me`.
**Expected:** lands **`/pharmacy/admin`** (not `/hospital/admin`); header **QA Pharmacy A**; `hospitalType: PHARMACY`; tabs per the tier matrix.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PA-002 — Session expiry returns to `/login/pharmacy`

`PHARMACY_A · any · Auth · Medium · /login/pharmacy`
**Steps:** = `TC-AUTH-025` for the pharmacy portal.
**Expected:** redirect to **`/login/pharmacy`**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PA-003 — Admin tab set has no clinical tabs

`PHARMACY_A · HOSPITAL_ADMIN · UI · Critical · sidebar`
**Steps:** list every tab and group.
**Expected — present:** Overview, Pharmacists (SINGLE tier), Billing, Analytics, Audit Logs, Settings, Support.
**Expected — absent:** Patients, Appointments, OPD, Follow-ups, IPD, Wards & Beds, ICU, Operation Theatre, Doctors, Receptionists, Nurses, Time Slots, Calendar, Fees, Medicine Inventory, Hospital Inventory, Reports & Analytics (the hospital one), the preset tabs, Pathology.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PA-004 — Overview for a pharmacy tenant

`PHARMACY_A · HOSPITAL_ADMIN · Overview · High · Overview`
**Steps:** with some sales and stock present, read every card; hand-count today's sales, sale value, low stock and near-expiry; compare.
**Expected:** pharmacy-shaped cards only — **no patient, OPD, IPD or bed cards**. Figures reconcile. Note the pharmacy Overview takes a different render path (`isPharmacyTenant`) from the hospital Overview.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PA-005 — Admin can open the pharmacist dashboard

`PHARMACY_A · HOSPITAL_ADMIN · Pharmacy · High · /pharmacy/pharmacy`
**Steps:** navigate to `/pharmacy/pharmacy`.
**Expected:** the route admits `PHARMACIST, HOSPITAL_ADMIN`, so it opens on any tier — record whether it does. On SINGLE there is no switcher (`TC-PT-014`), so the only way in is the URL; note that as an observation.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## B. STAFF

### TC-PA-006 — Create pharmacist (SINGLE tier)

`PHARMACY_A · HOSPITAL_ADMIN → PHARMACIST · Staff · Critical · Pharmacists`
**Steps:** = `TC-PT-013`: create `Deepak Jadhav` / `pharm.pharma@qa.test`; log in as him; with his token call `GET /pharmacy/inventory` and `GET /pharmacy/branches`.
**Expected:** created and lands on the 13-tab dashboard; inventory **200**; branches **403** (`hasRole('HOSPITAL_ADMIN')`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PA-007 — Pharmacist validation, duplicates, reset, deactivate

`PHARMACY_A · HOSPITAL_ADMIN · Staff · High · Pharmacists`
**Steps:** blank fields; invalid email; an email already used in **PHARMACY_B** and in **HOSPITAL_A**; reset password (session revoked, `TC-AUTH-019`); deactivate then attempt login.
**Expected:** validation messages; cross-tenant duplicate email refused **without naming the other tenant**; reset revokes; deactivated user cannot log in but their historical sales remain.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PA-008 — ⚠️ Doctor / Receptionist / Nurse creation is not supported

`PHARMACY_A · HOSPITAL_ADMIN · Staff · High · sidebar/API`
**Steps:** 1. Confirm there are **no** Doctors, Receptionists, Nurses or OT Incharge tabs. 2. `POST /pharmacy/doctors`, `/pharmacy/receptionists`, `/pharmacy/nurses`, `/pharmacy/ot-incharges` with the admin token. 3. If any succeeds, log in as the created user and record the landing URL.
**Expected (policy):** DOCTOR and RECEPTIONIST are **not supported operational roles** on a standalone pharmacy; nurse/OT are not aliased at all.
**Code position:** `/pharmacy/doctors` (11 endpoints) and `/pharmacy/receptionists` (6) **are aliased and ungated** → creation may succeed. Record it as **`IMPLEMENTATION_DRIFT`, HIGH**, and note that `LandingRedirect` sends a DOCTOR to **`/hospital/doctor`** regardless of tenant type (`TC-PH-016`). `/pharmacy/nurses` and `/pharmacy/ot-incharges` should be **404**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PA-009 — Branch staff (MULTI tier)

`PHARM_MULTI · HOSPITAL_ADMIN · Branches · Critical · Pharmacies`
**Steps:** = `TC-PT-017`, `019`, `021`.
**Expected:** branch creation makes a branch login; reset revokes; the branch user is scoped to its branch.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## C. ADMIN-LEVEL BILLING · ANALYTICS · AUDIT

### TC-PA-010 — Admin Billing tab

`PHARMACY_A · HOSPITAL_ADMIN · Billing · High · Billing`
**Steps:** after several sales, open the admin Billing tab; search by bill number and date; open one; reprint.
**Expected:** shows the tenant's pharmacy sales with amounts and dates; reprint identical to the pharmacist's invoice; figures match the pharmacist's Billing history (`TC-PS-013`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PA-011 — Admin Analytics tab

`PHARMACY_A · HOSPITAL_ADMIN · Reports · High · Analytics`
**Steps:** hand-count today's sales count, value, top medicines and stock value; open Analytics; apply a date filter; empty range.
**Expected:** every figure reconciles; the empty range shows an empty state, not a blank chart. **"Chart rendered" is not a pass** — the numbers must match.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PA-012 — Admin Audit Logs

`PHARMACY_A · HOSPITAL_ADMIN · Audit · High · Audit Logs`
**Steps:** perform: create medicine, post a purchase, adjust stock, make a sale, process a refund, block a batch, dispose a batch, create a pharmacist, change a setting. Open Audit Logs; filter by action and date; search.
**Expected:** every action recorded with actor, entity and timestamp; no passwords, tokens or full customer phone numbers; non-admin `GET /pharmacy/audit-logs` → **403**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## D. SETTINGS · SUPPORT · SUBSCRIPTION

### TC-PA-013 — Pharmacy Settings card set

`PHARMACY_A · HOSPITAL_ADMIN · Settings · High · Settings`
**Steps:** open Settings; record every card and control shown for a pharmacy tenant.
**Expected:** the pharmacy path renders **Pharmacy Settings** (`isPharmacyTenant` branch) rather than the hospital Operations grid. **Absent:** Vitals, Files & Access, ICU cards, OT cards, Print & Payment's clinical toggles. Record the exact list — do not assume.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PA-014 — Barcode workflow toggle → Billing Counter

`PHARMACY_A · HOSPITAL_ADMIN → PHARMACIST · Settings · Medium · Settings`
**Steps:** toggle barcode OFF (`PUT /pharmacy/settings/barcode`); pharmacist reloads the Billing Counter; toggle back ON.
**Expected:** the scan field hides and returns; manual batch selection always available.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PA-015 — ⚠️ Pharmacy settings endpoints and authorization

`PHARMACY_A · PHARMACIST · Settings · Critical · API`
**Steps:** = `TC-API-006` against the pharmacy aliases: `GET`/`PUT` `/pharmacy/settings/fees`, `/pharmacy/settings/operations`, `/pharmacy/settings/barcode`, `/pharmacy/settings/nurse-login`, `/pharmacy/settings/ot-incharge`, `/pharmacy/subscription` with the **pharmacist** token.
**Expected (intent):** 403. **Code:** `HospitalAuthController` has **no `@PreAuthorize`** → 200 likely → **Critical drift**. Note that `nurse-login` and `ot-incharge` are meaningless for a pharmacy yet still aliased — record whether they can be toggled.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PA-016 — Subscription and profile

`PHARMACY_A · HOSPITAL_ADMIN · Settings · Medium · Settings/Navbar`
**Steps:** `GET /pharmacy/subscription`; have Super Admin change the tier; refresh, then re-login; update the tenant name/logo and print an invoice.
**Expected:** subscription shows the plan and tier; the tier change needs a re-login (`TC-PT-030`); the invoice header reflects the pharmacy's name.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PA-017 — Support tickets and FAQs are pharmacy-typed

`PHARMACY_A · HOSPITAL_ADMIN · Support · Medium · Support`
**Steps:** raise a ticket; Super Admin opens **Pharmacy ▸ Tickets**; create a Pharmacy FAQ and a Hospital FAQ.
**Expected:** the ticket appears under Pharmacy ▸ Tickets only; pharmacy users see pharmacy FAQs only (`TC-SA-053`/`057`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## E. SUPER ADMIN → PHARMACY DOWNSTREAM

### TC-PA-018 — Tenant creation and deactivation

`PLATFORM → PHARMACY_A · SUPER_ADMIN · Tenant lifecycle · Critical · Pharmacies`
**Steps:** = `TC-SA-020`, `TC-SA-034`, `TC-SA-035` for a pharmacy tenant.
**Expected:** created under **Pharmacies**; deactivation blocks every pharmacy user and a live session; reactivation restores access **with all stock, sales and purchases intact**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PA-019 — Platform medicine catalogue reaches the pharmacy

`PLATFORM → PHARMACY_A · SUPER_ADMIN → PHARMACIST · Medicines · High · Inventory`
**Steps:** = `TC-SA-046`: create a platform medicine; search for it in the pharmacy's catalogue (`GET /pharmacy/catalog/search`).
**Expected:** found — the catalogue is deliberately global; **stock remains per tenant** (`TC-PI-004`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PA-020 — Tier change downstream

`PLATFORM → PHARMACY_A · SUPER_ADMIN · Entitlement · High · Plans`
**Steps:** = `TC-PT-030`.
**Expected:** tab set and switcher change only after re-login; no data lost.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## F. NEGATIVE · ISOLATION · UI

### TC-PA-021 — Pharmacy admin cannot reach the platform

`PHARMACY_A · HOSPITAL_ADMIN · Authorization · Critical · API`
**Steps:** = `TC-API-004`: `/platform/hospitals`, `/platform/plans`, `/platform/users`, `/platform/audit-logs`, and `POST /platform/hospitals`.
**Expected:** **403** on all; no tenant list disclosed.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PA-022 — ⚠️ Pharmacy admin attempts clinical domains

`PHARMACY_A · HOSPITAL_ADMIN · Authorization · Critical · API`
**Steps:** = the full battery in `02-PHARMACIST.md` §E / `10-PHARMACY-CROSS-ROLE-WORKFLOWS.md` P8: `/pharmacy/opd`, `/pharmacy/ipd`, `/pharmacy/beds`, `/pharmacy/wards`, `/pharmacy/patients`, `/pharmacy/appointments` (GET + write), and the `/hospital/...` equivalents.
**Expected (policy):** all denied. **Code:** 36 ungated endpoints across opd/ipd/beds/wards → 200 likely → **`IMPLEMENTATION_DRIFT`, HIGH**; a created row raises it to **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PA-023 — Pharmacy tenant isolation (admin surfaces)

`PHARMACY_A + PHARMACY_B · HOSPITAL_ADMIN · Isolation · Critical · API`
**Steps:** with PHARMACY_B's admin token: list PHARMACY_A's pharmacists, branches, sales, purchases and suppliers; fetch each by id; attempt to edit, delete and reset a PHARMACY_A pharmacist's password; attempt to create a branch under PHARMACY_A's tenant id.
**Expected:** **403/404** throughout; PHARMACY_A verified unchanged; its pharmacist can still log in with the original password.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PA-024 — Admin UI states, double-submit, persistence

`PHARMACY_A · HOSPITAL_ADMIN · UI · Medium · all tabs`
**Steps:** empty tenant (no sales/stock) on every tab; slow network; backend down; long medicine/branch names; double-click Save on the pharmacist and branch forms; F5; 375/768px; logout/login and re-check.
**Expected:** EmptyState per tab; readable errors; exactly one row per double-clicked form; F5 returns to Overview (expected); everything persists.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
