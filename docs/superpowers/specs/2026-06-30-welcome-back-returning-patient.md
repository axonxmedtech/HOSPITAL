# Welcome Back — Returning Patient Flow — Design Spec (R3)

**Date:** 2026-06-30
**Scope:** Frontend only — `AppointmentModal.jsx`, single file change, no new API calls, no backend changes

---

## Goal

When the receptionist selects an existing patient in the appointment booking modal, surface their visit history as a compact "Welcome Back" card — showing visit count, last seen date, and last doctor — and pre-fill the doctor dropdown with the last visit's doctor. A "FOLLOW-UP SUGGESTED" badge provides a gentle cue without auto-setting the OPD visit type.

---

## 1. Trigger and Placement

- The card appears **inside `AppointmentModal.jsx`**, immediately below the patient selector dropdown.
- It renders only when an **existing patient is selected** (not during new-patient inline registration).
- It disappears when the patient selection is cleared.
- On patient select, a `useEffect` fires `hospitalService.getAppointmentsByPatient(patientId)`.
- While the fetch is in progress, a one-line skeleton replaces the card area.
- If the patient has **no prior confirmed appointments**, the card is not rendered — first-time patients are silent.

---

## 2. Card Content

```
Welcome back, Priya!
3 visits · Last seen 12 Jun 2026 · Dr. Anand Sharma   [FOLLOW-UP SUGGESTED]
```

| Element | Detail |
|---------|--------|
| **Heading** | "Welcome back, [first name]!" — first word of `patient.name` |
| **Summary line** | `{count} visit{s} · Last seen {date} · {doctor name}` |
| **Visit count** | Number of CONFIRMED appointments for this patient |
| **Last seen date** | `appointmentDate` of most recent CONFIRMED appointment, formatted as `DD MMM YYYY` using `toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })` |
| **Doctor name** | `lastAppt.doctor?.name` — omitted if null |
| **Follow-up badge** | Amber pill: `FOLLOW-UP SUGGESTED` — static, non-interactive |

The follow-up badge is a visual hint only. It does NOT auto-set the OPD visit type radio — the receptionist or doctor decides during OPD creation.

The card uses the same visual style as existing info panels in the modal: white background, `border border-gray-200`, `rounded-xl`, `shadow-sm`, `p-4`.

---

## 3. Doctor Pre-fill

- When history loads and `patientHistory.length > 0`, set `formData.doctorId` to `lastAppt.doctorId`.
- Pre-fill is **skipped** if `formData.doctorId` is already non-empty (receptionist picked first).
- Pre-fill is **skipped silently** if `lastAppt.doctorId` is not found in the loaded `doctors` list (doctor left the hospital).
- The receptionist can freely change the pre-filled doctor — no lock, no indicator that it was auto-filled.

---

## 4. Architecture

**File changed:** `frontend/src/components/AppointmentModal.jsx` only

**No new files. No new API calls. No backend changes.**

### 4a. New state

```js
const [patientHistory, setPatientHistory] = useState(null);
// null = not yet fetched, [] = fetched but empty, [...] = has history
const [historyLoading, setHistoryLoading] = useState(false);
```

### 4b. New useEffect

Triggered on `selectedPatient` change:

```js
useEffect(() => {
  if (!selectedPatient?.id) {
    setPatientHistory(null);
    setHistoryLoading(false);
    return;
  }
  setHistoryLoading(true);
  setPatientHistory(null);
  hospitalService.getAppointmentsByPatient(selectedPatient.id)
    .then(res => {
      const history = (res.data || [])
        .filter(a => (a.status || '').toUpperCase() === 'CONFIRMED')
        .sort((a, b) => new Date(b.appointmentDate) - new Date(a.appointmentDate));
      setPatientHistory(history);
      // Doctor pre-fill: only if doctor not already selected
      if (history.length > 0 && !formData.doctorId) {
        const lastDoctorId = history[0].doctorId || history[0].doctor?.id;
        if (lastDoctorId && doctors.some(d => String(d.id) === String(lastDoctorId))) {
          setFormData(prev => ({ ...prev, doctorId: String(lastDoctorId) }));
        }
      }
    })
    .catch(() => setPatientHistory([]))
    .finally(() => setHistoryLoading(false));
}, [selectedPatient?.id]);
```

- `.catch()` sets `patientHistory` to `[]` — the card is not shown, no error is surfaced to the receptionist. The Welcome Back feature is non-critical; booking must not be blocked by a failed history fetch.

### 4c. Card render

Inserted immediately below the patient selector, before the doctor dropdown:

```jsx
{/* Welcome Back Card */}
{historyLoading && (
  <div className="h-10 bg-gray-100 rounded-xl animate-pulse" />
)}
{!historyLoading && patientHistory && patientHistory.length > 0 && (() => {
  const last = patientHistory[0];
  const firstName = (selectedPatient.name || '').split(' ')[0];
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
```

---

## 5. Error Handling

| Scenario | Behaviour |
|----------|-----------|
| History fetch fails | `patientHistory` set to `[]` — card hidden, no error shown |
| Patient has no confirmed appointments | `patientHistory` is `[]` — card hidden |
| Last doctor not in `doctors` list | Pre-fill skipped, doctor dropdown stays empty |
| Patient cleared mid-fetch | Effect re-runs with null patient, resets state |

---

## 6. Summary

| Constraint | Detail |
|---|---|
| Files changed | `frontend/src/components/AppointmentModal.jsx` only |
| New state | `patientHistory` (null/array), `historyLoading` (boolean) |
| New API calls | Uses existing `hospitalService.getAppointmentsByPatient(id)` |
| New components | None |
| Backend changes | None |
| Critical path | No — fetch failure is silent, booking always proceeds |
