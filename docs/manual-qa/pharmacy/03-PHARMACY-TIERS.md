# 03 — PHARMACY OPERATING TIERS

**Baseline:** `aa143a7` · **Cases:** `TC-PT-001` … `TC-PT-030`

A PHARMACY tenant's plan carries exactly **one tier**. The tier is a module key in the JWT
`modules` claim, so **changing it requires a re-login** to take UI effect.

```
pharmacyMode = modules.includes('MULTI_PHARMACY')        ? 'MULTI'
             : modules.includes('SINGLE_PHARMACIST_ADMIN') ? 'SOLO'
             : 'SINGLE'                                  // default
```

(`HospitalAdminDashboard.jsx:149-153`)

## Tier comparison matrix — derived from code, to be confirmed at runtime

| Feature                                    | SINGLE_PHARMACIST_ADMIN (SOLO)          | SINGLE_PHARMACY   | MULTI_PHARMACY                 |
| ------------------------------------------ | --------------------------------------- | ----------------- | ------------------------------ |
| Admin landing                              | `/pharmacy/pharmacy` (pharmacist first) | `/pharmacy/admin` | `/pharmacy/admin`              |
| **Dual-role switcher** in navbar           | **Yes** (Admin ⇄ Pharmacy)              | No                | No                             |
| Admin tab: Overview                        | ✅                                      | ✅                | ✅                             |
| Admin tab: **Pharmacists**                 | ❌ (one-person shop)                    | **✅**            | ❌                             |
| Admin tab: **Pharmacies** (branches)       | ❌                                      | ❌                | **✅**                         |
| Admin tab: **Suppliers**                   | ❌                                      | ❌                | **✅**                         |
| Admin tab: Billing (pharmacy)              | ✅                                      | ✅                | ✅                             |
| Admin tab: Analytics (pharmacy)            | ✅                                      | ✅                | ✅                             |
| Admin tab: Audit Logs · Settings · Support | ✅                                      | ✅                | ✅                             |
| Separate PHARMACIST login                  | — (admin is the pharmacist)             | expected          | expected, per branch           |
| `PHARMACY_BRANCH` capability               | ❌                                      | ❌                | ✅ (implied by the tier)       |
| `X-Branch-ID` branch switching             | n/a                                     | n/a               | ✅ (**`HOSPITAL_ADMIN` only**) |
| Pharmacist dashboard (13 tabs)             | ✅                                      | ✅                | ✅                             |

> ⚠️ **`PharmacyBranchController` carries no `@RequireModule`** — only `hasRole('HOSPITAL_ADMIN')`.
> So `/pharmacy/branches` may be reachable on **SINGLE** and **SOLO** too. That is the open
> question `TC-PT-011` answers.

> ⚠️ **`X-Branch-ID` is honoured only when the token's role is `HOSPITAL_ADMIN`**
> (`JwtAuthenticationFilter:168-175`). A pharmacist cannot switch branches by header. `TC-PT-024`
> tests this.

---

## A. TIER IDENTIFICATION

### TC-PT-001 — Confirm each tenant's tier

`PHARMACY_* · HOSPITAL_ADMIN · Tiers · High · API`
**Steps:** for `QA Pharmacy Solo`, `QA Pharmacy A` (SINGLE) and `QA Pharmacy Multi`, log in and read `modules` from the login response / `GET /auth/me`.
**Expected:** exactly one of `SINGLE_PHARMACIST_ADMIN` / `SINGLE_PHARMACY` / `MULTI_PHARMACY`, plus the `PHARMACY` base module (added automatically). `MULTI` also implies `PHARMACY_BRANCH`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PT-002 — Plan catalogue offers only pharmacy tiers

`PLATFORM · SUPER_ADMIN · Plans · High · Plans`
**Steps:** = `TC-SA-010`/`011`: open Create Plan under Pharmacy; list the options; then `TC-MOD-002` with `OPD`, `IPD`, `BILLING`, `NURSING` added to a pharmacy plan.
**Expected:** only the PHARMACY base module and the three tiers offered; each illegal module → **400**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PT-003 — Two tiers on one plan

`PLATFORM · SUPER_ADMIN · Plans · Medium · Plans`
**Steps:** attempt a plan with **both** `SINGLE_PHARMACY` and `MULTI_PHARMACY`; assign it and log in.
**Expected:** record whether the plan is refused. If accepted, `MULTI` wins (it is checked first) — record the resulting tab set. `NEEDS_PRODUCT_CONFIRMATION`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## B. SINGLE_PHARMACIST_ADMIN (SOLO) — the dual-role tier

### TC-PT-004 — SOLO landing and switcher

`PHARM_SOLO · HOSPITAL_ADMIN · Tiers · Critical · /login/pharmacy`
**Steps:** 1. Log in as `admin.pharmsolo@qa.test`. 2. Record the landing URL. 3. Find the navbar switcher; **Switch to Admin**; **Switch to Pharmacy**. 4. Refresh on each.
**Expected:** lands **`/pharmacy/pharmacy`**; the switcher is present; switching moves between `/pharmacy/pharmacy` and `/pharmacy/admin`; **refresh keeps the last choice** (`sessionStorage.activeDashboard`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PT-005 — SOLO admin tab set (no Pharmacists, no Pharmacies)

`PHARM_SOLO · HOSPITAL_ADMIN · Tiers · High · /pharmacy/admin`
**Steps:** switch to Admin; list every tab.
**Expected:** **Overview · Billing · Analytics · Audit Logs · Settings · Support** — and **no Pharmacists tab** (one-person shop) and **no Pharmacies tab**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PT-006 — SOLO runs the full pharmacist workflow

`PHARM_SOLO · HOSPITAL_ADMIN (as pharmacist) · Tiers · Critical · /pharmacy/pharmacy`
**Steps:** in pharmacy mode: add a medicine and batch; make a sale; print the invoice; process a return; open Reports.
**Expected:** every operation succeeds with the **admin** token (`PharmacySale`, `Inventory`, `Purchase`, `Supplier` all admit `HOSPITAL_ADMIN`); stock moves correctly.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PT-007 — SOLO dual role grants exactly two surfaces

`PHARM_SOLO · HOSPITAL_ADMIN · Authorization · Critical · API`
**Steps:** with the SOLO admin token call `/hospital/nurse`, `/hospital/notifications`, `/hospital/surgeries`, `/hospital/icu`, `/hospital/opd`, `/hospital/ipd`.
**Expected:** nurse/notifications/OT/ICU **403**. **`/hospital/opd` and `/hospital/ipd` are the drift rows** — record (`TC-PH-019`). The tier must not confer clinical capability.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PT-008 — SOLO `activeDashboard` is per tab and cleared on logout

`PHARM_SOLO · HOSPITAL_ADMIN · Session · Medium · Navbar`
**Steps:** = `TC-MODE-016`: switch to Admin; open a **new tab** and log in again; log out in tab 1 and back in.
**Expected:** the new tab lands on the **default** (`/pharmacy/pharmacy`) because `sessionStorage` is per tab; record whether the preference survives a logout/login in the same tab.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PT-009 — SOLO session revocation covers both dashboards

`PHARM_SOLO · HOSPITAL_ADMIN · Auth · High · API`
**Steps:** = `TC-MODE-007`: log in, switch to Admin, have Super Admin reset the password, then click any tab.
**Expected:** 401 → login. One token, one revocation, both surfaces gone.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PT-010 — Adding a separate pharmacist to a SOLO tenant

`PHARM_SOLO · HOSPITAL_ADMIN · Tiers · Medium · Staff`
**Steps:** = `TC-MODE-015`: there is no Pharmacists tab — attempt `POST /pharmacy/pharmacists` via API; if it succeeds, log in as that pharmacist.
**Expected:** record. `NEEDS_PRODUCT_CONFIRMATION` — should "single pharmacist admin" permit a second pharmacist? If it does, note that the UI offers no way to manage them.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## C. SINGLE_PHARMACY

### TC-PT-011 — ⭐ Is `/pharmacy/branches` reachable on SINGLE? (runtime discovery)

`PHARMACY_A · HOSPITAL_ADMIN · Tiers · High · API`
**This resolves the Tranche-1 open question (item 7). Do not answer from assumption.**
**Steps:**

1. Log in as `admin.pharma@qa.test` (SINGLE). Confirm there is **no Pharmacies tab**.
2. `GET /pharmacy/branches` — record the status and the body.
3. `POST /pharmacy/branches` with a valid branch body — record the status.
4. If a branch was created, `GET /pharmacy/branches` again and check whether a Pharmacies tab appears after a refresh and a re-login.
5. Repeat steps 2–3 on the **SOLO** tenant.
6. Repeat with a **PHARMACIST** token on both tenants.
   **Expected (policy intent):** branch management belongs to MULTI only → 403 on SINGLE and SOLO.
   **Code position:** `PharmacyBranchController` is `hasRole('HOSPITAL_ADMIN')` with **no `@RequireModule`**, so **200 is likely** for admins on all three tiers. If a branch can be created on a SINGLE tenant, record it as **`IMPLEMENTATION_DRIFT`, MEDIUM** — an unsold capability is reachable. Pharmacist tokens must be **403** (role gate) in every tier.
   **Observed:** SINGLE GET ____ · SINGLE POST ____ · SOLO GET ____ · SOLO POST ____ · PHARMACIST ____ · tab appeared? ____
   **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PT-012 — SINGLE admin tab set (has Pharmacists, no Pharmacies)

`PHARMACY_A · HOSPITAL_ADMIN · Tiers · High · /pharmacy/admin`
**Steps:** log in; list tabs.
**Expected:** **Overview · Pharmacists · Billing · Analytics · Audit Logs · Settings · Support**. No Pharmacies, no Suppliers tab at admin level (suppliers are managed from the pharmacist dashboard).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PT-013 — SINGLE: create a pharmacist → login → dashboard

`PHARMACY_A · HOSPITAL_ADMIN → PHARMACIST · Tiers · Critical · Pharmacists`
**Steps:** create `Deepak Jadhav` / `pharm.pharma@qa.test`; log in as him.
**Expected:** lands **`/pharmacy/pharmacy`** with the 13-tab dashboard; sees the tenant's stock.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PT-014 — SINGLE admin has **no** dual-role switcher

`PHARMACY_A · HOSPITAL_ADMIN · Tiers · High · Navbar`
**Steps:** = `TC-MODE-012`: look for a switcher; navigate directly to `/pharmacy/pharmacy`.
**Expected:** no switcher; `/pharmacy/pharmacy` is **allowed by the route** (it admits `HOSPITAL_ADMIN`) — record whether the admin lands there or is redirected. If allowed, note that the _practical_ difference from SOLO is only the landing page and the Pharmacists tab.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PT-015 — SINGLE: full ERP surface works

`PHARMACY_A · PHARMACIST · Tiers · Critical · all pharmacy tabs`
**Steps:** run one operation in each of: Inventory, Purchase Management, Suppliers, Manufacturers, Billing Counter, Returns & Refunds, Expiry Management, Reports, Audit Logs, Settings.
**Expected:** all thirteen tabs functional; detailed cases live in `04`–`09`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## D. MULTI_PHARMACY — branches

### TC-PT-016 — MULTI admin tab set (Pharmacies + Suppliers)

`PHARM_MULTI · HOSPITAL_ADMIN · Tiers · High · /pharmacy/admin`
**Steps:** create `QA Pharmacy Multi` on `QA-PHARM-MULTI`; log in; list tabs.
**Expected:** **Overview · Pharmacies · Suppliers · Billing · Analytics · Audit Logs · Settings · Support**. **No Pharmacists tab** — staff are managed per branch.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PT-017 — Create a branch

`PHARM_MULTI · HOSPITAL_ADMIN · Branches · Critical · Pharmacies`
**Steps:** Pharmacies → create **Branch A** (name, address, contact, login email `branchA.pharmmulti@qa.test`, password); save; reload.
**Expected:** branch listed and **active**; a branch login user is created (`PharmacyBranchService` creates a user with `isActive=true`); `POST /pharmacy/branches` returns 2xx.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PT-018 — Create a second branch and validate

`PHARM_MULTI · HOSPITAL_ADMIN · Branches · High · Pharmacies`
**Steps:** create **Branch B** (`branchB.pharmmulti@qa.test`); then attempt: blank name; duplicate branch name; duplicate login email (use Branch A's).
**Expected:** Branch B created; each invalid attempt refused with a field message and **no branch created** (reload to confirm).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PT-019 — Edit a branch and reset its password

`PHARM_MULTI · HOSPITAL_ADMIN · Branches · High · Pharmacies`
**Steps:** rename Branch A; `POST /pharmacy/branches/{id}/reset-password`; log in with the old password then the new one.
**Expected:** rename persists; old password fails, new works; the branch user's live session is **revoked** (`tokenVersion`, `TC-AUTH-019`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PT-020 — Deactivate / delete a branch

`PHARM_MULTI · HOSPITAL_ADMIN · Branches · Critical · Pharmacies`
**Steps:** 1. Note Branch B's stock and sales. 2. `DELETE /pharmacy/branches/{B}` (or the UI action) with confirmation. 3. Attempt to log in as the Branch B user. 4. Check whether Branch B's stock and sales still exist. 5. Check the admin's consolidated reports.
**Expected:** the branch is deactivated and its user login refused (`PharmacyBranchService:113-118` deactivates both). **Historical stock and sales must not be destroyed.** Record whether deactivation is reversible; `NEEDS_PRODUCT_CONFIRMATION` if there is no reactivate path.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PT-021 — Branch user landing and scope

`PHARM_MULTI · branch user · Branches · Critical · login`
**Steps:** log in as `branchA.pharmmulti@qa.test`; record the role, landing URL and tab set; `GET /auth/me`.
**Expected:** record the assigned role (likely `PHARMACIST`) and that the token carries a **`branchId`**. Its dashboard shows Branch A's data only.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PT-022 — ⭐ Branch inventory isolation

`PHARM_MULTI · branch users · Branches · Critical · Inventory`
**Steps:**

1. As admin (or each branch user) set `QA Multi Paracetamol`: **Branch A = 10**, **Branch B = 5**. Record both.
2. Log in as the **Branch A** user; Inventory shows **10** and **must not show** Branch B's row.
3. Sell **2** from Branch A.
4. Re-read Branch A (**8**) and Branch B (**5**).
5. Log in as the **Branch B** user; confirm **5**.
   **Expected:** **A = 8, B = 5.** Any change to Branch B is a **Critical** cross-branch inventory mutation.
   **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PT-023 — Branch user cannot reach another branch by id

`PHARM_MULTI · branch user · Branches · Critical · API`
**Steps:** with the Branch A token: `GET /pharmacy/inventory/transactions/{Branch_B_batch_id}`; `POST /pharmacy/sales` using a Branch B batch id; `GET /pharmacy/sales/{Branch_B_sale_id}`; `POST /pharmacy/sales/{Branch_B_sale_id}/return`.
**Expected:** **403/404** on all four; **Branch B's stock and sales unchanged** — verify as the Branch B user.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PT-024 — ⭐ `X-Branch-ID` switching is admin-only

`PHARM_MULTI · ADM + branch user · Branches · Critical · API`
**Why:** `JwtAuthenticationFilter:168-175` honours the `X-Branch-ID` header **only when the token's role is `HOSPITAL_ADMIN`**.
**Steps:**

1. With the **admin** token: `GET /pharmacy/inventory` with `X-Branch-ID: <A>`, then `<B>`. Compare the two results.
2. With the **Branch A user** token: repeat with `X-Branch-ID: <B>`.
3. With the admin token send a malformed header (`X-Branch-ID: abc`) and a **foreign tenant's** branch id.
   **Expected:** step 1 — the admin sees each branch's stock in turn (this is the branch-switching mechanism). Step 2 — the header is **ignored**; the branch user still sees only Branch A. Step 3 — the malformed value is ignored; a foreign branch id must **not** return another tenant's data (**Critical** if it does).
   **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PT-025 — Admin branch switcher in the UI

`PHARM_MULTI · HOSPITAL_ADMIN · Branches · High · Overview/Analytics`
**Steps:** find the branch selector on the admin dashboard; switch to Branch A, then Branch B; watch the `X-Branch-ID` header in DevTools; refresh on each.
**Expected:** the selector sets the header; figures change with the selection; record whether the selection survives a refresh.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PT-026 — Inactive branch behaviour

`PHARM_MULTI · ADM + branch user · Branches · High · Pharmacies`
**Steps:** deactivate Branch B (`TC-PT-020`); then as admin select Branch B in the switcher and via `X-Branch-ID`; attempt a sale into Branch B.
**Expected:** an inactive branch should not accept new sales — record the actual behaviour. `NEEDS_PRODUCT_CONFIRMATION` if a sale succeeds into a deactivated branch.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PT-027 — Per-branch purchases and suppliers

`PHARM_MULTI · ADM + branch users · Branches · High · Purchase/Suppliers`
**Steps:** create a supplier at tenant level; raise and post a purchase **into Branch A**; check Branch A stock (+) and Branch B stock (unchanged); check whether the supplier is visible to both branches.
**Expected:** stock lands in the purchasing branch only. Record whether suppliers are tenant-wide (the admin **Suppliers** tab suggests so) or per branch.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PT-028 — Branch reports and consolidated reports

`PHARM_MULTI · ADM + branch users · Reports · High · Reports & Analytics`
**Steps:** make 2 sales in Branch A and 1 in Branch B. As each branch user open Reports; as the admin open Analytics with each branch selected and (if offered) a consolidated view. Export the CSV ledger (`/pharmacy/reports/export`) for each.
**Expected:** each branch user sees only their own sales; the admin's per-branch figures match; a consolidated total, if present, equals A + B exactly; each CSV contains only the selected branch's rows.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PT-029 — Branch audit logs

`PHARM_MULTI · ADM + branch user · Audit · High · Audit Logs`
**Steps:** perform a sale, a return and a stock adjustment in each branch; open Audit Logs as the admin and as each branch user.
**Expected:** entries carry the **branch** (the `audit_logs.branch_id` column exists); a branch user sees only their branch's entries; the admin sees all.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PT-030 — Tier change requires re-login

`PLATFORM → PHARMACY · SUPER_ADMIN · Tiers · High · Plans`
**Steps:** = `TC-SA-042`: move `QA Pharmacy A` from SINGLE to SOLO, then to MULTI. After each change: refresh **without** logging out, then log out and back in.
**Expected:** a refresh alone does **not** change the tab set or the switcher (the tier lives in the JWT `modules` claim); a re-login does. **This is expected behaviour, not a bug** — but document it for support. Also confirm no data is lost across tier changes.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
