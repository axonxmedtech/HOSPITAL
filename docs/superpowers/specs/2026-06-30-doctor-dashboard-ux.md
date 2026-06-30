# Doctor Dashboard UX — Design Spec

**Date:** 2026-06-30  
**Scope:** Frontend only — `DoctorDashboard.jsx` overview tab header section only

---

## Goal

Replace the generic "Overview" header + 5 plain stat cards with a Strynkix-style "Today's Schedule" landing screen that answers "What do I need to do now?" the moment the doctor logs in.

---

## What Changes

Only the **overview tab top section** changes. All existing tabs (appointments, OPD, queue, IPD, patients, billing, inventory) remain exactly as-is. The lower dual-panel (appointments list + queue) within the overview tab also stays untouched. The low stock alert banner is preserved unchanged.

---

## 1. Greeting Header

Replaces the current `<h2>Overview</h2>` + "Add Patient" button row.

**Content:**
- Time-aware greeting: "Good morning / afternoon / evening, Dr. [firstName]"
- Name logic: take `user.name`, split on space, take first word. If it doesn't already start with "Dr.", prefix with "Dr."
  - e.g. `user.name = "Sharma Raj"` → "Dr. Sharma"
  - e.g. `user.name = "Dr. Anil Kumar"` → "Dr. Anil" (first word after split is "Dr.", so take second word instead, prefix "Dr.")
  - Fallback: `user.username` if `user.name` is absent
- Subtitle: "Doctor · [Day, DD Month YYYY]" — e.g. "Doctor · Wednesday, 30 June 2026"

**Implementation:** Pure JS — `new Date().getHours()` for time of day, `new Date()` formatted for the subtitle. No new API calls.

---

## 2. Stat Cards (3–4 cards, clickable)

Replaces the current 5-card grid. Cards are clickable and navigate to the relevant tab.

| Card | Value | Source | Click navigates to | Color signal |
|---|---|---|---|---|
| Today's OPD | `stats.today` | Already fetched | `?tab=opd` | Blue |
| In Queue | `queueEntries.length` | Already fetched | `?tab=queue` | Orange if > 10 |
| Pending | `stats.pending` | Already fetched | `?tab=appointments` | Red if > 5, green if 0 |
| IPD Patients | `opds.length` | Already fetched | `?tab=ipd` | Blue — **only rendered if `hasIPD === true`** |

Grid is `grid-cols-3` when `hasIPD` is false, `grid-cols-4` when `hasIPD` is true.

**Removed cards:** "Current Patient", "Next Patient", "Total Appointments" — these are not removed from the dashboard, only from the top card grid. "Current Patient" and "Next Patient" already appear in the queue panel lower on the page.

---

## 3. Quick Actions Row

Four prominent buttons below the stat cards, surfacing existing actions:

| Button | Style | Action | Condition |
|---|---|---|---|
| Start OPD → | Dark (slate-900) | `setActiveTab('opd')` | Always shown |
| Continue IPD → | Blue | `setActiveTab('ipd')` | Only if `hasIPD === true` |
| View Appointments | Outline | `setActiveTab('appointments')` | Always shown |
| Add Patient | Outline | `setIsAddPatientModalOpen(true)` | Only if `user?.receptionMode === 'SOLO'` |

The existing "Add Patient" button in the old header row is removed from there and only appears here. No behavior change.

---

## 4. Preserved Sections

These remain **unchanged**:
- Low stock alert banner
- Dual-panel lower section (Appointments list + Queue panel)
- All non-overview tabs

---

## 5. Architecture

| Constraint | Detail |
|---|---|
| Files changed | `frontend/src/pages/hospital/DoctorDashboard.jsx` only |
| New state variables | None |
| New API calls | None — all values (`stats`, `queueEntries`, `opds`) already fetched in existing `loadData` |
| New helper functions | `getGreeting()` and `getDoctorFirstName()` — pure functions defined before the JSX return |
| Regression risk | Zero — only overview tab header replaced; all tabs and lower panels untouched |

### Helper function specs

```js
const getGreeting = () => {
    const h = new Date().getHours();
    if (h < 12) return 'Good morning';
    if (h < 17) return 'Good afternoon';
    return 'Good evening';
};

const getDoctorFirstName = () => {
    const name = user?.name || user?.username || 'Doctor';
    const parts = name.trim().split(/\s+/);
    // If name starts with "Dr." prefix, take the next word
    const first = parts[0].toLowerCase() === 'dr.' ? (parts[1] || parts[0]) : parts[0];
    return first.startsWith('Dr') ? first : `Dr. ${first}`;
};

const todayLabel = new Date().toLocaleDateString('en-IN', {
    weekday: 'long', day: 'numeric', month: 'long', year: 'numeric'
});
```
