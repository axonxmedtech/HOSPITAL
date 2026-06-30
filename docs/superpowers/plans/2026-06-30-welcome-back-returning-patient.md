# Welcome Back Returning Patient Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When the receptionist selects an existing patient in the appointment booking modal, display a "Welcome Back" card showing visit count, last seen date, and last doctor — and pre-fill the doctor dropdown with the last visit's doctor.

**Architecture:** Single file change — `AppointmentModal.jsx`. Task 1 adds state variables and wires them into the existing `selectPatient` / `handleClose` / open-reset hooks. Task 2 adds the history fetch `useEffect` with doctor pre-fill. Task 3 inserts the card JSX between the patient selector and doctor dropdown.

**Tech Stack:** React 18, existing `useState` / `useEffect`, existing `hospitalService.getAppointmentsByPatient(patientId)`

---

## File Map

| Action | File |
|--------|------|
| Modify | `frontend/src/components/AppointmentModal.jsx` |

---

### Task 1: State Variables + selectPatient + Reset Hooks

**Files:**
- Modify: `frontend/src/components/AppointmentModal.jsx`

- [ ] **Step 1: Read the file landmarks**

Open `frontend/src/components/AppointmentModal.jsx`. Confirm:
- Line ~23: `const [loadingSlots, setLoadingSlots] = useState(false);` — last state declaration
- Line ~53: first `useEffect` (modal open reset): `if (isOpen) { setFormData({}); ... }`
- Line ~143: `const selectPatient = (patient) => {` — sets `patientName` and `patientId`
- Line ~195: `const handleClose = () => {` — resets form state and calls `onClose()`

- [ ] **Step 2: Add three new state variables**

Find:
```js
    const [loadingSlots, setLoadingSlots] = useState(false);
```

Replace with:
```js
    const [loadingSlots, setLoadingSlots] = useState(false);

    // Welcome Back card state
    const [selectedPatient, setSelectedPatient] = useState(null);
    const [patientHistory, setPatientHistory] = useState(null);
    const [historyLoading, setHistoryLoading] = useState(false);
```

- [ ] **Step 3: Update `selectPatient` to store the full patient object**

Find:
```js
    const selectPatient = (patient) => {
        handleChange('patientName', `${patient.name} - ${patient.phone}`);
        handleChange('patientId', patient.id);
        setShowPatientDropdown(false);
    };
```

Replace with:
```js
    const selectPatient = (patient) => {
        handleChange('patientName', `${patient.name} - ${patient.phone}`);
        handleChange('patientId', patient.id);
        setSelectedPatient(patient);
        setShowPatientDropdown(false);
    };
```

- [ ] **Step 4: Reset new state in the modal-open useEffect**

Find:
```js
    useEffect(() => {
        if (isOpen) {
            setFormData({});
            setIsNewPatient(false);
            setErrors({});
            setFilteredPatients([]);
            setSelectedSlot(null);
            setBookedSlots([]);
            if (patients) setFilteredPatients(patients);
        }
    }, [isOpen, patients]);
```

Replace with:
```js
    useEffect(() => {
        if (isOpen) {
            setFormData({});
            setIsNewPatient(false);
            setErrors({});
            setFilteredPatients([]);
            setSelectedSlot(null);
            setBookedSlots([]);
            setSelectedPatient(null);
            setPatientHistory(null);
            setHistoryLoading(false);
            if (patients) setFilteredPatients(patients);
        }
    }, [isOpen, patients]);
```

- [ ] **Step 5: Reset new state in `handleClose`**

Find:
```js
    const handleClose = () => {
        setIsNewPatient(false);
        setFormData({});
        setErrors({});
        onClose();
    };
```

Replace with:
```js
    const handleClose = () => {
        setIsNewPatient(false);
        setFormData({});
        setErrors({});
        setSelectedPatient(null);
        setPatientHistory(null);
        setHistoryLoading(false);
        onClose();
    };
```

- [ ] **Step 6: Build to verify**

```bash
cd e:/Projects/HOSPITAL/frontend && npm run build 2>&1 | tail -5
```

Expected: `✓ built in X.XXs` — zero errors.

- [ ] **Step 7: Commit**

```bash
cd e:/Projects/HOSPITAL && git add frontend/src/components/AppointmentModal.jsx && git commit -m "feat(receptionist): add selectedPatient/patientHistory state to AppointmentModal"
```

---

### Task 2: History Fetch useEffect + Doctor Pre-fill

**Files:**
- Modify: `frontend/src/components/AppointmentModal.jsx`

- [ ] **Step 1: Read the file landmarks**

Open `frontend/src/components/AppointmentModal.jsx`. Confirm:
- Line ~73–97: the slots fetch `useEffect` — dependency array is `[formData.doctorId, formData.appointmentDate]`
- The new history `useEffect` goes immediately after this slots effect (after line 97)

- [ ] **Step 2: Add the history fetch useEffect**

Find:
```js
    }, [formData.doctorId, formData.appointmentDate]);

    const handleSlotSelect = (time) => {
```

Replace with:
```js
    }, [formData.doctorId, formData.appointmentDate]);

    // Fetch patient history when a returning patient is selected
    useEffect(() => {
        if (!selectedPatient?.id) {
            setPatientHistory(null);
            setHistoryLoading(false);
            return;
        }
        const doctorAlreadySet = !!formData.doctorId;
        setHistoryLoading(true);
        setPatientHistory(null);
        hospitalService.getAppointmentsByPatient(selectedPatient.id)
            .then(data => {
                const history = (data || [])
                    .filter(a => (a.status || '').toUpperCase() === 'CONFIRMED')
                    .sort((a, b) => new Date(b.appointmentDate) - new Date(a.appointmentDate));
                setPatientHistory(history);
                if (history.length > 0 && !doctorAlreadySet) {
                    const lastDoctorId = history[0].doctorId || history[0].doctor?.id;
                    if (lastDoctorId && doctors.some(d => String(d.id) === String(lastDoctorId))) {
                        setFormData(prev => ({ ...prev, doctorId: String(lastDoctorId) }));
                    }
                }
            })
            .catch(() => setPatientHistory([]))
            .finally(() => setHistoryLoading(false));
    }, [selectedPatient?.id]);

    const handleSlotSelect = (time) => {
```

Notes:
- `doctorAlreadySet` is captured before the async call so we don't read stale `formData.doctorId` inside `.then()`
- `.catch()` silently sets `patientHistory` to `[]` — the card hides and booking is unaffected
- `hospitalService.getAppointmentsByPatient` returns `response.data` directly (the array), not a wrapper object

- [ ] **Step 3: Build to verify**

```bash
cd e:/Projects/HOSPITAL/frontend && npm run build 2>&1 | tail -5
```

Expected: `✓ built in X.XXs` — zero errors.

- [ ] **Step 4: Commit**

```bash
cd e:/Projects/HOSPITAL && git add frontend/src/components/AppointmentModal.jsx && git commit -m "feat(receptionist): fetch patient history and pre-fill doctor on patient select"
```

---

### Task 3: Welcome Back Card JSX

**Files:**
- Modify: `frontend/src/components/AppointmentModal.jsx`

- [ ] **Step 1: Find the insertion point**

In the JSX (`return (` block), find the end of the existing-patient section and the start of the doctor section. It looks like:

```jsx
                        {errors.patientId && <p className="text-red-500 text-xs mt-1">{errors.patientId}</p>}
                    </div>
                    )}

                    {/* Doctor Selection */}
                    <div>
```

The Welcome Back card inserts between the closing `)}` of the patient conditional and the `{/* Doctor Selection */}` comment.

- [ ] **Step 2: Insert the Welcome Back card**

Find:
```jsx
                    {/* Doctor Selection */}
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">Doctor *</label>
```

Replace with:
```jsx
                    {/* Welcome Back Card */}
                    {!isNewPatient && historyLoading && (
                        <div className="h-12 bg-gray-100 rounded-xl animate-pulse" />
                    )}
                    {!isNewPatient && !historyLoading && patientHistory && patientHistory.length > 0 && (() => {
                        const last = patientHistory[0];
                        const firstName = (selectedPatient?.name || '').split(' ')[0];
                        const count = patientHistory.length;
                        const lastDate = new Date(last.appointmentDate).toLocaleDateString('en-IN', {
                            day: 'numeric', month: 'short', year: 'numeric'
                        });
                        const doctorName = last.doctor?.name || null;
                        return (
                            <div className="bg-white border border-gray-200 rounded-xl shadow-sm p-4">
                                <div className="flex items-start justify-between gap-3">
                                    <div>
                                        <p className="text-sm font-bold text-gray-900">Welcome back, {firstName}!</p>
                                        <p className="text-xs text-gray-500 mt-0.5">
                                            {count} visit{count !== 1 ? 's' : ''} · Last seen {lastDate}
                                            {doctorName ? ` · ${doctorName}` : ''}
                                        </p>
                                    </div>
                                    <span className="shrink-0 text-xs font-semibold px-2 py-0.5 rounded-full bg-amber-100 text-amber-700">
                                        FOLLOW-UP SUGGESTED
                                    </span>
                                </div>
                            </div>
                        );
                    })()}

                    {/* Doctor Selection */}
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">Doctor *</label>
```

IMPORTANT: The `{/* Doctor Selection */}` comment and the `<div>` / `<label>` that follow it MUST remain as the last lines of the replacement.

- [ ] **Step 3: Self-review checklist**

Before building, verify:
- [ ] Loading skeleton only shown when `!isNewPatient && historyLoading`
- [ ] Card only shown when `!isNewPatient && !historyLoading && patientHistory && patientHistory.length > 0`
- [ ] `firstName` derived from `selectedPatient?.name.split(' ')[0]`
- [ ] `count` is `patientHistory.length`
- [ ] `lastDate` uses `toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })`
- [ ] `doctorName` falls back to `null` (hidden when null)
- [ ] `FOLLOW-UP SUGGESTED` badge is amber, static (no `onClick`)
- [ ] `{/* Doctor Selection */}` comment preserved on the correct line

- [ ] **Step 4: Build and verify**

```bash
cd e:/Projects/HOSPITAL/frontend && npm run build 2>&1 | tail -5
```

Expected: `✓ built in X.XXs` — zero errors.

- [ ] **Step 5: Commit**

```bash
cd e:/Projects/HOSPITAL && git add frontend/src/components/AppointmentModal.jsx && git commit -m "feat(receptionist): Welcome Back card with last visit and follow-up suggestion"
```

---

## Self-Review

**Spec coverage:**
- ✅ Trigger: card appears when existing patient is selected in AppointmentModal — Task 1 Step 3 (`selectPatient` sets `selectedPatient`)
- ✅ Placement: below patient selector, above doctor dropdown — Task 3 Step 2
- ✅ Not shown for new patients — Task 3 Step 2 (`!isNewPatient` guards)
- ✅ Loading skeleton while fetching — Task 3 Step 2
- ✅ Card hidden when no confirmed history — Task 3 Step 2 (`patientHistory.length > 0` guard)
- ✅ Heading: "Welcome back, {firstName}!" — Task 3 Step 2
- ✅ Summary: `{count} visits · Last seen {date}` — Task 3 Step 2
- ✅ Doctor name in summary line — Task 3 Step 2
- ✅ `FOLLOW-UP SUGGESTED` amber badge, non-interactive — Task 3 Step 2
- ✅ Doctor pre-fill from last CONFIRMED appointment — Task 2 Step 2
- ✅ Pre-fill skipped if doctorAlreadySet — Task 2 Step 2
- ✅ Pre-fill skipped if doctor not in doctors list — Task 2 Step 2
- ✅ Silent failure on API error (`patientHistory = []`, card hidden) — Task 2 Step 2
- ✅ Reset on modal open — Task 1 Step 4
- ✅ Reset on modal close — Task 1 Step 5
- ✅ No new files, no new API, no backend changes — architecture

**Placeholder scan:** None found. All steps contain exact code.

**Type consistency:**
- `selectedPatient` is `null | {id, name, phone, ...}` — set in Task 1 Step 3, read in Task 2 Step 2 (`selectedPatient?.id`, `selectedPatient?.name`) and Task 3 Step 2. Consistent.
- `patientHistory` is `null | []` — `null` = not yet fetched (card hidden), `[]` = fetched but empty (card hidden). Task 2 sets it; Task 3 reads `patientHistory && patientHistory.length > 0`. Consistent.
- `doctorAlreadySet` is captured as `!!formData.doctorId` before async. `formData.doctorId` is set via `handleChange('doctorId', ...)` which stores integers (from `parseInt`) or strings (from pre-fill). The pre-fill check uses `doctors.some(d => String(d.id) === String(lastDoctorId))` to compare — handles both. Consistent.
- `last.appointmentDate` is `YYYY-MM-DD` string from the API — `new Date(last.appointmentDate)` will parse correctly in all modern JS engines (treats as local midnight in UTC). For a date-only display this is fine. Consistent.
