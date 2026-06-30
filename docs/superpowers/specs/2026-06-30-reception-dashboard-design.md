# Reception Dashboard Overhaul — Design Spec

**Date:** 2026-06-30  
**Scope:** Frontend only — `ReceptionistDashboard.jsx` overview tab + `hospitalService.js`

---

## Goal

Replace the generic "Overview" header + 4 plain stat cards with a Strynkix-style "Today's Work" landing screen that answers *"What do I need to do now?"* the moment the receptionist logs in.

## What Changes

Only the **overview tab top section** changes. All existing tabs (appointments, OPD, queue, IPD, patients, billing, inventory) remain exactly as-is. The lower dual-panel (appointments list + queue) within the overview tab also stays untouched.

## 1. Greeting Header

Replaces the current `<h2>Overview</h2>` + "Add Patient" button row.

- Time-aware greeting: "Good morning / afternoon / evening, [firstName]"
- First name extracted from `user.name.split(' ')[0]` or fallback to `user.username`
- Subtitle: "Receptionist · [Day, DD Month YYYY]"
- The existing "Add Patient" button moves into Quick Actions (see section 3)

No new API calls. Pure JS — `new Date().getHours()` for time of day.

## 2. Stats Cards (4 cards, clickable)

Replaces the current static 4-card grid. Each card is clickable and navigates to the relevant tab.

| Card | Value | Source | Click navigates to |
|---|---|---|---|
| Today's Appointments | `stats.today` | Already fetched | `?tab=appointments` |
| Patients in Queue | `queueEntries.length` | Already fetched | `?tab=queue` |
| Pending Appointments | `stats.pending` | Already fetched | `?tab=appointments` |
| Available Beds | `availableBeds.length` | New: `GET /hospital/beds/available` | Informational (no tab) |

One new lightweight API call added: `hospitalService.getAvailableBeds()` → `GET /hospital/beds/available`. Called only when `activeTab === 'overview'`.

Color coding:
- Red chip if queue > 10 or pending > 5 (attention needed)
- Green chip if 0 pending
- Blue for info (beds, appointments)

## 3. Quick Actions Row

4 prominent buttons below the stats, each surfacing an existing action:

| Button | Action |
|---|---|
| + Register Patient | `setIsAddPatientModalOpen(true)` |
| + Book Appointment | `setIsAddModalOpen(true)` |
| + Admit Patient | `setIsIpdAdmitOpen(true)` |
| View Patients | `setActiveTab('patients')` |

No new modals or functionality. These buttons already exist in various tabs — we're promoting them to the front screen.

## 4. Preserved Sections

These remain **unchanged**:
- Low stock alert banner (lines 771–798 in current file)
- Dual-panel lower section: Appointments list + Queue (lines 835+)
- All non-overview tabs

## Architecture Decisions

- **Zero backend changes** — `GET /hospital/beds/available` already exists in `BedController`
- **Zero regression risk** — only the overview tab header + stats grid are replaced
- **One new state variable** — `availableBeds` (array, default `[]`)
- **One new service method** — `hospitalService.getAvailableBeds()`
