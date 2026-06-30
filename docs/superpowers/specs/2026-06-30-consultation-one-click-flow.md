# Consultation One-Click Flow — Design Spec (D3)

**Date:** 2026-06-30
**Scope:** Frontend only — `DoctorDashboard.jsx`, single file change, no new API calls, no backend changes

---

## Goal

Surface the next queued OPD patient as a prominent call-to-action in the doctor overview tab so the doctor can start a consultation with a single tap — without scanning a table or navigating to another tab.

---

## 1. Placement

Widget renders in the overview tab (`activeTab === 'overview'`), between the Quick Actions row and the dual-panel grid (appointments + queue).

Guard: only renders when `queueEntries.length > 0`.

---

## 2. Derived Data

Computed inline before `return (`, using existing `queueEntries` state. No new state, no new API calls.

```js
const sortedQueue = [...queueEntries].sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));
const consultingEntry = sortedQueue.find(q => q.opd?.status === 'CONSULTING');
const nextQueuedEntry = sortedQueue.find(q => q.opd?.status === 'QUEUED');
const nextQueuedToken = nextQueuedEntry
  ? sortedQueue.indexOf(nextQueuedEntry) + 1
  : null;
```

---

## 3. Widget States

### 3a. Nobody consulting yet — first entry is QUEUED

```
┌──────────────────────────────────────────────────────────────┐
│  Next Patient                                   4 waiting    │
│  Priya Sharma · Token #1 · Waiting since 10:05 AM           │
│                              [ Start Consultation → ]        │
└──────────────────────────────────────────────────────────────┘
```

- Header: "Next Patient" + "N waiting" badge (green when 1–3, amber when 4–7, red when 8+)
- Body: patient name (bold) · "Token #N" · "Waiting since HH:MM AM/PM"
- Button: "Start Consultation →" (slate-900), calls `handleStartOpdConsultation(nextQueuedEntry.opd)`
- Button disabled + shows "Starting…" while `startingConsultationId` is truthy

### 3b. Someone already consulting — first entry is CONSULTING

```
┌──────────────────────────────────────────────────────────────┐
│  Now Consulting: Ravi Kumar    [CONSULTING]     4 waiting    │
│  ─────────────────────────────────────────────────────────── │
│  Up Next: Priya Sharma · Token #1 · Waiting since 10:05 AM  │
│                              [ Start Consultation → ]        │
└──────────────────────────────────────────────────────────────┘
```

- Top row: "Now Consulting: [name]" + green CONSULTING badge + "N waiting" count
- Divider
- Bottom row: "Up Next: [name] · Token #N · Waiting since HH:MM"
- Button: "Start Consultation →", same handler

If `consultingEntry` exists but `nextQueuedEntry` is null (only one patient, currently being consulted): show only the "Now Consulting" row, no button, no Up Next row.

### 3c. Queue empty

Widget is not rendered (guarded by `queueEntries.length > 0`).

---

## 4. Patient Name Resolution

```js
const patientName = (entry) =>
  entry.opd?.patient?.name || entry.opd?.patientName || 'Unknown';
```

---

## 5. Wait Time Label

```js
const waitSince = (entry) =>
  new Date(entry.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
```

---

## 6. Waiting Count Badge

- 1–3: `bg-green-100 text-green-700`
- 4–7: `bg-amber-100 text-amber-700`
- 8+: `bg-red-100 text-red-700`

Count = `queueEntries.length`.

---

## 7. Architecture

| Constraint | Detail |
|---|---|
| Files changed | `frontend/src/pages/hospital/DoctorDashboard.jsx` only |
| New state variables | None |
| New API calls | None — uses existing `queueEntries` state |
| New handlers | None — button calls existing `handleStartOpdConsultation(opd)` |
| Insertion point | After Quick Actions `</div>` (~line 1069), before `{/* Side-by-Side Lists */}` (~line 1071) |
| Guard | `{queueEntries.length > 0 && (...)}` |
| `startingConsultationId` | Already tracked — disable + "Starting…" label while truthy |

Widget is purely additive — removing it requires deleting ~60 lines with no other impact.
