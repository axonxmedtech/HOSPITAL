CHECKPOINT REPORT
Task
Injection Food-Timing UI Rule

Before
The IPD "Add Medicine" form always offered Food Timing, whatever the medicine was. Nothing tied it to type or route — not in the UI, and not on the server, which validates the value but has no cross-field rule. So a doctor could pick "After food", change the order to an injection, and the stale value was submitted and stored. It then surfaced on the nurse's medication chart as "After food" beside an IV drug — a meal instruction for a dose that never meets a meal.

There were two ways in, not one. The second is the likelier: the catalogue click handler sets type: m.type?.toUpperCase(), so picking an injection from the catalogue turned the order into an injection without the doctor ever touching the Type field.

Implemented
isFoodTimingApplicable(type, route) in utils/foodTiming.js, beside the vocabulary it governs. Not applicable for types INJECTION / IV_FLUID and routes IV / IM / SUBCUTANEOUS — the server's full vocabulary, not just the subset this form offers. Case-insensitive and whitespace-tolerant. Type and route are judged independently, because in practice they disagree: a catalogue injection sets the type and leaves the route on its ORAL default. Unknown, null and blank stay applicable — this may hide a question that cannot apply, never one that might.
Disabled, not hidden, with Not applicable for injections. beneath it, wired via aria-describedby so the reason reaches screen readers too. Layout stays stable.
Cleared on every path that can make an order parenteral — Type, Route, and the catalogue pick — so the form never displays an answer to a question it has stopped asking.
Authoritative payload guard: foodTiming is decided at submit from the order itself, so no path — present or future — can put a meal time on an injection. The server already reads null as "not stated".
Files Changed
File Change
frontend/src/utils/foodTiming.js +25 — the predicate and its two vocabulary sets
frontend/src/pages/hospital/IpdDetails.jsx +71/−7 — disable + message, three clearing paths, payload guard
frontend/src/utils/foodTiming.test.js +37 — 5 new tests
frontend/src/pages/hospital/IpdDetails.foodTiming.test.jsx new, 222 lines — 9 tests
Every one of the 7 removed lines is one I intentionally replaced (verified line by line in the diff).

Architecture / API impact
None. Frontend-only. No backend, DTO, entity, schema, endpoint or domain-model change — git status -- backend is empty and the only files differing from origin/staging are the four above. The existing foodTiming contract is reused exactly: it was already nullable, and null already meant "not stated" (deliberately distinct from NOT_SPECIFIED). No permissions or tenant-isolation surface touched.

Regression Check
Oral prescribing is unchanged and proven: tablet and syrup both still select and submit AFTER_FOOD / BEFORE_FOOD, and an unstated oral order still sends null.
Historical data untouched: describeFoodTiming has zero diff lines and MedicationPanel.jsx is not in the changed-file list at all. The permissive display of unrecognised historical values is covered by the pre-existing tests, which still pass. No stored prescription is modified.
OPD unaffected: ConsultationModal has no type/route/foodTiming field — only a free-text instructions box — so the rule neither applies nor was applied there. Its submission suite passes untouched.
ESLint on IpdDetails.jsx: 4 warnings before, 4 after — I introduced none.
Tests
All nine of your required verifications, plus the util layer:

# Verification Covered by Result

1 Oral tablet works & submitted component ✅
2 Oral syrup works & submitted component ✅
3 Injection → disabled component ✅
4 After Food → INJECTION → null component ✅
5 Catalogue injection clears stale value component ✅
6 Back to applicable → available again component ✅
7 IV / IM / SUBCUTANEOUS non-applicable component (IV, IM) + util (all three) ✅
8 Historical MAR display unchanged foodTiming.test.js + MedicationPanel.stock.test.jsx ✅
9 Unrelated prescription behavior unchanged ConsultationModal.submission.test.jsx + full suite ✅
foodTiming.test.js — 9 passed (4 pre-existing + 5 new)
IpdDetails.foodTiming.test.jsx — 9 passed (new file for a previously untested 2,200-line page)
Targeted + regression set — 36 passed
Full suite — 472 tests, 71 files, green on 3 consecutive runs (no flakes this time)
Type Check
npm run typecheck — exit 0, clean.

Build
npm run build (tsc + vite) — ✓ built.

Scope Check
✅ no backend / API / DTO / schema / database change
✅ no new domain models
✅ no core patient/admission architecture change
✅ no cross-module clinical workflow change
✅ no change to OPD food/instruction behavior
✅ no change to nurse MAR behavior or describeFoodTiming
✅ no unrelated refactoring
✅ no mock/fake production data
✅ only the four approved files
Two disclosures:

I also clear foodTiming on the Route dropdown, not only Type and catalogue. Your decision list named Type and catalogue, but the wider rule you approved makes IV/IM/SUBCUTANEOUS non-applicable too — without this, changing Route to IV would disable the control while it still visibly held "After food". The payload guard already covered the wire; this keeps the screen coherent. Flagging it as a deliberate, one-line addition beyond the literal enumeration.
The pre-commit hook's prettier --write wrapped one pre-existing line — the Food Timing <label>, directly adjacent to my change. No unrelated churn.
Commit
ee3d6b6 — feat: do not offer or submit food timing for injections

Branch feat/injection-food-timing-rule, based on origin/staging (f93671c), not stacked on feat/medication-quick-presets — Task 1's commit is verifiably absent. Not pushed. Your untracked docs/tasks/ was left out of the commit.

Issues / Dependencies
None blocking.

One observation for Sagar, unchanged from the audit and not acted on: the IPD form offers only 3 of the 8 types and 3 of the 8 routes the backend accepts, so TOPICAL, INHALATION, OPHTHALMIC and OTIC orders cannot be entered at all today. My predicate already handles the full vocabulary, so widening the dropdowns later needs no change here.

Stopping as instructed — not starting Task 3.
