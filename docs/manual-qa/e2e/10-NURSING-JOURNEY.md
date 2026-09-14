# 10 — NURSING JOURNEY

**Cases:** `TC-E2E-082` … `TC-E2E-086` · Tenant **HOSPITAL_A** · module **`NURSING`** (hospital-only)

The whole nursing module turns on one setting: **Separate Nurse Login**
(`hospital_settings.separate_nurse_login`, default **OFF**). Every case below must be run in the
mode stated — the two modes are genuinely different products.

## Hierarchy under test

`HOSPITAL_ADMIN` → `NURSE_INCHARGE` (a `NurseProfile` with `is_incharge=true` + a `NURSE_INCHARGE`
user, owning one or more wards via `Ward.incharge_nurse_id`) → `NURSE` (staff; **may have no login**).

### TC-E2E-082 — ⭐ Mode OFF: the incharge records care on behalf of a staff nurse

`HOSPITAL_A · ADM → NUR_INC · Nursing · Critical`
**Steps:** `TC-HAS-006` (confirm OFF) · `TC-HNI-006` · `TC-HNI-012` · `TC-HN-008` · `TC-HN-012`.
**E2E assertion:** with the setting **OFF**, a staff nurse created **without a login cannot sign in at all**, and the incharge's entry forms show a **"Performed By Nurse"** selector. Record vitals and a re-assessment note choosing that nurse; the saved record must show **the selected nurse as performer** while the audit/created-by shows the **incharge**. Two different people in two different fields — if the performer silently becomes the incharge, that is a **High** defect.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-083 — ⭐ Mode ON: the logged-in nurse is the performer, and assignment changes

`HOSPITAL_A · ADM → REC → NUR_INC → NUR · Nursing · Critical`
**Steps:** `TC-HAS-006` (turn ON) · `TC-HNI-014` · `TC-HN-003` · `TC-HN-008`.
**E2E assertion:** with the setting **ON** the "Performed By Nurse" selector **disappears** and the logged-in nurse is recorded. Then prove the three assignment branches from `PatientAssignmentService` by admitting to a ward with: **(a)** OFF → the incharge assigns; **(b)** ON + exactly **one** ward staff nurse → **auto-assigned**; **(c)** ON + **many** staff nurses → the **incharge assigns**. Record which branch fired for each admission. **Records created in the other mode must remain intact and correctly attributed after the switch.**
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-084 — Ward scope and "only your patients"

`HOSPITAL_A · NUR_INC + NUR · Nursing · Critical`
**Steps:** `TC-HNI-020` · `TC-HNI-021` · `TC-HN-020` · `TC-HN-021` · `TC-PERM-028`.
**E2E assertion:** an incharge sees and acts on **only their own wards' patients** (`NurseInchargeGuard.myWardIds()`), and a staff nurse on **only their assigned patients** (`NurseAccessGuard`). Take an out-of-scope patient id from another ward and call the nursing read **and** write endpoints directly — both must be refused. **Admin bypasses both guards** and sees everything; confirm that too, so an over-broad admin view is not mistaken for a leak.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-085 — Shifts, schedules and attendance

`HOSPITAL_A · ADM + NUR_INC · Nursing · High`
**Steps:** `TC-HA-018` (shift template) · `TC-HNI-025` · `TC-HNI-027` · `TC-HNI-029`.
**E2E assertion:** create a shift template and generate schedules; a `nurse_shift_schedule` **snapshots** the template's times, so **editing the template rewrites only FUTURE schedules** — today's and past rows keep their original times. Verify that explicitly. **"On shift now" is derived from today's schedule** — there is no manual toggle; a toggle would be a defect. Mark attendance twice for the same nurse/date and confirm it **upserts to one row** with the shift window snapshotted, not two rows.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-086 — Nursing records, Files & Access, and module revocation

`HOSPITAL_A · ADM + NUR + DOC · Nursing · Critical`
**Steps:** `TC-HN-008` … `TC-HN-016` (the 5 nursing forms) · `TC-HAS-013` · `TC-HD-030` · `TC-MOD-015`.
**E2E assertion:** walk all five nursing records — **`VITALS`, `NOTES` (Re-Assessment Sheet), `INITIAL_ASSESSMENT`, `VULNERABILITY_ASSESSMENT`, `SUGAR_CHART`** — fill, save, reopen, print. Each must appear **identically in the doctor's IPD case sub-tabs**, since `IpdDetails` mirrors the nurse tabs. Set one form to `DOCTOR`-only: the nurse's entry form hides, existing records stay **visible and printable**, and the nurse's **API write is refused**. Finally revoke `NURSING`: all nurse tabs vanish and the endpoints 403 **without re-login**; **the records are not deleted** — re-grant and confirm they return.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
