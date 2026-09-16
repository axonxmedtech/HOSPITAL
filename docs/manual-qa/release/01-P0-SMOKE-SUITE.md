# 01 — P0 CRITICAL SMOKE SUITE (54)

**Blocking. Every staging→production release. A P0 failure stops the release — it is not triaged.**

**Composition:** Hospital **24** + Clinic **14** + Pharmacy **16** = **54**, exactly as Tranches 2
and 3 defined. **Tranche 4 did not add to P0.** Run the cases as written in their source documents
and record the result **here**.

| Block        | Source                                                                                               | Cases | Time    |
| ------------ | ---------------------------------------------------------------------------------------------------- | ----- | ------- |
| A — Hospital | [`../hospital/12-HOSPITAL-REGRESSION-CHECKLIST.md`](../hospital/12-HOSPITAL-REGRESSION-CHECKLIST.md) | 24    | ~90 min |
| B — Clinic   | [`../clinic/07-CLINIC-REGRESSION-CHECKLIST.md`](../clinic/07-CLINIC-REGRESSION-CHECKLIST.md)         | 14    | ~60 min |
| C — Pharmacy | [`../pharmacy/11-PHARMACY-REGRESSION-CHECKLIST.md`](../pharmacy/11-PHARMACY-REGRESSION-CHECKLIST.md) | 16    | ~70 min |

Three testers can run A, B and C in parallel in ~90 minutes. One tester needs ~4 hours.

---

## BLOCK A — HOSPITAL (24)

### A1 Auth & tenancy (4)

| ID            | Proves                                             | Result | Evidence |
| ------------- | -------------------------------------------------- | ------ | -------- |
| `TC-AUTH-001` | a hospital user can log in                         | `[ ]`  | ____     |
| `TC-AUTH-013` | two tenants in two tabs stay separate              | `[ ]`  | ____     |
| `TC-AUTH-019` | password reset revokes a live session              | `[ ]`  | ____     |
| `TC-API-003`  | a Super Admin token is refused on tenant endpoints | `[ ]`  | ____     |

### A2 Patient identity (4)

| ID          | Proves                                                                 | Result | Evidence |
| ----------- | ---------------------------------------------------------------------- | ------ | -------- |
| `TC-HR-001` | a patient registers and gets `PAT<id>`                                 | `[ ]`  | ____     |
| `TC-HR-005` | duplicate phone shows the chooser; **Use This Patient** creates nobody | `[ ]`  | ____     |
| `TC-HR-006` | **Register Different Patient** creates a separate identity             | `[ ]`  | ____     |
| `TC-HR-011` | the 409 exposes only id / publicId / customId / name / age             | `[ ]`  | ____     |

### A3 Core clinical flow (5)

| ID          | Proves                                                    | Result | Evidence |
| ----------- | --------------------------------------------------------- | ------ | -------- |
| `TC-HR-029` | an OPD case can be created                                | `[ ]`  | ____     |
| `TC-HD-005` | a consultation completes with a prescription              | `[ ]`  | ____     |
| `TC-HD-016` | the consultation attaches to the patient actually opened  | `[ ]`  | ____     |
| `TC-HD-017` | the prescription PDF carries the **patient's** identifier | `[ ]`  | ____     |
| `TC-HD-014` | double-clicking Complete does not create two bills        | `[ ]`  | ____     |

### A4 Admission & beds (3)

| ID           | Proves                                                 | Result | Evidence |
| ------------ | ------------------------------------------------------ | ------ | -------- |
| `TC-HR-039`  | reception can admit; the bed becomes `occupied`        | `[ ]`  | ____     |
| `TC-HWB-010` | the full bed cycle including the `cleaning` gate holds | `[ ]`  | ____     |
| `TC-HR-041`  | a patient cannot be admitted twice                     | `[ ]`  | ____     |

### A5 Pharmacy & billing (4)

| ID          | Proves                                                           | Result | Evidence |
| ----------- | ---------------------------------------------------------------- | ------ | -------- |
| `TC-HP-012` | a prescription sale decrements stock                             | `[ ]`  | ____     |
| `TC-HP-014` | insufficient stock is refused and stock is unchanged             | `[ ]`  | ____     |
| `TC-HP-017` | a double-clicked sale creates one sale                           | `[ ]`  | ____     |
| `TC-HB-003` | Mark Paid moves the bill to `PAID` and the collection reconciles | `[ ]`  | ____     |

### A6 Isolation & entitlement (4)

| ID           | Proves                                                  | Result | Evidence |
| ------------ | ------------------------------------------------------- | ------ | -------- |
| `TC-ISO-005` | another tenant's patient by numeric id → 403/404        | `[ ]`  | ____     |
| `TC-ISO-006` | another tenant's patient by **publicId** → 403/404      | `[ ]`  | ____     |
| `TC-ISO-020` | the `/ipd/:id` deep link leaks nothing across tenants   | `[ ]`  | ____     |
| `TC-SA-041`  | a revoked module denies the API even with a valid token | `[ ]`  | ____     |

---

## BLOCK B — CLINIC (14)

| ID           | Proves                                                                 | Result | Evidence |
| ------------ | ---------------------------------------------------------------------- | ------ | -------- |
| `TC-CD-001`  | clinic login lands correctly; the namespace is `/clinic`               | `[ ]`  | ____     |
| `TC-CD-004`  | the clinic sidebar shows only supported tabs                           | `[ ]`  | ____     |
| `TC-CR-001`  | a clinic patient registers and gets `PAT<id>`                          | `[ ]`  | ____     |
| `TC-CD-021`  | duplicate phone shows the chooser; **Use This Patient** creates nobody | `[ ]`  | ____     |
| `TC-CD-022`  | **Register Different Patient** creates a separate identity             | `[ ]`  | ____     |
| `TC-CR-011`  | an appointment can be booked                                           | `[ ]`  | ____     |
| `TC-CR-014`  | an OPD case can be created                                             | `[ ]`  | ____     |
| `TC-CDR-004` | a consultation completes with a prescription                           | `[ ]`  | ____     |
| `TC-CDR-013` | the consultation attaches to the patient actually opened               | `[ ]`  | ____     |
| `TC-CDR-014` | PDFs carry the **clinic** header and the **patient's** identifier      | `[ ]`  | ____     |
| `TC-CR-020`  | a bill is generated and can be paid                                    | `[ ]`  | ____     |
| `TC-CD-024`  | the same phone in another tenant leaks nothing                         | `[ ]`  | ____     |
| `TC-CX-010`  | hospital-only domains are denied (drift row recorded)                  | `[ ]`  | ____     |
| `TC-CD-039`  | clinic↔clinic and clinic↔hospital isolation holds                      | `[ ]`  | ____     |

---

## BLOCK C — PHARMACY (16)

| ID           | Proves                                                               | Result | Evidence |
| ------------ | -------------------------------------------------------------------- | ------ | -------- |
| `TC-PA-001`  | the pharmacy admin logs in and lands on `/pharmacy/admin`            | `[ ]`  | ____     |
| `TC-PH-001`  | the pharmacist lands on `/pharmacy/pharmacy` with 13 tabs            | `[ ]`  | ____     |
| `TC-PA-003`  | the admin tab set has no clinical tabs                               | `[ ]`  | ____     |
| `TC-PI-006`  | a medicine and batch can be created                                  | `[ ]`  | ____     |
| `TC-PP-009`  | **Post & Inward** increases stock                                    | `[ ]`  | ____     |
| `TC-PP-010`  | posting is idempotent (no double inward)                             | `[ ]`  | ____     |
| `TC-PS-001`  | a walk-in sale completes and decrements stock                        | `[ ]`  | ____     |
| `TC-PS-005`  | insufficient stock is refused; stock unchanged                       | `[ ]`  | ____     |
| `TC-PS-007`  | expired / blocked batches cannot be sold (UI **and** API)            | `[ ]`  | ____     |
| `TC-PS-008`  | a double-clicked sale creates one sale                               | `[ ]`  | ____     |
| `TC-PRF-001` | a patient refund restores stock                                      | `[ ]`  | ____     |
| `TC-PRF-003` | an over-refund is refused                                            | `[ ]`  | ____     |
| `TC-PR-001`  | the reconciliation fixture: every report matches the hand arithmetic | `[ ]`  | ____     |
| `TC-PX-006`  | multi-branch isolation: **A = 8, B = 5**                             | `[ ]`  | ____     |
| `TC-PX-009`  | pharmacy↔pharmacy tenant isolation holds                             | `[ ]`  | ____     |
| `TC-PX-010`  | clinical domains denied (drift rows recorded)                        | `[ ]`  | ____     |

---

## P0 result

**Passed ____ / 54.** Any figure below 54 = **RELEASE BLOCKED**.

|                    |      |
| ------------------ | ---- |
| Build / commit SHA | ____ |
| Environment        | ____ |
| Tester(s)          | ____ |
| Date               | ____ |
| Bugs raised        | ____ |

## Recommended P0 promotions — a decision for the release manager, not for QA

P0 was **deliberately held at 54**. If the team later wants stronger release-gating, these four
E2E cases are the strongest candidates, in this order. **Do not add them without a decision.**

| Candidate    | Why                                                                      | Cost    |
| ------------ | ------------------------------------------------------------------------ | ------- |
| `TC-E2E-127` | post-chaos full reconciliation — the single best catch-all               | ~30 min |
| `TC-E2E-110` | aggregates/search/exports never include another tenant (the silent leak) | ~20 min |
| `TC-E2E-062` | money cannot be created by a click                                       | ~20 min |
| `TC-E2E-093` | the eight-step bed cycle, every step audited                             | ~20 min |

Adopting all four would make P0 **58 cases / ~5 h**.
