# Medicine Administration One-Tap — Design Spec (N2)

**Date:** 2026-06-30
**Scope:** Frontend only — `NurseDashboard.jsx`, single file change, no new files, no backend changes

---

## Goal

Replace the single "💊 Administer" button on medication task cards with three inline one-tap actions: **Given**, **Snooze**, and **Not Given**. The most common action (oral medication given) submits in a single tap with no modal. Injectable medications fall back to the existing full modal automatically.

---

## 1. Button Replacement

For `MEDICATION` task cards only, the existing `💊 Administer` button is replaced with three buttons:

```
[ ✓ Given ]   [ ⏰ Snooze ]   [ ✗ Not Given ]
```

Non-medication tasks (`📋 Execute`) keep the existing single button that opens `MedicationAdministrationModal` — one-tap does not apply to procedures.

---

## 2. ✓ Given — One-Tap Submit

**Happy path (oral/non-injectable):**

Tapping Given calls `nurseService.executeTask` with:
```js
{ status: 'DONE', route: 'ORAL', administeredQuantity: 1.0 }
```

No modal. On success, `loadTasks()` is called to refresh the list (task disappears).

**Injectable fallback:**

If `isInjectable(task)` returns true, tapping Given opens the existing `MedicationAdministrationModal` (sets `administerTarget = task`) instead of submitting directly. The modal already handles injection site collection.

**Injectable detection** (pure function):
```js
const INJECTABLE_KEYWORDS = ['IV', 'IM', 'SUB-Q', 'SUBQ', 'SUBCUTANEOUS', 'INTRAMUSCULAR', 'INTRAVENOUS'];
function isInjectable(task) {
  const desc = (task.orderDescription || '').toUpperCase();
  return INJECTABLE_KEYWORDS.some(k => desc.includes(k));
}
```

---

## 3. ⏰ Snooze — One-Tap Hold

Tapping Snooze submits immediately:
```js
{ status: 'HELD', notes: 'Snoozed — retry shortly' }
```

No modal. Maps to existing HELD status. On success, `loadTasks()` refreshes the list.

---

## 4. ✗ Not Given — Inline Reason Picker

Tapping Not Given does NOT open a modal. Instead, the task card expands inline to show a reason picker:

```
Why wasn't this given?
[ Hold ]  [ Refuse ]  [ Skip ]

Reason: [_______________________________]

[ Cancel ]   [ Confirm ]
```

- Three chip buttons: Hold → HELD, Refuse → REFUSED, Skip → SKIPPED
- Default selection: HELD
- Reason input: single-line, required (cannot confirm without text)
- Cancel: collapses picker back to three action buttons
- Confirm: submits `{ status: selectedStatus, notes: reasonText }`, then `loadTasks()`

**Inline toggle:** Controlled by `notGivenOpenId` state — only one card can have the picker open at a time. Opening a new picker closes the previous one.

---

## 5. Loading State

While a submit is in flight, all three buttons on that card are disabled and the active button shows a spinner/ellipsis. Controlled by `submittingId` state (holds the `task.id` of the card being submitted, or `null`).

---

## 6. Error Handling

On submit failure, show an inline error below the card buttons (not a toast):
```
⚠ Failed to record. Try again.
```

Tracked by `errorId` state (holds `task.id` of the failed card, or `null`). Error clears on next action.

---

## 7. New State Variables

```js
const [notGivenOpenId, setNotGivenOpenId] = useState(null);   // task.id with picker open
const [notGivenStatus, setNotGivenStatus] = useState('HELD');  // chip selection
const [notGivenReason, setNotGivenReason] = useState('');      // reason text
const [submittingId, setSubmittingId] = useState(null);        // task.id being submitted
const [errorId, setErrorId] = useState(null);                  // task.id with inline error
```

---

## 8. New Handler Functions

Defined before the JSX return:

```js
async function handleGiven(task) {
  if (isInjectable(task)) {
    setAdministerTarget(task);
    return;
  }
  setSubmittingId(task.id);
  setErrorId(null);
  try {
    await nurseService.executeTask(task.ipdAdmissionId, task.id, {
      status: 'DONE', route: 'ORAL', administeredQuantity: 1.0,
    });
    loadTasks();
  } catch {
    setErrorId(task.id);
  } finally {
    setSubmittingId(null);
  }
}

async function handleSnooze(task) {
  setSubmittingId(task.id);
  setErrorId(null);
  try {
    await nurseService.executeTask(task.ipdAdmissionId, task.id, {
      status: 'HELD', notes: 'Snoozed — retry shortly',
    });
    loadTasks();
  } catch {
    setErrorId(task.id);
  } finally {
    setSubmittingId(null);
  }
}

async function handleNotGiven(task) {
  if (!notGivenReason.trim()) return;
  setSubmittingId(task.id);
  setErrorId(null);
  try {
    await nurseService.executeTask(task.ipdAdmissionId, task.id, {
      status: notGivenStatus, notes: notGivenReason.trim(),
    });
    setNotGivenOpenId(null);
    setNotGivenReason('');
    setNotGivenStatus('HELD');
    loadTasks();
  } catch {
    setErrorId(task.id);
  } finally {
    setSubmittingId(null);
  }
}
```

---

## 9. Architecture

| Constraint | Detail |
|---|---|
| Files changed | `frontend/src/pages/hospital/NurseDashboard.jsx` only |
| New state variables | 5 — `notGivenOpenId`, `notGivenStatus`, `notGivenReason`, `submittingId`, `errorId` |
| New functions | `isInjectable(task)`, `handleGiven(task)`, `handleSnooze(task)`, `handleNotGiven(task)` |
| New components | None — inline JSX in task card map |
| MedicationAdministrationModal | Preserved unchanged — used for injectable fallback and procedure tasks |
| Backend | Unchanged — uses existing `executeTask` endpoint with existing status values |
| Non-medication tasks | Unchanged — single Execute button, existing modal |
