# Doctor Availability Lookup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Doctor Availability" widget to the receptionist overview tab that shows a selected doctor's today appointment load and list — derived entirely from existing state, zero new API calls.

**Architecture:** Single file change — `frontend/src/pages/hospital/ReceptionistDashboard.jsx`. Task 1 adds one state variable and three derived constants before the JSX return. Task 2 inserts the widget JSX between the Quick Actions row and the dual-panel grid in the overview tab.

**Tech Stack:** React 18, Tailwind CSS, existing `appointments` + `doctors` state already loaded for the overview tab

---

## File Map

| Action | File |
|--------|------|
| Modify | `frontend/src/pages/hospital/ReceptionistDashboard.jsx` |

---

### Task 1: State Variable + Derived Constants

**Files:**
- Modify: `frontend/src/pages/hospital/ReceptionistDashboard.jsx`

- [ ] **Step 1: Read the file landmarks**

Open `frontend/src/pages/hospital/ReceptionistDashboard.jsx`. Confirm:
- Line ~84: `const [isAddModalOpen, setIsAddModalOpen] = useState(false);`
- Line ~748: `const todayLabel = new Date().toLocaleDateString(...)` — last constant before `return (`
- Line ~750: `return (` — start of JSX

- [ ] **Step 2: Add the availability state variable**

Find:
```jsx
    const [isAddModalOpen, setIsAddModalOpen] = useState(false);
```

Replace with:
```jsx
    const [isAddModalOpen, setIsAddModalOpen] = useState(false);
    const [availabilityDoctorId, setAvailabilityDoctorId] = useState('');
```

- [ ] **Step 3: Add derived constants before `return (`**

Find the line:
```jsx
    const todayLabel = new Date().toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
```

Replace with:
```jsx
    const todayLabel = new Date().toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });

    const availToday = new Date().toISOString().slice(0, 10);

    const availDoctorAppts = availabilityDoctorId
      ? appointments
          .filter(a => {
            const dId = String(a.doctorId ?? a.doctor?.id ?? '');
            const aDate = (a.appointmentDate ?? a.date ?? '').slice(0, 10);
            return dId === String(availabilityDoctorId) && aDate === availToday;
          })
          .sort((a, b) =>
            (a.appointmentTime ?? a.scheduledTime ?? '').localeCompare(
              b.appointmentTime ?? b.scheduledTime ?? ''
            )
          )
      : [];

    const availApptCount = availDoctorAppts.length;
    const availLastTime = availDoctorAppts.length > 0
      ? (availDoctorAppts[availDoctorAppts.length - 1].appointmentTime
          ?? availDoctorAppts[availDoctorAppts.length - 1].scheduledTime
          ?? null)
      : null;
```

- [ ] **Step 4: Build to verify**

```bash
cd e:/Projects/HOSPITAL/frontend && npm run build 2>&1 | tail -5
```

Expected: `✓ built in X.XXs` — no errors.

- [ ] **Step 5: Commit**

```bash
cd e:/Projects/HOSPITAL && git add frontend/src/pages/hospital/ReceptionistDashboard.jsx && git commit -m "feat(receptionist): availability state and derived doctor appointment constants"
```

---

### Task 2: Insert Availability Widget JSX

**Files:**
- Modify: `frontend/src/pages/hospital/ReceptionistDashboard.jsx`

- [ ] **Step 1: Find the insertion point**

In the overview tab JSX, find this comment (around line 889):
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
                            {/* Doctor Availability Widget */}
                            {doctors.length > 0 && (
                                <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
                                    <h3 className="text-base font-bold text-gray-900 mb-4">Doctor Availability — Today</h3>
                                    <select
                                        value={availabilityDoctorId}
                                        onChange={e => setAvailabilityDoctorId(e.target.value)}
                                        className="border border-gray-200 rounded-xl px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-300 w-full sm:w-auto"
                                    >
                                        <option value="">Select a doctor</option>
                                        {doctors.map(d => (
                                            <option key={d.id} value={d.id}>
                                                {d.name}{d.specialization ? ` · ${d.specialization}` : ''}
                                            </option>
                                        ))}
                                    </select>

                                    {!availabilityDoctorId && (
                                        <p className="text-sm text-gray-400 mt-3">Select a doctor to see today&apos;s schedule</p>
                                    )}

                                    {availabilityDoctorId && loading && (
                                        <p className="text-sm text-gray-400 mt-3">Loading…</p>
                                    )}

                                    {availabilityDoctorId && !loading && (
                                        <div className="mt-4">
                                            {availApptCount === 0 ? (
                                                <p className="text-sm text-gray-500">No appointments scheduled today</p>
                                            ) : (
                                                <>
                                                    <p className="text-sm text-gray-600 mb-3">
                                                        Today: <span className="font-semibold text-gray-900">{availApptCount} appointment{availApptCount !== 1 ? 's' : ''}</span>
                                                        {availLastTime && (
                                                            <span> · Last at <span className="font-semibold text-gray-900">
                                                                {new Date(`1970-01-01T${availLastTime}`).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                                                            </span></span>
                                                        )}
                                                    </p>
                                                    <div className="space-y-1.5">
                                                        {availDoctorAppts.slice(0, 10).map((a, i) => {
                                                            const time = a.appointmentTime ?? a.scheduledTime ?? null;
                                                            const timeLabel = time
                                                                ? new Date(`1970-01-01T${time}`).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                                                                : '—';
                                                            const patient = a.patientName ?? a.patient?.name ?? '—';
                                                            const status = (a.status || '').toUpperCase();
                                                            const statusCls = status === 'CONFIRMED'
                                                                ? 'bg-green-100 text-green-700'
                                                                : status === 'PENDING'
                                                                ? 'bg-yellow-100 text-yellow-700'
                                                                : status === 'CANCELLED'
                                                                ? 'bg-red-100 text-red-500 line-through'
                                                                : 'bg-gray-100 text-gray-600';
                                                            return (
                                                                <div key={a.id ?? i} className="flex items-center gap-3 py-1.5 border-b border-gray-50 last:border-0">
                                                                    <span className="text-xs font-mono text-gray-500 w-16 shrink-0">{timeLabel}</span>
                                                                    <span className="text-sm text-gray-800 flex-1 truncate">{patient}</span>
                                                                    <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${statusCls}`}>{status || '—'}</span>
                                                                </div>
                                                            );
                                                        })}
                                                        {availDoctorAppts.length > 10 && (
                                                            <p className="text-xs text-gray-400 pt-1">+{availDoctorAppts.length - 10} more appointments</p>
                                                        )}
                                                    </div>
                                                </>
                                            )}
                                            <div className="mt-4 pt-4 border-t border-gray-100">
                                                <button
                                                    onClick={() => setIsAddModalOpen(true)}
                                                    className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-semibold rounded-xl transition-all active:scale-95"
                                                >
                                                    Book Appointment with this doctor →
                                                </button>
                                            </div>
                                        </div>
                                    )}
                                </div>
                            )}

                            {/* Side-by-Side Lists: Appointments and Queue */}
```

- [ ] **Step 3: Self-review checklist**

Before building, verify:
- [ ] Widget wrapped in `{doctors.length > 0 && (...)}` — not rendered when doctors not loaded
- [ ] Doctor `<select>` uses `doctors.map(d => ...)` with `d.id` as value
- [ ] Empty state shown when `!availabilityDoctorId`
- [ ] Loading indicator shown when `availabilityDoctorId && loading`
- [ ] "No appointments" shown when doctor selected, not loading, `availApptCount === 0`
- [ ] Appointment list uses `availDoctorAppts.slice(0, 10)` — max 10 rows
- [ ] `+N more` shown when `availDoctorAppts.length > 10`
- [ ] Status badge applies 4 color cases (CONFIRMED/PENDING/CANCELLED/other)
- [ ] "Book" button calls `setIsAddModalOpen(true)` — same pattern as existing buttons
- [ ] `{/* Side-by-Side Lists: Appointments and Queue */}` comment preserved on last line

- [ ] **Step 4: Build and verify**

```bash
cd e:/Projects/HOSPITAL/frontend && npm run build 2>&1 | tail -5
```

Expected: `✓ built in X.XXs` with no errors.

- [ ] **Step 5: Commit**

```bash
cd e:/Projects/HOSPITAL && git add frontend/src/pages/hospital/ReceptionistDashboard.jsx && git commit -m "feat(receptionist): doctor availability widget in overview tab"
```

---

## Self-Review

**Spec coverage:**
- ✅ Placement: overview tab, after stat cards/quick actions, before dual-panel — Task 2 Step 2
- ✅ Guard: `doctors.length > 0` — Task 2 Step 2
- ✅ New state: `availabilityDoctorId` (string, default `''`) — Task 1 Step 2
- ✅ Derived: `availDoctorAppts` filtered by doctorId + today, sorted by time — Task 1 Step 3
- ✅ Derived: `availApptCount`, `availLastTime` — Task 1 Step 3
- ✅ Doctor selector from `doctors` state — Task 2 Step 2
- ✅ Summary line: count + last appointment time — Task 2 Step 2
- ✅ Last time formatted as 12-hour — Task 2 Step 2
- ✅ Last time omitted when null — Task 2 Step 2
- ✅ Appointment list: time / patient / status badge — Task 2 Step 2
- ✅ Status colors: CONFIRMED=green, PENDING=yellow, CANCELLED=red+strikethrough, other=gray — Task 2 Step 2
- ✅ Max 10 rows + "+N more" — Task 2 Step 2
- ✅ "No appointments" empty state — Task 2 Step 2
- ✅ Loading state when `loading` is true — Task 2 Step 2
- ✅ "Select a doctor" placeholder state — Task 2 Step 2
- ✅ Book button calls `setIsAddModalOpen(true)` — Task 2 Step 2
- ✅ No new API calls, no changes to AppointmentModal, hospitalService, or backend — architecture

**Placeholder scan:** None found.

**Type consistency:** `availabilityDoctorId` set as string in state (Task 1), compared with `String(availabilityDoctorId)` in filter (Task 1), used as `select` value (Task 2). `availDoctorAppts`, `availApptCount`, `availLastTime` defined in Task 1, consumed in Task 2. All consistent.
