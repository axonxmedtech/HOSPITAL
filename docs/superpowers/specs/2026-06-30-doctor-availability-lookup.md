# Doctor Availability Lookup — Design Spec (R2)

**Date:** 2026-06-30
**Scope:** Frontend only — `ReceptionistDashboard.jsx`, single file change, no new API calls, no backend changes

---

## Goal

Add a "Doctor Availability" widget to the receptionist overview tab so the receptionist can instantly see how busy each doctor is today and which time slots are taken — before opening the booking modal.

---

## 1. Placement

Widget renders in the overview tab (`activeTab === 'overview'`), after the existing stat cards and before the dual-panel (appointments list + queue). Guard: only renders when `doctors.length > 0`.

---

## 2. New State

One new state variable:

```js
const [availabilityDoctorId, setAvailabilityDoctorId] = useState('');
```

Initialized to `''` (no doctor selected). Reset to `''` when switching away from the overview tab is not needed — persists across tab switches so the receptionist can return to their last viewed doctor.

---

## 3. Derived Data

Computed inline before the JSX return (no new state, no new API calls). Uses existing `appointments` and `doctors` state already loaded for the overview tab.

```js
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
const availLastAppt = availDoctorAppts[availDoctorAppts.length - 1];
const availLastTime = availLastAppt
  ? (availLastAppt.appointmentTime ?? availLastAppt.scheduledTime ?? null)
  : null;
```

---

## 4. Widget Structure

```
┌─────────────────────────────────────────────────────────┐
│ Doctor Availability                                      │
│ [ Select a doctor ▾ ]                                    │
│                                                          │
│ (when doctor selected:)                                  │
│ Dr. Sharma · Cardiology                                  │
│ Today: 6 appointments · Last at 03:30 PM                 │
│                                                          │
│ 09:00  Ravi Kumar          Confirmed                     │
│ 09:30  Meena Devi          Confirmed                     │
│ 11:00  Arun Shah           Pending                       │
│ ...                                                      │
│                                                          │
│           [ Book Appointment with this doctor → ]        │
└─────────────────────────────────────────────────────────┘
```

### 4a. Doctor selector

A `<select>` populated from the `doctors` state (already loaded):

```jsx
<select
  value={availabilityDoctorId}
  onChange={e => setAvailabilityDoctorId(e.target.value)}
  className="border border-gray-200 rounded-xl px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-300"
>
  <option value="">Select a doctor</option>
  {doctors.map(d => (
    <option key={d.id} value={d.id}>{d.name}{d.specialization ? ` · ${d.specialization}` : ''}</option>
  ))}
</select>
```

### 4b. Summary line (shown when doctor selected and `availApptCount > 0`)

```
Today: {N} appointment{N !== 1 ? 's' : ''} · Last at {HH:MM AM/PM}
```

If `availApptCount === 0`: show `No appointments scheduled today` in gray.

`availLastTime` formatted as 12-hour time:
```js
new Date(`1970-01-01T${availLastTime}`).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
```
If `availLastTime` is null: omit the "Last at" part.

### 4c. Appointment list (shown when `availDoctorAppts.length > 0`)

A compact list — each row:
```
{time}  {patientName}  {status badge}
```

- Time: `a.appointmentTime ?? a.scheduledTime` formatted as HH:MM (or "—" if absent)
- Patient: `a.patientName ?? a.patient?.name ?? '—'`
- Status badge: pill using existing status color pattern:
  - CONFIRMED → `bg-green-100 text-green-700`
  - PENDING → `bg-yellow-100 text-yellow-700`
  - CANCELLED → `bg-red-100 text-red-600 line-through`
  - Other → `bg-gray-100 text-gray-600`

Max 10 rows shown. If more: `+N more appointments` text link (no pagination needed).

### 4d. Book button

```jsx
<button
  onClick={() => setIsAddModalOpen(true)}
  className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-semibold rounded-xl transition-all"
>
  Book Appointment with this doctor →
</button>
```

Only rendered when `availabilityDoctorId` is set. Opens the existing `AppointmentModal` — receptionist selects the same doctor in the modal (no pre-fill needed, YAGNI).

---

## 5. Empty / Loading States

- `doctors` not yet loaded (empty array): widget not rendered (guarded by `doctors.length > 0`)
- No doctor selected: show only the selector with placeholder text below: `Select a doctor to see today's schedule`
- Doctor selected, `appointments` still loading (`loading` state): show a subtle `Loading...` text instead of the list

---

## 6. Architecture

| Constraint | Detail |
|---|---|
| Files changed | `frontend/src/pages/hospital/ReceptionistDashboard.jsx` only |
| New state variables | 1 — `availabilityDoctorId` (string, default `''`) |
| New API calls | None — uses existing `appointments` and `doctors` already in state |
| New components | None — inline JSX |
| AppointmentModal | Unchanged |
| hospitalService | Unchanged |
| Backend | Unchanged |
| Other tabs | Unchanged |

Widget is purely additive — removing it requires deleting ~60 lines with no other impact.
