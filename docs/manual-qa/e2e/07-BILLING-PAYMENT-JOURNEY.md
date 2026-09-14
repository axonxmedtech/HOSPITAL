# 07 — BILLING & PAYMENT JOURNEY (financial integrity)

**Cases:** `TC-E2E-059` … `TC-E2E-066` · Tenants HOSPITAL_A, CLINIC_A, PHARMACY_A

Money must be traceable end to end and must never be created or destroyed by a UI accident.

## Money state table

| #   | Flow            | Entity             | Identifier | Charge              | Discount                  | Tax        | Paid            | Balance | Status        | Evidence    |
| --- | --------------- | ------------------ | ---------- | ------------------- | ------------------------- | ---------- | --------------- | ------- | ------------- | ----------- |
| 1   | OPD             | Bill               | `____`     | 555 + case paper    | _(record if implemented)_ | _(record)_ | 0               | full    | **`PENDING`** | screenshot  |
| 2   | OPD             | Bill               | same       | —                   | —                         | —          | full            | 0       | **`PAID`**    | receipt PDF |
| 3   | OPD partial     | Bill               | `____`     | 1000                | —                         | —          | 400             | 600     | **`PARTIAL`** | screenshot  |
| 4   | OPD partial     | Bill               | same       | —                   | —                         | —          | +600            | 0       | **`PAID`**    | receipt PDF |
| 5   | IPD             | Bill               | `____`     | accumulating        | —                         | —          | 0               | running | `PENDING`     | screenshot  |
| 6   | IPD final       | Bill               | same       | frozen at discharge | —                         | —          | full            | 0       | **`PAID`**    | receipt PDF |
| 7   | Pharmacy        | Sale               | `____`     | qty × rate          | _(record)_                | _(record)_ | full            | 0       | completed     | invoice PDF |
| 8   | Pharmacy refund | Return             | `____`     | —                   | —                         | —          | −refund         | —       | processed     | screenshot  |
| 9   | Reconciliation  | Overview / Reports | —          | —                   | —                         | —          | **Σ collected** | —       | —             | screenshot  |

> **`PENDING` · `PARTIAL` · `PAID`** are the only billing statuses in the entity (the UI also shows
> `CLOSED` in `BillingTable`). **There is no DELETE endpoint on billing for any role**, and **no
> cancel or refund path for a hospital/clinic bill** — that is `PARTIAL`, not a bug. Only pharmacy
> sales can be refunded.

---

### TC-E2E-059 — One visit produces exactly one bill, at the configured fee

`HOSPITAL_A · DOC → REC · Billing · Critical`
**Steps:** `TC-HAC-019` · `TC-HB-001` · `TC-HB-003` · `TC-HB-008`.
**E2E assertion:** the amount equals the fee the admin configured — trace it from **Settings ▸ Fees → consultation modal prefill → bill line → receipt PDF**, four places, one number. Completing the consultation **once** produces **one** bill; the Overview collection rises by exactly the paid amount **and by nothing else**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-060 — Payment timing changes when the bill exists, not how much

`HOSPITAL_A · ADM → REC · Billing · Critical`
**Steps:** `TC-HAS-003` · `TC-HB-002`.
**E2E assertion:** with **Before OPD**, the bill is created `PAID` at OPD entry with the method and reference recorded, and the doctor's completion creates **no second bill**. With **After OPD**, the bill appears `PENDING` after completion. In both modes the **total is identical** — timing must not change the amount.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-061 — Partial → full payment arithmetic

`HOSPITAL_A · RECEPTIONIST · Billing · Critical`
**Steps:** `TC-HB-004` · `TC-HB-006`.
**E2E assertion:** 1000 → pay 400 → status **`PARTIAL`**, balance **600**; pay 600 → **`PAID`**, balance **0**. **Both payments are listed separately** and the day's collection includes **both** — a `PARTIAL` bill must contribute only what was actually paid, never the full charge.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-062 — ⭐ Money cannot be created by a click

`HOSPITAL_A · REC + DOC · Billing · Critical`
**Steps:** `TC-HB-019` (double-click Mark Paid and a partial payment) · `TC-HB-020` (two users pay the same bill) · `TC-HB-024` (backend failure mid-payment) · `TC-HD-014` (double-click Complete Consultation).
**E2E assertion:** after **every** one of these, re-read the bill and the collection total:

- exactly **one** payment row and **one** status change per intended payment;
- the balance is **never negative**;
- the collection total moves by the intended amount **once**;
- a failed request leaves **no** half-recorded payment, and the retry produces exactly one.
  A duplicated payment or a double-billed consultation is **Critical**.
  **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-063 — Overpayment and illegal transitions

`HOSPITAL_A · RECEPTIONIST · Billing · Critical`
**Steps:** `TC-HB-005` · `TC-HB-021` · `TC-HB-022`.
**E2E assertion:** paying more than the balance, paying 0, paying a negative amount and paying an already-`PAID` bill are each refused with a clear message and **no ledger movement**. `PAID → PENDING` is refused. **`DELETE` on a bill is 403/405 for every role** — a deletable bill is **Critical**. If overpayment is _accepted_ as an advance, record it as `NEEDS_PRODUCT_CONFIRMATION`, not a pass.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-064 — IPD bill accumulates, freezes at discharge, and does not grow after

`HOSPITAL_A · DOC + ADM + REC · Billing · Critical`
**Steps:** `TC-HB-010` · `TC-HB-011` · `TC-HB-012` · `TC-HA-035`.
**E2E assertion:** each IPD prescription and administered hospital item adds **exactly one** line and decrements hospital-inventory stock by exactly that quantity; the running total always equals the sum of lines; at discharge the bill is settled; **post-discharge attempts to administer or prescribe are refused**, so the settled bill cannot grow afterwards.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-065 — Pharmacy money: sale, refund, and no double counting

`HOSPITAL_A + PHARMACY_A · PHA + ADM · Billing · Critical`
**Steps:** `TC-PS-011` (totals/tax/discount/rounding — **record what actually exists**) · `TC-PS-015` · `TC-PRF-001` · `TC-HB-013` · `TC-HB-014` · `TC-PR-002`.
**E2E assertion:** the invoice total equals the on-screen total **to the paisa**, with a consistent rounding rule; a refund reduces the day's net sales by exactly the refunded value; the hospital's **billing collection card and the pharmacy sales card are not summing the same money twice** on the Overview. Record the actual pharmacy-sale ↔ hospital-bill relationship (`NEEDS_PRODUCT_CONFIRMATION` if ambiguous).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-066 — ⭐ Day-end reconciliation and financial isolation

`HOSPITAL_A + B + CLINIC_A + PHARMACY_A · ADM · Billing/Reports · Critical`
**Steps:** `TC-HB-018` · `TC-HB-027` · `TC-ISO-034`…`036` · `TC-PR-014`.
**E2E assertion:** sum every payment you took today by hand from the money table; the Overview collection card, the Reports revenue figure and the pharmacy dashboard must equal it **exactly**. Then confirm from another tenant that you cannot list, fetch, **pay**, or download a receipt for any of these bills, and that the other tenant's totals **exclude** them entirely. An aggregate that silently includes another tenant is a **Critical** leak even with no name on screen.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
