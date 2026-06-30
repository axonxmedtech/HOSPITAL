# Shift Handover Summary — Design Spec (N3)

**Date:** 2026-06-30
**Scope:** Backend (1 new column, 1 new endpoint) + Frontend (admin settings card + nurse handover card)

---

## Goal

Give the incoming nurse an instant picture of their ward at shift start: what tasks are still pending, and what happened during the shift they're inheriting. The shift window is either auto-detected from fixed 8-hour blocks (hospital-wide setting) or entered manually by the nurse.

---

## 1. Shift Mode Setting

### 1a. Database

```sql
ALTER TABLE hospital_settings
  ADD COLUMN shift_mode VARCHAR(20) NOT NULL DEFAULT 'FIXED';
```

Two valid values: `FIXED`, `MANUAL`.

### 1b. Backend entity + DTO

**`HospitalSetting.java`** — add field:
```java
@Column(name = "shift_mode", nullable = false, length = 20)
private String shiftMode = "FIXED";
```

**`HospitalSettingDTO.java`** — add field:
```java
private String shiftMode;
```

**`HospitalAuthService.updateHospitalOperationsSettings`** — add validation:
```java
List<String> validShiftModes = List.of("FIXED", "MANUAL");
if (!validShiftModes.contains(dto.getShiftMode())) {
    throw new IllegalArgumentException("Invalid shiftMode: " + dto.getShiftMode());
}
```

Include `shiftMode` in the JPQL UPDATE query and in the response DTO mapping.

### 1c. Admin Settings UI (`HospitalAdminDashboard.jsx`)

A new "Shift Mode" card in the Settings tab, following the same toggle-card pattern as the three existing cards (receptionMode, billingHandler, inClinic).

```
┌──────────────────────────────────────────────────────┐
│  [clock icon]  Shift Mode                            │
│  How nurse shift windows are determined              │
│                                                      │
│  ● Fixed Shifts    Morning 7–15 · Evening 15–23 ·   │
│                    Night 23–7 (auto-detected)        │
│  ○ Manual Entry    Nurse enters shift start time     │
└──────────────────────────────────────────────────────┘
```

- Radio buttons (not toggle) since the two modes are mutually exclusive named options.
- Save triggers `PUT /hospital/settings/operations` with the full settings payload including `shiftMode`.
- Confirmation modal: "Nurses will now enter their shift start time manually each session." (MANUAL) or "Shift windows will be auto-detected from the current time." (FIXED).

---

## 2. Fixed Shift Windows

When `shiftMode = 'FIXED'`, the frontend auto-detects the current shift from the local clock hour. No user input needed.

| Shift | Window | Condition |
|-------|--------|-----------|
| Morning | 07:00 – 15:00 | `hour >= 7 && hour < 15` |
| Evening | 15:00 – 23:00 | `hour >= 15 && hour < 23` |
| Night | 23:00 – 07:00 (next day) | `hour >= 23 \|\| hour < 7` |

Night shift spans midnight: `shiftStart` is the previous calendar day at 23:00 when `hour < 7`, otherwise today at 23:00. `shiftEnd` is always `shiftStart + 8h`.

The full implementation is in Section 4d (`getShiftWindow`) which handles both FIXED and MANUAL in one function — do not implement a separate fixed-only function.

---

## 3. Shift Activity API Endpoint

### 3a. Endpoint

```
GET /hospital/nurse/shift-activity?shiftStart={ISO}&shiftEnd={ISO}
```

**Auth:** `NURSE` or `HOSPITAL_ADMIN` role required. `hospitalId` from security context — never from query params.

**Response:**
```json
{
  "completedTaskCount": 12,
  "newAdmissionCount": 3
}
```

### 3b. Backend implementation

**Repository methods** (new JPQL `@Query` on existing repositories):

`NurseTaskRepository`:
```java
@Query("SELECT COUNT(t) FROM NurseTask t WHERE t.hospitalId = :hospitalId " +
       "AND t.executedAt BETWEEN :shiftStart AND :shiftEnd " +
       "AND t.status IN ('DONE', 'HELD', 'REFUSED', 'SKIPPED')")
long countCompletedInShift(@Param("hospitalId") Long hospitalId,
                            @Param("shiftStart") LocalDateTime shiftStart,
                            @Param("shiftEnd") LocalDateTime shiftEnd);
```

`IpdAdmissionRepository`:
```java
@Query("SELECT COUNT(a) FROM IpdAdmission a WHERE a.hospitalId = :hospitalId " +
       "AND a.admissionDatetime BETWEEN :shiftStart AND :shiftEnd")
long countAdmissionsInShift(@Param("hospitalId") Long hospitalId,
                             @Param("shiftStart") LocalDateTime shiftStart,
                             @Param("shiftEnd") LocalDateTime shiftEnd);
```

**Service method** (`NurseService` or existing nurse service class):
```java
public ShiftActivityDTO getShiftActivity(LocalDateTime shiftStart, LocalDateTime shiftEnd) {
    Long hospitalId = securityHelper.getCurrentHospitalId();
    long tasks = nurseTaskRepository.countCompletedInShift(hospitalId, shiftStart, shiftEnd);
    long admissions = ipdAdmissionRepository.countAdmissionsInShift(hospitalId, shiftStart, shiftEnd);
    return new ShiftActivityDTO(tasks, admissions);
}
```

**Controller** (existing `NurseController` or `HospitalNurseController`):
```java
@GetMapping("/nurse/shift-activity")
@PreAuthorize("hasAnyRole('NURSE', 'HOSPITAL_ADMIN')")
public ResponseEntity<ShiftActivityDTO> getShiftActivity(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime shiftStart,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime shiftEnd) {
    return ResponseEntity.ok(nurseService.getShiftActivity(shiftStart, shiftEnd));
}
```

**`ShiftActivityDTO`** (new DTO):
```java
public record ShiftActivityDTO(long completedTaskCount, long newAdmissionCount) {}
```

### 3c. Frontend service method

```js
getShiftActivity: async (shiftStart, shiftEnd) => {
    const response = await apiClient.get('/hospital/nurse/shift-activity', {
        params: { shiftStart, shiftEnd }
    });
    return response.data;
}
```

---

## 4. NurseDashboard Handover Card

### 4a. Placement

Inserted at the top of the overview tab in `NurseDashboard.jsx`, before any existing content. Always visible when the nurse opens the dashboard.

### 4b. New state

```js
const [shiftMode, setShiftMode] = useState('FIXED');         // from settings
const [manualShiftStart, setManualShiftStart] = useState(''); // MANUAL mode only
const [shiftActivity, setShiftActivity] = useState(null);    // { completedTaskCount, newAdmissionCount }
const [shiftActivityLoading, setShiftActivityLoading] = useState(false);
```

### 4c. Settings fetch

On mount, fetch `GET /hospital/settings/operations` and read `shiftMode`. Store in state. This is a single fetch shared with any other settings the dashboard might need.

```js
useEffect(() => {
    hospitalService.getHospitalOperationsSettings()
        .then(data => {
            setShiftMode(data.shiftMode || 'FIXED');
            if (data.shiftMode === 'MANUAL') {
                // pre-fill to nearest fixed window start
                const hour = new Date().getHours();
                const defaultStart = hour >= 15 && hour < 23 ? '15:00'
                    : hour >= 7 && hour < 15 ? '07:00' : '23:00';
                setManualShiftStart(defaultStart);
            }
        })
        .catch(() => {}); // silent — card still works with FIXED default
}, []);
```

### 4d. Shift window derivation

```js
function getShiftWindow(shiftMode, manualShiftStart) {
    if (shiftMode === 'MANUAL' && manualShiftStart) {
        const today = new Date();
        const [h, m] = manualShiftStart.split(':').map(Number);
        const start = new Date(today.getFullYear(), today.getMonth(), today.getDate(), h, m);
        const end = new Date(start.getTime() + 8 * 60 * 60 * 1000);
        return {
            label: `Manual Shift · ${manualShiftStart}–${end.getHours().toString().padStart(2, '0')}:${end.getMinutes().toString().padStart(2, '0')}`,
            start: start.toISOString(),
            end: end.toISOString()
        };
    }
    // FIXED
    const hour = new Date().getHours();
    const today = new Date().toISOString().slice(0, 10);
    if (hour >= 7 && hour < 15) return { label: 'Morning Shift · 07:00–15:00', start: `${today}T07:00:00`, end: `${today}T15:00:00` };
    if (hour >= 15 && hour < 23) return { label: 'Evening Shift · 15:00–23:00', start: `${today}T15:00:00`, end: `${today}T23:00:00` };
    const isAfterMidnight = hour < 7;
    const shiftDate = isAfterMidnight ? new Date(Date.now() - 86400000).toISOString().slice(0, 10) : today;
    const nextDate = isAfterMidnight ? today : new Date(Date.now() + 86400000).toISOString().slice(0, 10);
    return { label: 'Night Shift · 23:00–07:00', start: `${shiftDate}T23:00:00`, end: `${nextDate}T07:00:00` };
}
```

### 4e. Activity fetch

```js
useEffect(() => {
    const window = getShiftWindow(shiftMode, manualShiftStart);
    if (!window) return;
    setShiftActivityLoading(true);
    hospitalService.getShiftActivity(window.start, window.end)
        .then(data => setShiftActivity(data))
        .catch(() => setShiftActivity(null))
        .finally(() => setShiftActivityLoading(false));
}, [shiftMode, manualShiftStart]);
```

### 4f. Pending Now counts (derived from existing `tasks` state)

```js
const now = new Date();
const overdueTasks = tasks.filter(t => t.scheduledAt && new Date(t.scheduledAt) < now);
const dueSoonTasks = tasks.filter(t => t.scheduledAt && new Date(t.scheduledAt) >= now
    && new Date(t.scheduledAt) <= new Date(now.getTime() + 60 * 60 * 1000));
const upcomingTasks = tasks.filter(t => !t.scheduledAt || new Date(t.scheduledAt) > new Date(now.getTime() + 60 * 60 * 1000));
```

### 4g. Card UI

```
┌────────────────────────────────────────────────────────────────┐
│  Shift Handover  ·  Morning Shift · 07:00–15:00               │
│  [MANUAL only: My shift started at ▼ 07:00 ]                  │
│                                                                │
│  PENDING NOW              │  THIS SHIFT'S ACTIVITY            │
│  ─────────────────────    │  ────────────────────────         │
│  3   Overdue    (red)     │  12  Tasks completed              │
│  5   Due soon   (amber)   │   3  New admissions               │
│  8   Upcoming   (gray)    │  [loading skeleton / "—" on err]  │
└────────────────────────────────────────────────────────────────┘
```

- Error state for "This Shift's Activity": show `—` counts with muted text "Data unavailable". "Pending Now" always works (no fetch).
- Loading skeleton: animate-pulse on the right panel only.
- Manual time input: `<input type="time">` with `onChange` re-triggering the activity fetch.

---

## 5. Architecture Summary

| Layer | Change | File |
|-------|--------|------|
| DB | `ALTER TABLE hospital_settings ADD shift_mode` | `setup/schema-full.sql` |
| Entity | Add `shiftMode` field | `HospitalSetting.java` |
| DTO | Add `shiftMode` field | `HospitalSettingDTO.java` |
| Service | Validate + save `shiftMode`; `getShiftActivity()` | `HospitalAuthService.java`, `NurseService.java` |
| Repository | 2 COUNT queries | `NurseTaskRepository.java`, `IpdAdmissionRepository.java` |
| Controller | `GET /hospital/nurse/shift-activity` | `NurseController.java` (or existing) |
| New DTO | `ShiftActivityDTO` record | `ShiftActivityDTO.java` |
| Admin UI | "Shift Mode" settings card | `HospitalAdminDashboard.jsx` |
| Nurse UI | Handover summary card | `NurseDashboard.jsx` |
| API service | `getShiftActivity()` | `hospitalService.js` |

---

## 6. Error Handling

| Scenario | Behaviour |
|----------|-----------|
| Settings fetch fails | `shiftMode` defaults to `FIXED` — card works normally |
| Shift activity fetch fails | Right panel shows `—` counts with muted "Data unavailable" text |
| `shiftStart > shiftEnd` (invalid manual input) | Frontend guards: only fetch when `shiftStart < shiftEnd`; disable time input values in the past |
| `NurseTask.hospitalId` not set | COUNT query returns 0 — safe |
