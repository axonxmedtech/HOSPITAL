# Reception Dashboard Overhaul Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the generic "Overview" header and plain stat cards in ReceptionistDashboard with a Strynkix-style "Today's Work" greeting screen — time-aware greeting, 4 clickable stat cards, and 4 quick-action buttons.

**Architecture:** Frontend-only change. Add `getAvailableBeds` to `hospitalService.js`, add `availableBeds` state + fetch to the dashboard, then replace the overview tab's top section JSX (greeting header + stats grid). All existing tabs and the lower dual-panel stay untouched.

**Tech Stack:** React 18, Tailwind CSS, existing `hospitalService.js` pattern (`apiClient.get(...).then(r => r.data)`)

---

## Codebase Quick Reference

- `frontend/src/pages/hospital/ReceptionistDashboard.jsx` — target file
- `frontend/src/services/hospitalService.js` — add one method
- `user` state: `authService.getCurrentUser()` — has `.name` (full name) and `.username`
- `stats` state: `{ today, pending, total }` — already fetched on every tab load via `hospitalService.getAppointmentStats()`
- `queueEntries` state: array fetched on overview load via `hospitalService.getHospitalQueue()`
- `currentPatientName` state: derived from queue, already set
- `setIsAddPatientModalOpen(true)` — opens Register Patient modal
- `setIsAddModalOpen(true)` — opens Book Appointment modal
- `setIsIpdAdmitOpen(true)` — opens Admit Patient modal
- `setActiveTab(tab)` — navigates to a tab
- Overview tab JSX starts at line ~757 with `{activeTab === 'overview' && !loading && (`
- The section to **replace** is lines ~759–833: the `<div className="flex items-center justify-between">` header + `<div className="grid grid-cols-1 md:grid-cols-4 gap-6">` stats grid
- The section to **keep** starts at line ~835: `{/* Side-by-Side Lists: Appointments and Queue */}`
- Low stock alert (lines ~771–798) must also be **kept** between the new greeting and the new stats cards

---

## File Map

**Modify:**
- `frontend/src/services/hospitalService.js` — add `getAvailableBeds` method
- `frontend/src/pages/hospital/ReceptionistDashboard.jsx` — add state + fetch + replace overview top section JSX

---

## Task 1: Add `getAvailableBeds` to hospitalService.js

**Files:**
- Modify: `frontend/src/services/hospitalService.js`

- [ ] **Step 1: Read the file to find where to add the method**

Read `frontend/src/services/hospitalService.js`. Find the section with bed-related methods (search for `changeBed`). Add `getAvailableBeds` immediately after `changeBed`:

```javascript
getAvailableBeds: async () => {
    const response = await apiClient.get('/hospital/beds/available');
    return response.data;
},
```

- [ ] **Step 2: Build check**

```bash
cd e:/Projects/HOSPITAL/frontend && npm run build 2>&1 | tail -5
```

Expected: `✓ built in X.XXs` with no errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/services/hospitalService.js
git commit -m "feat(reception): add getAvailableBeds to hospitalService"
```

---

## Task 2: Add `availableBeds` state and fetch to ReceptionistDashboard

**Files:**
- Modify: `frontend/src/pages/hospital/ReceptionistDashboard.jsx`

- [ ] **Step 1: Add state variable**

Read `frontend/src/pages/hospital/ReceptionistDashboard.jsx`. Find the Stats state declaration (search for `const [stats, setStats]`). Add the new state immediately after it:

```javascript
const [availableBeds, setAvailableBeds] = useState([]);
```

- [ ] **Step 2: Add fetch inside loadData**

Inside the `loadData` async function, find the block:
```javascript
if (activeTab === 'overview') {
    setTodaysFollowUps(followUpsData || []);
}
```

Add the beds fetch right after `setTodaysFollowUps`:

```javascript
if (activeTab === 'overview') {
    setTodaysFollowUps(followUpsData || []);
    try {
        const beds = await hospitalService.getAvailableBeds();
        setAvailableBeds(beds || []);
    } catch {
        setAvailableBeds([]);
    }
}
```

- [ ] **Step 3: Build check**

```bash
cd e:/Projects/HOSPITAL/frontend && npm run build 2>&1 | tail -5
```

Expected: clean build.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/hospital/ReceptionistDashboard.jsx
git commit -m "feat(reception): add availableBeds state and fetch for overview"
```

---

## Task 3: Replace overview top section with greeting + stats + quick actions

**Files:**
- Modify: `frontend/src/pages/hospital/ReceptionistDashboard.jsx`

This is the main visual change. Read the file first to confirm line numbers, then replace the section.

- [ ] **Step 1: Add the greeting helper — add these two constants** just before the `return (` statement of the component (search for `return (` near the JSX):

```javascript
const getGreeting = () => {
    const h = new Date().getHours();
    if (h < 12) return 'Good morning';
    if (h < 17) return 'Good afternoon';
    return 'Good evening';
};
const firstName = user?.name?.split(' ')[0] || user?.username || 'there';
const todayLabel = new Date().toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
```

- [ ] **Step 2: Replace the overview header + stats grid**

Find this exact block (it starts inside `{activeTab === 'overview' && !loading && (`):

```jsx
                        <div className="space-y-6">
                            <div className="flex items-center justify-between">
                                <h2 className="text-2xl font-bold text-gray-900">Overview</h2>
                                <button
                                    onClick={() => setIsAddPatientModalOpen(true)}
                                    className="inline-flex items-center justify-center gap-2 px-4 py-2 bg-gray-900 hover:bg-gray-800 text-white text-sm font-semibold rounded-xl transition-all shadow-sm active:scale-95 cursor-pointer animate-fade-in"
                                >
                                    <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M18 9v3m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0zM3 20a6 6 0 0112 0v1H3v-1z" />
                                    </svg>
                                    Add Patient
                                </button>
                            </div>
```

Replace it with:

```jsx
                        <div className="space-y-6">
                            {/* Greeting Header */}
                            <div className="flex items-start justify-between">
                                <div>
                                    <h2 className="text-2xl font-bold text-gray-900">{getGreeting()}, {firstName} 👋</h2>
                                    <p className="text-sm text-gray-500 mt-1">Receptionist &middot; {todayLabel}</p>
                                </div>
                            </div>
```

- [ ] **Step 3: Replace the 4-card stats grid**

Find this block immediately after the low stock alert (around line 799–833):

```jsx
                            <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
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
                                            <p className="text-gray-600 text-sm font-medium">Pending</p>
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
                                <div className="bg-white rounded-lg border border-gray-200 p-6">
                                    <div>
                                        <p className="text-gray-600 text-sm font-medium">Active / Next Patient</p>
                                        <div className="mt-2 flex items-baseline gap-2 flex-wrap">
                                            <h3 className="text-base font-bold text-gray-900 truncate max-w-[150px]" title={currentPatientName}>{currentPatientName ?? 'None'}</h3>
                                            <span className="text-xs text-gray-500 truncate max-w-[120px]" title={nextPatientName}>/ {nextPatientName ?? 'None'}</span>
                                        </div>
                                    </div>
                                </div>
                            </div>
```

Replace it with the new stats grid **plus** the quick actions row:

```jsx
                            {/* Stats Cards */}
                            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                                <button
                                    onClick={() => setActiveTab('appointments')}
                                    className="bg-white rounded-xl border border-gray-200 p-5 text-left hover:border-blue-300 hover:shadow-md transition-all group"
                                >
                                    <p className="text-xs font-medium text-gray-500 uppercase tracking-wide">Today's Appointments</p>
                                    <p className="text-3xl font-bold text-gray-900 mt-2">{stats.today}</p>
                                    <p className="text-xs text-blue-600 mt-1 group-hover:underline">View all →</p>
                                </button>
                                <button
                                    onClick={() => setActiveTab('queue')}
                                    className="bg-white rounded-xl border border-gray-200 p-5 text-left hover:border-orange-300 hover:shadow-md transition-all group"
                                >
                                    <p className="text-xs font-medium text-gray-500 uppercase tracking-wide">Patients in Queue</p>
                                    <p className={`text-3xl font-bold mt-2 ${queueEntries.length > 10 ? 'text-orange-600' : 'text-gray-900'}`}>{queueEntries.length}</p>
                                    <p className="text-xs text-orange-600 mt-1 group-hover:underline">Manage queue →</p>
                                </button>
                                <button
                                    onClick={() => setActiveTab('appointments')}
                                    className="bg-white rounded-xl border border-gray-200 p-5 text-left hover:border-red-300 hover:shadow-md transition-all group"
                                >
                                    <p className="text-xs font-medium text-gray-500 uppercase tracking-wide">Pending</p>
                                    <p className={`text-3xl font-bold mt-2 ${stats.pending > 5 ? 'text-red-600' : 'text-gray-900'}`}>{stats.pending}</p>
                                    <p className="text-xs text-red-500 mt-1 group-hover:underline">Review →</p>
                                </button>
                                <div className="bg-white rounded-xl border border-gray-200 p-5">
                                    <p className="text-xs font-medium text-gray-500 uppercase tracking-wide">Available Beds</p>
                                    <p className={`text-3xl font-bold mt-2 ${availableBeds.length === 0 ? 'text-red-600' : 'text-green-600'}`}>{availableBeds.length}</p>
                                    <p className="text-xs text-gray-400 mt-1">across all wards</p>
                                </div>
                            </div>

                            {/* Quick Actions */}
                            <div className="flex flex-wrap gap-3">
                                <button
                                    onClick={() => setIsAddPatientModalOpen(true)}
                                    className="inline-flex items-center gap-2 px-4 py-2.5 bg-gray-900 hover:bg-gray-800 text-white text-sm font-semibold rounded-xl transition-all shadow-sm active:scale-95"
                                >
                                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M18 9v3m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0zM3 20a6 6 0 0112 0v1H3v-1z" />
                                    </svg>
                                    Register Patient
                                </button>
                                <button
                                    onClick={() => setIsAddModalOpen(true)}
                                    className="inline-flex items-center gap-2 px-4 py-2.5 bg-blue-600 hover:bg-blue-700 text-white text-sm font-semibold rounded-xl transition-all shadow-sm active:scale-95"
                                >
                                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                                    </svg>
                                    Book Appointment
                                </button>
                                {hasIPD && (
                                    <button
                                        onClick={() => setIsIpdAdmitOpen(true)}
                                        className="inline-flex items-center gap-2 px-4 py-2.5 bg-green-600 hover:bg-green-700 text-white text-sm font-semibold rounded-xl transition-all shadow-sm active:scale-95"
                                    >
                                        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-2 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
                                        </svg>
                                        Admit Patient
                                    </button>
                                )}
                                <button
                                    onClick={() => setActiveTab('patients')}
                                    className="inline-flex items-center gap-2 px-4 py-2.5 bg-white border border-gray-300 hover:bg-gray-50 text-gray-700 text-sm font-semibold rounded-xl transition-all shadow-sm active:scale-95"
                                >
                                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                                    </svg>
                                    Search Patients
                                </button>
                            </div>
```

- [ ] **Step 4: Build check**

```bash
cd e:/Projects/HOSPITAL/frontend && npm run build 2>&1 | tail -10
```

Expected: `✓ built in X.XXs` with no errors. Fix any JSX errors (common issue: unclosed tags if the old block wasn't fully replaced).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/hospital/ReceptionistDashboard.jsx
git commit -m "feat(reception): redesign overview tab — greeting, clickable stats, quick actions"
```

---

## Verification Checklist

After all tasks complete, verify:

- [ ] Overview tab shows time-aware greeting with receptionist's first name
- [ ] 4 stat cards show correct numbers (Today's Appointments, Queue, Pending, Available Beds)
- [ ] Clicking "Today's Appointments" navigates to appointments tab
- [ ] Clicking "Patients in Queue" navigates to queue tab
- [ ] Clicking "Pending" navigates to appointments tab
- [ ] "Register Patient" button opens the patient registration modal
- [ ] "Book Appointment" button opens the appointment modal
- [ ] "Admit Patient" button opens the admit modal (only visible if `hasIPD` is true)
- [ ] "Search Patients" button navigates to patients tab
- [ ] Low stock alert still appears (unchanged)
- [ ] Dual-panel lower section (Appointments + Queue) still renders correctly
- [ ] All other tabs (appointments, OPD, queue, IPD, patients, billing) work unchanged
- [ ] Frontend build passes with no errors
