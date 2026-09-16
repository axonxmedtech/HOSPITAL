# 16 — TENANT ISOLATION, END TO END

**Cases:** `TC-E2E-107` … `TC-E2E-112` · Tenants HOSPITAL_A, HOSPITAL_B, CLINIC_A, PHARMACY_A

Isolation is the product's single most important property. Tranche 1 tests it endpoint by endpoint
(`cross-tenant/01-TENANT-ISOLATION`, 58 cases); **this journey tests it after a full day of real
work has created real records in both tenants**, which is when leaks actually appear.

## Method — the "captured id" sheet

Before starting, run a working day in **HOSPITAL_A** and write down the real ids:

| Entity        | HOSPITAL_A id | Entity         | HOSPITAL_A id |
| ------------- | ------------- | -------------- | ------------- |
| patient       | ____          | IPD admission  | ____          |
| OPD case      | ____          | bed            | ____          |
| appointment   | ____          | ward           | ____          |
| prescription  | ____          | surgery        | ____          |
| bill          | ____          | nursing record | ____          |
| pharmacy sale | ____          | document       | ____          |
| batch         | ____          | staff user     | ____          |

Then, as **HOSPITAL_B's admin**, attempt **read, update and delete on every id above**.
`hospitalId` always comes from the **JWT** (`SecurityContextHelper.getCurrentHospitalId()`) and
`setHospitalId()` **overwrites client input**, so a supplied `hospitalId` in a body must be ignored.

---

### TC-E2E-107 — ⭐ The captured-id sweep: read

`HOSPITAL_A + B · ADM · Isolation · Critical`
**Steps:** the sheet above · `TC-ISO-001` … `TC-ISO-020`.
**E2E assertion:** every GET by id returns **403 or 404 — never the record**. Record the actual status per row. A **404 is acceptable**; a 200 with data is **Critical**. Also confirm no error message leaks the other tenant's name, patient name or hospital id.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-108 — ⭐ The captured-id sweep: write and delete

`HOSPITAL_A + B · ADM · Isolation · Critical`
**Steps:** `TC-ISO-021` … `TC-ISO-040`.
**E2E assertion:** every PUT/PATCH/POST/DELETE against another tenant's id is refused — **and then you must go back to HOSPITAL_A and re-read the record to prove it is unchanged**. A refusal that still mutated data is worse than a leak. Include: editing the patient, paying the bill, discharging the admission, freeing the bed, selling the batch, transitioning the surgery, deactivating the staff user.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-109 — Client-supplied `hospitalId` is always ignored

`HOSPITAL_B · ADM · Isolation · Critical`
**Steps:** `TC-ISO-007` · `TC-ISO-012` · `TC-API-007`.
**E2E assertion:** as HOSPITAL_B, create a patient, an appointment and a bill with **`"hospitalId": <HOSPITAL_A's id>`** in the body, and with `?hospitalId=` in the query string. Every created record must belong to **HOSPITAL_B**. Then log into HOSPITAL_A and confirm **nothing new appeared there**. A record landing in HOSPITAL_A is **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-110 — Aggregates, search, lists and exports never include another tenant

`HOSPITAL_A + B + CLINIC_A + PHARMACY_A · ADM · Isolation · Critical`
**Steps:** `TC-VIS-001` … `TC-VIS-022` · `TC-HB-018` · `TC-PR-008`.
**E2E assertion:** the most dangerous leak shows **no name on screen** — a count. In HOSPITAL_B verify that dashboard counts, revenue, occupancy, patient **search by a HOSPITAL_A phone number or `custom_id`**, dropdown lists (doctors, wards, medicines), reports and **CSV exports** all exclude HOSPITAL_A entirely. Search by the exact HOSPITAL_A phone must return **zero rows** — this is also the cross-tenant half of the patient-identity journey.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-111 — Isolation across tenant _types_, and the known ungated aliases

`HOSPITAL_A + CLINIC_A + PHARMACY_A · ADM · Isolation · Critical`
**Steps:** `TC-ISO-053` … `TC-ISO-058` · `TC-PERM-018` … `TC-PERM-020` · `TC-CD-008` … `TC-CD-014`.
**E2E assertion:** a CLINIC token on `/hospital/**` and a PHARMACY token on `/clinic/**` are refused. Then exercise each **documented ungated alias** — `/clinic/ipd` (13), `/clinic/wards` (7), `/clinic/beds` (5), `/pharmacy/opd` (11), `/pharmacy/ipd` (13), `/pharmacy/beds` (5), `/pharmacy/wards` (7), `/pharmacy/doctors` (11), `/pharmacy/receptionists` (6) — and record the **actual** response for each. **Reachable-but-unsupported is the known state** (log against `status/IMPLEMENTATION-STATUS.md`); **returning another tenant's data is a new Critical bug.** Keep those two outcomes strictly separate in your report.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-112 — Super Admin is the only legitimate cross-tenant actor

`PLATFORM + HOSPITAL_A · SA + ADM · Isolation · Critical`
**Steps:** `TC-SA-040` … `TC-SA-048` · `TC-AUTH-006` · `TC-PERM-024`.
**E2E assertion:** `SUPER_ADMIN` may manage **hospitals, plans and billing** across tenants, but confirm what it can reach of **clinical** data and record it exactly (`NEEDS_PRODUCT_CONFIRMATION` if the intent is unclear — do not call it a bug without a stated rule). In the other direction, a `HOSPITAL_ADMIN` token on **`/platform/**`** must be refused on **every** endpoint, and there is **no impersonation feature** — an admin cannot obtain a Super Admin token or a token for another tenant by any route you can find.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
