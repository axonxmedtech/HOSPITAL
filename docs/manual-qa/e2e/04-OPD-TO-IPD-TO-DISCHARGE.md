# 04 — OPD → IPD → DISCHARGE (the inpatient spine)

**Cases:** `TC-E2E-025` … `TC-E2E-036` · Tenant **HOSPITAL_A**

The longest state machine in the product, touching five actors and four entities at once. The
happy path is `TC-E2E-025`–`029`; the failure variants are `TC-E2E-030`–`036` and matter more.

## Master state table

| #   | Stage             | Actor     | Entity           | Identifier              | Before      | Action         | After                                      | Evidence    |
| --- | ----------------- | --------- | ---------------- | ----------------------- | ----------- | -------------- | ------------------------------------------ | ----------- |
| 1   | OPD               | S-REC     | OPD              | `____`                  | —           | create         | **`QUEUED`**                               | screenshot  |
| 2   | Consultation      | S-DOC     | OPD              | same                    | `QUEUED`    | complete       | **`COMPLETED`**                            | case paper  |
| 3   | Request           | S-DOC     | OPD              | same                    | `COMPLETED` | Admit to IPD   | `ipdAdmitRecommended = true`               | screenshot  |
| 4   | Queue             | S-REC     | IPD ▸ requested  | —                       | —           | badge          | count +1                                   | screenshot  |
| 5   | Admission         | S-REC     | IPD              | `____` / IPD no. `____` | —           | admit `GA-01`  | **`ADMITTED`**                             | screenshot  |
| 6   | Bed               | system    | `GA-01`          | `____`                  | `available` | assign         | **`occupied`**                             | screenshot  |
| 7   | OPD link          | system    | OPD              | same                    | `COMPLETED` | admission      | **`IN_IPD`**                               | screenshot  |
| 8   | Assignment        | S-NI      | assignment       | —                       | Unassigned  | assign nurse   | assigned                                   | screenshot  |
| 9   | Care              | S-NUR     | vitals/notes/MAR | `____`                  | —           | record         | saved                                      | screenshot  |
| 10  | Charges           | S-DOC     | Rx + items       | —                       | —           | administer     | on IPD bill                                | screenshot  |
| 11  | Transfer _(opt.)_ | S-DOC/REC | bed              | `GA-01`→`GA-02`         | `occupied`  | change bed     | `GA-01`→**`cleaning`**, `GA-02`→`occupied` | screenshot  |
| 12  | Plan              | S-DOC     | IPD              | same                    | `ADMITTED`  | plan discharge | still `ADMITTED`                           | screenshot  |
| 13  | Discharge         | S-REC     | IPD              | same                    | `ADMITTED`  | confirm        | **`DISCHARGED`**                           | screenshot  |
| 14  | Bed               | system    | current bed      | —                       | `occupied`  | discharge      | **`cleaning`**                             | screenshot  |
| 15  | Clean             | S-NI      | bed              | —                       | `cleaning`  | mark cleaned   | **`available`**                            | screenshot  |
| 16  | Final bill        | S-REC     | IPD bill         | `____`                  | `PENDING`   | pay            | **`PAID`**                                 | receipt PDF |

---

## Happy path

### TC-E2E-025 — Request → admission: the handoff carries the patient and the OPD

`HOSPITAL_A · DOC → REC · IPD · Critical`
**Steps:** `TC-HD-023` · `TC-HR-039`.
**E2E assertion:** the request appears in reception's `requested` sub-tab with **this patient's name and `PAT` number**; admitting creates an admission carrying the same identity plus a **unique IPD number**; the source OPD case moves to **`IN_IPD`**; the doctor who requested is recorded on the admission.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-026 — Admission → bed: one bed, four views, one truth

`HOSPITAL_A · REC + ADM + NI · Beds · Critical`
**Steps:** `TC-HWB-010` steps 1–3 · `TC-HWB-015`.
**E2E assertion:** immediately after admission `GA-01` reads **`occupied`** in admin Wards & Beds, incharge Beds, the ICU/bed board and the Overview beds card, **and is absent from reception's admit picker**. Any view still showing `available` is **Critical** — it is the precondition for a double admission.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-027 — Admission → nursing: visibility follows assignment, not admission

`HOSPITAL_A · NI + NUR + DOC · Nursing · Critical`
**Steps:** `TC-HNI-006` · `TC-HN-004` · `TC-HN-008`.
**E2E assertion:** the nurse sees the patient **only** after assignment; every record she writes is attributed to her and appears immediately in the doctor's `/ipd/:id` under the matching sub-tab; the incharge sees the same records. Records written before assignment must be impossible (`TC-HN-005`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-028 — Stay → money: every clinical event lands on the IPD bill

`HOSPITAL_A · DOC + ADM + REC · Billing · Critical`
**Steps:** `TC-HB-010` · `TC-HA-035`.
**E2E assertion:** each IPD prescription and each administered hospital item produces **exactly one** bill line; hospital-inventory stock falls by exactly the administered quantity; the running IPD total equals the sum of the lines at every point.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-029 — ⭐ Discharge → bed release: the `cleaning` gate

`HOSPITAL_A · DOC → REC → NI · IPD/Beds · Critical`
**Steps:** `TC-HD-025` · `TC-HB-011` · `TC-HNI-014` · `TC-HWB-010` steps 4–7.
**E2E assertion:** the sequence is exactly `occupied → cleaning → available`. Between discharge and "mark cleaned" the bed **must not** be offered in the admit picker and **must not** be admissible via API. Only after the incharge marks it cleaned may a new patient occupy it. A bed that jumps straight to `available` is **Critical** — it puts a patient in an uncleaned bed.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## Failure variants (these matter more than the happy path)

### TC-E2E-030 — No bed available

`HOSPITAL_A · RECEPTIONIST · IPD · Critical`
**Steps:** occupy every bed in the ward, then attempt an admission (UI and API) — `TC-HR-040(a)`.
**E2E assertion:** the bed picker is empty with a readable message; the API refuses; **no admission record is created** and no bed changes state. Verify the request still sits in `requested` and can be admitted later once a bed frees.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-031 — ⭐ Occupied bed: two receptionists, one bed

`HOSPITAL_A · RECEPTIONIST ×2 · IPD/Beds · Critical`
**Steps:** `TC-HR-040(b)` and `TC-HWB-022`.
**E2E assertion:** a direct API admission onto an `occupied` bed returns **409** and creates nothing. With two tabs submitting together, **exactly one** admission succeeds; the bed is occupied **once**; the loser gets a clear conflict. Two admissions on one bed is the worst inpatient outcome in the product — **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-032 — Duplicate admission for an already-admitted patient

`HOSPITAL_A · RECEPTIONIST · IPD · Critical`
**Steps:** `TC-HR-041`.
**E2E assertion:** refused via UI and API; the patient has exactly **one** active admission; the second attempt consumes **no** bed. Check that the first admission's bed is untouched.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-033 — Failed / interrupted admission

`HOSPITAL_A · RECEPTIONIST · IPD · Critical`
**Steps:** `TC-HR-044` — stop the backend mid-admit, restart, retry; separately double-click Admit.
**E2E assertion:** **no half-admission**: either an admission exists and its bed is `occupied`, or neither. A bed left `occupied` with no admission, or an admission with an `available` bed, is **Critical** (orphan state). A double click produces **one** admission.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-034 — Refresh and re-login mid-stay

`HOSPITAL_A · REC + NUR + DOC · IPD · High`
**Steps:** at stages 8, 10 and 12 press F5, then log out and back in as each actor; re-open `/ipd/:id`.
**E2E assertion:** F5 returns to the dashboard's default tab (expected, `TC-AUTH-015`) but **`/ipd/:id` is a real route and reloads the same admission**; every nursing record, prescription and charge persists exactly; nothing needs re-entering.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-035 — Wrong patient and wrong tenant

`HOSPITAL_A + B · REC + DOC + NUR · IPD · Critical`
**Steps:** `TC-ISO-020` (`/ipd/:id` from HOSPITAL_B) · `TC-ISO-021` (cross-tenant discharge/transfer) · `TC-ISO-022` (nursing records against a foreign admission) · plus the family case: with the Kale family admitted, confirm the admission is against **the child**, not the father.
**E2E assertion:** every cross-tenant read is 403/404 with no clinical content; every cross-tenant write is refused **and HOSPITAL_A is verified unchanged**; the admission, its nursing records and its bill all name the correct family member.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-036 — Invalid status transitions

`HOSPITAL_A · DOC + REC · IPD · Critical`
**Steps:** `TC-HD-026` — confirm discharge twice; change bed after discharge; administer medication to a discharged admission; discharge an admission that was never admitted.
**E2E assertion:** every one refused with 400/409; **no second discharge event**; the bed is released **once** and is not driven back into `cleaning` by the refused calls; the final bill does not grow after settlement (`TC-HB-012`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
