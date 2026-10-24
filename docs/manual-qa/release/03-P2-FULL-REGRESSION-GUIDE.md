# 03 — P2 FULL REGRESSION GUIDE (1,176)

The whole pack, executed once. Run it before a major release, quarterly, and **always** after a
schema, security, entitlement or multi-tenancy change.

## What "the whole pack" is

| Tranche | Area                                                 | Cases     |
| ------- | ---------------------------------------------------- | --------- |
| 1       | foundation, matrices, Super Admin, API, cross-tenant | **265**   |
| 2       | hospital                                             | **464**   |
| 3       | clinic (134) + standalone pharmacy (186)             | **320**   |
| 4       | end-to-end journeys                                  | **127**   |
|         | **Total**                                            | **1,176** |

## A realistic schedule — 15 working days, one tester

| Day   | Work                                                    | Cases | Notes                                                |
| ----- | ------------------------------------------------------- | ----- | ---------------------------------------------------- |
| 1     | environment + seed data + `03-GLOBAL-AUTH-AND-SECURITY` | ~30   | nothing else can run until the six tenants exist     |
| 2     | `04-SUPER-ADMIN`                                        | 62    | do this before tenant work — it creates the plans    |
| 3     | `cross-tenant/*` matrices                               | 163   | isolation, roles, modules, visibility, special modes |
| 4     | `05-API-TEST-TRACK`                                     | 12    | keep the collection; you will reuse it all fortnight |
| 5–6   | hospital admin (setup, clinical, settings)              | 116   |                                                      |
| 7     | hospital reception                                      | 58    |                                                      |
| 8     | hospital doctor                                         | 48    |                                                      |
| 9     | hospital nursing (nurse + incharge)                     | 72    | **run both nurse-login modes**                       |
| 10    | hospital pharmacy + billing                             | 70    |                                                      |
| 11    | hospital OT + ICU + wards/beds + cross-role             | 100   |                                                      |
| 12    | clinic                                                  | 134   |                                                      |
| 13–14 | standalone pharmacy, all three tiers                    | 186   |                                                      |
| 15    | **E2E journeys**                                        | 127   | last, deliberately — they need a populated system    |

Three testers working in parallel on hospital / clinic / pharmacy finish in **5–6 days**.

## Order rules that actually matter

1. **Super Admin first.** Tenants, plans and modules must exist before anything else.
2. **Admin setup before role testing.** Doctors, wards, beds, medicines and fees are prerequisites,
   not optional extras — this is why the pack forbids testing admin CRUD in isolation.
3. **E2E last.** Journeys assume a populated, realistic system.
4. **Both nurse-login modes.** OFF and ON are different products; a single pass covers neither.
5. **All three pharmacy tiers.** SOLO, SINGLE, MULTI.
6. **Isolation cases need two browsers** — the second tenant in a different browser, never a second tab.

## Coverage accounting — nothing may silently disappear

Every implemented tab ends in exactly one verdict:

**`TESTED` · `PARTIAL` · `PLACEHOLDER` · `DEAD_UNREACHABLE` · `NEEDS_PRODUCT_CONFIRMATION`**

At the end of a P2 run, reconcile the verdicts against [`../status/IMPLEMENTATION-STATUS.md`](../status/IMPLEMENTATION-STATUS.md)
and record **every difference in either direction** — a `PARTIAL` that now works is as important to
report as one that has regressed.

## Known-issue handling — read this before filing anything

These are **documented, accepted, open** items. Confirm them and attach evidence to the existing
entry; do **not** open new bugs, and do **not** fail the run for them.

| Known item                                                                                                                                                                                    | Expected observation                                                                                       |
| --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| 10 `HospitalAuthController` settings endpoints have **no `@PreAuthorize`**                                                                                                                    | any of the 7 tenant roles can read/write fees, operations, barcode, nurse-login, OT-incharge, subscription |
| 2 `DoctorController` prescription-PDF endpoints have **no role check**                                                                                                                        | any same-tenant authenticated role can fetch them; **cross-tenant must still fail**                        |
| Notification bell on the Nurse Incharge dashboard                                                                                                                                             | the API is `NURSE`-only, so it does not populate                                                           |
| **OPD and PHARMACY have no `@RequireModule`**; IPD has exactly one                                                                                                                            | revoking them hides the UI but the API still answers                                                       |
| ungated aliases `/clinic/ipd` · `/clinic/wards` · `/clinic/beds` · `/pharmacy/opd` · `/pharmacy/ipd` · `/pharmacy/beds` · `/pharmacy/wards` · `/pharmacy/doctors` · `/pharmacy/receptionists` | reachable but unsupported                                                                                  |
| `DatabaseMigrationRunner.addIndexIfMissing` swallows failures                                                                                                                                 | not user-visible; engineering-side, HIGH                                                                   |
| **no DB uniqueness backstop for duplicate phones (Phase D not shipped)**                                                                                                                      | two simultaneous registrations may both succeed — **do not assert otherwise**                              |
| macOS document-storage test baseline (`LocalVpsClinicalDocumentStorageTest`, `PatientDocumentApiTest`, `PatientDocumentJourneyTest`)                                                          | an automated-test environment issue; **the manual document cases still run**                               |

**A change in any of these, in either direction, is worth reporting** — including one that now
behaves _better_ than documented, because the documentation then needs updating.

## Deliverables at the end of a P2 run

- [ ] [`../07-EXECUTION-TRACKER.md`](../07-EXECUTION-TRACKER.md) complete — every case has a result and a date.
- [ ] Every FAIL has a bug with a reproducible id and evidence.
- [ ] `../status/IMPLEMENTATION-STATUS.md` reconciled, with every verdict change listed.
- [ ] The 35-point coverage reconciliation re-run.
- [ ] [`04-RELEASE-SIGN-OFF.md`](04-RELEASE-SIGN-OFF.md) completed and signed.
