# 18 — FAILURE & RECOVERY JOURNEYS

**Cases:** `TC-E2E-118` … `TC-E2E-127` · All tenants

Everything above tests the product working. This document tests the product **being interrupted** —
which is how real clinics use it. The single assertion running through all ten cases:

> **After any interruption, the system must be in exactly one of two states: the action fully
> happened, or it fully did not. Never half.**

For every case, the evidence is a **before/after pair**: the record and the counter _before_ the
interruption, and the same two _after_ recovery.

## How to interrupt, without touching application code

Use only the browser and the network — **never** edit code, config, schema or data to force a
failure, and **never** "fix" data afterwards to make a case pass.

- **Network offline** — DevTools ▸ Network ▸ Offline, then back online.
- **Throttle** — "Slow 3G", to make double-submits easy to perform.
- **Kill the request** — DevTools ▸ Network ▸ block the request URL pattern.
- **Refresh / navigate away** mid-action (F5, Back).
- **Close and reopen the tab** (remember: `sessionStorage`, so this **ends the session**).
- **Two browsers** for concurrency.
- **Let the token expire** (12h) or have an admin change the user's role/password to revoke it.

---

### TC-E2E-118 — ⭐ Double-submit across every money and stock action

`All tenants · all roles · Failure/Recovery · Critical`
**Steps:** `TC-HB-019` · `TC-HB-020` · `TC-HD-014` · `TC-PS-008` · `TC-PS-010` · `TC-PP-010` · `TC-HOT-024`.
**E2E assertion:** throttle to Slow 3G and double-click: Complete Consultation, Mark Paid, Record Payment, Complete Sale, Post & Inward, Process Refund, Start Surgery, Complete Surgery, Admit, Discharge. After **each**, re-read the record and its counter. Exactly **one** of everything: one bill, one payment row, one stock movement, one status transition, one audit row. Any duplicate is **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-119 — ⭐ Network drop mid-write leaves nothing half-written

`All tenants · all roles · Failure/Recovery · Critical`
**Steps:** `TC-HB-024` · `TC-PS-009` · `TC-HR-030` · `TC-AUTH-027`.
**E2E assertion:** go **Offline** at the moment of submit for: patient registration, OPD entry, consultation completion, payment, pharmacy sale, IPD admission, document upload. Come back online, **refresh**, and check: either the record exists **complete and correct**, or it does not exist **at all**. A patient with no OPD case, a bill with no payment, a sale that decremented stock without an invoice, or a half-uploaded document are all **Critical**. Then **retry** the same action and confirm it produces exactly one record, not a second.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-120 — Refresh and Back mid-flow

`All tenants · all roles · Failure/Recovery · High`
**Steps:** `TC-AUTH-015` · `TC-HR-040` · `TC-HAS-034`.
**E2E assertion:** refresh (F5) in the middle of registration, admission, a sale and a multi-step form. **Returning to the default tab after F5 is expected and correct** — the frontend is tab-state-based, with `/ipd/:id` the only deep-linkable record screen; do **not** file that as a bug. What you are testing is that **no partial record was committed** and that pressing **Back** and re-submitting does not create a second one.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-121 — Session loss mid-flow is handled cleanly

`All tenants · ADM + staff · Failure/Recovery · Critical`
**Steps:** `TC-AUTH-018` · `TC-AUTH-025` · `TC-AUTH-014`.
**E2E assertion:** have an admin **change the working user's role or password** (which bumps `tokenVersion`) while they are half-way through a form. Their submit must fail with **401** and redirect to `/login` — not hang, not silently discard, not appear to succeed. After re-login, confirm **nothing partial was saved**. Also confirm closing the tab genuinely ends the session (`sessionStorage`), and that a second tab was always a separate session.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-122 — Two users, one resource

`HOSPITAL_A · REC + PHA · Failure/Recovery · Critical`
**Steps:** `TC-HWB-017` (last bed) · `TC-PS-010` (last unit) · `TC-HB-020` (same bill) · `TC-HR-035` (same slot).
**E2E assertion:** two browsers, two users, simultaneous submit on the **same** last bed, last stock unit, same bill and same appointment slot. The correct outcome is **one succeeds, one is refused with a clear message**. Record both results and the final state of the resource. Two successes on a single-occupancy resource is **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-123 — ⭐ Duplicate patient registration under stress

`HOSPITAL_A · REC · Patient Identity · High`
**Steps:** `TC-E2E-013` … `TC-E2E-024` (the identity journey) · `TC-HR-012`.
**E2E assertion:** register a patient with a phone that already exists — the **confirmation** must appear, and cancelling must create **nothing**. Confirming creates **one** patient. Then repeat with a **double-click on Confirm**: still **one** patient.
**IMPORTANT:** two **simultaneous** registrations from two browsers **may both succeed** — the database uniqueness backstop is **Phase D and has not shipped**. **Do NOT assert concurrency uniqueness and do NOT file it as a bug.** Record the observed behaviour under `NEEDS_PRODUCT_CONFIRMATION` and move on.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-124 — Stale acknowledgement cannot be reused

`HOSPITAL_A · REC · Patient Identity · High`
**Steps:** `TC-E2E-019` · `TC-E2E-020` · `TC-E2E-021`.
**E2E assertion:** acknowledge a duplicate for phone **X**; the patient keeps phone X → no re-prompt. **Change the phone to Y** → the exemption **lapses**: editing again must re-prompt if Y also collides. Change **back to X** → it may become effective again. The acknowledgement is **bound to the phone value**, never a permanent "never ask again" flag. A stale ack that exempts a **different** phone is **High** and must be filed.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-125 — Validation refusals never leave residue

`All tenants · all roles · Failure/Recovery · High`
**Steps:** `TC-HR-025` … `TC-HR-030` · `TC-HR-031` · `TC-PS-005` · `TC-PI-011` · `TC-HB-021` · `TC-API-012`.
**E2E assertion:** submit a batch of invalid forms — missing required fields, bad phone, future date of birth, age over 120, negative amount, zero quantity, emoji in a name field, over-long text, whitespace-only values. Each must be refused **with a message that names the field**, and afterwards the list counts, stock figures and ledgers must be **identical to before**. Also confirm the error text comes through as expected (`ApiResponse.error` puts the message in the **`error`** field) and that **no stack trace, SQL or internal path** is shown to the user.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-126 — Backend restart / outage during a working day

`HOSPITAL_A · ADM + REC · Failure/Recovery · Critical`
**Steps:** `TC-AUTH-027` · `TC-API-011`.
**E2E assertion:** ask the environment owner to restart the backend (**do not kill processes yourself**) during an ordinary working session. While it is down, the UI must show a clear failure — never a silent success or an infinite spinner. When it returns, **`/api/public/health` answers**, the user's session still works if the token is valid, all records created before the restart are present and correct, and retrying the interrupted action creates exactly one record. Then confirm the **overnight/idle case**: leave a session open, return, and the first action either works or cleanly 401s.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-127 — ⭐ Post-chaos full reconciliation

`All tenants · ADM · Failure/Recovery · Critical`
**Steps:** re-run `TC-E2E-066` (money), `TC-E2E-067` (stock), `TC-E2E-096` (beds), `TC-E2E-110` (isolation).
**E2E assertion:** after every interruption above, do a **complete reconciliation**: total collected = sum of your money table; stock on-hand = ledger balance; bed states = census; no tenant's figures include another's; no duplicate patients beyond those you deliberately confirmed; every audit trail continuous with no gaps or repeats. **This is the release gate for the E2E suite** — if this case fails, the build does not ship regardless of how many earlier cases passed.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
