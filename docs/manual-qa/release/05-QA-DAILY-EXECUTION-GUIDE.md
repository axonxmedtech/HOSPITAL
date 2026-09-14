# 05 — QA DAILY EXECUTION GUIDE

For a tester on their first day. Read this once, then work from the suite documents.

## Before you touch anything

1. You test on the **QA environment only**. **Never production, never production data, never
   production credentials.** If someone hands you a production login, decline and tell your lead.
2. You **never change the product** to make a test pass — not code, not configuration, not schema,
   not permissions, not a database row. If you cannot proceed, the result is **BLOCKED**, which is a
   perfectly good result and often the most useful one.
3. You record **what the product did**, not what you expected it to do.

## Your morning setup (≈15 minutes)

- [ ] Open [`../01-TEST-ENVIRONMENT-SETUP.md`](../01-TEST-ENVIRONMENT-SETUP.md) and confirm the app loads and `GET /api/public/health` answers.
- [ ] Confirm the seed data from [`../02-TEST-DATA-SETUP.md`](../02-TEST-DATA-SETUP.md) exists (six tenants).
- [ ] Write today's **build SHA** at the top of your tracker row block.
- [ ] Open the tabs from the standard session map in [`../e2e/00-E2E-MASTER-INDEX.md`](../e2e/00-E2E-MASTER-INDEX.md).
- [ ] Open a **second browser** (not a second tab) for the "other tenant". Tokens live in
      `sessionStorage`, so tabs are separate sessions — but an isolation test deserves a separate browser.
- [ ] Open DevTools ▸ Network, "Preserve log" on. You will need it for evidence and for the failure cases.

## Executing one test case

1. **Read the whole case first**, including the assertion. Do not start clicking.
2. **Set up the preconditions.** If a precondition is missing, the result is BLOCKED — do not
   improvise data to get past it.
3. **Do the steps in order, exactly.**
4. **Observe before you judge.** Read the screen, the status, the counter, the network response.
5. **Record the result:**

| Result      | When to use it                                                                   |
| ----------- | -------------------------------------------------------------------------------- |
| **PASS**    | the product did exactly what the assertion says                                  |
| **FAIL**    | it did something else — raise a bug                                              |
| **BLOCKED** | you could not run it (missing precondition, environment down, dependency failed) |
| **N/A**     | it does not apply to this tenant/tier/mode — say why                             |

6. **Attach evidence.** A screenshot of the state, the PDF, the network response. **A case with no
   evidence is not executed**, whatever the tick box says.
7. **Fill the state table** as you go, not at the end from memory.

## Rules that trip up new testers

- **F5 returning you to the default tab is expected, not a bug.** The UI is tab-state based; only
  `/ipd/:id` is deep-linkable.
- **Bed states are lowercase** — `available`, `occupied`, `cleaning`, `maintenance` — and a bed
  **never** goes straight from `occupied` to `available`. `cleaning` in between is correct.
- **OPD statuses are `QUEUED`, `CONSULTED`, `COMPLETED`, `IN_IPD`.** There is no "WAITING".
- **Two simultaneous registrations with the same phone may both succeed.** The database backstop has
  not shipped. **Do not file it.**
- **Known issues are not new bugs.** Check [`../status/IMPLEMENTATION-STATUS.md`](../status/IMPLEMENTATION-STATUS.md)
  before filing anything; attach your evidence to the existing entry instead.
- **UI-hidden is not the same as API-refused.** Where a case says to check the API, check it — a
  feature hidden in the UI but reachable by request is a real finding.
- If the intent is genuinely unclear, the verdict is **`NEEDS_PRODUCT_CONFIRMATION`**. Never guess in
  either direction, and never call something a bug because it surprised you.

## When you find something that looks wrong

1. **Try to reproduce it.** Twice, from a clean state. An unreproducible report wastes everyone's day.
2. **Note the exact time, the user, the tenant, and the record id.**
3. **Save the network request and response** (right-click ▸ Copy ▸ as cURL) — but **remove the
   `Authorization` header before pasting it anywhere**. Never put a token, a password or a
   connection string in a bug report.
4. File it with [`../06-BUG-REPORT-TEMPLATE.md`](../06-BUG-REPORT-TEMPLATE.md) and triage it using
   [`06-QA-BUG-TRIAGE-GUIDE.md`](06-QA-BUG-TRIAGE-GUIDE.md).
5. **Then carry on.** Do not stop the whole suite for one bug unless it blocks the rest of the run —
   say so in the tracker if it does.

## End of day (≈15 minutes)

- [ ] Every case you touched has a result and a date in [`../07-EXECUTION-TRACKER.md`](../07-EXECUTION-TRACKER.md).
- [ ] Every FAIL has a bug id.
- [ ] Every BLOCKED says **what** blocked it and **who** can unblock it.
- [ ] Evidence files are named for the case (`TC-HR-005-chooser.png`) and stored where your lead expects them.
- [ ] Post a one-line status: **cases run / pass / fail / blocked**, and anything that will block tomorrow.

## A realistic daily rate

| Work                                      | Cases/day                                            |
| ----------------------------------------- | ---------------------------------------------------- |
| straightforward single-screen cases       | 40–60                                                |
| negative / validation cases               | 30–40                                                |
| isolation and matrix cases (two browsers) | 25–35                                                |
| **E2E journeys**                          | **6–12** — they are long by design; do not rush them |

If you are moving much faster than this, you are probably not filling in the state tables.
