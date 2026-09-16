# 11 — HOSPITAL CROSS-ROLE WORKFLOWS (end-to-end journeys)

**Baseline:** `aa143a7` · **Cases:** `TC-HX-001` … `TC-HX-016` · Tenant HOSPITAL_A unless stated

Each journey is **one test case** executed by several users in sequence. Every step names **which
session** performs it.

## Session convention

| Label     | Browser context                   | Account                          |
| --------- | --------------------------------- | -------------------------------- |
| **S-ADM** | Chrome tab 1                      | `admin.hospa@qa.test`            |
| **S-REC** | Chrome tab 2                      | `rec.hospa@qa.test`              |
| **S-DOC** | Chrome tab 3                      | `doc1.hospa@qa.test`             |
| **S-NUR** | Chrome tab 4                      | `nurse.hospa@qa.test`            |
| **S-NI**  | Chrome tab 5                      | `ni.hospa@qa.test`               |
| **S-PHA** | Chrome tab 6                      | `pharm.hospa@qa.test`            |
| **S-OTI** | Chrome tab 7                      | `ot.hospa@qa.test`               |
| **S-SA**  | **Incognito window**              | Super Admin at `/platform/login` |
| **S-B**   | **Second browser** (Firefox/Edge) | `rec.hospb@qa.test` — HOSPITAL_B |

> Tokens live in `sessionStorage`, so tabs are independent sessions (`TC-AUTH-013`). Use a
> **separate browser** for HOSPITAL_B so an accidental logout cannot contaminate the isolation
> journeys. Keep all sessions open for the whole journey — the point is to observe propagation.

---

## JOURNEY H1 — New patient → appointment → consultation → billing → follow-up

### TC-HX-001 — H1 happy path, six roles, one patient

`HOSPITAL_A · ADM+REC+DOC · multi · Critical`
**Pre:** full plan; Bill Payment `LAST`; Billing Handler `RECEPTIONIST`; fees set (777 / case paper).
**Steps**

1. **S-ADM** Staff ▸ Doctors → confirm `Dr Meera Kulkarni` active. Finance ▸ Fees → note the consultation fee.
2. **S-REC** Patients → **Add Patient** `Journey One`, phone `9900070001`, DOB 1990-01-01, Female → Save. **Record `id`, `publicId`, `customId`.**
3. **S-ADM** Patients → confirm the new patient is visible **without re-login**.
4. **S-REC** Appointments → **Add Appointment** → existing patient `Journey One` → Dr Meera → tomorrow → first free slot → **Schedule**.
5. **S-DOC** Appointments → the appointment is listed for this doctor.
6. **S-REC** OPD → **Add OPD** → existing patient → Dr Meera → vitals BP `120/80`, temp, pulse → Problem `Fever` → **Create OPD**. Record the case id.
7. **S-DOC** Overview/queue → the case appears (no manual refresh).
8. **S-DOC** **Start Consultation** → header shows `Journey One` + `PAT<id>` → Symptoms, Diagnosis, Treatment Notes → **Add Medicine** `QA Paracetamol 500` → lab `CBC` → follow-up **+3 days** → **Complete Consultation**.
9. **S-DOC** **Print Prescription** and **Print Case Paper** → open both PDFs.
10. **S-REC** Billing → the bill is `PENDING` with the expected amount → **Mark Paid** → **Print** receipt.
11. **S-ADM** Overview → today's collection increased by exactly that amount; OPD consultation count +1.
12. **S-REC** Follow-ups → the row sits under **Upcoming**; open the source consultation.
13. **S-ADM** Reports & Analytics and Audit Logs → the visit and every action are recorded.
    **Expected**

- One patient, one appointment, one OPD, **one** consultation, **one** bill, one prescription, one follow-up, one lab order.
- Every screen shows `Journey One` / `PAT<id>` — never another patient.
- PDFs carry the **patient's** identifier, the hospital header and the correct doctor.
- Collection and counts reconcile exactly at step 11.
  **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HX-002 — H1 under Bill Payment = FIRST

`HOSPITAL_A · ADM+REC+DOC · Billing · Critical`
**Steps:** **S-ADM** set Bill Payment `Before OPD`; repeat H1 steps 6–11 for a new patient.
**Expected:** payment fields required at OPD entry; the bill is `PAID` from creation; the doctor's completion creates **no second bill**; restore the setting afterwards.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## JOURNEY H2 — OPD → IPD admission → nursing → discharge → bed release

### TC-HX-003 — H2 full inpatient lifecycle

`HOSPITAL_A · REC+DOC+NI+NUR · multi · Critical`
**Pre:** `General Ward A` with incharge Latha and beds `GA-01`…`GA-03` all `Available`; _Separate Nurse Login_ **ON**; Reena is a ward nurse.
**Steps**

1. **S-REC** create an OPD for `Journey One`. **S-DOC** start the consultation → **Admit to IPD** → reason/diagnosis → submit.
2. **S-REC** IPD ▸ `requested` → badge shows 1 → **Admit** → ward `General Ward A` → bed `GA-01` → admission type → **Admit**. Record the admission id and IPD number.
3. **S-ADM** Wards & Beds → `GA-01` **Occupied**. **S-NI** Beds → same. **S-REC** re-open the admit picker → `GA-01` **absent**.
4. **S-NI** **Unassigned Patients** → the patient is listed → assign to **Reena**.
5. **S-NUR** **My Patients** → the patient appears; open them → header shows the correct name and `PAT` id.
6. **S-NUR** record **Vitals**, a **Note**, an **Initial Assessment**, a **Sugar Chart** entry.
7. **S-DOC** open `/ipd/:id` → all four records are visible → add an IPD prescription.
8. **S-NUR** **Medication** → administer that prescription.
9. **S-ADM** Inventory ▸ Hospital Inventory → administer 2 units of a master item to this admission → stock falls by 2.
10. **S-DOC** **plan discharge** with a summary. **S-REC** **confirm discharge**.
11. **S-ADM/S-NI** → `GA-01` is now **Cleaning Required**; **S-REC** admit picker still excludes it.
12. **S-NI** Beds → **Mark Bed Cleaned** → `Confirm` → `GA-01` **Available**.
13. **S-REC** admit a different patient into `GA-01` → succeeds.
14. **S-REC** open the final IPD bill → it includes the prescription and the hospital items → take payment → print.
    **Expected**

- Bed states follow **Available → Occupied → Cleaning Required → Available** and never skip cleaning.
- The nurse sees the patient **only after assignment**.
- Every nursing record is attributed to Reena and visible to the doctor.
- Stock decremented once; the final bill reconciles with the administered items.
- Every transition appears in Audit Logs and bed history.
  **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HX-004 — H2 variant: transfer between beds and wards

`HOSPITAL_A · REC+DOC+NI · IPD · Critical`
**Steps:** during H2 (before discharge) **change bed** `GA-01` → `GA-02`, then `GA-02` → a bed in `Ward Z` (a different incharge's ward).
**Expected:** source bed → cleaning each time; destination occupied; **ward change moves the patient between incharges' scopes** — Latha loses them, Sister Two gains them; the assigned nurse's visibility — record what happens (`NEEDS_PRODUCT_CONFIRMATION` if the old ward's nurse keeps access).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HX-005 — H2 variant: Separate Nurse Login OFF

`HOSPITAL_B · REC+NI · Nursing · High`
**Steps:** in HOSPITAL_B (setting OFF) run H2 steps 1–8 with the **incharge** recording care and choosing a **Performed By Nurse**.
**Expected:** no staff-nurse login exists; the picker is mandatory; records show performer ≠ recorder (`TC-VIS-015`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## JOURNEY H3 — Prescription → pharmacy sale → inventory → billing

### TC-HX-006 — H3 dispensing chain

`HOSPITAL_A · ADM+DOC+PHA · Pharmacy · Critical`
**Pre:** `QA Paracetamol 500` batch `QA-B1` qty **100**.
**Steps**

1. **S-ADM** Inventory → record the exact starting quantity.
2. **S-DOC** complete a consultation for `Journey One` prescribing `QA Paracetamol 500`.
3. **S-PHA** **Prescriptions** → the prescription appears with the patient's name and `PAT` id → **See Consultation** opens the source.
4. **S-PHA** **Dispense** (or Billing Counter ▸ **Hospital Rx Mode**) → select batch `QA-B1` → qty **5** → payment `CASH` → complete → print the invoice.
5. **S-PHA** Inventory → quantity is **95**.
6. **S-PHA** Dashboard and Reports → today's sales include this one.
7. **S-ADM** Overview → the Pharmacy sales card reflects it; the Billing collection card is **not** double-counted.
8. **S-PHA** Returns ▸ `PATIENT` → **Search Bill** → refund 2 units → **Process Patient Refund**.
9. **S-PHA** Inventory → quantity is **97**; Dashboard totals fall accordingly.
   **Expected:** one sale, one decrement, one refund, one increment; the invoice shows the patient's `PAT` id; no double counting anywhere.
   **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HX-007 — H3 negative: insufficient / expired / blocked stock

`HOSPITAL_A · PHA · Pharmacy · Critical`
**Steps:** attempt to dispense more than in stock (`TC-HP-014`), from an expired batch and from a blocked batch (`TC-HP-016`, `TC-HP-026`); then verify stock.
**Expected:** all refused; **stock unchanged** after every refused attempt.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## JOURNEY H4 — OT request → schedule → forms → completion

### TC-HX-008 — H4 full surgical lifecycle

`HOSPITAL_A · ADM+DOC+OTI+NUR · OT · Critical`
**Pre:** OT module on; OT Incharge enabled; theatre `OT Room 1`; OT Policies = **Corporate / NABH**; patient admitted (from H2).
**Steps**

1. **S-ADM** Settings ▸ OT Permissions → record the grid. Settings ▸ OT Policies → apply **Corporate / NABH**.
2. **S-DOC** from the IPD case → **Create Request** (procedure, `ELECTIVE`).
3. **S-OTI** **Requests** → the request is listed → **approve**.
4. **S-OTI** **Schedule** → surgeon Dr Meera, anaesthetist, room `OT Room 1`, tomorrow 10:00–11:00 → `Surgery scheduled`.
5. **S-OTI** try to **Start** now → refused (pre-op checklist and anaesthesia clearance required).
6. **S-NUR** Consent Forms → fill **Pre-Operative Checklist**, **Informed Consent — Surgery**, **GA Consent** → each print shows **UHID = the patient's PAT id**.
7. **S-DOC** record **Pre-Anaesthesia Evaluation** and **anaesthesia clearance**.
8. **S-OTI** **Start** → `IN_PROGRESS`; sign the WHO checklist phases; record milestones.
9. **S-DOC** write the **operative note**; fill the **Surgical Case Record**.
10. **S-OTI** **Complete** → `COMPLETED`; the OT bed/room frees to **cleaning**.
11. **S-NUR** fill **Post-Operative Checklist** and **Post-Anaesthesia Recovery Chart**.
12. **S-OTI/S-ADM** move to recovery if `RECOVERY_TRACKING` is on; then **close**.
13. **S-ADM** OT Analytics and Audit Logs.
    **Expected:** the status chain is exactly `REQUESTED → APPROVED → SCHEDULED → IN_PROGRESS → COMPLETED → CLOSED`; every gate blocks until satisfied; **every printed NABH form shows the patient's identifier, never the hospital's**; analytics count this surgery once; every transition is audited.
    **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HX-009 — H4 variant: Small-hospital policy and cancellation

`HOSPITAL_A · ADM+OTI · OT · High`
**Steps:** apply the **Small hospital** preset; raise and schedule another surgery; start it **without** pre-op; then cancel an elective surgery with `CANCELLATION_REASON` required.
**Expected:** the Small preset relaxes the gates (advisory); cancellation demands a reason for elective; the room slot is freed. Restore the policy afterwards.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## JOURNEY H5 — ICU admission → monitoring → transfer out

### TC-HX-010 — H5 ICU lifecycle

`HOSPITAL_A · REC+DOC+NUR+NI · ICU · Critical`
**Pre:** ICU module on; `ICU Ward` with `ICU-01`; alert threshold SpO2 low = 95; ventilator parameter `QA PEEP`; one severity score type enabled.
**Steps**

1. **S-REC** admit a patient to `ICU Ward` / `ICU-01`.
2. **S-ADM** ICU Dashboard and ICU Bed Board → the patient and the occupied bed appear.
3. **S-NI** assign the patient to **Reena**.
4. **S-NUR** record **Intake/Output**, **Ventilator** (`QA PEEP`), a **Severity Score**, and an **Infusion** (start → rate change → stop).
5. **S-NUR** record SpO2 **92** → **S-ADM** ICU Dashboard shows the alert.
6. **S-NUR** record SpO2 **97** → the alert clears.
7. **S-DOC** open `/ipd/:id` → all ICU records visible; update the ICU stay.
8. **S-REC** transfer to `GA-02` → the ICU stay ends; `ICU-01` → cleaning; patient leaves the ICU dashboard.
9. **S-NI** **Mark Bed Cleaned** on `ICU-01`.
10. **S-ADM** Reports/Audit → ICU activity recorded.
    **Expected:** the ICU stay is created on admission and ended on transfer; thresholds fire and clear; only the assigned nurse can record; bed states follow the standard cycle.
    **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## JOURNEY H6 — ⭐ Parent/child shared phone: the record must attach to the CHILD

### TC-HX-011 — H6 family journey end-to-end

`HOSPITAL_A · REC+DOC+PHA · Patients · Critical`
**This is the highest-value clinical-safety journey in the Hospital pack.**
**Pre:** no patient on `9900080001`.
**Steps**

1. **S-REC** register the **father** `Vikas Kale`, phone `9900080001`, DOB 1985-06-15, Male. Record `id_father`, `PAT` id.
2. **S-REC** register the **child** `Ishaan Kale`, **same phone**, DOB 2018-03-10, Male.
   → chooser appears listing **only** `Vikas Kale`, `Age 40`, `Patient ID PAT…` (no phone/DOB/address).
   → click **Register Different Patient**. Record `id_child`.
3. **S-REC** register the **mother** `Sneha Kale`, same phone.
   → chooser now lists **both** Vikas and Ishaan, each with its own **Use This Patient**.
   → click **Use This Patient on Ishaan** → confirm **no new patient is created** and the flow continues with `id_child`.
4. **S-REC** repeat step 3 and this time **Register Different Patient** → the mother is created. Three active patients on one number.
5. **S-REC** OPD ▸ **Add OPD** → search `9900080001` → the picker offers **all three** → select **Ishaan** → create the OPD.
6. **S-DOC** Start Consultation → **the header must read `Ishaan Kale` / `PAT<id_child>`** → prescribe a paediatric dose → Complete.
7. **S-DOC** Print Prescription and Case Paper → **both must name Ishaan, never Vikas**.
8. **S-PHA** Prescriptions → the row shows **Ishaan** → dispense → the invoice names **Ishaan**.
9. **S-REC** Billing → the bill is under **Ishaan**; open Vikas's bills → **the child's bill is absent** (`TC-HB-017`).
10. **S-REC** Patients → open Vikas → his record is **unchanged**: same name, phone, no clinical records from this visit.
11. **S-ADM** Audit Logs → two `PATIENT_DUPLICATE_PHONE_ACKNOWLEDGED` entries with the phone **masked** (`99******01`).
12. **S-REC** edit Ishaan's phone to a free number → save → then set it back → observe the chooser behaviour (`TC-VIS-007`).
    **Expected**

- At **every** step the clinical record, prescription, invoice and bill attach to **Ishaan**, the patient actually selected — never to the father.
- The chooser never exposes phone, DOB, address or email.
- The father's record is untouched throughout.
- ⚠️ **Do not assert** that two simultaneous registrations on one number are impossible — the database backstop (Phase D) has not shipped. This journey tests the _workflow_, not concurrency.
  **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HX-012 — H6 via appointment booking

`HOSPITAL_A · REC+DOC · Appointments · Critical`
**Steps:** repeat H6 steps 2–7 starting from **Appointments ▸ Add ▸ New Patient** instead of Patients (`TC-HR-023`).
**Expected:** identical behaviour — a single match still asks; the appointment attaches to the exact patient chosen; no silent reuse.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HX-013 — H6 cross-tenant: same family phone in HOSPITAL_B

`HOSPITAL_A + B · REC · Patients · Critical`
**Steps:** **S-B** register a patient on `9900080001` in HOSPITAL_B.
**Expected:** **no chooser appears, or it lists only HOSPITAL_B patients.** If any Kale family member from HOSPITAL_A appears in B's chooser, that is a **Critical** cross-tenant disclosure (`TC-ISO-010`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## JOURNEY H7 — Module revoked while a hospital user is logged in

### TC-HX-014 — H7 live module revocation and restoration

`PLATFORM → HOSPITAL_M · SA + ADM + REC · Entitlement · Critical`
**Steps**

1. **S-ADM(M)** log in to Hospital M (full plan). Open **Wards & Beds**; confirm a ward and bed exist. In DevTools **Copy as cURL** the ward-list request and keep the token. **Do not log out.**
2. **S-SA** (incognito) assign the OPD-only plan to Hospital M.
3. **S-ADM(M)** **without logging out**, click another tab, then refresh.
4. Observe: which tabs disappear; what a click on a now-gated area does.
5. Replay the captured cURL with the **still-valid** token.
6. **S-SA** restore the full plan.
7. **S-ADM(M)** refresh → tabs return → open Wards & Beds.
   **Expected**

- Step 4: IPD, Wards & Beds, ICU, OT, Nursing, Billing tabs disappear after refresh; a gated API call returns **403** even before refresh (`ModuleAccessAspect` reads the live row).
- Step 5: **403** — a valid token is not enough. A **200** here is a **Critical** entitlement bypass.
- Step 7: the **original ward and bed are still there, unchanged** — revocation hides and denies, never deletes.
- Note which tabs are driven by the **JWT `modules` claim** and therefore only update after refresh/re-login (`TC-MOD-031`) — that is expected, not a bug.
  **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HX-015 — H7 variant: tenant deactivated mid-session

`PLATFORM → HOSPITAL_B · SA + REC · Tenant lifecycle · Critical`
**Steps:** **S-B** logged in and working; **S-SA** sets QA Hospital B **Inactive**; **S-B** clicks a tab; attempt a fresh login; **S-SA** reactivates; **S-B** logs in and re-checks all data.
**Expected:** live session blocked, login refused, **all data intact** on reactivation (`TC-SA-034`/`035`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## JOURNEY H8 — Cross-tenant isolation from a live hospital session

### TC-HX-016 — H8 A-vs-B sweep with real records

`HOSPITAL_A + B · REC/DOC/NUR/PHA · all · Critical`
**Pre:** everything created in H1–H6 exists in HOSPITAL_A; HOSPITAL_B has its own mirror records; you hold `TOKEN_A` and `TOKEN_B`.
**Steps** — from **S-B** (browser 2) and with `TOKEN_B`:

1. Patients: list and search for `Journey One`, `Ishaan Kale`, `9900080001`, their `PAT` ids → `TC-ISO-003`/`004`.
2. Fetch each A record by **numeric id** and by **publicId**: patient, appointment, OPD, IPD admission, bill, sale, surgery, ICU stay, document, staff user → `TC-ISO-005`/`006`/`014`/`017`/`019`/`028`/`031`/`035`/`039`/`046`.
3. Attempt to **modify**: update A's patient, pay A's bill, discharge A's admission, change A's bed status, complete A's surgery, refund A's sale, reset A's doctor's password → `TC-ISO-007`/`021`/`024`/`031`/`036`/`040`/`047`.
4. Attempt to **link**: create an OPD/appointment/IPD in B using A's `patientId` → `TC-ISO-012`.
5. Attempt to **download**: A's prescription PDF, case paper, bill receipt, clinical document → `TC-ISO-018`/`035`/`050`.
6. Open `/ipd/{A_admission_id}` in browser 2 → `TC-ISO-020`.
7. Check every **dropdown** in B — patient, doctor, ward, bed, medicine batch → `TC-ISO-011`/`026`/`048`.
8. Compare B's **Overview, Reports and Audit Logs** against B's own hand counts → `TC-ISO-053`/`056`.
   **Expected**

- Every read is **403 or 404** with **no** name, phone, DOB, amount, diagnosis or filename in the body.
- Every write is refused **and A is verified unchanged afterwards** (switch to S-REC and re-check).
- No A record appears in any B dropdown, report, or total.
- **Any 200 carrying A's data stops testing immediately** — capture evidence and raise **Critical**.
  **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
