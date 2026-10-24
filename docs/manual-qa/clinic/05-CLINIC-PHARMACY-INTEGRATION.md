# 05 — CLINIC PHARMACY INTEGRATION

**Baseline:** `aa143a7` · **Cases:** `TC-CP-001` … `TC-CP-016` · Tenant CLINIC_A · PHARMACY + MEDICAL_INVENTORY modules · `pharm.clina@qa.test` → `/hospital/pharmacy`

A clinic's pharmacy is **the same module as the hospital's pharmacy department** — the same
`PharmacyDashboard` with 13 tabs, reached at `/hospital/pharmacy` (the route admits `PHARMACIST`
and `HOSPITAL_ADMIN` regardless of tenant type). It is **not** the standalone PHARMACY tenant,
which has its own tiers and branch model (`../pharmacy/`).

Shared behaviour is referenced to `06-HOSPITAL-PHARMACY-DEPT.md`; this document covers the clinic
delta and the prescription→sale chain inside a clinic.

---

### TC-CP-001 — Clinic pharmacist landing and tabs

`CLINIC_A · PHARMACIST · Pharmacy · Critical · /hospital/pharmacy`
**Steps:** log in at `/login/clinic`; record URL and tabs; watch the Network namespace.
**Expected:** lands **`/hospital/pharmacy`** (not `/pharmacy/pharmacy` — that is the pharmacy tenant); the 13 tabs (Dashboard, Billing Counter, Billing, Prescriptions, Inventory, Purchase Management, Suppliers, Manufacturers, Returns & Refunds, Expiry Management, Reports & Analytics, Audit Logs, Settings). ERP calls go to `/pharmacy/...` (the ERP controllers have a single namespace) while clinic clinical calls go to `/clinic/...` — record both.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CP-002 — Clinic admin can open the pharmacy dashboard

`CLINIC_A · HOSPITAL_ADMIN · Pharmacy · Medium · /hospital/pharmacy`
**Steps:** = `TC-HP-002` as clinic admin.
**Expected:** allowed; shows the **clinic's** stock and sales only.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CP-003 — Inventory, suppliers, manufacturers, categories

`CLINIC_A · PHARMACIST · Inventory · High · Inventory/Masters`
**Steps:** = `TC-HP-004`, `005`, `006` as clinic: add `QA Clinic Paracetamol` batch `QC-B1` qty 60; a supplier; a manufacturer; a category.
**Expected:** all persist and are clinic-scoped.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CP-004 — Platform medicine catalogue is shared (expected)

`CLINIC_A · PHARMACIST · Inventory · Medium · Inventory`
**Steps:** = `TC-HP-007`: search for a Super-Admin-created medicine.
**Expected:** found — the catalogue is deliberately global; **stock is per tenant**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CP-005 — Purchase → inward → stock

`CLINIC_A · PHARMACIST · Purchase · Critical · Purchase Management`
**Steps:** = `TC-HP-008`/`009` as clinic.
**Expected:** `DRAFT` does not move stock; **Post & Inward** does; posting twice does not double-inward.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CP-006 — ⭐ Clinic prescription appears in Prescriptions

`CLINIC_A · DOCTOR → PHARMACIST · Prescriptions · Critical · Prescriptions`
**Steps:** = `TC-HP-010`: clinic doctor completes a consultation prescribing `QA Clinic Paracetamol`; pharmacist opens **Prescriptions**.
**Expected:** the row shows the clinic patient's name and `PAT` id, the clinic doctor and the date; **See Consultation** opens the clinic OPD case; statuses `ACTIVE`/`Pending`/`UNLINKED` as in hospital.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CP-007 — Dispense against the clinic prescription

`CLINIC_A · PHARMACIST · Prescriptions · Critical · DispenseModal`
**Steps:** = `TC-HP-011`.
**Expected:** sale created against the clinic patient; batch decremented; validation messages (`Select a medicine`, `No dosage recorded`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CP-008 — Billing Counter: Hospital Rx Mode and Walk-in Mode

`CLINIC_A · PHARMACIST · Billing Counter · Critical · Billing Counter`
**Steps:** = `TC-HP-012`/`013` as clinic. Note the mode label reads **Hospital Rx Mode** even in a clinic.
**Expected:** both modes work; stock decrements; invoice shows the patient's `PAT` id (or `Walk-in Customer`) and the **clinic's** header. Record the "Hospital Rx Mode" wording in a clinic as a **LOW** cosmetic observation, not a functional bug.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CP-009 — Stock, expiry and blocked-batch guards

`CLINIC_A · PHARMACIST · Billing Counter · Critical · Billing Counter`
**Steps:** = `TC-HP-014`, `015`, `016`, `026` as clinic.
**Expected:** insufficient stock, no-batch, expired and blocked batches all refused; **stock unchanged** after every refusal.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CP-010 — Double-submit and failure during a clinic sale

`CLINIC_A · PHARMACIST · Billing Counter · Critical · Billing Counter`
**Steps:** = `TC-HP-017`/`018`.
**Expected:** exactly one sale and one decrement; no decrement from a failed attempt.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CP-011 — Returns and refunds

`CLINIC_A · PHARMACIST · Returns · Critical · Returns & Refunds`
**Steps:** = `TC-HP-021`, `022`, `023`, `024` as clinic.
**Expected:** patient refund restores stock; over-refund and duplicate refund refused; supplier return works.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CP-012 — Expiry management

`CLINIC_A · PHARMACIST · Expiry · High · Expiry Management`
**Steps:** = `TC-HP-025`, `026`, `027`, `028` as clinic.
**Expected:** buckets `ACTIVE`/`NEAR`/`CRITICAL`/`EXPIRED`; block/freeze prevents sale; disposal removes sellable stock.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CP-013 — Pharmacy reports, CSV export and audit in a clinic

`CLINIC_A · PHARMACIST · Reports/Audit · High · Reports & Analytics / Audit Logs`
**Steps:** = `TC-HP-029`/`030` as clinic; also `GET /pharmacy/reports/export` (CSV ledger).
**Expected:** totals reconcile with the clinic's own sales; the CSV downloads and contains **clinic rows only**; audit entries carry the actor.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CP-014 — Clinic pharmacist cannot reach clinical or admin areas

`CLINIC_A · PHARMACIST · Authorization · Critical · API`
**Steps:** = `TC-HP-033` against `/clinic/...`: `GET/POST /clinic/opd`, `/clinic/patients`, `/clinic/nurse/vitals`; plus `GET /clinic/wards` (matrix admits `PHA` — record); plus `TC-API-006` settings drift with the pharmacist token.
**Expected:** clinical endpoints **403**; ward result recorded (`NEEDS_PRODUCT_CONFIRMATION`); settings result recorded (drift).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CP-015 — PHARMACY module removed from the clinic plan

`CLINIC_B · HOSPITAL_ADMIN/PHARMACIST · Entitlement · High · sidebar/API`
**Steps:** put CLINIC_B on a plan without PHARMACY; log in as admin (tabs) and as its pharmacist; call `GET /clinic/pharmacy`, `/pharmacy/inventory`, `/pharmacy/sales`.
**Expected (policy):** pharmacy tabs hidden and endpoints 403. **Code:** no `@RequireModule("PHARMACY")` and the `/pharmacy/**` ERP controllers have no module gate → 200 likely → **`IMPLEMENTATION_DRIFT`, HIGH** (`TC-MOD-012`). Record whether a sale can actually be made.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CP-016 — Clinic pharmacy tenant isolation

`CLINIC_A + CLINIC_B + HOSPITAL_A + PHARMACY_A · PHARMACIST · Isolation · Critical · API`
**Steps:** = `TC-ISO-038`…`044` across the four tenants: list sales; fetch a sale, batch, supplier, purchase by id; attempt a refund against another tenant's sale; attempt a sale from another tenant's batch id.
**Expected:** 403/404 throughout; **the other tenant's stock and sales unchanged** — verify afterwards in that tenant. All four tenants use the same `/pharmacy/**` ERP namespace, so tenant scoping is the _only_ separation here; this is the highest-risk isolation surface in the pharmacy module.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
