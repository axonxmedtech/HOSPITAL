# 01 — HOSPITAL ADMIN: SETUP & MASTER DATA

**Baseline:** `aa143a7` · **Cases:** `TC-HA-001` … `TC-HA-046` · Tenant **HOSPITAL_A** unless stated · Login `admin.hospa@qa.test` at `/login/hospital`

**Sidebar (grouped):** Overview · **Patient Management** (Patients, Appointments, OPD, IPD, Operation Theatre, Pathology) · **Rooms** (Wards & Beds) · **Critical Care** (ICU Dashboard, ICU Bed Board) · **Staff** (Doctors, Pharmacists, Receptionists, OT Incharge, OT Theatres) · **Nursing** (Nurses, Nurse Assignments, Nurse Tasks, Time Slots, Calendar) · **Pharmacy** (Pharmacy) · **Inventory** (Medicine Inventory, Hospital Inventory) · **Finance** (Billing, Fees) · **Reports** (Reports & Analytics, OT Analytics, Audit Logs) · **Presets** (Quick Notes, Symptom Presets, Diagnosis Presets, Prescription Presets, In-Clinic Presets) · **Administration** (Settings, Support).

> Global rule (`TC-AUTH-015`): F5 returns to **Overview**. Do not re-file per screen.
> Every "create staff" case here proves a **downstream effect**, not just the row.

**Format:** `Tenant · Role · Module · Priority · Screen` line, then Pre / Data / Steps / Expected / Result.

---

## A. OVERVIEW

### TC-HA-001 — Overview renders module-aware cards

`HOSPITAL_A · HOSPITAL_ADMIN · Overview · High · Overview`
**Pre:** full plan; data from `02-TEST-DATA-SETUP.md` §7 exists.
**Steps:** 1. Log in. 2. Read every card and chart. 3. Count by hand: active patients, today's OPD, current admissions, beds occupied/available/cleaning/maintenance, today's collection. 4. Compare.
**Expected:** cards for Core, OPD (consultations, trend, visit types, busiest specialities), IPD, Beds (occupied / usable capacity / available / cleaning / maintenance / occupancy %), Billing collection, Pharmacy sales — **only for enabled modules**; no disabled/placeholder cards. Every figure equals the hand count. Currency shows two decimals.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HA-002 — Overview on the OPD-only plan

`HOSPITAL_M · HOSPITAL_ADMIN · Overview · High · Overview`
**Steps:** 1. Log in as `admin.hospm@qa.test`. 2. Read the Overview.
**Expected:** only Core + OPD cards; **no** IPD/Beds/Billing/Pharmacy cards, no "0" placeholders for them. `GET /hospital/dashboard` body contains no `ipd`/`beds`/`billing` keys.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HA-003 — Overview stale-response and error state

`HOSPITAL_A · HOSPITAL_ADMIN · Overview · Medium · Overview`
**Steps:** 1. Throttle network (DevTools → Network → Slow 3G). 2. Click Overview, immediately click Patients, then Overview again. 3. Stop backend; reload Overview. 4. Restart.
**Expected:** no flash of wrong-tenant or mixed data; a loading state then correct figures; with backend down a readable error, header still rendered; retry recovers.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## B. DOCTORS (Staff ▸ Doctors)

### TC-HA-004 — Create doctor → login → dashboard → picker

`HOSPITAL_A · HOSPITAL_ADMIN → DOCTOR · Doctors · Critical · Staff ▸ Doctors`
**Data:** name `Dr Meera Kulkarni`, email `doc1.hospa@qa.test`, phone `9900100001`, specialization `General Medicine`, password `QaPass#2026`. <!-- pragma: allowlist secret — synthetic QA credential, see docs/manual-qa/02-TEST-DATA-SETUP.md -->
**Steps:** 1. Doctors → **Add**. 2. Fill; Save. 3. New tab: log in as the doctor. 4. As reception, open Appointments → Add → doctor dropdown; OPD → Add → doctor dropdown. 5. As doctor call `GET /hospital/patients` with a HOSPITAL_B patient id.
**Expected:** row appears, Active; login lands `/hospital/doctor` with Overview/Patients/Appointments/OPD/Follow-ups/IPD/Billing/OT/ICU/Medicine Inventory tabs (module-dependent); doctor is offered in **both** pickers; step 5 → 403/404.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HA-005 — Doctor validation

`HOSPITAL_A · HOSPITAL_ADMIN · Doctors · High · Staff ▸ Doctors`
**Steps:** Save with: all blank; invalid email; 9-digit phone; duplicate email `doc1.hospa@qa.test`; name with emoji; 120-char name.
**Expected:** field-level messages, no row created (reload list), no 500. Duplicate email refused; existing doctor unaffected.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HA-006 — Edit doctor propagates

`HOSPITAL_A · HOSPITAL_ADMIN · Doctors · High · Staff ▸ Doctors`
**Steps:** 1. Edit Dr Meera: specialization → `Cardiology`, phone → `9900100002`. Save. 2. Reload. 3. Reception opens the doctor picker. 4. Doctor logs in → profile.
**Expected:** persists; picker shows `Dr Meera Kulkarni - Cardiology`; the doctor's own session is **not** revoked (no password/role change).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HA-007 — Reset doctor password revokes session

`HOSPITAL_A · HOSPITAL_ADMIN · Doctors · Critical · Staff ▸ Doctors`
**Steps:** = `TC-AUTH-019` from the Doctors tab (**Reset Password** action).
**Expected:** live doctor session → 401; old password fails; new works.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HA-008 — Deactivate doctor: login, pickers, history, appointments

`HOSPITAL_A · HOSPITAL_ADMIN · Doctors · Critical · Staff ▸ Doctors`
**Pre:** Dr Arjun has 1 completed consultation and 1 future appointment.
**Steps:** 1. Doctors → Dr Arjun → **Delete/Deactivate** (confirm dialog). 2. Dr Arjun attempts login. 3. Reception opens both pickers. 4. Open the past consultation and its prescription PDF. 5. Open the future appointment.
**Expected:** confirmation required; login refused; absent from pickers; history and PDF still show his name; the future appointment still exists and shows his name — record how the UI flags it (`NEEDS_PRODUCT_CONFIRMATION`: should reception be prompted to reassign?).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HA-009 — Doctor list search/sort/pagination/empty

`HOSPITAL_A · HOSPITAL_ADMIN · Doctors · Medium · Staff ▸ Doctors`
**Steps:** search `Meera`, `meera`, `9900100002`, `zzz`; click column headers; page if >10; a 60-char name renders.
**Expected:** case-insensitive partial match; readable empty state; stable sort; no overflow.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HA-010 — Doctor creating a doctor (runtime discovery)

`HOSPITAL_A · DOCTOR · Doctors · Medium · API`
**Steps:** `POST /hospital/doctors` with the **doctor** token (`05` §6).
**Expected:** matrix says `ADM DOC`. **Record the status.** `NEEDS_PRODUCT_CONFIRMATION` — do not classify pass/fail. Cross-ref `TC-PERM-002`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## C. RECEPTIONISTS (Staff ▸ Receptionists)

### TC-HA-011 — Create receptionist → login → dashboard

`HOSPITAL_A · HOSPITAL_ADMIN → RECEPTIONIST · Receptionists · Critical · Staff ▸ Receptionists`
**Data:** `Priya Salunke`, `rec.hospa@qa.test`, `9900100010`.
**Steps:** 1. Add; Save. 2. Log in as her. 3. With her token `GET /hospital/doctors` (allowed) and `POST /hospital/doctors` (denied).
**Expected:** lands `/hospital/receptionist` with Overview/Patients/Appointments/OPD/Follow-ups/IPD/Billing/Medicine Inventory/OT/ICU tabs per modules; 200 then 403.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HA-012 — Receptionist edit / reset / deactivate

`HOSPITAL_A · HOSPITAL_ADMIN · Receptionists · High · Staff ▸ Receptionists`
**Steps:** edit name; reset password (session revoked); deactivate; attempt login; reactivate if the UI offers it.
**Expected:** as doctors. Patients registered by her keep her as creator in audit.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HA-013 — Receptionist validation & duplicates

`HOSPITAL_A · HOSPITAL_ADMIN · Receptionists · Medium · Staff ▸ Receptionists`
**Steps:** blank; bad email; email already used by a **doctor** (`doc1.hospa@qa.test`); email used in **HOSPITAL_B** (`rec.hospb@qa.test`).
**Expected:** refused with messages. Record whether an email used by another tenant is refused (users table is global) — expected refused; that is correct, not a leak, as long as the message does not name the other tenant.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## D. PHARMACISTS (Staff ▸ Pharmacists) — PHARMACY module

### TC-HA-014 — Create pharmacist → login → pharmacy dashboard

`HOSPITAL_A · HOSPITAL_ADMIN → PHARMACIST · Pharmacists · Critical · Staff ▸ Pharmacists`
**Data:** `Nilesh Gupta`, `pharm.hospa@qa.test`.
**Steps:** 1. Add; Save. 2. Log in. 3. Token: `GET /hospital/opd` and `GET /hospital/patients`.
**Expected:** lands `/hospital/pharmacy` (Dashboard, Billing Counter, Billing, Prescriptions, Inventory, Purchase Management, Suppliers, Manufacturers, Returns & Refunds, Expiry Management, Reports & Analytics, Audit Logs, Settings); both API calls **403**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HA-015 — Pharmacists tab hidden without PHARMACY

`HOSPITAL_M · HOSPITAL_ADMIN · Pharmacists · High · Staff`
**Steps:** log in to Hospital M; `POST /hospital/pharmacists` with its token.
**Expected:** tab absent; API — **record** (controller has no `@RequireModule`; likely 200 → `IMPLEMENTATION_DRIFT`, cross-ref `TC-MOD-012`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## E. NURSES (Nursing ▸ Nurses) — NURSING module

### TC-HA-016 — Create staff nurse (no login) and incharge (with login)

`HOSPITAL_A · HOSPITAL_ADMIN → NURSE_INCHARGE · Nurses · Critical · Nursing ▸ Nurses`
**Pre:** _Separate Nurse Login_ **OFF**.
**Data:** `Sister Latha Menon` (tick **Is Incharge**, email `ni.hospa@qa.test`); `Staff Nurse Reena Das` (no incharge).
**Steps:** 1. Add Latha as incharge; Save. 2. Add Reena; Save. 3. Log in as Latha. 4. Attempt login as Reena.
**Expected:** Latha lands `/hospital/nurse-incharge`; Reena has **no credentials** while the setting is OFF (record exactly what the form shows for a staff nurse's login fields).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HA-017 — Separate Nurse Login ON → staff nurse gets credentials

`HOSPITAL_A · HOSPITAL_ADMIN → NURSE · Nurses · Critical · Settings ▸ Operations`
**Steps:** 1. Settings → Operations Settings → nurse login toggle **ON**. 2. Nurses → Reena → set/reset password `QaPass#2026`, email `nurse.hospa@qa.test`. 3. Log in as Reena. <!-- pragma: allowlist secret — synthetic QA credential, see docs/manual-qa/02-TEST-DATA-SETUP.md -->
**Expected:** lands `/hospital/nurse` (Dashboard, My Patients, My Tasks, My Shifts, My Attendance, Forms, ICU Beds). Downstream: nursing records now attribute to the logged-in nurse; the "Performed By Nurse" picker disappears for her (`TC-VIS-015`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HA-018 — Promote / demote nurse

`HOSPITAL_A · HOSPITAL_ADMIN · Nurses · High · Nursing ▸ Nurses`
**Steps:** 1. Reena logged in (tab 2). 2. Admin → Reena → **Promote**. 3. Tab 2: click a tab. 4. Re-login. 5. **Demote**; re-login.
**Expected:** step 3 → 401 (role change revokes, `TC-AUTH-020`); re-login lands on incharge dashboard; demote returns her to `/hospital/nurse`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HA-019 — Assign ward incharge from Nurses

`HOSPITAL_A · HOSPITAL_ADMIN · Nurses · High · Nursing ▸ Nurses / Rooms ▸ Wards & Beds`
**Steps:** 1. Set Latha as incharge of `General Ward A` (Wards & Beds ward card or Nurses ward-incharge action). 2. Latha → **My Ward Patients**, **Beds**. 3. Reception: IPD admit → ward picker.
**Expected:** toast `Ward incharge updated`; Latha sees the ward's beds; reception can admit into it (a ward without incharge must be refused — see `TC-HWB-006`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HA-020 — Deactivate nurse

`HOSPITAL_A · HOSPITAL_ADMIN · Nurses · High · Nursing ▸ Nurses`
**Steps:** 1. Reena has an active patient assignment. 2. Nurses → Reena → active toggle **off**. 3. Latha → Unassigned Patients. 4. Reena login.
**Expected:** login refused; record whether her assignment is auto-released (patient reappears as Unassigned) — `NEEDS_PRODUCT_CONFIRMATION` if it stays assigned to an inactive nurse.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HA-021 — Nurse Assignments tab (admin view)

`HOSPITAL_A · HOSPITAL_ADMIN · Nurse Assignments · High · Nursing ▸ Nurse Assignments`
**Steps:** 1. Open with one admitted, assigned patient. 2. Filter by ward/nurse. 3. Attempt an assignment change from here if offered.
**Expected:** list matches Latha's view; admin can view (`ADM DOC REC` GET) and POST/PUT (`ADM`). Record available actions verbatim.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HA-022 — Nurse Tasks (admin creates → nurse completes)

`HOSPITAL_A · HOSPITAL_ADMIN → NURSE · Nurse Tasks · High · Nursing ▸ Nurse Tasks`
**Steps:** 1. Create a task for Reena on P1, priority `HIGH`. 2. Reena → My Tasks → **Start Task** → **Complete Task**. 3. Admin refreshes.
**Expected:** task flows `PENDING → (started) → COMPLETED`; toasts `Task started`, `Task completed successfully`; admin sees status. Cancel path shows `CANCELLED`. `ManualTask` PUT is `NUR` only — admin cannot complete on the nurse's behalf (record).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HA-023 — Time Slots: shift templates and appointment slots

`HOSPITAL_A · HOSPITAL_ADMIN · Time Slots · High · Nursing ▸ Time Slots`
**Steps:** 1. Create shift template `Morning 07:00–15:00`. 2. Create appointment slot rule. 3. Latha → Schedule → **Fill Schedule** for next week. 4. Edit the template to 08:00–16:00. 5. Check today's vs next week's schedules.
**Expected:** templates/slots save; `Shift assigned`; editing a template rewrites **future** schedules only (today keeps 07:00). Reception's appointment slot picker reflects the slot rule.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HA-024 — Calendar (admin)

`HOSPITAL_A · HOSPITAL_ADMIN · Calendar · Medium · Nursing ▸ Calendar`
**Steps:** open Calendar; navigate months; click a day with schedules/appointments; empty day.
**Expected:** renders nurse schedules and hospital events; empty day shows an empty state; NURSING-gated (`TC-MOD-015`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## F. OT INCHARGE & THEATRES (Staff ▸ OT Incharge / OT Theatres) — OT module

### TC-HA-025 — OT Incharge enabled setting → create → login → permissions

`HOSPITAL_A · HOSPITAL_ADMIN → OT_INCHARGE · OT Incharge · Critical · Settings ▸ Operations / Staff ▸ OT Incharge`
**Steps:** 1. Settings → Operations → OT Incharge toggle **ON**. 2. OT Incharge → Add `Sunil More`, `ot.hospa@qa.test`. 3. Log in. 4. Token: `GET /hospital/surgeries` (needs `OT_VIEW`), `GET /hospital/patients`.
**Expected:** lands `/hospital/ot-incharge` with **OT Board** and **Requests** only; surgeries per OT Permissions grid; patients **403**. With the setting OFF, record whether the tab/creation is hidden.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HA-026 — OT Theatres tab → theatre available in scheduling

`HOSPITAL_A · HOSPITAL_ADMIN · OT Theatres · High · Staff ▸ OT Theatres`
**Steps:** 1. Create theatre `OT Room 1`. 2. Reception/OT incharge: **Schedule** a surgery → room picker. 3. Deactivate the room; re-open picker.
**Expected:** room offered, then absent after deactivation; existing scheduled surgeries keep the room name. Also mirrors **Settings ▸ OT / Rooms** (`OtRoomsCard`) — same data (`TC-HAS-030`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## G. PATIENTS (admin view) · PRESETS · SUPPORT · AUDIT

### TC-HA-027 — Admin Patients tab mirrors reception

`HOSPITAL_A · HOSPITAL_ADMIN · Patients · High · Patient Management ▸ Patients`
**Steps:** 1. Add patient via **PatientModal** (`Add New Patient`) with a free phone. 2. Add with `9900011111` → chooser → **Register Different Patient**. 3. Edit; view; delete with reason. 4. **New OPD Case** modal → New Patient inline.
**Expected:** identical behaviour to `02-HOSPITAL-RECEPTIONIST.md` §A; admin can acknowledge duplicates; deleted patient hidden but history kept.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HA-028 — Quick Notes preset → doctor consultation

`HOSPITAL_A · HOSPITAL_ADMIN → DOCTOR · Presets · Medium · Presets ▸ Quick Notes`
**Steps:** 1. Create note `Advise rest 3 days`. 2. Doctor opens a consultation → notes area. 3. Delete preset; re-check.
**Expected:** preset offered in the consultation; removed after delete; existing consultations keep the inserted text. Presets are **per-doctor-isolated** where `ConsultationNotePreset` has `doctorId` — record whether a doctor sees admin-created global presets.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HA-029 — Symptom / Diagnosis presets

`HOSPITAL_A · HOSPITAL_ADMIN → DOCTOR · Presets · Medium · Presets`
**Steps:** create `Fever` symptom and `Viral fever` diagnosis; doctor consultation → symptom/diagnosis fields; edit; delete.
**Expected:** offered as suggestions; edits persist; deletion does not alter past consultations.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HA-030 — Prescription presets

`HOSPITAL_A · HOSPITAL_ADMIN → DOCTOR · Presets · Medium · Presets ▸ Prescription Presets`
**Steps:** create preset `Fever pack` with 2 medicines + dosage; doctor applies it in a consultation; verify prescription lines; delete preset.
**Expected:** applying fills the prescription table; saved prescription unaffected by later preset deletion.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HA-031 — In-Clinic Presets visible only when In-Clinic is ON

`HOSPITAL_A · HOSPITAL_ADMIN · Presets · Medium · Presets ▸ In-Clinic Presets`
**Steps:** 1. Confirm tab present (In-Clinic default ON). 2. Settings → Operations → In-Clinic **OFF**. 3. Watch the sidebar **without refresh** (WebSocket `SETTINGS_UPDATED`). 4. Turn back ON.
**Expected:** tab disappears live and returns; doctor's in-clinic (procedure) section in consultation follows the same setting.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HA-032 — Support tickets → Super Admin → resolution visible

`HOSPITAL_A · HOSPITAL_ADMIN · Support · Medium · Administration ▸ Support`
**Steps:** 1. Raise ticket `QA ticket 1`. 2. Super Admin resolves (`TC-SA-055`). 3. Refresh Support. 4. FAQ list.
**Expected:** status → `RESOLVED`; hospital-type FAQs listed; `HospitalTicket` is `ADM` only — receptionist token `GET /hospital/tickets` → 403.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HA-033 — Audit Logs: coverage, filters, privacy

`HOSPITAL_A · HOSPITAL_ADMIN · Audit Logs · High · Reports ▸ Audit Logs`
**Steps:** 1. Perform: create patient, delete patient (reason `QA`), acknowledge duplicate, create doctor, reset password, change a setting, complete consultation, admit, discharge, bed cleaned. 2. Open Audit Logs; filter by action/date/user; search reason text. 3. Look for passwords, tokens, full phone numbers.
**Expected:** every action listed with actor email, entity id, timestamp; duplicate-phone entry shows **masked** phone; no secrets; empty filter → empty state; pagination stable. Non-admin `GET /hospital/audit-logs` → 403 (`TC-PERM-024`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## H. INVENTORY TABS (admin)

### TC-HA-034 — Medicine Inventory (MEDICAL_INVENTORY)

`HOSPITAL_A · HOSPITAL_ADMIN · Medicine Inventory · High · Inventory ▸ Medicine Inventory`
**Steps:** 1. Add `QA Paracetamol 500`, batch `QA-B1`, qty 100, expiry +24m. 2. Add zero-stock and 20-day-expiry items. 3. Pharmacist → Inventory. 4. Doctor → Medicine Inventory tab (read). 5. Sale of 5 units at the Billing Counter. 6. Re-check qty.
**Expected:** rows persist; pharmacist sees the same stock; doctor tab is read-only; qty 100 → 95 after the sale; low/expiry banners (`LowStockBanner`) reflect the seeded items.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HA-035 — Hospital Inventory (HOSPITAL_INVENTORY) → IPD administer

`HOSPITAL_A · HOSPITAL_ADMIN → DOCTOR · Hospital Inventory · High · Inventory ▸ Hospital Inventory`
**Steps:** 1. Add stock for a platform master item (`TC-SA-052`) qty 10. 2. On an admitted patient: IPD → **administer hospital items** (2 units). 3. Re-check stock; check the IPD bill.
**Expected:** stock 10 → 8; bill gains an item line; audit entry. Tab label reads `Hospital Inventory` (tenant word).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HA-036 — Inventory validation & permissions

`HOSPITAL_A · HOSPITAL_ADMIN · Inventory · Medium · Inventory`
**Steps:** negative qty; past expiry date; blank name; nurse token `POST /hospital/medicines`; receptionist token `GET /hospital/medicines`.
**Expected:** validation messages; nurse 403; receptionist 200 (matrix `ADM DOC REC`) — record; no 500.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## I. PHARMACY (admin tab) & PATHOLOGY

### TC-HA-037 — Admin Pharmacy tab

`HOSPITAL_A · HOSPITAL_ADMIN · Pharmacy · Medium · Pharmacy ▸ Pharmacy`
**Steps:** open; read sales summary/stock summary; compare with pharmacist's Dashboard figures after one sale.
**Expected:** identical totals; admin can reach the pharmacy dashboard (`/hospital/pharmacy` route admits `HOSPITAL_ADMIN`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HA-038 — Pathology placeholder state

`HOSPITAL_A · HOSPITAL_ADMIN · Pathology · Low · Patient Management ▸ Pathology`
**Steps:** look for the tab; if present open it.
**Expected:** **absent** (module key `PATHOLOGY` cannot be granted). If present: heading `Pathology`, text `Pathology Module is currently under development.` — **PLACEHOLDER**, no functional cases. Cross-ref `TC-MOD-030`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## J. ADMIN-SIDE NEGATIVE / PERMISSION / PERSISTENCE

### TC-HA-039 — Every Staff-group create is admin-only (API)

`HOSPITAL_A · all non-admin · Staff · Critical · API`
**Steps:** POST `/hospital/doctors`, `/receptionists`, `/pharmacists`, `/nurses`, `/ot-incharges` with REC, DOC, PHA, NURSE, NI, OTI tokens.
**Expected:** 403 ×30 (note `Doctor` POST admits `DOC` — record separately, `TC-HA-010`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HA-040 — Staff email uniqueness is global, error is tenant-safe

`HOSPITAL_A · HOSPITAL_ADMIN · Staff · High · Staff`
**Steps:** create a doctor with `admin.hospb@qa.test`.
**Expected:** refused; message does **not** say "used by QA Hospital B".
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HA-041 — Double-submit on every staff form

`HOSPITAL_A · HOSPITAL_ADMIN · Staff · High · Staff`
**Steps:** fill each Add form; double-click Save; reload.
**Expected:** exactly one row per form (email uniqueness also backstops it).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HA-042 — Cancel / back / refresh mid-form

`HOSPITAL_A · HOSPITAL_ADMIN · Staff · Medium · Staff`
**Steps:** open Add Doctor, type, **Cancel** → reopen (empty); type, browser Back; type, F5.
**Expected:** no row created; F5 returns to Overview (global rule); no console error.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HA-043 — Staff lists never show another tenant

`HOSPITAL_A · HOSPITAL_ADMIN · Staff · Critical · Staff`
**Steps:** = `TC-ISO-045`/`046`/`047` executed from the admin tabs.
**Expected:** no HOSPITAL_B staff; B ids → 403/404.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HA-044 — Long names, null optionals, dates

`HOSPITAL_A · HOSPITAL_ADMIN · Staff · Low · Staff`
**Steps:** doctor with a 100-char name and blank specialization; view list, picker, and a prescription PDF.
**Expected:** truncation with ellipsis in tables; no layout break; PDF wraps; blank specialization shows `—`/`Unassigned`, never `null`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HA-045 — Responsive layout (admin)

`HOSPITAL_A · HOSPITAL_ADMIN · UI · Low · all`
**Steps:** DevTools → device toolbar → 375px and 768px; open Doctors, Patients, Add modals, Settings.
**Expected:** sidebar collapses; tables scroll horizontally inside their container; modals fit; no double scrollbar.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HA-046 — Sidebar group expand/collapse and active-tab highlighting

`HOSPITAL_A · HOSPITAL_ADMIN · UI · Low · sidebar`
**Steps:** expand each group; select a tab in each; collapse another group.
**Expected:** owning group auto-expands for the active tab; only one highlighted item; labels exactly as listed at the top of this document.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
