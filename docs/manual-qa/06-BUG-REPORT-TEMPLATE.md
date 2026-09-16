# 06 — BUG REPORT TEMPLATE

Copy the block below into the tracker for every defect. **Never include a real JWT, password,
connection string or production data.** Redact tokens as `Bearer <redacted>`.

Before filing, check [`status/IMPLEMENTATION-STATUS.md`](status/IMPLEMENTATION-STATUS.md) — if
the behaviour is listed there as `PLACEHOLDER`, `DEAD_UNREACHABLE`, `BACKEND_WITHOUT_UI` or
`NEEDS_PRODUCT_CONFIRMATION`, reference that row instead of filing a new bug.

```
Bug ID              : BUG-____
Title               : <one line: role + action + wrong outcome>
Environment         : QA local  |  build SHA: aa143a7  |  backend :8080  |  frontend :5173
Tenant              : HOSPITAL_A / HOSPITAL_B / CLINIC_A / ... (name + numeric id)
Role                : <role of the logged-in user>
Module              : <sidebar tab / area>
Severity            : Critical | High | Medium | Low       (see table below)
Priority            : P1 | P2 | P3 | P4
Related Test Case   : TC-____-___

Preconditions       :
Test data used      : <synthetic records by name, e.g. P2 Aarav Patil, phone 9900011111>

Steps to reproduce  :
  1.
  2.
  3.

Expected            :
Actual              :

Evidence            : screenshot / video filename(s) — must show URL bar + role/tenant
API (if relevant)   :
  Endpoint          :
  Method            :
  Auth used         : <role/tenant>  Bearer <redacted>
  Request body      :
  Response status   :
  Response body     :
Backend log excerpt : <if 500 — stack trace first lines>

Browser + version   :
Timestamp           :
Reproducibility     : Always | Intermittent (x of y) | Once
Status class        : BUG | IMPLEMENTATION_DRIFT | NEEDS_PRODUCT_CONFIRMATION
```

## Severity guide

| Severity     | Use when                                                                                                                                                                          |
| ------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Critical** | Cross-tenant data disclosure or mutation · authentication/session bypass · data loss · wrong patient on a clinical record · a 200 where the matrix says 403 for a protected write |
| **High**     | A core workflow cannot be completed · authorization weaker than intended without cross-tenant impact · financial/stock integrity error · unsupported module operable              |
| **Medium**   | Secondary workflow broken · validation missing · misleading UI · `UI_WITHOUT_BACKEND` controls                                                                                    |
| **Low**      | Cosmetic · wording · non-blocking edge case                                                                                                                                       |

## Priority guide

P1 fix before release · P2 fix this cycle · P3 schedule · P4 backlog.
