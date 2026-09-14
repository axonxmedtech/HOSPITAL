# 12 — BED & WARD LIFECYCLE

**Cases:** `TC-E2E-093` … `TC-E2E-097` · Tenant **HOSPITAL_A** (hospital-only — clinic and pharmacy have **no supported** ward/bed surface)

**All** bed status writes go through `BedStatusService` and are audited into **`bed_status_audits`**.
Every case below therefore has two halves: the state, **and the audit row that explains it**.

## The four states (`entity/BedStatus`, lowercase in the data)

`available` · `occupied` · `cleaning` · `maintenance`

## Full cycle

| #   | Trigger                 | From → To                       | Audit row? | Evidence   |
| --- | ----------------------- | ------------------------------- | ---------- | ---------- |
| 1   | create bed              | — → `available`                 | ____       | screenshot |
| 2   | IPD admission           | `available` → **`occupied`**    | ✅         | screenshot |
| 3   | transfer out            | `occupied` → **`cleaning`**     | ✅         | screenshot |
| 4   | incharge marks cleaned  | `cleaning` → **`available`**    | ✅         | audit row  |
| 5   | discharge (other bed)   | `occupied` → **`cleaning`**     | ✅         | screenshot |
| 6   | OT complete             | `occupied` → **`cleaning`**     | ✅         | screenshot |
| 7   | send to maintenance     | `available` → **`maintenance`** | ✅         | screenshot |
| 8   | return from maintenance | `maintenance` → **`available`** | ✅         | screenshot |

---

### TC-E2E-093 — ⭐ The full eight-step cycle, every step audited

`HOSPITAL_A · ADM + REC + NUR_INC · Wards/Beds · Critical`
**Steps:** `TC-HWB-001` · `TC-HWB-005` · `TC-HWB-012` · `TC-HWB-014` · `TC-HWB-015` · `TC-HWB-018`.
**E2E assertion:** complete the table. The decisive rule: **vacating a bed never goes straight to `available`** — discharge, transfer and OT completion all land on **`cleaning`**, and only the incharge's "cleaned" action returns it to `available`. Every transition has exactly **one** `bed_status_audits` row naming the actor, the from/to states and the time. A missing audit row is **High**; a skipped `cleaning` step is **Critical** (infection-control requirement).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-094 — Only an `available` bed can be occupied

`HOSPITAL_A · RECEPTIONIST · Wards/Beds · Critical`
**Steps:** `TC-HWB-009` · `TC-HWB-010` · `TC-HWB-011` · `TC-HWB-017`.
**E2E assertion:** admission into an `occupied`, `cleaning` or `maintenance` bed is refused with a clear message and **no audit row**. Two receptionists taking the **last available bed** at once must not both succeed — record both admission ids if they do (**Critical**). Reception may only admit into a ward that **has an incharge** and an available bed; a ward with no incharge must be refused or hidden — record which.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-095 — Ward configuration changes do not orphan patients

`HOSPITAL_A · HOSPITAL_ADMIN · Wards/Beds · High`
**Steps:** `TC-HWB-002` · `TC-HWB-003` · `TC-HWB-006` · `TC-HWB-007` · `TC-HWB-020`.
**E2E assertion:** with a patient admitted, attempt to **delete the bed**, **delete the ward**, and **reassign the ward's incharge**. Deleting an occupied bed or a ward containing admitted patients must be **refused** — if either succeeds, the admission is orphaned and that is **Critical**. Reassigning the incharge must move the ward's scope to the new incharge **and remove it from the old one** (`myWardIds()` on both), leaving all nursing records intact and attributed to whoever actually wrote them.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-096 — Occupancy figures agree everywhere

`HOSPITAL_A · ADM + REC + NUR_INC · Wards/Beds · High`
**Steps:** `TC-HWB-021` · `TC-HWB-022` · `TC-HAC-024` · `TC-HNI-031`.
**E2E assertion:** with a known fixture (say 10 beds: 3 `occupied`, 1 `cleaning`, 1 `maintenance`, 5 `available`) the **admin Overview**, the **ward/bed screen**, the **incharge dashboard** and any **report** must show the _same_ counts. Confirm a `cleaning` or `maintenance` bed is **not counted as available** anywhere — that is the arithmetic error most likely to be present.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-097 — Bed/ward isolation and gating

`HOSPITAL_A + B + CLINIC_A + PHARMACY_A · ADM · Isolation · Critical`
**Steps:** `TC-ISO-023` … `TC-ISO-026` · `TC-CD-011` · `TC-MOD-011`.
**E2E assertion:** HOSPITAL_B cannot read, occupy, free or delete HOSPITAL_A's bed by id. Then exercise the **known ungated aliases** `/clinic/beds` (5), `/clinic/wards` (7), `/pharmacy/beds` (5), `/pharmacy/wards` (7) with a clinic/pharmacy token: record the **actual** response for each. These are documented as **ungated drift** in `status/IMPLEMENTATION-STATUS.md` — a 200 with data is expected-but-wrong; log it against the existing entry rather than as a new bug, **unless it returns another tenant's data**, which is **Critical** and new.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
