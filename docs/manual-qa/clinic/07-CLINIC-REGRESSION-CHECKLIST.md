# 07 — CLINIC REGRESSION CHECKLIST

**Baseline:** `aa143a7` · **No new Test Case IDs** — this document selects existing ones.

| Tier                         | When                                      | Cases  | Time    |
| ---------------------------- | ----------------------------------------- | ------ | ------- |
| **P0 — Critical smoke**      | every staging→production release          | **14** | ~45 min |
| **P1 — Major workflow**      | every release touching clinic/shared code | **18** | ~2.5 h  |
| **P2 — Extended regression** | major releases                            | **22** | ~1 day  |

> Release rule: run **Hospital P0 + Clinic P0 + Pharmacy P0** before every production release.

---

## P0 — CRITICAL SMOKE (14)

| ID           | Proves                                                                 | Result |
| ------------ | ---------------------------------------------------------------------- | ------ |
| `TC-CD-001`  | clinic login lands correctly and the namespace is `/clinic`            | `[ ]`  |
| `TC-CD-004`  | the clinic sidebar shows only supported tabs                           | `[ ]`  |
| `TC-CR-001`  | a clinic patient can be registered and gets `PAT<id>`                  | `[ ]`  |
| `TC-CD-021`  | duplicate phone shows the chooser; **Use This Patient** creates nobody | `[ ]`  |
| `TC-CD-022`  | **Register Different Patient** creates a separate identity             | `[ ]`  |
| `TC-CR-011`  | an appointment can be booked                                           | `[ ]`  |
| `TC-CR-014`  | an OPD case can be created                                             | `[ ]`  |
| `TC-CDR-004` | a consultation completes with a prescription                           | `[ ]`  |
| `TC-CDR-013` | the consultation attaches to the patient actually opened               | `[ ]`  |
| `TC-CDR-014` | PDFs carry the **clinic** header and the **patient's** identifier      | `[ ]`  |
| `TC-CR-020`  | a bill is generated and can be paid                                    | `[ ]`  |
| `TC-CD-024`  | the same phone in another tenant leaks nothing                         | `[ ]`  |
| `TC-CX-010`  | hospital-only domains are denied (drift row recorded)                  | `[ ]`  |
| `TC-CD-039`  | clinic↔clinic and clinic↔hospital isolation holds                      | `[ ]`  |

---

## P1 — MAJOR WORKFLOW (18)

**Journeys (4):** `TC-CX-001` C1 · `TC-CX-002` C2 · `TC-CX-004` **C3 family** · `TC-CX-008` C6 isolation.
**Admin setup → downstream (4):** `TC-CA-002` doctor→picker · `TC-CA-006` fee→bill · `TC-CA-007` vitals→OPD form · `TC-CA-008` operations toggles.
**Reception (3):** `TC-CR-012` new-patient booking never auto-selects · `TC-CR-015` OPD inline new patient · `TC-CR-019` follow-ups.
**Doctor (3):** `TC-CDR-003` start consultation · `TC-CDR-006` lab ordering · `TC-CDR-012` double-submit / failure.
**Pharmacy (2):** `TC-CX-006` C4 dispensing chain · `TC-CP-009` stock/expiry guards.
**Entitlement (2):** `TC-CD-036` working module gates · `TC-CX-007` C5 live revocation.

---

## P2 — EXTENDED REGRESSION (22)

**Negative / drift (8):** `TC-CD-010` · `TC-CD-011` · `TC-CD-013` · `TC-CD-015` · `TC-CD-016` · `TC-CD-017` · `TC-CD-018` · `TC-CD-037`.
**Roles & authorization (4):** `TC-CD-005` · `TC-CD-007` · `TC-CD-008` · `TC-CA-024`.
**Settings depth (4):** `TC-CD-029` · `TC-CD-030` · `TC-CD-031` · `TC-CD-034`.
**Pharmacy depth (3):** `TC-CP-011` returns · `TC-CP-012` expiry · `TC-CP-016` pharmacy isolation.
**Reports & audit (2):** `TC-CA-019` · `TC-CA-021`.
**Special mode (1):** `TC-CD-040` clinic `isSingleDoctor`.

---

## Sign-off

```
Release ____________  SHA ____________  Env ____________  Date ____________

Clinic P0  14  Pass ___ Fail ___ Blocked ___   ⇒ release blocked if Fail > 0
Clinic P1  18  Pass ___ Fail ___ Blocked ___
Clinic P2  22  Pass ___ Fail ___ Blocked ___ (N/A if not run — state why)

Accepted known issues (IDs + reference to status/IMPLEMENTATION-STATUS.md):
______________________________________________________________

QA ____________   Product ____________
```

> **Known drift is not a P0 failure.** The `/clinic/ipd`, `/clinic/wards`, `/clinic/beds` aliases
> and the missing OPD/PHARMACY module gates are **recorded, accepted, open items**. Track their
> status per release; a _change_ in behaviour, in either direction, is worth reporting.
