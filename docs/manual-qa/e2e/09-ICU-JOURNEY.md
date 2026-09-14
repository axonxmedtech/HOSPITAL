# 09 — ICU JOURNEY

**Cases:** `TC-E2E-078` … `TC-E2E-081` · Tenant **HOSPITAL_A** · module **`ICU`** (hospital-only; `@RequireModule` **and** `@TenantType` both present)

ICU is one of only three areas carrying `@TenantType`, so its tenant restriction is genuinely
enforced rather than merely conventional — prove both halves.

### TC-E2E-078 — Admit → monitor → step down

`HOSPITAL_A · REC + DOC + NUR · ICU · Critical`
**Steps:** `TC-HICU-002` · `TC-HICU-007` · `TC-HICU-011` · `TC-HICU-014` · `TC-HWB-011`.
**E2E assertion:** an ICU admission occupies an **ICU bed** and the patient shows as `ADMITTED`; monitoring entries save and reopen with the recording user and timestamp; transferring the patient to a general ward frees the ICU bed to **`cleaning`** (not `available`) and occupies the destination bed in **one** movement, with the admission record continuous — no discharge/re-admit gap. The bill follows the patient, not the bed.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-079 — Capacity and double-occupancy guards

`HOSPITAL_A · RECEPTIONIST · ICU · Critical`
**Steps:** `TC-HICU-017` · `TC-HICU-001` · `TC-HWB-017` (two receptionists, last bed).
**E2E assertion:** admitting into an `occupied`, `cleaning` or `maintenance` ICU bed is refused; admitting when ICU is full is refused with a clear message; two simultaneous admissions to the **last** ICU bed must not both succeed. If they do, record it **Critical** with both admission ids.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-080 — ICU discharge closes everything

`HOSPITAL_A · DOC + REC · ICU · Critical`
**Steps:** `TC-HICU-016` · `TC-HICU-017` · `TC-HB-012`.
**E2E assertion:** on discharge the status becomes **`DISCHARGED`**, the ICU bed becomes **`cleaning`**, the bill is settleable, and post-discharge clinical writes are refused. The discharged patient no longer appears in the ICU census, but the full record remains **readable and printable**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-081 — ⭐ ICU gating, tenant type, and isolation

`HOSPITAL_A + B + CLINIC_A + PHARMACY_A · ADM · Isolation · Critical`
**Steps:** `TC-HICU-018` · `TC-MOD-017` · `TC-PERM-020` · `TC-ISO-028`.
**E2E assertion:** three separate proofs, recorded separately —

1. **Module:** revoke `ICU` → tabs vanish and `/hospital/icu/**` returns 403 **without re-login**;
2. **Tenant type:** a CLINIC or PHARMACY token is refused by the ICU handlers (`@TenantType`), even with an admin role;
3. **Tenant data:** HOSPITAL_B cannot read, monitor, transfer or discharge HOSPITAL_A's ICU admission by id.
   Any one of the three failing is **Critical**.
   **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
