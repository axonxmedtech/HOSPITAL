# 11 — PHARMACY REGRESSION CHECKLIST

**Baseline:** `aa143a7` · **No new Test Case IDs** — this document selects existing ones.

| Tier                         | When                                     | Cases  | Time    |
| ---------------------------- | ---------------------------------------- | ------ | ------- |
| **P0 — Critical smoke**      | every staging→production release         | **16** | ~60 min |
| **P1 — Major workflow**      | every release touching pharmacy/ERP code | **20** | ~3 h    |
| **P2 — Extended regression** | major releases, or tier/branch changes   | **26** | ~1 day  |

> Release rule: run **Hospital P0 + Clinic P0 + Pharmacy P0** before every production release.

---

## P0 — CRITICAL SMOKE (16)

| ID           | Proves                                                               | Result |
| ------------ | -------------------------------------------------------------------- | ------ |
| `TC-PA-001`  | pharmacy admin logs in and lands on `/pharmacy/admin`                | `[ ]`  |
| `TC-PH-001`  | pharmacist lands on `/pharmacy/pharmacy` with 13 tabs                | `[ ]`  |
| `TC-PA-003`  | the admin tab set has no clinical tabs                               | `[ ]`  |
| `TC-PI-006`  | a medicine and batch can be created                                  | `[ ]`  |
| `TC-PP-009`  | **Post & Inward** increases stock                                    | `[ ]`  |
| `TC-PP-010`  | posting is idempotent (no double inward)                             | `[ ]`  |
| `TC-PS-001`  | a walk-in sale completes and decrements stock                        | `[ ]`  |
| `TC-PS-005`  | insufficient stock is refused, stock unchanged                       | `[ ]`  |
| `TC-PS-007`  | expired / blocked batches cannot be sold (UI **and** API)            | `[ ]`  |
| `TC-PS-008`  | a double-clicked sale creates one sale                               | `[ ]`  |
| `TC-PRF-001` | a patient refund restores stock                                      | `[ ]`  |
| `TC-PRF-003` | over-refund is refused                                               | `[ ]`  |
| `TC-PR-001`  | the reconciliation fixture: every report matches the hand arithmetic | `[ ]`  |
| `TC-PX-006`  | multi-branch isolation: A = 8, B = 5                                 | `[ ]`  |
| `TC-PX-009`  | pharmacy↔pharmacy tenant isolation holds                             | `[ ]`  |
| `TC-PX-010`  | clinical domains denied (drift rows recorded)                        | `[ ]`  |

---

## P1 — MAJOR WORKFLOW (20)

**Journeys (5):** `TC-PX-001` P1 · `TC-PX-002` P2 · `TC-PX-004` P3 · `TC-PX-005` P4 · `TC-PX-008` P6 SOLO.
**Tiers (4):** `TC-PT-004` SOLO landing/switcher · `TC-PT-011` **branches on SINGLE (discovery)** · `TC-PT-012` SINGLE tabs · `TC-PT-016` MULTI tabs.
**Inventory (4):** `TC-PI-012` FEFO ordering · `TC-PI-013` chosen batch decrements · `TC-PI-019` lifecycle arithmetic · `TC-PI-010` stock adjustment.
**Sales & returns (3):** `TC-PS-002` multi-line sale · `TC-PS-015` invoice PDF · `TC-PRF-014` returns arithmetic.
**Expiry (2):** `TC-PE-001` buckets · `TC-PE-004` block stops sales.
**Reports & audit (2):** `TC-PR-002` sales report · `TC-PR-011` audit coverage.

---

## P2 — EXTENDED REGRESSION (26)

**Branches (6):** `TC-PT-017` · `TC-PT-019` · `TC-PT-020` · `TC-PT-022` · `TC-PT-024` `X-Branch-ID` authority · `TC-PX-007`.
**Inventory depth (5):** `TC-PI-007` batch validation · `TC-PI-009` ledger · `TC-PI-011` adjustment guards · `TC-PI-014` multi-batch quantity · `TC-PI-021` isolation.
**Purchases (4):** `TC-PP-008` draft · `TC-PP-011` validation · `TC-PP-013` existing batch number · `TC-PP-016` isolation.
**Sales depth (3):** `TC-PS-010` two counters, last unit · `TC-PS-011` totals/tax/discount · `TC-PS-017` wrong branch.
**Returns depth (3):** `TC-PRF-006` wrong batch · `TC-PRF-010` another tenant's sale · `TC-PRF-012` supplier-return guards.
**Expiry depth (2):** `TC-PE-002` boundary dates · `TC-PE-006` disposal.
**Roles & drift (3):** `TC-PH-016` doctor unsupported · `TC-PA-015` settings drift · `TC-PH-018` nurse/OT correctly closed.

---

## Sign-off

```
Release ____________  SHA ____________  Env ____________  Date ____________
Tier(s) under test: [ ] SOLO  [ ] SINGLE  [ ] MULTI

Pharmacy P0  16  Pass ___ Fail ___ Blocked ___   ⇒ release blocked if Fail > 0
Pharmacy P1  20  Pass ___ Fail ___ Blocked ___
Pharmacy P2  26  Pass ___ Fail ___ Blocked ___ (N/A if not run — state why)

Accepted known issues (IDs + reference to status/IMPLEMENTATION-STATUS.md):
______________________________________________________________

QA ____________   Product ____________
```

> **Known drift is not a P0 failure.** The `/pharmacy/opd|ipd|beds|wards` aliases, the
> `/pharmacy/doctors|receptionists` aliases, the ungated settings endpoints and the unresolved
> `/pharmacy/branches` reachability on SINGLE/SOLO are **recorded, accepted, open items**. Track
> their status per release; a _change_ in behaviour, in either direction, is worth reporting.
