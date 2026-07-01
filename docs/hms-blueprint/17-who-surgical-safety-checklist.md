# Form Spec — WHO Surgical Safety Checklist / OT Safety Orchestration Engine

| | |
|---|---|
| **Status** | Draft |
| **Source** | pasted form analysis — *VH/NABH/OT/03/2026* (2026-07-01) |
| **Existing code?** | **already built as a phase-level checklist — extend it, don't replace.** [`OtChecklist`](../../backend/src/main/java/com/hms/entity/OtChecklist.java) implements the three WHO phases; [`OtService.signChecklist`](../../backend/src/main/java/com/hms/service/hospital/OtService.java#L157) **already enforces phase sequencing** (Time Out blocked before Sign In; Sign Out blocked before Time Out) and auto-advances the booking to `IN_PROGRESS` on time-out. Form 17 adds the *granular* layer + *auto-verification* + *count safety*. Consumes the Surgical Readiness Engine (Forms [15](./15-pre-anaesthesia-assessment.md)/[16](./16-surgical-consent.md)). |

> **Read first — do NOT create `ot_surgical_checklist`. The WHO checklist exists; extend `OtChecklist`.**
> `OtChecklist` (1:1 with `OtBooking`) already stores sign-in / time-out / sign-out completion + who/when/notes,
> and `signChecklist` **already implements the phase gates** the form asks for (BR-4/5/6 — *"cannot proceed
> to next phase / cannot start surgery / cannot close record"*). What it lacks, and what this form supplies:
> **(1) Granular per-item verification** — today each phase is one boolean + free-text `notes`. Add
> **`ot_checklist_items`** (phase, item, status, verified_by) so identity/consent/PAC/blood/equipment/counts
> are individually recorded, not lumped into a note.
> **(2) Auto-verification (the architect's core point)** — `setSignInCompleted(true)` currently *trusts the
> caller*. It should instead **auto-validate** identity/consent/PAC/investigations/blood from existing
> modules (= the **Surgical Readiness gate**, Forms 15/16). Only items *not electronically verifiable*
> stay as manual clinician ticks. Sign-in should be **blocked unless readiness passes**.
> **(3) Count safety** — instrument/needle/swab counts (Phase 3) with a **critical alert + block on
> patient leaving OT** (BR-7) do not exist today. New count items + sign-out guard.

---

## 1. Form Overview
- **Department:** OT (primary); Surgeon, Anaesthesia, OT Nurse, Blood Bank, CSSD, ICU, MRD (secondary)
- **Module:** **Operation Theatre → WHO Surgical Safety Checklist** (interactive 3-phase wizard, not a PDF)
- **Filled By:** OT Nurse leads; Surgeon / Anaesthetist / Scrub Nurse confirm their items
- **Reviewed By:** entire OT team (Time Out pause)
- **Archived By:** MRD
- **Lifecycle:** completed inside OT; gates incision + OT-record close; permanent after archival
- **NABH clause:** PSQ — the final pre-incision safety verification (WHO checklist).

## 2. Purpose
- **Hospital use:** last safety verification before surgery — prevents wrong patient/site/procedure/implant/blood, missing equipment/consent.
- **NABH requirement:** documented WHO three-phase checklist per surgery.
- **Legal:** timed, signed team verification is core medico-legal evidence.
- **Clinical:** the team pause + counts demonstrably reduce surgical error and mortality.
- **Business rationale:** an **active safety gate**, not passive documentation — surgery cannot proceed until every mandatory condition passes.

## 3. Trigger
`PAC complete (Form 15) → surgical consent complete (Form 16) → patient shifted to OT → **Sign In** → anaesthesia → **Time Out** → incision/surgery → intraop docs → **Sign Out** → recovery (PACU)`. Phases gated in sequence (already enforced).

## 4. User Roles
| Actor | Capacity | HMS role |
|---|---|---|
| OT Nurse (circulating) | leads + documents all phases | `NURSE` (OT) |
| Surgeon | confirm patient/procedure/site | `DOCTOR` |
| Anaesthesiologist | confirm anaesthesia readiness | `DOCTOR` (anaesthetist flag) |
| Scrub Nurse | confirm instruments/implants/counts | `NURSE` (scrub) |
| MRD | archive | `MRD_OFFICER` (gap) |

No new role gaps (scrub/circulating = `NURSE` capacities).

## 5. Three Phases → storage (items on `ot_checklist_items`)
**Phase 1 — SIGN IN (before anaesthesia):** identity (UHID/name/DOB/wristband — **auto**), correct surgery (procedure/side/site-marked), consent (General+Surgical+Blood — **auto** from Consent Engine), PAC approved + ASA (**auto** from Form 15), allergy red banner (**auto** from `PatientAllergy`), blood cross-match/units/group (Blood Bank), equipment (anaesthesia machine/O₂/suction/airway/emergency drugs — manual). **Mandatory item fail → cannot proceed.**
**Phase 2 — TIME OUT (before incision, team pause):** surgeon confirms patient/procedure/site; anaesthetist confirms stable/monitoring/antibiotics given; scrub nurse confirms sterile instruments/implants; team introductions; expected blood loss → blood+ICU ready; imaging (CT/MRI/X-ray) available. **Incision blocked until Time Out complete** *(already enforced)*.
**Phase 3 — SIGN OUT (before leaving OT):** procedure name confirmed, **instrument/needle/swab counts correct**, specimens labelled, equipment problems recorded, post-op plan + recovery instructions, disposition (ICU/Ward/PACU). **OT record cannot close until Sign Out complete** *(already enforced)*.

## 6. Database Design
**Extend `OtChecklist`** (keep the phase spine + existing sequencing): optionally add `anaesthetist_id`, `ot_nurse_id` (role attribution beyond the current `*_by` email strings), and count fields or roll counts into items.
**`ot_checklist_items`** (new — the granular layer): `id, hospital_id, checklist_id (→ ot_checklists), phase (SIGN_IN/TIME_OUT/SIGN_OUT), item, status (PASS/FAIL/NA/CONFIRMED), auto_verified (bool), verified_by, verified_at, remarks`.
**Counts** (Phase 3): items `instrument_count`/`needle_count`/`swab_count` carrying `expected` vs `found` (in remarks or dedicated cols) — mismatch drives BR-7.
- `hospital_id` on every row (tenant-scoped like `OtChecklist`); FK `checklist_id`. Index `(hospital_id, checklist_id, phase)`.

## 7. Business Rules
- **BR-1/2/3** Sign In, Time Out, Sign Out each **mandatory**.
- **BR-4** Cannot advance to next phase until previous complete — **already enforced** in `signChecklist`.
- **BR-5** Cannot start surgery until Time Out complete — **already enforced** (auto-advances booking to `IN_PROGRESS`).
- **BR-6** Cannot close OT record until Sign Out complete — **already enforced**.
- **BR-7** **Instrument/needle/swab count mismatch → critical alert; patient cannot leave OT until resolved** (new; blocks Sign Out completion).
- **BR-8** Sign In should be **blocked unless Surgical Readiness passes** (auto-verification, Read-first-2) — new precondition on the current trusting `setSignInCompleted(true)`.
- **BR-9** Every query filters `hospital_id`; every `{id}` validates ownership *(already enforced — `findByOtBookingIdAndHospitalId` + tenant check in `signChecklist`)*.

## 8. Surgical Readiness / OT Safety Orchestration Engine
The Sign-In auto-checks **are** the [Surgical Readiness gate](./15-pre-anaesthesia-assessment.md) (Forms 15 §13 + 16 §9): patient identity → consent (General/Surgical/Blood) → PAC → blood → investigations → WHO checklist → **READY FOR INCISION**. Everything electronically verifiable is auto-validated; the checklist only asks clinicians to confirm what a system cannot (physical equipment, team readiness, counts). This turns OT from passive documentation into an **active safety platform**.

## 9. APIs
Reuse existing OT endpoints (nested `/api/ipd/{admissionId}/ot/bookings/{bookingId}/checklist`), extend with items. Every `{id}` validates `hospital_id`.
| Verb | Path | Roles | Purpose |
|---|---|---|---|
| GET | `.../checklist` | OT team | current checklist + items **exists** |
| PUT | `.../checklist` (phase=SIGN_IN) | OT NURSE | sign-in (add readiness precondition, BR-8) |
| PUT | `.../checklist` (phase=TIME_OUT) | OT NURSE | time-out **exists (gated)** |
| PUT | `.../checklist` (phase=SIGN_OUT) | OT NURSE | sign-out (add count guard, BR-7) |
| POST | `.../checklist/items` | OT team | record/confirm an item |
| GET | `/hospital/ot/readiness/{bookingId}` | OT team | readiness panel (§8) |

## 10. Permissions
| Role | Sign In | Time Out | Sign Out | View |
|---|---|---|---|---|
| OT Nurse | Yes | Yes | Yes | Yes |
| Surgeon | Confirm | Confirm | Confirm | Yes |
| Anaesthesiologist | Confirm | Confirm | No | Yes |
| Scrub Nurse | Equipment | Equipment | Counts | Yes |
| MRD | No | No | No | Archived |

Matches §9 `@PreAuthorize`.

## 11. Notifications
Checklist pending → OT Nurse · consent missing → Surgeon · blood not ready → Blood Bank · **count mismatch → critical alert** · patient ready → OT dashboard. Reuse `WebSocketConfig` + `broadcast(hospitalId)` (already called by `signChecklist`).

## 12. OT Dashboard
Today's surgeries · checklist pending/complete · blood ready · PAC pending · consent pending · patient inside OT · surgery running · recovery. All `WHERE hospital_id = current`; live via existing OT `broadcast`.

## 13. Print Rules
Via [Signature & Document service](./shared/signature-and-document-service.md). `templates/who-checklist.html`: patient/surgeon/anaesthetist, procedure, all three phases with items + who/when, digital signatures, QR, completion time. Copy: file (MRD).

## 14. AI & Smart Enhancements
- **Wrong-patient detection** — compare wristband barcode vs OT schedule (`OtBooking`) vs consent (Form 16) vs UHID; mismatch → 🚨 **block surgery** + alert team.
- **Instrument-count verification** — expected vs found (needles 12 vs 11) → critical alert; block leaving OT (BR-7).
- **Surgical Readiness Score** — §8 aggregate → "100% READY FOR INCISION."
- **OT delay analytics** — track delays by cause (late consent / missing blood / pending PAC / equipment / incomplete checklist) → monthly OT efficiency reports.

## 15. Validation
Phase order enforced *(exists)*; sign-in blocked unless readiness passes (BR-8); mandatory items must be PASS/CONFIRMED to complete a phase; counts must reconcile before sign-out (BR-7); `status`/`phase` ∈ enums; server-side only.

## 16. Audit Logs
Via `AuditLogService` (`entity_type="OT_CHECKLIST"`): each phase signed (who/when) · each item verified · readiness pass/block · count mismatch critical alert · wrong-patient block — user, role, timestamp, IP. `signChecklist` already calls `audit(...)`; extend with item-level detail.

---

## Module & workflow placement
- **Owning module:** Operation Theatre → WHO Surgical Safety Checklist (OT Safety Orchestration Engine).
- **Extends:** `OtChecklist` (phase spine + gates **already built**). **Creates:** `ot_checklist_items`. **Gates:** incision (Time Out) + OT-record close (Sign Out) *(existing)* + **adds** sign-in readiness precondition + sign-out count guard. **Reads/auto-verifies:** identity (`Patient`), consent (Consent Engine, Forms 05/01/16), PAC (Form 15), `PatientAllergy`, blood (Blood Bank), investigations (`LabOrder`). **Prints:** WHO checklist. **Archives:** MRD.
- **Feeds into:** OT dashboard · incision authorization · PACU (recovery, next OT form) · MRD · OT analytics. **Fed by:** Surgery scheduling · PAC · Consent · Blood Bank · CSSD/Implant inventory (gaps).
- **New this form implies (add to README):** **`OtChecklist` is the WHO checklist and its phase gates already work** — extend, don't rebuild · **`ot_checklist_items`** granular layer · **auto-verification precondition on sign-in** (wire to Surgical Readiness Engine — the OT Safety Orchestration Engine) · **count-verification safety** (instrument/needle/swab, block sign-out on mismatch) · CSSD + Implant Inventory surface as feeder gaps.
