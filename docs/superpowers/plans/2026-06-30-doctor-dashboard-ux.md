# Doctor Dashboard UX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the generic "Overview" header and 5 static stat cards in `DoctorDashboard.jsx` with a personalized greeting, 3–4 clickable stat cards, and a Quick Actions row that answers "What do I need to do now?" the moment the doctor logs in.

**Architecture:** Single file change — `frontend/src/pages/hospital/DoctorDashboard.jsx`. Two new pieces: (1) pure-UI greeting + stat cards using already-fetched data, (2) IPD count state + one new `getAdmittedIpdAdmissions()` call + Quick Actions row. Low stock alert and dual-panel sections untouched.

**Tech Stack:** React 18, Tailwind CSS, existing `hospitalService.getAdmittedIpdAdmissions()`

---

## File Map

| Action | File |
|--------|------|
| Modify | `frontend/src/pages/hospital/DoctorDashboard.jsx` |

---

### Task 1: Greeting Header + 3 Stat Cards

**Files:**
- Modify: `frontend/src/pages/hospital/DoctorDashboard.jsx`

**Context:**
The overview tab currently starts (around line 912) with a flex row containing `<h2>Overview</h2>` and an optional "Add Patient" button, followed by a low stock alert, then a 5-card grid. We replace the h2 header + 5-card grid. The low stock alert stays untouched.

The 3 stat cards in this task use data already in state (`stats.today`, `queueEntries`, `stats.pending`) — no new fetches. The 4th card (IPD) is added in Task 2.

- [ ] **Step 1: Read the current file**

Open `frontend/src/pages/hospital/DoctorDashboard.jsx`. Locate:
- The component's state block (top of component)
- The `return (` statement
- Inside the overview tab: the `<div className="flex items-center justify-between">` block containing `<h2>Overview</h2>`
- The 5-card grid: `<div className="grid grid-cols-1 md:grid-cols-5 gap-6">`

Note exact line numbers before making changes.

- [ ] **Step 2: Add helper functions before the JSX return**

Find the `return (` line of the component. Just before it, add these three helpers:

```jsx
    const getGreeting = () => {
        const h = new Date().getHours();
        if (h < 12) return 'Good morning';
        if (h < 17) return 'Good afternoon';
        return 'Good evening';
    };

    const getDoctorFirstName = () => {
        const name = user?.name || user?.username || 'Doctor';
        const parts = name.trim().split(/\s+/);
        const first = parts[0].toLowerCase().replace('.', '') === 'dr' ? (parts[1] || parts[0]) : parts[0];
        return `Dr. ${first}`;
    };

    const todayLabel = new Date().toLocaleDateString('en-IN', {
        weekday: 'long', day: 'numeric', month: 'long', year: 'numeric'
    });
```

- [ ] **Step 3: Replace the overview header row**

Find this block (the header row inside the overview tab):

```jsx
                        <div className="flex items-center justify-between">
                            <h2 className="text-2xl font-bold text-gray-900">Overview</h2>
                            {user?.receptionMode === 'SOLO' && (
                                <div className="flex items-center gap-3">
                                    <button
                                        onClick={() => setIsAddPatientModalOpen(true)}
                                        className="inline-flex items-center justify-center gap-2 px-4 py-2 bg-slate-900 hover:bg-slate-800 text-white text-sm font-semibold rounded-xl transition-all shadow-sm active:scale-95 cursor-pointer animate-fade-in"
                                    >
                                        <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M18 9v3m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0zM3 20a6 6 0 0112 0v1H3v-1z" />
                                        </svg>
                                        Add Patient
                                    </button>
                                </div>
                            )}
                        </div>
```

Replace it with:

```jsx
                        {/* Greeting Header */}
                        <div>
                            <h2 className="text-2xl font-bold text-gray-900">
                                {getGreeting()}, {getDoctorFirstName()} 👋
                            </h2>
                            <p className="text-sm text-gray-500 mt-1">Doctor · {todayLabel}</p>
                        </div>
```

- [ ] **Step 4: Replace the 5-card grid with a 3-card grid**

Find this block (the 5-card grid):

```jsx
                            <div className="grid grid-cols-1 md:grid-cols-5 gap-6">
                                <div className="bg-white rounded-lg border border-gray-200 p-6">
                                    <div className="flex justify-between items-center">
                                        <div className="min-w-0 w-full">
                                            <p className="text-gray-600 text-sm font-medium">Current Patient</p>
                                            <h3 className="text-base font-bold text-gray-900 mt-1 truncate" title={currentPatient}>{currentPatient ?? 'None'}</h3>
                                        </div>
                                    </div>
                                </div>
                                <div className="bg-white rounded-lg border border-gray-200 p-6">
                                    <div className="flex justify-between items-center">
                                        <div className="min-w-0 w-full">
                                            <p className="text-gray-600 text-sm font-medium">Next Patient</p>
                                            <h3 className="text-base font-bold text-gray-900 mt-1 truncate" title={nextPatient}>{nextPatient ?? 'None'}</h3>
                                        </div>
                                    </div>
                                </div>
                                <div className="bg-white rounded-lg border border-gray-200 p-6">
                                    <div className="flex justify-between items-center">
                                        <div>
                                            <p className="text-gray-600 text-sm font-medium">Today's Appointments</p>
                                            <h3 className="text-3xl font-bold text-gray-900 mt-1">{stats.today}</h3>
                                        </div>
                                    </div>
                                </div>
                                <div className="bg-white rounded-lg border border-gray-200 p-6">
                                    <div className="flex justify-between items-center">
                                        <div>
                                            <p className="text-gray-600 text-sm font-medium">Pending Action</p>
                                            <h3 className="text-3xl font-bold text-gray-900 mt-1">{stats.pending}</h3>
                                        </div>
                                    </div>
                                </div>
                                <div className="bg-white rounded-lg border border-gray-200 p-6">
                                    <div className="flex justify-between items-center">
                                        <div>
                                            <p className="text-gray-600 text-sm font-medium">Total Appointments</p>
                                            <h3 className="text-3xl font-bold text-gray-900 mt-1">{stats.total}</h3>
                                        </div>
                                    </div>
                                </div>
                            </div>
```

Replace it with:

```jsx
                            {/* Stat Cards */}
                            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                                <button
                                    onClick={() => setActiveTab('opd')}
                                    className="bg-white rounded-2xl border border-gray-200 p-6 text-left hover:shadow-md hover:border-blue-200 transition-all group"
                                >
                                    <p className="text-sm font-medium text-gray-500 group-hover:text-blue-600 transition-colors">Today's OPD</p>
                                    <p className="text-3xl font-bold text-gray-900 mt-1">{stats.today}</p>
                                    <p className="text-xs text-blue-500 mt-2 font-medium">View OPD →</p>
                                </button>
                                <button
                                    onClick={() => setActiveTab('queue')}
                                    className="bg-white rounded-2xl border border-gray-200 p-6 text-left hover:shadow-md transition-all group"
                                >
                                    <p className="text-sm font-medium text-gray-500 group-hover:text-orange-600 transition-colors">In Queue</p>
                                    <p className={`text-3xl font-bold mt-1 ${queueEntries.length > 10 ? 'text-orange-600' : 'text-gray-900'}`}>
                                        {queueEntries.length}
                                    </p>
                                    <p className="text-xs text-gray-400 mt-2 font-medium">View Queue →</p>
                                </button>
                                <button
                                    onClick={() => setActiveTab('appointments')}
                                    className="bg-white rounded-2xl border border-gray-200 p-6 text-left hover:shadow-md transition-all group"
                                >
                                    <p className="text-sm font-medium text-gray-500 group-hover:text-red-600 transition-colors">Pending</p>
                                    <p className={`text-3xl font-bold mt-1 ${stats.pending > 5 ? 'text-red-600' : stats.pending === 0 ? 'text-green-600' : 'text-gray-900'}`}>
                                        {stats.pending}
                                    </p>
                                    <p className="text-xs text-gray-400 mt-2 font-medium">View Appointments →</p>
                                </button>
                            </div>
```

- [ ] **Step 5: Build and verify**

```bash
cd e:/Projects/HOSPITAL/frontend && npm run build 2>&1 | tail -5
```

Expected: `✓ built in X.XXs` with no errors.

- [ ] **Step 6: Commit**

```bash
cd e:/Projects/HOSPITAL && git add frontend/src/pages/hospital/DoctorDashboard.jsx && git commit -m "feat(doctor-dashboard): greeting header and 3-card stat grid on overview tab"
```

---

### Task 2: IPD Card + Quick Actions Row

**Files:**
- Modify: `frontend/src/pages/hospital/DoctorDashboard.jsx`

**Context:**
Task 1 left the stat grid with 3 cards. This task adds:
1. A 4th IPD card (conditional on `hasIPD`) with a live count from `getAdmittedIpdAdmissions()`
2. A Quick Actions row below the stat cards

The `hasIPD` variable is already defined at the top of the component: `const hasIPD = modules.includes('IPD');`

`hospitalService.getAdmittedIpdAdmissions()` exists in `frontend/src/services/hospitalService.js` and returns an array of currently admitted patients.

- [ ] **Step 1: Add `ipdCount` state**

Find the state declarations block near the top of the component (around the `const [stats, setStats]` line). Add:

```jsx
    const [ipdCount, setIpdCount] = useState(0);
```

- [ ] **Step 2: Fetch IPD count in `loadData`**

Find the `loadData` function (or the `useEffect` that loads overview data). Specifically find the block that runs when `activeTab === 'overview'`. It looks like:

```js
} else if (activeTab === 'overview') {
```

Inside that block, add a non-blocking IPD fetch. Find where other data is fetched (appointments, followups, stats) and add after them:

```js
                    // Fetch IPD count if module is enabled
                    if (hasIPD) {
                        try {
                            const admitted = await hospitalService.getAdmittedIpdAdmissions();
                            setIpdCount(Array.isArray(admitted) ? admitted.length : 0);
                        } catch {
                            setIpdCount(0);
                        }
                    }
```

- [ ] **Step 3: Add IPD card to the stat grid**

Find the stat grid from Task 1:

```jsx
                            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
```

Change it to dynamically use 3 or 4 columns:

```jsx
                            <div className={`grid grid-cols-1 gap-4 ${hasIPD ? 'md:grid-cols-4' : 'md:grid-cols-3'}`}>
```

Then add the IPD card as the last card inside the grid (after the "Pending" card, before the closing `</div>`):

```jsx
                                {hasIPD && (
                                    <button
                                        onClick={() => setActiveTab('ipd')}
                                        className="bg-white rounded-2xl border border-gray-200 p-6 text-left hover:shadow-md hover:border-blue-200 transition-all group"
                                    >
                                        <p className="text-sm font-medium text-gray-500 group-hover:text-blue-600 transition-colors">IPD Patients</p>
                                        <p className="text-3xl font-bold text-gray-900 mt-1">{ipdCount}</p>
                                        <p className="text-xs text-blue-500 mt-2 font-medium">View IPD →</p>
                                    </button>
                                )}
```

- [ ] **Step 4: Add Quick Actions row**

Find the closing `</div>` of the stat cards grid. Immediately after it (before the low stock alert or dual-panel), add:

```jsx
                            {/* Quick Actions */}
                            <div className="flex flex-wrap gap-3">
                                <button
                                    onClick={() => setActiveTab('opd')}
                                    className="inline-flex items-center gap-2 px-5 py-2.5 bg-slate-900 hover:bg-slate-800 text-white text-sm font-semibold rounded-xl transition-all shadow-sm active:scale-95"
                                >
                                    Start OPD →
                                </button>
                                {hasIPD && (
                                    <button
                                        onClick={() => setActiveTab('ipd')}
                                        className="inline-flex items-center gap-2 px-5 py-2.5 bg-blue-600 hover:bg-blue-700 text-white text-sm font-semibold rounded-xl transition-all shadow-sm active:scale-95"
                                    >
                                        Continue IPD →
                                    </button>
                                )}
                                <button
                                    onClick={() => setActiveTab('appointments')}
                                    className="inline-flex items-center gap-2 px-5 py-2.5 bg-white hover:bg-gray-50 text-gray-700 text-sm font-semibold rounded-xl border border-gray-200 transition-all shadow-sm active:scale-95"
                                >
                                    View Appointments
                                </button>
                                {user?.receptionMode === 'SOLO' && (
                                    <button
                                        onClick={() => setIsAddPatientModalOpen(true)}
                                        className="inline-flex items-center gap-2 px-5 py-2.5 bg-white hover:bg-gray-50 text-gray-700 text-sm font-semibold rounded-xl border border-gray-200 transition-all shadow-sm active:scale-95"
                                    >
                                        + Add Patient
                                    </button>
                                )}
                            </div>
```

- [ ] **Step 5: Build and verify**

```bash
cd e:/Projects/HOSPITAL/frontend && npm run build 2>&1 | tail -5
```

Expected: `✓ built in X.XXs` with no errors.

- [ ] **Step 6: Commit**

```bash
cd e:/Projects/HOSPITAL && git add frontend/src/pages/hospital/DoctorDashboard.jsx && git commit -m "feat(doctor-dashboard): IPD card with live count and Quick Actions row"
```

---

## Self-Review

**Spec coverage:**
- ✅ Greeting header ("Good morning/afternoon/evening, Dr. [name]") — Task 1 Step 3
- ✅ Subtitle "Doctor · [date]" — Task 1 Step 3
- ✅ `getDoctorFirstName()` with "Dr." prefix logic — Task 1 Step 2
- ✅ `getGreeting()` time-aware — Task 1 Step 2
- ✅ `todayLabel` formatted date — Task 1 Step 2
- ✅ Today's OPD card (`stats.today`) → `?tab=opd` — Task 1 Step 4
- ✅ In Queue card (`queueEntries.length`, orange if > 10) → `?tab=queue` — Task 1 Step 4
- ✅ Pending card (`stats.pending`, red if > 5, green if 0) → `?tab=appointments` — Task 1 Step 4
- ✅ IPD card (`ipdCount`, only if `hasIPD`) → `?tab=ipd` — Task 2 Steps 1-3
- ✅ Grid `grid-cols-3` / `grid-cols-4` based on `hasIPD` — Task 2 Step 3
- ✅ `getAdmittedIpdAdmissions()` fetch with try/catch — Task 2 Step 2
- ✅ "Start OPD →" button — Task 2 Step 4
- ✅ "Continue IPD →" button (conditional on `hasIPD`) — Task 2 Step 4
- ✅ "View Appointments" button — Task 2 Step 4
- ✅ "Add Patient" button (conditional on SOLO mode) — Task 2 Step 4
- ✅ Low stock alert preserved (not touched in either task)
- ✅ Dual-panel preserved (not touched in either task)
- ✅ Old "Add Patient" button in header removed (replaced by Task 1 Step 3)
- ✅ Old 5 stat cards removed (replaced by Task 1 Step 4)

**Placeholder scan:** None found.

**Type consistency:** `ipdCount` is `number` (from `useState(0)`, set via `admitted.length`). Used as `{ipdCount}` in JSX — correct.
