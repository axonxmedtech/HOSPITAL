# 00 — RELEASE QA POLICY

**Baseline:** `origin/staging` @ `aa143a7` · **No new Test Case IDs in this folder.** Every document
here _selects_ cases defined in Tranches 1–4 and records their result for a release.

## The three tiers

| Tier                     | Cases                      | When it runs                                                        | Time                                        | Blocking?                                                     |
| ------------------------ | -------------------------- | ------------------------------------------------------------------- | ------------------------------------------- | ------------------------------------------------------------- |
| **P0 — Critical smoke**  | **54**                     | **Every** staging→production release                                | ~4 h, 1 tester (or ~90 min × 3 in parallel) | **Yes — a P0 failure blocks the release. It is not triaged.** |
| **P1 — Major workflow**  | **72** + 12 E2E gates      | Every release touching hospital / clinic / pharmacy behaviour       | ~1.5 days                                   | Blocking unless the release manager records a written waiver  |
| **P2 — Full regression** | **1,176** (the whole pack) | Major releases, quarterly, and before any schema or security change | ~3 weeks, 1 tester                          | Planned, not blocking                                         |

**P0 is deliberately unchanged at 54** — Hospital 24 + Clinic 14 + Pharmacy 16, exactly as
Tranches 2 and 3 defined it. Tranche 4 did **not** inflate it. The end-to-end reconciliation gates
are placed in **P1** instead; promoting any of them to P0 is a **product decision for the release
manager**, listed with a recommendation in [`01-P0-SMOKE-SUITE.md`](01-P0-SMOKE-SUITE.md).

## Non-negotiable rules

1. **Never test against production, and never use production data or production credentials.** Every
   suite runs on a dedicated QA environment seeded by [`../02-TEST-DATA-SETUP.md`](../02-TEST-DATA-SETUP.md).
2. **Never "fix" data by hand to make a case pass.** If a case cannot proceed because of data, it is
   **BLOCKED**, and that is a result worth reporting.
3. **Never modify application code, configuration, schema or permissions to make a test pass.** QA
   reports; engineering fixes.
4. **A case with no evidence is not executed**, whatever the tick box says. Evidence = the filled
   state table plus the named screenshot/PDF.
5. **Record what the product does, not what you expected.** Where the intent is genuinely unclear,
   the verdict is `NEEDS_PRODUCT_CONFIRMATION` — never a guess in either direction.
6. **Known issues are not new bugs.** Everything in [`../status/IMPLEMENTATION-STATUS.md`](../status/IMPLEMENTATION-STATUS.md)
   is already logged; attach your evidence to the existing entry. File a new bug only for a
   **deviation from what is documented there**.

## Standing limitation, restated in every release report

**Phase A duplicate-phone protection has no database backstop (Phase D has not shipped.)** The
suites test the _workflow_. **Do not assert that two simultaneous registrations on one phone
number cannot both succeed, and do not file it as a bug.**

## Entry criteria — a build is accepted into QA only if

- [ ] the build is deployed to the QA environment and `GET /api/public/health` answers;
- [ ] the commit SHA under test is recorded on the sign-off sheet;
- [ ] the seed data from `../02-TEST-DATA-SETUP.md` is present and the six tenants exist;
- [ ] the previous release's **blocking** bugs are either fixed or explicitly waived;
- [ ] the release notes name the modules touched, so the right tier can be chosen.

## Exit criteria — a build leaves QA only if

- [ ] **P0: 54/54 PASS.** No exceptions, no waivers.
- [ ] P1 run in full where the tier applies, with every FAIL either fixed or waived **in writing** by the release manager.
- [ ] Every FAIL has a bug raised using [`../06-BUG-REPORT-TEMPLATE.md`](../06-BUG-REPORT-TEMPLATE.md) with a reproducible id and evidence.
- [ ] No **Critical** bug is open. A Critical is, by definition, ship-blocking (see [`06-QA-BUG-TRIAGE-GUIDE.md`](06-QA-BUG-TRIAGE-GUIDE.md)).
- [ ] The sign-off sheet in [`04-RELEASE-SIGN-OFF.md`](04-RELEASE-SIGN-OFF.md) is complete and signed.

## Choosing the tier

| The release touches…                                           | Run                                     |
| -------------------------------------------------------------- | --------------------------------------- |
| copy, styling, a single isolated screen                        | **P0**                                  |
| any hospital / clinic / pharmacy workflow                      | **P0 + P1**                             |
| money, stock, beds, identity, auth, tenancy                    | **P0 + P1 + the matching E2E journeys** |
| schema, migrations, security, entitlements, or a major version | **P0 + P1 + P2 (full)**                 |

When in doubt, run the larger tier. Under-testing a money or identity change is the one mistake
this policy exists to prevent.
