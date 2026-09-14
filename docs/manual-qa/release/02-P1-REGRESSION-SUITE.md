# 02 — P1 MAJOR WORKFLOW SUITE (72 + 12 E2E gates)

Run **after P0 is green**. Blocking unless the release manager records a **written** waiver.

**Composition:** Hospital **34** + Clinic **18** + Pharmacy **20** = **72**, as defined in Tranches 2–3,
plus the **12 Tranche-4 E2E gates** below. ~1.5 days for one tester; ~half a day for three in parallel.

| Block         | Source                                                                                               | Cases |
| ------------- | ---------------------------------------------------------------------------------------------------- | ----- |
| A — Hospital  | [`../hospital/12-HOSPITAL-REGRESSION-CHECKLIST.md`](../hospital/12-HOSPITAL-REGRESSION-CHECKLIST.md) | 34    |
| B — Clinic    | [`../clinic/07-CLINIC-REGRESSION-CHECKLIST.md`](../clinic/07-CLINIC-REGRESSION-CHECKLIST.md)         | 18    |
| C — Pharmacy  | [`../pharmacy/11-PHARMACY-REGRESSION-CHECKLIST.md`](../pharmacy/11-PHARMACY-REGRESSION-CHECKLIST.md) | 20    |
| D — E2E gates | [`../e2e/`](../e2e/00-E2E-MASTER-INDEX.md)                                                           | 12    |

---

## BLOCK A — HOSPITAL (34)

| Group                            | Cases                                                                                                                                                                | Result |
| -------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------ |
| **Journeys (5)**                 | `TC-HX-001` H1 · `TC-HX-003` H2 · `TC-HX-006` H3 · `TC-HX-011` H6 family · `TC-HX-016` H8 isolation sweep                                                            | `[ ]`  |
| **Admin setup → downstream (6)** | `TC-HA-004` doctor→picker · `TC-HA-011` receptionist→dashboard · `TC-HA-016` / `TC-HA-017` nurse-login modes · `TC-HA-034` medicine stock · `TC-HAC-019` fee→bill    | `[ ]`  |
| **Reception (5)**                | `TC-HR-007` third family member · `TC-HR-021` appointment · `TC-HR-030` OPD inline new patient · `TC-HR-034` bill + prescription · `TC-HR-040` no bed / occupied bed | `[ ]`  |
| **Doctor (4)**                   | `TC-HD-004` start consultation · `TC-HD-011` follow-up · `TC-HD-023` admit request · `TC-HD-025` plan discharge                                                      | `[ ]`  |
| **Nursing (5)**                  | `TC-HN-004` assigned-only visibility · `TC-HN-008` vitals · `TC-HN-023` task lifecycle · `TC-HNI-006` assignment · `TC-HNI-014` bed cleaning                         | `[ ]`  |
| **Pharmacy (3)**                 | `TC-HP-008` purchase→inward · `TC-HP-021` refund · `TC-HP-026` blocked batch not sellable                                                                            | `[ ]`  |
| **Billing (3)**                  | `TC-HB-001` OPD bill · `TC-HB-004` partial payment · `TC-HB-019` double-click payment                                                                                | `[ ]`  |
| **Remainder (3)**                | the balance of the 34 as listed in the hospital checklist                                                                                                            | `[ ]`  |

## BLOCK B — CLINIC (18)

| Group                            | Cases                                                                                                            | Result |
| -------------------------------- | ---------------------------------------------------------------------------------------------------------------- | ------ |
| **Journeys (4)**                 | `TC-CX-001` C1 · `TC-CX-002` C2 · `TC-CX-004` C3 family · `TC-CX-008` C6 isolation                               | `[ ]`  |
| **Admin setup → downstream (4)** | `TC-CA-002` doctor→picker · `TC-CA-006` fee→bill · `TC-CA-007` vitals→OPD form · `TC-CA-008` operations toggles  | `[ ]`  |
| **Reception (3)**                | `TC-CR-012` new-patient booking never auto-selects · `TC-CR-015` OPD inline new patient · `TC-CR-019` follow-ups | `[ ]`  |
| **Doctor (3)**                   | `TC-CDR-003` start consultation · `TC-CDR-006` lab ordering · `TC-CDR-012` double-submit / failure               | `[ ]`  |
| **Pharmacy (2)**                 | `TC-CX-006` C4 dispensing chain · `TC-CP-009` stock/expiry guards                                                | `[ ]`  |
| **Entitlement (2)**              | `TC-CD-036` working module gates · `TC-CX-007` C5 live revocation                                                | `[ ]`  |

## BLOCK C — PHARMACY (20)

| Group                   | Cases                                                                                                                             | Result |
| ----------------------- | --------------------------------------------------------------------------------------------------------------------------------- | ------ |
| **Journeys (5)**        | `TC-PX-001` · `TC-PX-002` · `TC-PX-004` · `TC-PX-005` · `TC-PX-008` SOLO                                                          | `[ ]`  |
| **Tiers (4)**           | `TC-PT-004` SOLO landing/switcher · `TC-PT-011` branches on SINGLE (discovery) · `TC-PT-012` SINGLE tabs · `TC-PT-016` MULTI tabs | `[ ]`  |
| **Inventory (4)**       | `TC-PI-012` FEFO ordering · `TC-PI-013` chosen batch decrements · `TC-PI-019` lifecycle arithmetic · `TC-PI-010` stock adjustment | `[ ]`  |
| **Sales & returns (3)** | `TC-PS-002` multi-line sale · `TC-PS-015` invoice PDF · `TC-PRF-014` returns arithmetic                                           | `[ ]`  |
| **Expiry (2)**          | `TC-PE-001` buckets · `TC-PE-004` block stops sales                                                                               | `[ ]`  |
| **Reports & audit (2)** | `TC-PR-002` sales report · `TC-PR-011` audit coverage                                                                             | `[ ]`  |

---

## BLOCK D — THE 12 E2E GATES

These are the Tranche-4 cases that catch what no single-screen case can see. Each is a **cross-step
continuity assertion**, so they are cheap relative to what they cover.

| ID           | Gate                                                               | Journey                                        | Result | Evidence |
| ------------ | ------------------------------------------------------------------ | ---------------------------------------------- | ------ | -------- |
| `TC-E2E-001` | a full hospital day, 8 actors, one identity throughout             | [`01`](../e2e/01-COMPLETE-HOSPITAL-JOURNEY.md) | `[ ]`  | ____     |
| `TC-E2E-013` | PI-1…PI-3 — the patient-identity core                              | [`05`](../e2e/05-PATIENT-IDENTITY-JOURNEY.md)  | `[ ]`  | ____     |
| `TC-E2E-024` | no record ever attaches to another family member on the same phone | [`05`](../e2e/05-PATIENT-IDENTITY-JOURNEY.md)  | `[ ]`  | ____     |
| `TC-E2E-025` | OPD → IPD → discharge, states continuous                           | [`04`](../e2e/04-OPD-TO-IPD-TO-DISCHARGE.md)   | `[ ]`  | ____     |
| `TC-E2E-037` | prescription → dispensing continuity                               | [`06`](../e2e/06-PRESCRIPTION-TO-PHARMACY.md)  | `[ ]`  | ____     |
| `TC-E2E-062` | money cannot be created by a click                                 | [`07`](../e2e/07-BILLING-PAYMENT-JOURNEY.md)   | `[ ]`  | ____     |
| `TC-E2E-066` | day-end reconciliation and financial isolation                     | [`07`](../e2e/07-BILLING-PAYMENT-JOURNEY.md)   | `[ ]`  | ____     |
| `TC-E2E-067` | the numeric stock fixture reconciles across nine surfaces          | [`14`](../e2e/14-PHARMACY-STOCK-LIFECYCLE.md)  | `[ ]`  | ____     |
| `TC-E2E-093` | the eight-step bed cycle, every step audited                       | [`12`](../e2e/12-BED-WARD-LIFECYCLE.md)        | `[ ]`  | ____     |
| `TC-E2E-107` | the captured-id sweep: read                                        | [`16`](../e2e/16-TENANT-ISOLATION-E2E.md)      | `[ ]`  | ____     |
| `TC-E2E-113` | live module revocation without re-login                            | [`17`](../e2e/17-MODULE-REVOCATION-E2E.md)     | `[ ]`  | ____     |
| `TC-E2E-127` | post-chaos full reconciliation                                     | [`18`](../e2e/18-FAILURE-RECOVERY-JOURNEYS.md) | `[ ]`  | ____     |

### Conditional additions — run these when the release touches the area

| If the release touches…                    | Also run                                   |
| ------------------------------------------ | ------------------------------------------ |
| OT                                         | `TC-E2E-073` · `TC-E2E-077`                |
| ICU                                        | `TC-E2E-078` · `TC-E2E-081`                |
| nursing                                    | `TC-E2E-082` · `TC-E2E-083` · `TC-E2E-086` |
| staff / auth / roles                       | `TC-E2E-087` · `TC-E2E-088` · `TC-E2E-089` |
| documents / PDFs                           | `TC-E2E-098` · `TC-E2E-101`                |
| multi-branch pharmacy                      | `TC-E2E-102` · `TC-E2E-103`                |
| entitlements / plans                       | `TC-E2E-114` · `TC-E2E-116` · `TC-E2E-117` |
| clinic or standalone pharmacy specifically | `TC-E2E-043` · `TC-E2E-051`                |

---

## P1 result

| Block       | Cases  | Pass | Fail | Blocked | N/A |
| ----------- | ------ | ---- | ---- | ------- | --- |
| A Hospital  | 34     | ___  | ___  | ___     | ___ |
| B Clinic    | 18     | ___  | ___  | ___     | ___ |
| C Pharmacy  | 20     | ___  | ___  | ___     | ___ |
| D E2E gates | 12     | ___  | ___  | ___     | ___ |
| **Total**   | **84** | ___  | ___  | ___     | ___ |

Every **Fail** needs a bug id **or** a written waiver from the release manager. A Fail with neither
is an incomplete run, and the suite is not signed off.
