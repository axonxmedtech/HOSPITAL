# 06 — QA BUG TRIAGE GUIDE

How to decide a severity, with **HMS-specific** examples. Severity is about **consequence in a real
clinic**, not about how annoying the bug is or how hard it was to find.

## The four severities

| Severity     | Definition                                                                                 | Release impact                                 |
| ------------ | ------------------------------------------------------------------------------------------ | ---------------------------------------------- |
| **Critical** | patient safety, money, stock integrity, or tenant/role isolation is wrong                  | **ships nothing.** Blocks the release outright |
| **High**     | a core workflow is broken or a guard is missing, with a workaround or limited blast radius | blocks unless explicitly waived in writing     |
| **Medium**   | a feature misbehaves; the workflow completes with effort                                   | fix in the next release                        |
| **Low**      | cosmetic, wording, layout, minor inconvenience                                             | backlog                                        |

---

## CRITICAL — real examples from this product

**Patient identity / safety**

- A consultation, prescription, vitals record or document attaches to **a different family member
  sharing the phone number**.
- A PDF shows patient A's identifier over patient B's clinical content.
- A prescription is dispensed against the wrong patient's record.

**Money**

- One click produces **two** bills or **two** payment rows.
- A bill can be **deleted**, or moved from `PAID` back to `PENDING`.
- The day's collection total does not equal the sum of payments taken.
- A tenant's revenue figure **includes another tenant's** money — even with no name on screen.

**Stock**

- Stock goes **negative**, or an inward/refund inflates it twice.
- A `DRAFT` purchase moves stock.
- An **expired, blocked or disposed** batch can be sold through the API.
- A sale at Branch A changes Branch B's quantity.

**Isolation / authorization**

- Any cross-tenant **read that returns data**, or any cross-tenant **write that succeeds**.
- A client-supplied `hospitalId` lands a record in another tenant.
- A pharmacist changes branch with `X-Branch-ID` (admin-only capability).
- A revoked role or password change leaves the **old token still working**.

**Clinical state**

- A bed goes `occupied` → `available` **without** the `cleaning` step.
- Two patients occupy one bed.
- A discharged patient's record keeps accruing charges.

## HIGH — real examples

- An endpoint missing an authorization check, where the blast radius is one tenant (e.g. a
  receptionist can write a setting only an admin should write). **If it is already listed in
  `status/IMPLEMENTATION-STATUS.md`, it is not a new bug** — attach evidence to that entry.
- A **stale duplicate-phone acknowledgement** exempting a _different_ phone number.
- A nursing record saved with the **wrong performer** in mode OFF.
- Module revocation requiring a **re-login** to take effect (`@RequireModule` reads the live row, so
  it should not).
- Editing a shift template rewriting **past** schedules instead of only future ones.
- A required validation missing, allowing impossible data (future date of birth, age > 120).
- A missing `bed_status_audits` row for a real transition.
- A deactivated doctor still selectable for new appointments.

## MEDIUM — real examples

- A dashboard count that is stale until refresh but correct after it.
- A report filter that ignores a date boundary.
- An error message that does not name the field that failed.
- A tab that loads slowly or needs a second click.
- A PDF with a layout problem that does not affect identity or totals.

## LOW — real examples

- A typo, inconsistent capitalisation, or a wrong label.
- Column widths, alignment, a truncated tooltip.
- A missing empty-state illustration.

---

## Deciding, in order

1. **Could this harm a patient?** → Critical.
2. **Could this lose or invent money or stock?** → Critical.
3. **Could one tenant, branch or role reach another's data or actions?** → Critical.
4. **Is a core workflow blocked with no workaround?** → High.
5. **Is a security or validation guard missing, within one tenant?** → High.
6. **Does the workflow still complete, with effort?** → Medium.
7. **Is it only how it looks or reads?** → Low.

**When you are torn between two levels, pick the higher one and say why in the bug.** Over-reporting
costs a triage meeting; under-reporting a Critical costs a patient or a day's takings.

## Before you file — the five checks

- [ ] **Reproduced twice** from a clean state.
- [ ] **Not already a known issue** in [`../status/IMPLEMENTATION-STATUS.md`](../status/IMPLEMENTATION-STATUS.md).
- [ ] **Not the shipped-and-documented behaviour** — in particular: F5 returning to the default tab;
      two simultaneous same-phone registrations both succeeding (**Phase D has not shipped**); the
      macOS document-storage automated-test baseline.
- [ ] **Evidence attached**: ids, screenshots, the network request/response — **with the
      `Authorization` header removed**. Never paste a token, password or connection string.
- [ ] **Written with the actual ids and the actual numbers**, not "it didn't work".

## The three judgement calls people get wrong here

**1. "Reachable but unsupported" is not automatically Critical.** The ungated `/clinic/ipd`,
`/pharmacy/opd` and similar aliases are **documented drift**. Reaching one is expected and goes
against the existing entry. It becomes **Critical** only if it returns **another tenant's data**.

**2. UI-hidden is not a fix.** If a feature is hidden in the UI but the API still performs the
action, report it against the **API**, and say explicitly which layer you tested.

**3. Unclear intent is not a bug.** If nothing in the pack or the code states the rule, the verdict
is **`NEEDS_PRODUCT_CONFIRMATION`**. Write down exactly what you saw and let product decide.

## Bug title format

```
[Severity] [Area] short factual statement of what happens

Critical  Billing    Double-clicking Mark Paid records two payments on one bill
Critical  Isolation  HOSPITAL_B admin can GET HOSPITAL_A's patient by publicId
High      Nursing    Vitals saved in mode OFF record the incharge as performer
Medium    Reports    OPD report excludes cases created after 23:00
Low       Reception  "Registeration" misspelled on the patient form
```

Use [`../06-BUG-REPORT-TEMPLATE.md`](../06-BUG-REPORT-TEMPLATE.md) for the body.
