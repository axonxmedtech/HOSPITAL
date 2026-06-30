# Nurse Dashboard Shift-Start View Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Overhaul `NurseDashboard.jsx` so the nurse lands on a prioritized task list with a 3-stat summary row — overdue tasks first, due-soon next, upcoming last.

**Architecture:** Single file change — `frontend/src/pages/hospital/NurseDashboard.jsx`. No new state, no new API calls, no new files. Three coordinated edits: (1) default tab + load behavior, (2) sort derivations + helper functions before return, (3) replacement task tab JSX.

**Tech Stack:** React 18, Tailwind CSS, existing `nurseService.getMyTasks()`

---

## File Map

| Action | File |
|--------|------|
| Modify | `frontend/src/pages/hospital/NurseDashboard.jsx` |

---

### Task 1: Load Behavior + Default Tab

**Files:**
- Modify: `frontend/src/pages/hospital/NurseDashboard.jsx`

- [ ] **Step 1: Read the file**

Open `frontend/src/pages/hospital/NurseDashboard.jsx`. Confirm:
- Line 7: `const [tab, setTab] = useState('patients');`
- Lines 15–18: single `useEffect` that loads patients or tasks based on `tab`

- [ ] **Step 2: Change default tab to `'tasks'`**

Find:
```jsx
  const [tab, setTab] = useState('patients');
```

Replace with:
```jsx
  const [tab, setTab] = useState('tasks');
```

- [ ] **Step 3: Split the useEffect into two**

Find the existing useEffect (lines 15–18):
```jsx
  useEffect(() => {
    if (tab === 'patients') loadPatients();
    if (tab === 'tasks') loadTasks();
  }, [tab]);
```

Replace with:
```jsx
  useEffect(() => {
    loadTasks();
  }, []);

  useEffect(() => {
    if (tab === 'patients') loadPatients();
  }, [tab]);
```

This ensures tasks load immediately on mount (for the stat row), and patients only load when the nurse switches to that tab.

- [ ] **Step 4: Build to verify no errors so far**

```bash
cd e:/Projects/HOSPITAL/frontend && npm run build 2>&1 | tail -5
```

Expected: `✓ built in X.XXs`

- [ ] **Step 5: Commit**

```bash
cd e:/Projects/HOSPITAL && git add frontend/src/pages/hospital/NurseDashboard.jsx && git commit -m "feat(nurse): tasks tab default + eager load on mount"
```

---

### Task 2: Sort Derivations + Helper Functions

**Files:**
- Modify: `frontend/src/pages/hospital/NurseDashboard.jsx`

- [ ] **Step 1: Find the insertion point**

In `NurseDashboard.jsx`, find the line just before the `if (selectedAdmission)` check — it is after the `wards` and `filteredPatients` constants:

```jsx
  const wards = [...new Set(patients.map(p => p.wardName).filter(Boolean))];
  const filteredPatients = wardFilter
    ? patients.filter(p => p.wardName === wardFilter)
    : patients;

  if (selectedAdmission) {
```

- [ ] **Step 2: Insert sort derivations and helper functions**

After the `filteredPatients` constant and before `if (selectedAdmission)`, insert:

```jsx
  const now = new Date();
  const MS_60 = 60 * 60 * 1000;

  const overdueTaskList = tasks
    .filter(t => t.scheduledAt && new Date(t.scheduledAt) < now)
    .sort((a, b) => new Date(a.scheduledAt) - new Date(b.scheduledAt));

  const dueSoonTaskList = tasks
    .filter(t => t.scheduledAt && new Date(t.scheduledAt) >= now && new Date(t.scheduledAt) - now <= MS_60)
    .sort((a, b) => new Date(a.scheduledAt) - new Date(b.scheduledAt));

  const upcomingTaskList = tasks
    .filter(t => !t.scheduledAt || (new Date(t.scheduledAt) >= now && new Date(t.scheduledAt) - now > MS_60))
    .sort((a, b) => {
      if (!a.scheduledAt) return 1;
      if (!b.scheduledAt) return -1;
      return new Date(a.scheduledAt) - new Date(b.scheduledAt);
    });

  const sortedTasks = [...overdueTaskList, ...dueSoonTaskList, ...upcomingTaskList];

  const overdueCount = overdueTaskList.length;
  const dueSoonCount = dueSoonTaskList.length;
  const patientCount = new Set(tasks.map(t => t.patientName).filter(Boolean)).size;

  function getTaskBucket(task) {
    if (!task.scheduledAt) return 'upcoming';
    const diffMs = new Date(task.scheduledAt) - now;
    if (diffMs < 0) return 'overdue';
    if (diffMs <= MS_60) return 'dueSoon';
    return 'upcoming';
  }

  function getTimeLabel(task) {
    if (!task.scheduledAt) return { label: 'No time set', cls: 'text-gray-400 text-xs' };
    const scheduled = new Date(task.scheduledAt);
    const diffMs = scheduled - now;
    if (diffMs < 0) {
      const mins = Math.floor(-diffMs / 60000);
      return { label: `OVERDUE ${mins} min`, cls: 'text-red-600 font-semibold text-xs' };
    }
    if (diffMs <= MS_60) {
      const mins = Math.floor(diffMs / 60000);
      return { label: `Due in ${mins} min`, cls: 'text-amber-600 font-semibold text-xs' };
    }
    return {
      label: `Due at ${scheduled.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`,
      cls: 'text-gray-500 text-xs',
    };
  }
```

- [ ] **Step 3: Build to verify**

```bash
cd e:/Projects/HOSPITAL/frontend && npm run build 2>&1 | tail -5
```

Expected: `✓ built in X.XXs`

- [ ] **Step 4: Commit**

```bash
cd e:/Projects/HOSPITAL && git add frontend/src/pages/hospital/NurseDashboard.jsx && git commit -m "feat(nurse): task sort derivations and time-label helpers"
```

---

### Task 3: Replace Task Tab JSX

**Files:**
- Modify: `frontend/src/pages/hospital/NurseDashboard.jsx`

- [ ] **Step 1: Find the existing tasks tab block**

Find the block that begins with:
```jsx
      {!loading && tab === 'tasks' && (
        <div className="space-y-3">
          {tasks.length === 0 && (
```
and ends just before the `{administerTarget && (` block.

- [ ] **Step 2: Replace the entire tasks tab block**

Replace everything from `{!loading && tab === 'tasks' && (` through the matching closing `)}` with:

```jsx
      {!loading && tab === 'tasks' && (
        <div className="space-y-3">
          {/* Stat summary row */}
          {tasks.length > 0 && (
            <div className="flex flex-wrap gap-2 mb-4">
              {overdueCount > 0 && (
                <span className="text-xs font-semibold px-3 py-1.5 rounded-full border bg-red-50 text-red-700 border-red-200">
                  ⚠️ {overdueCount} Overdue
                </span>
              )}
              {dueSoonCount > 0 && (
                <span className="text-xs font-semibold px-3 py-1.5 rounded-full border bg-amber-50 text-amber-700 border-amber-200">
                  🕐 {dueSoonCount} Due Soon
                </span>
              )}
              <span className="text-xs font-semibold px-3 py-1.5 rounded-full border bg-blue-50 text-blue-700 border-blue-200">
                👤 {patientCount} Patient{patientCount !== 1 ? 's' : ''}
              </span>
            </div>
          )}

          {sortedTasks.length === 0 && (
            <div className="text-center py-8 text-gray-400">No pending tasks</div>
          )}

          {sortedTasks.map(task => {
            const bucket = getTaskBucket(task);
            const { label: timeLabel, cls: timeLabelCls } = getTimeLabel(task);
            const cardCls = bucket === 'overdue'
              ? 'border-l-4 border-red-400 bg-red-50 border border-red-200'
              : bucket === 'dueSoon'
              ? 'border-l-4 border-amber-400 bg-amber-50 border border-amber-200'
              : 'border-l-4 border-gray-200 bg-white border border-gray-200';
            return (
              <div key={task.id} className={`flex items-center justify-between p-4 rounded-lg ${cardCls}`}>
                <div className="flex-1 min-w-0">
                  <span className={timeLabelCls}>{timeLabel}</span>
                  <p className="font-medium text-gray-800 mt-0.5">{task.orderDescription}</p>
                  <p className="text-sm text-gray-500 mt-0.5">
                    {task.orderType}
                    {task.patientName && ` · ${task.patientName}`}
                    {task.bedNumber ? ` · Bed ${task.bedNumber}` : task.wardName ? ` · ${task.wardName}` : ''}
                  </p>
                </div>
                <div className="flex gap-2 ml-4 shrink-0">
                  <button
                    onClick={() => setAdministerTarget(task)}
                    className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white font-semibold text-xs rounded-xl shadow-sm transition-all active:scale-95"
                  >
                    {task.orderType === 'MEDICATION' ? '💊 Administer' : '📋 Execute'}
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}
```

- [ ] **Step 3: Self-review checklist**

Before building, verify:
- [ ] Stat row: `overdueCount`, `dueSoonCount`, `patientCount` are all referenced (defined in Task 2)
- [ ] `sortedTasks.map(...)` replaces `tasks.map(...)` — old `isOverdue` variable is gone
- [ ] `getTaskBucket` and `getTimeLabel` are called inside the map (defined in Task 2)
- [ ] `setAdministerTarget(task)` call is preserved
- [ ] `administerTarget && <MedicationAdministrationModal ...>` block is untouched below

- [ ] **Step 4: Build and verify**

```bash
cd e:/Projects/HOSPITAL/frontend && npm run build 2>&1 | tail -5
```

Expected: `✓ built in X.XXs` with no errors.

- [ ] **Step 5: Commit**

```bash
cd e:/Projects/HOSPITAL && git add frontend/src/pages/hospital/NurseDashboard.jsx && git commit -m "feat(nurse): shift-start view — prioritized task list with stat row and color-coded cards"
```

---

## Self-Review

**Spec coverage:**
- ✅ Default tab → `'tasks'` — Task 1 Step 2
- ✅ Tasks load on mount — Task 1 Step 3
- ✅ Stat row: Overdue (red), Due Soon (amber), Patients (blue) — Task 3 Step 2
- ✅ Stat row hidden when tasks empty — Task 3 Step 2 (`tasks.length > 0` guard)
- ✅ Sort order: overdue → due soon → upcoming, each bucket sorted ascending — Task 2 Step 2
- ✅ No-scheduledAt tasks at bottom of upcoming — Task 2 Step 2 (sort returns 1 if no `a.scheduledAt`)
- ✅ Left border color: red/amber/gray — Task 3 Step 2
- ✅ Background color: red-50/amber-50/white — Task 3 Step 2
- ✅ Time label: OVERDUE N min / Due in N min / Due at HH:MM / No time set — Task 2 Step 2 `getTimeLabel`
- ✅ Patient name + bed/ward in card — Task 3 Step 2
- ✅ Action button unchanged (💊/📋, same MedicationAdministrationModal) — Task 3 Step 2
- ✅ My Patients tab untouched — Tasks 1-3 only touch tasks tab and useEffect

**Placeholder scan:** None found.

**Type consistency:** `overdueTaskList`, `dueSoonTaskList`, `upcomingTaskList` defined in Task 2 and consumed in Task 2 (`overdueCount`, `dueSoonCount`, `sortedTasks`). `getTaskBucket` and `getTimeLabel` defined in Task 2, used in Task 3. All consistent.
