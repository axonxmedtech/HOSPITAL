# DATA VISIBILITY (inside one tenant)

**Baseline:** `origin/staging` @ `aa143a7` · **Cases:** 22 (`TC-VIS-001` … `TC-VIS-022`)

`TENANT-ISOLATION.md` proves Tenant A cannot see Tenant B. This document proves that **within one
tenant**, each role sees exactly what it should — and that the same record is shown consistently
to everyone who is allowed to see it.

**Run everything here in HOSPITAL_A** unless stated.

---

## A. One patient, many roles

### TC-VIS-001 — A newly registered patient is visible to every clinical role

| Tenant                                                                                                                                                                                                                                                 | Role           | Module   | Priority     |
| ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------- | -------- | ------------ |
| HOSPITAL_A                                                                                                                                                                                                                                             | REC → ADM, DOC | Patients | **Critical** |
| **Steps** 1. Reception registers `QA Vis Patient` (phone `9900066666`). 2. Log in as admin → Patients. 3. Log in as doctor → Patients. 4. Log in as pharmacist and nurse.                                                                              |
| **Expected** — admin and doctor see the patient immediately (no refresh, no re-login needed beyond navigation). Pharmacist and nurse **do not** have a Patients tab (matrix: `ADM DOC REC`) — the nurse sees patients only once admitted and assigned. |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                                                                                     |

### TC-VIS-002 — Patient edit propagates to every view

| Tenant                                                                                                                                                                                                                                     | Role      | Module   | Priority |
| ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------- | -------- | -------- |
| HOSPITAL_A                                                                                                                                                                                                                                 | REC → all | Patients | **High** |
| **Steps** 1. Create an appointment and an OPD case for `QA Vis Patient`. 2. Reception edits the name to `QA Vis Patient EDITED`. 3. Check: doctor's OPD queue, appointment list, IPD list, billing, the printed case paper, the audit log. |
| **Expected** — every view shows the new name; no screen caches the old one. A stale name on a prescription is a **High** patient-safety issue.                                                                                             |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                                                                         |

### TC-VIS-003 — Soft-deleted patient disappears everywhere but keeps history

| Tenant                                                                                                                                                                                                                                                                | Role      | Module   | Priority     |
| --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------- | -------- | ------------ |
| HOSPITAL_A                                                                                                                                                                                                                                                            | REC → all | Patients | **Critical** |
| **Steps** 1. Reception deletes `QA Vis Patient` with reason `QA test`. 2. Search for them as reception, admin, doctor. 3. Open the OPD case created earlier from the OPD history. 4. Check the audit log.                                                             |
| **Expected** — the patient no longer appears in lists, search or pickers. **The historical OPD case and its prescription still open and still show the patient's name** (deletion is soft, `is_active = 0`). The audit log records `PATIENT_DELETED` with the reason. |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                                                                                                    |

### TC-VIS-004 — Inactive patient does not block their phone number

| Tenant                                                                                                                                                                                                    | Role         | Module   | Priority |
| --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------ | -------- | -------- |
| HOSPITAL_A                                                                                                                                                                                                | RECEPTIONIST | Patients | **High** |
| **Steps** With P5 (`Old Record`, deactivated, phone `9900033333`): register a new patient on `9900033333`.                                                                                                |
| **Expected** — **no** duplicate-phone dialog; the new patient is created with no acknowledgement recorded. An inactive patient does not reserve a number (there is no reactivation flow, so it must not). |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                                        |

### TC-VIS-005 — ⭐ Duplicate-phone family: parent, child, third member

| Tenant     | Role                         | Module   | Priority     |
| ---------- | ---------------------------- | -------- | ------------ |
| HOSPITAL_A | RECEPTIONIST, HOSPITAL_ADMIN | Patients | **Critical** |
| **Steps**  |

1. P1 Rahul Patil exists on `9900011111`.
2. Register Aarav Patil, same phone → dialog **"Mobile number already registered"** lists **Rahul, Age ~39, Patient ID PAT…**.
3. Click **Use This Patient** on Rahul → **no** new patient; the workflow continues with Rahul.
4. Register Aarav again → dialog → click **Register Different Patient** → Aarav created.
5. Register Sunita Patil, same phone → dialog now lists **both Rahul and Aarav**, each with their own **Use This Patient**.
6. Click **Use This Patient** on **Aarav** specifically.
7. Repeat step 5 and this time **Register Different Patient** → Sunita created.
8. Repeat the whole sequence as **admin** (`admin.hospa@qa.test`) — same behaviour.
   **Expected**

- The dialog shows **only** name, age and Patient ID — never phone, DOB, address or email.
- Step 3 and 6 create nobody and select the exact patient clicked (verify the resulting OPD/appointment's `patientId`).
- Steps 4 and 7 create a patient whose record carries the acknowledgement (visible in the audit log as `PATIENT_DUPLICATE_PHONE_ACKNOWLEDGED`, phone masked).
- Both RECEPTIONIST and HOSPITAL_ADMIN can acknowledge; no other role reaches this flow.
  **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

### TC-VIS-006 — Duplicate-phone via appointment booking (any match asks)

| Tenant                                                                                                                                                                                                                                                                 | Role         | Module       | Priority     |
| ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------ | ------------ | ------------ |
| HOSPITAL_A                                                                                                                                                                                                                                                             | RECEPTIONIST | Appointments | **Critical** |
| **Steps** 1. Appointments → Add → tick **New Patient** → name `Aarav Patil`, phone `9900011111`, DOB, gender, doctor, date, slot → Schedule.                                                                                                                           |
| **Expected** — the conflict dialog appears **even though it could have auto-matched**; the system never picks a patient by phone alone. **Use This Patient** books against that exact id; **Register Different Patient** creates the walk-in with the acknowledgement. |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                                                                                                     |

### TC-VIS-007 — Duplicate-phone on phone **edit**

| Tenant                                                                                                                                                                                                                                                                                                                                                                                                                                                         | Role         | Module   | Priority |
| -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------ | -------- | -------- |
| HOSPITAL_A                                                                                                                                                                                                                                                                                                                                                                                                                                                     | RECEPTIONIST | Patients | **High** |
| **Steps** 1. Edit P4 Meera Joshi (`9900022222`) and change her phone to `9900011111`. Save. 2. Dialog appears → Cancel; confirm the phone is unchanged. 3. Repeat → **Register Different Patient**. 4. Now edit Meera's phone to a free number `9900077777`. Save. 5. Edit back to `9900011111`.                                                                                                                                                               |
| **Expected** — step 3 saves with an acknowledgement for `9900011111`; step 4 saves **and clears** the acknowledgement (audit shows nothing new); step 5 triggers the dialog **again** — the earlier acknowledgement for X must not silently carry over after the number was changed away and back (record whether it re-asks; product accepted re-effectiveness of the same-number ack, so either a re-ask or a silent accept is defensible — **note which**). |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                                                                                                                                                                                                                                                                                             |

### TC-VIS-008 — Acknowledgement for X never exempts Y

| Tenant                                                                                                                             | Role         | Module   | Priority     |
| ---------------------------------------------------------------------------------------------------------------------------------- | ------------ | -------- | ------------ |
| HOSPITAL_A                                                                                                                         | RECEPTIONIST | Patients | **Critical** |
| **Steps** 1. Aarav (acknowledged on `9900011111`) — edit his phone to `9900044444` (P7's number). Save.                            |
| **Expected** — the dialog appears listing P7. The acknowledgement Aarav carries for `…11111` grants nothing on `…44444`.           |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______ |

---

## B. Clinical workflow visibility

### TC-VIS-009 — OPD case visible to reception, doctor and admin with role-appropriate actions

| Tenant                                                                                                                                                                                                      | Role          | Module | Priority |
| ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------- | ------ | -------- |
| HOSPITAL_A                                                                                                                                                                                                  | REC, DOC, ADM | OPD    | **High** |
| **Steps** Reception creates an OPD case for P1 with vitals. View it as each role.                                                                                                                           |
| **Expected** — all three see the case and vitals. Only the **doctor** sees _Start Consultation_; reception sees the queue position; admin sees both without a consultation control unless `isSingleDoctor`. |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                                          |

### TC-VIS-010 — Doctor sees only their own queue by default

| Tenant                                                                                                                                                                                                                              | Role   | Module | Priority |
| ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------ | ------ | -------- |
| HOSPITAL_A                                                                                                                                                                                                                          | DOCTOR | OPD    | **High** |
| **Steps** Create one OPD for Dr Meera and one for Dr Arjun. Log in as each.                                                                                                                                                         |
| **Expected** — each doctor's queue shows their own case first/only. Record whether the other doctor's case is reachable at all (via the full OPD tab) — `NEEDS_PRODUCT_CONFIRMATION` on whether doctors may view colleagues' cases. |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                                                                  |

### TC-VIS-011 — Prescription visible to pharmacist after consultation

| Tenant                                                                                                                                                                                                                           | Role      | Module   | Priority |
| -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------- | -------- | -------- |
| HOSPITAL_A                                                                                                                                                                                                                       | DOC → PHA | Pharmacy | **High** |
| **Steps** Doctor completes a consultation with two medicines. Log in as pharmacist → **Prescriptions**.                                                                                                                          |
| **Expected** — the prescription appears with the patient name, medicines and doctor. The pharmacist sees **no** vitals, diagnosis notes or medical history beyond what dispensing needs — record exactly which fields are shown. |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                                                               |

### TC-VIS-012 — Bill visible to reception, doctor, admin; hidden from nurse and pharmacist

| Tenant                                                                                                                                                           | Role | Module  | Priority |
| ---------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---- | ------- | -------- |
| HOSPITAL_A                                                                                                                                                       | all  | Billing | **High** |
| **Steps** After the OPD bill is generated, open Billing as each role.                                                                                            |
| **Expected** — REC/DOC/ADM see it; NURSE, NURSE_INCHARGE, OT_INCHARGE have no Billing tab; PHARMACIST's "Billing" is the **pharmacy** counter, not the OPD bill. |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                               |

### TC-VIS-013 — Admission visible to nurse only after assignment

| Tenant                                                                                                                                                                                                                                | Role                  | Module  | Priority     |
| ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------- | ------- | ------------ |
| HOSPITAL_A                                                                                                                                                                                                                            | NURSE, NURSE_INCHARGE | Nursing | **Critical** |
| **Steps** 1. Admit P1 to General Ward A (incharge: Latha). 2. Log in as Latha → **My Ward Patients** and **Unassigned Patients**. 3. Log in as staff nurse Reena → **My Patients**. 4. Latha assigns P1 to Reena. 5. Reena refreshes. |
| **Expected** — step 2: P1 under Unassigned; step 3: Reena sees **nothing**; step 5: Reena sees P1. Assignment, not admission, grants a staff nurse visibility.                                                                        |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                                                                    |

### TC-VIS-014 — Incharge sees only their wards' patients

| Tenant                                                                                                                                                       | Role           | Module  | Priority     |
| ------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------- | ------- | ------------ |
| HOSPITAL_A                                                                                                                                                   | NURSE_INCHARGE | Nursing | **Critical** |
| **Steps** Create a second incharge `Sister Two` with a new ward `Ward Z`; admit a patient there. Log in as Latha.                                            |
| **Expected** — Latha's My Ward Patients excludes the Ward Z patient; Sister Two's excludes General Ward A's. Cross-reference `TC-PERM-027` for the API half. |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                           |

### TC-VIS-015 — Separate Nurse Login OFF: incharge records care on behalf of a nurse

| Tenant                                                                                                                                                                                                                   | Role           | Module  | Priority |
| ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------- | ------- | -------- |
| HOSPITAL_B                                                                                                                                                                                                               | NURSE_INCHARGE | Nursing | **High** |
| **Preconditions:** HOSPITAL_B has _Separate Nurse Login_ **OFF**.                                                                                                                                                        |
| **Steps** 1. As B's incharge, record vitals for an admitted patient. 2. Observe the **Performed By Nurse** picker. 3. Save with a staff nurse selected. 4. View the record as the doctor.                                |
| **Expected** — the picker is present and required; the saved record shows the chosen nurse as performer, the incharge as recorder. In HOSPITAL_A (ON) the picker is **absent** and the logged-in nurse is the performer. |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                                                       |

### TC-VIS-016 — Files & Access: read-only role sees records but not the form

| Tenant                                                                                                                                                                      | Role          | Module         | Priority |
| --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------- | -------------- | -------- |
| HOSPITAL_A                                                                                                                                                                  | DOCTOR, NURSE | Files & Access | **High** |
| **Steps** Set `NOTES` (Re-Assessment Sheet) to **NURSE only**. Nurse creates a note. Doctor opens the same patient's Notes tab.                                             |
| **Expected** — doctor sees the note (read-only, printable) with **no entry form**; nurse sees the form. Set `NOTES` **Off**: the tab disappears for both. Restore **BOTH**. |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                          |

### TC-VIS-017 — OT surgery visible to requesting doctor, reception, OT incharge and nurse

| Tenant                                                                                                                                                                                                                                                                                           | Role                 | Module | Priority |
| ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------------- | ------ | -------- |
| HOSPITAL_A                                                                                                                                                                                                                                                                                       | DOC, REC, OTI, NURSE | OT     | **High** |
| **Steps** Doctor requests a surgery from P1's IPD case. View as each role.                                                                                                                                                                                                                       |
| **Expected** — doctor sees it on the IPD case; reception on the OT tab (with Schedule); OT incharge on **Requests**; the nurse sees the **Consent Forms** tab once scheduled. Visibility per role follows the OT permission grid (`TC-PERM-010`), so record the grid state alongside the result. |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                                                                                                                               |

### TC-VIS-018 — ICU stay visible on dashboard, bed board and nurse ICU tab

| Tenant                                                                                                                                         | Role                     | Module | Priority |
| ---------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------ | ------ | -------- |
| HOSPITAL_A                                                                                                                                     | ADM, DOC, REC, NURSE, NI | ICU    | **High** |
| **Steps** Admit P1 to `ICU Ward` bed `ICU-01`. View ICU Dashboard, ICU Bed Board, and the nurse's ICU Beds tab.                                |
| **Expected** — the stay appears on all of them consistently (same bed, same patient, same severity). The bed board shows `ICU-01` as occupied. |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______             |

---

## C. Configuration and reference data

### TC-VIS-019 — Admin settings changes are seen by staff without re-login

| Tenant                                                                                                                                             | Role      | Module   | Priority |
| -------------------------------------------------------------------------------------------------------------------------------------------------- | --------- | -------- | -------- |
| HOSPITAL_A                                                                                                                                         | ADM → REC | Settings | **High** |
| **Steps** 1. Reception has the OPD form open. 2. Admin adds a custom vital `QA Pain Score` in **Settings → Vitals**. 3. Reception reopens Add OPD. |
| **Expected** — the new vital appears in reception's OPD form on next open (the form reads live settings). No re-login required.                    |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                 |

### TC-VIS-020 — Deactivated doctor vanishes from pickers but not from history

| Tenant                                                                                                                                                                                                         | Role           | Module  | Priority |
| -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------- | ------- | -------- |
| HOSPITAL_A                                                                                                                                                                                                     | ADM → REC, DOC | Doctors | **High** |
| **Steps** 1. Dr Arjun has one completed consultation. 2. Admin deactivates Dr Arjun. 3. Reception opens Add Appointment / Add OPD doctor pickers. 4. Open Dr Arjun's historical consultation and prescription. |
| **Expected** — Dr Arjun absent from every picker; the historical consultation and PDF still show his name. Cross-reference `TC-AUTH-010`.                                                                      |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                                             |

### TC-VIS-021 — Bed status changes are visible to every role that shows beds

| Tenant                                                                                                                                                                                                                                                                                     | Role         | Module | Priority |
| ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------ | ------ | -------- |
| HOSPITAL_A                                                                                                                                                                                                                                                                                 | REC, NI, ADM | Beds   | **High** |
| **Steps** 1. Admit P1 to `GA-01`. 2. Check Wards & Beds (admin), Beds (incharge), and the IPD admit bed picker (reception). 3. Discharge P1. 4. Re-check all three. 5. Incharge marks `GA-01` cleaned. 6. Re-check.                                                                        |
| **Expected** — after 1: occupied everywhere and **absent from reception's picker**; after 3: **cleaning** everywhere, still absent from the picker; after 5: available everywhere and back in the picker. All writes go through `BedStatusService` and are audited in `bed_status_audits`. |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                                                                                                                         |

### TC-VIS-022 — Audit log shows the acting user correctly for every role

| Tenant                                                                                                                                                                                                                                    | Role      | Module | Priority |
| ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------- | ------ | -------- |
| HOSPITAL_A                                                                                                                                                                                                                                | all → ADM | Audit  | **High** |
| **Steps** Have each role perform one audited action (reception: create patient; doctor: complete consultation; nurse: record vitals; incharge: assign patient; admin: change a setting; pharmacist: make a sale). Admin opens Audit Logs. |
| **Expected** — every entry attributes the action to the **correct email**, with a timestamp and entity id. Nurse actions under _Separate Nurse Login OFF_ show the incharge as actor and the selected nurse as performer.                 |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                                                                        |
