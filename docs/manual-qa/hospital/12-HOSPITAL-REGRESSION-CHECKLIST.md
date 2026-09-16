# 12 — HOSPITAL REGRESSION CHECKLIST

**Baseline:** `aa143a7` · **No new Test Case IDs** — this document _selects_ existing ones.

Three tiers. Each names cases defined elsewhere in the pack; run them as written there and record
the result **here** for the release.

| Tier                         | When                                                        | Cases | Rough time        |
| ---------------------------- | ----------------------------------------------------------- | ----- | ----------------- |
| **P0 — Critical smoke**      | **Every** staging→production release. Blocking.             | 24    | ~90 min, 1 tester |
| **P1 — Major workflow**      | Every release with hospital changes                         | 34    | ~4 h              |
| **P2 — Extended regression** | Before a major release, or when the touched area demands it | 45    | ~1.5 days         |

> Rule: **P0 must be green before any production release.** A P0 failure blocks the release; it is
> not triaged.

---

## P0 — CRITICAL SMOKE (24)

### Auth & tenancy (4)

| ID            | What it proves                                   | Result |
| ------------- | ------------------------------------------------ | ------ |
| `TC-AUTH-001` | a hospital user can log in                       | `[ ]`  |
| `TC-AUTH-013` | two tenants in two tabs stay separate            | `[ ]`  |
| `TC-AUTH-019` | password reset revokes a live session            | `[ ]`  |
| `TC-API-003`  | Super Admin token is refused on tenant endpoints | `[ ]`  |

### Patient identity (4)

| ID          | What it proves                                                         | Result |
| ----------- | ---------------------------------------------------------------------- | ------ |
| `TC-HR-001` | a patient can be registered and gets `PAT<id>`                         | `[ ]`  |
| `TC-HR-005` | duplicate phone shows the chooser; **Use This Patient** creates nobody | `[ ]`  |
| `TC-HR-006` | **Register Different Patient** creates a separate identity             | `[ ]`  |
| `TC-HR-011` | the 409 exposes only id/publicId/customId/name/age                     | `[ ]`  |

### Core clinical flow (5)

| ID          | What it proves                                            | Result |
| ----------- | --------------------------------------------------------- | ------ |
| `TC-HR-029` | an OPD case can be created                                | `[ ]`  |
| `TC-HD-005` | a consultation completes with a prescription              | `[ ]`  |
| `TC-HD-016` | the consultation attaches to the patient actually opened  | `[ ]`  |
| `TC-HD-017` | the prescription PDF carries the **patient's** identifier | `[ ]`  |
| `TC-HD-014` | double-click Complete does not create two bills           | `[ ]`  |

### Admission & beds (3)

| ID           | What it proves                                | Result |
| ------------ | --------------------------------------------- | ------ |
| `TC-HR-039`  | reception can admit; the bed becomes Occupied | `[ ]`  |
| `TC-HWB-010` | the full bed cycle incl. cleaning gate holds  | `[ ]`  |
| `TC-HR-041`  | a patient cannot be admitted twice            | `[ ]`  |

### Pharmacy & billing (4)

| ID          | What it proves                                                 | Result |
| ----------- | -------------------------------------------------------------- | ------ |
| `TC-HP-012` | a prescription sale decrements stock                           | `[ ]`  |
| `TC-HP-014` | insufficient stock is refused and stock is unchanged           | `[ ]`  |
| `TC-HP-017` | a double-clicked sale creates one sale                         | `[ ]`  |
| `TC-HB-003` | Mark Paid moves the bill to PAID and the collection reconciles | `[ ]`  |

### Isolation & entitlement (4)

| ID           | What it proves                                          | Result |
| ------------ | ------------------------------------------------------- | ------ |
| `TC-ISO-005` | another tenant's patient by numeric id → 403/404        | `[ ]`  |
| `TC-ISO-006` | another tenant's patient by **publicId** → 403/404      | `[ ]`  |
| `TC-ISO-020` | `/ipd/:id` deep link leaks nothing across tenants       | `[ ]`  |
| `TC-SA-041`  | a revoked module denies the API even with a valid token | `[ ]`  |

---

## P1 — MAJOR WORKFLOW (34)

Run **after** P0 is green.

### End-to-end journeys (5)

`TC-HX-001` H1 · `TC-HX-003` H2 · `TC-HX-006` H3 · `TC-HX-011` **H6 family** · `TC-HX-016` H8 isolation sweep

### Admin setup → downstream (6)

`TC-HA-004` doctor → picker · `TC-HA-011` receptionist → dashboard · `TC-HA-016`/`017` nurse login modes · `TC-HA-034` medicine stock · `TC-HAC-019` fee → bill

### Reception (5)

`TC-HR-007` third family member · `TC-HR-021` appointment · `TC-HR-030` OPD inline new patient · `TC-HR-034` bill + prescription · `TC-HR-040` no bed / occupied bed

### Doctor (4)

`TC-HD-004` start consultation · `TC-HD-011` follow-up · `TC-HD-023` admit request · `TC-HD-025` plan discharge

### Nursing (5)

`TC-HN-004` assigned-only visibility · `TC-HN-008` vitals · `TC-HN-023` task lifecycle · `TC-HNI-006` assignment · `TC-HNI-014` bed cleaning

### Pharmacy (3)

`TC-HP-008` purchase → inward · `TC-HP-021` refund · `TC-HP-026` blocked batch not sellable

### Billing (3)

`TC-HB-001` OPD bill · `TC-HB-004` partial payment · `TC-HB-019` double-click payment

### Settings downstream (3)

`TC-HAS-003` payment timing · `TC-HAS-010` vitals toggle · `TC-HAS-013` Files & Access enforcement

---

## P2 — EXTENDED REGRESSION (45)

Run before a major release, or when the change touches the area.

### OT (8)

`TC-HOT-002` permission grid · `TC-HOT-006` schedule · `TC-HOT-007` double booking · `TC-HOT-010` pre-op gate · `TC-HOT-013` start · `TC-HOT-016` complete · `TC-HOT-021` invalid transitions · `TC-HX-008` H4 journey
**Plus:** at least **three** NABH forms end-to-end, always including `TC-HOT-029` (WHO) and one consent form, checking the UHID identity.

### ICU (6)

`TC-HICU-002` admission creates a stay · `TC-HICU-004` dashboard accuracy · `TC-HICU-008` ventilator params · `TC-HICU-011` alert threshold · `TC-HICU-014` transfer out · `TC-HX-010` H5 journey

### Wards & beds (4)

`TC-HWB-004` ward delete rules · `TC-HWB-011` transfer frees bed · `TC-HWB-012` maintenance capacity · `TC-HWB-022` two receptionists, one bed

### Permissions & drift watch (7)

`TC-PERM-029` hidden tab = server refusal · `TC-API-006` settings drift · `TC-API-007` prescription PDF drift · `TC-PERM-007` incharge notification mismatch · `TC-MOD-010` OPD gate · `TC-MOD-011` IPD gate · `TC-MOD-013` APPOINTMENTS gate (positive control)

### Isolation depth (6)

`TC-ISO-012` cross-tenant linking · `TC-ISO-022` nursing records · `TC-ISO-036` pay another tenant's bill · `TC-ISO-043` sell from another tenant's batch · `TC-ISO-047` reset another tenant's password · `TC-ISO-050` download another tenant's document

### Super Admin (5)

`TC-SA-018` create tenant · `TC-SA-034`/`035` deactivate + reactivate with data intact · `TC-SA-040` plan upgrade downstream · `TC-SA-060` platform audit

### Special modes & settings (5)

`TC-MODE-001` single-doctor landing · `TC-HD-048` single-doctor hospital · `TC-HAS-022` OT permission revoke · `TC-HAS-024` OT policy presets · `TC-HAS-039` restore checklist

### UI & resilience (4)

`TC-HR-055` loading/empty/error states · `TC-HR-054` modal behaviour · `TC-HB-029` currency and long names · `TC-HR-058` re-login persistence

---

## Release sign-off

```
Release            : ____________________     Build SHA : ____________________
Environment        : ____________________     Date      : ____________________

P0  24 cases   Pass ___  Fail ___  Blocked ___      ⇒ release blocked if Fail > 0
P1  34 cases   Pass ___  Fail ___  Blocked ___
P2  45 cases   Pass ___  Fail ___  Blocked ___   (N/A if not run — state why)

Known-issue exceptions accepted (IDs + reason, referencing status/IMPLEMENTATION-STATUS.md):
____________________________________________________________________

QA sign-off : ____________________   Product sign-off : ____________________
```

> **Known findings are not P0 failures.** `TC-API-006`, `TC-API-007`, `TC-PERM-007`,
> `TC-MOD-010`/`011`/`012` and the tenant-alias drift are **recorded, accepted, open items**.
> Track their status per release rather than re-raising them; a _change_ in their behaviour,
> in either direction, is worth reporting.
