# Form Spec — HR Training Attendance / Learning Management (LMS)

| | |
|---|---|
| **Status** | Draft |
| **Source** | pasted form analysis — *VH/NABH/HR/01/2026* (2026-07-01) |
| **Existing code?** | **new module.** No training/LMS/attendance/employee-competency tables exist. Builds on the fragmented staff model: [`User`](../../backend/src/main/java/com/hms/entity/User.java), [`Doctor`](../../backend/src/main/java/com/hms/entity/Doctor.java), [`Nurse`](../../backend/src/main/java/com/hms/entity/Nurse.java). |

> **Prerequisite — read first (the identity gap).** This is an **HR/staff-facing** module: every row
> keys off `employee_id`. But **there is no unified employee entity today.** Staff identity is
> fragmented across three tables — `User` (login + `role` String + `hospital_id` + name/email, **no
> department, no designation**), `Doctor` (standalone, **no `user_id` link**), `Nurse` (standalone,
> **no `user_id` link**). Emails are duplicated across them. An LMS that must assign *role-based
> mandatory training* and produce *department-wise compliance* needs one canonical staff record with
> a stable id, role, and **department**. **Decision needed (see §7):** introduce an `employee`
> identity (or add `department`/`designation` + a `user_id` FK unifying Doctor/Nurse→User) before the
> LMS can be trustworthy. Until then `employee_id → users.id` with `role`-string matching is the
> `[ASSUMPTION]` interim — flagged, not assumed silently. This is the same "unify staff identity"
> gap MRD/feedback also brush against; log it once, prominently.

---

## 1. Form Overview
- **Department:** Human Resources (primary); Quality, Nursing Education, Medical Admin, Hospital Admin (secondary)
- **Module:** **HR → Training Management (Learning & Training Management System)** (new)
- **Filled By:** HR Executive (creates session) · Trainer (verifies) · Employees (mark attendance)
- **Verified By:** Trainer + Department Head
- **Reviewed By:** Quality Officer / NABH Coordinator (audit evidence)
- **Stored In:** `training_master`, `training_session`, `training_attendance`, `training_certification`
- **Lifecycle:** permanent (NABH competency evidence)
- **NABH clause:** HRM (Human Resource Management) — staff training, competency, and continuing education records.

## 2. Purpose
- **Hospital use:** evidence that staff completed mandatory training (infection control, fire safety, BMW, CPR/BLS, patient safety, hand hygiene, etc.).
- **NABH requirement:** documented training + competency per role is inspected at every audit.
- **Legal:** proof of competency limits liability when an incident involves a trained procedure.
- **Clinical/operational:** real-time workforce readiness; expired certs (CPR) surfaced before they lapse.
- **Business rationale:** first-class LMS differentiates from clinical-only HMS competitors.

## 3. Trigger
`Training planned` → HR creates `training_master` (course) → creates a `training_session` → nominates staff → **attendance recorded** (QR/badge/manual) → trainer verifies → completion recorded → optional certificate issued → employee training history updated → **NABH audit-ready**.
Also **role-driven trigger:** on staff onboarding (`User` created), the role→training matrix (§8) auto-assigns mandatory courses as *pending*.

## 4. User Roles
| Actor | Capacity | HMS role |
|---|---|---|
| HR Executive | create course/session, full manage | **MISSING → `HR_EXECUTIVE`** (gap); interim `HOSPITAL_ADMIN` |
| Trainer | conduct, mark/verify attendance | any staff user flagged trainer (`is_trainer`) — **no role today** |
| Employee | mark own attendance, view own history | `DOCTOR`/`NURSE`/`RECEPTIONIST`/`PHARMACIST`/… (any staff `User`) |
| Department Head | verify department participation | **MISSING → `DEPARTMENT_HEAD`** (gap); interim `HOSPITAL_ADMIN` |
| Quality Officer / NABH Coordinator | review compliance, audit report | `QUALITY_OFFICER` (gap from Form 03) |
| Hospital Admin | full | `HOSPITAL_ADMIN` |
| Patient | none | — |

**Role gaps:** `HR_EXECUTIVE`, `DEPARTMENT_HEAD`, trainer flag. Add to README.

## 5. Fields
**Training master**
| Field | Type | Max | Mand. | DB column | Validation | Source |
|---|---|---|---|---|---|---|
| Title | text | 150 | Y | `title` | non-empty | manual |
| Category | enum | — | Y | `category` | INFECTION_CONTROL/FIRE_SAFETY/BMW/CPR/PATIENT_SAFETY/NABH/HAND_HYGIENE/MED_SAFETY/EMERGENCY_CODES/EQUIPMENT | manual |
| Description | text | 1000 | N | `description` | — | manual |
| Mandatory | bool | — | Y | `mandatory` | — | manual |
| Validity period (months) | int | — | N | `validity_period` | ≥0 (0 = never expires) | manual |
| Target roles | multi-enum | — | N | `target_roles` | subset of role list | manual |

**Training session**
| Field | Type | Mand. | DB column | Validation |
|---|---|---|---|---|
| Training master | FK | Y | `training_master_id` | same hospital |
| Trainer | FK user | Y | `trainer_id` | staff of hospital |
| Session date | date | Y | `session_date` | not past when creating |
| Start / End time | time | Y | `start_time`/`end_time` | end > start |
| Venue | text | Y | `venue` | non-empty |
| Status | enum | Y | `status` | PLANNED/IN_PROGRESS/COMPLETED/CANCELLED |

**Attendance** (one row per employee per session)
| Field | Type | Mand. | DB column | Validation |
|---|---|---|---|---|
| Session | FK | Y | `session_id` | same hospital |
| Employee | FK | Y | `employee_id` | staff of hospital |
| Department | text | N | `department` | — (from employee once identity unified) |
| Status | enum | Y | `attendance_status` | PRESENT/ABSENT/LATE |
| Check-in / Check-out | timestamp | N | `check_in_time`/`check_out_time` | out > in, within session window |
| Signature (digital) | ref | N | `signature` | via shared signature service |
| Remarks | text | N | `remarks` | — |

## 6. Business Rules
- **BR-1** Mandatory courses auto-assigned by **role→training matrix** (§8) on staff onboarding.
- **BR-2** `IF employee not staff of this hospital THEN` cannot be added to a session (tenant).
- **BR-3** `end_time > start_time`; `check_out > check_in`; attendance timestamps within session window (or flagged LATE).
- **BR-4** Attendance is markable only while session `IN_PROGRESS` or by HR/trainer post-hoc with remark.
- **BR-5** Completion = `PRESENT` + trainer verification. Only then does it count toward compliance / trigger certification.
- **BR-6** `IF training.validity_period > 0 THEN` certification `expiry = completion_date + validity_period`; reminder at expiry −30 days (§9).
- **BR-7** Attendance, once trainer-verified, is **immutable** except HR correction with reason (audited).
- **BR-8** Compliance% per department/role = completed-mandatory / assigned-mandatory (expired = not compliant).
- **BR-9** Every query filters `hospital_id` (audit SEC rule); every `{id}` endpoint validates ownership.

## 7. Database Design
**`training_master`** · **`training_session`** · **`training_attendance`** · **`training_certification`** — all tenant-owned (`hospital_id NOT NULL, INDEX`), audit cols (`created_at/by`, `updated_at/by`), `is_deleted` soft-delete.
- `training_certification`: `id, hospital_id, employee_id, training_master_id, session_id, completed_at, expires_at, certificate_ref, status(VALID/EXPIRING/EXPIRED/REVOKED)`.
- **FK:** `training_session.training_master_id → training_master.id`; `training_attendance.session_id → training_session.id`; `*.employee_id → <staff identity>` (see below).
- **Unique:** `(session_id, employee_id)` (one attendance row per person per session); `(hospital_id, employee_id, training_master_id)` on active certification.
- **Index:** `(hospital_id, status)`, `(hospital_id, expires_at)` for expiry sweeps.

**Staff-identity decision (the §prerequisite):**
- **Preferred:** add a canonical `employee` record (or extend `User` with `department`, `designation`, `is_trainer`, and add `user_id` FK on `Doctor`/`Nurse`). `employee_id → users.id` cleanly.
- **Interim `[ASSUMPTION]`:** `employee_id → users.id`, department derived from `role`. Works but doctors/nurses whose login lives only in Doctor/Nurse tables (not `User`) would be invisible — hence the unify-identity gap must be resolved for full coverage.

## 8. Business Rules — Role→Training Matrix
Drives BR-1. Data-driven (`training_master.target_roles`), examples:
- **NURSE** → Infection Control · Medication Safety · CPR · BMW · Hand Hygiene
- **DOCTOR** → Patient Safety · Clinical Documentation · Emergency Response · NABH Awareness
- **RECEPTIONIST** → Patient Communication · Registration Workflow · Fire Safety · Privacy & Confidentiality
- **HOUSEKEEPING** *(role gap)* → BMW · Cleaning Protocols · Infection Prevention

## 9. Training Expiry (automated)
`completed → valid (validity_period) → T-30d reminder → EXPIRING → re-training required → EXPIRED`. A scheduled sweep flips status by `expires_at` and fires notifications (§11). Mirrors any existing scheduled-job pattern; expired mandatory cert = non-compliant (BR-8).

## 10. Workflow
```
PLANNED (master+session) → nominate staff → IN_PROGRESS
IN_PROGRESS → [attendance marked] → present/absent per employee
IN_PROGRESS → [trainer verifies] → COMPLETED
COMPLETED → [validity>0] → certification VALID → EXPIRING(−30d) → EXPIRED → re-train
session → CANCELLED (with reason) at any pre-complete point
```

## 11. Notifications
New training assigned · starts tomorrow · training missed · certification expiring (T-30d) · attendance pending · mandatory overdue. Reuse existing notification/event infra (WhatsApp/email + in-app).

## 12. Dashboard (HR)
Total employees · mandatory trainings · completed · pending · expired certifications · **department compliance %** · upcoming sessions · missed trainings. All `WHERE hospital_id = current`.

## 13. Reports
Employee training history · department-wise compliance · trainer-wise sessions · monthly training calendar · **NABH compliance report** · expired certifications · attendance summary. Tenant-scoped; export via report API. (Inspection-critical.)

## 14. APIs
Under `/hospital/training`; every `{id}` validates `hospital_id` ownership.
| Verb | Path | Roles | Purpose |
|---|---|---|---|
| POST/GET | `/hospital/training/masters` | HR_EXECUTIVE, HOSPITAL_ADMIN / +all(read) | course CRUD |
| POST/GET | `/hospital/training/sessions` | HR_EXECUTIVE, HOSPITAL_ADMIN, Trainer(limited) | session CRUD |
| POST | `/hospital/training/attendance` | Trainer, HR_EXECUTIVE, HOSPITAL_ADMIN | mark attendance |
| GET | `/hospital/training/attendance` | HR, Dept Head(dept), Admin | list (scoped) |
| GET | `/hospital/training/employees/{id}/history` | self / HR / Dept Head / Admin | employee training history |
| POST | `/hospital/training/sessions/{id}/verify` | Trainer, Dept Head | verify participation |
| GET | `/hospital/training/reports/compliance` | QUALITY_OFFICER, HR, HOSPITAL_ADMIN | compliance report |
| GET | `/hospital/training/attendance/{id}/checkin-qr` | Trainer | QR check-in token (digital enhancement) |

## 15. Permissions
| Role | Create | Edit | View | Approve/Verify |
|---|---|---|---|---|
| HR_EXECUTIVE *(gap→admin)* | Yes | Yes | Yes (all) | Yes |
| HOSPITAL_ADMIN | Yes | Yes | Yes (all) | Yes |
| Trainer | No | Limited (own session) | Yes | Attendance only |
| Employee | No | No | Own history only | No |
| Department Head *(gap→admin)* | No | No | Department only | Verify |
| QUALITY_OFFICER | No | No | Compliance/reports | Review |
| Patient | No | No | No | No |

Matches §14 `@PreAuthorize`.

## 16. Print Rules
Via [shared service](./shared/signature-and-document-service.md). `templates/training-attendance.html`: header (form code **VH/NABH/HR/01/2026**), session details, employee attendance table (name, dept, in/out, signature), trainer signature block, QR (session public_id). Certificate: `templates/training-certificate.html` (employee, course, date, validity, cert id + QR). Copies: HR file + optional employee.

## 17. Audit Logs
Via [`AuditLogService`](../../backend/src/main/java/com/hms/service/AuditLogService.java) (`entity_type="TRAINING_*"`): training created · attendance marked · attendance modified (old→new) · session verified · certificate issued · certification expired. Each with user, timestamp, old/new. Attendance edits especially must show prior value (BR-7).

## 18. Validation
`end_time > start_time`; `check_out > check_in`; timestamps within session window; `validity_period ≥ 0`; `attendance_status ∈ enum`; employee + trainer belong to hospital; no duplicate `(session,employee)`; category ∈ enum.

## 19. Digital Enhancements
QR check-in · badge/biometric integration · digital trainer signature · auto certificate generation · automatic expiry reminders · training calendar · online learning materials · post-training quiz + passing-score tracking. Spec the module now; wire biometric/quiz as `[Future]`.

## 20. Missing / Intelligent Features
- Auto-assign mandatory courses on onboarding (role matrix) + overdue escalation.
- Competency heat-map (which department/role is behind).
- Predictive expiry wave alert (N certs expiring next month → schedule a session now).
- Trainer load balancing; venue/slot conflict detection.
- Assessment scoring gate: certificate only if quiz passed.

---

## Module & workflow placement
- **Owning module:** HR → Training Management / LMS (new).
- **Creates:** training master/session/attendance/certification. **Updates:** attendance, cert status. **Views:** employee profile, role, department. **Prints:** attendance sheet + certificate. **Archives:** permanent HRM/NABH evidence.
- **Feeds into:** Quality/NABH compliance reporting · Dashboard · Notifications · Audit. **Fed by:** HR/Employee Management · Role Management · onboarding.
- **New modules/roles this form implies:** `HR_EXECUTIVE`, `DEPARTMENT_HEAD`, trainer flag, `HOUSEKEEPING` role (gaps) · **canonical staff/employee identity** (unify `User`/`Doctor`/`Nurse`, add `department`/`designation`) — the biggest prerequisite; add to README as a cross-cutting foundational gap.
