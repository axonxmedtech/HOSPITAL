# 00 — E2E MASTER INDEX

**Baseline:** `origin/staging` @ `aa143a720b9cc7fa6f275792e3acbf1269c0350f` (re-fetched at Tranche 4; **0 commits, 0 files changed**).
**E2E cases:** 127 (`TC-E2E-001` … `TC-E2E-127`, no gaps, no duplicates) · **Grand total across the pack: 1,176.**

## What this tranche is, and is not

Tranches 1–3 specify **1,049 detailed cases**, screen by screen. Tranche 4 does **not** repeat them.
Every E2E journey here is an **ordered composition of existing cases** plus a small number of new
assertions that only exist _between_ the steps — continuity of identity, state, money and stock
across a handoff. Those cross-step assertions are the new `TC-E2E-*` cases.

**Read a journey like this:** the **Steps** column tells you which existing case to execute; the
**E2E assertion** is what you additionally verify _after_ that step, which no single-screen case
can see.

---

## Actual product states (verified from code — use these, never invent)

| Entity            | States                                                                                                                 | Source                |
| ----------------- | ---------------------------------------------------------------------------------------------------------------------- | --------------------- |
| **Patient**       | `REGISTERED` · `CONSULTING` · `COMPLETED`; plus `isActive` true/false (soft delete)                                    | `PatientStatus.java`  |
| **Appointment**   | `SCHEDULED` · `COMPLETED` · `CANCELLED`; plus `isActive`                                                               | `AppointmentService`  |
| **OPD**           | **`QUEUED` · `CONSULTED` · `COMPLETED` · `IN_IPD`** (+ `ipdAdmitRecommended` boolean)                                  | `Opd.Status` enum     |
| **IPD admission** | `ADMITTED` · `DISCHARGED`                                                                                              | `IpdAdmission.status` |
| **Bed**           | **lowercase** `available` · `occupied` · `cleaning` · `maintenance`                                                    | `BedStatus.java`      |
| **Billing**       | `PENDING` · `PARTIAL` · `PAID` (UI also shows `CLOSED`)                                                                | `Billing` javadoc     |
| **Surgery**       | `REQUESTED` · `APPROVED` · `SCHEDULED` · `PRE_OP` · `IN_PROGRESS` · `COMPLETED` · `CLOSED` · `CANCELLED` · `POSTPONED` | `SurgeryStatus.java`  |
| **Purchase**      | `DRAFT` · `POSTED` · `PAID`                                                                                            | `PurchaseView`        |
| **Batch**         | `ACTIVE` · `NEAR` · `CRITICAL` · `EXPIRED` · `BLOCKED` · `DISPOSED`                                                    | `ExpiryView`          |
| **Nurse task**    | `PENDING` · `COMPLETED` · `CANCELLED`                                                                                  | `MyTasksView`         |
| **Attendance**    | `PRESENT` · `ABSENT` · `LATE` · `LEAVE` · `HOLIDAY` · `Half Day`                                                       | `AttendanceView`      |

> ⚠️ A bed does **not** go straight from `occupied` to `available`. Discharge, transfer and OT
> completion all leave it `cleaning` until a Nurse Incharge marks it cleaned. Every journey table
> below records that intermediate state; do not shortcut it.

---

## Documents

| #   | Document                                                                     | Journey                                      | Cases | `TC-E2E-` range |
| --- | ---------------------------------------------------------------------------- | -------------------------------------------- | ----- | --------------- |
| 01  | [`01-COMPLETE-HOSPITAL-JOURNEY.md`](01-COMPLETE-HOSPITAL-JOURNEY.md)         | a full day in a hospital, 8 actors           | 12    | 001–012         |
| 02  | [`02-COMPLETE-CLINIC-JOURNEY.md`](02-COMPLETE-CLINIC-JOURNEY.md)             | full clinic lifecycle + hospital-only denial | 8     | 043–050         |
| 03  | [`03-COMPLETE-PHARMACY-JOURNEY.md`](03-COMPLETE-PHARMACY-JOURNEY.md)         | standalone pharmacy, all three tiers         | 8     | 051–058         |
| 04  | [`04-OPD-TO-IPD-TO-DISCHARGE.md`](04-OPD-TO-IPD-TO-DISCHARGE.md)             | the inpatient spine + 8 failure variants     | 12    | 025–036         |
| 05  | [`05-PATIENT-IDENTITY-JOURNEY.md`](05-PATIENT-IDENTITY-JOURNEY.md)           | **PI-1…PI-10 — the clinical-safety core**    | 12    | 013–024         |
| 06  | [`06-PRESCRIPTION-TO-PHARMACY.md`](06-PRESCRIPTION-TO-PHARMACY.md)           | prescriber → dispenser continuity            | 6     | 037–042         |
| 07  | [`07-BILLING-PAYMENT-JOURNEY.md`](07-BILLING-PAYMENT-JOURNEY.md)             | financial integrity, OPD/IPD/pharmacy        | 8     | 059–066         |
| 08  | [`08-OT-JOURNEY.md`](08-OT-JOURNEY.md)                                       | surgical lifecycle + NABH identity           | 5     | 073–077         |
| 09  | [`09-ICU-JOURNEY.md`](09-ICU-JOURNEY.md)                                     | ICU stay lifecycle                           | 4     | 078–081         |
| 10  | [`10-NURSING-JOURNEY.md`](10-NURSING-JOURNEY.md)                             | assignment → care → attribution              | 5     | 082–086         |
| 11  | [`11-STAFF-LIFECYCLE.md`](11-STAFF-LIFECYCLE.md)                             | hire → work → change → deactivate            | 6     | 087–092         |
| 12  | [`12-BED-WARD-LIFECYCLE.md`](12-BED-WARD-LIFECYCLE.md)                       | the four bed states end to end               | 5     | 093–097         |
| 13  | [`13-CLINICAL-DOCUMENT-LIFECYCLE.md`](13-CLINICAL-DOCUMENT-LIFECYCLE.md)     | upload → view → print → archive              | 4     | 098–101         |
| 14  | [`14-PHARMACY-STOCK-LIFECYCLE.md`](14-PHARMACY-STOCK-LIFECYCLE.md)           | **numeric stock fixture**                    | 6     | 067–072         |
| 15  | [`15-MULTI-BRANCH-PHARMACY-JOURNEY.md`](15-MULTI-BRANCH-PHARMACY-JOURNEY.md) | branch isolation                             | 5     | 102–106         |
| 16  | [`16-TENANT-ISOLATION-E2E.md`](16-TENANT-ISOLATION-E2E.md)                   | 6 tenants, 3 tenant types                    | 6     | 107–112         |
| 17  | [`17-MODULE-REVOCATION-E2E.md`](17-MODULE-REVOCATION-E2E.md)                 | live entitlement change                      | 5     | 113–117         |
| 18  | [`18-FAILURE-RECOVERY-JOURNEYS.md`](18-FAILURE-RECOVERY-JOURNEYS.md)         | "what if it fails halfway?"                  | 10    | 118–127         |

> **Numbering note.** The `TC-E2E-*` numbers are allocated by _journey_, not by file order, so the
> document numbers and the case ranges deliberately do not run in parallel (document 14 holds
> 067–072, document 08 holds 073–077). The sequence `001`–`127` is complete with no gaps and no
> duplicates — verified mechanically.

## Standard session map (used by every journey)

| Label                                                | Context                           | Account                            |
| ---------------------------------------------------- | --------------------------------- | ---------------------------------- |
| S-SA                                                 | **incognito window**              | Super Admin at `/platform/login`   |
| S-ADM · S-REC · S-DOC · S-NUR · S-NI · S-PHA · S-OTI | Chrome tabs 1–7                   | HOSPITAL_A staff                   |
| S-CADM · S-CREC · S-CDOC · S-CPHA                    | Chrome tabs 8–11                  | CLINIC_A staff                     |
| S-PADM · S-PPHA · S-SOLO · S-MADM · S-BA · S-BB      | Chrome tabs 12–17                 | pharmacy tenants and branches      |
| S-B                                                  | **second browser** (Firefox/Edge) | HOSPITAL_B / CLINIC_B / PHARMACY_B |

Tokens live in `sessionStorage`, so tabs are independent sessions (`TC-AUTH-013`). Always use a
**separate browser** for the "other tenant" so an accidental logout cannot contaminate an
isolation journey.

## Evidence rule

Every journey carries a **state table**. Fill the _Actual_ column as you go and attach the named
evidence. A journey with an empty state table is **not executed**, whatever the tick boxes say.

## Standing limitation — repeat in every report

**Phase A duplicate-phone protection has no database backstop yet (Phase D not shipped).** Journeys
test the _workflow_: chooser appears, exact patient is selectable, records attach correctly.
**Never assert that two simultaneous submissions on one phone number cannot both succeed.**
