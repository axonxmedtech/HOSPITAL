# 08 — OPERATION THEATRE JOURNEY

**Cases:** `TC-E2E-073` … `TC-E2E-077` · Tenant **HOSPITAL_A** · module **`OT`** (hospital-only, `@RequireModule("OT")` + `@TenantType`)

A surgery always begins from an **IPD admission**. There is no standalone surgery.

## State table

| #   | Actor          | Action                                                           | `Surgery.status`  | Bed / ward               | Evidence     |
| --- | -------------- | ---------------------------------------------------------------- | ----------------- | ------------------------ | ------------ |
| 1   | DOCTOR         | request surgery from the IPD case                                | **`REQUESTED`**   | ward bed `occupied`      | screenshot   |
| 2   | RECEPTIONIST   | schedule (operator + optional anaesthetist + single-bed OT ward) | **`SCHEDULED`**   | OT bed reserved/occupied | screenshot   |
| 3   | NURSE          | fill consent + pre-op NABH forms                                 | `SCHEDULED`       | —                        | printed form |
| 4   | RECEPTIONIST   | Start                                                            | **`IN_PROGRESS`** | OT bed `occupied`        | screenshot   |
| 5   | RECEPTIONIST   | Complete                                                         | **`COMPLETED`**   | **OT bed → `cleaning`**  | screenshot   |
| 6   | NURSE_INCHARGE | mark OT bed cleaned                                              | `COMPLETED`       | OT bed → `available`     | audit row    |

> `SurgeryStatus` has **9** values. Record every one you can reach and the exact transition that
> produced it; the cancel/postpone semantics are **`NEEDS_PRODUCT_CONFIRMATION`** unless the UI
> states them. Do not invent transitions.

---

### TC-E2E-073 — ⭐ Request → schedule → start → complete, with the bed following

`HOSPITAL_A · DOC → REC → NUR_INC · OT · Critical`
**Steps:** `TC-HOT-005` · `TC-HOT-008` · `TC-HOT-012` · `TC-HOT-016` · `TC-HOT-019` · `TC-HWB-014`.
**E2E assertion:** fill the state table above. On **Complete**, the OT bed must go to **`cleaning`**, never straight to `available`, and a `bed_status_audits` row must exist for the transition. The patient remains admitted (`ADMITTED`) throughout — surgery does not discharge anyone.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-074 — Scheduling guards

`HOSPITAL_A · REC + OTI · OT · High`
**Steps:** `TC-HOT-009` · `TC-HOT-010` · `TC-HOT-011` · `TC-HWB-010` · `TC-HAS-022` · `TC-HAS-023` · `TC-PERM-012`.
**E2E assertion:** an OT ward is identified **by its name containing "OT"** and is single-bed; scheduling a second surgery into an occupied OT slot, scheduling into a past time, and scheduling without an operator are each refused with a clear message and **no state change**. Confirm a **free-text operator** (not a registered doctor) is accepted, since that is the documented design.
Then bring in the **`OT_INCHARGE`** role: with the OT-incharge toggle on, log in as that user and
confirm the OT permissions grid actually drives what they can do — revoke `OT_VIEW` and their
**API** access must be denied, not merely the tab hidden.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-075 — Out-of-order transitions are refused

`HOSPITAL_A · REC · OT · Critical`
**Steps:** `TC-HOT-013` · `TC-HOT-014` · `TC-HOT-024` (double-click Start / Complete).
**E2E assertion:** Complete before Start, Start twice, Complete twice, and Start on a `REQUESTED` (unscheduled) surgery are all refused. After each refusal the status **and the OT bed state** are unchanged. A double-click that produces two bed transitions or two audit rows is **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-076 — The 15 NABH forms: fill → save → reopen → print

`HOSPITAL_A · NURSE (+ DOC) · OT · High`
**Steps:** `TC-HOT-026` … `TC-HOT-031` · `TC-HAS-015` · `TC-HAS-016` (Files & Access).
**E2E assertion:** walk **every form in `surgeryFormsRegistry`** — for each: fill, save, navigate away, reopen, and confirm the values persisted and the print output carries the hospital header and the correct patient. Then turn one form **off** in Files & Access and confirm its tab disappears **for everyone**; set one to `DOCTOR`-only and confirm the nurse sees existing records **read-only with no entry form**, and that the **API write is refused too** (`assertCanEdit` is server-side). A UI-only block is **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-077 — OT gating and isolation

`HOSPITAL_A + B + CLINIC_A + PHARMACY_A · ADM · Isolation · Critical`
**Steps:** `TC-HOT-035` · `TC-MOD-016` · `TC-ISO-031` · `TC-PERM-019`.
**E2E assertion:** revoke `OT` from HOSPITAL_A's plan — **without re-login** the OT tabs vanish and every `/hospital/ot/**` call returns 403, because `@RequireModule` reads the **live hospital row**, not the JWT. Confirm `/clinic/**` and `/pharmacy/**` have **no** OT surface at all (`@TenantType` is present on OT), and that HOSPITAL_B cannot read or transition HOSPITAL_A's surgery by id.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
