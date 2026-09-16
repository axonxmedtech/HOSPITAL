# 10 — PHARMACY CROSS-ROLE WORKFLOWS

**Baseline:** `aa143a7` · **Cases:** `TC-PX-001` … `TC-PX-010`

## Session convention

| Label      | Context          | Account                                        |
| ---------- | ---------------- | ---------------------------------------------- |
| **S-PADM** | Chrome tab 1     | `admin.pharma@qa.test` — PHARMACY_A (SINGLE)   |
| **S-PPHA** | Chrome tab 2     | `pharm.pharma@qa.test` — PHARMACY_A pharmacist |
| **S-SOLO** | Chrome tab 3     | `admin.pharmsolo@qa.test` — SOLO tier          |
| **S-MADM** | Chrome tab 4     | `admin.pharmmulti@qa.test` — MULTI tier        |
| **S-BA**   | Chrome tab 5     | `branchA.pharmmulti@qa.test`                   |
| **S-BB**   | Chrome tab 6     | `branchB.pharmmulti@qa.test`                   |
| **S-SA**   | Incognito window | Super Admin at `/platform/login`               |
| **S-PB**   | Second browser   | `pharm.pharmb@qa.test` — PHARMACY_B            |

All pharmacy logins start at **`/login/pharmacy`**.

---

## P1 — Admin setup → pharmacist → medicine → batch → sale → invoice

### TC-PX-001 — P1: stand up a pharmacy and make the first sale

`PHARMACY_A · ADM + PHA · multi · Critical`
**Steps**

1. **S-SA** create `QA Pharmacy A` on `QA-PHARM-SINGLE` with admin `admin.pharma@qa.test`.
2. **S-PADM** log in → lands `/pharmacy/admin`. Pharmacists → create `Deepak Jadhav` / `pharm.pharma@qa.test`.
3. **S-PPHA** log in → lands `/pharmacy/pharmacy` with 13 tabs.
4. **S-PPHA** Manufacturers → create one. Suppliers → create `QA Pharm Supplier One`.
5. **S-PPHA** Inventory → create medicine `QA Pharm Paracetamol 500`; batch `QP-B1` qty **100**, expiry +24 months. Record the batch id.
6. **S-PPHA** Billing Counter → **Walk-in Mode** → select `QP-B1` → qty **5** → `CASH` → Complete → **print the invoice**.
7. **S-PPHA** Inventory → **95**. Batch ledger shows one outward row.
8. **S-PPHA** Dashboard and Billing → the sale appears with its value.
9. **S-PADM** Billing and Analytics tabs → the same figures.
10. **S-PADM** Audit Logs → medicine creation, supplier creation and the sale are all recorded.
    **Expected:** one pharmacist, one medicine, one batch, **one** sale, **one** decrement; the invoice carries the **pharmacy's** name and bill number; admin and pharmacist views agree exactly.
    **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## P2 — Supplier → purchase → stock up → sale → stock down

### TC-PX-002 — P2: the procurement-to-sale chain

`PHARMACY_A · PHA · Purchase/Sales · Critical`
**Steps**

1. **S-PPHA** record `QP-B1` = 95.
2. Purchase Management → new purchase from `QA Pharm Supplier One`: batch `QP-B4` qty **50**, expiry +18 months → save as **DRAFT**.
3. Inventory → **unchanged** (95; `QP-B4` not sellable).
4. **Post & Inward** → Inventory shows `QP-B4` = **50**; ledger has one inward row.
5. Billing Counter → sell **8** from `QP-B4` → **42**.
6. Mark the purchase **PAID**.
7. Reports → purchase value includes the posted purchase; sales include the 8 units.
   **Expected:** stock moves **only** on posting, never on draft; the sale decrements the chosen batch only; purchase and sales reports both reconcile.
   **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PX-003 — P2 negative: idempotent posting and validation

`PHARMACY_A · PHA · Purchase · Critical`
**Steps:** = `TC-PP-010` and `TC-PP-011` in sequence: double-click **Post & Inward**; re-post via API; then attempt an invalid purchase (negative qty, past expiry, no supplier).
**Expected:** stock rises **exactly once**; invalid purchases create nothing and move no stock.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## P3 — Sale → partial return → refund → inventory impact

### TC-PX-004 — P3: the refund chain

`PHARMACY_A · PHA · Returns · Critical`
**Steps**

1. **S-PPHA** sell **6** units of `QP-B1` (95 → 89). Note the bill number.
2. Returns & Refunds ▸ `PATIENT` → **Search Bill** → refund **2** → **Process Patient Refund**.
3. Inventory → **91**. Ledger shows the return row.
4. Dashboard → net sales for the day fall by the refunded value.
5. Refund the remaining **4** → Inventory **95**; that sale now contributes **zero** to the day's revenue.
6. Attempt one more unit → refused (`TC-PRF-003`).
7. `GET /pharmacy/inventory/returns-history` → both refunds listed.
8. **S-PADM** Analytics and Audit Logs → both refunds visible and attributed.
   **Expected:** stock returns exactly to 95; no over-refund possible; revenue nets correctly; both refunds audited.
   **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## P4 — Expired batch → attempted sale

### TC-PX-005 — P4: expiry restriction, observed end to end

`PHARMACY_A · PHA · Expiry/Sales · Critical`
**Steps**

1. **S-PPHA** create batch `QP-EXP` qty 20 with expiry **yesterday**; and `QP-TODAY` qty 20 expiring **today**.
2. Expiry Management → record which bucket each lands in (`TC-PE-001`/`002`).
3. Billing Counter → search the medicine; record whether either batch is offered.
4. `POST /pharmacy/sales` via **API** with `QP-EXP`'s batch id, then `QP-TODAY`'s.
5. Block a healthy batch and repeat step 4 with it.
6. **Confirm Disposal** on `QP-EXP`; re-attempt the sale.
   **Expected (product intent):** expired, blocked and disposed stock is **not sellable in the UI or the API**. Record each result; the today-expiry boundary is the one genuinely ambiguous case (`TC-PE-002`) — mark it `NEEDS_PRODUCT_CONFIRMATION` if the UI and the API disagree. **A completed sale of expired stock is Critical.**
   **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## P5 — Multi-branch isolation

### TC-PX-006 — ⭐ P5: Branch A sale must not touch Branch B

`PHARM_MULTI · ADM + branch users · Branches · Critical`
**Steps**

1. **S-SA** create `QA Pharmacy Multi` on `QA-PHARM-MULTI`.
2. **S-MADM** Pharmacies → create **Branch A** and **Branch B**, each with a login.
3. Stock `QA Multi Paracetamol`: **Branch A = 10**, **Branch B = 5**. Record both.
4. **S-BA** log in → Inventory shows **10**, and **no Branch B row**.
5. **S-BA** sell **2**.
6. **S-BA** Inventory → **8**. **S-BB** Inventory → **5**.
7. **S-MADM** switch branches with the selector / `X-Branch-ID`: A = 8, B = 5.
8. **S-BA** attempt `GET`/sale/refund against a **Branch B** batch id and sale id (`TC-PT-023`).
9. **S-BB** attempt to **Search Bill** for Branch A's bill number (`TC-PRF-009`).
10. **S-MADM** per-branch reports and the consolidated view (`TC-PT-028`).
    **Expected:** **A = 8, B = 5** — exactly. Steps 8–9 → **403/404** with no data. Branch reports match their own branch; a consolidated total equals A + B. **Any change to Branch B is a Critical cross-branch inventory mutation.**
    **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PX-007 — P5 variant: branch lifecycle and `X-Branch-ID` authority

`PHARM_MULTI · ADM + branch user · Branches · Critical`
**Steps:** = `TC-PT-019`, `020`, `024`, `026`: reset a branch password (session revoked); deactivate Branch B (login refused, **data preserved**); send `X-Branch-ID` as the **branch user** (ignored) and as the **admin** (honoured); send a malformed and a foreign-tenant branch id; attempt a sale into the deactivated branch.
**Expected:** the header is honoured **only** for `HOSPITAL_ADMIN`; a foreign branch id must never return another tenant's data (**Critical**); deactivation preserves history.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## P6 — Single Pharmacist Admin dual role

### TC-PX-008 — P6: SOLO dual-dashboard lifecycle

`PHARM_SOLO · HOSPITAL_ADMIN · Tiers · Critical`
**Steps**

1. **S-SOLO** log in → lands **`/pharmacy/pharmacy`**.
2. Run a full pharmacist day: add a medicine and batch; make a sale; print; process a refund; block a batch.
3. **Switch to Admin** → `/pharmacy/admin`; confirm the tab set has **no Pharmacists and no Pharmacies** (`TC-PT-005`).
4. From the admin side read Billing, Analytics and Audit Logs — they show the sales just made.
5. **Switch to Pharmacy**; refresh on each side (`TC-PT-004`).
6. Open a **new tab** and log in again → lands on the default, not the remembered choice (`TC-PT-008`).
7. **S-SA** reset this admin's password → **S-SOLO** clicks any tab → 401 → login (`TC-PT-009`).
8. With the SOLO token call `/hospital/nurse`, `/hospital/surgeries`, `/hospital/icu` → **403**; then `/pharmacy/opd`, `/pharmacy/ipd` → **record** (`TC-PT-007`).
   **Expected:** one login operates both surfaces; the switcher and `activeDashboard` behave as described; revocation kills both; **no third role is granted** — the clinical drift rows are recorded, not accepted.
   **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## P7 — Tenant isolation

### TC-PX-009 — P7: PHARMACY_A resource vs PHARMACY_B user (and vs hospital/clinic)

`PHARMACY_A + PHARMACY_B + HOSPITAL_A + CLINIC_A · PHA/ADM · Isolation · Critical`
**Steps** — from **S-PB** with PHARMACY_B's tokens, and then from the hospital and clinic pharmacists:

1. List inventory, sales, purchases, suppliers, manufacturers, branches — PHARMACY_A's must be absent.
2. Fetch each PHARMACY_A record by id: batch, sale, purchase, supplier, branch, `transactions/{batchId}`.
3. **Search Bill** for PHARMACY_A's bill number (`TC-PRF-009`).
4. Attempt writes: sell from A's batch; refund A's sale; adjust A's stock; block and dispose A's batch; post A's purchase; edit/delete A's supplier; reset A's pharmacist's password; create a branch under A.
5. Download `GET /pharmacy/sales/{A sale id}/pdf`.
6. Run `/pharmacy/reports/dashboard` and `/pharmacy/reports/export` and search the CSV for A's medicine names.
7. Return to **S-PADM** and verify **every** A figure is unchanged.
   **Expected:** every read **403/404** with no data in the body; **no PDF bytes**; every write refused and A verified unchanged; no A row in B's reports or CSV. **Any 200 carrying A's data stops testing immediately — Critical.**
   **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## P8 — Clinical domains must stay closed

### TC-PX-010 — ⭐ P8: pharmacy attempts OPD / IPD / wards / beds

`PHARMACY_A · ADM + PHA · Authorization · Critical`
**Steps:** run `TC-PH-019` in full (the method matrix) plus `TC-PH-020`, with **both** the admin and the pharmacist tokens, and record every status in that table. Then:

1. If any **GET returns 200 with rows**, note whose rows they are — another tenant's is **Critical**.
2. If `POST /pharmacy/opd` or `/pharmacy/ipd/admit` **creates a record**, capture the row id and report it; **do not delete it yourself**.
3. If `GET /pharmacy/opd/{HOSPITAL_A id}/pdf` returns bytes, **open the file** and record whether it contains a real patient's data.
4. Confirm in the UI that a pharmacy tenant shows **no** Patients, OPD, IPD, Wards or Beds tab on either dashboard.
5. Check `/pharmacy/doctors` and `/pharmacy/receptionists` creation (`TC-PA-008`, `TC-PH-016`/`017`) and the landing URL a pharmacy-tenant DOCTOR receives.
   **Expected (product policy):** a standalone PHARMACY **cannot** operate any clinical domain, and DOCTOR/RECEPTIONIST are not supported roles here.
   **Known code position:** 36 ungated endpoints across `/pharmacy/opd|ipd|beds|wards`, plus 17 across `/pharmacy/doctors|receptionists`. **A 200 is a FAIL / IMPLEMENTATION_DRIFT at HIGH; a created row or another tenant's data raises it to Critical.**
   **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
