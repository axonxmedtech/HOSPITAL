# Laboratory Workflow — Design Spec
**Date:** 2026-06-30
**Phase:** Step 9
**Status:** Approved

---

## Overview

Add a `LAB_TECHNICIAN` role and the complete laboratory workflow: doctor orders a test → lab technician collects sample → enters results → doctor views results on the patient's IPD/OPD page.

The existing `LabOrder` entity is skeletal (testName + status only). This spec extends it and adds the surrounding workflow without touching unrelated modules.

---

## Context

What already exists:
- `LabOrder` entity — thin: publicId, hospitalId, medicalRecordId, testName, status (ORDERED/COMPLETED/CANCELLED), createdAt
- `MedicalRecord` — OPD consultation record, can link to ipd_admission_id
- Full IPD admission and nurse workflow (just built)
- Billing module (auto-billing integration deferred to Step 15)

What this spec adds:
- LAB_TECHNICIAN role (user account + profile entity)
- Extended LabOrder (patient ref, IPD/OPD source, priority, sample tracking)
- LabResult entity (test parameters as JSON, summary, abnormality flag)
- Lab Tech Dashboard (pending orders → sample collection → result entry)
- Lab Orders & Results panel in IPD Details
- Hospital Admin tab for managing lab technicians

---

## Data Model

### `lab_technicians` (new table)

Mirrors the `nurses` table pattern exactly.

| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | |
| public_id | VARCHAR(36) UNIQUE | UUID, generated @PrePersist |
| custom_id | VARCHAR(10) | Sequential "LT1", "LT2", … |
| hospital_id | BIGINT NOT NULL | Tenant FK |
| name | VARCHAR(100) NOT NULL | |
| email | VARCHAR(100) NOT NULL | Matches User account email |
| phone | VARCHAR(15) | |
| is_active | BOOLEAN DEFAULT TRUE | Soft delete |
| created_at | TIMESTAMP NOT NULL | @CreationTimestamp |

### `lab_orders` (ALTER — add columns to existing table)

Current state: id, public_id, hospital_id, medical_record_id, test_name, status, created_at

Columns to ADD:

| Column | Type | Notes |
|---|---|---|
| patient_id | BIGINT NOT NULL DEFAULT 0 | Denormalized for queries (backfill = 0) |
| ipd_admission_id | BIGINT NULL | Set for IPD orders |
| opd_id | BIGINT NULL | Set for OPD orders |
| ordered_by | BIGINT NULL | Doctor's user ID |
| ordered_by_name | VARCHAR(100) NULL | Denormalized email |
| notes | TEXT NULL | Doctor's clinical notes on the order |
| priority | VARCHAR(10) DEFAULT 'ROUTINE' | ROUTINE / URGENT |
| sample_collected_at | DATETIME NULL | |
| sample_collected_by_name | VARCHAR(100) NULL | |
| updated_at | DATETIME NULL | Set on every status change |

Status lifecycle: `ORDERED` → `SAMPLE_COLLECTED` → `COMPLETED` / `CANCELLED`

`medical_record_id` stays (existing data) but becomes nullable for IPD orders.

### `lab_results` (new table)

One result per lab order (UNIQUE constraint on lab_order_id).

| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | |
| public_id | VARCHAR(36) UNIQUE | UUID, @PrePersist |
| hospital_id | BIGINT NOT NULL | Tenant FK |
| lab_order_id | BIGINT NOT NULL UNIQUE | FK → lab_orders.id |
| patient_id | BIGINT NOT NULL | Denormalized |
| parameters | TEXT | JSON array: [{name, value, unit, referenceRange, flag}] |
| result_summary | TEXT NULL | Free-text overall interpretation |
| is_abnormal | BOOLEAN DEFAULT FALSE | True if any parameter flagged |
| result_file_url | VARCHAR(500) NULL | Reserved for future PDF upload |
| resulted_by_name | VARCHAR(100) NOT NULL | Lab tech email |
| resulted_at | DATETIME NOT NULL | |
| verified_by_name | VARCHAR(100) NULL | Optional senior verification |
| created_at | TIMESTAMP NOT NULL | @CreationTimestamp |

---

## Role & Auth

### New role: `LAB_TECHNICIAN`

`User.role = "LAB_TECHNICIAN"` (plain string, same pattern as NURSE).

**SecurityConfig changes:**
- Add `LAB_TECHNICIAN` to the `/ws/**` hasAnyRole list
- Add `LAB_TECHNICIAN` to the `/hospital/**` hasAnyRole list

**Method-level `@PreAuthorize` rules:**

| Action | Allowed roles |
|---|---|
| Create lab order | DOCTOR, HOSPITAL_ADMIN |
| View lab orders | LAB_TECHNICIAN, DOCTOR, NURSE, HOSPITAL_ADMIN |
| Collect sample | LAB_TECHNICIAN, HOSPITAL_ADMIN |
| Enter result | LAB_TECHNICIAN, HOSPITAL_ADMIN |
| Cancel order | DOCTOR, HOSPITAL_ADMIN |
| Manage lab technicians (CRUD) | HOSPITAL_ADMIN |

---

## API Structure

All endpoints under `/hospital/**` (already protected).

### Lab Technician Management (Admin only)

```
POST   /hospital/lab-technicians            — create lab tech (creates User + LabTechnician)
GET    /hospital/lab-technicians            — list active lab techs (pageable)
PUT    /hospital/lab-technicians/{publicId} — update name/phone
DELETE /hospital/lab-technicians/{publicId} — deactivate (soft delete)
```

Request body for POST/PUT:
```json
{ "name": "Ravi Kumar", "email": "ravi@hospital.com", "phone": "9876543210", "password": "..." }
```

### Lab Orders

```
POST /hospital/lab/orders
  Body: { "testName", "patientId", "ipdAdmissionId"(opt), "opdId"(opt), "notes"(opt), "priority" }
  → returns created LabOrder

GET  /hospital/lab/orders
  Query: status?, ipdAdmissionId?, patientId?, page, size
  → returns Page<LabOrder with embedded LabResult if present>

GET  /hospital/lab/orders/{publicId}
  → returns single LabOrder + embedded LabResult

PUT  /hospital/lab/orders/{publicId}/collect-sample
  → status: ORDERED → SAMPLE_COLLECTED, sets sample_collected_at + by_name

POST /hospital/lab/orders/{publicId}/result
  Body: { "parameters": [...], "resultSummary", "isAbnormal", "verifiedByName"(opt) }
  → creates LabResult, sets order status to COMPLETED
  → WebSocket broadcast: {"type":"REFRESH_DATA"}

PUT  /hospital/lab/orders/{publicId}/cancel
  Body: { "reason"(opt) }
  → status: any non-COMPLETED → CANCELLED
```

---

## Service Layer

### `LabTechnicianService`
Handles CRUD for lab technician profiles. Same pattern as NurseService:
- `create(request)` — creates User(role=LAB_TECHNICIAN) + LabTechnician, generates custom ID ("LT" + sequence)
- `list(pageable)` — hospital-scoped active list
- `update(publicId, request)` — name/phone update
- `deactivate(publicId)` — soft delete isActive=false

### `LabWorkflowService`
Handles the order lifecycle:
- `placeOrder(request)` — creates LabOrder, validates hospital scope
- `getOrders(filters, pageable)` — hospital-scoped query with optional status/IPD/patient filters
- `getOrder(publicId)` — single order + result DTO
- `collectSample(publicId)` — status transition guard (must be ORDERED), sets timestamps
- `enterResult(publicId, request)` — validates order is SAMPLE_COLLECTED, creates LabResult, updates order status, broadcasts WebSocket
- `cancelOrder(publicId)` — validates not already COMPLETED, sets CANCELLED

All methods:
1. Extract hospitalId via `securityHelper.getCurrentHospitalId()` — throw UnauthorizedException if null
2. Verify the entity's hospitalId matches before any mutation
3. Write AuditLog on all writes

---

## Frontend Structure

### New files

**`frontend/src/services/labService.js`**
Axios wrappers for all lab API endpoints.

**`frontend/src/pages/hospital/LabTechnicianDashboard.jsx`**
Two-tab layout:
- **Pending Orders** — ORDERED + SAMPLE_COLLECTED orders; action buttons "Mark Sample Collected" and "Enter Result"
- **Completed** — COMPLETED + CANCELLED orders with result summary

**`frontend/src/components/lab/LabResultForm.jsx`**
Form to enter test parameters. Dynamic rows (add/remove parameter). Fields per row: Test Parameter, Value, Unit, Reference Range, Flag (Normal/Abnormal). Plus overall Summary and Is Abnormal toggle.

**`frontend/src/components/lab/LabResultsPanel.jsx`**
Read-only display of lab orders + results for a given IPD admission or patient. Used inside IpdDetails. Shows order list with status badge; expand to see parameters table.

### Modified files

**`frontend/src/App.jsx`**
- Add route `/lab-dashboard` with `allowedRoles={['LAB_TECHNICIAN']}`
- Add case `'LAB_TECHNICIAN'` in `LandingRedirect` → `/lab-dashboard`

**`frontend/src/services/authService.js`**
- Add `isLabTechnician: () => user?.role === 'LAB_TECHNICIAN'`

**`frontend/src/pages/hospital/HospitalAdminDashboard.jsx`**
- Add "Lab Technicians" tab (after Nurses tab)
- Add `LabTechniciansTable` component (same pattern as NursesTable)

**`frontend/src/pages/hospital/IpdDetails.jsx`**
- Add "Lab Orders" section after Doctor Orders section
- Doctors/Nurses can place new orders here; all roles see existing orders + results
- Reuses `LabResultsPanel`

---

## Sequence Diagram

```
Doctor (IpdDetails)
  → POST /hospital/lab/orders
    → LabOrder created (status=ORDERED)

Lab Tech (Dashboard, Pending tab)
  → sees ORDERED order
  → PUT /hospital/lab/orders/{id}/collect-sample
    → status=SAMPLE_COLLECTED

Lab Tech
  → POST /hospital/lab/orders/{id}/result
    → LabResult created
    → LabOrder status=COMPLETED
    → WebSocket broadcast → all connected clients refresh

Doctor (IpdDetails, refreshed)
  → sees COMPLETED order with parameters table
```

---

## Out of Scope (Phase 1)

- PDF / file upload for results (URL column reserved, upload deferred)
- Master lab test catalog with auto-populated reference ranges
- Lab billing auto-trigger (Step 15)
- SMS/email notification to patient
- Barcode / QR for sample tubes
- Multiple samples per order

---

## Key Invariants

1. One LabResult per LabOrder (UNIQUE on lab_order_id) — entering result twice is a 409 conflict
2. Status is a one-way machine: ORDERED → SAMPLE_COLLECTED → COMPLETED; CANCELLED is terminal from any non-COMPLETED state
3. All queries are scoped by hospitalId extracted from JWT — no cross-tenant data leakage
4. Lab tech can only modify orders belonging to their hospital (hospitalId check on every mutation)
5. `parameters` JSON is stored as TEXT (no separate table) — max ~50 parameters per order is well within TEXT limits
