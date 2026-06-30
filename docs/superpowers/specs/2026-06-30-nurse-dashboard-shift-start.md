# Nurse Dashboard Shift-Start View — Design Spec (N1)

**Date:** 2026-06-30
**Scope:** Frontend only — `NurseDashboard.jsx`, single file change, no new files

---

## Goal

Give the nurse a prioritized shift-start view the moment they log in: overdue tasks on top, due-soon next, with a 3-stat summary row showing what needs attention now. No new API calls — derived entirely from the existing `getMyTasks()` response.

---

## 1. Default Tab

Change the default `tab` state from `'patients'` to `'tasks'`. The nurse lands on the task list on every login/refresh.

---

## 2. Stat Summary Row

Rendered at the top of the tasks tab (above the task list). Three chips, derived from `tasks` state:

| Chip | Value | Color |
|---|---|---|
| ⚠️ N Overdue | count of tasks where `scheduledAt < now` | Red (`bg-red-50 text-red-700 border-red-200`) |
| 🕐 N Due Soon | count of tasks where `scheduledAt` is within 60 min from now | Amber (`bg-amber-50 text-amber-700 border-amber-200`) |
| 👤 N Patients | count of unique patient names across all tasks | Blue (`bg-blue-50 text-blue-700 border-blue-200`) |

Hidden entirely when `tasks` is empty. Each chip: `text-xs font-semibold px-3 py-1.5 rounded-full border`.

---

## 3. Task Sort Order

Tasks are sorted into three buckets, each sorted by `scheduledAt` ascending within the bucket:

1. **Overdue** — `scheduledAt < now` (oldest overdue first)
2. **Due Soon** — `scheduledAt >= now && scheduledAt <= now + 60 min` (soonest first)
3. **Upcoming** — everything else (earliest first)

Tasks with no `scheduledAt` go at the bottom of Upcoming.

Sorting is a derived computation (no new state). Use a single `sortedTasks` constant:

```js
const now = new Date();
const MS_60 = 60 * 60 * 1000;

const overdue = tasks.filter(t => t.scheduledAt && new Date(t.scheduledAt) < now)
    .sort((a, b) => new Date(a.scheduledAt) - new Date(b.scheduledAt));

const dueSoon = tasks.filter(t => t.scheduledAt && new Date(t.scheduledAt) >= now && new Date(t.scheduledAt) - now <= MS_60)
    .sort((a, b) => new Date(a.scheduledAt) - new Date(b.scheduledAt));

const upcoming = tasks.filter(t => !t.scheduledAt || (new Date(t.scheduledAt) >= now && new Date(t.scheduledAt) - now > MS_60))
    .sort((a, b) => {
        if (!a.scheduledAt) return 1;
        if (!b.scheduledAt) return -1;
        return new Date(a.scheduledAt) - new Date(b.scheduledAt);
    });

const sortedTasks = [...overdue, ...dueSoon, ...upcoming];
```

---

## 4. Task Card Design

Replace the existing flat card with a bordered, priority-coded card.

**Visual structure (per card):**

```
[colored left border]  [time label]  [task description]     [patient · bed]
                                     [orderType]            [action button]
```

**Left border:** `border-l-4` — red (`border-red-400`) for overdue, amber (`border-amber-400`) for due soon, transparent/gray (`border-gray-200`) for upcoming.

**Background:** `bg-red-50` for overdue, `bg-amber-50` for due soon, `bg-white` for upcoming.

**Time label** (small, colored text, left of description):
- Overdue: `OVERDUE {N} min` in `text-red-600 font-semibold text-xs` — minutes = `Math.floor((now - scheduledAt) / 60000)`
- Due soon: `Due in {N} min` in `text-amber-600 font-semibold text-xs`
- Upcoming with time: `Due at {HH:MM}` in `text-gray-500 text-xs`
- No scheduledAt: `No time set` in `text-gray-400 text-xs`

**Patient info:** `{task.patientName}` · `{task.bedNumber || task.wardName || '—'}` — right side of card, `text-sm text-gray-600`.

**Action button:** Unchanged — `💊 Administer` for `MEDICATION`, `📋 Execute` for all others. Same `MedicationAdministrationModal` flow.

---

## 5. Load Behavior

Tasks load on mount (not just on tab click). Change the `useEffect` to always call `loadTasks()` on mount, regardless of initial tab:

```js
useEffect(() => {
    loadTasks();
}, []);

useEffect(() => {
    if (tab === 'patients') loadPatients();
}, [tab]);
```

This ensures the stat row is populated immediately when the nurse arrives on the tasks tab.

---

## 6. Architecture

| Constraint | Detail |
|---|---|
| Files changed | `frontend/src/pages/hospital/NurseDashboard.jsx` only |
| New state variables | None — all derived from existing `tasks` state |
| New API calls | None — `getMyTasks()` already exists |
| New components | None — inline JSX |
| My Patients tab | Unchanged |
| MedicationAdministrationModal | Unchanged |
| nurseService | Unchanged |
| Backend | Unchanged |

**Stat derivations** (no new state, computed inline):
```js
const overdueCount = tasks.filter(t => t.scheduledAt && new Date(t.scheduledAt) < now).length;
const dueSoonCount = tasks.filter(t => t.scheduledAt && new Date(t.scheduledAt) >= now && new Date(t.scheduledAt) - now <= MS_60).length;
const patientCount = new Set(tasks.map(t => t.patientName).filter(Boolean)).size;
```
