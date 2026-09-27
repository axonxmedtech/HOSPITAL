PHASE 1 — AUDIT

1. Current implementation
   Frequency entry is already a structured component, not free text: FrequencyInput.jsx. It renders three dose boxes (Morning-Afternoon-Night) plus an "As Per Required" (SOS/PRN) checkbox, and its value contract is a plain string — "1-0-1", "As Per Required", or "".

2. Components / pages involved
   Site File Purpose
   Doctor prescribing — Add/Edit Medicine ConsultationModal.jsx:1824 main OPD prescription
   Doctor prescribing — in-clinic administered row ConsultationModal.jsx:1483 medicines given at the clinic
   IPD medicine modal IpdDetails.jsx:1084 inpatient prescribing
   Catalogue default frequency MedicineInventoryTab.jsx:638 defaultFrequency per medicine
   Prescription presets PrescriptionPresetsManager.jsx:312 saved bundles
   In-clinic presets InClinicPresetsManager.jsx:268 saved bundles
   All six are entry points and all consume the same controlled value/onChange string contract.

3. State management
   Plain local useState in each parent (newMedicine, administeredList via updateAdministeredField, medicineModal, stockFormState, preset item arrays). FrequencyInput itself is stateless — it derives boxes from value via parse() and emits via compose().

4–5. API / services / backend
frequency reaches the server inside ConsultationRequest.PrescriptionItem / AdministeredItem, AddIpdPrescriptionRequest, PrescriptionPresetItemDTO, Medicine.defaultFrequency.
Persisted as Prescription.frequency — @Column(length = 50), a plain String. 6. Validation rules
Backend: only @NotBlank (ConsultationRequest.java:57). No format constraint at all.
Frontend: isFrequencyValid is the single rule — "As Per Required", or ^(\d)-(\d)-(\d)$ with at least one non-zero dose. Enforced at handleAddMedicine (ConsultationModal:540), validateMedicines (ConsultationModal:672/677), and both preset managers. 7. Existing tests
FrequencyInput.test.jsx — 2 tests (digit replacement, paste handling).
ConsultationModal.submission.test.jsx — drives frequency via getByLabelText('Morning dose'); asserts payload carries a frequency and that a missing frequency blocks submit.
ConsultationModal.medicineSelection.test.jsx, PrescriptionPresetsManager.test.jsx, MedicineInventoryTab.loadError.test.jsx render these surfaces. 8. Current workflow
Doctor picks a medicine → types Dosage (500mg) → types digits into three boxes → Duration → Instructions → + Add Medicine. Frequency is mandatory.

9. Reusable assets found
   FrequencyInput itself (compose/parse/isFrequencyValid) — the presets are just values it already understands.
   An established preset-chip visual pattern at ConsultationModal.jsx:1048: inline-flex px-3 py-1 text-xs font-medium bg-teal-50 text-teal-700 border border-teal-200 rounded-full hover:bg-teal-100.
10. Verified-not-missing
    Nothing anywhere parses the frequency string into slots. It is printed verbatim (ClinicalPdfService.java:156,242), displayed verbatim in the nurse MAR (MedicationPanel.jsx:404), and pharmacy explicitly documents that it does not derive quantities from it. No BD/TDS/OD/HS/QID mapping exists anywhere today.

PHASE 2 — FIT & IMPACT ANALYSIS
Classification: Frontend-only — existing API contract reused. No backend, schema or cross-module dependency.

# Question Finding

1 Where it fits A chip row inside FrequencyInput, above/beside the existing boxes
2 Reuse compose/parse/isFrequencyValid unchanged; existing chip styling
3 Minimal change One component + tests
4 API supports it? Yes — presets emit the same string the boxes already emit
5 Backend change? No
6 Schema change? No
7 Other module affected? Visual only, in the 6 entry surfaces
8 Shared contracts? Not for 4 of 5 presets — see the QID issue below
9 Permissions? No
10 Tenant isolation? No
11 Clinical workflows? Unchanged — manual entry preserved
12 Patient/admission workflows? Untouched
13 Regression risks Low: chips must be type="button" (they sit inside forms); the dense in-clinic cell must still wrap
14 Client-facing as specified? Yes for BD/TDS/OD/HS
⚠️ One dependency to report: 1-1-1-1 / QID does not fit the current contract
Four of five presets map cleanly onto the existing three slots:

Preset Value Fits today?
1-0-0 / OD 1-0-0 ✅
1-0-1 / BD 1-0-1 ✅
1-1-1 / TDS 1-1-1 ✅
0-0-1 / HS 0-0-1 ✅
1-1-1-1 / QID 1-1-1-1 ❌ rejected by isFrequencyValid
The backend would happily store "1-1-1-1" (7 chars in a varchar(50), no pattern rule), and it would print and display correctly. The blocker is entirely the frontend contract: the regex accepts exactly three slots, and parse() treats a 4-slot string as unrecognised legacy text and shows empty boxes.

Supporting QID means widening the shared frequency format from 3 slots to 3-or-4 and adding a 4th box everywhere. That is a domain-contract change, and it carries a real clinical ambiguity: in a 3-slot value the third digit means Night, whereas in a 4-slot value the third would mean Evening and the fourth Night — so historical and new records would read differently at the same position. Per the assignment ("if a task requires a change beyond the existing contract, STOP and report the dependency"), I am reporting this rather than expanding scope.

Options for the gate:

Option A (recommended) — ship OD / BD / TDS / HS now; raise QID with Sagar as a frequency-contract decision. Zero contract change, delivers 4/5 immediately.
Option B — widen the contract to 3-or-4 slots in this task: add a 4th box, update isFrequencyValid/parse/compose, and settle the Evening/Night semantics. Still frontend-only, but it changes a shared clinical format and touches all six surfaces.
Option C — ship QID as a 3-slot approximation. I do not recommend this: it would silently record a dose schedule the doctor did not choose.
PHASE 3 — IMPLEMENTATION PLAN
Current behavior
The prescriber types a digit into each of three boxes to build 1-0-1.

Desired behavior
A row of clickable chips — 1-0-0 OD, 1-0-1 BD, 1-1-1 TDS, 0-0-1 HS (plus 1-1-1-1 QID only under Option B). One click fills the boxes and emits the value. The chip matching the current value is shown as selected. Typing into the boxes still works exactly as today.

Files to change
frontend/src/components/FrequencyInput.jsx — add an exported FREQUENCY_PRESETS list and render the chip row.
frontend/src/components/FrequencyInput.test.jsx — extend.
Files to create
None.

Reuse
compose/parse/isFrequencyValid untouched; the existing teal pill styling; the existing value/onChange contract, so all six consumers get presets with no change to any parent file.

Changes (exact)
Export FREQUENCY_PRESETS = [{ value:'1-0-0', label:'OD' }, …] — one list, the single source of truth.
Render chips inside the existing flex-wrap container: type="button", aria-pressed when the chip equals the current value, title giving the full meaning (e.g. "Once daily — morning").
Click handler: onChange(preset.value) — which inherently clears the SOS state, since a preset is not "As Per Required".
Respect the existing disabled prop.
Tests
Added to FrequencyInput.test.jsx:

clicking each preset emits the exact expected string;
boxes visibly reflect the clicked preset;
the chip matching the current value is aria-pressed;
clicking a preset while "As Per Required" is ticked clears SOS;
manual typing after a preset click still works (the explicit requirement);
chips are type="button" so they never submit the surrounding form;
chips honour disabled.
Plus one integration test in ConsultationModal.submission.test.jsx: add a medicine by clicking a preset instead of typing, and assert the submitted payload carries that frequency.

Regression checks
FrequencyInput.test.jsx (2 existing), ConsultationModal.submission.test.jsx, ConsultationModal.medicineSelection.test.jsx, PrescriptionPresetsManager.test.jsx, MedicineInventoryTab.loadError.test.jsx, then the full 413-test suite, npm run typecheck, npm run build.

Scope check
✅ no core patient/admission architecture change
✅ no database/schema change
✅ no cross-module clinical workflow change
✅ no unrelated refactor
✅ no mock/fake production data
✅ no backend/API/DTO change
Dependencies
1-1-1-1 / QID needs a decision from Sagar (Option A vs B above). Everything else is unblocked.

⛔ APPROVAL GATE — STOPPED
Nothing has been implemented. Two things I need from you before Phase 4:

Approve the plan, and
Choose A or B for QID (I recommend A).
Also confirm the branch: I'm currently on create-patient-feature (clean, and its work is already pushed). The assignment requires a separate branch — I'll cut feat/medication-quick-presets from origin/staging unless you'd rather it come off something else.
