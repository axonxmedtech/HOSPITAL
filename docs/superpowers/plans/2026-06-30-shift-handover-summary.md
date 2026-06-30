# Shift Handover Summary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a per-hospital "Shift Mode" setting (FIXED/MANUAL) and a NurseDashboard handover summary card that shows pending task counts and shift activity (completed tasks + new admissions) for the current shift window.

**Architecture:** Six tasks across backend and frontend. Tasks 1–3 are backend: DB migration → settings stack → shift-activity endpoint. Tasks 4–6 are frontend: service method → admin settings card → nurse handover card. Each task is independently committable.

**Tech Stack:** Spring Boot 3 / JPA / MySQL backend; React 18 / Tailwind CSS frontend; existing `hospital_settings` / `HospitalSettingDTO` / `NurseDashboardController` / `NurseDashboardService` patterns.

---

## File Map

| Action | File |
|--------|------|
| Modify | `setup/schema-full.sql` |
| Modify | `backend/src/main/java/com/hms/entity/HospitalSetting.java` |
| Modify | `backend/src/main/java/com/hms/dto/HospitalSettingDTO.java` |
| Modify | `backend/src/main/java/com/hms/repository/HospitalSettingRepository.java` |
| Modify | `backend/src/main/java/com/hms/service/hospital/HospitalAuthService.java` |
| Create | `backend/src/main/java/com/hms/dto/ShiftActivityDTO.java` |
| Modify | `backend/src/main/java/com/hms/repository/NurseTaskRepository.java` |
| Modify | `backend/src/main/java/com/hms/service/hospital/NurseDashboardService.java` |
| Modify | `backend/src/main/java/com/hms/controller/hospital/NurseDashboardController.java` |
| Modify | `frontend/src/services/hospitalService.js` |
| Modify | `frontend/src/pages/hospital/HospitalAdminDashboard.jsx` |
| Modify | `frontend/src/pages/hospital/NurseDashboard.jsx` |

---

### Task 1: DB Migration + Entity + DTO + Repository

**Files:**
- Modify: `setup/schema-full.sql`
- Modify: `backend/src/main/java/com/hms/entity/HospitalSetting.java`
- Modify: `backend/src/main/java/com/hms/dto/HospitalSettingDTO.java`
- Modify: `backend/src/main/java/com/hms/repository/HospitalSettingRepository.java`

- [ ] **Step 1: Add `shift_mode` column to schema**

Find in `setup/schema-full.sql` the `hospital_settings` table CREATE statement:
```sql
CREATE TABLE `hospital_settings` (
  ...
  `in_clinic` tinyint(1) DEFAULT 1,
  PRIMARY KEY (`id`),
```

Add the new column before the `PRIMARY KEY` line:
```sql
CREATE TABLE `hospital_settings` (
  ...
  `in_clinic` tinyint(1) DEFAULT 1,
  `shift_mode` varchar(20) NOT NULL DEFAULT 'FIXED',
  PRIMARY KEY (`id`),
```

Then run the migration on your local database:
```sql
ALTER TABLE hospital_settings ADD COLUMN shift_mode VARCHAR(20) NOT NULL DEFAULT 'FIXED';
```

- [ ] **Step 2: Add `shiftMode` field to `HospitalSetting` entity**

Open `backend/src/main/java/com/hms/entity/HospitalSetting.java`. The file ends with the `inClinic` getter/setter block. Add the new field and explicit getter/setter following the exact existing pattern:

Find:
```java
    @Column(name = "in_clinic", nullable = false)
    private Boolean inClinic = true;

    public Long getId() {
```

Replace with:
```java
    @Column(name = "in_clinic", nullable = false)
    private Boolean inClinic = true;

    @Column(name = "shift_mode", nullable = false, length = 20)
    private String shiftMode = "FIXED";

    public Long getId() {
```

Then find the end of the `setInClinic` method and add getter/setter before the closing `}` of the class:

Find:
```java
    public void setInClinic(Boolean inClinic) {
        this.inClinic = inClinic;
    }
}
```

Replace with:
```java
    public void setInClinic(Boolean inClinic) {
        this.inClinic = inClinic;
    }

    public String getShiftMode() {
        return shiftMode;
    }

    public void setShiftMode(String shiftMode) {
        this.shiftMode = shiftMode;
    }
}
```

- [ ] **Step 3: Add `shiftMode` field to `HospitalSettingDTO`**

Open `backend/src/main/java/com/hms/dto/HospitalSettingDTO.java`.

Find:
```java
    private Boolean inClinic = true;

    public HospitalSettingDTO(String receptionMode, String billingHandler) {
```

Replace with:
```java
    private Boolean inClinic = true;

    private String shiftMode = "FIXED";

    public HospitalSettingDTO(String receptionMode, String billingHandler) {
```

The `@AllArgsConstructor` Lombok annotation will now generate a 4-arg constructor `(receptionMode, billingHandler, inClinic, shiftMode)`. The existing 3-arg calls in `HospitalAuthService` will break — Task 2 fixes them.

- [ ] **Step 4: Update `HospitalSettingRepository.updateByHospitalId` JPQL**

Open `backend/src/main/java/com/hms/repository/HospitalSettingRepository.java`.

Find:
```java
    @Modifying
    @Query("UPDATE HospitalSetting s SET s.receptionMode = :receptionMode, s.billingHandler = :billingHandler, s.inClinic = :inClinic WHERE s.hospital.id = :hospitalId")
    void updateByHospitalId(@Param("hospitalId") Long hospitalId,
                            @Param("receptionMode") String receptionMode,
                            @Param("billingHandler") String billingHandler,
                            @Param("inClinic") Boolean inClinic);
```

Replace with:
```java
    @Modifying
    @Query("UPDATE HospitalSetting s SET s.receptionMode = :receptionMode, s.billingHandler = :billingHandler, s.inClinic = :inClinic, s.shiftMode = :shiftMode WHERE s.hospital.id = :hospitalId")
    void updateByHospitalId(@Param("hospitalId") Long hospitalId,
                            @Param("receptionMode") String receptionMode,
                            @Param("billingHandler") String billingHandler,
                            @Param("inClinic") Boolean inClinic,
                            @Param("shiftMode") String shiftMode);
```

- [ ] **Step 5: Build backend to verify**

```bash
cd e:/Projects/HOSPITAL/backend && mvn clean package -DskipTests 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`. If you see `no suitable constructor` errors for `HospitalSettingDTO`, that is expected — Task 2 fixes them.

- [ ] **Step 6: Commit**

```bash
cd e:/Projects/HOSPITAL && git add setup/schema-full.sql backend/src/main/java/com/hms/entity/HospitalSetting.java backend/src/main/java/com/hms/dto/HospitalSettingDTO.java backend/src/main/java/com/hms/repository/HospitalSettingRepository.java && git commit -m "feat(settings): add shiftMode column to hospital_settings and update entity/DTO/repository"
```

---

### Task 2: HospitalAuthService — Wire shiftMode into get/update settings

**Files:**
- Modify: `backend/src/main/java/com/hms/service/hospital/HospitalAuthService.java`

- [ ] **Step 1: Read the file landmarks**

Open `backend/src/main/java/com/hms/service/hospital/HospitalAuthService.java`. Find:
- `getHospitalOperationsSettings` method (~line 588): the `.map(...)` lambda that builds the return DTO
- `updateHospitalOperationsSettings` method (~line 600): the validation block and two DTO constructor call sites

- [ ] **Step 2: Update `getHospitalOperationsSettings` to include `shiftMode`**

Find:
```java
        return hospitalSettingRepository.findByHospital_Id(user.getHospitalId())
                .map(s -> new HospitalSettingDTO(s.getReceptionMode(), s.getBillingHandler(), s.getInClinic()))
```

Replace with:
```java
        return hospitalSettingRepository.findByHospital_Id(user.getHospitalId())
                .map(s -> new HospitalSettingDTO(s.getReceptionMode(), s.getBillingHandler(), s.getInClinic(), s.getShiftMode() != null ? s.getShiftMode() : "FIXED"))
```

The `orElseGet` default after this `.map(...)` returns a new DTO with defaults — find it and add `"FIXED"` as the 4th arg:

Find:
```java
                .orElseGet(() -> new HospitalSettingDTO("HAS_RECEPTIONIST", "RECEPTIONIST", true));
```

Replace with:
```java
                .orElseGet(() -> new HospitalSettingDTO("HAS_RECEPTIONIST", "RECEPTIONIST", true, "FIXED"));
```

- [ ] **Step 3: Add `shiftMode` validation in `updateHospitalOperationsSettings`**

Find the existing validation block:
```java
        String receptionMode = dto.getReceptionMode() == null ? null : dto.getReceptionMode().trim().toUpperCase();
        String billingHandler = dto.getBillingHandler() == null ? null : dto.getBillingHandler().trim().toUpperCase();
```

Replace with:
```java
        String receptionMode = dto.getReceptionMode() == null ? null : dto.getReceptionMode().trim().toUpperCase();
        String billingHandler = dto.getBillingHandler() == null ? null : dto.getBillingHandler().trim().toUpperCase();
        String shiftMode = (dto.getShiftMode() == null || dto.getShiftMode().isBlank()) ? "FIXED" : dto.getShiftMode().trim().toUpperCase();
```

Then find the existing guard block:
```java
        if (!"HAS_RECEPTIONIST".equals(receptionMode) && !"SOLO".equals(receptionMode)) {
            throw new IllegalArgumentException("receptionMode must be HAS_RECEPTIONIST or SOLO");
        }
```

After that block (but before the SOLO cross-field check), add:
```java
        if (!"FIXED".equals(shiftMode) && !"MANUAL".equals(shiftMode)) {
            throw new IllegalArgumentException("shiftMode must be FIXED or MANUAL");
        }
```

- [ ] **Step 4: Pass `shiftMode` to `updateByHospitalId` and return DTO**

Find the repository call:
```java
        hospitalSettingRepository.updateByHospitalId(
                user.getHospitalId(),
                receptionMode,
                billingHandler,
                inClinic != null ? inClinic : settings.getInClinic()
        );
```

Replace with:
```java
        hospitalSettingRepository.updateByHospitalId(
                user.getHospitalId(),
                receptionMode,
                billingHandler,
                inClinic != null ? inClinic : settings.getInClinic(),
                shiftMode
        );
```

Find the return statement:
```java
        return new HospitalSettingDTO(receptionMode, billingHandler,
                inClinic != null ? inClinic : settings.getInClinic());
```

Replace with:
```java
        return new HospitalSettingDTO(receptionMode, billingHandler,
                inClinic != null ? inClinic : settings.getInClinic(), shiftMode);
```

- [ ] **Step 5: Update the `orElseGet` new-settings branch**

Find in `updateHospitalOperationsSettings` the `orElseGet` that creates a new `HospitalSetting`:
```java
                    newSettings.setReceptionMode(receptionMode != null ? receptionMode : "HAS_RECEPTIONIST");
                    newSettings.setBillingHandler(effectiveBillingHandler != null ? effectiveBillingHandler : "RECEPTIONIST");
                    newSettings.setInClinic(inClinic != null ? inClinic : Boolean.FALSE);
                    return hospitalSettingRepository.save(newSettings);
```

Replace with:
```java
                    newSettings.setReceptionMode(receptionMode != null ? receptionMode : "HAS_RECEPTIONIST");
                    newSettings.setBillingHandler(effectiveBillingHandler != null ? effectiveBillingHandler : "RECEPTIONIST");
                    newSettings.setInClinic(inClinic != null ? inClinic : Boolean.FALSE);
                    newSettings.setShiftMode(shiftMode);
                    return hospitalSettingRepository.save(newSettings);
```

- [ ] **Step 6: Build backend**

```bash
cd e:/Projects/HOSPITAL/backend && mvn clean package -DskipTests 2>&1 | tail -10
```

Expected: `BUILD SUCCESS` with zero compilation errors.

- [ ] **Step 7: Commit**

```bash
cd e:/Projects/HOSPITAL && git add backend/src/main/java/com/hms/service/hospital/HospitalAuthService.java && git commit -m "feat(settings): wire shiftMode through HospitalAuthService get/update settings"
```

---

### Task 3: ShiftActivityDTO + NurseTaskRepository + NurseDashboardService + NurseDashboardController

**Files:**
- Create: `backend/src/main/java/com/hms/dto/ShiftActivityDTO.java`
- Modify: `backend/src/main/java/com/hms/repository/NurseTaskRepository.java`
- Modify: `backend/src/main/java/com/hms/service/hospital/NurseDashboardService.java`
- Modify: `backend/src/main/java/com/hms/controller/hospital/NurseDashboardController.java`

- [ ] **Step 1: Create `ShiftActivityDTO`**

Create `backend/src/main/java/com/hms/dto/ShiftActivityDTO.java`:

```java
package com.hms.dto;

public record ShiftActivityDTO(long completedTaskCount, long newAdmissionCount) {}
```

- [ ] **Step 2: Add COUNT query to `NurseTaskRepository`**

Open `backend/src/main/java/com/hms/repository/NurseTaskRepository.java`. It currently imports only `JpaRepository` and `List`. Add the missing imports and method:

Add to the import block:
```java
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
```

Add the new method after the existing methods:
```java
    @Query("SELECT COUNT(t) FROM NurseTask t WHERE t.hospitalId = :hospitalId " +
           "AND t.executedAt BETWEEN :shiftStart AND :shiftEnd " +
           "AND t.status IN ('DONE', 'HELD', 'REFUSED', 'SKIPPED')")
    long countCompletedInShift(@Param("hospitalId") Long hospitalId,
                               @Param("shiftStart") LocalDateTime shiftStart,
                               @Param("shiftEnd") LocalDateTime shiftEnd);
```

- [ ] **Step 3: Add `getShiftActivity` to `NurseDashboardService`**

Open `backend/src/main/java/com/hms/service/hospital/NurseDashboardService.java`. Add the missing import at the top of the import block:

```java
import com.hms.dto.ShiftActivityDTO;
import java.time.LocalDateTime;
```

Add the new method after the `getMyTasks()` method:

```java
    public ShiftActivityDTO getShiftActivity(LocalDateTime shiftStart, LocalDateTime shiftEnd) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        long completedTasks = nurseTaskRepository.countCompletedInShift(hospitalId, shiftStart, shiftEnd);
        long newAdmissions = ipdAdmissionRepository
                .findByHospitalIdAndAdmissionDatetimeBetween(hospitalId, shiftStart, shiftEnd)
                .size();

        return new ShiftActivityDTO(completedTasks, newAdmissions);
    }
```

Note: `IpdAdmissionRepository.findByHospitalIdAndAdmissionDatetimeBetween` already exists — no new repository method needed.

- [ ] **Step 4: Add `GET /hospital/nurse/dashboard/shift-activity` endpoint**

Open `backend/src/main/java/com/hms/controller/hospital/NurseDashboardController.java`. Add the missing import:

```java
import com.hms.dto.ShiftActivityDTO;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;
```

Add the new endpoint after `getMyTasks()`:

```java
    @GetMapping("/shift-activity")
    public ResponseEntity<ShiftActivityDTO> getShiftActivity(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime shiftStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime shiftEnd) {
        return ResponseEntity.ok(dashboardService.getShiftActivity(shiftStart, shiftEnd));
    }
```

- [ ] **Step 5: Build backend**

```bash
cd e:/Projects/HOSPITAL/backend && mvn clean package -DskipTests 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
cd e:/Projects/HOSPITAL && git add backend/src/main/java/com/hms/dto/ShiftActivityDTO.java backend/src/main/java/com/hms/repository/NurseTaskRepository.java backend/src/main/java/com/hms/service/hospital/NurseDashboardService.java backend/src/main/java/com/hms/controller/hospital/NurseDashboardController.java && git commit -m "feat(nurse): add shift-activity endpoint returning completed tasks and new admissions"
```

---

### Task 4: Frontend — `hospitalService.js` — Add `getShiftActivity`

**Files:**
- Modify: `frontend/src/services/hospitalService.js`

- [ ] **Step 1: Read the file landmarks**

Open `frontend/src/services/hospitalService.js`. Find:
- `getHospitalOperationsSettings` — returns `response.data` (already includes all fields; no change needed since `shiftMode` is now in the backend response automatically)
- The end of the file — new method goes near the other nurse dashboard methods

- [ ] **Step 2: Add `getShiftActivity` to hospitalService**

Find the `getAppointmentsByPatient` method (or any nurse-related method near the end of the service object). Add the new method in the nurse dashboard section:

```js
    getShiftActivity: async (shiftStart, shiftEnd) => {
        const response = await apiClient.get('/hospital/nurse/dashboard/shift-activity', {
            params: { shiftStart, shiftEnd }
        });
        return response.data;
    },
```

- [ ] **Step 3: Build frontend to verify**

```bash
cd e:/Projects/HOSPITAL/frontend && npm run build 2>&1 | tail -5
```

Expected: `✓ built in X.XXs` — zero errors.

- [ ] **Step 4: Commit**

```bash
cd e:/Projects/HOSPITAL && git add frontend/src/services/hospitalService.js && git commit -m "feat(nurse): add getShiftActivity API call to hospitalService"
```

---

### Task 5: HospitalAdminDashboard — Shift Mode Settings Card

**Files:**
- Modify: `frontend/src/pages/hospital/HospitalAdminDashboard.jsx`

- [ ] **Step 1: Read the file landmarks**

Open `frontend/src/pages/hospital/HospitalAdminDashboard.jsx`. Find:
- The `operationsSettings` state (contains `receptionMode`, `billingHandler`, `inClinic`)
- The `handleSaveOperationsSettings` function that calls `hospitalService.updateHospitalOperationsSettings`
- The Settings tab JSX — find the last existing settings card (likely `inClinic`) — the new "Shift Mode" card goes after it

- [ ] **Step 2: Add `shiftMode` to the settings state**

Find the `operationsSettings` state initialization (look for `receptionMode` and `billingHandler`):
```js
    const [operationsSettings, setOperationsSettings] = useState({
        receptionMode: 'HAS_RECEPTIONIST',
        billingHandler: 'RECEPTIONIST',
        inClinic: true,
    });
```

Replace with:
```js
    const [operationsSettings, setOperationsSettings] = useState({
        receptionMode: 'HAS_RECEPTIONIST',
        billingHandler: 'RECEPTIONIST',
        inClinic: true,
        shiftMode: 'FIXED',
    });
```

Then find where `fetchOperationsSettings` or similar sets state from the API response — ensure `shiftMode` is read:

Find a line like:
```js
        setOperationsSettings({
            receptionMode: data.receptionMode || 'HAS_RECEPTIONIST',
            billingHandler: data.billingHandler || 'RECEPTIONIST',
            inClinic: data.inClinic ?? true,
        });
```

Replace with:
```js
        setOperationsSettings({
            receptionMode: data.receptionMode || 'HAS_RECEPTIONIST',
            billingHandler: data.billingHandler || 'RECEPTIONIST',
            inClinic: data.inClinic ?? true,
            shiftMode: data.shiftMode || 'FIXED',
        });
```

- [ ] **Step 3: Insert the Shift Mode card in the Settings tab**

Find the last existing settings card in the Settings tab JSX. It likely ends near a "Save" button or a closing `</div>` after the inClinic card. Insert the new card after it, before any global Save button or the tab's closing `</div>`:

Add this card:
```jsx
                                {/* Shift Mode Card */}
                                <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
                                    <div className="flex items-start gap-4">
                                        <div className="p-2 bg-indigo-50 rounded-xl">
                                            <svg className="w-5 h-5 text-indigo-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                                            </svg>
                                        </div>
                                        <div className="flex-1">
                                            <h4 className="text-sm font-bold text-gray-900 mb-1">Shift Mode</h4>
                                            <p className="text-xs text-gray-500 mb-4">How nurse shift windows are determined for handover summaries</p>
                                            <div className="space-y-3">
                                                <label className="flex items-start gap-3 cursor-pointer">
                                                    <input
                                                        type="radio"
                                                        name="shiftMode"
                                                        value="FIXED"
                                                        checked={operationsSettings.shiftMode === 'FIXED'}
                                                        onChange={() => setOperationsSettings(prev => ({ ...prev, shiftMode: 'FIXED' }))}
                                                        className="mt-0.5 h-4 w-4 text-indigo-600"
                                                    />
                                                    <div>
                                                        <p className="text-sm font-semibold text-gray-800">Fixed Shifts</p>
                                                        <p className="text-xs text-gray-500">Morning 07–15 · Evening 15–23 · Night 23–07 (auto-detected)</p>
                                                    </div>
                                                </label>
                                                <label className="flex items-start gap-3 cursor-pointer">
                                                    <input
                                                        type="radio"
                                                        name="shiftMode"
                                                        value="MANUAL"
                                                        checked={operationsSettings.shiftMode === 'MANUAL'}
                                                        onChange={() => setOperationsSettings(prev => ({ ...prev, shiftMode: 'MANUAL' }))}
                                                        className="mt-0.5 h-4 w-4 text-indigo-600"
                                                    />
                                                    <div>
                                                        <p className="text-sm font-semibold text-gray-800">Manual Entry</p>
                                                        <p className="text-xs text-gray-500">Nurse enters their shift start time when opening the handover panel</p>
                                                    </div>
                                                </label>
                                            </div>
                                        </div>
                                    </div>
                                </div>
```

Note: The existing settings tab likely has a single "Save" button that saves all settings at once. Since `shiftMode` is now part of `operationsSettings`, it will automatically be included when `handleSaveOperationsSettings` calls `updateHospitalOperationsSettings(operationsSettings)`. No change to the save handler is needed.

- [ ] **Step 4: Build frontend**

```bash
cd e:/Projects/HOSPITAL/frontend && npm run build 2>&1 | tail -5
```

Expected: `✓ built in X.XXs` — zero errors.

- [ ] **Step 5: Commit**

```bash
cd e:/Projects/HOSPITAL && git add frontend/src/pages/hospital/HospitalAdminDashboard.jsx && git commit -m "feat(settings): add Shift Mode radio card to hospital admin settings"
```

---

### Task 6: NurseDashboard — Handover Summary Card

**Files:**
- Modify: `frontend/src/pages/hospital/NurseDashboard.jsx`

- [ ] **Step 1: Read the file landmarks**

Open `frontend/src/pages/hospital/NurseDashboard.jsx`. Find:
- The `useState` declarations block (first ~50 lines of the component)
- The last existing `useEffect` in the file
- The overview tab JSX — find the opening of the overview content (the first `<div>` or wrapper inside `activeTab === 'overview'`)
- The existing `tasks` state and how overdue/due-soon tasks are currently categorized (look for `scheduledAt` filtering)

- [ ] **Step 2: Add four new state variables**

Find the last `useState` declaration in the component. Add after it:

```js
    const [shiftMode, setShiftMode] = useState('FIXED');
    const [manualShiftStart, setManualShiftStart] = useState('');
    const [shiftActivity, setShiftActivity] = useState(null);
    const [shiftActivityLoading, setShiftActivityLoading] = useState(false);
```

- [ ] **Step 3: Add `getShiftWindow` helper function**

After all state declarations and before the first `useEffect`, add the helper function:

```js
    const getShiftWindow = (mode, manualStart) => {
        if (mode === 'MANUAL' && manualStart) {
            const today = new Date();
            const [h, m] = manualStart.split(':').map(Number);
            const start = new Date(today.getFullYear(), today.getMonth(), today.getDate(), h, m, 0);
            const end = new Date(start.getTime() + 8 * 60 * 60 * 1000);
            const pad = n => String(n).padStart(2, '0');
            const endLabel = `${pad(end.getHours())}:${pad(end.getMinutes())}`;
            return {
                label: `Manual Shift · ${manualStart}–${endLabel}`,
                start: start.toISOString(),
                end: end.toISOString(),
            };
        }
        const hour = new Date().getHours();
        const d = new Date();
        const fmt = date => `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`;
        const today = fmt(d);
        if (hour >= 7 && hour < 15) return { label: 'Morning Shift · 07:00–15:00', start: `${today}T07:00:00`, end: `${today}T15:00:00` };
        if (hour >= 15 && hour < 23) return { label: 'Evening Shift · 15:00–23:00', start: `${today}T15:00:00`, end: `${today}T23:00:00` };
        const isAfterMidnight = hour < 7;
        const shiftDate = fmt(isAfterMidnight ? new Date(d.getTime() - 86400000) : d);
        const nextDate = fmt(isAfterMidnight ? d : new Date(d.getTime() + 86400000));
        return { label: 'Night Shift · 23:00–07:00', start: `${shiftDate}T23:00:00`, end: `${nextDate}T07:00:00` };
    };
```

- [ ] **Step 4: Add settings fetch useEffect**

After the last existing `useEffect`, add:

```js
    useEffect(() => {
        hospitalService.getHospitalOperationsSettings()
            .then(data => {
                const mode = data.shiftMode || 'FIXED';
                setShiftMode(mode);
                if (mode === 'MANUAL') {
                    const hour = new Date().getHours();
                    const defaultStart = hour >= 15 && hour < 23 ? '15:00'
                        : hour >= 7 && hour < 15 ? '07:00' : '23:00';
                    setManualShiftStart(defaultStart);
                }
            })
            .catch(() => {});
    }, []);
```

- [ ] **Step 5: Add shift activity fetch useEffect**

Immediately after the settings fetch useEffect, add:

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

- [ ] **Step 6: Add pending-task derived constants**

Find the JSX `return (` statement. Immediately before it, add:

```js
    const now = new Date();
    const overdueTasks = tasks.filter(t => t.scheduledAt && new Date(t.scheduledAt) < now);
    const dueSoonTasks = tasks.filter(t =>
        t.scheduledAt &&
        new Date(t.scheduledAt) >= now &&
        new Date(t.scheduledAt) <= new Date(now.getTime() + 60 * 60 * 1000)
    );
    const upcomingTasks = tasks.filter(t =>
        !t.scheduledAt || new Date(t.scheduledAt) > new Date(now.getTime() + 60 * 60 * 1000)
    );
    const shiftWindow = getShiftWindow(shiftMode, manualShiftStart);
```

- [ ] **Step 7: Insert the Handover Summary card in the overview tab**

Find the opening content of the overview tab (the first `<div>` or first element that renders inside the overview tab). Insert the handover card as the FIRST child:

```jsx
                        {/* Shift Handover Summary Card */}
                        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
                            <div className="flex items-center justify-between mb-4">
                                <div>
                                    <h3 className="text-base font-bold text-gray-900">Shift Handover</h3>
                                    <p className="text-xs text-gray-500 mt-0.5">{shiftWindow?.label || 'Loading shift…'}</p>
                                </div>
                                {shiftMode === 'MANUAL' && (
                                    <div className="flex items-center gap-2">
                                        <label className="text-xs text-gray-500 shrink-0">My shift started at</label>
                                        <input
                                            type="time"
                                            value={manualShiftStart}
                                            onChange={e => setManualShiftStart(e.target.value)}
                                            className="border border-gray-200 rounded-lg px-2 py-1 text-sm focus:outline-none focus:ring-2 focus:ring-blue-300"
                                        />
                                    </div>
                                )}
                            </div>
                            <div className="grid grid-cols-2 gap-6">
                                {/* Pending Now */}
                                <div>
                                    <p className="text-xs font-bold text-gray-400 uppercase tracking-wide mb-3">Pending Now</p>
                                    <div className="space-y-2">
                                        <div className="flex items-center justify-between">
                                            <span className="text-sm text-gray-600">Overdue</span>
                                            <span className={`text-sm font-bold ${overdueTasks.length > 0 ? 'text-red-600' : 'text-gray-400'}`}>
                                                {overdueTasks.length}
                                            </span>
                                        </div>
                                        <div className="flex items-center justify-between">
                                            <span className="text-sm text-gray-600">Due soon</span>
                                            <span className={`text-sm font-bold ${dueSoonTasks.length > 0 ? 'text-amber-600' : 'text-gray-400'}`}>
                                                {dueSoonTasks.length}
                                            </span>
                                        </div>
                                        <div className="flex items-center justify-between">
                                            <span className="text-sm text-gray-600">Upcoming</span>
                                            <span className="text-sm font-bold text-gray-500">{upcomingTasks.length}</span>
                                        </div>
                                    </div>
                                </div>
                                {/* This Shift's Activity */}
                                <div>
                                    <p className="text-xs font-bold text-gray-400 uppercase tracking-wide mb-3">This Shift</p>
                                    {shiftActivityLoading ? (
                                        <div className="space-y-2">
                                            <div className="h-5 bg-gray-100 rounded animate-pulse" />
                                            <div className="h-5 bg-gray-100 rounded animate-pulse" />
                                        </div>
                                    ) : (
                                        <div className="space-y-2">
                                            <div className="flex items-center justify-between">
                                                <span className="text-sm text-gray-600">Tasks completed</span>
                                                <span className="text-sm font-bold text-gray-800">
                                                    {shiftActivity != null ? shiftActivity.completedTaskCount : '—'}
                                                </span>
                                            </div>
                                            <div className="flex items-center justify-between">
                                                <span className="text-sm text-gray-600">New admissions</span>
                                                <span className="text-sm font-bold text-gray-800">
                                                    {shiftActivity != null ? shiftActivity.newAdmissionCount : '—'}
                                                </span>
                                            </div>
                                            {shiftActivity == null && (
                                                <p className="text-xs text-gray-400 mt-1">Data unavailable</p>
                                            )}
                                        </div>
                                    )}
                                </div>
                            </div>
                        </div>
```

- [ ] **Step 8: Build frontend**

```bash
cd e:/Projects/HOSPITAL/frontend && npm run build 2>&1 | tail -5
```

Expected: `✓ built in X.XXs` — zero errors. Fix any variable name mismatches before committing.

- [ ] **Step 9: Commit**

```bash
cd e:/Projects/HOSPITAL && git add frontend/src/pages/hospital/NurseDashboard.jsx && git commit -m "feat(nurse): shift handover summary card with pending tasks and shift activity"
```

---

## Self-Review

**Spec coverage:**
- ✅ `shift_mode` column in `hospital_settings` — Task 1 Step 1
- ✅ `shiftMode` in `HospitalSetting` entity — Task 1 Step 2
- ✅ `shiftMode` in `HospitalSettingDTO` — Task 1 Step 3
- ✅ `updateByHospitalId` JPQL updated — Task 1 Step 4
- ✅ `getHospitalOperationsSettings` returns `shiftMode` — Task 2 Step 2
- ✅ `updateHospitalOperationsSettings` validates and saves `shiftMode` — Task 2 Steps 3–5
- ✅ `ShiftActivityDTO` record — Task 3 Step 1
- ✅ `NurseTaskRepository.countCompletedInShift` JPQL COUNT — Task 3 Step 2
- ✅ `NurseDashboardService.getShiftActivity` — Task 3 Step 3
- ✅ `GET /hospital/nurse/dashboard/shift-activity` endpoint — Task 3 Step 4
- ✅ `hospitalService.getShiftActivity` — Task 4 Step 2
- ✅ Admin Shift Mode card with FIXED/MANUAL radio buttons — Task 5 Step 3
- ✅ `shiftMode` in `operationsSettings` state + fetch — Task 5 Steps 2
- ✅ `shiftMode` / `manualShiftStart` / `shiftActivity` / `shiftActivityLoading` states — Task 6 Step 2
- ✅ `getShiftWindow` helper (FIXED + MANUAL + night-shift cross-midnight) — Task 6 Step 3
- ✅ Settings fetch useEffect with MANUAL pre-fill default — Task 6 Step 4
- ✅ Activity fetch useEffect triggered on shiftMode/manualShiftStart — Task 6 Step 5
- ✅ `overdueTasks` / `dueSoonTasks` / `upcomingTasks` derived from existing `tasks` — Task 6 Step 6
- ✅ Handover card: shift label, MANUAL time input, Pending Now column, This Shift column — Task 6 Step 7
- ✅ Loading skeleton on right panel — Task 6 Step 7
- ✅ `—` with "Data unavailable" on API error — Task 6 Step 7
- ✅ Silent error on settings fetch (defaults to FIXED) — Task 6 Step 4

**Placeholder scan:** None found. All steps contain exact code.

**Type consistency:**
- `shiftMode` is `String` in entity/DTO/service; `string` in frontend state. ✓
- `ShiftActivityDTO(long, long)` returned by service → `{ completedTaskCount, newAdmissionCount }` read in frontend. ✓
- `getShiftWindow` returns `{ label, start, end }` where `start`/`end` are ISO strings. These are passed to `hospitalService.getShiftActivity(window.start, window.end)` as query params, and Spring parses them with `@DateTimeFormat(iso = DATE_TIME)`. ISO strings from `toISOString()` match this format. ✓
- `NurseTaskRepository.countCompletedInShift` takes `LocalDateTime` params; backend controller uses `@DateTimeFormat(iso = DATE_TIME)` for binding. ✓
- `IpdAdmissionRepository.findByHospitalIdAndAdmissionDatetimeBetween` — uses `LocalDateTime`; field name is `admissionDatetime` matching the entity. ✓
