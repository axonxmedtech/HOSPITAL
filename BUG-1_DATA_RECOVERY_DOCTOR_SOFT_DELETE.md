# Bug 1 Resolution Report: Doctor Soft Delete & Data Recovery System

## 1. Issue Summary
Deleting a doctor profile previously executed a hard delete (`DELETE FROM doctors WHERE id = ?`) in the database. This caused catastrophic cascading data loss or foreign key constraint violations across historical patient records, appointment histories, medical logs, and billing invoices linked to that doctor.

## 2. Root Cause Analysis
- **Database Layer**: Lack of a soft-delete column (`is_deleted`) on the `doctors` table resulted in permanent deletion of records.
- **Service Layer**: `DoctorService` did not check or flag active vs inactive states; records were permanently purged when requested by admin users.

## 3. Changes Implemented

### Backend Changes (`backend/src/main/java/.../doctor/`)
- Updated `Doctor` entity with `is_deleted` (boolean, default `false`), `deleted_at` (Timestamp), and `deletion_reason` (String).
- Added `DELETE /hospital/doctors/{id}?reason=...` endpoint in `DoctorController.java` to set `is_deleted = true` instead of removing the row.
- Added `POST /hospital/doctors/{id}/restore` endpoint to allow `HOSPITAL_ADMIN` users to restore deleted doctors in 1-click.
- Modified doctor query methods in `DoctorRepository.java` to automatically exclude soft-deleted doctors from active dropdown lists while preserving relations for historical queries.

### Frontend Changes (`frontend/src/pages/DoctorManagement.jsx`)
- Added a "Show Deleted / Inactive Doctors" toggle in the Doctor Management table.
- Added a "Restore" button next to deleted profiles for quick recovery.
- Added a mandatory reason modal when initiating a doctor profile deactivation/deletion.

## 4. Edge Cases Identified & Solved
- **Attempting double soft-delete**: Added checks returning `400 Bad Request` if attempting to soft-delete an already deactivated doctor.
- **Restoring an active doctor**: Added guard logic preventing redundant restore calls.
- **Historical Preserving**: Past appointments, IPD admission assignments, and billing records retain the doctor's name and ID reference without throwing missing key exceptions.
- **Access Control**: Soft-delete restoration restricted strictly to `HOSPITAL_ADMIN` role.

## 5. Empirical Verification
- Soft-deleted a doctor profile with reason "On Extended Leave". Verified active list hides the doctor while appointment history retains doctor association.
- Clicked "Restore" on the doctor profile; verified status changed back to `ACTIVE` and reappeared in new appointment allocation dropdowns.
