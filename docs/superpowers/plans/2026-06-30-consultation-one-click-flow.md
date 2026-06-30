# Consultation One-Click Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Next Patient" call-to-action widget to the doctor overview tab so the doctor can start a consultation with a single tap from the overview, without scanning a table or switching tabs.

**Architecture:** Single file change — `frontend/src/pages/hospital/DoctorDashboard.jsx`. Task 1 adds four derived constants before the JSX return. Task 2 inserts the widget JSX between the Quick Actions row and the dual-panel grid. No new state, no new API calls, no new files — everything derives from the existing `queueEntries` state and calls the existing `handleStartOpdConsultation` handler.

**Tech Stack:** React 18, Tailwind CSS, existing `queueEntries` + `startingConsultationId` state, existing `handleStartOpdConsultation(opd)` handler

---

## File Map

| Action | File |
|--------|------|
| Modify | `frontend/src/pages/hospital/DoctorDashboard.jsx` |

---

### Task 1: Derived Queue Constants

**Files:**
- Modify: `frontend/src/pages/hospital/DoctorDashboard.jsx`

- [ ] **Step 1: Read the file landmarks**

Open `frontend/src/pages/hospital/DoctorDashboard.jsx`. Confirm:
- Lines ~912–914: `const todayLabel = new Date().toLocaleDateString('en-IN', { ... });` — last constant before `return (`
- Line ~916: `return (` — start of JSX
- Line ~84: `const [queueEntries, setQueueEntries] = useState([]);` — existing state
- Line ~840: `const [startingConsultationId, setStartingConsultationId] = useState(null);` — existing state

- [ ] **Step 2: Add four derived constants after `todayLabel`**

Find:
```jsx
    const todayLabel = new Date().toLocaleDateString('en-IN', {
        weekday: 'long', day: 'numeric', month: 'long', year: 'numeric'
    });
```

Replace with:
```jsx
    const todayLabel = new Date().toLocaleDateString('en-IN', {
        weekday: 'long', day: 'numeric', month: 'long', year: 'numeric'
    });

    const sortedQueue = [...queueEntries].sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));
    const consultingEntry = sortedQueue.find(q => q.opd?.status === 'CONSULTING');
    const nextQueuedEntry = sortedQueue.find(q => q.opd?.status === 'QUEUED');
    const nextQueuedToken = nextQueuedEntry ? sortedQueue.indexOf(nextQueuedEntry) + 1 : null;
```

- [ ] **Step 3: Build to verify**

```bash
cd e:/Projects/HOSPITAL/frontend && npm run build 2>&1 | tail -5
```

Expected: `✓ built in X.XXs` — zero errors.

- [ ] **Step 4: Commit**

```bash
cd e:/Projects/HOSPITAL && git add frontend/src/pages/hospital/DoctorDashboard.jsx && git commit -m "feat(doctor): derived queue constants for consultation one-click widget"
```

---

### Task 2: Insert One-Click Widget JSX

**Files:**
- Modify: `frontend/src/pages/hospital/DoctorDashboard.jsx`

- [ ] **Step 1: Find the insertion point**

In the overview tab JSX, find this comment (around line 1071):
```jsx
                            {/* Side-by-Side Lists: Appointments and Queue */}
```

The widget inserts immediately before this comment, after the closing `</div>` of the Quick Actions row.

- [ ] **Step 2: Insert the widget**

Find:
```jsx
                            {/* Side-by-Side Lists: Appointments and Queue */}
```

Replace with:
```jsx
                            {/* Consultation One-Click Widget */}
                            {queueEntries.length > 0 && (
                                <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
                                    <div className="flex items-center justify-between mb-4">
                                        <h3 className="text-base font-bold text-gray-900">
                                            {consultingEntry ? 'Now Consulting' : 'Next Patient'}
                                        </h3>
                                        <span className={`text-xs font-semibold px-2.5 py-1 rounded-full ${
                                            queueEntries.length >= 8
                                                ? 'bg-red-100 text-red-700'
                                                : queueEntries.length >= 4
                                                ? 'bg-amber-100 text-amber-700'
                                                : 'bg-green-100 text-green-700'
                                        }`}>
                                            {queueEntries.length} waiting
                                        </span>
                                    </div>

                                    {consultingEntry && (
                                        <div className="flex items-center gap-3 mb-3">
                                            <span className="text-sm font-semibold text-gray-800">
                                                {consultingEntry.opd?.patient?.name || consultingEntry.opd?.patientName || 'Unknown'}
                                            </span>
                                            <span className="text-xs font-bold px-2 py-0.5 rounded-full bg-green-100 text-green-700">
                                                CONSULTING
                                            </span>
                                        </div>
                                    )}

                                    {consultingEntry && nextQueuedEntry && (
                                        <div className="border-t border-gray-100 my-3" />
                                    )}

                                    {nextQueuedEntry && (
                                        <div className="flex items-center justify-between gap-4">
                                            <div>
                                                {consultingEntry && (
                                                    <p className="text-xs font-semibold text-gray-400 uppercase tracking-wide mb-1">Up Next</p>
                                                )}
                                                <p className="text-sm font-bold text-gray-900">
                                                    {nextQueuedEntry.opd?.patient?.name || nextQueuedEntry.opd?.patientName || 'Unknown'}
                                                </p>
                                                <p className="text-xs text-gray-500 mt-0.5">
                                                    Token #{nextQueuedToken} · Waiting since {new Date(nextQueuedEntry.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                                                </p>
                                            </div>
                                            <button
                                                onClick={() => handleStartOpdConsultation(nextQueuedEntry.opd)}
                                                disabled={!!startingConsultationId}
                                                className="shrink-0 px-4 py-2 bg-slate-900 hover:bg-slate-800 text-white text-sm font-semibold rounded-xl transition-all active:scale-95 disabled:opacity-50 disabled:cursor-not-allowed"
                                            >
                                                {startingConsultationId ? 'Starting…' : 'Start Consultation →'}
                                            </button>
                                        </div>
                                    )}

                                    {consultingEntry && !nextQueuedEntry && (
                                        <p className="text-sm text-gray-500">No more patients in queue</p>
                                    )}
                                </div>
                            )}

                            {/* Side-by-Side Lists: Appointments and Queue */}
```

IMPORTANT: The `{/* Side-by-Side Lists: Appointments and Queue */}` comment MUST remain as the last line of the replacement.

- [ ] **Step 3: Self-review checklist**

Before building, verify:
- [ ] Widget wrapped in `{queueEntries.length > 0 && (...)}` — not rendered when queue is empty
- [ ] Header shows "Now Consulting" when `consultingEntry` is truthy, "Next Patient" otherwise
- [ ] "N waiting" badge uses `queueEntries.length` with three color tiers (green/amber/red)
- [ ] Consulting row renders only when `consultingEntry` is truthy
- [ ] Divider renders only when BOTH `consultingEntry` and `nextQueuedEntry` are truthy
- [ ] "Up Next" label renders only when `consultingEntry` is truthy (not when it's the only card)
- [ ] "No more patients in queue" shows when `consultingEntry` exists but `nextQueuedEntry` is null
- [ ] Button calls `handleStartOpdConsultation(nextQueuedEntry.opd)`
- [ ] Button disabled when `!!startingConsultationId` — shows "Starting…" label
- [ ] Patient name uses `entry.opd?.patient?.name || entry.opd?.patientName || 'Unknown'` fallback chain
- [ ] `nextQueuedToken` used for Token # display
- [ ] `{/* Side-by-Side Lists: Appointments and Queue */}` comment preserved as last line

- [ ] **Step 4: Build and verify**

```bash
cd e:/Projects/HOSPITAL/frontend && npm run build 2>&1 | tail -5
```

Expected: `✓ built in X.XXs` — zero errors.

- [ ] **Step 5: Commit**

```bash
cd e:/Projects/HOSPITAL && git add frontend/src/pages/hospital/DoctorDashboard.jsx && git commit -m "feat(doctor): consultation one-click widget in overview tab"
```

---

## Self-Review

**Spec coverage:**
- ✅ Placement: overview tab, after Quick Actions, before dual-panel — Task 2 Step 2
- ✅ Guard: `queueEntries.length > 0` — Task 2 Step 2
- ✅ Derived: `sortedQueue`, `consultingEntry`, `nextQueuedEntry`, `nextQueuedToken` — Task 1 Step 2
- ✅ State 3a (nobody consulting, first QUEUED): "Next Patient" header + name + token + wait time + button — Task 2 Step 2
- ✅ State 3b (consulting + up next): "Now Consulting" header + consulting row + divider + "Up Next" row + button — Task 2 Step 2
- ✅ State 3b edge (consulting, no next): "Now Consulting" + "No more patients in queue" — Task 2 Step 2
- ✅ State 3c (queue empty): widget not rendered — Task 2 Step 2
- ✅ "N waiting" badge with 3 color tiers (1–3 green, 4–7 amber, 8+ red) — Task 2 Step 2
- ✅ Button calls `handleStartOpdConsultation(nextQueuedEntry.opd)` — Task 2 Step 2
- ✅ Button disabled + "Starting…" while `startingConsultationId` truthy — Task 2 Step 2
- ✅ Patient name: `opd?.patient?.name || opd?.patientName || 'Unknown'` — Task 2 Step 2
- ✅ Wait time: `toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })` — Task 2 Step 2
- ✅ Token #: `nextQueuedToken` — Task 2 Step 2
- ✅ No new state, no new API calls, no new files — architecture

**Placeholder scan:** None found.

**Type consistency:** `sortedQueue` is `QueueEntry[]` derived in Task 1. `consultingEntry` and `nextQueuedEntry` are `QueueEntry | undefined` from Task 1. In Task 2: `nextQueuedEntry.opd` passed to `handleStartOpdConsultation` — matches the existing call signature at line 870 (`setConsultationModal({ isOpen: true, appointment: null, patient: opd.patient, opd })`). `nextQueuedToken` is `number | null` from Task 1, used in `Token #${nextQueuedToken}` in Task 2 — only rendered when `nextQueuedEntry` is truthy, so `nextQueuedToken` is always a number at that point. All consistent.
