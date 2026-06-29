# Nurse IPD Workflow — Design Spec
**Date:** 2026-06-30
**Phase:** 1 (Layers 1 + 2)
**Status:** Approved

---

## Overview

Add a `NURSE` role and the clinical IPD workflow that sits between patient admission and discharge. This covers two layers:

- **Layer 1** — Nurse dashboard, patient list, initial assessment form, vitals recording
- **Layer 2** — Doctor orders panel, nurse task list (execute orders)

Layer 3 (clinical charts — Sugar Chart, I&O Chart, vitals trend graph) is explicitly out of scope for this phase.

---

## Context

The system already has: patient registration, appointments, OPD, IPD admission, bed/ward allocation, billing, pharmacy. Once a patient is admitted to IPD, there is currently no clinical workflow — no nurse assessment, no doctor orders, no task execution. This spec fills that gap.

---

## Data Model

### `Nurse`
Profile entity, one per nurse user. Mirrors the existing `Doctor` / `Receptionist` pattern.

| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | |
| public_id | VARCHAR | UUID, exposed in API |
| user_id | BIGINT FK | → users.id |
| hospital_id | BIGINT FK | → hospitals.id |
| name | VARCHAR | |
| phone | VARCHAR | |
| created_at | TIMESTAMP | |

### `NurseWardAssignment`
Optional. A nurse with no assignments sees all IPD patients. A nurse with one or more assignments sees only patients in those wards.

| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | |
| nurse_id | BIGINT FK | |
| ward_id | BIGINT FK | |

### `NurseAssessment`
One per IPD admission. Filled by nurse on patient arrival at ward.

| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | |
| ipd_admission_id | BIGINT FK | UNIQUE (one per admission) |
| hospital_id | BIGINT FK | |
| blood_pressure | VARCHAR | e.g. "120/80" |
| pulse | INT | bpm |
| temperature | DECIMAL(4,1) | °F |
| spo2 | INT | % |
| height | DECIMAL(5,1) | cm |
| weight | DECIMAL(5,1) | kg |
| pain_score | INT | 0–10 |
| allergies | TEXT | |
| fall_risk | ENUM | LOW / MEDIUM / HIGH |
| general_condition | TEXT | |
| chief_complaint_on_admission | TEXT | |
| assessed_by | BIGINT FK | → nurses.id |
| assessed_at | TIMESTAMP | |

### `VitalSigns`
Many per IPD admission. Time-series vitals recorded throughout the stay.

| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | |
| ipd_admission_id | BIGINT FK | |
| hospital_id | BIGINT FK | |
| blood_pressure | VARCHAR | |
| pulse | INT | |
| temperature | DECIMAL(4,1) | |
| spo2 | INT | |
| recorded_by | BIGINT FK | → nurses.id |
| recorded_at | TIMESTAMP | |

### `DoctorOrder`
Created by doctor on an IPD patient. Drives the nurse task list.

| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | |
| public_id | VARCHAR | |
| ipd_admission_id | BIGINT FK | |
| hospital_id | BIGINT FK | |
| order_type | ENUM | MEDICATION / INVESTIGATION / PROCEDURE / DIET |
| description | TEXT | e.g. "Ceftriaxone 1g IV" |
| frequency | VARCHAR | e.g. "BD", "TDS", "SOS", "Once" |
| start_date | DATE | |
| end_date | DATE | nullable |
| status | ENUM | ACTIVE / COMPLETED / CANCELLED |
| created_by | BIGINT FK | → doctors.id |
| created_at | TIMESTAMP | |
| notes | TEXT | nullable |

### `NurseTask`
One execution record per scheduled instance of an order. Generated when an order is created (one task per frequency slot per day) or on-demand for SOS orders.

| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | |
| doctor_order_id | BIGINT FK | |
| ipd_admission_id | BIGINT FK | denormalized for fast queries |
| hospital_id | BIGINT FK | |
| scheduled_at | TIMESTAMP | when this instance should be given |
| executed_at | TIMESTAMP | nullable |
| executed_by | BIGINT FK | → nurses.id, nullable |
| status | ENUM | PENDING / DONE / SKIPPED |
| notes | TEXT | nullable |

---

## Backend API

All endpoints are under `/hospital/**` and require a valid JWT with the correct hospital context.

### Nurse Management — `/hospital/nurses`
| Method | Path | Roles | Purpose |
|---|---|---|---|
| POST | `/` | ADMIN | Add nurse (creates User + Nurse) |
| GET | `/` | ADMIN | List nurses, searchable |
| PUT | `/{id}` | ADMIN | Update nurse |
| DELETE | `/{id}` | ADMIN | Remove nurse |
| POST | `/{id}/assign-ward` | ADMIN | Assign/unassign ward |

### IPD Assessment — `/hospital/ipd/{admissionId}/assessment`
| Method | Path | Roles | Purpose |
|---|---|---|---|
| POST | `/` | NURSE | Create initial assessment (once per admission) |
| GET | `/` | NURSE, DOCTOR, ADMIN | Get assessment |

### IPD Vitals — `/hospital/ipd/{admissionId}/vitals`
| Method | Path | Roles | Purpose |
|---|---|---|---|
| POST | `/` | NURSE | Record new vitals entry |
| GET | `/` | NURSE, DOCTOR, ADMIN | All vitals, ordered by time desc |

### Doctor Orders — `/hospital/ipd/{admissionId}/orders`
| Method | Path | Roles | Purpose |
|---|---|---|---|
| POST | `/` | DOCTOR, ADMIN | Create order |
| GET | `/` | DOCTOR, NURSE, ADMIN | List all orders |
| PUT | `/{orderId}` | DOCTOR, ADMIN | Edit or cancel order |

### Nurse Tasks — `/hospital/ipd/{admissionId}/tasks`
| Method | Path | Roles | Purpose |
|---|---|---|---|
| GET | `/` | NURSE, DOCTOR | All tasks for this admission |
| GET | `/pending` | NURSE | Pending tasks only |
| PUT | `/{taskId}/execute` | NURSE | Mark done or skipped |

### Nurse Dashboard — `/hospital/nurse/dashboard`
| Method | Path | Roles | Purpose |
|---|---|---|---|
| GET | `/patients` | NURSE | All admitted IPD patients (ward-filtered if assigned) |
| GET | `/my-tasks` | NURSE | All pending tasks across all patients |

---

## Frontend

### New Pages

**`NurseDashboard.jsx`** — route `/nurse-dashboard`, NURSE role only.

Two tabs:
1. **My Patients** — Table/cards of all admitted IPD patients. Columns: name, UHID, ward, bed, admission date, pending tasks badge. Ward filter dropdown. Row click → `PatientClinicalRecord`.
2. **My Tasks** — Global task list across all patients. Grouped by patient. Columns: patient name, order description, type badge, scheduled time, status. "Done" and "Skip" buttons inline. Overdue tasks (past scheduled time, still PENDING) shown in red.

**`PatientClinicalRecord.jsx`** — opened from My Patients row. Tabbed:
1. **Assessment** — Initial assessment form if not yet done; read-only summary if done.
2. **Vitals** — "Record Vitals" button (opens modal form). Chronological list of all recorded vitals below.
3. **Orders & Tasks** — Read-only list of active doctor orders and their task execution status for today.

### Modified Pages

**IPD Admission Detail** (existing, used by Doctor/Admin) — add new **Orders** tab:
- Doctor can create orders (form: type, description, frequency, start/end date, notes)
- List of existing orders with status badges
- Cancel button on active orders
- Read-only Assessment and Vitals tabs visible to doctor

**`HospitalAdminDashboard.jsx`** — add **Nurses** tab:
- Same pattern as Doctors/Receptionists tabs
- DataTable with Add/Edit/Delete
- Add form: name, phone, email, password, optional ward assignment

### Routing & Auth

- `/nurse-dashboard` added to `App.jsx`, wrapped in `ProtectedRoute` for NURSE
- `authService.js` post-login redirect: `case 'NURSE': return '/nurse-dashboard'`
- `Sidebar.jsx` NURSE role: shows Dashboard, My Patients, My Tasks only
- `ProtectedRoute` updated to accept NURSE role

---

## Role Changes

| Area | Change |
|---|---|
| `UserRole` enum | Add `NURSE` |
| `SecurityConfig` | Permit NURSE on assessment, vitals, tasks (read+write), orders (read only) |
| Admin UI | New Nurses tab for adding/managing nurse accounts |
| JWT | No changes — role already encoded in token |
| Login flow | NURSE redirects to `/nurse-dashboard` |

---

## Out of Scope (Phase 2)

- Sugar chart, Intake/Output chart, vitals trend graph
- ICU-specific nurse workflow
- Shift management / nurse scheduling
- Medication administration record (MAR) with full audit trail
- Automatic task generation on order creation (Phase 1 uses manual task creation or simple frequency parsing)

---

## Success Criteria

1. Admin can add a nurse, assign her to a ward, and she can log in
2. Nurse sees all IPD patients (filtered by ward if assigned)
3. Nurse can fill the Initial Assessment form for a newly admitted patient
4. Nurse can record vitals at any time; each entry is time-stamped
5. Doctor can open an IPD patient and write medication/investigation orders
6. Orders appear as tasks in the nurse's task list
7. Nurse can mark tasks done or skipped with optional notes
8. Doctor can see which tasks have been executed and when
