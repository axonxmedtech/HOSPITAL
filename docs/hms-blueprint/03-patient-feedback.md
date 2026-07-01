# Form Spec — Patient Feedback / Quality Intelligence (Patient Experience Module)

| | |
|---|---|
| **Status** | Draft |
| **Source** | pasted form analysis — *VH/NABH/GEN/03/2026* (2026-07-01) |
| **Existing code?** | **new module.** Reuses existing [`WhatsAppService`](../../backend/src/main/java/com/hms/service/whatsapp/WhatsAppService.java) for delivery, the [`/api/public`](../../backend/src/main/java/com/hms/controller/publicapi/) namespace for unauthenticated submission, and existing completion states [`Opd.Status.COMPLETED`](../../backend/src/main/java/com/hms/entity/Opd.java#L54) / IPD `DISCHARGED`. No feedback/complaint/quality table exists today. |

> **Architectural spine — read first.** Every prior form is filled by a *logged-in staff member*.
> This one is filled by the **patient, with no HMS login, via a public link** (SMS/WhatsApp/email).
> That inverts the security model: submission happens on `/api/public/**` (unauthenticated), so the
> link itself must carry a **single-use, expiring, tenant-scoped token** — the token *is* the
> authorization. Get this wrong and you have a cross-tenant spam/poisoning hole. Everything else
> (ratings, complaints, dashboard, AI) hangs off that spine. Treat this as a **Quality Intelligence
> module**, not a form: feedback in → auto-complaint → dashboard → trend/alert → corrective action.

---

## 1. Form Overview
- **Department:** Quality Department + Hospital Administration (primary); Reception, Nursing, Doctors, Housekeeping, Billing (rated secondaries)
- **Module:** **Patient Experience / Quality Intelligence** (new)
- **Filled By:** Patient (or Relative if patient cannot). **Never** editable by doctors/nurses.
- **Reviewed By:** Quality Officer (**role gap** → interim `HOSPITAL_ADMIN`)
- **Action By:** Hospital Admin / Management
- **Stored In:** `patient_feedback` (+ `quality_complaint`, `feedback_token`)
- **Lifecycle:** permanent (QMS evidence); **immutable after submission** (§18)
- **NABH clause:** PRE/CQI — patient satisfaction measurement + continuous quality improvement.

## 2. Purpose
- **Hospital use:** measure satisfaction, staff performance, service quality; drive corrective action.
- **NABH requirement:** documented patient-satisfaction feedback + analysis is mandatory for accreditation.
- **Legal:** an unaddressed documented complaint is evidence; the trail shows due diligence.
- **Clinical/service:** surfaces recurring operational issues (waiting time, cleanliness) as intelligence.
- **Business rationale:** NPS + department ratings → retention, reputation, referral-source ROI.

## 3. Trigger
`OPD consult status → COMPLETED` **OR** `IPD status → DISCHARGED` **→** system creates a `feedback_token` (single-use, expiring) **→** dispatches request via existing WhatsApp/SMS/email **→** patient opens `/feedback/<token>` **→** submits **→** ratings persisted, low scores auto-spawn `quality_complaint` **→** Quality Dashboard updates.
**Gating state:** feedback can be **created only after** a completed encounter (BR-1); never before treatment.

## 4. User Roles
| Actor | Capacity | HMS role |
|---|---|---|
| Patient | submit (via public token), view own | none — **public token auth** |
| Relative | submit on patient's behalf | none — same token, `submitted_by=RELATIVE` |
| Quality Officer | review, categorise, resolve | **MISSING → `QUALITY_OFFICER`** (gap); interim `HOSPITAL_ADMIN` |
| Hospital Admin | review, dashboard, corrective action | `HOSPITAL_ADMIN` |
| Management | analytics, trends | `HOSPITAL_ADMIN` |
| Doctor / Nurse / Reception | **no access** — cannot view or edit | — |

**Role gap:** `QUALITY_OFFICER`. Add to README.

## 5. Fields
| Field | Type | Max | Mandatory | Editable rule | DB column | Validation | Searchable | Printable | Source |
|---|---|---|---|---|---|---|---|---|---|
| Patient | FK | — | Y | read-only | `patient_id` | resolved from token | Y | Y | auto (token) |
| OPD encounter | FK | — | cond. | read-only | `appointment_id` | one of OPD/IPD set | N | N | auto |
| IPD admission | FK | — | cond. | read-only | `admission_id` | one of OPD/IPD set | N | N | auto |
| Feedback type | enum | — | Y | read-only | `feedback_type` | OPD/IPD | Y | Y | auto |
| Submitted by | enum | — | Y | patient | `submitted_by` | PATIENT/RELATIVE | N | Y | manual |
| How heard of hospital | enum | — | N | patient | `source` | DOCTOR/NEWSPAPER/RELATIVE/EXISTING_PATIENT/OTHER | Y | Y | manual |
| Overall rating | int 1–5 | — | Y | patient | `overall_rating` | 1..5 | Y | Y | manual |
| Reception rating | int 1–5 | — | N | patient | `reception_rating` | 1..5 | Y | Y | manual |
| Doctor rating | int 1–5 | — | N | patient | `doctor_rating` | 1..5 | Y | Y | manual |
| Nurse rating | int 1–5 | — | N | patient | `nurse_rating` | 1..5 | Y | Y | manual |
| Housekeeping rating | int 1–5 | — | N | patient | `housekeeping_rating` | 1..5 | Y | Y | manual |
| Billing rating | int 1–5 | — | N | patient | `billing_rating` | 1..5 | Y | Y | manual |
| Facilities available | enum | — | N | patient | `facility_rating` | YES/NO/PARTIALLY | Y | Y | manual |
| Recommend (NPS) | int 0–10 | — | N | patient | `recommend_score` | 0..10 | Y | Y | manual |
| Complaints | text | 2000 | N | patient | `complaints` | — | Y | Y | manual |
| Suggestions | text | 2000 | N | patient | `suggestions` | — | N | Y | manual |
| Status | enum | — | Y | system | `status` | PENDING/SUBMITTED/UNDER_REVIEW/ACTIONED/CLOSED | Y | Y | auto |
| Reviewed by / at | FK / ts | — | on review | Quality/Admin | `reviewed_by`,`reviewed_at` | staff of hospital | Y | Y | auto |

## 6. Business Rules
- **BR-1** `IF encounter not COMPLETED/DISCHARGED THEN` no token issued, no feedback.
- **BR-2** One completed encounter → **one primary** feedback response. Re-submit with a used/expired token is rejected.
- **BR-3** `IF token expired (>N days) OR already used THEN` submission refused (410).
- **BR-4** `IF overall_rating ≤ 2 OR any department_rating ≤ 2 OR complaints non-empty THEN` auto-create `quality_complaint` (BR feeds §12).
- **BR-5** Feedback is **immutable after submission** — no staff edit ever (§18). Only `status`, `reviewed_by/at`, complaint-resolution fields change.
- **BR-6** Doctors/nurses/reception have **zero** read on feedback (avoid retaliation/bias).
- **BR-7** Token resolves patient + hospital + encounter server-side; the public payload **never** carries `hospital_id`/`patient_id` (prevents tampering / cross-tenant injection).
- **BR-8** NPS bucket: 9–10 promoter, 7–8 passive, 0–6 detractor; `NPS = %promoters − %detractors`.
- **BR-9** All queries filter `hospital_id` (audit SEC rule), including the public read that renders the form (scoped by token).

## 7. Database Design
**`patient_feedback`** (tenant-owned): all §5 columns + `id`, `public_id`, `hospital_id` (NOT NULL, INDEX), audit cols (`created_at`, `submitted_at`, `updated_at`), `is_deleted` (soft only, but effectively never deleted).
- **Unique:** `(hospital_id, appointment_id)` and `(hospital_id, admission_id)` — enforces BR-2 (one per encounter).
- **Index:** `(hospital_id, status)`, `(hospital_id, submitted_at)` for dashboard/trend reads.

**`feedback_token`** (the auth spine):
| Column | Type | Notes |
|---|---|---|
| id / token | BIGINT / VARCHAR(64) UNIQUE | token = UUIDv4/random, unguessable |
| hospital_id | BIGINT NOT NULL, INDEX | tenant key resolved on submit |
| patient_id, appointment_id, admission_id | BIGINT | encounter binding |
| feedback_type | VARCHAR | OPD/IPD |
| expires_at | TIMESTAMP | e.g. issued +7 days |
| used_at | TIMESTAMP nullable | set on first successful submit (single-use) |
| created_at | TIMESTAMP | |

**`quality_complaint`** (auto-generated, workflow):
| Column | Type | Notes |
|---|---|---|
| id / public_id | | |
| hospital_id | BIGINT NOT NULL, INDEX | tenant |
| feedback_id | BIGINT FK | source |
| category | VARCHAR | BILLING/RECEPTION/DOCTOR/NURSING/PHARMACY/CLEANLINESS/FOOD/WAITING_TIME/OTHER (AI/manual) |
| department | VARCHAR | rated dept implicated |
| description | TEXT | copied from `complaints` |
| severity | VARCHAR | LOW/MEDIUM/HIGH (rating-derived) |
| assigned_to | BIGINT nullable | admin/quality user |
| status | VARCHAR | OPEN/INVESTIGATING/ACTION_TAKEN/CLOSED |
| resolution | TEXT nullable | corrective action |
| resolved_by/at | FK/ts | |
| audit cols | | |

- **FK:** `patient_id → patients`, `appointment_id → appointments`/OPD, `admission_id → ipd_admissions`, `hospital_id → hospitals`.

## 8. APIs
**Public (unauthenticated, token-scoped)** — under existing `/api/public`:
| Verb | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/api/public/feedback/{token}` | token | render form context (hospital name, patient first name, dept list); 410 if used/expired |
| POST | `/api/public/feedback/{token}` | token | submit; resolves hospital/patient server-side (BR-7), marks token used (BR-3) |

**Authenticated** — under `/hospital/feedback`, every `{id}` validates `hospital_id` ownership:
| Verb | Path | Roles | Purpose |
|---|---|---|---|
| GET | `/hospital/feedback` | QUALITY_OFFICER, HOSPITAL_ADMIN | list (tenant-scoped, filters) |
| GET | `/hospital/feedback/{id}` | QUALITY_OFFICER, HOSPITAL_ADMIN | detail |
| GET | `/hospital/feedback/dashboard` | QUALITY_OFFICER, HOSPITAL_ADMIN | aggregates (§11) |
| PUT | `/hospital/feedback/{id}/review` | QUALITY_OFFICER, HOSPITAL_ADMIN | mark reviewed / categorise |
| POST | `/hospital/feedback/complaints/{id}/resolve` | QUALITY_OFFICER, HOSPITAL_ADMIN | close complaint + resolution |
| GET | `/hospital/feedback/report` | HOSPITAL_ADMIN | analytics export (§14) |

Internal: token issuance is **not** a public endpoint — fired by the discharge/OPD-complete event handler (like existing WhatsApp event listener), never client-triggered.

## 9. UI Design
- **Public form (patient):** mobile-first single screen — **star tap** per department (mental model: *app-store / Uber rating*), one overall star row, NPS 0–10 slider, optional complaint/suggestion text, submit. Big touch targets, no login, no jargon, ≤7 items visible. Thank-you screen on submit; friendly 410 page if link used/expired.
- **Quality dashboard (staff):** KPI cards (avg rating, NPS, complaints open/closed), department bar chart, most-complained vs most-appreciated, trend lines, complaint queue with assign/resolve. Filters: date, OPD/IPD, doctor, ward, source.
- Read-only for everyone except review/resolve actions by Quality/Admin.

## 10. Workflow
```
encounter COMPLETED/DISCHARGED → [issue token + dispatch] → PENDING
PENDING → [patient submits via token] → SUBMITTED   (token → used, immutable)
PENDING → [token expires] → EXPIRED (no feedback)
SUBMITTED → [low rating/complaint present] → auto quality_complaint OPEN
SUBMITTED → [quality reviews] → UNDER_REVIEW → ACTIONED → CLOSED
complaint: OPEN → INVESTIGATING → ACTION_TAKEN → CLOSED
```

## 11. Quality Dashboard (aggregates)
Today's feedback · average overall rating · most-complained dept · most-appreciated dept · per-dept averages (doctor/nurse/reception/billing/housekeeping) · **NPS** (BR-8) · total/resolved/pending complaints. All computed `WHERE hospital_id = current` over date window.

## 12. Complaint Workflow
Auto (BR-4): low rating or complaint text → `quality_complaint` OPEN → assigned to Hospital Admin/Quality → INVESTIGATING → ACTION_TAKEN (resolution recorded) → CLOSED. Category from AI (§19) or manual. Ties into Notification (§13).

## 13. Notification Workflow
`rating ≤ 2 detected` → notify Quality Officer + Hospital Admin (reuse WhatsApp/event infra) → internal complaint ticket created → tracked to CLOSED. Positive feedback optionally thanked. Delivery reuses existing [`WhatsAppService`](../../backend/src/main/java/com/hms/service/whatsapp/WhatsAppService.java) + event-listener pattern.

## 14. Analytics
Monthly satisfaction · doctor-wise · department-wise · ward-wise · IPD vs OPD · complaint trends · referral-source effectiveness · repeat-patient satisfaction. All tenant-scoped; feeds `report` API + management view.

## 15. APIs summary
See §8 (public submit + staff review/dashboard/report). Public = token auth on `/api/public`; staff = `@PreAuthorize` on `/hospital/feedback`.

## 16. Permissions
| Role | Submit | View own | View all | Review | Resolve |
|---|---|---|---|---|---|
| Patient (token) | Yes | Yes (own, via token) | No | No | No |
| QUALITY_OFFICER *(gap→admin)* | No | Yes | Yes | Yes | Yes |
| HOSPITAL_ADMIN | No | Yes | Yes | Yes | Yes |
| Reception / Doctor / Nurse | No | No | No | No | No |

Matches §8 `@PreAuthorize`. Public endpoints have **no** role — token is the sole authorization.

## 17. Print Rules
Optional PDF (via [shared service](./shared/signature-and-document-service.md)) `templates/patient-feedback.html`: patient details, feedback summary, star ratings, complaint, suggestions, date, **QR code (submission public_id)**. Copy for QMS file only. No patient signature required (digital submission is the attestation; capture token + timestamp + IP instead).

## 18. Audit Logs
Every event → existing [`AuditLogService`](../../backend/src/main/java/com/hms/service/AuditLogService.java): SUBMITTED (with IP/UA from public request) → COMPLAINT_GENERATED → REVIEWED → ACTION_TAKEN → CLOSED. `entity_type="PATIENT_FEEDBACK"`/`"QUALITY_COMPLAINT"`. **Feedback content is never edited** post-submit — enforce at service layer; only status/review/resolution mutate, each logged old→new.

## 19. AI Enhancements
- **Sentiment analysis** on `complaints`/`suggestions` — per-aspect tags ("Doctor: positive, Waiting time: negative").
- **Auto-categorisation** of complaints into the §7 category enum.
- **Trend detection** — e.g. ≥N complaints on "waiting time" in a window → flag recurring operational issue.
- **Predictive dashboard** — declining department satisfaction (95→92→85%) → early alert before it hits accreditation. `[Future]` — spec the module now; wire AI later.

## 20. Missing / Intelligent Features
- Token single-use + rate-limit on public endpoint (abuse/spam guard).
- Duplicate-submission and bot-submission detection.
- Reminder nudge if link unopened after X days (reuse notification infra).
- Referral-source ROI (which `source` yields highest-satisfaction / repeat patients).
- Auto-escalation SLA: complaint OPEN > N days → escalate to Management.

---

## Module & workflow placement
- **Owning module:** Patient Experience / Quality Intelligence (new).
- **Creates:** `patient_feedback`, `feedback_token`, `quality_complaint`. **Updates:** complaint status/resolution. **Views:** patient, OPD/IPD encounter, department ratings. **Prints:** optional QMS PDF. **Archives:** permanent QMS evidence.
- **Feeds into:** Quality Management · Dashboard · Reporting · Complaint Management · Analytics. **Fed by:** OPD completion · IPD Discharge · Patient Management · Notification (WhatsApp).
- **New modules/roles this form implies:** `QUALITY_OFFICER` role (gap) · **Complaint Management** sub-module · **public tokenized-link infrastructure** (reusable for any patient-facing form — surveys, consent-at-home) — add to README.
