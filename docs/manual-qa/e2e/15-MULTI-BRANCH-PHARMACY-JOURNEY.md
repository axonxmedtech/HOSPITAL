# 15 — MULTI-BRANCH PHARMACY JOURNEY

**Cases:** `TC-E2E-102` … `TC-E2E-106` · Tenant **PHARMACY_MULTI** (a PHARMACY tenant on a tier including `MULTI_PHARMACY`)

`MULTI_PHARMACY` **implies** `PHARMACY_BRANCH` (`EntitlementRegistry.IMPLIED_BY`). Branch selection
uses the **`X-Branch-ID`** header, which `JwtAuthenticationFilter` honours **only when the role is
`HOSPITAL_ADMIN`** — a pharmacist cannot switch branches by sending the header.

## Branch fixture (use these exact numbers)

| Medicine                        | Branch A | Branch B          |
| ------------------------------- | -------- | ----------------- |
| MED-X opening stock             | **10**   | **5**             |
| after selling **2** at Branch A | **8**    | **5** ← unchanged |

### TC-E2E-102 — ⭐ The branch arithmetic

`PHARMACY_MULTI · ADM + PHA · Branches · Critical`
**Steps:** `TC-PT-014` · `TC-PT-016` · `TC-PT-018`.
**E2E assertion:** create Branch A and Branch B; stock MED-X **10** at A and **5** at B. Sell **2** at Branch A. Immediately read both: **A = 8, B = 5**. Branch B's figure must not move by any amount, in any direction. Confirm the ledger row for the sale is attached to **Branch A only**, and Branch B's ledger has **no new row**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: A=____ B=____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-103 — ⭐ `X-Branch-ID` is an admin-only capability

`PHARMACY_MULTI · ADM + PHA · Security · Critical`
**Steps:** `TC-PT-020` · `TC-PT-021` · `TC-PT-024`.
**E2E assertion:** three requests, same endpoint:

1. **admin** + `X-Branch-ID: <B>` → returns **Branch B's** data (impersonation works);
2. **pharmacist of Branch A** + `X-Branch-ID: <B>` → must return **Branch A's own** data or be refused — **never Branch B's**;
3. **admin** + `X-Branch-ID: <a branch of another tenant>` → must be refused.
   Case 2 returning Branch B's data is **Critical**: it is privilege escalation via a header. Case 3 succeeding is a **Critical** tenant leak.
   **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-104 — A pharmacist is confined to their branch, end to end

`PHARMACY_MULTI · PHARMACIST · Branches · Critical`
**Steps:** `TC-PT-022` · `TC-PT-023` · `TC-PI-021`.
**E2E assertion:** as Branch A's pharmacist, run a full working day — sale, refund, adjustment, purchase inward, expiry block — and after each confirm **Branch B is untouched**. Then take Branch B's **batch id, sale id and purchase id** and attempt to read, sell from, refund and adjust them directly by id. Every attempt must be refused. **Re-read Branch B's stock after every attempt.**
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-105 — Consolidated figures add up exactly

`PHARMACY_MULTI · ADMIN · Reports · High`
**Steps:** `TC-PT-025` · `TC-PT-026` · `TC-PR-014`.
**E2E assertion:** with a known day at both branches, the admin's consolidated stock, sales and revenue must equal **Branch A + Branch B exactly** — not approximately, and never double-counted. Then filter to a single branch and confirm the figure matches that branch's own dashboard. A consolidated total that includes a **third** tenant's branch is **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-106 — Tier boundaries and branch revocation

`PHARMACY_A (single) + PHARMACY_MULTI · ADMIN · Entitlements · Critical`
**Steps:** `TC-PT-001` … `TC-PT-010` (the three tiers) · `TC-PT-028` · `TC-MOD-029`.
**E2E assertion:** on a **single-branch** pharmacy tenant, the branch UI is absent **and** the branch endpoints are refused — and sending `X-Branch-ID` changes nothing. On PHARMACY_MULTI, revoke `MULTI_PHARMACY` while the admin is **logged in**: branch features must stop working **without re-login**. Record whether the implied `PHARMACY_BRANCH` also stops — `EntitlementRegistry` **declares only and enforces nothing**, so verify the **backend** behaviour rather than trusting the declaration or the UI. Existing branch data must survive the revocation and return when re-granted.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
