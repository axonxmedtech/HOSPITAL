# Medicine Administration One-Tap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the single "💊 Administer" button on medication task cards with three inline one-tap actions — Given, Snooze, Not Given — so the common case (oral medication given) submits in a single tap with no modal.

**Architecture:** Single file change — `frontend/src/pages/hospital/NurseDashboard.jsx`. Task 1 adds 5 state variables and 4 handler/helper functions before the JSX return. Task 2 replaces the action button area inside `sortedTasks.map()` with the three-button row and inline reason picker. No new files, no backend changes, no changes to `MedicationAdministrationModal`.

**Tech Stack:** React 18, Tailwind CSS, existing `nurseService.executeTask()`

---

## File Map

| Action | File |
|--------|------|
| Modify | `frontend/src/pages/hospital/NurseDashboard.jsx` |

---

### Task 1: State Variables + Handler Functions

**Files:**
- Modify: `frontend/src/pages/hospital/NurseDashboard.jsx`

- [ ] **Step 1: Read the file**

Open `frontend/src/pages/hospital/NurseDashboard.jsx`. Locate:
- The state declarations block (lines 7–14, ends with `administerTarget`)
- The `getTimeLabel` function (last function before `if (selectedAdmission)`)
- The `if (selectedAdmission)` early return

- [ ] **Step 2: Add 5 new state variables**

Find the last existing state declaration:
```jsx
  const [administerTarget, setAdministerTarget] = useState(null);
```

Replace it with:
```jsx
  const [administerTarget, setAdministerTarget] = useState(null);
  const [notGivenOpenId, setNotGivenOpenId] = useState(null);
  const [notGivenStatus, setNotGivenStatus] = useState('HELD');
  const [notGivenReason, setNotGivenReason] = useState('');
  const [submittingId, setSubmittingId] = useState(null);
  const [errorId, setErrorId] = useState(null);
```

- [ ] **Step 3: Add helper and handler functions before `if (selectedAdmission)`**

Find the closing brace of `getTimeLabel` function, then the blank line before `if (selectedAdmission) {`. Insert after that blank line:

```jsx
  const INJECTABLE_KEYWORDS = ['IV', 'IM', 'SUB-Q', 'SUBQ', 'SUBCUTANEOUS', 'INTRAMUSCULAR', 'INTRAVENOUS'];
  function isInjectable(task) {
    const desc = (task.orderDescription || '').toUpperCase();
    return INJECTABLE_KEYWORDS.some(k => desc.includes(k));
  }

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

- [ ] **Step 4: Build to verify**

```bash
cd e:/Projects/HOSPITAL/frontend && npm run build 2>&1 | tail -5
```

Expected: `✓ built in X.XXs` — no errors. (Functions are defined but not yet wired to UI.)

- [ ] **Step 5: Commit**

```bash
cd e:/Projects/HOSPITAL && git add frontend/src/pages/hospital/NurseDashboard.jsx && git commit -m "feat(nurse): one-tap state and handler functions — Given, Snooze, Not Given"
```

---

### Task 2: Replace Action Button Area in Task Card

**Files:**
- Modify: `frontend/src/pages/hospital/NurseDashboard.jsx`

- [ ] **Step 1: Find the action button div in the task card map**

In the `sortedTasks.map(task => { ... })` block, find this exact JSX (the action button area):

```jsx
                <div className="flex gap-2 ml-4 shrink-0">
                  <button
                    onClick={() => setAdministerTarget(task)}
                    className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white font-semibold text-xs rounded-xl shadow-sm transition-all active:scale-95"
                  >
                    {task.orderType === 'MEDICATION' ? '💊 Administer' : '📋 Execute'}
                  </button>
                </div>
```

- [ ] **Step 2: Replace the entire action button div**

Replace the block found in Step 1 with:

```jsx
                <div className="ml-4 shrink-0">
                  {task.orderType === 'MEDICATION' ? (
                    <div>
                      {notGivenOpenId === task.id ? (
                        <div className="flex flex-col gap-2 min-w-[220px]">
                          <p className="text-xs font-semibold text-gray-600">Why wasn't this given?</p>
                          <div className="flex gap-1.5">
                            {[
                              { key: 'HELD', label: 'Hold' },
                              { key: 'REFUSED', label: 'Refuse' },
                              { key: 'SKIPPED', label: 'Skip' },
                            ].map(opt => (
                              <button
                                key={opt.key}
                                type="button"
                                onClick={() => setNotGivenStatus(opt.key)}
                                className={`px-2.5 py-1 text-xs font-semibold rounded-lg border transition-all ${
                                  notGivenStatus === opt.key
                                    ? 'bg-gray-800 text-white border-gray-800'
                                    : 'bg-white text-gray-600 border-gray-300 hover:border-gray-400'
                                }`}
                              >
                                {opt.label}
                              </button>
                            ))}
                          </div>
                          <input
                            type="text"
                            value={notGivenReason}
                            onChange={e => setNotGivenReason(e.target.value)}
                            placeholder="Reason (required)"
                            className="text-xs border border-gray-300 rounded-lg px-2.5 py-1.5 focus:outline-none focus:ring-2 focus:ring-blue-300"
                          />
                          {errorId === task.id && (
                            <p className="text-xs text-red-600">⚠ Failed to record. Try again.</p>
                          )}
                          <div className="flex gap-2">
                            <button
                              type="button"
                              onClick={() => { setNotGivenOpenId(null); setNotGivenReason(''); setNotGivenStatus('HELD'); }}
                              className="flex-1 px-3 py-1.5 text-xs font-medium border border-gray-200 rounded-lg text-gray-600 hover:bg-gray-50 transition-colors"
                            >
                              Cancel
                            </button>
                            <button
                              type="button"
                              onClick={() => handleNotGiven(task)}
                              disabled={!notGivenReason.trim() || submittingId === task.id}
                              className="flex-1 px-3 py-1.5 text-xs font-semibold bg-gray-800 text-white rounded-lg hover:bg-gray-900 disabled:opacity-50 transition-all"
                            >
                              {submittingId === task.id ? '…' : 'Confirm'}
                            </button>
                          </div>
                        </div>
                      ) : (
                        <div className="flex flex-col gap-1.5 items-end">
                          <div className="flex gap-1.5">
                            <button
                              type="button"
                              onClick={() => handleGiven(task)}
                              disabled={submittingId === task.id}
                              className="px-3 py-1.5 text-xs font-semibold bg-green-600 hover:bg-green-700 text-white rounded-xl shadow-sm transition-all active:scale-95 disabled:opacity-50"
                            >
                              {submittingId === task.id ? '…' : '✓ Given'}
                            </button>
                            <button
                              type="button"
                              onClick={() => handleSnooze(task)}
                              disabled={submittingId === task.id}
                              className="px-3 py-1.5 text-xs font-semibold bg-amber-500 hover:bg-amber-600 text-white rounded-xl shadow-sm transition-all active:scale-95 disabled:opacity-50"
                            >
                              ⏰ Snooze
                            </button>
                            <button
                              type="button"
                              onClick={() => { setNotGivenOpenId(task.id); setNotGivenReason(''); setNotGivenStatus('HELD'); }}
                              disabled={submittingId === task.id}
                              className="px-3 py-1.5 text-xs font-semibold bg-white hover:bg-gray-50 text-gray-700 border border-gray-300 rounded-xl shadow-sm transition-all active:scale-95 disabled:opacity-50"
                            >
                              ✗ Not Given
                            </button>
                          </div>
                          {errorId === task.id && (
                            <p className="text-xs text-red-600">⚠ Failed to record. Try again.</p>
                          )}
                        </div>
                      )}
                    </div>
                  ) : (
                    <button
                      onClick={() => setAdministerTarget(task)}
                      className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white font-semibold text-xs rounded-xl shadow-sm transition-all active:scale-95"
                    >
                      📋 Execute
                    </button>
                  )}
                </div>
```

- [ ] **Step 3: Self-review checklist**

Before building, verify:
- [ ] `notGivenOpenId === task.id` controls which card shows the reason picker
- [ ] Three chip buttons set `notGivenStatus` (HELD / REFUSED / SKIPPED)
- [ ] Confirm button calls `handleNotGiven(task)`, disabled when reason is empty or submitting
- [ ] Cancel resets `notGivenOpenId`, `notGivenReason`, `notGivenStatus`
- [ ] ✓ Given calls `handleGiven(task)` — injectable check is inside the handler
- [ ] ⏰ Snooze calls `handleSnooze(task)`
- [ ] ✗ Not Given sets `notGivenOpenId = task.id`
- [ ] `submittingId === task.id` disables all buttons on the active card
- [ ] `errorId === task.id` shows inline error in both views (picker and button row)
- [ ] Non-MEDICATION tasks render the single `📋 Execute` button calling `setAdministerTarget`
- [ ] `MedicationAdministrationModal` block below `sortedTasks.map()` is untouched

- [ ] **Step 4: Build and verify**

```bash
cd e:/Projects/HOSPITAL/frontend && npm run build 2>&1 | tail -5
```

Expected: `✓ built in X.XXs` with no errors.

- [ ] **Step 5: Commit**

```bash
cd e:/Projects/HOSPITAL && git add frontend/src/pages/hospital/NurseDashboard.jsx && git commit -m "feat(nurse): one-tap Given/Snooze/Not Given buttons with inline reason picker"
```

---

## Self-Review

**Spec coverage:**
- ✅ Button replacement (MEDICATION → three buttons, non-MEDICATION → Execute unchanged) — Task 2 Step 2
- ✅ ✓ Given: submits DONE/ORAL/1.0 immediately — Task 1 Step 3 `handleGiven`
- ✅ Injectable fallback: `isInjectable` check → opens `MedicationAdministrationModal` — Task 1 Step 3
- ✅ Injectable keywords: IV, IM, SUB-Q, SUBQ, SUBCUTANEOUS, INTRAMUSCULAR, INTRAVENOUS — Task 1 Step 3
- ✅ ⏰ Snooze: submits HELD with 'Snoozed — retry shortly' — Task 1 Step 3 `handleSnooze`
- ✅ ✗ Not Given: opens inline reason picker, not a modal — Task 2 Step 2
- ✅ Reason picker: three chips (Hold/Refuse/Skip), required reason input, Cancel/Confirm — Task 2 Step 2
- ✅ Only one picker open at a time (`notGivenOpenId` holds task.id) — Task 2 Step 2
- ✅ Loading state: `submittingId` disables all buttons on active card — Task 1 Step 2 + Task 2 Step 2
- ✅ Error state: `errorId` shows inline error below buttons — Task 1 Step 2 + Task 2 Step 2
- ✅ Error shown in both picker view and button row view — Task 2 Step 2
- ✅ MedicationAdministrationModal preserved for injectables and procedures — Task 2 Step 2

**Placeholder scan:** None found.

**Type consistency:** `notGivenStatus` initialized as `'HELD'` in state (Task 1) and set to `'HELD'` / `'REFUSED'` / `'SKIPPED'` in picker (Task 2). `handleNotGiven` reads `notGivenStatus` (Task 1). `handleGiven`, `handleSnooze`, `handleNotGiven` all take `task` parameter with `.id`, `.ipdAdmissionId`, `.orderDescription`, `.orderType` — same shape as existing task objects throughout the file.
