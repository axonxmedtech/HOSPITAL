# 17 — MODULE GRANT / REVOCATION, END TO END

**Cases:** `TC-E2E-113` … `TC-E2E-117` · PLATFORM + HOSPITAL_A + CLINIC_A + PHARMACY_A

Two facts govern every case here, both verified in code:

1. **`@RequireModule` reads the LIVE hospital row, not the JWT** — so a revocation takes effect on
   the **next request**, with **no re-login**. That is the behaviour to prove, in both directions.
2. **`EntitlementRegistry` declares only — "it enforces nothing."** So a module being _sellable_
   for a tenant type tells you nothing about whether it is _enforced_. **Always verify the backend,
   not just the UI.**

Enforcement is uneven and that is **known**: `@RequireModule` appears on **47 handlers** only.
**OPD and PHARMACY have no `@RequireModule` anywhere; IPD has exactly one.** Those are recorded
in `status/IMPLEMENTATION-STATUS.md` — confirm them, do not re-raise them as new bugs.

## The revocation matrix — fill it in

| Module               | UI tabs hide? | API 403?           | Re-login needed? | Data survives? | Verdict |
| -------------------- | ------------- | ------------------ | ---------------- | -------------- | ------- |
| `OT`                 | ____          | ____               | ____             | ____           | ____    |
| `ICU`                | ____          | ____               | ____             | ____           | ____    |
| `NURSING`            | ____          | ____               | ____             | ____           | ____    |
| `BILLING`            | ____          | ____               | ____             | ____           | ____    |
| `APPOINTMENTS`       | ____          | ____               | ____             | ____           | ____    |
| `REPORTS`            | ____          | ____               | ____             | ____           | ____    |
| `MEDICAL_INVENTORY`  | ____          | ____               | ____             | ____           | ____    |
| `HOSPITAL_INVENTORY` | ____          | ____               | ____             | ____           | ____    |
| `IPD`                | ____          | **1 handler only** | ____             | ____           | ____    |
| `OPD`                | ____          | **none expected**  | ____             | ____           | ____    |
| `PHARMACY`           | ____          | **none expected**  | ____             | ____           | ____    |

---

### TC-E2E-113 — ⭐ Revoke live: tabs vanish and the API refuses, without re-login

`PLATFORM → HOSPITAL_A · SA + ADM · Modules · Critical`
**Steps:** `TC-MOD-013` … `TC-MOD-021`.
**E2E assertion:** with a HOSPITAL_A user **logged in and working in another browser**, revoke `OT`. Their next navigation must lose the tab **and** their next `/hospital/ot/**` call must 403 — **with the same token**. If it takes a re-login, the live-row read is not working and that is **High**. Repeat for `ICU`, `NURSING`, `BILLING`, `REPORTS` and fill the matrix.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-114 — ⭐ Re-grant restores access and **no data was lost**

`PLATFORM → HOSPITAL_A · SA + ADM · Modules · Critical`
**Steps:** `TC-MOD-022` · `TC-MOD-023`.
**E2E assertion:** before revoking, record the ids of real records in each module (surgery, ICU admission, nursing records, bills). After revoking and re-granting, every one must still exist, unchanged, with its original attribution and timestamps. **Revocation is a gate, never a delete** — any lost record is **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-115 — The known enforcement gaps behave as recorded

`HOSPITAL_A · SA + ADM · Modules · High`
**Steps:** `TC-MOD-010` … `TC-MOD-012` · `TC-MOD-024` … `TC-MOD-025`.
**E2E assertion:** revoke `OPD`, then `PHARMACY`, then `IPD`. Record for each whether the **UI** hides it (it reads `user.modules`, so it likely does) and whether the **API still answers** (expected: **yes**, because there is no `@RequireModule`). Log these against the **existing** `status/IMPLEMENTATION-STATUS.md` entries with the exact endpoints you reached. Do **not** file them as new bugs, and do **not** mark the case FAIL for behaving as documented — mark it PASS with the evidence attached, and FAIL only if it differs from what is documented.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-116 — Implied modules

`PLATFORM → HOSPITAL_A + PHARMACY_MULTI · SA + ADM · Modules · High`
**Steps:** `TC-MOD-004` … `TC-MOD-006`.
**E2E assertion:** exercise each `IMPLIED_BY` relation — **`APPOINTMENTS` → `OPD`**, **`IPD` → `WARDS`, `BEDS`, `CLINICAL_RECORDS`**, **`MULTI_PHARMACY` → `PHARMACY_BRANCH`**. Grant only the parent and record whether the implied children actually work; then revoke the parent and record whether the children stop. Because the registry **enforces nothing**, the honest outcome may be "the declaration and the behaviour disagree" — record exactly which, per relation, as `NEEDS_PRODUCT_CONFIRMATION` rather than guessing the intent.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-117 — Unsellable modules cannot be granted to the wrong tenant type

`PLATFORM → CLINIC_A + PHARMACY_A · SA + ADM · Modules · Critical`
**Steps:** `TC-MOD-002` · `TC-MOD-003` · `TC-MOD-007` · `TC-MOD-026` · `TC-MOD-027` · `TC-SA-030` … `TC-SA-034`.
**E2E assertion:** attempt to grant `OT`, `ICU`, `NURSING` and `IPD` to **CLINIC_A**, and anything beyond the pharmacy set to **PHARMACY_A**. Record whether the platform UI **offers** them at all and whether the API **accepts** the grant. If a grant is accepted, follow through and check whether the feature then becomes reachable — for `OT` and `ICU` the `@TenantType` guard should refuse regardless, which is the safety net. A clinic that actually gains a working ICU is **Critical**; a grant that is merely _stored_ but unreachable is **High** and belongs in `NEEDS_PRODUCT_CONFIRMATION`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
