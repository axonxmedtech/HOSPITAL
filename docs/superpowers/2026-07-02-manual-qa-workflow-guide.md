# HMS Manual QA Workflow Guide

This is a step-by-step manual testing script for the Hospital Management System. It covers every major workflow end-to-end, in the order you'd naturally set up and run a hospital. Follow the sections top to bottom the first time through — later sections assume earlier setup (fees, wards, staff, patients) already exists.

**Branch under test:** `testing` (cut from `phase-0-01-discharge-isolation`)

**General tips for the tester:**
- Use a different browser tab (or incognito window) per role so you can be logged in as Admin, Doctor, Receptionist, Nurse, Pharmacist simultaneously — many flows require switching between two roles to confirm the other side sees the update.
- Whenever you complete an action, check the corresponding entry actually shows up for the *next* role in the chain (e.g. after Receptionist registers a patient, confirm Doctor's queue shows them) — that hand-off is where bugs usually hide.
- Note down: exact steps to reproduce, screenshots, and the URL/tab you were on, for anything that looks wrong.

---

## 0. Platform Setup (Super Admin) — do this first, once

**Login:** Super Admin portal login page → `/platform/dashboard` after login.

1. Log in as Super Admin.
2. Go to **Hospitals** → Create a new hospital (tenant):
   - Fill in hospital name, contact details, address.
   - Assign a **Plan** (subscription plan — check the Plans tab first if none exist, create one with a set of modules).
   - Assign **Modules** to the hospital: at minimum enable `OPD`, `APPOINTMENTS`, `BILLING`, `IPD`, `PHARMACY`, `MEDICAL_INVENTORY`, `OT`, `REPORTS` so you can test everything. (If a module isn't assigned here, its entire tab disappears from the Hospital Admin dashboard later — if you can't find a tab, check this first.)
   - Note the Hospital Admin login credentials created (or the invite flow, depending on how onboarding is wired) — this is what you'll use in Section 1.
3. Go to **Plans** — confirm you can create/edit a plan and toggle which modules it includes.
4. Go to **Users** — confirm you can see/manage Super Admin-level users.
5. Go to **Tickets** — this is where hospitals raise support tickets to the platform (tested end-to-end in Section 9).
6. Go to **FAQs** — create an FAQ entry, then separately verify it's visible on the public FAQ page (no login).
7. Go to **Audit Logs** — this is a read-only, cross-tenant log viewer; just confirm it loads and shows entries once other tests below start generating activity.
8. Go to **WhatsApp** settings — confirm the platform-level WhatsApp integration config screen loads (full send-test is optional, depends on if you have a sandbox number).

**Pass criteria:** New hospital created, modules assigned, Hospital Admin can log in.

---

## 1. Hospital Setup (Hospital Admin) — do this second, once per hospital

**Login:** Hospital login page (`portalType=HOSPITAL`) → `/hospital/admin`.

1. Log in as the Hospital Admin created in Section 0.
2. **Settings tab** — Operations Settings:
   - Set `receptionMode` = `HAS_RECEPTIONIST` (test the "normal" flow first; you'll test `SOLO` mode separately in Section 10).
   - Set `billingHandler` = `RECEPTIONIST` (test other combinations later).
   - Note any other toggles here (`inClinic`, `shiftMode`) — click through each and confirm it saves without error.
3. **Fees tab** — Create at least 2–3 custom fee entries (e.g. "Consultation Fee", "Follow-up Fee", "Registration Fee") with amounts. These feed into OPD/billing later.
4. **Charge Master tab** — Create charge master entries for procedures/services you'll bill later (e.g. "X-Ray Chest", "Blood Test - CBC", "Minor Surgery Package") with a price for each. This is what Procurement's invoice-verify and Billing's item pricing both draw from.
   - **Note:** as of this branch, only `HOSPITAL_ADMIN` can create/edit Charge Master entries (Receptionist access was intentionally removed for security reasons) — if you're testing as Receptionist here expect a 403, that's correct behavior, not a bug.
5. **Wards & Beds tab** — Create at least 1–2 wards (e.g. "General Ward", "ICU"), then add beds to each ward. You need at least a few available beds to test IPD admission later.
6. **Master Data tab** — Check what's configurable here (departments, categories etc.) and create a couple of entries if the screen is empty — other dropdowns elsewhere may depend on this.
7. **Staff creation** — Create one of each:
   - **Doctors tab** → Add Doctor (note login credentials)
   - **Receptionists tab** → Add Receptionist (note login credentials)
   - **Nurses tab** → Add Nurse (note login credentials)
   - **Pharmacists tab** → Add Pharmacist (note login credentials)
   - **Lab Technicians tab** → Add Lab Technician (note login credentials)
   - **Radiology Technicians tab** → Add Radiology Technician (note login credentials)
8. **HR & Workforce tab** — Confirm the staff you just created appear here too (employee records). Try: raise a leave request as one role, approve it as Admin. Try running "payroll process" if available (check it doesn't error, don't worry about correctness of amounts).
9. **Training & LMS tab** — Create a training record for a staff member, mark it complete.
10. **Master data for Blood Bank / CSSD / Biomedical / Housekeeping tabs** — these don't have dedicated staff logins; Admin (or Doctor/Nurse where applicable) operates them directly. Just click into each tab once and confirm it loads without error — deeper testing is in Section 11.

**Pass criteria:** Fees, charge master, wards/beds, and one login per staff role all exist and each new staff member can log in successfully.

---

## 2. OPD (Outpatient) Flow

**Roles involved, in order:** Receptionist → Doctor → (Receptionist/Doctor for billing, per `billingHandler` setting)

1. **Receptionist logs in** → `/hospital/receptionist`.
2. **Register a new patient**: Patients tab → Add Patient. Fill full demographic details (name, age/DOB, gender, contact, address). Save.
   - Confirm the patient now appears in the patient list/search.
3. **Create an OPD visit** for that patient: OPD tab → Add OPD Visit (or via Patients → "Send to OPD" if that's the flow) → select the Doctor created in Section 1, select the fee (from Section 1's Fees), confirm.
   - Confirm the visit appears in the OPD queue with status "Waiting" (or equivalent).
4. Optionally, also test **Appointments tab**: book a future-dated appointment for the same or a new patient against the Doctor. Confirm it shows on the Doctor's appointment list.
5. **Doctor logs in** (separate tab) → `/hospital/doctor`.
6. Confirm the patient you just registered shows up in the **OPD queue**.
7. Doctor clicks into the patient, records the consultation: chief complaint, diagnosis, prescription (add at least 2 medicines with dosage/frequency), any lab/radiology orders if that's part of the consult screen.
8. Doctor marks the OPD visit complete / moves patient to "Consulted" status.
9. If a **follow-up** is needed, use the follow-up scheduling action from the Doctor's OPD screen — confirm it creates a follow-up appointment visible back on Receptionist's Appointments tab.
10. **Billing**: depending on the `billingHandler` setting from Section 1 (`RECEPTIONIST`), go back to Receptionist → Billing tab (or the billing action attached to the OPD visit) → generate the bill for the consultation fee, add any charge-master items (e.g. the X-Ray you ordered), record a payment (full or partial), download/print the receipt PDF.
    - Try a **partial payment** once, and confirm the bill shows a remaining balance rather than marking it fully paid.
11. Print the **OPD visit report / prescription PDF** from either Doctor or Admin side and confirm it renders correctly with the hospital's branding and correct patient/visit details.

**Pass criteria:** Patient flows Receptionist → Doctor → Billing cleanly, all data (prescription, fee, payment) is consistent across all three screens, PDFs render correctly.

---

## 3. IPD (Inpatient / Admission) Flow

**Roles involved, in order:** Doctor (or Receptionist) → Nurse → Doctor → Receptionist (billing) → Doctor/Admin (discharge)

1. From an existing OPD visit (Section 2) or a fresh patient, **admit to IPD**: on the Doctor Dashboard (or Receptionist, depending on config) use "Admit to IPD" — select a ward and bed from Section 1, enter admission diagnosis, admission type (Elective/Emergency).
   - Confirm the admission appears in the **IPD tab** list for Admin/Doctor/Receptionist, and the bed now shows "Occupied" in Wards & Beds.
2. Open the admission — this lands you on the **`IPD Details`** page, which has these clinical tabs: **Overview, Clinical Assessment, Consents, Risk Assessment, Fluid Chart, Nursing Progress, Vitals, Discharge Summary.** Test each one:
   - **Clinical Assessment tab** — Doctor/Nurse fills out a clinical assessment form, saves it. Confirm it locks/versions correctly if you try to edit after submission (should create an amendment, not silently overwrite).
   - **Consents tab** — Create a General Consent, fill patient/guardian details, capture signature, submit (locks the consent). Try creating a Surgery/Blood Transfusion consent too if the OT/transfusion flow needs it later. Confirm a locked consent can't be edited, and the PDF print works.
     - **If the patient is a minor** (age < 18 or DOB implies < 18), confirm the guardian fields are required and the consent flow behaves differently (guardian signs instead of / in addition to patient).
   - **Risk Assessment tab** — Nurse fills the fall-risk / pressure-ulcer / nutrition risk assessment. Confirm the score/level calculates and displays (LOW/MEDIUM/HIGH), and that this same fall-risk value surfaces in the Nurse Assessment section (fall risk should sync between the two).
   - **Fluid Chart tab** — Nurse records fluid intake/output entries across a shift. Confirm running totals update correctly.
   - **Nursing Progress tab** — Nurse adds a shift progress note (Morning/Evening/Night). Confirm it's timestamped and attributed to the nurse.
   - **Vitals tab** — Nurse records vitals (BP, pulse, temp, SpO2, respiratory rate, pain score, weight). Record a couple of entries a few minutes apart and confirm the vitals trend/history displays in order.
3. **Doctor Orders**: from the Overview tab, Doctor adds a doctor's order (medication or investigation order) for the admission. Confirm it's visible on the **Nurse Dashboard's task list** as something to action.
4. **Doctor Rounds**: Doctor logs a round note against the admission (findings, plan for the day).
5. **Medicine administration (MAR)**: Nurse Dashboard → find the pending medication task from step 3 → mark administered, recording actual time given. Confirm the Overview tab's medicine section shows it as administered, not just prescribed.
6. **Nurse Task list**: assign/complete a couple of generic nurse tasks against the admission from the Nurse Dashboard, confirm they show completed.
7. **Referral**: raise a referral to another specialty from the admission (e.g. refer to Cardiology), confirm it shows as "Requested". Then, as the responding side, respond to the referral (accept/complete it with a note). Confirm status updates correctly on both ends.
8. **Bed change**: move the patient to a different bed/ward mid-admission. Confirm the old bed frees up and the new bed shows occupied, and the admission record reflects the new bed/ward.
9. **IPD Billing**: add billable items to the IPD bill as they accrue (room charges, procedures, medicines administered). Record a partial payment. This should feed into the final discharge billing.
10. **Discharge** (this is the focus area of the current branch — test thoroughly):
    - **Plan Discharge** (Doctor or Admin only): fill final diagnosis, treatment given, discharge notes, follow-up date → save. Confirm this is now visible to Receptionist as "pending discharge" so they know to finalize billing.
    - Attempt **Confirm Discharge** *before* fully settling the bill, if the system allows it — note whether it blocks you or warns you (this is intentional behavior to verify, not necessarily a bug either way — just report what happens).
    - Settle the remaining IPD bill balance (Receptionist, since Confirm Discharge is also Receptionist-accessible).
    - **Confirm Discharge** (Doctor, Admin, or Receptionist) → confirm the admission status flips to "Discharged", the bed frees up in Wards & Beds, and the patient can no longer be edited/admitted-to again without a fresh admission.
    - Download the **Discharge Summary PDF** and confirm it includes diagnosis, treatment, medications, and follow-up date correctly.
    - Confirm the discharged admission now shows up in **MRD Archive** (Section 8).

**Pass criteria:** Full admission → treatment → discharge cycle works, bed occupancy stays accurate throughout, discharge summary PDF is complete and correct, and you cannot silently lose data by discharging with an open balance (confirm what actually happens and report it either way).

---

## 4. OT (Operation Theatre) Flow

**Roles involved:** Doctor (surgeon) → Nurse (OT staff) → Doctor (finalize)

This flow only appears if the hospital has the `OT` module enabled (Section 0) and only applies to an **IPD admission** (a patient must be admitted first — see Section 3).

1. From an IPD admission's Overview tab, or from the **Operation Theatre** register (Admin/Doctor/Nurse dashboards each have an "Operation Theatre" / OT Register section), **book an OT slot**: choose OT room, date/time, surgeon, procedure name.
2. **Pre-Anaesthesia Checkup (PAC)**: fill the PAC form ahead of the booking (fitness for anaesthesia, ASA grade, etc.).
3. **OT Readiness**: as whoever is responsible for the OT room (likely Nurse or Admin via the OT Register), mark the room "READY" for the booked date. **This step matters — try skipping it once** to confirm the system correctly blocks the case from starting when the room isn't marked ready (this gate was specifically hardened this session; test both the pass and fail case).
4. **WHO Surgical Safety Checklist** — three phases, complete them in order:
   - **Sign In** (before anaesthesia)
   - **Time Out** (before incision) — confirm that if the room is NOT marked ready, signing Time Out is blocked with a clear error, and the booking stays in its prior status (doesn't silently start).
   - Confirm that once Time Out is signed (with the room ready), the booking status automatically advances to "In Progress".
5. **Operation Record**: Doctor fills the actual procedure performed, specimens taken, implants used, complications summary, post-op plan. Save as draft, edit it, confirm edits work while still in DRAFT.
6. **Anaesthesia Record**: fill anaesthesia type, drugs given, vitals during surgery.
7. **Sign Out** (third WHO checklist phase — instrument/sponge count): complete the instrument count. **Test the mismatch path**: deliberately record a mismatched count once and confirm it flags/creates an incident (don't worry about resolving it perfectly, just confirm something visibly happens — an alert, incident record, or blocked sign-out).
8. **Finalize the Operation Record**: requires actual procedure + post-op plan filled, and Sign Out completed. Confirm it's rejected if you try to finalize before Sign Out is done, and rejected/blocked if you try to finalize it a second time after it's already finalized (this idempotency check was added this session).
9. Confirm any **specimens** you recorded created corresponding **Lab Orders** (check Lab tab) and any **implants** you recorded correctly decremented **inventory stock** (check Inventory).
10. **PACU Record**: fill recovery room monitoring after the operation.
11. **Clinical Handover**: complete the handover note from OT/PACU back to the ward nurse.
12. **Post-op Orders**: Doctor signs post-op medication/monitoring orders; confirm these appear as nurse tasks on the ward (similar to Section 3 step 3).
13. Go back to the IPD admission's Overview and confirm the OT episode summary is visible there.

**Pass criteria:** the room-readiness gate genuinely blocks starting a case in a non-ready room (both via direct status update and via signing Time Out), the operation record can't be finalized twice, and specimens/implants correctly fan out to Lab Orders and Inventory respectively.

---

## 5. Pharmacy Flow

**Roles involved:** Doctor (prescribes) → Pharmacist (dispenses) → Pharmacist (inventory/purchasing, separately)

### 5a. Prescription → Dispensing (do this after Section 2 or 3, since you need a prescription to dispense)

1. Confirm a prescription written by the Doctor (OPD in Section 2, or IPD in Section 3) shows up on the **Pharmacist Dashboard → Prescriptions view** ("Live Doctor Prescriptions").
2. Dispense it: select the prescription, confirm/adjust quantities against available stock, complete the sale at the **Billing Counter** view — this should deduct stock and generate a pharmacy bill/receipt.
3. Try dispensing a medicine where stock is **insufficient** — confirm the system blocks or warns rather than allowing negative stock.
4. Print the dispensing receipt and confirm it's correct.

### 5b. Pharmacy Master Data & Inventory (independent, can be done anytime, useful before 5a if catalog is empty)

1. **Manufacturer Master** — create a manufacturer.
2. **Category Master** — create a medicine category.
3. **Medicine Master** — create a new medicine (name, category, manufacturer, unit, batch tracking fields). Confirm it appears in the catalog and is selectable in prescriptions.
4. **Suppliers & Vendors** — create a supplier.
5. **Opening Stock / Purchase**: create a purchase order against the supplier for a medicine, confirm it, and confirm stock increases in the Inventory view.
6. **Expiry Management** — check a medicine batch with a near-expiry date shows up on the Expiry view/alerts.
7. **Returns & Refunds** — process a return of a dispensed medicine and confirm stock is correctly restored and a refund/credit is recorded.
8. **Reports & Analytics** — open the pharmacy reports view, confirm sales/stock reports render without error.

**Pass criteria:** Prescription-to-dispense flow correctly deducts stock and produces a bill; purchasing correctly increases stock; returns correctly restore it; insufficient stock is blocked rather than silently allowed to go negative.

---

## 6. Billing / RCM Deep Dive

(You've already touched billing in Sections 2, 3, and 5 — this section is for the parts not covered incidentally.)

1. As Admin, go to **Billing tab** — view the full list of all bills across OPD/IPD/Pharmacy. Filter/search by patient, date, status.
2. Pick an existing bill and **edit line items** (add/remove a charge) before it's fully paid — confirm the total recalculates correctly.
3. **Insurance / Cashless claim flow**: on an IPD bill, raise a cashless insurance pre-authorization claim (payer name, status). While the claim is `PENDING_AUTH`, `APPROVED`, or `SUBMITTED`:
   - Try to **mark the bill PAID/CLOSED** directly — confirm the system blocks this with a clear error about the active claim.
   - Try to **record a payment via the Pay action** on that bill — confirm this is also blocked (this was a specific bug fixed this session — the direct payment endpoint used to bypass the insurance freeze; it should not anymore).
   - Resolve/settle the claim (mark it approved and settled, or rejected), then confirm the bill CAN be paid/closed afterward.
4. **Advances & Refunds**: record an advance payment against a patient before their bill is finalized, confirm it's applied/adjusted against the final bill. Process a refund on an overpaid bill.
5. **Cross-tenant check** (if you have two hospitals set up from Section 0): as Hospital A's Receptionist, try to pay a bill belonging to Hospital B (you'd need the bill ID — ask your dev/tester partner for one, or just confirm you can't even see Hospital B's bills in any dropdown/search to begin with). This should be impossible — confirm you get a "not found" rather than being able to act on it.
6. Test all three **billing handler modes** from Section 1 Settings: `RECEPTIONIST`, `DOCTOR`, `BOTH` — for each, confirm only the permitted role(s) can access the Billing tab/actions, and the disallowed role gets a clear "forbidden" message rather than a silent failure.

**Pass criteria:** insurance freeze blocks both the status-update path and the direct pay path consistently; cross-tenant billing access is impossible; billing-handler role restriction is enforced correctly in all three modes.

---

## 7. Procurement / Inventory Flow (Hospital-side, non-pharmacy)

**Roles involved:** all currently operated through Hospital Admin (no dedicated Purchase Officer / Store Keeper login exists yet in the frontend — test these as Admin).

1. **General/Hospital Inventory tab** — add a few inventory items (consumables, linens, equipment) with opening stock quantities.
2. **Purchase Requisition**: raise a requisition (department, required date, priority, items).
3. **Approve the requisition.**
4. **Vendor**: create a vendor if none exists.
5. **Purchase Order**: create a PO against the vendor with specific items and quantities (this is what later GRN/invoice steps validate against).
6. **Approve the PO** (adds signature).
7. **Goods Receipt (GRN)**: confirm receipt of goods against the PO.
   - Test receiving the **correct** quantity — confirm it succeeds and stock increases.
   - Test receiving **more than what was ordered** — confirm this is now blocked with a clear "exceeds ordered quantity" error (new this session).
   - Try **confirming GRN twice** on the same PO — confirm the second attempt is blocked ("already been received") rather than double-counting stock (new this session).
8. **Invoice Verification (3-way match)**: submit a vendor invoice matched to the PO.
   - Test an invoice amount that roughly matches the PO's ordered value — should succeed.
   - Test an invoice amount **wildly higher** than the PO's ordered value (e.g. 3x) — confirm it's now rejected as not reconciling against the PO (new this session).
   - Test submitting the exact same invoice number twice — confirm the duplicate is blocked.
9. **Process Payment** against the verified invoice.
10. Confirm the PO/invoice list views on the Admin dashboard reflect all the above accurately.

**Pass criteria:** GRN respects ordered quantities and can't be double-confirmed; invoice verification rejects amounts that don't reconcile with the PO; duplicate invoices are blocked.

---

## 8. MRD Archive

1. After discharging a patient (Section 3), go to **MRD Archive** (Admin or Doctor dashboard).
2. Confirm the discharged admission's full record (assessments, consents, vitals, discharge summary) is browsable/searchable here.
3. Confirm you **cannot** edit a discharged/archived admission's clinical data from here (read-only archive).

---

## 9. Support / Feedback / Messaging

1. **Patient Feedback**: from the public feedback page (no login — get the link from Admin's Patient Feedback tab or ask your dev partner), submit feedback tied to a visit/token. Confirm it appears under Admin's **Patient Feedback tab**.
2. **Support Tickets**: as Hospital Admin, raise a support ticket to the platform (Tickets area). As Super Admin (Section 0), confirm the ticket appears under Platform → Tickets, respond to it, and confirm the Hospital Admin sees the response.
3. **Messages tab**: send/check any in-app messages/notifications between roles if this is wired up — confirm at least that the tab loads without error.
4. **WhatsApp notifications** (optional, depends on sandbox access): trigger a notification-worthy event (e.g. patient creation) and confirm a WhatsApp dispatch attempt is logged, even if you can't verify actual delivery.

---

## 10. Special Modes — Solo Doctor & Standalone Pharmacy

These change who can do what, so re-test a slice of the earlier flows under each mode.

### 10a. Solo Doctor Mode

1. As Hospital Admin, in Settings, flip `receptionMode` to `SOLO`.
2. Log in as the Doctor (or as the Hospital Admin flagged `isSingleDoctor`, who should be able to switch between Admin and Doctor dashboards — look for a dashboard-switcher control).
3. Confirm the Doctor dashboard now shows **self-service controls** that were previously Receptionist-only: add patient, add OPD visit, add appointment, and (if `IPD` module enabled) "Admit to IPD" directly.
4. Confirm `billingHandler` is now force-set to `DOCTOR` and the dropdown for it is disabled/greyed out in Settings.
5. Confirm the "Reception" staff tab is hidden from Admin's tab list while in SOLO mode.
6. Run a quick OPD cycle (register → consult → bill) entirely from the Doctor's dashboard without touching a Receptionist login, confirming nothing is missing.
7. Switch back to `HAS_RECEPTIONIST` afterward and confirm the Reception tab reappears and the self-service controls disappear from Doctor's dashboard again.

### 10b. Standalone Pharmacy Mode

1. This applies when a hospital's modules include `PHARMACY` but not `OPD` (set this up as a *separate* test hospital in Section 0 if you want to test it in isolation, rather than reconfiguring your main test hospital).
2. Confirm the Hospital Admin for such a tenant can access both the **Pharmacist Dashboard** and the **Admin Dashboard**, landing on the Pharmacy dashboard by default.
3. Run the Pharmacy flow (Section 5) end-to-end under this mode and confirm no OPD/IPD-dependent features leak into the UI (e.g. no broken links to a disabled OPD module).

---

## 11. Support Modules (quick smoke test — Admin only, no dedicated login yet)

For each of the following Admin dashboard tabs, just confirm the tab loads, you can create at least one record, and it saves/displays correctly. These don't have deep workflows yet, so a light pass is enough:

- **Emergency** — log an emergency department visit intake.
- **Blood Bank** — add a blood donor/unit record, test a cross-match/issue action.
- **CSSD** — log a sterilization cycle for an instrument set.
- **Biomedical Equipment** — add a piece of equipment, log a maintenance record.
- **Housekeeping** — create a cleaning task, mark it complete.
- **MIS Dashboard** — confirm the analytics/stats dashboard loads and shows numbers that roughly match what you've entered elsewhere (patient counts, revenue, etc.).
- **Reports & Analytics** — generate/export a report, confirm it downloads correctly.

---

## 12. Quality / Incident Reporting

1. Trigger an incident the "organic" way if possible — e.g. deliberately cause an OT instrument count mismatch (Section 4, step 7) and confirm it creates an incident report automatically.
2. As **Hospital Admin**, go to the Incident Reports area (`/hospital/quality/incidents`) and confirm the incident is listed.
   - **Note:** as of this session, this list is restricted to `HOSPITAL_ADMIN` and a `QUALITY_OFFICER` role (which has no dedicated login yet) — if you log in as a Doctor or Nurse and expect to see this list, you won't; that's intentional (previously any Doctor/Nurse could read all incident reports, which was tightened for privacy/security). Please flag if this breaks a workflow you expected to work, so we can reconsider.
3. Investigate/update the incident status as Doctor or Admin, add investigation notes, confirm it saves.

---

## 13. Patient Self-Service Portal

1. Get a patient's OTP-login access (from Patient Portal login page) — this may require a phone/OTP setup depending on config; check with your dev partner on how OTP is delivered in this environment (SMS gateway vs displayed in logs for testing).
2. Log in as the patient.
3. Confirm the patient can view their own visit history, prescriptions, bills, and discharge summaries (whatever's exposed on the self-service dashboard) — and importantly, **cannot** see any other patient's data.

---

## 14. Cross-Cutting Checks (do these throughout, not as a separate pass)

- **Tenant isolation**: whenever you have two hospitals set up, periodically try to access hospital A's data while logged in as hospital B's staff (via URL manipulation with a guessed/known ID if you're comfortable doing so) — this should always fail with "not found" or "access denied", never succeed.
- **Role gating**: whenever a tab/action is admin-only or role-restricted, log in as a different role and confirm you get a clean "forbidden" (403) message, not a crash or blank page.
- **Print/PDF outputs**: every PDF (prescription, receipt, discharge summary, consent form) should have correct hospital branding, correct patient/visit details, and no placeholder/lorem-ipsum text.
- **Audit logs**: spot-check that major actions (patient creation, admission, discharge, billing, staff creation) show up in the Hospital's Audit Logs tab with the correct actor and timestamp.

---

## Reporting bugs

For anything that looks wrong, note:
1. What you did (exact steps, which role you were logged in as)
2. What you expected to happen
3. What actually happened (include screenshot if visual, or the exact error message text if there was one)
4. Whether you could reproduce it a second time

Send these back as a list — plain text or a spreadsheet is fine, doesn't need to be formatted.
