# 10 — HOSPITAL WARDS & BEDS

**Baseline:** `aa143a7` · **Cases:** `TC-HWB-001` … `TC-HWB-024` · Tenant HOSPITAL_A · **IPD module** (tab `requiredModule: 'IPD'`)

**Screens:** Admin ▸ Rooms ▸ **Wards & Beds** (`WardsAndBeds.jsx`) · Incharge ▸ **Beds** (`WardBedsView.jsx`) · Reception ▸ IPD admit bed picker · ICU Bed Board.
**Bed states (`BedStatus`):** `available` · `occupied` · `cleaning` · `maintenance` — UI labels `Available`, `Occupied`, `Cleaning Required`, `Under Maintenance`.
**Rule:** **every** bed-status write goes through `BedStatusService` and is audited into `bed_status_audits`.
**Authorization:** `Bed` endpoints = `ADM` + `NI` only (GET and POST). `Ward` GET = `ADM DOC REC PHA`; POST/PUT/DELETE = `ADM`.

---

## A. WARD LIFECYCLE

### TC-HWB-001 — Create ward

`HOSPITAL_A · HOSPITAL_ADMIN · Wards · Critical · Wards & Beds`
**Steps:** create `General Ward A`; assign incharge `Sister Latha Menon`; save.
**Expected:** ward card appears with incharge name and bed count 0; toast `Ward incharge updated` when the incharge is set; NURSING is required to pick an incharge (the card notes `NURSING`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HWB-002 — Ward validation and duplicates

`HOSPITAL_A · HOSPITAL_ADMIN · Wards · High · Wards & Beds`
**Steps:** blank name; 200-char name; a name already used; emoji.
**Expected:** field messages; duplicate handled (record whether refused); no ward created.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HWB-003 — Edit ward / change incharge

`HOSPITAL_A · HOSPITAL_ADMIN · Wards · High · Wards & Beds`
**Steps:** rename the ward; change the incharge to `Sister Two`; check both incharges' dashboards.
**Expected:** rename propagates to the bed picker, IPD detail and ICU board; the old incharge loses the ward, the new one gains it immediately after refresh.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HWB-004 — Delete ward

`HOSPITAL_A · HOSPITAL_ADMIN · Wards · Critical · Wards & Beds ▸ Delete Ward`
**Steps:** (a) delete an **empty** ward; (b) delete a ward that has beds; (c) delete a ward with an **occupied** bed.
**Expected:** (a) removed after confirmation; (b) and (c) — record exactly what happens. A ward with an admitted patient must **not** be silently deletable; if it is, that is a **Critical** data-integrity bug.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HWB-005 — OT ward naming convention

`HOSPITAL_A · HOSPITAL_ADMIN · Wards · Medium · Wards & Beds`
**Steps:** create ward `OT-1` with a single bed; check the OT scheduling screen.
**Expected:** wards whose **name contains "OT"** are treated as theatre wards by the OT module; a single-bed OT ward is selectable when scheduling. Record the exact matching behaviour.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HWB-006 — ⭐ Ward without an incharge cannot receive admissions

`HOSPITAL_A · HOSPITAL_ADMIN → RECEPTIONIST · Wards · Critical · IPD admit`
**Steps:** = `TC-HNI-027`.
**Expected:** the ward is not offered, or admission is refused with a clear reason.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## B. BED LIFECYCLE

### TC-HWB-007 — Create beds

`HOSPITAL_A · HOSPITAL_ADMIN · Beds · Critical · Wards & Beds`
**Steps:** add `GA-01`…`GA-05` to `General Ward A`; check the incharge's Beds tab and the reception bed picker.
**Expected:** all five `Available` everywhere; ward bed count = 5.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HWB-008 — Bed validation and duplicates

`HOSPITAL_A · HOSPITAL_ADMIN · Beds · High · Wards & Beds`
**Steps:** blank bed number; duplicate `GA-01` in the same ward; `GA-01` in a **different** ward.
**Expected:** blank refused; duplicate within a ward refused; the same number in another ward — record (likely allowed).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HWB-009 — Edit / delete bed

`HOSPITAL_A · HOSPITAL_ADMIN · Beds · High · Wards & Beds`
**Steps:** rename `GA-05`; delete it; then try to delete an **occupied** bed.
**Expected:** rename propagates; empty bed deletes after confirmation; occupied bed deletion **refused** — allowing it would strand an admission (**Critical**).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HWB-010 — ⭐ Full occupancy cycle

`HOSPITAL_A · REC + NI · Beds · Critical · IPD / Beds`
**Steps:** 1. `GA-01` `Available`. 2. Reception admits P1 → **Occupied**. 3. Attempt a second admission into `GA-01` (UI picker and API). 4. Discharge P1 → **Cleaning Required**. 5. Attempt an admission into `GA-01` while cleaning. 6. Incharge **Mark Bed Cleaned** → **Available**. 7. Admit another patient successfully.
**Expected:** step 3 — not offered in the picker and API returns **409**; step 5 — still not admissible; step 6 — `Bed status updated`; step 7 — succeeds. This is the core bed-integrity assertion.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HWB-011 — Transfer frees the source bed

`HOSPITAL_A · DOCTOR/REC · Beds · Critical · /ipd/:id ▸ change bed`
**Steps:** move P1 from `GA-01` to `GA-02`.
**Expected:** `GA-01` → **Cleaning Required** (not straight to Available), `GA-02` → **Occupied**; both transitions audited.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HWB-012 — Maintenance excludes a bed from capacity

`HOSPITAL_A · NURSE_INCHARGE · Beds · High · Beds / Overview`
**Steps:** = `TC-HNI-015`: put `GA-03` under maintenance; read the admin Overview beds card; return it to available.
**Expected:** `usableCapacity = allBeds − maintenance`; the maintenance bed is excluded from capacity **and** from the admit picker; occupancy % recomputes.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HWB-013 — Invalid bed transitions

`HOSPITAL_A · NURSE_INCHARGE · Beds · Critical · Beds`
**Steps:** = `TC-HNI-016`.
**Expected:** cleaning an occupied bed and maintaining an occupied bed are refused; occupancy is never freed while a patient is admitted.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HWB-014 — Unknown / legacy bed status

`HOSPITAL_A · HOSPITAL_ADMIN · Beds · Medium · Overview`
**Steps:** read the Overview beds card's `unknownStatusCount`.
**Expected:** 0 on a clean QA database. If non-zero, some bed has a status outside the four — record it; it affects capacity maths.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## C. CROSS-ROLE CONSISTENCY

### TC-HWB-015 — One bed, four views

`HOSPITAL_A · ADM/REC/NI/NURSE · Beds · Critical · all bed screens`
**Steps:** for each state change in `TC-HWB-010`, check **simultaneously**: admin Wards & Beds, incharge Beds, reception admit picker, ICU Bed Board (for ICU beds), admin Overview card.
**Expected:** all five agree at every step, after refresh. A bed that is Available in one view and Occupied in another is **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HWB-016 — Bed history / audit

`HOSPITAL_A · NURSE_INCHARGE/ADMIN · Beds · High · Beds ▸ history`
**Steps:** = `TC-HNI-018`: open bed history after a full cycle; cross-check Audit Logs.
**Expected:** every transition with from/to state, actor and timestamp; `Failed to load bed history` on error.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HWB-017 — Ward scope for the incharge

`HOSPITAL_A · NURSE_INCHARGE ×2 · Beds · Critical · Beds`
**Steps:** = `TC-HNI-017`.
**Expected:** an incharge sees and changes only their wards' beds; cross-ward by id → 403/404.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## D. PERMISSIONS · GATING · ISOLATION · UI

### TC-HWB-018 — Bed endpoints are admin/incharge only

`HOSPITAL_A · REC/DOC/NURSE/PHA · Beds · High · API`
**Steps:** = `TC-PERM-022`: `GET` and `POST /hospital/beds` with each token.
**Expected:** **403** for reception, doctor, nurse, pharmacist — **but reception must still be able to admit** through `/hospital/ipd`. If reception cannot see beds at all yet must choose one, record how the picker is populated (it may come from the ward/IPD endpoints).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HWB-019 — Ward GET includes the pharmacist (discovery)

`HOSPITAL_A · PHARMACIST · Wards · Medium · API`
**Steps:** `GET /hospital/wards` with the pharmacist token.
**Expected:** matrix admits `PHA` — record the result and mark `NEEDS_PRODUCT_CONFIRMATION` (Tranche-1 item 3).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HWB-020 — IPD module off hides wards and beds

`HOSPITAL_M · HOSPITAL_ADMIN · Wards · High · sidebar/API`
**Steps:** = `TC-MOD-011`.
**Expected:** the Wards & Beds tab is hidden (`requiredModule: 'IPD'`); the **API result must be recorded per endpoint** — `/hospital/wards` and `/hospital/beds` carry no `@RequireModule`, so 200 is likely → `IMPLEMENTATION_DRIFT`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HWB-021 — Wards/beds tenant isolation

`HOSPITAL_A + B · ADM/NI · Wards · Critical · API`
**Steps:** = `TC-ISO-023`, `024`, `025`, `026`.
**Expected:** B sees no A wards/beds; A's bed id → 403/404; **A's bed status unchanged** by B's attempts; A's ward never appears in B's admit picker.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HWB-022 — Two receptionists, one last bed (manual concurrency)

`HOSPITAL_A · RECEPTIONIST ×2 · Beds · Critical · IPD admit`
**Steps:** one available bed `GA-01`; two tabs each with a pending admission; submit together.
**Expected:** exactly one admission succeeds; the other gets a **409**; the bed is occupied once. Two admissions on one bed is **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HWB-023 — Double-submit on bed actions

`HOSPITAL_A · ADM/NI · Beds · High · Wards & Beds / Beds`
**Steps:** double-click Add Bed, Mark Bed Cleaned, Put Under Maintenance.
**Expected:** one bed created; one transition each; no duplicate audit rows.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HWB-024 — Wards & Beds UI

`HOSPITAL_A · HOSPITAL_ADMIN · UI · Low · Wards & Beds`
**Steps:** no wards (empty state); a ward with 0 beds; 50 beds (scroll/paging); a 60-char ward name; confirmation dialogs on delete; F5; 768px.
**Expected:** EmptyState; long names truncate; delete always confirms; layout holds at tablet width.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
