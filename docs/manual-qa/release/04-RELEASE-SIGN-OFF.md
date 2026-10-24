# 04 — RELEASE SIGN-OFF

One sheet per release. Complete it in order; it is the record that the release was tested, and by whom.

## 1. Build under test

|                                                                          |                                                    |
| ------------------------------------------------------------------------ | -------------------------------------------------- |
| Release name / version                                                   | ____________________                               |
| **Commit SHA**                                                           | ____________________                               |
| Branch                                                                   | ____________________                               |
| Environment (URL)                                                        | ____________________                               |
| Deployed at                                                              | ____________________                               |
| Modules touched by this release                                          | ____________________                               |
| Tier selected (per [`00-RELEASE-QA-POLICY.md`](00-RELEASE-QA-POLICY.md)) | `[ ] P0` `[ ] P0+P1` `[ ] P0+P1+E2E` `[ ] Full P2` |

## 2. Entry criteria

|                                                  | Met?  |
| ------------------------------------------------ | ----- |
| build deployed; `GET /api/public/health` answers | `[ ]` |
| commit SHA recorded above                        | `[ ]` |
| seed data present; six tenants exist             | `[ ]` |
| previous release's blocking bugs fixed or waived | `[ ]` |
| release notes name the modules touched           | `[ ]` |

**No entry criterion may be waived by QA.** If one is unmet, the build is not accepted and testing
does not start.

## 3. Results

| Suite            | Cases  | Pass | Fail | Blocked | N/A | Signed by | Date   |
| ---------------- | ------ | ---- | ---- | ------- | --- | --------- | ------ |
| **P0 smoke**     | **54** | ___  | ___  | ___     | ___ | ______    | ______ |
| P1 hospital      | 34     | ___  | ___  | ___     | ___ | ______    | ______ |
| P1 clinic        | 18     | ___  | ___  | ___     | ___ | ______    | ______ |
| P1 pharmacy      | 20     | ___  | ___  | ___     | ___ | ______    | ______ |
| P1 E2E gates     | 12     | ___  | ___  | ___     | ___ | ______    | ______ |
| P2 full (if run) | 1,176  | ___  | ___  | ___     | ___ | ______    | ______ |

> **P0 must read 54 / 54 Pass.** Any other figure = **RELEASE BLOCKED**, with no waiver available.

## 4. Bugs found

| Bug id | Severity                       | Area   | Summary | Blocking? | Status |
| ------ | ------------------------------ | ------ | ------- | --------- | ------ |
| ______ | Critical / High / Medium / Low | ______ | ______  | Y / N     | ______ |
| ______ |                                |        |         |           |        |
| ______ |                                |        |         |           |        |

**Open Criticals: ____** — must be **0** to ship.

## 5. Known issues re-confirmed this release

List the items from [`../status/IMPLEMENTATION-STATUS.md`](../status/IMPLEMENTATION-STATUS.md) you
observed, and **any that changed behaviour in either direction**.

| Known item                                                    | Still as documented? | Note |
| ------------------------------------------------------------- | -------------------- | ---- |
| settings endpoints without `@PreAuthorize`                    | Y / N                | ____ |
| prescription-PDF endpoints without a role check               | Y / N                | ____ |
| OPD / PHARMACY / IPD module-gate gaps                         | Y / N                | ____ |
| ungated `/clinic/**` and `/pharmacy/**` aliases               | Y / N                | ____ |
| **no DB backstop for duplicate phones (Phase D not shipped)** | Y / N                | ____ |

## 6. Waivers — release manager only

QA does not waive anything. Each waiver needs a named person and a reason.

| Case / bug id | Why it is acceptable to ship | Risk accepted | Waived by | Date   |
| ------------- | ---------------------------- | ------------- | --------- | ------ |
| ______        | ______                       | ______        | ______    | ______ |

## 7. Exit criteria

|                                                                      | Met?  |
| -------------------------------------------------------------------- | ----- |
| P0 54/54 Pass                                                        | `[ ]` |
| P1 run where the tier applies; every Fail fixed or waived in writing | `[ ]` |
| every Fail has a bug with a reproducible id and evidence             | `[ ]` |
| **no open Critical**                                                 | `[ ]` |
| execution tracker complete                                           | `[ ]` |
| this sheet complete                                                  | `[ ]` |

## 8. Decision

```
QA VERDICT:        [ ] GO      [ ] NO-GO      [ ] GO WITH WAIVERS

QA Lead              ______________________   Date ____________

Engineering Lead     ______________________   Date ____________

Product / Release Mgr______________________   Date ____________
```

**Statement signed by QA:**

> The suites named above were executed against commit `____________` on the environment named above,
> using test data only. No production data or production credentials were used. No application code,
> configuration, schema, permission or data was modified to obtain a result. Every result recorded
> is what the product actually did.
